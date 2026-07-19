import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

const root = process.cwd();
const buildInstaller = process.argv.includes("--installer");
const electronDist = path.join(root, "node_modules", "electron", "dist");
const electronPackageJson = path.join(root, "node_modules", "electron", "package.json");
const electronBuilderCli = path.join(root, "node_modules", "electron-builder", "cli.js");

if (!existsSync(electronDist) || !existsSync(electronPackageJson)) {
  console.error("[dist-beta-offline] Missing local Electron distribution. Run npm install first.");
  process.exit(1);
}

if (!existsSync(electronBuilderCli)) {
  console.error("[dist-beta-offline] Missing local electron-builder CLI. Run npm install first.");
  process.exit(1);
}

const electronVersion = JSON.parse(readFileSync(electronPackageJson, "utf8")).version;
const builderArgs = [
  electronBuilderCli,
  "--publish",
  "never",
  "--config.directories.output=release-beta",
  `--config.electronDist=${electronDist}`,
  `--config.electronVersion=${electronVersion}`
];
if (!buildInstaller) builderArgs.splice(1, 0, "--dir");

const result = spawnSync(
  process.execPath,
  builderArgs,
  { cwd: root, stdio: "inherit" }
);

process.exit(result.status ?? 1);
