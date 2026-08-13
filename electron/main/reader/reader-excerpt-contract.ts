import { strict as assert } from "node:assert";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { removeWithRetry } from "../creation-workspace/test-utils";
import { openCreationWorkspace, type CreationWorkspace } from "../creation-workspace/index";
import type { ExcerptSourceSnapshot } from "../../../src/types/library";
import {
  buildInboxCreateCommand,
  buildCardCreateCommand,
  EXCERPT_CARD_KIND,
  EXCERPT_INBOX_KIND,
  EXCERPT_INBOX_STATUS,
  executeSaveToInbox,
  executeSaveToProjectCard,
  sourceToSnapshot,
  type ExcerptCommandExecutor
} from "../../../src/features/library/excerpt-commands";
import {
  buildExcerptSource,
  isDuplicateExcerpt,
  makeExcerptSignature,
  EXCERPT_DEDUP_WINDOW_MS,
  type ExcerptBuildContext
} from "../../../src/features/library/excerpt-source";

/**
 * Electron 工作区层摘录契约（三层验证中的第三层）。
 *
 * 摘录功能分为三层验证，各层职责明确、不互相冒充：
 * 1. 共享命令映射 contract（renderer 侧 vitest）：
 *    src/features/library/__tests__/excerpt-destination-impl.test.ts 验证
 *    excerpt-commands.ts 的命令构造/entityId 读取/失败归一化，与 contract 共用同一真源。
 * 2. renderer production adapter 测试（renderer 侧 vitest）：
 *    src/features/library/__tests__/excerpt-destination-impl.test.ts（adapter 分支）与
 *    src/features/library/__tests__/excerpt-destination.test.ts（useReaderExcerpt hook：
 *    首次提交调用 destination、同签名第二次拒绝、不同选文可创建、失败不记录签名）。
 * 3. 本文件（Electron 工作区 contract）：把真实 better-sqlite3 workspace 包装成
 *    ExcerptCommandExecutor，验证共享深模块 executeSaveToInbox / executeSaveToProjectCard
 *    对真实 store 的命令映射与落库结果。
 *
 * 本 contract 只覆盖第 3 层；完整 renderer → preload → IPC → workspace 链路由
 * Electron smoke 单独验证（见 scripts/visual-capture 或人工验收记录）。
 */

const baseSource: ExcerptSourceSnapshot = {
  bookId: "book-1",
  bookTitle: "测试书",
  bookAuthor: "作者",
  format: "txt",
  chapterTitle: "第一章",
  progressPercent: 0.5,
  locationLabel: "第一章 · 50%",
  excerpt: "要摘录的正文片段",
  href: undefined,
  cfi: undefined,
  charOffset: 100,
  charLength: 12,
  scrollTop: 300,
  createdAt: "2026-08-12T10:00:00.000Z"
};

/**
 * 把真实 workspace 包装成 ExcerptCommandExecutor。
 * 这样共享深模块 executeSaveToInbox / executeSaveToProjectCard 可以直接调用
 * 真实 better-sqlite3 store，验证生产 destination 的命令映射正确。
 */
function createWorkspaceExecutor(workspace: CreationWorkspace): ExcerptCommandExecutor {
  return {
    async inboxCreate(command) {
      const result = await workspace.transact({
        type: "inbox.create",
        title: command.title,
        body: command.body,
        kind: command.kind,
        status: command.status,
        tags: command.tags,
        source: command.source
      });
      return { itemId: result.itemId };
    },
    async cardCreate(command) {
      const result = await workspace.transact({
        type: "card.create",
        projectId: command.projectId,
        kind: command.kind,
        title: command.title,
        tags: command.tags,
        content: command.content
      }) as { entityId?: string; commandType?: string; cardId?: string };
      return result;
    }
  };
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "reader-excerpt-contract-"));
  let tests = 0;
  let runError: unknown;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    const directory = path.join(parent, "ws");
    const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
    const executor = createWorkspaceExecutor(workspace);
    const project = await workspace.transact({ type: "project.create", title: "项目A" });

    // ---- 1. 真实生产 destination 的命令映射：saveToInbox 发出 inbox.create ----
    await scenario("saveToInbox 通过共享深模块发出 inbox.create 并落库", async () => {
      const result = await executeSaveToInbox(executor, baseSource);
      assert.equal(result.success, true);
      assert.equal(typeof result.itemId, "string");
      assert.ok((result.itemId as string).length > 0);

      // 验证完整来源快照进入数据库
      const items = (await workspace.read({ kind: "inbox.list", limit: 10 })) as unknown as Array<Record<string, unknown>>;
      const migrated = items.find((item) => (item.title as string).startsWith("摘录："));
      assert.ok(migrated, "收件箱里应该有摘录条目");
      assert.equal((migrated!.source as { excerpt?: string }).excerpt, "要摘录的正文片段");
      assert.equal((migrated!.source as { bookTitle?: string }).bookTitle, "测试书");
      assert.equal((migrated!.source as { format?: string }).format, "txt");
      assert.equal((migrated!.source as { charOffset?: number }).charOffset, 100);
      assert.equal((migrated!.source as { scrollTop?: number }).scrollTop, 300);
    });

    // ---- 2. 真实生产 destination 的命令映射：saveToProjectCard 发出 card.create ----
    await scenario("saveToProjectCard 通过共享深模块发出 card.create，kind=reference，读取 entityId", async () => {
      const result = await executeSaveToProjectCard(executor, project.projectId, baseSource);
      assert.equal(result.success, true);
      assert.equal(typeof result.itemId, "string");
      assert.ok((result.itemId as string).length > 0);

      // 真实读取卡片验证类型是 reference（不是 excerpt）
      const cards = await workspace.read({ kind: "cards.list", projectId: project.projectId }) as Array<{ id: string; kind: string; title: string }>;
      const card = cards.find((entry) => entry.id === result.itemId);
      assert.ok(card, "项目里应该有这张资料卡");
      assert.equal(card!.kind, EXCERPT_CARD_KIND);
      assert.equal(card!.title, "摘录：测试书");
    });

    // ---- 3. 空 entityId 不假成功 ----
    await scenario("空 entityId 不假成功（项目不存在）", async () => {
      const result = await executeSaveToProjectCard(executor, "project-nonexistent", baseSource);
      assert.equal(result.success, false);
      assert.ok(result.error !== undefined && result.error.length > 0);
    });

    // ---- 4. 命令构造：buildInboxCreateCommand / buildCardCreateCommand 字段正确 ----
    await scenario("命令构造字段正确：tags、kind、title、source", async () => {
      const inboxCmd = buildInboxCreateCommand(baseSource);
      assert.equal(inboxCmd.type, "inbox.create");
      assert.equal(inboxCmd.kind, EXCERPT_INBOX_KIND);
      assert.equal(inboxCmd.status, EXCERPT_INBOX_STATUS);
      assert.equal(inboxCmd.title, "摘录：测试书");
      assert.deepEqual(inboxCmd.tags, ["摘录", "TXT"]);
      assert.equal(inboxCmd.source.excerpt, "要摘录的正文片段");

      const cardCmd = buildCardCreateCommand(project.projectId, baseSource);
      assert.equal(cardCmd.type, "card.create");
      assert.equal(cardCmd.kind, EXCERPT_CARD_KIND);
      assert.equal(cardCmd.projectId, project.projectId);
      assert.deepEqual(cardCmd.tags, ["摘录", "TXT"]);
    });

    // ---- 5. 旧字段 cardId 被忽略，只认 entityId ----
    await scenario("命令结果只读 entityId，不读旧字段 cardId", async () => {
      // 构造一个 mock executor 返回 cardId（旧契约），不返回 entityId
      const mockExecutor: ExcerptCommandExecutor = {
        async inboxCreate() { return { itemId: "x" }; },
        async cardCreate() { return { cardId: "legacy-id", commandType: "card.create" }; }
      };
      const result = await executeSaveToProjectCard(mockExecutor, "any-project", baseSource);
      assert.equal(result.success, false);
      assert.ok(result.error?.includes("空 ID"));
    });

    // ---- 6. 共享命令映射：workspace executor 对真实 store 的落库与失败归一化 ----
    await scenario("executeSaveToInbox / executeSaveToProjectCard 通过共享模块对真实 store 落库", async () => {
      let inboxCallCount = 0;
      let cardCallCount = 0;
      const countingExecutor: ExcerptCommandExecutor = {
        async inboxCreate(command) {
          inboxCallCount += 1;
          await workspace.transact({
            type: "inbox.create",
            title: command.title,
            body: command.body,
            kind: command.kind,
            status: command.status,
            tags: command.tags,
            source: command.source
          });
          return { itemId: `inbox-${inboxCallCount}` };
        },
        async cardCreate(command) {
          cardCallCount += 1;
          const result = await workspace.transact({
            type: "card.create",
            projectId: command.projectId,
            kind: command.kind,
            title: command.title,
            tags: command.tags,
            content: command.content
          }) as { entityId?: string };
          return { entityId: result.entityId };
        }
      };

      // 首次提交：调用 destination 并落库
      const firstResult = await executeSaveToInbox(countingExecutor, baseSource);
      assert.equal(firstResult.success, true);
      assert.equal(inboxCallCount, 1);

      // 不同选文：签名不同 → 正常创建到真实项目
      const differentSource: ExcerptSourceSnapshot = { ...baseSource, excerpt: "完全不同的另一段选文" };
      const differentResult = await executeSaveToProjectCard(countingExecutor, project.projectId, differentSource);
      assert.equal(differentResult.success, true);
      assert.equal(cardCallCount, 1);

      // 落库确认：收件箱与资料卡都已写入真实 store
      const inboxItems = await workspace.read({ kind: "inbox.list", limit: 50 });
      assert.ok(inboxItems.some((item) => item.title === "摘录：测试书"), "收件箱应包含摘录条目");
      const cards = await workspace.read({ kind: "cards.list", projectId: project.projectId });
      // scenario 2 已创建一张「摘录：测试书」卡，这里再创建一张不同选文的卡
      assert.ok(cards.length >= 2, `应包含两张资料卡，实际 ${cards.length}`);
      assert.ok(cards.some((card) => card.kind === "reference"), "资料卡类型应为 reference");
      const createdCard = cards.find((card) => card.title === "摘录：测试书");
      assert.ok(createdCard, "应包含摘录资料卡");
      // 失败归一化：空 entityId 不假成功
      const emptyResult = await executeSaveToProjectCard(
        { ...countingExecutor, cardCreate: async () => ({}) },
        project.projectId,
        differentSource
      );
      assert.equal(emptyResult.success, false);
      assert.ok(emptyResult.error?.includes("空 ID"));

      // 注意：重复摘录去重（同签名第二次拒绝 / 失败不记录签名）由 renderer 侧
      // useReaderExcerpt 真实 hook 测试覆盖（excerpt-destination.test.ts），
      // 不在本 contract 中手工复刻 if (isDuplicateExcerpt(...)) 分支。
    });

    // ---- 7. sourceToSnapshot 完整性（与 contract 共用同一函数）----
    await scenario("sourceToSnapshot 保留所有来源字段", async () => {
      const snapshot = sourceToSnapshot(baseSource);
      assert.equal(snapshot.bookId, "book-1");
      assert.equal(snapshot.bookTitle, "测试书");
      assert.equal(snapshot.bookAuthor, "作者");
      assert.equal(snapshot.format, "txt");
      assert.equal(snapshot.chapterTitle, "第一章");
      assert.equal(snapshot.progressPercent, 0.5);
      assert.equal(snapshot.locationLabel, "第一章 · 50%");
      assert.equal(snapshot.excerpt, "要摘录的正文片段");
      assert.equal(snapshot.charOffset, 100);
      assert.equal(snapshot.charLength, 12);
      assert.equal(snapshot.scrollTop, 300);
      assert.equal(snapshot.createdAt, "2026-08-12T10:00:00.000Z");
    });

    // ---- 8. buildExcerptSource 与 sourceToSnapshot 端到端一致 ----
    await scenario("buildExcerptSource 构造的快照可通过 sourceToSnapshot 完整序列化", async () => {
      const ctx: ExcerptBuildContext = {
        bookId: "book-2",
        bookTitle: "测试书二",
        bookAuthor: "作者二",
        format: "epub",
        chapterTitle: "引子",
        progressPercent: 0.25,
        excerpt: "  EPUB 选文  ",
        href: "ch1.xhtml",
        cfi: undefined,
        charOffset: undefined,
        charLength: 9,
        scrollTop: 200,
        now: () => "2026-08-12T11:00:00.000Z"
      };
      const source = buildExcerptSource(ctx);
      assert.equal(source.excerpt, "EPUB 选文"); // trim
      assert.equal(source.locationLabel, "引子 · 25% 附近");

      const snapshot = sourceToSnapshot(source);
      assert.equal(snapshot.bookId, "book-2");
      assert.equal(snapshot.href, "ch1.xhtml");
      assert.equal(snapshot.excerpt, "EPUB 选文");
    });

    await workspace.close();

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } catch (error) {
    runError = error;
    process.stderr.write(`CONTRACT ERROR: ${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  } finally {
    try {
      // Windows 上 SQLite WAL/shm 句柄延迟释放，清理需重试避免偶发 EBUSY。
      await removeWithRetry(parent);
    } catch (cleanupError) {
      if (!runError) throw cleanupError;
      process.stderr.write(`(cleanup also failed: ${String(cleanupError)})\n`);
    }
  }
  if (runError) throw runError;
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
