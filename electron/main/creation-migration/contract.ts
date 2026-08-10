import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
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

    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 5 })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
