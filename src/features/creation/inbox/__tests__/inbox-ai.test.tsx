// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { InboxPage } from "@/features/creation/inbox/InboxPage";
import { useCreationStore } from "@/stores/creation-store";
import { useAppStore } from "@/stores/app-store";
import { useUIStore } from "@/stores/ui-store";
import { isAIAvailable } from "@/types/ai";
import * as creationService from "@/services/creation-service";
import * as aiService from "@/services/ai-service";

vi.mock("@/services/creation-service", () => ({
  inboxList: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  inboxCreate: vi.fn(),
  createInbox: vi.fn(),
  cardsList: vi.fn(),
  runStructure: vi.fn(),
  listProjects: vi.fn(),
  readProjectNavigation: vi.fn(),
  readProjectOutline: vi.fn(),
  readSceneBody: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  search: vi.fn(),
  replacePreview: vi.fn(),
  replaceApply: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  sessionReport: vi.fn(),
  sessionDelete: vi.fn(),
  proofQuery: vi.fn(),
  trashList: vi.fn(),
  snapshotList: vi.fn(),
  cardRead: vi.fn(),
  cardTypesList: vi.fn(),
  cardRelations: vi.fn(),
  exportDraft: vi.fn(),
  importDraftPreview: vi.fn(),
  exportProjectBundle: vi.fn(),
  importProjectBundle: vi.fn(),
  annotationList: vi.fn(),
  annotationCreate: vi.fn(),
  annotationUpdate: vi.fn(),
  annotationDelete: vi.fn(),
  resourceList: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  projectExport: vi.fn(),
  migrationStatus: vi.fn(),
  migrationRun: vi.fn(),
  createProject: vi.fn()
}));

vi.mock("@/services/ai-service", () => ({
  getAISettings: vi.fn(),
  updateAISettings: vi.fn(),
  saveAIApiKey: vi.fn(),
  clearAIApiKey: vi.fn(),
  testAIConnection: vi.fn(),
  runAIAction: vi.fn()
}));

const projects = [
  { id: "p1", title: "项目A", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 }
];

function makeItem(overrides: Record<string, unknown> = {}) {
  return {
    id: "i1",
    legacyId: null,
    title: "测试灵感",
    body: "原始正文内容",
    type: "note",
    status: "inbox",
    tags: [],
    platformTags: [],
    source: null,
    variants: [],
    revision: 1,
    createdAt: "",
    updatedAt: "",
    ...overrides
  };
}

let aiSettingsValue: unknown = null;

function resetStores(): void {
  useCreationStore.setState({ projects, cards: [], selectedCardId: undefined });
  useUIStore.setState({ toasts: [] });
  useAppStore.setState({ errors: [] });
}

async function renderAndSelect(item: Record<string, unknown>, aiSettings: unknown) {
  aiSettingsValue = aiSettings;
  vi.mocked(creationService.inboxList).mockResolvedValue([item]);
  render(<InboxPage projectId="p1" />);
  const entry = await screen.findByText("测试灵感");
  fireEvent.click(entry);
  // 详情区渲染后，AI 面板区域出现
  await screen.findByText("AI 候选版本");
  return;
}

beforeEach(() => {
  resetStores();
  aiSettingsValue = null;
  vi.mocked(aiService.getAISettings).mockReset();
  vi.mocked(aiService.getAISettings).mockImplementation(() => Promise.resolve(aiSettingsValue as never));
  vi.mocked(creationService.inboxList).mockReset();
  vi.mocked(creationService.inboxList).mockResolvedValue([]);
  vi.mocked(creationService.inboxCount).mockResolvedValue({ total: 0, pending: 0 });
  vi.mocked(creationService.inboxUpdate).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.createInbox).mockResolvedValue("i-new");
  vi.mocked(creationService.inboxUpdate).mockResolvedValue(undefined);
  vi.mocked(aiService.runAIAction).mockReset();
});
afterEach(() => cleanup());

describe("isAIAvailable 边界（默认关闭）", () => {
  it("enabled 为 false 时即使已配置 Key 也不可用", () => {
    expect(isAIAvailable({ enabled: false, hasApiKey: true } as never)).toBe(false);
  });
  it("enabled 为 true 但未配置 Key 时不可用", () => {
    expect(isAIAvailable({ enabled: true, hasApiKey: false } as never)).toBe(false);
  });
  it("enabled 且已配置 Key 时才可用", () => {
    expect(isAIAvailable({ enabled: true, hasApiKey: true } as never)).toBe(true);
  });
  it("未初始化（null/undefined）时不可用", () => {
    expect(isAIAvailable(null)).toBe(false);
    expect(isAIAvailable(undefined)).toBe(false);
  });
});

describe("InboxPage AI 门控", () => {
  it("AI 未配置（默认关闭）时不渲染任何 AI 操作按钮", async () => {
    await renderAndSelect(makeItem(), null);
    expect(screen.queryByText("润色")).toBeNull();
    expect(screen.queryByText("扩写")).toBeNull();
    expect(screen.queryByText("采纳为正文")).toBeNull();
  });

  it("AI 未启用时给出引导说明且不发起 runAIAction", async () => {
    await renderAndSelect(makeItem(), { enabled: false, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    expect(screen.getByText(/AI 助手未启用/)).toBeDefined();
    expect(vi.mocked(aiService.runAIAction)).not.toHaveBeenCalled();
  });

  it("启用但缺少 API Key 时给出引导且不渲染 AI 按钮", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: false, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    expect(screen.getByText(/尚未配置 API Key/)).toBeDefined();
    expect(screen.queryByText("润色")).toBeNull();
    expect(vi.mocked(aiService.runAIAction)).not.toHaveBeenCalled();
  });

  it("关闭 AI 后，已存在的候选版本仍然展示", async () => {
    const variant = { id: "v1", kind: "polish", content: "已有候选正文", model: "gpt", createdAt: new Date().toISOString(), prompt: "p" };
    await renderAndSelect(makeItem({ variants: [variant] }), null);
    expect(screen.getByText("已有候选正文")).toBeDefined();
    expect(screen.queryByText("润色")).toBeNull();
  });
});

describe("InboxPage AI 候选生成与采纳", () => {
  it("生成候选只追加到 variants，不覆盖正文", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    vi.mocked(aiService.runAIAction).mockResolvedValue({ kind: "polish", content: "AI 生成的候选", prompt: "p", model: "gpt" });
    fireEvent.click(screen.getByText("润色"));

    await waitFor(() => {
      const variantCalls = vi.mocked(creationService.inboxUpdate).mock.calls
        .map((call) => call[0] as Record<string, unknown>)
        .filter((cmd) => cmd && Array.isArray(cmd.variants));
      expect(variantCalls.length).toBeGreaterThanOrEqual(1);
    });
    const variantCalls = vi.mocked(creationService.inboxUpdate).mock.calls
      .map((call) => call[0] as Record<string, unknown>)
      .filter((cmd) => cmd && Array.isArray(cmd.variants));
    const lastVariantCall = variantCalls[variantCalls.length - 1]!;
    expect(lastVariantCall.body).toBeUndefined();
    expect((lastVariantCall.variants as unknown[]).length).toBe(1);
    expect((screen.getByDisplayValue("原始正文内容") as HTMLTextAreaElement).value).toBe("原始正文内容");
  });

  it("AI 调用失败时只设置错误、不写入任何候选、不改正文", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    vi.mocked(aiService.runAIAction).mockRejectedValue(new Error("网络异常"));
    fireEvent.click(screen.getByText("润色"));

    await waitFor(() => expect(useAppStore.getState().errors.some((error) => error.message.includes("网络异常"))).toBe(true));
    const variantCalls = vi.mocked(creationService.inboxUpdate).mock.calls
      .map((call) => call[0] as Record<string, unknown>)
      .filter((cmd) => cmd && Array.isArray(cmd.variants));
    expect(variantCalls.length).toBe(0);
    expect((screen.getByDisplayValue("原始正文内容") as HTMLTextAreaElement).value).toBe("原始正文内容");
  });

  it("采纳候选需要明确点击「采纳为正文」，且只写候选内容到正文", async () => {
    const variant = { id: "v1", kind: "polish", content: "采纳后的正文", model: "gpt", createdAt: new Date().toISOString(), prompt: "p" };
    await renderAndSelect(makeItem({ variants: [variant] }), null);
    expect(screen.getByText("采纳后的正文")).toBeDefined();
    // 点击前不得自动采纳
    expect(vi.mocked(creationService.inboxUpdate).mock.calls.some((call) => {
      const cmd = call[0] as Record<string, unknown>;
      return cmd && cmd.body === "采纳后的正文";
    })).toBe(false);
    fireEvent.click(screen.getByText("采纳为正文"));
    await waitFor(() => expect(creationService.inboxUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ itemId: "i1", body: "采纳后的正文" })
    ));
  });

  it("草稿保存失败时不调用 AI", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    // 第一次 inboxUpdate（保存草稿）失败：useCreationActions.updateInbox 只在 IPC 抛错时返回 false，
    // 因此用 mockRejectedValue 触发失败路径，而不是 mockResolvedValue(false)。
    vi.mocked(creationService.inboxUpdate).mockReset();
    vi.mocked(creationService.inboxUpdate).mockRejectedValue(new Error("草稿保存失败"));
    vi.mocked(aiService.runAIAction).mockResolvedValue({ kind: "polish", content: "AI 结果", prompt: "p", model: "gpt" });

    fireEvent.click(screen.getByText("润色"));
    await waitFor(() => expect(useUIStore.getState().toasts.some((t) => t.title.includes("草稿保存失败"))).toBe(true));

    // AI 不应被调用
    expect(vi.mocked(aiService.runAIAction)).not.toHaveBeenCalled();
  });

  it("草稿保存成功并推进 revision 后，候选用最新 revision 写入", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    // 模拟保存草稿后返回最新 revision 2 的条目
    vi.mocked(creationService.inboxUpdate).mockReset();
    vi.mocked(aiService.runAIAction).mockResolvedValue({ kind: "polish", content: "AI 候选", prompt: "p", model: "gpt" });
    const refreshedItem = makeItem({ revision: 2 });
    vi.mocked(creationService.inboxList).mockResolvedValue([refreshedItem]);
    // 第一次保存草稿 ok，第二次追加候选 ok
    vi.mocked(creationService.inboxUpdate).mockResolvedValue(true);

    fireEvent.click(screen.getByText("润色"));
    await waitFor(() => {
      const variantCalls = vi.mocked(creationService.inboxUpdate).mock.calls
        .map((call) => call[0] as Record<string, unknown>)
        .filter((cmd) => cmd && Array.isArray(cmd.variants));
      expect(variantCalls.length).toBeGreaterThanOrEqual(1);
    });

    const variantCall = vi.mocked(creationService.inboxUpdate).mock.calls
      .map((call) => call[0] as Record<string, unknown>)
      .find((cmd) => cmd && Array.isArray(cmd.variants))!;
    // 候选必须用最新 revision 2，不是旧 revision 1
    expect(variantCall.baseRevision).toBe(2);
    expect((variantCall.variants as unknown[]).length).toBe(1);
  });

  it("候选保存冲突时不显示成功", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    // 草稿保存 ok，但候选保存（第二次 update）失败
    vi.mocked(creationService.inboxUpdate).mockReset();
    vi.mocked(aiService.runAIAction).mockResolvedValue({ kind: "polish", content: "AI 结果", prompt: "p", model: "gpt" });
    vi.mocked(creationService.inboxList).mockResolvedValue([makeItem({ revision: 2 })]);
    // 第一次成功（保存草稿），第二次抛错（候选冲突）。
    // useCreationActions.updateInbox 只在 IPC 抛错时返回 false。
    vi.mocked(creationService.inboxUpdate)
      .mockResolvedValueOnce(true)
      .mockRejectedValueOnce(new Error("候选保存冲突"));

    fireEvent.click(screen.getByText("润色"));
    await waitFor(() => expect(useUIStore.getState().toasts.some((t) => t.title.includes("候选保存失败"))).toBe(true));
  });

  it("快速重复点击只发起一次 AI 请求", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    // 让 AI 请求挂起，确保第二次点击在第一次返回前到达
    vi.mocked(aiService.runAIAction).mockReturnValue(new Promise(() => undefined) as never);
    vi.mocked(creationService.inboxUpdate).mockResolvedValue(true);
    vi.mocked(creationService.inboxList).mockResolvedValue([makeItem({ revision: 2 })]);

    const button = screen.getByText("润色");
    fireEvent.click(button);
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() => expect(vi.mocked(aiService.runAIAction).mock.calls.length).toBe(1));
    expect(vi.mocked(aiService.runAIAction)).toHaveBeenCalledTimes(1);
  });

  it("AI 不会自动覆盖正文（采纳必须显式点击）", async () => {
    await renderAndSelect(makeItem(), { enabled: true, hasApiKey: true, provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7 });
    vi.mocked(aiService.runAIAction).mockResolvedValue({ kind: "polish", content: "AI 自动覆盖", prompt: "p", model: "gpt" });
    vi.mocked(creationService.inboxList).mockResolvedValue([makeItem({ revision: 2 })]);
    vi.mocked(creationService.inboxUpdate).mockResolvedValue(true);

    fireEvent.click(screen.getByText("润色"));
    // 关键不变量：AI 返回的内容只能进入 variants，绝不能作为 body 写入。
    // 草稿保存调用会带 body=原始正文，但 AI 内容 "AI 自动覆盖" 绝不能出现在 body 字段中。
    await waitFor(() => {
      const aiBodyCalls = vi.mocked(creationService.inboxUpdate).mock.calls
        .map((call) => call[0] as Record<string, unknown>)
        .filter((cmd) => cmd && typeof cmd.body === "string" && cmd.body === "AI 自动覆盖");
      expect(aiBodyCalls.length).toBe(0);
    });
    // 正文保持原值
    expect((screen.getByDisplayValue("原始正文内容") as HTMLTextAreaElement).value).toBe("原始正文内容");
  });
});
