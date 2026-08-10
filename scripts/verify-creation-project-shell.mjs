import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-project-shell-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[verify-creation-project-shell] ${message}`);
  process.exitCode = 1;
}

try {
  // --- Static anchors: fixed renderer/IPC contract ---
  const mainSource = readFileSync(path.join(root, "electron", "main", "index.ts"), "utf8");
  const creationIpcSource = readFileSync(path.join(root, "electron", "main", "creation-ipc.ts"), "utf8");
  const preloadSource = readFileSync(path.join(root, "electron", "preload", "index.ts"), "utf8");
  const apiSource = readFileSync(path.join(root, "src", "types", "api.ts"), "utf8");
  const creationTypesPath = path.join(root, "src", "types", "creation.ts");
  if (!existsSync(creationTypesPath)) fail("Missing required renderer contract: src/types/creation.ts");

  const requiredChannels = ["creation:listProjects", "creation:readProjectNavigation", "creation:createProject"];
  for (const channel of requiredChannels) {
    if (!creationIpcSource.includes(`"${channel}"`) || !preloadSource.includes(`"${channel}"`)) {
      fail(`IPC channel must exist in both main and preload: ${channel}`);
    }
  }

  const allowedChannels = [
    ...requiredChannels,
    "creation:readSceneBody",
    "creation:updateSceneBody",
    "creation:readProjectOutline",
    "creation:runStructure",
    "creation:cardsList",
    "creation:cardRead",
    "creation:cardTypesList",
    "creation:relationTypesList",
    "creation:cardRelations",
    "creation:trashList",
    "creation:snapshotList",
    "creation:exportDraft",
    "creation:search",
    "creation:replacePreview",
    "creation:replaceApply",
    "creation:statsView",
    "creation:sessionList",
    "creation:sessionReport",
    "creation:sessionDelete",
    "creation:proofQuery",
    "creation:watchProject",
    "creation:unwatchProject",
    "creation:event"
  ];
  const preloadCreationChannels = [...preloadSource.matchAll(/"creation:[^"]+"/g)].map((match) => match[0].slice(1, -1));
  for (const channel of preloadCreationChannels) {
    if (!allowedChannels.includes(channel)) fail(`Preload must not expose raw creation channel: ${channel}`);
  }
  if (!/creation:\s*\{[\s\S]*?listProjects[\s\S]*?readProjectNavigation[\s\S]*?createProject[\s\S]*?\}/.test(apiSource)) {
    fail("DesktopApi.creation must expose listProjects, readProjectNavigation and createProject.");
  }
  if (!/creation:\s*\{[\s\S]*?listProjects: \(\) => Promise<CreationProjectSummary\[\]>[\s\S]*?readProjectNavigation: \(projectId: string\) => Promise<CreationProjectNavigation \| null>[\s\S]*?createProject: \(input: CreateProjectInput\) => Promise<CreationProjectNavigation>[\s\S]*?\}/.test(apiSource)) {
    fail("DesktopApi.creation signatures do not match the fixed renderer contract.");
  }
  if (!creationIpcSource.includes("kind: \"projects.list\"")) fail("Main handler must delegate list to read(projects.list).");
  if ((mainSource.match(/creationCoordinator\.withWorkspaceClosed/g) ?? []).length < 3) {
    fail("Backup/restore/data-migration must hold the coordinator maintenance lock.");
  }
  if (!mainSource.includes("creationCoordinator") || !mainSource.includes(".close()")) {
    fail("before-quit must trigger coordinator close.");
  }
  if (!mainSource.includes("event.preventDefault()") || !mainSource.includes("creationWorkspaceReadyToQuit")) {
    fail("before-quit must wait for coordinator close before allowing Electron to exit.");
  }

  const betaCheck = readFileSync(path.join(root, "scripts", "beta-check.mjs"), "utf8");
  if (!betaCheck.includes('runScoped("verify:creation-project-shell")')) {
    fail("verify:beta must run verify:creation-project-shell for desktop scope.");
  }
  const packageJson = JSON.parse(readFileSync(path.join(root, "package.json"), "utf8"));
  if (packageJson.scripts?.["verify:creation-project-shell"] !== "node scripts/verify-creation-project-shell.mjs") {
    fail("package.json must expose verify:creation-project-shell.");
  }

  // --- TypeScript contract ---
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  if (tsc.status !== 0) {
    fail(`TypeScript contract failed.\n${tsc.stdout}\n${tsc.stderr}`);
  } else {
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-workspace", "project-shell-contract.ts")],
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
      else console.log(`[verify-creation-project-shell] ${evidence.tests} project-shell contracts verified.`);
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
