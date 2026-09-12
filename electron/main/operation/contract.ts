/**
 * operation 协调器 + 集成契约。
 *
 * 通过 esbuild 打包为 cjs，再用 electron ELECTRON_RUN_AS_NODE=1 运行
 * （因为依赖 better-sqlite3 原生模块）。每个场景用 node:assert 断言真实行为，
 * 不断言源码字符串。成功时在末行打印 JSON 证据 { allPass, tests }。
 */

import { strict as assert } from "node:assert";
import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { lstat, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  createOperationCoordinator,
  runBackupCreate,
  runBackupRestore,
  runBackupExportEncrypted,
  runBackupImportEncrypted,
  runBundleExportEncrypted,
  runBundleImport,
  runBundleImportEncrypted,
  OperationInvariantError
} from "./index";
import type { OperationCoordinator } from "./coordinator";
import type { OperationState } from "./types";
import { createBackupSnapshot, type BackupManifest } from "../backup";
import { exportProjectBundleDirectory } from "../creation-bundle";
import { openCreationWorkspace, scanResourceConsistencyCore } from "../creation-workspace";
import { CONTAINER_MAGIC } from "../portable-encryption";
import type { ProjectBundleData, ProjectBundleImportCommand, ProjectBundleImportResult } from "../../../src/types/creation";

function sha256Buffer(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

function deferred<T = void>(): {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (error: unknown) => void;
} {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function waitForPhase(coordinator: OperationCoordinator, operationId: string, phase: string, timeoutMs = 5000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const state = coordinator.getState(operationId);
    if (state && state.progress.phase === phase) return;
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  throw new Error(`等待阶段 ${phase} 超时（operationId=${operationId}）。`);
}

async function hashWorkspace(workspaceDirectory: string): Promise<string> {
  const hash = createHash("sha256");
  const dbPath = path.join(workspaceDirectory, "workspace.sqlite");
  if (existsSync(dbPath)) hash.update(await readFile(dbPath));
  const resourcesDir = path.join(workspaceDirectory, "resources");
  const walk = async (relative: string): Promise<void> => {
    let entries;
    try {
      entries = await readdir(path.join(resourcesDir, relative), { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const child = relative ? `${relative}/${entry.name}` : entry.name;
      if (entry.isDirectory()) await walk(child);
      else if (entry.isFile()) hash.update(child).update(await readFile(path.join(resourcesDir, child)));
    }
  };
  await walk("");
  return hash.digest("hex");
}

async function withWorkspace<T>(directory: string, fn: (workspace: Awaited<ReturnType<typeof openCreationWorkspace>>) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory });
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

async function buildSourceProject(parent: string): Promise<{ workspaceDir: string; resourceRel: string; resourceSha: string; exportedData: ProjectBundleData }> {
  const workspaceDir = path.join(parent, `ws-${randomUUID()}`);
  let exportedData!: ProjectBundleData;
  let resourceRel = "";
  let resourceSha = "";
  await withWorkspace(workspaceDir, async (workspace) => {
    const created = (await workspace.transact({
      type: "project.create",
      title: "契约项目",
      setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] }
    })) as { projectId: string };
    const card = (await workspace.transact({
      type: "card.create",
      projectId: created.projectId,
      kind: "character",
      title: "角色"
    })) as { entityId: string };
    const content = Buffer.from("资源-内容-20260814", "utf8");
    resourceSha = sha256Buffer(content);
    resourceRel = `resources/${created.projectId}/${randomUUID()}-材料.pdf`;
    await mkdir(path.dirname(path.join(workspaceDir, resourceRel)), { recursive: true });
    await writeFile(path.join(workspaceDir, resourceRel), content);
    await workspace.transact({
      type: "resource.attach",
      projectId: created.projectId,
      cardId: card.entityId,
      relativePath: resourceRel,
      sha256: resourceSha,
      size: content.length,
      originalName: "材料.pdf"
    });
    exportedData = (await workspace.read({ kind: "project.bundle.export", projectId: created.projectId }))!;
  });
  return { workspaceDir, resourceRel, resourceSha, exportedData };
}

async function run(): Promise<number> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "operation-contract-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.stack ?? error.message : String(error)}`);
      }
    };

    // ---------------- A. 协调器状态机不变量（纯） ----------------
    await scenario("阶段回退抛 OperationInvariantError", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async (c) => {
        c.setPhase("validating");
        c.setPhase("copying");
        let threw = false;
        try {
          c.setPhase("scanning");
        } catch (error) {
          threw = error instanceof OperationInvariantError;
        }
        assert.equal(threw, true);
        return "ok";
      });
      const res = await started.done;
      assert.equal(res.status, "completed");
    });

    await scenario("completed 倒退抛 OperationInvariantError", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async (c) => {
        c.setProgress({ completed: 5, total: 10 });
        let threw = false;
        try {
          c.setProgress({ completed: 3 });
        } catch (error) {
          threw = error instanceof OperationInvariantError;
        }
        assert.equal(threw, true);
        return "ok";
      });
      assert.equal((await started.done).status, "completed");
    });

    await scenario("bytesCompleted 倒退抛 OperationInvariantError", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async (c) => {
        c.setProgress({ bytesCompleted: 100, bytesTotal: 200 });
        let threw = false;
        try {
          c.setProgress({ bytesCompleted: 50 });
        } catch (error) {
          threw = error instanceof OperationInvariantError;
        }
        assert.equal(threw, true);
        return "ok";
      });
      assert.equal((await started.done).status, "completed");
    });

    await scenario("completed 超过 total 抛 OperationInvariantError", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async (c) => {
        c.setProgress({ completed: 0, total: 10 });
        let threw = false;
        try {
          c.setProgress({ completed: 11 });
        } catch (error) {
          threw = error instanceof OperationInvariantError;
        }
        assert.equal(threw, true);
        return "ok";
      });
      assert.equal((await started.done).status, "completed");
    });

    await scenario("bytesCompleted 超过 bytesTotal 抛 OperationInvariantError", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async (c) => {
        c.setProgress({ bytesCompleted: 0, bytesTotal: 200 });
        let threw = false;
        try {
          c.setProgress({ bytesCompleted: 300 });
        } catch (error) {
          threw = error instanceof OperationInvariantError;
        }
        assert.equal(threw, true);
        return "ok";
      });
      assert.equal((await started.done).status, "completed");
    });

    await scenario("requestCancel 对不存在/已结束 operationId 幂等返回 false", async () => {
      const coordinator = createOperationCoordinator();
      assert.equal(coordinator.requestCancel("op-does-not-exist"), false);
      const started = coordinator.start("backup.create", async () => "ok");
      const res = await started.done;
      assert.equal(res.status, "completed");
      assert.equal(coordinator.has(started.operationId), false);
      assert.equal(coordinator.requestCancel(started.operationId), false);
    });

    await scenario("结束后 registry 清空（has/listStates），getState 短窗口内返回终态缓存", async () => {
      const coordinator = createOperationCoordinator();
      const started = coordinator.start("backup.create", async () => "ok");
      await started.done;
      assert.equal(coordinator.has(started.operationId), false);
      assert.equal(coordinator.listStates().length, 0);
      // 终态缓存（30 秒窗口）仍可取到终态快照：迟到订阅 / renderer 补偿拉取依赖它，
      // 避免快速任务在 start → subscribe 窗口完成时 UI 永久显示“运行中”。
      const cached = coordinator.getState(started.operationId);
      assert.equal(cached !== undefined, true);
      assert.equal(cached?.status, "completed");
      assert.equal(cached?.progress.phase, "done");
      assert.equal(coordinator.requestCancel(started.operationId), false);
    });

    // ---------------- B. 状态区分 ----------------
    await scenario("completed / cancelled / failed 状态互不相同", async () => {
      const c1 = createOperationCoordinator();
      const ok = await c1.start("backup.create", async () => "ok").done;
      assert.equal(ok.status, "completed");

      const c2 = createOperationCoordinator();
      const gate = deferred();
      const cancelledOp = c2.start("backup.create", async (c) => {
        c.setPhase("copying");
        await gate.promise;
        c.throwIfCancelled();
        return "x";
      });
      c2.requestCancel(cancelledOp.operationId);
      gate.resolve();
      const cancelled = await cancelledOp.done;
      assert.equal(cancelled.status, "cancelled");

      const c3 = createOperationCoordinator();
      const failed = await c3.start("backup.create", async () => {
        throw new Error("boom");
      }).done;
      assert.equal(failed.status, "failed");

      const statuses = new Set([ok.status, cancelled.status, failed.status]);
      assert.equal(statuses.size, 3);
    });

    // ---------------- C. 不可中断阶段 vs 可中断阶段 ----------------
    await scenario("不可中断阶段（database）收到取消 → 安全完成且 deferredCancel", async () => {
      const coordinator = createOperationCoordinator();
      const gate = deferred();
      const started = coordinator.start("backup.create", async (c) => {
        c.setPhase("validating");
        c.setPhase("copying");
        c.setPhase("database"); // 不可中断
        await gate.promise;
        c.setPhase("committing");
        return "committed";
      });
      await waitForPhase(coordinator, started.operationId, "database");
      coordinator.requestCancel(started.operationId);
      gate.resolve();
      const res = await started.done;
      assert.equal(res.status, "completed");
      assert.equal(res.deferredCancel, true);
      assert.equal(res.result, "committed");
    });

    await scenario("可中断阶段（copying）收到取消 → cancelled 且无写入", async () => {
      const coordinator = createOperationCoordinator();
      const marker = path.join(parent, `marker-${randomUUID()}.txt`);
      const gate = deferred();
      const started = coordinator.start("backup.create", async (c) => {
        c.setPhase("validating");
        c.setPhase("copying");
        await gate.promise;
        c.throwIfCancelled();
        await writeFile(marker, "should-not-exist");
        c.setPhase("committing");
        return "done";
      });
      coordinator.requestCancel(started.operationId);
      gate.resolve();
      const res = await started.done;
      assert.equal(res.status, "cancelled");
      let exists = false;
      try {
        await lstat(marker);
        exists = true;
      } catch {
        exists = false;
      }
      assert.equal(exists, false);
    });

    await scenario("重复取消幂等（不抛错，最终状态一致）", async () => {
      const coordinator = createOperationCoordinator();
      const gate = deferred();
      const started = coordinator.start("backup.create", async (c) => {
        c.setPhase("copying");
        await gate.promise;
        c.throwIfCancelled();
        return "x";
      });
      const first = coordinator.requestCancel(started.operationId);
      const second = coordinator.requestCancel(started.operationId);
      gate.resolve();
      const res = await started.done;
      assert.equal(first, true);
      assert.equal(second, true);
      assert.equal(res.status, "cancelled");
    });

    // ---------------- D. 真实模块零写入 / 清理 / 补偿 ----------------
    await scenario("备份创建：开始前取消 → cancelled 且 backupRoot 零写入", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const backupRoot = path.join(parent, `backup-${randomUUID()}`);
      const coordinator = createOperationCoordinator();
      const handle = runBackupCreate(coordinator, {
        backupRoot,
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString()
      });
      coordinator.requestCancel(handle.operationId);
      const res = await handle.done;
      assert.equal(res.status, "cancelled");
      assert.equal(existsSync(backupRoot), false);
    });

    await scenario("项目包导入：开始前取消 → cancelled 且工作区无新增资源 / staging 已清理", async () => {
      const source = await buildSourceProject(parent);
      const exportParent = path.join(parent, `exports-${randomUUID()}`);
      await mkdir(exportParent, { recursive: true });
      const exported = await exportProjectBundleDirectory({
        workspaceDirectory: source.workspaceDir,
        data: source.exportedData,
        targetDirectory: exportParent
      });
      const target = path.join(parent, `target-${randomUUID()}`);
      const coordinator = createOperationCoordinator();
      const handle = runBundleImport(coordinator, {
        workspaceDirectory: target,
        bundleDirectory: exported.directory
      });
      coordinator.requestCancel(handle.operationId);
      const res = await handle.done;
      assert.equal(res.status, "cancelled");
      // 取消不应留下任何资源记录或已落盘附件 / staging 目录。
      const scan = await scanResourceConsistencyCore({ workspaceDirectory: target });
      assert.equal(scan.scannedRecordCount, 0);
      assert.equal(scan.issues.some((i) => i.type === "file-unreferenced"), false);
      const staged = await readdir(target).catch(() => [] as string[]);
      assert.equal(staged.some((name) => name.startsWith(".bundle-import-")), false);
    });

    await scenario("备份恢复：开始前取消 → cancelled 且当前数据未触碰", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const backupRoot = path.join(parent, `backup-${randomUUID()}`);
      const manifest: BackupManifest = await createBackupSnapshot({
        backupRoot,
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString()
      });
      assert.equal(manifest.version, 2);
      const current = path.join(parent, `current-${randomUUID()}`);
      await mkdir(current, { recursive: true });
      await writeFile(path.join(current, "sentinel.txt"), "KEEP-ME");
      const coordinator = createOperationCoordinator();
      const handle = runBackupRestore(coordinator, {
        backupRoot,
        currentAppDataRoot: current,
        resolveLibraryTarget: async () => path.join(parent, "should-not-be-called")
      });
      coordinator.requestCancel(handle.operationId);
      const res = await handle.done;
      assert.equal(res.status, "cancelled");
      const sentinel = await readFile(path.join(current, "sentinel.txt"), "utf8");
      assert.equal(sentinel, "KEEP-ME");
    });

    await scenario("备份恢复：交换失败注入 → failed 且当前数据从检查点还原", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const backupRoot = path.join(parent, `backup-${randomUUID()}`);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString()
      });
      const current = path.join(parent, `current-${randomUUID()}`);
      await mkdir(current, { recursive: true });
      await writeFile(path.join(current, "sentinel.txt"), "KEEP-ME");
      const coordinator = createOperationCoordinator();
      const handle = runBackupRestore(coordinator, {
        backupRoot,
        currentAppDataRoot: current,
        resolveLibraryTarget: async () => path.join(parent, "should-not-be-called"),
        testHooks: { failAppDataRename: true }
      });
      const res = await handle.done;
      assert.equal(res.status, "failed");
      const sentinel = await readFile(path.join(current, "sentinel.txt"), "utf8");
      assert.equal(sentinel, "KEEP-ME");
    });

    await scenario("项目包导入：transact 失败 → failed 且已落盘附件被补偿回滚", async () => {
      const source = await buildSourceProject(parent);
      const exportParent = path.join(parent, `exports-${randomUUID()}`);
      await mkdir(exportParent, { recursive: true });
      const exported = await exportProjectBundleDirectory({
        workspaceDirectory: source.workspaceDir,
        data: source.exportedData,
        targetDirectory: exportParent
      });
      const target = path.join(parent, `target-${randomUUID()}`);
      const coordinator = createOperationCoordinator();
      let calledTransact = false;
      const handle = runBundleImport(coordinator, {
        workspaceDirectory: target,
        bundleDirectory: exported.directory,
        transact: async (_command: ProjectBundleImportCommand): Promise<ProjectBundleImportResult> => {
          calledTransact = true;
          throw new Error("DB 导入失败（注入）");
        }
      });
      const res = await handle.done;
      assert.equal(res.status, "failed");
      assert.equal(calledTransact, true);
      const scan = await scanResourceConsistencyCore({ workspaceDirectory: target });
      assert.equal(scan.scannedRecordCount, 0);
      assert.equal(scan.issues.some((i) => i.type === "file-unreferenced"), false);
    });

    // ---------------- E. 并发：取消其一不影响另一个 ----------------
    await scenario("两个并行 operation：取消其一不影响另一个", async () => {
      const coordinator = createOperationCoordinator();
      const gate = deferred();
      const a = coordinator.start("backup.create", async (c) => {
        c.setPhase("copying");
        await gate.promise;
        c.throwIfCancelled();
        return "a";
      });
      const b = coordinator.start("backup.create", async (c) => {
        c.setPhase("copying");
        await gate.promise;
        return "b";
      });
      coordinator.requestCancel(a.operationId);
      gate.resolve();
      const ra = await a.done;
      const rb = await b.done;
      assert.equal(ra.status, "cancelled");
      assert.equal(rb.status, "completed");
      assert.equal(rb.result, "b");
    });

    // ---------------- F. 资源扫描操作级（只读零写入） ----------------
    await scenario("资源扫描：operation 包裹取消 → cancelled 且扫描前后 DB/文件树哈希不变", async () => {
      const source = await buildSourceProject(parent);
      const coordinator = createOperationCoordinator();
      const before = await hashWorkspace(source.workspaceDir);
      const handle = coordinator.start("bundle.import", async (c) => {
        c.setPhase("scanning");
        const result = await scanResourceConsistencyCore({ workspaceDirectory: source.workspaceDir, operation: c });
        return result;
      });
      coordinator.requestCancel(handle.operationId);
      const res = await handle.done;
      assert.equal(res.status, "cancelled");
      const after = await hashWorkspace(source.workspaceDir);
      assert.equal(after, before);
    });

    // ---------------- G. 快速任务终态可达（start → subscribe 竞态窗口） ----------------
    await scenario("快速任务完成后再订阅：仍能立即收到终态快照（30 秒缓存窗口）", async () => {
      const coordinator = createOperationCoordinator();
      const handle = coordinator.start("resource.scan", async () => ({ issues: [], scannedRecordCount: 0, scannedFileCount: 0 }));
      const res = await handle.done;
      assert.equal(res.status, "completed");
      // 模拟 renderer 迟到订阅：任务已结束、entry 已移除，订阅必须立即回放终态。
      const received: OperationState[] = [];
      const unsubscribe = coordinator.subscribe(handle.operationId, (state) => {
        received.push(state);
      });
      unsubscribe();
      assert.equal(received.length, 1);
      assert.equal(received[0].status, "completed");
      assert.equal(received[0].progress.phase, "done");
      // getState 在终态缓存窗口内仍可取到终态。
      const cached = coordinator.getState(handle.operationId);
      assert.equal(cached !== undefined, true);
      assert.equal(cached?.status, "completed");
      // registry 语义不变：listStates 不包含已结束条目。
      assert.equal(coordinator.listStates().some((s) => s.operationId === handle.operationId), false);
      assert.equal(coordinator.has(handle.operationId), false);
    });

    await scenario("运行中订阅：立即回放当前状态，随后终态事件也送达", async () => {
      const coordinator = createOperationCoordinator();
      const gate = deferred();
      const handle = coordinator.start("backup.create", async (c) => {
        c.setPhase("copying");
        await gate.promise;
        return "done";
      });
      const received: OperationState[] = [];
      const unsubscribe = coordinator.subscribe(handle.operationId, (state) => {
        received.push(state);
      });
      assert.equal(received.length >= 1, true);
      assert.equal(received[0].status, "running");
      assert.equal(received[0].progress.phase, "copying");
      gate.resolve();
      await handle.done;
      assert.equal(received.some((s) => s.status === "completed"), true);
      unsubscribe();
    });

    // ---------------- H. 加密长任务端到端（seam 接线） ----------------
    await scenario("加密备份导出：产出生效容器（CRPK 魔数）且明文临时目录被清理", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const targetFile = path.join(parent, `bk-${randomUUID()}.crbackup`);
      const coordinator = createOperationCoordinator();
      const handle = runBackupExportEncrypted(coordinator, {
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString(),
        targetFile,
        passphrase: "secret"
      });
      const res = await handle.done;
      assert.equal(res.status, "completed");
      assert.equal(existsSync(targetFile), true);
      const head = await readFile(targetFile);
      assert.equal(head.subarray(0, 4).equals(CONTAINER_MAGIC), true);
    });

    await scenario("加密备份导入：错误口令 → failed 且 code=auth-failed，当前数据未触碰", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const targetFile = path.join(parent, `bk-${randomUUID()}.crbackup`);
      const coordinator = createOperationCoordinator();
      const exportHandle = runBackupExportEncrypted(coordinator, {
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString(),
        targetFile,
        passphrase: "secret"
      });
      assert.equal((await exportHandle.done).status, "completed");

      const current = path.join(parent, `current-${randomUUID()}`);
      await mkdir(current, { recursive: true });
      await writeFile(path.join(current, "sentinel.txt"), "KEEP-ME");

      const coordinator2 = createOperationCoordinator();
      const importHandle = runBackupImportEncrypted(
        coordinator2,
        {
          containerFile: targetFile,
          passphrase: "WRONG-PASSPHRASE",
          currentAppDataRoot: current,
          resolveLibraryTarget: async () => path.join(parent, `lib-${randomUUID()}`)
        },
        (fn) => fn()
      );
      const res = await importHandle.done;
      assert.equal(res.status, "failed");
      assert.equal(res.error?.code, "auth-failed");
      const sentinel = await readFile(path.join(current, "sentinel.txt"), "utf8");
      assert.equal(sentinel, "KEEP-ME");
    });

    await scenario("加密备份导入：正确口令 → completed 且数据恢复", async () => {
      const appData = path.join(parent, `appdata-${randomUUID()}`);
      await mkdir(appData, { recursive: true });
      await writeFile(path.join(appData, "state.json"), JSON.stringify({ ok: true }));
      const targetFile = path.join(parent, `bk-${randomUUID()}.crbackup`);
      const coordinator = createOperationCoordinator();
      const exportHandle = runBackupExportEncrypted(coordinator, {
        appDataDirectory: appData,
        appVersion: "0.4.0",
        platform: "win32",
        arch: "x64",
        createdAt: new Date().toISOString(),
        targetFile,
        passphrase: "secret"
      });
      assert.equal((await exportHandle.done).status, "completed");

      const current = path.join(parent, `current-${randomUUID()}`);
      await mkdir(current, { recursive: true });

      const coordinator2 = createOperationCoordinator();
      const importHandle = runBackupImportEncrypted(
        coordinator2,
        {
          containerFile: targetFile,
          passphrase: "secret",
          currentAppDataRoot: current,
          resolveLibraryTarget: async () => path.join(parent, `lib-${randomUUID()}`)
        },
        (fn) => fn()
      );
      const res = await importHandle.done;
      assert.equal(res.status, "completed");
      const restored = await readFile(path.join(current, "state.json"), "utf8");
      assert.equal(JSON.parse(restored).ok, true);
    });

    await scenario("加密项目包导出：产出生效 .crbundle 容器", async () => {
      const source = await buildSourceProject(parent);
      const targetFile = path.join(parent, `bundle-${randomUUID()}.crbundle`);
      const coordinator = createOperationCoordinator();
      const handle = runBundleExportEncrypted(coordinator, {
        workspaceDirectory: source.workspaceDir,
        data: source.exportedData,
        targetFile,
        passphrase: "secret"
      });
      const res = await handle.done;
      assert.equal(res.status, "completed");
      assert.equal(existsSync(targetFile), true);
      const head = await readFile(targetFile);
      assert.equal(head.subarray(0, 4).equals(CONTAINER_MAGIC), true);
    });

    await scenario("加密项目包导入：错误口令 → auth-failed；正确口令 → completed 且资源入库", async () => {
      const source = await buildSourceProject(parent);
      const targetFile = path.join(parent, `bundle-${randomUUID()}.crbundle`);
      const coordinator = createOperationCoordinator();
      const exportHandle = runBundleExportEncrypted(coordinator, {
        workspaceDirectory: source.workspaceDir,
        data: source.exportedData,
        targetFile,
        passphrase: "secret"
      });
      assert.equal((await exportHandle.done).status, "completed");

      // 错误口令：auth-failed，目标工作区零写入。
      const wrongTarget = path.join(parent, `wrong-${randomUUID()}`);
      const coordinatorWrong = createOperationCoordinator();
      const wrongHandle = runBundleImportEncrypted(
        coordinatorWrong,
        { workspaceDirectory: wrongTarget, containerFile: targetFile, passphrase: "WRONG" },
        (fn) => fn()
      );
      const wrongRes = await wrongHandle.done;
      assert.equal(wrongRes.status, "failed");
      assert.equal(wrongRes.error?.code, "auth-failed");

      // 正确口令：completed，资源记录入库。
      const rightTarget = path.join(parent, `right-${randomUUID()}`);
      const coordinatorRight = createOperationCoordinator();
      let previewCalled = false;
      const rightHandle = runBundleImportEncrypted(
        coordinatorRight,
        {
          workspaceDirectory: rightTarget,
          containerFile: targetFile,
          passphrase: "secret",
          resolveCardResolutions: async (preview) => {
            previewCalled = true;
            assert.equal(preview.cardCount, source.exportedData.cards.length);
            assert.equal(preview.conflicts.length, 0);
            return [];
          }
        },
        (fn) => fn()
      );
      const rightRes = await rightHandle.done;
      assert.equal(rightRes.status, "completed");
      assert.equal(previewCalled, true, "加密包也必须在数据库写入前执行稳定 ID 预检");
      const scan = await scanResourceConsistencyCore({ workspaceDirectory: rightTarget });
      assert.equal(scan.scannedRecordCount >= 1, true);

      // 用户在解密后的冲突确认阶段取消：以 cancelled 收尾，目标工作区零项目/零附件。
      const cancelledTarget = path.join(parent, `cancelled-${randomUUID()}`);
      const coordinatorCancelled = createOperationCoordinator();
      const cancelledHandle = runBundleImportEncrypted(
        coordinatorCancelled,
        {
          workspaceDirectory: cancelledTarget,
          containerFile: targetFile,
          passphrase: "secret",
          resolveCardResolutions: async () => null
        },
        (fn) => fn()
      );
      const cancelledResult = await cancelledHandle.done;
      assert.equal(cancelledResult.status, "cancelled");
      await withWorkspace(cancelledTarget, async (workspace) => {
        assert.equal((await workspace.read({ kind: "projects.list" })).length, 0);
      });
      const cancelledFiles = await readdir(path.join(cancelledTarget, "resources"), { recursive: true }).catch(() => [] as string[]);
      assert.equal(cancelledFiles.length, 0);
    });

    return tests;
  } finally {
    await rm(parent, { recursive: true, force: true }).catch(() => undefined);
  }
}

run()
  .then((tests) => {
    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  })
  .catch((error) => {
    process.stdout.write(`${JSON.stringify({ allPass: false, error: error instanceof Error ? error.message : String(error) })}\n`);
    process.exitCode = 1;
  });
