import path from "node:path";
import { spawnSync } from "node:child_process";

const root = path.resolve(import.meta.dirname, "..");
const vitest = path.join(root, "node_modules", "vitest", "vitest.mjs");
const result = spawnSync(process.execPath, [vitest, "run", "electron/main/backup/__tests__/auto-backup-contract.test.ts"], {
  cwd: root,
  encoding: "utf8",
  windowsHide: true,
  timeout: 120_000
});

process.stdout.write(result.stdout ?? "");
process.stderr.write(result.stderr ?? "");
if (result.status !== 0) process.exit(result.status ?? 1);

const mainSource = await import("node:fs/promises").then(({ readFile }) =>
  readFile(path.join(root, "electron", "main", "index.ts"), "utf8")
);
for (const marker of ["Notification.isSupported()", 'title: "自动备份失败"', "lastAutoBackupError: undefined"]) {
  if (!mainSource.includes(marker)) {
    console.error(`[verify-auto-backup] Missing significant failure-notification contract: ${marker}`);
    process.exit(1);
  }
}
console.log("[verify-auto-backup] Automatic backup contract passed.");
