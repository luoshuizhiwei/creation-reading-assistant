import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-card-io-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");
// 类型检查复用主进程 tsconfig，但排除并行 agent 在途的脏文件（如 replace-contract.ts），
// 避免其既有类型错误连累本契约的类型门禁。临时配置写在仓库根以便相对 include 正确解析。
const tsconfigPath = path.join(root, "tsconfig.contract.tmp.json");
writeFileSync(
  tsconfigPath,
  JSON.stringify({
    extends: "./tsconfig.main.json",
    compilerOptions: { noEmit: true },
    include: [
      "electron/main/creation-card-io/**/*.ts",
      "electron/main/creation-workspace/index.ts",
      "electron/main/creation-workspace/test-utils.ts",
      "electron/main/creation-workspace/better-sqlite3.d.ts",
      "src/types/**/*.ts"
    ],
    exclude: ["electron/**/__tests__/**"]
  })
);

function fail(message) {
  console.error(`[verify-creation-card-io] ${message}`);
  process.exitCode = 1;
}

try {
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "-p", tsconfigPath],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  if (tsc.status !== 0) {
    fail(`TypeScript contract failed.\n${tsc.stdout}\n${tsc.stderr}`);
  } else {
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-card-io", "card-io-contract.ts")],
      outfile: bundlePath,
      bundle: true,
      platform: "node",
      format: "cjs",
      target: "node20",
      external: ["electron", "better-sqlite3"]
    });

    const electron = path.join(root, "node_modules", "electron", "dist", "electron.exe");
    // 代理环境会把 --use-system-ca 注入 NODE_OPTIONS；electron-as-node 拒绝未知 flag。
    const electronEnv = { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") };
    delete electronEnv.NODE_OPTIONS;
    const contract = spawnSync(electron, [bundlePath], {
      cwd: root,
      encoding: "utf8",
      env: electronEnv,
      windowsHide: true,
      timeout: 120_000
    });
    if (contract.status !== 0) {
      fail(`Runtime contract failed.\n${contract.stdout}\n${contract.stderr}`);
    } else {
      const evidence = JSON.parse(contract.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1));
      if (evidence.allPass !== true) fail(`Incomplete evidence: ${JSON.stringify(evidence)}`);
      else console.log(`[verify-creation-card-io] ${evidence.tests} card-io contracts verified.`);
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
  rmSync(tsconfigPath, { force: true });
}
