import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { openCreationWorkspace, type CreationWorkspace } from "../index";
import { CreationWorkspaceError } from "../types";

/**
 * 本测试在 Vitest (Node) 环境里直接打开 better-sqlite3 工作区。
 * 项目以 Electron 为运行宿主，better-sqlite3 针对 Electron ABI 编译；
 * 当宿主 Node ABI 与 better-sqlite3 ABI 不一致时（例如 Node v22=ABI127，
 * Electron 33=ABI130），Node 侧无法加载 native 模块，4 个用例会全部失败。
 *
 * 此处检测到 ABI 不匹配时改为明确 skip，并在控制台打印原因，避免把 ABI
 * 失败冒充为业务回归；业务逻辑由 npm run verify:creation-inbox-convert /
 * verify:creation-inbox-count / verify:creation-migration 在 Electron 环境
 * 下以 18 个 contract 覆盖。
 *
 * 测试放在 electron/ 侧而非 src/，避免被 beta-check 的 renderer 安全扫描
 * 误判为渲染进程直接导入特权模块。
 */
const require = createRequire(import.meta.url);
let nativeWorkspaceAvailable = true;
let nativeWorkspaceSkipReason = "";
try {
  // 直接尝试加载 better-sqlite3 的 native 二进制，触发 ABI 校验。
  // 仅 require('better-sqlite3') 不会立即加载 .node，需要 new Database() 才会触发。
  const Database = require("better-sqlite3");
  const probeDb = new Database(":memory:");
  probeDb.close();
} catch (error) {
  nativeWorkspaceAvailable = false;
  nativeWorkspaceSkipReason = error instanceof Error ? error.message : String(error);
}

const nativeWorkspaceDescribe = nativeWorkspaceAvailable ? describe : describe.skip;

if (!nativeWorkspaceAvailable) {
  // eslint-disable-next-line no-console
  console.warn(`[creation-workspace-legacy] 跳过原因：${nativeWorkspaceSkipReason}`);
}

nativeWorkspaceDescribe("creation-workspace-legacy", () => {
  let directory: string;
  let workspace: CreationWorkspace | undefined;

  beforeEach(async () => {
    directory = await mkdtemp(join(tmpdir(), "cra-inbox-test-"));
    workspace = await openCreationWorkspace({ directory });
  });

  afterEach(async () => {
    try {
      await workspace?.close();
    } catch {
      // ignore
    }
    await rm(directory, { recursive: true, force: true });
  });

  const legacyItem = {
    type: "inbox.create" as const,
    legacyId: "leg-0001",
    title: "旧灵感：反差男主",
    body: "表面温和的图书管理员，实为地下情报网核心。",
    kind: "character",
    status: "usable",
    tags: ["反差", "谍战"],
    platformTags: ["起点", "番茄"],
    source: { bookTitle: "测试书目", locationLabel: "第3章", format: "epub", excerpt: "原文摘录" },
    variants: [
      { id: "v1", kind: "polish", content: "润色后的候选", model: "gpt", createdAt: new Date().toISOString(), prompt: "p" },
      { id: "v2", kind: "expand", content: "扩写后的候选", model: "gpt", createdAt: new Date().toISOString(), prompt: "p" }
    ]
  };

  describe("收件箱 legacyId 去重（迁移幂等基础）", () => {
    it("相同 legacyId 第二次创建被拒绝（conflict）", async () => {
      await workspace!.transact(legacyItem);
      await expect(workspace!.transact(legacyItem)).rejects.toBeInstanceOf(CreationWorkspaceError);
      try {
        await workspace!.transact(legacyItem);
      } catch (error) {
        expect(error).toBeInstanceOf(CreationWorkspaceError);
        expect((error as CreationWorkspaceError).code).toBe("conflict");
      }
    });

    it("不同 legacyId 不冲突，且都保留 legacyId", async () => {
      await workspace!.transact(legacyItem);
      await workspace!.transact({ ...legacyItem, legacyId: "leg-0002", title: "第二条" });
      const items = (await workspace!.read({ kind: "inbox.list", limit: 500 })) as Array<{ legacyId: string | null }>;
      const legacyIds = items.map((item) => item.legacyId).sort();
      expect(legacyIds).toEqual(["leg-0001", "leg-0002"]);
    });
  });

  describe("旧灵感字段与候选迁移完整", () => {
    it("迁移后标题/正文/类型/状态/标签/平台标签/来源/候选全部一致", async () => {
      await workspace!.transact(legacyItem);
      const items = (await workspace!.read({ kind: "inbox.list", limit: 500 })) as Array<Record<string, unknown>>;
      expect(items).toHaveLength(1);
      const migrated = items[0]!;
      expect(migrated.legacyId).toBe("leg-0001");
      expect(migrated.title).toBe(legacyItem.title);
      expect(migrated.body).toBe(legacyItem.body);
      expect(migrated.type).toBe("character");
      expect(migrated.status).toBe("usable");
      expect(migrated.tags).toEqual(["反差", "谍战"]);
      expect(migrated.platformTags).toEqual(["起点", "番茄"]);
      expect(migrated.source).toMatchObject({ bookTitle: "测试书目", format: "epub" });
      expect(Array.isArray(migrated.variants)).toBe(true);
      expect((migrated.variants as unknown[]).length).toBe(2);
      expect((migrated.variants as Array<Record<string, unknown>>)[0]!.content).toBe("润色后的候选");
    });

    it("正文哈希（内容）与候选数可作为迁移校验基准", async () => {
      await workspace!.transact(legacyItem);
      const items = (await workspace!.read({ kind: "inbox.list", limit: 500 })) as Array<Record<string, unknown>>;
      // 迁移校验使用的三个维度：存在、正文一致、候选数一致
      const migrated = items[0]!;
      expect(migrated.body).toBe(legacyItem.body);
      expect((migrated.variants as unknown[]).length).toBe(legacyItem.variants.length);
      expect(migrated.legacyId).not.toBeNull();
    });
  });
});
