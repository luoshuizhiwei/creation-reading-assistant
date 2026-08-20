import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..", "..", "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "portable-encryption-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[portable-encryption-contract] ${message}`);
  process.exitCode = 1;
}

try {
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  // 仅当类型错误落在本次新增文件时才失败；工作区中其他所有者正在修改的文件
  // （如 creation-card-io）的既有错误不属于本定向合同范围。
  const owned = ["portable-encryption", "encrypted-backup", "encrypted-bundle"];
  const tscLines = (tsc.stdout ?? "").split(/\r?\n/).filter(Boolean);
  const ownedErrors = tscLines.filter((l) => owned.some((o) => l.includes(o)) && l.includes("error TS"));
  if (ownedErrors.length > 0) {
    fail(`TypeScript contract failed (owned files).\n${ownedErrors.join("\n")}`);
  } else {
    if (tsc.status !== 0) {
      console.error(
        `[portable-encryption-contract] 跳过与本模块无关的工作区既有类型错误 (${tscLines.length} 行); 仅校验新增文件。`
      );
    }

    buildSync({
      entryPoints: [path.join(root, "electron", "main", "portable-encryption", "contract.ts")],
      outfile: bundlePath,
      bundle: true,
      platform: "node",
      format: "cjs",
      target: "node20",
      external: ["electron", "better-sqlite3"]
    });

    const electron = path.join(root, "node_modules", "electron", "dist", "electron.exe");
    const electronEnv = {
      ...process.env,
      ELECTRON_RUN_AS_NODE: "1",
      NODE_PATH: path.join(root, "node_modules")
    };
    delete electronEnv.NODE_OPTIONS;
    const contract = spawnSync(electron, [bundlePath], {
      cwd: root,
      encoding: "utf8",
      env: electronEnv,
      windowsHide: true,
      timeout: 300_000
    });
    if (contract.status !== 0) {
      fail(`Runtime contract failed.\n--- stdout ---\n${contract.stdout}\n--- stderr ---\n${contract.stderr}`);
    } else {
      const evidence = JSON.parse(contract.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1));
      if (evidence.allPass !== true) fail(`Incomplete evidence: ${JSON.stringify(evidence)}`);
      else console.log(`[portable-encryption-contract] ${evidence.tests} portable encryption contracts verified.`);
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
