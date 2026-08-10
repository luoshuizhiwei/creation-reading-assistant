import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-resource-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[verify-creation-resource] ${message}`);
  process.exitCode = 1;
}

try {
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  if (tsc.status !== 0) {
    fail(`TypeScript contract failed.\n${tsc.stdout}\n${tsc.stderr}`);
  } else {
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-workspace", "resource-contract.ts")],
      outfile: bundlePath,
      bundle: true,
      platform: "node",
      format: "cjs",
      target: "node20",
      external: ["electron", "better-sqlite3"]
    });

    const electron = path.join(root, "node_modules", "electron", "dist", "electron.exe");
    const contract = spawnSync(electron, [bundlePath], {
      cwd: root,
      encoding: "utf8",
      env: {
        ...process.env,
        ELECTRON_RUN_AS_NODE: "1",
        NODE_PATH: path.join(root, "node_modules")
      },
      windowsHide: true,
      timeout: 120_000
    });
    if (contract.status !== 0) {
      fail(`Runtime contract failed.\n${contract.stdout}\n${contract.stderr}`);
    } else {
      const evidence = JSON.parse(contract.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1));
      if (evidence.allPass !== true) fail(`Incomplete evidence: ${JSON.stringify(evidence)}`);
      else console.log(`[verify-creation-resource] ${evidence.tests} resource contracts verified.`);
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
