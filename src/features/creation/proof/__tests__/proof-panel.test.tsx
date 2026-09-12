// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ProofPanel } from "@/features/creation/proof/ProofPanel";
import type { ProofIssue, ProofLocation, ProofView } from "@/types/creation";

const runProof = vi.fn();
const ignoreProofLocation = vi.fn();
const unignoreProofLocation = vi.fn();

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({ runProof, ignoreProofLocation, unignoreProofLocation })
}));

function location(overrides: Partial<ProofLocation> = {}): ProofLocation {
  return {
    locationKey: "repeatedChar#0#aaaaaaaa",
    paragraphIndex: 0,
    matchedText: "他他他",
    snippet: null,
    detail: "连续重复字「他他他」",
    ignored: false,
    ...overrides
  };
}

function issue(overrides: Partial<ProofIssue> = {}): ProofIssue {
  return {
    sceneId: "scene-1",
    chapterId: "chapter-1",
    chapterTitle: "第一章",
    sceneTitle: "开场",
    rule: "repeatedChar",
    message: "连续重复字「他他他」（共 2 处）",
    snippet: "…他他他站在门口…",
    count: 2,
    ignoredCount: 0,
    locations: [
      location({ locationKey: "repeatedChar#0#aaaaaaaa" }),
      location({ locationKey: "repeatedChar#1#aaaaaaaa", paragraphIndex: 1 })
    ],
    ...overrides
  };
}

function proofView(overrides: Partial<ProofView> = {}): ProofView {
  return {
    projectId: "project-1",
    scanScope: {
      kind: "project",
      label: "全书扫描：2 卷 / 8 章 / 30 场景",
      volumeCount: 2,
      chapterCount: 8,
      sceneCount: 30,
      rules: ["repeatedChar"],
      bannedWords: [],
      maxParagraphChars: 500
    },
    issues: [issue()],
    ignoredIssues: [],
    scannedScenes: 30,
    affectedScenes: 1,
    total: 2,
    ignoredCount: 0,
    rawTotal: 2,
    truncated: false,
    ignoredTruncated: false,
    ignoreRecordCount: 0,
    ...overrides
  };
}

describe("ProofPanel 全书扫描与总数", () => {
  beforeEach(() => {
    runProof.mockReset();
    ignoreProofLocation.mockReset();
    unignoreProofLocation.mockReset();
    runProof.mockResolvedValue(proofView());
  });

  afterEach(() => cleanup());

  it("显式展示扫描范围与未忽略问题总数", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    expect(await screen.findByText(/全书扫描：2 卷 \/ 8 章 \/ 30 场景/)).toBeTruthy();
    expect(screen.getByText("共 2 处问题")).toBeTruthy();
  });

  it("同时展示已忽略数量与忽略记录条数", async () => {
    runProof.mockResolvedValue(proofView({ total: 5, ignoredCount: 2, rawTotal: 7, ignoreRecordCount: 2 }));
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    expect(await screen.findByText("共 5 处问题")).toBeTruthy();
    expect(screen.getByText("2 处已忽略")).toBeTruthy();
    expect(screen.getByText("本项目已保存 2 条忽略记录")).toBeTruthy();
  });

  it("截断时提示列表不完整，而不是假装只有这些", async () => {
    runProof.mockResolvedValue(proofView({ total: 500, truncated: true, issues: [] }));
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    expect(await screen.findByText(/列表仅显示前 0 组/)).toBeTruthy();
    expect(screen.getByText("结果超出上限，已按章节顺序截断")).toBeTruthy();
  });

  it("无问题时给出干净提示", async () => {
    runProof.mockResolvedValue(proofView({ total: 0, issues: [] }));
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    expect(await screen.findByText(/未发现问题/)).toBeTruthy();
  });

  it("扫描失败时提示正文未被修改", async () => {
    runProof.mockRejectedValue(new Error("boom"));
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    expect(await screen.findByText(/正文未被修改/)).toBeTruthy();
  });
});

describe("ProofPanel 按位置忽略", () => {
  beforeEach(() => {
    runProof.mockReset();
    ignoreProofLocation.mockReset();
    unignoreProofLocation.mockReset();
    runProof.mockResolvedValue(proofView());
    ignoreProofLocation.mockResolvedValue({ commandType: "proof.ignore", ignoreIds: ["x"], removed: 0 });
    unignoreProofLocation.mockResolvedValue({ commandType: "proof.unignore", ignoreIds: ["x"], removed: 1 });
  });

  afterEach(() => cleanup());

  it("每个位置独立一个忽略按钮，点击只传该位置", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    const buttons = await screen.findAllByRole("button", { name: /忽略第 1 段/ });
    expect(buttons.length).toBe(1);
    fireEvent.click(buttons[0]!);
    await waitFor(() => expect(ignoreProofLocation).toHaveBeenCalledTimes(1));
    expect(ignoreProofLocation).toHaveBeenCalledWith({
      projectId: "project-1",
      sceneId: "scene-1",
      rule: "repeatedChar",
      locationKey: "repeatedChar#0#aaaaaaaa",
      matchedText: "他他他"
    });
    // 忽略后必须重新扫描，让总数立刻反映新状态。
    await waitFor(() => expect(runProof).toHaveBeenCalledTimes(2));
  });

  it("忽略第 2 段不影响第 1 段（位置粒度而非文本粒度）", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    const second = await screen.findByRole("button", { name: /忽略第 2 段/ });
    fireEvent.click(second);
    await waitFor(() => expect(ignoreProofLocation).toHaveBeenCalledTimes(1));
    expect(ignoreProofLocation.mock.calls[0]![0]).toMatchObject({
      locationKey: "repeatedChar#1#aaaaaaaa"
    });
    expect(screen.getByRole("button", { name: /忽略第 1 段/ })).toBeTruthy();
  });

  it("已忽略的位置显示取消忽略并调用 unignore", async () => {
    runProof.mockResolvedValue(
      proofView({
        total: 1,
        ignoredCount: 1,
        rawTotal: 2,
        ignoreRecordCount: 1,
        issues: [
          issue({
            count: 1,
            ignoredCount: 1,
            locations: [
              location({ locationKey: "repeatedChar#0#aaaaaaaa", ignored: true }),
              location({ locationKey: "repeatedChar#1#aaaaaaaa", paragraphIndex: 1 })
            ]
          })
        ]
      })
    );
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    const undo = await screen.findByRole("button", { name: /取消忽略第 1 段/ });
    fireEvent.click(undo);
    await waitFor(() => expect(unignoreProofLocation).toHaveBeenCalledTimes(1));
    expect(unignoreProofLocation).toHaveBeenCalledWith({
      projectId: "project-1",
      sceneId: "scene-1",
      rule: "repeatedChar",
      locationKey: "repeatedChar#0#aaaaaaaa"
    });
  });

  it("勾选显示已忽略后重新扫描并展示已忽略分组", async () => {
    runProof.mockResolvedValue(
      proofView({
        total: 0,
        ignoredCount: 2,
        rawTotal: 2,
        issues: [],
        ignoredIssues: [
          issue({
            count: 0,
            ignoredCount: 2,
            locations: [
              location({ ignored: true }),
              location({ locationKey: "repeatedChar#1#aaaaaaaa", paragraphIndex: 1, ignored: true })
            ]
          })
        ]
      })
    );
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    const toggle = await screen.findByLabelText(/同时显示已忽略的问题/);
    fireEvent.click(toggle);
    await waitFor(() => expect(runProof).toHaveBeenCalledTimes(2));
    expect(runProof.mock.calls[1]![0]).toMatchObject({ includeIgnored: true });
    expect(await screen.findByText(/已忽略（1 组）/)).toBeTruthy();
  });
});

describe("ProofPanel 规则与阈值", () => {
  beforeEach(() => {
    runProof.mockReset();
    ignoreProofLocation.mockReset();
    unignoreProofLocation.mockReset();
    runProof.mockResolvedValue(proofView());
  });

  afterEach(() => cleanup());

  it("默认启用全部 10 条规则", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    await screen.findByText(/全书扫描/);
    expect(runProof.mock.calls[0]![0].rules).toHaveLength(10);
    expect(screen.getByLabelText(/别名一致性/)).toBeTruthy();
    expect(screen.getByLabelText(/疑似错拼/)).toBeTruthy();
  });

  it("取消勾选规则后重新扫描只带剩余规则", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    await screen.findByText(/全书扫描/);
    const checkbox = screen.getByLabelText(/口头禅/) as HTMLInputElement;
    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole("button", { name: "重新检查" }));
    await waitFor(() => expect(runProof).toHaveBeenCalledTimes(2));
    const rules = runProof.mock.calls[1]![0].rules as string[];
    expect(rules).toHaveLength(9);
    expect(rules).not.toContain("crutchWord");
  });

  it("修改超长段落阈值后传入新值", async () => {
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    await screen.findByText(/全书扫描/);
    const input = screen.getByLabelText(/超长段落阈值/) as HTMLInputElement;
    fireEvent.change(input, { target: { value: "800" } });
    fireEvent.click(screen.getByRole("button", { name: "重新检查" }));
    await waitFor(() => expect(runProof).toHaveBeenCalledTimes(2));
    expect(runProof.mock.calls[1]![0].maxParagraphChars).toBe(800);
  });
});
