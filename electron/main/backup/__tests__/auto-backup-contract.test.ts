import { mkdirSync, mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  assertSafeBackupTarget,
  createAutoBackupScheduler,
  evaluateAutoBackup,
  isAutoBackupDue,
  shouldBackupBeforeUpgrade,
  uniqueAutoBackupRoot,
  type AutoBackupState
} from "../auto-backup";

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

const tempRoots: string[] = [];

function makeTempRoot(): string {
  const root = mkdtempSync(path.join(os.tmpdir(), "auto-backup-contract-"));
  tempRoots.push(root);
  return root;
}

afterEach(() => {
  for (const root of tempRoots.splice(0)) {
    rmSync(root, { recursive: true, force: true });
  }
});

describe("auto-backup due 判断", () => {
  it("从未成功过则到期", () => {
    expect(isAutoBackupDue(undefined, Date.now())).toBe(true);
  });

  it("距上次成功超过 24 小时则到期", () => {
    const last = new Date(Date.now() - 25 * HOUR_MS).toISOString();
    expect(isAutoBackupDue(last, Date.now(), DAY_MS)).toBe(true);
  });

  it("距上次成功正好 24 小时则到期", () => {
    const last = new Date(Date.now() - DAY_MS).toISOString();
    expect(isAutoBackupDue(last, Date.now(), DAY_MS)).toBe(true);
  });

  it("距上次成功不足 24 小时则不到期", () => {
    const last = new Date(Date.now() - 23 * HOUR_MS).toISOString();
    expect(isAutoBackupDue(last, Date.now(), DAY_MS)).toBe(false);
  });

  it("上次成功时间无效时按到期处理", () => {
    expect(isAutoBackupDue("not-a-date", Date.now(), DAY_MS)).toBe(true);
  });
});

describe("evaluateAutoBackup", () => {
  it("未启用则跳过", () => {
    const evaluation = evaluateAutoBackup({ enabled: false, backupDirectory: "D:/backup" }, Date.now(), DAY_MS);
    expect(evaluation).toEqual({ shouldRun: false, reason: "disabled" });
  });

  it("启用但没有目录则跳过", () => {
    const evaluation = evaluateAutoBackup({ enabled: true }, Date.now(), DAY_MS);
    expect(evaluation).toEqual({ shouldRun: false, reason: "no-directory" });
  });

  it("启用、有目录但未到期则跳过", () => {
    const state: AutoBackupState = {
      enabled: true,
      backupDirectory: "D:/backup",
      lastAutoBackupAt: new Date(Date.now() - 1 * HOUR_MS).toISOString()
    };
    expect(evaluateAutoBackup(state, Date.now(), DAY_MS)).toEqual({ shouldRun: false, reason: "not-due" });
  });

  it("启用、有目录且到期则运行", () => {
    const state: AutoBackupState = { enabled: true, backupDirectory: "D:/backup" };
    expect(evaluateAutoBackup(state, Date.now(), DAY_MS)).toEqual({ shouldRun: true, reason: "due" });
  });
});

describe("目标目录安全", () => {
  const appData = path.join("C:", "data-app", "app");
  const library = path.join("C:", "data-lib", "lib");

  it("拒绝磁盘根目录", () => {
    expect(() => assertSafeBackupTarget("C:\\", appData, library)).toThrow(/磁盘根目录/);
  });

  it("拒绝位于应用数据目录内", () => {
    expect(() => assertSafeBackupTarget(path.join(appData, "backup"), appData, library)).toThrow(/应用数据目录内/);
  });

  it("拒绝包含应用数据目录", () => {
    expect(() => assertSafeBackupTarget(path.dirname(appData), appData, library)).toThrow(/包含应用数据目录/);
  });

  it("拒绝位于书库目录内", () => {
    expect(() => assertSafeBackupTarget(path.join(library, "backup"), appData, library)).toThrow(/书库目录内/);
  });

  it("拒绝包含书库目录", () => {
    expect(() => assertSafeBackupTarget(path.dirname(library), appData, library)).toThrow(/包含书库目录/);
  });

  it("合法目录通过并返回规范化路径", () => {
    const resolved = assertSafeBackupTarget("D:\\MyBackups", appData, library);
    expect(path.isAbsolute(resolved)).toBe(true);
  });

  it("错误信息不泄露内部绝对路径", () => {
    try {
      assertSafeBackupTarget(path.join(appData, "backup"), appData, library);
      throw new Error("should not reach");
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      expect(message).not.toContain(appData);
      expect(message).not.toContain("at ");
    }
  });
});

describe("唯一时间戳备份目录", () => {
  it("同秒冲突时追加序号去重", () => {
    const root = makeTempRoot();
    const first = uniqueAutoBackupRoot(root, new Date(2026, 0, 1, 10, 30, 0));
    mkdirSync(first, { recursive: true });
    const second = uniqueAutoBackupRoot(root, new Date(2026, 0, 1, 10, 30, 0));
    expect(second).toBe(`${first}-2`);
    expect(first).toMatch(/CreationReadingAssistant-backup-20260101-103000$/);
  });
});

describe("shouldBackupBeforeUpgrade", () => {
  it("未启用或未配置目录时不触发升级前备份", () => {
    expect(shouldBackupBeforeUpgrade({ enabled: false, backupDirectory: "D:/backup" })).toBe(false);
    expect(shouldBackupBeforeUpgrade({ enabled: true })).toBe(false);
  });

  it("启用且配置目录时触发", () => {
    expect(shouldBackupBeforeUpgrade({ enabled: true, backupDirectory: "D:/backup" })).toBe(true);
  });
});

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void } {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("调度器", () => {
  it("启动后检查一次（到期时执行备份），dispose 后不再触发", async () => {
    const root = makeTempRoot();
    let backupCalls = 0;
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: true, backupDirectory: path.join(root, "backups") }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async ({ backupRoot }) => {
        backupCalls += 1;
        mkdirSync(backupRoot, { recursive: true });
      },
      onSuccess: async () => undefined,
      onFailure: async () => undefined,
      now: () => new Date(2026, 0, 1, 12, 0, 0),
      initialDelayMs: 5,
      checkIntervalMs: 10_000
    });
    scheduler.start();
    await new Promise((resolve) => setTimeout(resolve, 80));
    expect(backupCalls).toBe(1);
    scheduler.dispose();
    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(backupCalls).toBe(1);
  });

  it("启动检查未到期时不执行备份", async () => {
    const root = makeTempRoot();
    let backupCalls = 0;
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({
        enabled: true,
        backupDirectory: path.join(root, "backups"),
        lastAutoBackupAt: new Date(Date.now() - 1 * HOUR_MS).toISOString()
      }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async () => {
        backupCalls += 1;
      },
      onSuccess: async () => undefined,
      onFailure: async () => undefined,
      initialDelayMs: 5,
      checkIntervalMs: 10_000
    });
    scheduler.start();
    await new Promise((resolve) => setTimeout(resolve, 80));
    expect(backupCalls).toBe(0);
    scheduler.dispose();
  });

  it("进程内防重入：并发 runNow 只执行一次备份", async () => {
    const root = makeTempRoot();
    let backupCalls = 0;
    const gate = deferred<void>();
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: true, backupDirectory: path.join(root, "backups") }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async ({ backupRoot }) => {
        backupCalls += 1;
        mkdirSync(backupRoot, { recursive: true });
        await gate.promise;
      },
      onSuccess: async () => undefined,
      onFailure: async () => undefined,
      now: () => new Date(2026, 0, 1, 12, 0, 0)
    });
    const first = scheduler.runNow();
    const second = scheduler.runNow();
    gate.resolve();
    const [a, b] = await Promise.all([first, second]);
    expect(backupCalls).toBe(1);
    expect(a.backupRoot).toBe(b.backupRoot);
  });

  it("成功时持久化最近成功时间", async () => {
    const root = makeTempRoot();
    const successes: string[] = [];
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: true, backupDirectory: path.join(root, "backups") }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async ({ backupRoot }) => mkdirSync(backupRoot, { recursive: true }),
      onSuccess: async (at) => successes.push(at),
      onFailure: async () => undefined,
      now: () => new Date("2026-01-01T12:00:00.000Z")
    });
    const result = await scheduler.runNow();
    expect(result.createdAt).toBe("2026-01-01T12:00:00.000Z");
    expect(successes).toEqual(["2026-01-01T12:00:00.000Z"]);
  });

  it("失败时持久化可读错误且不泄露堆栈", async () => {
    const root = makeTempRoot();
    const failures: Array<{ at: string; message: string }> = [];
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: true, backupDirectory: path.join(root, "backups") }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async () => {
        throw new Error("磁盘写入失败");
      },
      onSuccess: async () => undefined,
      onFailure: async (at, message) => failures.push({ at, message }),
      now: () => new Date("2026-01-01T12:00:00.000Z")
    });
    await expect(scheduler.runNow()).rejects.toThrow("磁盘写入失败");
    expect(failures).toHaveLength(1);
    expect(failures[0]!.message).toBe("磁盘写入失败");
    expect(failures[0]!.message).not.toContain("at ");
    expect(failures[0]!.message).not.toContain(root);
  });

  it("升级前：未启用或未配置目录时直接放行", async () => {
    const root = makeTempRoot();
    let backupCalls = 0;
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: false }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async () => {
        backupCalls += 1;
      },
      onSuccess: async () => undefined,
      onFailure: async () => undefined
    });
    await scheduler.backupBeforeUpgrade();
    expect(backupCalls).toBe(0);
  });

  it("升级前：备份先于打开下载，失败则阻断打开", async () => {
    const root = makeTempRoot();
    const order: string[] = [];
    let failBackup = false;
    const scheduler = createAutoBackupScheduler({
      readState: async () => ({ enabled: true, backupDirectory: path.join(root, "backups") }),
      resolveRoots: () => ({ appDataRoot: path.join(root, "app"), libraryRoot: path.join(root, "lib") }),
      runBackup: async ({ backupRoot }) => {
        order.push("backup");
        mkdirSync(backupRoot, { recursive: true });
        if (failBackup) throw new Error("升级前备份失败");
      },
      onSuccess: async () => undefined,
      onFailure: async () => undefined,
      now: () => new Date(2026, 0, 1, 12, 0, 0)
    });

    await scheduler.backupBeforeUpgrade();
    order.push("open");
    expect(order).toEqual(["backup", "open"]);

    order.length = 0;
    failBackup = true;
    await expect(scheduler.backupBeforeUpgrade()).rejects.toThrow("升级前备份失败");
    expect(order).toEqual(["backup"]);
  });
});
