/**
 * 可移植加密合同（深模块）: 在 Node 运行时验证 12 项要求中的可测部分。
 *
 * 覆盖: round-trip、错误口令、篡改、截断、取消、清理、旧格式兼容、大文件边界，
 * 以及“密文完整性通过后再进入现有验证流程”与“明文不被误识别为容器”。
 *
 * 运行方式见同目录 run-contract.mjs（tsc --noEmit + esbuild + electron-as-node）。
 */
import crypto from "node:crypto";
import { promises as fsp, readdirSync, mkdtempSync } from "node:fs";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  encryptDirectory,
  decryptContainerToStaging,
  withDecryptedStaging,
  EncryptionError,
  CONTAINER_MAGIC
} from "./index";
import type { EncryptionProgress } from "./types";
import { parseBackupManifest } from "../backup";

interface ScenarioResult {
  name: string;
  pass: boolean;
  detail: string;
}

const noopLogger = (level: string, message: string, meta?: Record<string, unknown>) => {
  // 仅写入 stderr，避免污染 stdout 的最终 JSON 证据。
  process.stderr.write(`[contract:${level}] ${message} ${meta ? JSON.stringify(meta) : ""}\n`);
};

function countDecryptTemp(): number {
  return readdirSync(os.tmpdir()).filter((n) => n.startsWith("cr-decrypt-")).length;
}

async function writeTree(root: string, files: Record<string, string | Buffer>): Promise<void> {
  for (const [rel, content] of Object.entries(files)) {
    const abs = path.join(root, rel);
    await fsp.mkdir(path.dirname(abs), { recursive: true });
    await fsp.writeFile(abs, content);
  }
}

async function hashTree(root: string): Promise<string> {
  const out: string[] = [];
  async function rec(dir: string): Promise<void> {
    for (const ent of await fsp.readdir(dir, { withFileTypes: true })) {
      const abs = path.join(dir, ent.name);
      if (ent.isDirectory()) await rec(abs);
      else if (ent.isFile()) {
        const buf = await fsp.readFile(abs);
        const h = crypto.createHash("sha256").update(buf).digest("hex");
        out.push(`${path.relative(root, abs).split(path.sep).join("/")}\t${h}\t${buf.length}`);
      }
    }
  }
  await rec(root);
  out.sort();
  return out.join("\n");
}

async function getHeaderEnd(file: string): Promise<number> {
  const fd = await fsp.open(file, "r");
  try {
    const prefix = Buffer.alloc(10);
    await fd.read(prefix, 0, 10, 0);
    if (!prefix.subarray(0, 4).equals(CONTAINER_MAGIC)) throw new Error("not a container");
    const headerLen = prefix.readUInt32BE(6);
    return 10 + headerLen;
  } finally {
    await fd.close();
  }
}

function sha256Hex(buf: Buffer): string {
  return crypto.createHash("sha256").update(buf).digest("hex");
}

async function scenario(
  evidence: ScenarioResult[],
  name: string,
  fn: () => Promise<void>
): Promise<void> {
  try {
    await fn();
    evidence.push({ name, pass: true, detail: "ok" });
  } catch (e) {
    evidence.push({ name, pass: false, detail: e instanceof Error ? `${e.name}: ${e.message}` : String(e) });
  }
}

async function runContract(): Promise<{ allPass: boolean; tests: number; evidence: ScenarioResult[] }> {
  const evidence: ScenarioResult[] = [];
  const baseDir = mkdtempSync(path.join(os.tmpdir(), "portable-encryption-contract-"));
  // 基线：仅检测“本次运行”是否泄漏，忽略此前崩溃遗留的临时目录（环境产物，非本模块缺陷）。
  const baseline = countDecryptTemp();

  await scenario(evidence, "round-trip-plaintext-preserved", async () => {
    const src = path.join(baseDir, "src-roundtrip");
    await writeTree(src, {
      "app-data/scenes.sqlite": crypto.randomBytes(2048),
      "app-data/meta.json": JSON.stringify({ a: 1, b: "中文" }),
      "library/book.txt": "hello world".repeat(50),
      "deep/nested/dir/note.md": "# title\n\nbody"
    });
    const before = await hashTree(src);

    const container = path.join(baseDir, "rt.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "correct horse battery staple", payloadKind: "backup", logger: noopLogger });

    await withDecryptedStaging(
      { containerFile: container, passphrase: "correct horse battery staple", logger: noopLogger },
      async (stagingDir) => {
        const after = await hashTree(stagingDir);
        if (before !== after) throw new Error("tree hash mismatch after round-trip");
      }
    );
  });

  await scenario(evidence, "empty-passphrase-rejected", async () => {
    const src = path.join(baseDir, "src-empty");
    await writeTree(src, { "a.txt": "x" });
    const container = path.join(baseDir, "empty.crbackup");
    let threw = false;
    try {
      await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "" });
    } catch (e) {
      threw = e instanceof EncryptionError && e.code === "invalid-input";
    }
    if (!threw) throw new Error("empty passphrase should be rejected");
    if (fs.existsSync(container)) throw new Error("no container should be created for invalid input");
  });

  await scenario(evidence, "wrong-passphrase-fails-before-staging", async () => {
    const src = path.join(baseDir, "src-wrong");
    await writeTree(src, { "data.txt": "secret contents".repeat(20) });
    const container = path.join(baseDir, "wrong.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "right-pass" });
    const before = countDecryptTemp();
    let code = "";
    try {
      await decryptContainerToStaging({ containerFile: container, passphrase: "wrong-pass" });
    } catch (e) {
      code = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (code !== "auth-failed") throw new Error(`expected auth-failed, got ${code}`);
    if (countDecryptTemp() !== before) throw new Error("staging dir leaked after wrong passphrase");
  });

  await scenario(evidence, "tampered-ciphertext-fails", async () => {
    const src = path.join(baseDir, "src-tamper");
    await writeTree(src, { "data.txt": crypto.randomBytes(4096) });
    const container = path.join(baseDir, "tamper.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "pass" });
    const headerEnd = await getHeaderEnd(container);
    const buf = await fsp.readFile(container);
    const tampered = Buffer.from(buf);
    const pos = headerEnd + 16;
    if (pos >= tampered.length - 16) throw new Error("container too small to tamper payload");
    tampered[pos] = tampered[pos] ^ 0xff;
    const tamperedFile = path.join(baseDir, "tamper.crbackup.tampered");
    await fsp.writeFile(tamperedFile, tampered);

    const before = countDecryptTemp();
    let code = "";
    try {
      await decryptContainerToStaging({ containerFile: tamperedFile, passphrase: "pass" });
    } catch (e) {
      code = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (code !== "auth-failed") throw new Error(`expected auth-failed, got ${code}`);
    if (countDecryptTemp() !== before) throw new Error("staging dir leaked after tampering");
  });

  await scenario(evidence, "truncated-container-fails", async () => {
    const src = path.join(baseDir, "src-trunc");
    await writeTree(src, { "data.txt": crypto.randomBytes(8192) });
    const container = path.join(baseDir, "trunc.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "pass" });
    const headerEnd = await getHeaderEnd(container);
    const fileSize = (await fsp.stat(container)).size;

    // 1) 截断到头部之后、不足以容纳认证标签 -> truncated
    const shortFile = path.join(baseDir, "trunc.short.crbackup");
    await fsp.copyFile(container, shortFile);
    await fsp.truncate(shortFile, headerEnd + 4);
    let codeShort = "";
    try {
      await decryptContainerToStaging({ containerFile: shortFile, passphrase: "pass" });
    } catch (e) {
      codeShort = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (codeShort !== "truncated") throw new Error(`expected truncated, got ${codeShort}`);

    // 2) 截掉一半密文（但保留标签空间）-> 认证失败 auth-failed
    const midLen = headerEnd + 16 + Math.floor((fileSize - headerEnd - 16) / 2);
    const midFile = path.join(baseDir, "trunc.mid.crbackup");
    await fsp.copyFile(container, midFile);
    await fsp.truncate(midFile, midLen);
    let codeMid = "";
    try {
      await decryptContainerToStaging({ containerFile: midFile, passphrase: "pass" });
    } catch (e) {
      codeMid = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (codeMid !== "auth-failed") throw new Error(`expected auth-failed, got ${codeMid}`);
  });

  await scenario(evidence, "cancel-aborts-and-cleans", async () => {
    const src = path.join(baseDir, "src-cancel");
    await writeTree(src, {
      "a.txt": crypto.randomBytes(1024),
      "b.txt": crypto.randomBytes(1024),
      "c.txt": crypto.randomBytes(1024)
    });
    const container = path.join(baseDir, "cancel.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "pass" });

    const cancelOp: EncryptionProgress = {
      setProgress: () => {},
      throwIfCancelled: () => {
        throw new EncryptionError("cancelled", "user cancelled");
      }
    };
    const before = countDecryptTemp();
    let code = "";
    try {
      await decryptContainerToStaging({ containerFile: container, passphrase: "pass", operation: cancelOp });
    } catch (e) {
      code = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (code !== "cancelled") throw new Error(`expected cancelled, got ${code}`);
    if (countDecryptTemp() !== before) throw new Error("staging dir leaked after cancel");
  });

  await scenario(evidence, "old-format-compatible-v1-v2", async () => {
    // 备份 v1 / v2 与项目包 v1 / v2 作为“明文内层”被透明封装，格式不变。
    const cases: Array<{ dir: string; manifest: Record<string, unknown>; kind: "backup" | "bundle" }> = [
      {
        dir: "v1-backup",
        kind: "backup",
        manifest: { formatVersion: 1, createdAt: new Date().toISOString(), files: [{ relativePath: "x.txt", sha256: "deadbeef", size: 3 }] }
      },
      {
        dir: "v2-backup",
        kind: "backup",
        manifest: { formatVersion: 2, createdAt: new Date().toISOString(), appVersion: "0.2.1", files: [] }
      },
      {
        dir: "v1-bundle",
        kind: "bundle",
        manifest: { formatVersion: 1, project: { id: "p1", title: "T" } }
      },
      {
        dir: "v2-bundle",
        kind: "bundle",
        manifest: { formatVersion: 2, project: { id: "p1", title: "T" } }
      }
    ];
    for (const c of cases) {
      const src = path.join(baseDir, `src-${c.dir}`);
      const innerName = c.kind === "backup" ? "backup-manifest.json" : "manifest.json";
      await writeTree(src, {
        [innerName]: JSON.stringify(c.manifest),
        "resources/r.txt": "asset"
      });
      const container = path.join(baseDir, `${c.dir}.cr`);
      await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "p", payloadKind: c.kind });

      await withDecryptedStaging({ containerFile: container, passphrase: "p" }, async (stagingDir) => {
        const restored = JSON.parse(await fsp.readFile(path.join(stagingDir, innerName), "utf8"));
        if (restored.formatVersion !== (c.manifest as { formatVersion: number }).formatVersion) {
          throw new Error(`${c.dir}: inner formatVersion not preserved (${restored.formatVersion})`);
        }
      });
    }
  });

  await scenario(evidence, "integrity-then-existing-verify-flow", async () => {
    // 只有密文完整性通过后才进入现有备份验证流程（parseBackupManifest）。
    const src = path.join(baseDir, "src-verify");
    const scenes = crypto.randomBytes(1024);
    await writeTree(src, {
      "app-data/scenes.sqlite": scenes,
      "backup-manifest.json": "{}"
    });
    const manifest = {
      version: 2,
      createdAt: new Date().toISOString(),
      appVersion: "0.2.1",
      checksumAlgorithm: "sha256",
      appDataPath: "app-data",
      platform: process.platform,
      arch: process.arch,
      files: [
        { path: "app-data/scenes.sqlite", size: scenes.length, sha256: sha256Hex(scenes) },
        { path: "backup-manifest.json", size: 2, sha256: "0".repeat(64) }
      ]
    };
    await fsp.writeFile(path.join(src, "backup-manifest.json"), JSON.stringify(manifest));

    const container = path.join(baseDir, "verify.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "p", payloadKind: "backup" });

    await withDecryptedStaging({ containerFile: container, passphrase: "p" }, async (stagingDir) => {
      const restored = JSON.parse(await fsp.readFile(path.join(stagingDir, "backup-manifest.json"), "utf8"));
      parseBackupManifest(restored); // 不抛错即通过现有验证流程
    });
  });

  await scenario(evidence, "large-file-streamed-boundary", async () => {
    const src = path.join(baseDir, "src-large");
    const big = crypto.randomBytes(32 * 1024 * 1024); // 32 MiB, 远大于单 chunk
    await writeTree(src, {
      "big.bin": big,
      "edge-64k.bin": crypto.randomBytes(64 * 1024),
      "edge-64k-plus-1.bin": crypto.randomBytes(64 * 1024 + 1),
      "empty.bin": Buffer.alloc(0),
      "tiny.bin": crypto.randomBytes(1)
    });
    const before = await hashTree(src);
    const container = path.join(baseDir, "large.crbackup");
    await encryptDirectory({ sourceDir: src, targetFile: container, passphrase: "p" });

    await withDecryptedStaging({ containerFile: container, passphrase: "p" }, async (stagingDir) => {
      const after = await hashTree(stagingDir);
      if (before !== after) throw new Error("large file round-trip mismatch");
    });
  });

  await scenario(evidence, "plaintext-not-mistaken-for-container", async () => {
    // 明文备份文件不应被误识别为加密容器（明文继续兼容）。
    const plain = path.join(baseDir, "plain-backup-manifest.json");
    await fsp.writeFile(plain, JSON.stringify({ formatVersion: 2, files: [] }));
    let code = "";
    try {
      await decryptContainerToStaging({ containerFile: plain, passphrase: "p" });
    } catch (e) {
      code = e instanceof EncryptionError ? e.code : "unknown";
    }
    if (code !== "invalid-input") throw new Error(`expected invalid-input, got ${code}`);
  });

  await scenario(evidence, "no-staging-leak-at-end", async () => {
    const current = countDecryptTemp();
    if (current !== baseline) throw new Error(`本次运行泄漏 cr-decrypt 目录: 基线 ${baseline}, 当前 ${current}`);
  });

  return {
    allPass: evidence.every((e) => e.pass),
    tests: evidence.length,
    evidence
  };
}

runContract()
  .then((r) => {
    process.stderr.write(`[contract:done] allPass=${r.allPass} tests=${r.tests}\n`);
    console.log(JSON.stringify(r));
    process.exitCode = r.allPass ? 0 : 1;
  })
  .catch((e) => {
    process.stderr.write(`[contract:fatal] ${e instanceof Error ? e.stack ?? e.message : String(e)}\n`);
    console.log(
      JSON.stringify({
        allPass: false,
        tests: 0,
        evidence: [{ name: "fatal", pass: false, detail: e instanceof Error ? e.stack ?? e.message : String(e) }]
      })
    );
    process.exitCode = 1;
  });
