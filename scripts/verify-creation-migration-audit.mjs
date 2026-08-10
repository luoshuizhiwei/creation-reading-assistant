import { mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-migration-audit-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[verify-creation-migration-audit] ${message}`);
  process.exitCode = 1;
}

try {
  const moduleDirectory = path.join(root, "electron", "main", "creation-migration-audit");
  const moduleSource = readdirSync(moduleDirectory)
    .filter((name) => name.endsWith(".ts") && name !== "contract.ts")
    .map((name) => readFileSync(path.join(moduleDirectory, name), "utf8"))
    .join("\n");
  for (const forbidden of [
    "writeFile(",
    "appendFile(",
    "mkdir(",
    "copyFile(",
    "rename(",
    "unlink(",
    "rm(",
    "open(",
    "truncate(",
    "chmod(",
    "utimes(",
    "createWriteStream(",
    "node:child_process",
    'from "electron"',
    "better-sqlite3",
    "node:sqlite"
  ]) {
    if (moduleSource.includes(forbidden)) {
      throw new Error(`Read-only audit module contains forbidden capability: ${forbidden}`);
    }
  }
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  if (tsc.status !== 0) {
    fail(`TypeScript contract failed.\n${tsc.stdout}\n${tsc.stderr}`);
  } else {
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-migration-audit", "contract.ts")],
      outfile: bundlePath,
      bundle: true,
      platform: "node",
      format: "cjs",
      target: "node20"
    });
    const contract = spawnSync(process.execPath, [bundlePath], {
      cwd: root,
      encoding: "utf8",
      windowsHide: true,
      timeout: 120_000
    });
    if (contract.status !== 0) {
      fail(`Runtime contract failed.\n${contract.stdout}\n${contract.stderr}`);
    } else {
      const evidence = JSON.parse(contract.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1));
      if (evidence.allPass !== true || evidence.tests !== 10) {
        fail(`Incomplete evidence: ${JSON.stringify(evidence)}`);
      } else {
        console.log("[verify-creation-migration-audit] 10 read-only audit contracts verified.");
      }
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
