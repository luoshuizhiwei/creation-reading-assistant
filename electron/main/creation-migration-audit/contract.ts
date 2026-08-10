import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { auditLegacyDesktopData } from "./index";

async function writeJson(filePath: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function fingerprint(directory: string): Promise<Record<string, string>> {
  const result: Record<string, string> = {};
  async function visit(current: string): Promise<void> {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const absolute = path.join(current, entry.name);
      const relative = path.relative(directory, absolute).replaceAll("\\", "/");
      if (entry.isDirectory()) await visit(absolute);
      else if (entry.isFile()) {
        const data = await readFile(absolute);
        const info = await stat(absolute);
        result[relative] = `${info.size}:${createHash("sha256").update(data).digest("hex")}`;
      }
    }
  }
  await visit(directory);
  return result;
}

async function createValidFixture(root: string): Promise<void> {
  await writeJson(path.join(root, "app-settings.json"), { version: 1, appearance: {} });
  await writeJson(path.join(root, "inspirations.json"), {
    version: 1,
    items: [
      {
        id: "insp-1",
        title: "测试灵感",
        body: "正文_SENTINEL_BODY",
        variants: [{ content: "VARIANT_SENTINEL", prompt: "PROMPT_SENTINEL", model: "MODEL_SENTINEL" }],
        source: { bookTitle: "测试 EPUB" }
      }
    ]
  });
  await mkdir(path.join(root, "AppLibrary"), { recursive: true });
  await writeFile(path.join(root, "ai-secrets.json"), "SENTINEL_SECRET", "utf8");
  await writeJson(path.join(root, "AppLibrary", "library.json"), {
    books: [
      { id: "book-1", title: "测试 EPUB", format: "epub" },
      { id: "book-2", title: "测试 TXT", format: "txt" }
    ]
  });
  await writeJson(path.join(root, "AppLibrary", "highlights.json"), { version: 1, items: [{ id: "h-1" }] });
  await writeJson(path.join(root, "AppLibrary", "bookmarks.json"), { version: 1, items: [{ id: "b-1" }] });
  await writeJson(path.join(root, "AppLibrary", "reading-progress.json"), { version: 2, items: [{ bookId: "book-1" }] });
  await mkdir(path.join(root, "AppLibrary", "files"), { recursive: true });
  await mkdir(path.join(root, "AppLibrary", "covers"), { recursive: true });
  await mkdir(path.join(root, "AppLibrary", "search-index"), { recursive: true });
  await writeFile(path.join(root, "AppLibrary", "files", "fixture.bin"), "file", "utf8");
  await writeFile(path.join(root, "AppLibrary", "covers", "fixture.bin"), "cover", "utf8");
  await writeFile(path.join(root, "AppLibrary", "search-index", "fixture.json"), "index", "utf8");
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-migration-audit-"));
  try {
    const missing = path.join(parent, "missing");
    const missingReport = await auditLegacyDesktopData({ dataRoot: missing });
    assert.equal(missingReport.canProceed, false);
    await assert.rejects(stat(missing));

    const rootFile = path.join(parent, "root-is-file");
    await writeFile(rootFile, "fixture", "utf8");
    const rootFileReport = await auditLegacyDesktopData({ dataRoot: rootFile });
    assert.equal(rootFileReport.canProceed, false);
    assert.equal(rootFileReport.issues.some((issue) => issue.code === "root-not-directory"), true);

    const valid = path.join(parent, "valid");
    await createValidFixture(valid);
    const before = await fingerprint(valid);
    const report = await auditLegacyDesktopData({ dataRoot: valid });
    const after = await fingerprint(valid);
    assert.deepEqual(after, before);
    assert.equal(report.canProceed, true);
    assert.equal(report.writesPerformed, 0);
    assert.equal(report.activated, false);
    assert.equal(report.inspirationPlan.items[0]?.bodySha256, createHash("sha256").update("正文_SENTINEL_BODY").digest("hex"));
    assert.equal(report.inspirationPlan.items[0]?.variantsCount, 1);
    assert.equal(report.readerCompatibility.booksByFormat.epub, 1);
    assert.equal(report.readerCompatibility.booksByFormat.txt, 1);
    assert.equal(report.readerCompatibility.files, 1);
    assert.equal(report.sources.aiSecrets.status, "metadata-only");
    assert.equal(report.targetStore.inspectedByOpening, false);
    await assert.rejects(stat(path.join(valid, "CreationWorkspace")));
    const serialized = JSON.stringify(report);
    for (const forbidden of [
      "SENTINEL_SECRET",
      "正文_SENTINEL_BODY",
      "VARIANT_SENTINEL",
      "PROMPT_SENTINEL",
      "MODEL_SENTINEL",
      "测试 EPUB",
      "测试 TXT",
      valid
    ]) {
      assert.equal(serialized.includes(forbidden), false);
    }

    const recoverable = path.join(parent, "recoverable");
    await mkdir(recoverable, { recursive: true });
    await writeFile(path.join(recoverable, "inspirations.json"), "{broken", "utf8");
    await writeJson(path.join(recoverable, "inspirations.json.bak"), { version: 1, items: [] });
    const recoverableReport = await auditLegacyDesktopData({ dataRoot: recoverable });
    assert.equal(recoverableReport.sources.inspirations.status, "recoverable-backup");
    assert.equal(recoverableReport.issues.some((issue) => issue.code === "using-backup"), true);

    const shapeRecoverable = path.join(parent, "shape-recoverable");
    await mkdir(shapeRecoverable, { recursive: true });
    await writeJson(path.join(shapeRecoverable, "inspirations.json"), { wrong: true });
    await writeJson(path.join(shapeRecoverable, "inspirations.json.bak"), { version: 1, items: [] });
    const shapeRecoverableReport = await auditLegacyDesktopData({ dataRoot: shapeRecoverable });
    assert.equal(shapeRecoverableReport.sources.inspirations.status, "recoverable-backup");

    const empty = path.join(parent, "empty");
    await mkdir(empty, { recursive: true });
    await writeFile(path.join(empty, "inspirations.json"), "", "utf8");
    const emptyReport = await auditLegacyDesktopData({ dataRoot: empty });
    assert.equal(emptyReport.canProceed, true);
    assert.equal(emptyReport.sources.inspirations.status, "empty");
    assert.equal(emptyReport.issues.some((issue) => issue.code === "source-empty"), true);

    const customDataRoot = path.join(parent, "custom-data");
    const customLibraryRoot = path.join(parent, "custom-library");
    await mkdir(customDataRoot, { recursive: true });
    await writeJson(path.join(customLibraryRoot, "library.json"), {
      books: [{ id: "custom-book", title: "测试 TXT", format: "txt" }]
    });
    await mkdir(path.join(customLibraryRoot, "files"), { recursive: true });
    await writeFile(path.join(customLibraryRoot, "files", "fixture.bin"), "file", "utf8");
    const customReport = await auditLegacyDesktopData({
      dataRoot: customDataRoot,
      libraryRoot: customLibraryRoot
    });
    assert.equal(customReport.readerCompatibility.books, 1);
    assert.equal(customReport.readerCompatibility.files, 1);

    const broken = path.join(parent, "broken");
    await mkdir(broken, { recursive: true });
    await writeFile(path.join(broken, "inspirations.json"), "{broken", "utf8");
    await writeFile(path.join(broken, "inspirations.json.bak"), "{also-broken", "utf8");
    const brokenReport = await auditLegacyDesktopData({ dataRoot: broken });
    assert.equal(brokenReport.canProceed, false);
    assert.equal(brokenReport.sources.inspirations.status, "invalid");

    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 10 })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
