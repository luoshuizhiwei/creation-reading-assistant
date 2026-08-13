import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { cp, mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { getLegacyMigrationStatus, runLegacyMigration } from "./index";
import { openCreationWorkspace, type CreationWorkspace, type InboxItem } from "../creation-workspace";

async function writeJson(filePath: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function createLegacyFixture(dataRoot: string): Promise<void> {
  await writeJson(path.join(dataRoot, "app-settings.json"), { version: 1, appearance: {} });
  await writeJson(path.join(dataRoot, "inspirations.json"), {
    version: 1,
    items: [
      {
        id: "insp-1",
        title: "测试灵感一",
        body: "正文_SENTINEL_BODY_1",
        type: "plot",
        status: "reviewing",
        tags: ["标签A"],
        platformTags: ["起点"],
        variants: [{ content: "VARIANT_SENTINEL_1", prompt: "PROMPT_SENTINEL", model: "MODEL_SENTINEL" }],
        source: { bookTitle: "测试 EPUB", createdAt: "2026-01-01T00:00:00.000Z" }
      },
      {
        id: "insp-2",
        title: "测试灵感二",
        body: "正文_SENTINEL_BODY_2",
        variants: []
      },
      { id: "insp-broken" }
    ]
  });
  await mkdir(path.join(dataRoot, "AppLibrary"), { recursive: true });
  await writeFile(path.join(dataRoot, "ai-secrets.json"), "SENTINEL_SECRET", "utf8");
  await writeJson(path.join(dataRoot, "AppLibrary", "library.json"), {
    books: [{ id: "book-1", title: "测试 EPUB", format: "epub" }]
  });
  await writeJson(path.join(dataRoot, "AppLibrary", "highlights.json"), { version: 1, items: [] });
  await writeJson(path.join(dataRoot, "AppLibrary", "bookmarks.json"), { version: 1, items: [] });
  await writeJson(path.join(dataRoot, "AppLibrary", "reading-progress.json"), { version: 2, items: [] });
  await mkdir(path.join(dataRoot, "AppLibrary", "files"), { recursive: true });
  await writeFile(path.join(dataRoot, "AppLibrary", "files", "fixture.bin"), "file", "utf8");
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-migration-"));
  try {
    // ---- 场景 1：全新迁移（目标 store 不存在 → staging 构建 + rename + 激活） ----
    const fresh = path.join(parent, "fresh");
    await createLegacyFixture(fresh);
    const report = await runLegacyMigration({ dataRoot: fresh, minFreeBytes: 1 });
    assert.equal(report.activated, true);
    assert.equal(report.targetStore.wasFresh, true);
    assert.equal(report.sources.discovered, 3);
    assert.equal(report.sources.migrated, 2);
    assert.equal(report.sources.failed, 1);
    assert.equal(report.failures[0]?.legacyId, "insp-broken");
    assert.equal(report.backup.files > 0, true);
    assert.equal(report.backup.checksumVerified, true);
    assert.equal(report.targetStore.integrityOk, true);
    await stat(path.join(fresh, "CreationWorkspace", "activated.json"));
    await stat(path.join(fresh, "CreationWorkspace", "migration-report.json"));
    await stat(path.join(fresh, "CreationWorkspace", "migration-id-map.json"));
    const migrationIdMap = JSON.parse(await readFile(path.join(fresh, "CreationWorkspace", "migration-id-map.json"), "utf8")) as {
      entries: Record<string, string>;
    };
    assert.equal(Object.keys(migrationIdMap.entries).length, 2);
    // 旧数据保持只读不动
    const legacy = JSON.parse(await readFile(path.join(fresh, "inspirations.json"), "utf8")) as { items: unknown[] };
    assert.equal(legacy.items.length, 3);
    // 备份 manifest 存在且可解析
    const manifestPath = path.join(report.backup.directory, "manifest.json");
    const manifest = JSON.parse(await readFile(manifestPath, "utf8")) as { formatVersion: number; files: Array<{ path: string; sha256?: string }> };
    assert.equal(manifest.formatVersion, 1);
    assert.equal(manifest.files.some((file) => file.path === "inspirations.json" && typeof file.sha256 === "string"), true);
    // 收件箱内容校验
    const workspace = await openCreationWorkspace({ directory: path.join(fresh, "CreationWorkspace") }) as CreationWorkspace;
    const inbox = (await workspace.read({ kind: "inbox.list", limit: 100 })) as InboxItem[];
    assert.equal(inbox.length, 2);
    const first = inbox.find((item) => item.legacyId === "insp-1")!;
    assert.equal(first.title, "测试灵感一");
    assert.equal(createHash("sha256").update(first.body).digest("hex"), createHash("sha256").update("正文_SENTINEL_BODY_1").digest("hex"));
    assert.equal(first.variants.length, 1);
    assert.equal(first.tags.join(","), "标签A");
    assert.equal(first.platformTags.join(","), "起点");
    assert.equal(first.status, "reviewing");
    assert.equal((first.source as { bookTitle?: string }).bookTitle, "测试 EPUB");
    const second = inbox.find((item) => item.legacyId === "insp-2")!;
    assert.equal(second.variants.length, 0);
    await workspace.close();

    // ---- 场景 2：幂等——已激活后重跑返回现有报告且不重复写入 ----
    const reportAgain = await runLegacyMigration({ dataRoot: fresh, minFreeBytes: 1 });
    assert.equal(reportAgain.sources.migrated, 2);
    const workspace2 = await openCreationWorkspace({ directory: path.join(fresh, "CreationWorkspace") }) as CreationWorkspace;
    const inboxAgain = (await workspace2.read({ kind: "inbox.list", limit: 100 })) as InboxItem[];
    assert.equal(inboxAgain.length, 2);
    await workspace2.close();

    // ---- 场景 3：目标 store 已存在（用户已用过新工作台）→ 直接合并迁移 ----
    const existing = path.join(parent, "existing-store");
    await createLegacyFixture(existing);
    await openCreationWorkspace({ directory: path.join(existing, "CreationWorkspace") });
    const mergeReport = await runLegacyMigration({ dataRoot: existing, minFreeBytes: 1 });
    assert.equal(mergeReport.activated, true);
    assert.equal(mergeReport.targetStore.wasFresh, false);
    const workspace3 = await openCreationWorkspace({ directory: path.join(existing, "CreationWorkspace") }) as CreationWorkspace;
    const inbox3 = (await workspace3.read({ kind: "inbox.list", limit: 100 })) as InboxItem[];
    assert.equal(inbox3.length, 2);
    await workspace3.close();

    // ---- 场景 4：预检失败（坏 JSON + 不可回退）→ 抛错且不留半迁移状态 ----
    const broken = path.join(parent, "broken");
    await mkdir(broken, { recursive: true });
    await writeFile(path.join(broken, "inspirations.json"), "{broken", "utf8");
    await writeFile(path.join(broken, "inspirations.json.bak"), "{also-broken", "utf8");
    let brokenError: unknown;
    try {
      await runLegacyMigration({ dataRoot: broken, minFreeBytes: 1 });
    } catch (error) {
      brokenError = error;
    }
    assert.equal(brokenError instanceof Error, true);
    assert.equal((brokenError as Error).message.includes("预检"), true);
    const brokenEntries = await readdir(broken);
    assert.equal(brokenEntries.includes("CreationWorkspace"), false);
    assert.equal(brokenEntries.some((name) => name.startsWith(".creation-staging-")), false);

    // ---- 场景 5：状态查询（未激活 / 已激活） ----
    const missing = path.join(parent, "missing");
    const statusMissing = await getLegacyMigrationStatus({ dataRoot: missing });
    assert.equal(statusMissing.activated, false);
    assert.equal(statusMissing.canProceed, false);
    assert.equal(statusMissing.blockingReasons.length > 0, true);
    const statusActivated = await getLegacyMigrationStatus({ dataRoot: fresh });
    assert.equal(statusActivated.activated, true);
    assert.equal(statusActivated.activation?.migrated, 2);
    assert.equal(statusActivated.report?.activated, true);

    // ---- 场景 6：真实形态数据——完整字段（来源/locator/多候选/书库资产）全部保留 ----
    const realistic = path.join(parent, "realistic");
    await mkdir(realistic, { recursive: true });
    await writeJson(path.join(realistic, "inspirations.json"), {
      version: 1,
      items: [
        {
          id: "insp-real-1",
          title: "真实灵感",
          body: "真实正文_SENTINEL",
          type: "plot",
          status: "polished",
          tags: ["剧情", "高亮"],
          platformTags: ["番茄", "起点"],
          source: {
            bookId: "book-real-1",
            bookTitle: "测试 TXT",
            format: "txt",
            chapterTitle: "第一章",
            locationLabel: "12.3%",
            progressPercent: 12.3,
            excerpt: "来源摘录",
            locator: { bookId: "book-real-1", kind: "txt", offset: 1234 },
            createdAt: "2026-01-02T00:00:00.000Z"
          },
          revision: 5,
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-01-03T00:00:00.000Z",
          variants: [
            { id: "v1", kind: "polish", content: "候选一", prompt: "润色", model: "m1" },
            { id: "v2", kind: "expand", content: "候选二", prompt: "扩写", model: "m2" },
            { id: "v3", kind: "platform-style", content: "候选三", prompt: "平台风", model: "m3" }
          ]
        }
      ]
    });
    await mkdir(path.join(realistic, "AppLibrary", "files"), { recursive: true });
    await mkdir(path.join(realistic, "AppLibrary", "covers"), { recursive: true });
    await writeFile(path.join(realistic, "AppLibrary", "files", "book-real-1.txt"), "测试书库文件内容", "utf8");
    await writeFile(path.join(realistic, "AppLibrary", "covers", "book-real-1.jpg"), "cover", "utf8");
    await writeJson(path.join(realistic, "AppLibrary", "library.json"), {
      books: [{ id: "book-real-1", title: "测试 TXT", format: "txt" }]
    });
    const realisticReport = await runLegacyMigration({ dataRoot: realistic, minFreeBytes: 1 });
    assert.equal(realisticReport.sources.migrated, 1);
    const realisticWorkspace = await openCreationWorkspace({ directory: path.join(realistic, "CreationWorkspace") }) as CreationWorkspace;
    const realisticInbox = (await realisticWorkspace.read({ kind: "inbox.list", limit: 10 })) as Array<{
      legacyId: string | null;
      title: string;
      type: string;
      status: string;
      tags: string[];
      platformTags: string[];
      source: Record<string, unknown> | null;
      variants: Array<Record<string, unknown>>;
    }>;
    const item = realisticInbox.find((entry) => entry.legacyId === "insp-real-1")!;
    assert.equal(item.type, "plot");
    assert.equal(item.status, "polished");
    assert.deepEqual(item.tags, ["剧情", "高亮"]);
    assert.deepEqual(item.platformTags, ["番茄", "起点"]);
    assert.equal(item.variants.length, 3);
    assert.equal(item.variants[2]!.kind, "platform-style");
    assert.equal((item.source as { locator?: { offset?: number } }).locator?.offset, 1234);
    assert.equal((item.source as { progressPercent?: number }).progressPercent, 12.3);
    // 书库资产保留在备份中
    const backupFiles = JSON.parse(await readFile(path.join(realisticReport.backup.directory, "manifest.json"), "utf8")) as {
      files: Array<{ path: string }>;
    };
    assert.equal(backupFiles.files.some((file) => file.path === "AppLibrary/files/book-real-1.txt"), true);
    assert.equal(backupFiles.files.some((file) => file.path === "AppLibrary/covers/book-real-1.jpg"), true);
    await realisticWorkspace.close();

    // ---- 场景 7：备份回退演练——删除激活状态后从备份恢复，可重新迁移 ----
    const rollback = path.join(parent, "rollback");
    await mkdir(rollback, { recursive: true });
    await writeJson(path.join(rollback, "inspirations.json"), {
      version: 1,
      items: [{ id: "insp-rb-1", title: "回退灵感", body: "回退正文" }]
    });
    const rbReport = await runLegacyMigration({ dataRoot: rollback, minFreeBytes: 1 });
    assert.equal(rbReport.activated, true);
    // 模拟用户回退：删除激活指针与新建 store，从备份恢复旧数据
    await rm(path.join(rollback, "CreationWorkspace"), { recursive: true, force: true });
    const backupRoot = rbReport.backup.directory;
    await cp(backupRoot, rollback, { recursive: true, filter: (source) => !path.basename(source).startsWith("manifest") });
    const restoredInspirations = JSON.parse(await readFile(path.join(rollback, "inspirations.json"), "utf8")) as { items: unknown[] };
    assert.equal(restoredInspirations.items.length, 1);
    // 恢复后可重新迁移（原旧数据仍在）
    const reRun = await runLegacyMigration({ dataRoot: rollback, minFreeBytes: 1 });
    assert.equal(reRun.sources.migrated, 1);

    // ---- 场景 8：空正文旧灵感允许迁移（审计与执行契约一致） ----
    const emptyBody = path.join(parent, "empty-body");
    await mkdir(emptyBody, { recursive: true });
    await writeJson(path.join(emptyBody, "inspirations.json"), {
      version: 1,
      items: [
        {
          id: "insp-empty-1",
          title: "只有标题的灵感",
          body: "",
          type: "character",
          status: "inbox",
          tags: ["空正文"],
          platformTags: ["番茄"],
          variants: [{ id: "v1", kind: "polish", content: "候选仍然在", prompt: "p", model: "m" }],
          source: { bookTitle: "测试书", createdAt: "2026-01-01T00:00:00.000Z" }
        }
      ]
    });
    const emptyReport = await runLegacyMigration({ dataRoot: emptyBody, minFreeBytes: 1 });
    assert.equal(emptyReport.activated, true);
    assert.equal(emptyReport.sources.migrated, 1);
    assert.equal(emptyReport.sources.failed, 0);
    const emptyWs = await openCreationWorkspace({ directory: path.join(emptyBody, "CreationWorkspace") }) as CreationWorkspace;
    const emptyInbox = (await emptyWs.read({ kind: "inbox.list", limit: 10 })) as Array<{
      legacyId: string | null;
      title: string;
      body: string;
      type: string;
      status: string;
      tags: string[];
      platformTags: string[];
      source: Record<string, unknown> | null;
      variants: Array<Record<string, unknown>>;
    }>;
    assert.equal(emptyInbox.length, 1);
    const migratedEmpty = emptyInbox[0]!;
    assert.equal(migratedEmpty.legacyId, "insp-empty-1");
    assert.equal(migratedEmpty.title, "只有标题的灵感");
    assert.equal(migratedEmpty.body, "");
    assert.equal(migratedEmpty.type, "character");
    assert.equal(migratedEmpty.status, "inbox");
    assert.deepEqual(migratedEmpty.tags, ["空正文"]);
    assert.deepEqual(migratedEmpty.platformTags, ["番茄"]);
    assert.equal(migratedEmpty.variants.length, 1);
    assert.equal((migratedEmpty.source as { bookTitle?: string }).bookTitle, "测试书");
    await emptyWs.close();

    // ---- 场景 9：超过 500 条旧灵感全量迁移（去重、分页校验、幂等） ----
    const bigCount = 520;
    const bigRoot = path.join(parent, "over-500");
    await mkdir(bigRoot, { recursive: true });
    const bigItems: Array<Record<string, unknown>> = [];
    for (let i = 0; i < bigCount; i++) {
      bigItems.push({
        id: `insp-big-${i}`,
        title: `超量灵感${i}`,
        body: `正文_${i}_SENTINEL`,
        type: "plot",
        status: "inbox",
        tags: [`标签${i % 5}`],
        platformTags: [],
        variants: i % 3 === 0 ? [{ id: `v-${i}`, kind: "polish", content: `候选${i}`, prompt: "p", model: "m" }] : [],
        source: null
      });
    }
    await writeJson(path.join(bigRoot, "inspirations.json"), { version: 1, items: bigItems });
    const bigReport = await runLegacyMigration({ dataRoot: bigRoot, minFreeBytes: 1 });
    assert.equal(bigReport.activated, true);
    assert.equal(bigReport.sources.migrated, bigCount);
    assert.equal(bigReport.sources.failed, 0);
    // 重新打开后用分页读取全量，确认超过 500 条都在
    const bigWs = await openCreationWorkspace({ directory: path.join(bigRoot, "CreationWorkspace") }) as CreationWorkspace;
    const page1 = (await bigWs.read({ kind: "inbox.list", limit: 200, offset: 0 })) as InboxItem[];
    const page2 = (await bigWs.read({ kind: "inbox.list", limit: 200, offset: 200 })) as InboxItem[];
    const page3 = (await bigWs.read({ kind: "inbox.list", limit: 200, offset: 400 })) as InboxItem[];
    assert.equal(page1.length, 200);
    assert.equal(page2.length, 200);
    assert.equal(page3.length, 120);
    const all = [...page1, ...page2, ...page3];
    assert.equal(all.length, bigCount);
    // 正文哈希校验：随便挑几条对照
    const sampled = [all[0]!, all[123]!, all[519]!];
    for (const entry of sampled) {
      const expected = `正文_${Number(entry.legacyId!.replace("insp-big-", ""))}_SENTINEL`;
      assert.equal(createHash("sha256").update(entry.body).digest("hex"), createHash("sha256").update(expected).digest("hex"));
    }
    // 幂等：重跑不产生重复条目
    const bigAgain = await runLegacyMigration({ dataRoot: bigRoot, minFreeBytes: 1 });
    assert.equal(bigAgain.sources.migrated, bigCount);
    const bigWs2 = await openCreationWorkspace({ directory: path.join(bigRoot, "CreationWorkspace") }) as CreationWorkspace;
    const recheckPage1 = (await bigWs2.read({ kind: "inbox.list", limit: 200, offset: 0 })) as InboxItem[];
    assert.equal(recheckPage1.length, 200);
    const recheckPage2 = (await bigWs2.read({ kind: "inbox.list", limit: 200, offset: 200 })) as InboxItem[];
    const recheckPage3 = (await bigWs2.read({ kind: "inbox.list", limit: 200, offset: 400 })) as InboxItem[];
    const recheckAll = [...recheckPage1, ...recheckPage2, ...recheckPage3];
    assert.equal(recheckAll.length, bigCount);
    await bigWs.close();
    await bigWs2.close();

    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 9 })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
