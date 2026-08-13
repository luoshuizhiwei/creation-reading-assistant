// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ExcerptSourceSnapshot } from "@/types/library";
import type { ExcerptCommandExecutor } from "@/features/library/excerpt-commands";
import {
  buildCardCreateCommand,
  buildInboxCreateCommand,
  EXCERPT_CARD_KIND,
  EXCERPT_INBOX_KIND,
  EXCERPT_INBOX_STATUS,
  executeSaveToInbox,
  executeSaveToProjectCard,
  sourceToSnapshot
} from "@/features/library/excerpt-commands";

// Mock 桌面 IPC client：createReaderExcerptDestination 通过 getDesktopApi()
// 拿到 creation.listProjects / inboxCreate / runStructure 三个方法。
const inboxCreate = vi.fn();
const runStructure = vi.fn();
const listProjects = vi.fn();

vi.mock("@/services/ipc-client", () => ({
  getDesktopApi: () => ({
    creation: {
      listProjects,
      inboxCreate,
      runStructure
    }
  })
}));

import { createReaderExcerptDestination } from "../excerpt-destination-impl";

const baseSource: ExcerptSourceSnapshot = {
  bookId: "b1",
  bookTitle: "测试书",
  bookAuthor: "作者",
  format: "txt",
  chapterTitle: "第一章",
  progressPercent: 0.5,
  locationLabel: "第一章 · 50%",
  excerpt: "要摘录的选文",
  href: undefined,
  cfi: undefined,
  charOffset: 100,
  charLength: 12,
  scrollTop: 300,
  createdAt: "2026-08-12T10:00:00.000Z"
};

beforeEach(() => {
  inboxCreate.mockReset();
  runStructure.mockReset();
  listProjects.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("生产 ReaderExcerptDestination — 摘录到收件箱", () => {
  it("调用 inbox.create 并保留完整选文与来源快照", async () => {
    inboxCreate.mockResolvedValue({ itemId: "inbox-1" });
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToInbox(baseSource);
    expect(result.success).toBe(true);
    expect(result.itemId).toBe("inbox-1");
    expect(inboxCreate).toHaveBeenCalledTimes(1);
    const command = inboxCreate.mock.calls[0]![0];
    expect(command.type).toBe("inbox.create");
    expect(command.title).toBe("摘录：测试书");
    expect(command.body).toBe("要摘录的选文");
    expect(command.kind).toBe(EXCERPT_INBOX_KIND);
    expect(command.status).toBe(EXCERPT_INBOX_STATUS);
    expect(command.tags).toEqual(["摘录", "TXT"]);
    // 来源快照保留完整字段（由共享 sourceToSnapshot 生成）
    expect(command.source).toMatchObject({
      bookId: "b1",
      bookTitle: "测试书",
      bookAuthor: "作者",
      format: "txt",
      chapterTitle: "第一章",
      progressPercent: 0.5,
      locationLabel: "第一章 · 50%",
      excerpt: "要摘录的选文",
      charOffset: 100,
      charLength: 12,
      scrollTop: 300,
      createdAt: "2026-08-12T10:00:00.000Z"
    });
  });

  it("IPC 抛异常时不假成功，错误透传", async () => {
    inboxCreate.mockRejectedValue(new Error("工作区不可用"));
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToInbox(baseSource);
    expect(result.success).toBe(false);
    expect(result.error).toContain("工作区不可用");
    expect(result.itemId).toBeUndefined();
  });

  it("IPC 返回空 itemId 时不假成功", async () => {
    inboxCreate.mockResolvedValue({ itemId: undefined });
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToInbox(baseSource);
    expect(result.success).toBe(false);
    expect(result.error).toContain("空");
  });
});

describe("生产 ReaderExcerptDestination — 摘录到项目资料卡", () => {
  it("使用合法资料卡类型 reference 并正确读取 entityId", async () => {
    runStructure.mockResolvedValue({ entityId: "card-1", commandType: "card.create" });
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToProjectCard("p1", baseSource);
    expect(result.success).toBe(true);
    expect(result.itemId).toBe("card-1");
    expect(runStructure).toHaveBeenCalledTimes(1);
    const command = runStructure.mock.calls[0]![0];
    expect(command.type).toBe("card.create");
    expect(command.projectId).toBe("p1");
    // 必须使用合法内置类型 reference（不是 excerpt）
    expect(command.kind).toBe(EXCERPT_CARD_KIND);
    expect(command.title).toBe("摘录：测试书");
    expect(command.tags).toEqual(["摘录", "TXT"]);
    // content 携带完整来源快照
    expect(command.content).toMatchObject({
      bookId: "b1",
      bookTitle: "测试书",
      excerpt: "要摘录的选文",
      format: "txt"
    });
  });

  it("IPC 抛异常时不假成功", async () => {
    runStructure.mockRejectedValue(new Error("卡片写入失败"));
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToProjectCard("p1", baseSource);
    expect(result.success).toBe(false);
    expect(result.error).toContain("卡片写入失败");
  });

  it("返回空 entityId 时不假成功", async () => {
    runStructure.mockResolvedValue({ entityId: undefined, commandType: "card.create" });
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToProjectCard("p1", baseSource);
    expect(result.success).toBe(false);
    expect(result.error).toContain("空 ID");
  });

  it("不读取旧字段 cardId（旧契约已废弃）", async () => {
    // 即使 IPC 错误返回 cardId 字段，实现也应只认 entityId
    runStructure.mockResolvedValue({ cardId: "legacy-id", commandType: "card.create" });
    const dest = createReaderExcerptDestination();
    const result = await dest.saveToProjectCard("p1", baseSource);
    expect(result.success).toBe(false);
    expect(result.error).toContain("空 ID");
  });
});

describe("生产 ReaderExcerptDestination — listProjects", () => {
  it("返回项目 id/title 列表，不泄漏其它字段", async () => {
    listProjects.mockResolvedValue([
      { id: "p1", title: "项目一", revision: 3, updatedAt: "x" },
      { id: "p2", title: "项目二", revision: 1, updatedAt: "y" }
    ]);
    const dest = createReaderExcerptDestination();
    const projects = await dest.listProjects();
    expect(projects).toEqual([
      { id: "p1", title: "项目一" },
      { id: "p2", title: "项目二" }
    ]);
  });

  it("listProjects 抛异常时向上抛出（不假成功）", async () => {
    listProjects.mockRejectedValue(new Error("无法读取项目列表"));
    const dest = createReaderExcerptDestination();
    await expect(dest.listProjects()).rejects.toThrow("无法读取项目列表");
  });
});

describe("共享深模块 excerpt-commands — 与 contract 共用同一套逻辑", () => {
  it("buildInboxCreateCommand 构造的字段与生产实现一致", () => {
    const cmd = buildInboxCreateCommand(baseSource);
    expect(cmd.type).toBe("inbox.create");
    expect(cmd.kind).toBe(EXCERPT_INBOX_KIND);
    expect(cmd.status).toBe(EXCERPT_INBOX_STATUS);
    expect(cmd.title).toBe("摘录：测试书");
    expect(cmd.tags).toEqual(["摘录", "TXT"]);
  });

  it("buildCardCreateCommand 使用 reference 类型", () => {
    const cmd = buildCardCreateCommand("p1", baseSource);
    expect(cmd.type).toBe("card.create");
    expect(cmd.kind).toBe(EXCERPT_CARD_KIND);
    expect(cmd.projectId).toBe("p1");
  });

  it("sourceToSnapshot 保留所有字段", () => {
    const snapshot = sourceToSnapshot(baseSource);
    expect(snapshot).toMatchObject({
      bookId: "b1",
      bookTitle: "测试书",
      excerpt: "要摘录的选文",
      format: "txt",
      charOffset: 100,
      scrollTop: 300
    });
  });

  it("executeSaveToInbox 通过注入的 executor 调用，不依赖 getDesktopApi", async () => {
    const executor: ExcerptCommandExecutor = {
      inboxCreate: vi.fn().mockResolvedValue({ itemId: "x1" }),
      cardCreate: vi.fn()
    };
    const result = await executeSaveToInbox(executor, baseSource);
    expect(result.success).toBe(true);
    expect(result.itemId).toBe("x1");
    expect(executor.inboxCreate).toHaveBeenCalledTimes(1);
    expect(executor.cardCreate).not.toHaveBeenCalled();
  });

  it("executeSaveToProjectCard 读取 entityId（不读 cardId）", async () => {
    const executor: ExcerptCommandExecutor = {
      inboxCreate: vi.fn(),
      cardCreate: vi.fn().mockResolvedValue({ entityId: "card-x", commandType: "card.create" })
    };
    const result = await executeSaveToProjectCard(executor, "p1", baseSource);
    expect(result.success).toBe(true);
    expect(result.itemId).toBe("card-x");
  });

  it("executeSaveToProjectCard 空 entityId 不假成功", async () => {
    const executor: ExcerptCommandExecutor = {
      inboxCreate: vi.fn(),
      cardCreate: vi.fn().mockResolvedValue({ cardId: "legacy" })
    };
    const result = await executeSaveToProjectCard(executor, "p1", baseSource);
    expect(result.success).toBe(false);
  });
});
