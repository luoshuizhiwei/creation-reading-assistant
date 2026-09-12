/**
 * Stage 1 packaged Electron acceptance.
 *
 * Launches the unpacked production executable with isolated APPDATA/LOCALAPPDATA,
 * drives the real renderer/preload/IPC/SQLite/file stack, and records evidence in
 * output/playwright. Native file/message dialogs are replaced with deterministic
 * responses that point only at the temporary acceptance directory.
 */
import { cpSync, mkdtempSync, mkdirSync, rmSync, writeFileSync, existsSync } from "node:fs";
import { readFile, readdir } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { buildSync } from "esbuild";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const packagedDirectory = path.join(root, "release-beta", "win-unpacked");
const sourceExecutablePath = path.join(packagedDirectory, "创作阅读助手.exe");
const evidenceDirectory = path.join(root, "output", "playwright");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-packaged-acceptance-"));
const roamingDirectory = path.join(temporaryRoot, "Roaming");
const localDirectory = path.join(temporaryRoot, "Local");
const exportParent = path.join(temporaryRoot, "bundle-export");
const encryptedBundleFile = path.join(temporaryRoot, "bundle.crbundle");
const isolatedPackagedDirectory = path.join(temporaryRoot, "packaged-app");
const executablePath = path.join(isolatedPackagedDirectory, "创作阅读助手.exe");
const isolatedWorkspaceDirectory = path.join(isolatedPackagedDirectory, "data", "CreationWorkspace");
const v9SeedBundle = path.join(temporaryRoot, "packaged-v9-seed.cjs");
const v9BaselineFile = path.join(temporaryRoot, "packaged-v9-baseline.json");

mkdirSync(roamingDirectory, { recursive: true });
mkdirSync(localDirectory, { recursive: true });
mkdirSync(exportParent, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[packaged] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function runOperation(page, request, timeoutMs = 90_000) {
  const initial = await page.evaluate((value) => window.api.operation.start(value), request);
  if (!initial) throw new Error(`Operation was cancelled before start: ${request.kind}`);
  const deadline = Date.now() + timeoutMs;
  let state = initial;
  while (state.status === "running" || state.status === "cancelling") {
    if (Date.now() >= deadline) throw new Error(`Operation timed out: ${request.kind}`);
    await new Promise((resolve) => setTimeout(resolve, 100));
    state = await page.evaluate((operationId) => window.api.operation.getState(operationId), initial.operationId);
    if (!state) throw new Error(`Operation state disappeared: ${initial.operationId}`);
  }
  if (state.status !== "completed") {
    throw new Error(`Operation ${request.kind} ended as ${state.status}: ${state.error?.message ?? "unknown error"}`);
  }
  return state;
}

async function configureDialogs(app, config) {
  await app.evaluate(({ dialog }, nextConfig) => {
    globalThis.__creationPackagedAcceptanceDialogs = {
      ...(globalThis.__creationPackagedAcceptanceDialogs ?? {}),
      ...nextConfig
    };
    if (globalThis.__creationPackagedAcceptanceDialogsInstalled) return;
    globalThis.__creationPackagedAcceptanceDialogsInstalled = true;
    dialog.showOpenDialog = async (...args) => {
      const options = args.at(-1) ?? {};
      const current = globalThis.__creationPackagedAcceptanceDialogs ?? {};
      const wantsDirectory = Array.isArray(options.properties) && options.properties.includes("openDirectory");
      const selected = wantsDirectory ? current.directoryPath : current.openFilePath;
      return { canceled: !selected, filePaths: selected ? [selected] : [] };
    };
    dialog.showSaveDialog = async () => {
      const selected = globalThis.__creationPackagedAcceptanceDialogs?.saveFilePath;
      return { canceled: !selected, filePath: selected || undefined };
    };
    dialog.showMessageBox = async () => ({
      response: globalThis.__creationPackagedAcceptanceDialogs?.messageResponse ?? 0,
      checkboxChecked: false
    });
  }, config);
}

async function main() {
  check("打包可执行文件存在", existsSync(sourceExecutablePath), sourceExecutablePath);
  cpSync(packagedDirectory, isolatedPackagedDirectory, {
    recursive: true,
    filter: (source) => path.resolve(source).toLowerCase() !== path.resolve(path.join(packagedDirectory, "data")).toLowerCase()
  });
  check("打包目录已复制到隔离沙箱", existsSync(executablePath), executablePath);
  buildSync({
    entryPoints: [path.join(root, "scripts", "packaged-v9-seed.ts")],
    outfile: v9SeedBundle,
    bundle: true,
    platform: "node",
    format: "cjs",
    target: "node20",
    external: ["electron", "better-sqlite3"]
  });
  const seed = spawnSync(path.join(root, "node_modules", "electron", "dist", "electron.exe"), [
    v9SeedBundle,
    isolatedWorkspaceDirectory,
    v9BaselineFile
  ], {
    cwd: root,
    encoding: "utf8",
    env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
    windowsHide: true,
    timeout: 120_000
  });
  check("打包启动前生成真实 v9 双项目工作区", seed.status === 0 && existsSync(v9BaselineFile), seed.stderr.trim());
  const v9Baseline = JSON.parse(await readFile(v9BaselineFile, "utf8"));
  const app = await electron.launch({
    executablePath,
    args: [`--user-data-dir=${roamingDirectory}`],
    cwd: path.dirname(executablePath),
    env: {
      ...process.env,
      APPDATA: roamingDirectory,
      LOCALAPPDATA: localDirectory
    }
  });

  try {
    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData"),
      appPath: electronApp.getAppPath()
    }));
    check("真实打包态启动", runtime.isPackaged === true, JSON.stringify(runtime));
    check(
      "用户数据根完全隔离",
      path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(roamingDirectory).toLowerCase()),
      runtime.userData
    );

    const migratedV9 = await page.evaluate(async (baseline) => {
      const projects = await window.api.creation.listProjects();
      const cardsA = await window.api.creation.cardsList({ kind: "cards.list", projectId: baseline.projectA });
      const cardsB = await window.api.creation.cardsList({ kind: "cards.list", projectId: baseline.projectB });
      return {
        projectIds: projects.map((project) => project.id),
        cardA: cardsA.find((card) => card.id === baseline.cardASkill),
        cardB: cardsB.find((card) => card.id === baseline.cardBCharacter)
      };
    }, v9Baseline);
    const migrationBackupNames = (await readdir(path.dirname(isolatedWorkspaceDirectory)))
      .filter((name) => name.startsWith("CreationWorkspace.v9-to-v10-backup-"));
    check(
      "隔离打包版首次读取完成 v9→v10 并保留稳定 ID/项目关联",
      migratedV9.projectIds.includes(v9Baseline.projectA) &&
        migratedV9.projectIds.includes(v9Baseline.projectB) &&
        migratedV9.cardA?.linkedProjectIds.includes(v9Baseline.projectA) &&
        migratedV9.cardB?.linkedProjectIds.includes(v9Baseline.projectB),
      JSON.stringify(migratedV9)
    );
    check(
      "v9→v10 升级前备份已落盘",
      migrationBackupNames.length === 1 &&
        existsSync(path.join(path.dirname(isolatedWorkspaceDirectory), migrationBackupNames[0], "backup-manifest.json")),
      JSON.stringify(migrationBackupNames)
    );

    await page.getByRole("button", { name: "新建项目" }).first().click();
    await page.getByRole("dialog", { name: "新建作品" }).waitFor({ state: "visible", timeout: 15_000 });
    await page.getByRole("button", { name: /下一步/ }).click();
    await page.locator(".creation-field input").first().fill("打包验收项目 A");
    await page.getByRole("button", { name: /下一步/ }).click();
    await page.getByRole("button", { name: /创建项目/ }).click();
    await page.waitForSelector(".project-nav-back", { timeout: 15_000 });
    const projectA = await page.evaluate(async () => {
      const projects = await window.api.creation.listProjects();
      return projects.find((project) => project.title === "打包验收项目 A");
    });
    check("UI 向导经真实 IPC 创建项目 A", Boolean(projectA?.id), projectA?.id ?? "missing");

    const setup = await page.evaluate(async (projectAId) => {
      const projectB = await window.api.creation.createProject({
        title: "打包验收项目 B",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
      });
      const created = await window.api.creation.runStructure({
        type: "card.create",
        projectId: projectAId,
        kind: "character",
        title: "打包共享卡片",
        aliases: ["共享角色"],
        tags: ["验收"]
      });
      await window.api.creation.cardLink(projectB.project.id, created.entityId);
      return { projectBId: projectB.project.id, cardId: created.entityId };
    }, projectA.id);
    check("创建项目 B 并关联同一稳定 ID 卡片", Boolean(setup.projectBId && setup.cardId), JSON.stringify(setup));

    const syncResult = await page.evaluate(async ({ projectAId, projectBId, cardId }) => {
      const before = await window.api.creation.cardRead(cardId);
      await window.api.creation.runStructure({
        type: "card.update",
        cardId,
        title: "打包共享卡片（同步后）",
        baseRevision: before.revision
      });
      const [cardsA, cardsB] = await Promise.all([
        window.api.creation.cardsList({ kind: "cards.list", projectId: projectAId }),
        window.api.creation.cardsList({ kind: "cards.list", projectId: projectBId })
      ]);
      return {
        a: cardsA.find((card) => card.id === cardId),
        b: cardsB.find((card) => card.id === cardId)
      };
    }, { projectAId: projectA.id, projectBId: setup.projectBId, cardId: setup.cardId });
    check(
      "跨项目读取同一全局卡片的更新",
      syncResult.a?.title === "打包共享卡片（同步后）" && syncResult.b?.title === syncResult.a.title,
      JSON.stringify(syncResult)
    );

    const unlinkResult = await page.evaluate(async ({ projectBId, cardId }) => {
      const result = await window.api.creation.cardUnlink(projectBId, cardId);
      const cards = await window.api.creation.cardsList({ kind: "cards.list", projectId: projectBId });
      return { result, remains: cards.some((card) => card.id === cardId) };
    }, { projectBId: setup.projectBId, cardId: setup.cardId });
    check("项目 B 解绑不删除全局卡片", unlinkResult.result.linked === false && unlinkResult.remains === false, JSON.stringify(unlinkResult));

    const recoveryResult = await page.evaluate(async ({ projectAId, cardId }) => {
      await window.api.creation.runStructure({ type: "card.delete", cardId });
      const trash = await window.api.creation.trashList();
      const hidden = !(await window.api.creation.cardsList({ kind: "cards.list", projectId: projectAId }))
        .some((card) => card.id === cardId);
      await window.api.creation.runStructure({ type: "trash.restore", entity: "card", entityId: cardId });
      const restored = (await window.api.creation.cardsList({ kind: "cards.list", projectId: projectAId }))
        .some((card) => card.id === cardId);
      return { inTrash: trash.some((item) => item.entity === "card" && item.id === cardId), hidden, restored };
    }, { projectAId: projectA.id, cardId: setup.cardId });
    check("全局卡片移入回收站并恢复", recoveryResult.inTrash && recoveryResult.hidden && recoveryResult.restored, JSON.stringify(recoveryResult));

    await configureDialogs(app, { directoryPath: exportParent, messageResponse: 2 });
    const normalExport = await runOperation(page, { kind: "bundle.export", projectId: projectA.id });
    const bundleDirectory = normalExport.result?.result?.directory;
    check("普通项目包通过 operation 导出", Boolean(bundleDirectory && existsSync(path.join(bundleDirectory, "project.json"))), bundleDirectory ?? "missing");

    await page.evaluate(async (cardId) => {
      const card = await window.api.creation.cardRead(cardId);
      await window.api.creation.runStructure({
        type: "card.update",
        cardId,
        title: "本机导出后修改",
        baseRevision: card.revision
      });
    }, setup.cardId);
    await configureDialogs(app, { directoryPath: bundleDirectory, messageResponse: 2 });
    const normalImport = await runOperation(page, { kind: "bundle.import" });
    const normalImportResult = normalImport.result?.result;
    const normalMapping = normalImportResult?.cardMappings?.find((mapping) => mapping.sourceCardId === setup.cardId);
    const normalImportProbe = await page.evaluate(async ({ localCardId, importedProjectId }) => {
      const local = await window.api.creation.cardRead(localCardId);
      const importedCards = await window.api.creation.cardsList({ kind: "cards.list", projectId: importedProjectId });
      return { local, importedCards };
    }, { localCardId: setup.cardId, importedProjectId: normalImportResult.projectId });
    check(
      "普通包冲突选择导入副本并完整映射",
      normalMapping?.action === "copied" &&
        normalMapping.targetCardId !== setup.cardId &&
        normalImportProbe.local?.title === "本机导出后修改" &&
        normalImportProbe.importedCards.some((card) => card.id === normalMapping.targetCardId && card.title === "打包共享卡片（同步后）"),
      JSON.stringify({ mapping: normalMapping, localTitle: normalImportProbe.local?.title })
    );

    await configureDialogs(app, { saveFilePath: encryptedBundleFile, messageResponse: 2 });
    const encryptedExport = await runOperation(page, {
      kind: "bundle.export-encrypted",
      projectId: projectA.id,
      passphrase: "packaged-acceptance-passphrase"
    });
    check("加密项目包通过 operation 导出", existsSync(encryptedBundleFile), JSON.stringify(encryptedExport.result?.result));

    await page.evaluate(async (cardId) => {
      const card = await window.api.creation.cardRead(cardId);
      await window.api.creation.runStructure({
        type: "card.update",
        cardId,
        title: "本机加密导出后修改",
        baseRevision: card.revision
      });
    }, setup.cardId);
    await configureDialogs(app, { openFilePath: encryptedBundleFile, messageResponse: 2 });
    const encryptedImport = await runOperation(page, {
      kind: "bundle.import-encrypted",
      passphrase: "packaged-acceptance-passphrase"
    });
    const encryptedImportResult = encryptedImport.result?.result;
    const encryptedMapping = encryptedImportResult?.cardMappings?.find((mapping) => mapping.sourceCardId === setup.cardId);
    const encryptedCards = await page.evaluate(
      (projectId) => window.api.creation.cardsList({ kind: "cards.list", projectId }),
      encryptedImportResult.projectId
    );
    check(
      "加密包冲突复用同一预检并导入副本",
      encryptedMapping?.action === "copied" &&
        encryptedMapping.targetCardId !== setup.cardId &&
        encryptedCards.some((card) => card.id === encryptedMapping.targetCardId && card.title === "本机导出后修改"),
      JSON.stringify({ mapping: encryptedMapping })
    );

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    const finalProjects = await page.evaluate(async () => {
      const [listed, home] = await Promise.all([
        window.api.creation.listProjects(),
        window.api.creation.readProjectHome()
      ]);
      return {
        listed: listed.map((project) => ({ id: project.id, title: project.title })),
        home: home.projects.map((project) => ({ id: project.id, title: project.title }))
      };
    });
    const projectCount = await page.locator(".project-home-item").count();
    const uniqueListedIds = new Set(finalProjects.listed.map((project) => project.id));
    const uniqueHomeIds = new Set(finalProjects.home.map((project) => project.id));
    check(
      "导入后重载项目首页仍可见且无重复",
      projectCount === 6 && finalProjects.listed.length === 6 && uniqueListedIds.size === 6 &&
        finalProjects.home.length === 6 && uniqueHomeIds.size === 6,
      JSON.stringify({ projectCount, ...finalProjects })
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "packaged-stage1-final.png"), fullPage: true });

    const report = {
      generatedAt: new Date().toISOString(),
      sourceExecutablePath,
      runtime,
      isolatedRoot: temporaryRoot,
      allPass: checks.every((item) => item.ok),
      checks
    };
    writeFileSync(path.join(evidenceDirectory, "packaged-stage1-log.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8");
    console.log(`[packaged] ALL PASS — ${checks.length} checks`);
  } finally {
    await app.close();
  }
}

main()
  .catch((error) => {
    console.error(`[packaged] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    process.exitCode = 1;
  })
  .finally(() => {
    try {
      rmSync(temporaryRoot, { recursive: true, force: true });
    } catch {
      // Best-effort cleanup of this script's own mkdtemp directory only.
    }
  });
