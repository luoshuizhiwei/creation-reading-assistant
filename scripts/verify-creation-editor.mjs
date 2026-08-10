import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-editor-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[verify-creation-editor] ${message}`);
  process.exitCode = 1;
}

function runNodeTool(arguments_, label) {
  const result = spawnSync(process.execPath, arguments_, {
    cwd: root,
    encoding: "utf8",
    windowsHide: true,
    timeout: 120_000
  });
  if (result.status !== 0) {
    fail(`${label} failed.\n${result.stdout}\n${result.stderr}`);
    return null;
  }
  return result;
}

try {
  const ipcSource = readFileSync(path.join(root, "electron", "main", "creation-ipc.ts"), "utf8");
  const preloadSource = readFileSync(path.join(root, "electron", "preload", "index.ts"), "utf8");
  const apiSource = readFileSync(path.join(root, "src", "types", "api.ts"), "utf8");
  const sceneEditorSource = readFileSync(
    path.join(root, "src", "features", "creation", "editor", "SceneEditor.tsx"),
    "utf8"
  );
  const sessionSource = readFileSync(
    path.join(root, "src", "features", "creation", "editor", "scene-document-session.ts"),
    "utf8"
  );
  const betaSource = readFileSync(path.join(root, "scripts", "beta-check.mjs"), "utf8");

  const invokeChannels = [
    "creation:readSceneBody",
    "creation:updateSceneBody",
    "creation:watchProject",
    "creation:unwatchProject"
  ];
  for (const channel of invokeChannels) {
    if (!ipcSource.includes(`"${channel}"`) || !preloadSource.includes(`"${channel}"`)) {
      fail(`IPC channel must exist in both creation IPC and preload: ${channel}`);
    }
  }
  if (!ipcSource.includes('sender.send("creation:event"') || !preloadSource.includes('ipcRenderer.on("creation:event"')) {
    fail("Typed creation watch push channel is missing.");
  }
  if (!/readSceneBody: \(sceneId: string\) => Promise<SceneBodyView \| null>/.test(apiSource)) {
    fail("DesktopApi.creation.readSceneBody signature is missing.");
  }
  if (!/updateSceneBody: \(input: UpdateSceneBodyInput\) => Promise<SceneSaveResponse>/.test(apiSource)) {
    fail("DesktopApi.creation.updateSceneBody signature is missing.");
  }
  if (!/watchProject: \(projectId: string, listener: CreationProjectListener\) => Promise<\(\) => void>/.test(apiSource)) {
    fail("DesktopApi.creation.watchProject signature is missing.");
  }
  if (!sceneEditorSource.includes("createSceneDocumentSession") || !sceneEditorSource.includes("compositionstart")) {
    fail("SceneEditor must use the tested document session and IME composition boundary.");
  }
  if (!sceneEditorSource.includes("inspectScenePaste") || !sceneEditorSource.includes("PastePreviewDialog")) {
    fail("SceneEditor must keep the paste-clean preview path.");
  }
  if (!sessionSource.includes("SCENE_AUTOSAVE_DEBOUNCE_MS = 800")) {
    fail("Scene document session must keep the fixed 800ms autosave delay.");
  }
  if (!betaSource.includes('runScoped("verify:creation-editor")')) {
    fail("verify:beta must run verify:creation-editor for desktop scope.");
  }

  const mainResult = runNodeTool(
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    "Main TypeScript contract"
  );
  const rendererResult = runNodeTool(
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.renderer.json"],
    "Renderer TypeScript contract"
  );
  const editorTestsResult = runNodeTool(
    [
      path.join(root, "node_modules", "vitest", "vitest.mjs"),
      "run",
      "src/features/creation/editor/__tests__"
    ],
    "Editor unit contract"
  );

  if (mainResult && rendererResult && editorTestsResult) {
    const unitOutput = editorTestsResult.stdout.replace(/\u001b\[[0-9;]*m/g, "");
    const unitMatch = unitOutput.match(/Tests\s+(\d+) passed/);
    const unitCount = unitMatch ? Number(unitMatch[1]) : 0;
    if (unitCount < 1) fail("Editor unit test evidence could not be parsed.");
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-workspace", "editor-contract.ts")],
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
      if (evidence.allPass !== true || evidence.tests < 5) {
        fail(`Incomplete runtime evidence: ${JSON.stringify(evidence)}`);
      } else if (unitCount > 0) {
        console.log(`[verify-creation-editor] ${evidence.tests} runtime contracts and ${unitCount} editor unit contracts verified.`);
      }
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
