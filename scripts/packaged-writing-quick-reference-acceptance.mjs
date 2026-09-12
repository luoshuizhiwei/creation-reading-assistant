/**
 * Stage 2 packaged Electron acceptance for the writing quick-reference flow.
 *
 * The packaged directory is copied into a disposable root because the desktop
 * app intentionally supports executable-adjacent portable data. No user
 * workspace, APPDATA directory, or production AI endpoint is used.
 */
import { cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const packagedDirectory = process.env.CREATION_PACKAGED_DIRECTORY
  ? path.resolve(process.env.CREATION_PACKAGED_DIRECTORY)
  : path.join(root, "release-beta", "win-unpacked");
const sourceExecutablePath = path.join(packagedDirectory, "创作阅读助手.exe");
const evidenceDirectory = path.join(root, "output", "playwright");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-packaged-stage2-"));
const isolatedPackagedDirectory = path.join(temporaryRoot, "packaged-app");
const executablePath = path.join(isolatedPackagedDirectory, "创作阅读助手.exe");
const roamingDirectory = path.join(temporaryRoot, "Roaming");
const localDirectory = path.join(temporaryRoot, "Local");

mkdirSync(roamingDirectory, { recursive: true });
mkdirSync(localDirectory, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[packaged-stage2] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function main() {
  check("打包可执行文件存在", existsSync(sourceExecutablePath), sourceExecutablePath);
  cpSync(packagedDirectory, isolatedPackagedDirectory, {
    recursive: true,
    filter: (source) => path.resolve(source).toLowerCase() !== path.resolve(path.join(packagedDirectory, "data")).toLowerCase()
  });
  check("打包目录已复制到隔离沙箱", existsSync(executablePath), executablePath);

  const app = await electron.launch({
    executablePath,
    args: [`--user-data-dir=${roamingDirectory}`],
    cwd: path.dirname(executablePath),
    env: { ...process.env, APPDATA: roamingDirectory, LOCALAPPDATA: localDirectory }
  });

  try {
    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData")
    }));
    check("真实打包态且用户数据根隔离", runtime.isPackaged === true && path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(roamingDirectory).toLowerCase()), JSON.stringify(runtime));

    const seed = await page.evaluate(async () => {
      const navigation = await window.api.creation.createProject({
        title: "写作速查打包验收",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
      });
      const sceneId = navigation.chapters[0].scenes[0].id;
      const linked = await window.api.creation.runStructure({
        type: "card.create",
        projectId: navigation.project.id,
        kind: "character",
        title: "场景内角色",
        aliases: ["本场景"]
      });
      const global = await window.api.creation.runStructure({
        type: "card.create",
        kind: "character",
        title: "全局候选角色",
        aliases: ["待关联"]
      });
      await window.api.creation.runStructure({
        type: "scene.updatePlanning",
        sceneId,
        planning: { castCardIds: [linked.entityId], goal: "不中断正文完成速查" }
      });
      await window.api.ai.saveApiKey({ apiKey: "isolated-stage2-acceptance-key" });
      await window.api.ai.updateSettings({
        enabled: true,
        baseUrl: "https://example.invalid/v1",
        model: "stage2-acceptance"
      });
      return { projectId: navigation.project.id, sceneId, linkedCardId: linked.entityId, globalCardId: global.entityId };
    });
    check("隔离工作区已创建场景卡片与未关联全局卡片", Boolean(seed.projectId && seed.sceneId && seed.linkedCardId && seed.globalCardId), JSON.stringify(seed));

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：写作速查打包验收" }).click();
    await page.waitForSelector(".writing-desk", { timeout: 30_000 });
    const editor = page.locator(".ProseMirror").first();
    await editor.waitFor({ state: "visible", timeout: 20_000 });
    await editor.click();
    await page.keyboard.type("速查前正文");

    await page.keyboard.press("Control+Shift+K");
    const panel = page.getByRole("complementary", { name: "写作速查" });
    await panel.waitFor({ state: "visible", timeout: 10_000 });
    check("Ctrl+Shift+K 打开真实写作速查面板", await panel.isVisible());

    const initialBox = await panel.boundingBox();
    const resizer = page.getByRole("separator", { name: "调整速查面板宽度" });
    const resizerBox = await resizer.boundingBox();
    if (!initialBox || !resizerBox) throw new Error("无法读取速查面板尺寸");
    await page.mouse.move(resizerBox.x + 4, resizerBox.y + 80);
    await page.mouse.down();
    await page.mouse.move(resizerBox.x - 56, resizerBox.y + 80, { steps: 5 });
    await page.mouse.up();
    const resizedBox = await panel.boundingBox();
    check("拖动索引尺可调整面板宽度且保持 280–480px", Boolean(resizedBox && resizedBox.width > initialBox.width + 40 && resizedBox.width >= 280 && resizedBox.width <= 480), JSON.stringify({ before: initialBox.width, after: resizedBox?.width }));

    await page.getByRole("button", { name: /场景内角色/ }).click();
    const titleInput = page.getByLabel("名称");
    await titleInput.fill("场景内角色（速查修订）");
    await page.getByLabel("速查 AI 上下文").click();
    await panel.getByText("已保存", { exact: true }).waitFor({ state: "visible", timeout: 5_000 });
    const savedCard = await page.evaluate((cardId) => window.api.creation.cardRead(cardId), seed.linkedCardId);
    check("字段失焦 800ms 后经真实 IPC 自动保存且状态可见", savedCard?.title === "场景内角色（速查修订）", savedCard?.title ?? "missing");

    await page.getByRole("button", { name: "全局搜索" }).click();
    await page.getByPlaceholder("搜索名称、别名或字段").fill("全局候选");
    await page.getByRole("button", { name: /全局候选角色/ }).waitFor({ state: "visible", timeout: 5_000 });
    await page.getByRole("button", { name: /全局候选角色/ }).click();
    await page.getByRole("button", { name: "关联到项目" }).click();
    await page.getByRole("button", { name: "已关联项目" }).waitFor({ state: "visible", timeout: 5_000 });
    await page.getByRole("button", { name: "加入本场景" }).click();
    await page.getByRole("button", { name: "已在本场景" }).waitFor({ state: "visible", timeout: 5_000 });
    const linkedState = await page.evaluate(async ({ projectId, sceneId, cardId }) => {
      const card = await window.api.creation.cardRead(cardId);
      const outline = await window.api.creation.readProjectOutline(projectId);
      const scenes = [
        ...(outline?.volumes ?? []).flatMap((volume) => volume.chapters.flatMap((chapter) => chapter.scenes)),
        ...(outline?.chapters ?? []).flatMap((chapter) => chapter.scenes)
      ];
      const scene = scenes.find((item) => item.id === sceneId);
      return { linked: card?.linkedProjectIds.includes(projectId), castCardIds: scene?.planning?.castCardIds ?? [] };
    }, { projectId: seed.projectId, sceneId: seed.sceneId, cardId: seed.globalCardId });
    check("全局搜索结果可关联项目并加入本场景出场", linkedState.linked === true && linkedState.castCardIds.includes(seed.globalCardId), JSON.stringify(linkedState));

    await page.screenshot({ path: path.join(evidenceDirectory, "packaged-stage2-quick-reference.png"), fullPage: true });
    await page.getByRole("button", { name: "回到正文" }).click();
    await panel.waitFor({ state: "detached", timeout: 5_000 });
    await page.waitForFunction(() => document.activeElement?.classList.contains("ProseMirror"));
    await page.keyboard.type("速查后正文");
    check("关闭面板后正文焦点恢复并可继续输入", (await editor.innerText()).includes("速查前正文速查后正文"), await editor.innerText());

    await page.keyboard.press("Control+Shift+K");
    await panel.waitFor({ state: "visible", timeout: 5_000 });
    await page.getByRole("button", { name: "本项目 2" }).click();
    await page.getByRole("button", { name: /场景内角色（速查修订）/ }).click();
    await page.getByRole("button", { name: "关闭写作速查" }).click();
    await panel.waitFor({ state: "detached", timeout: 5_000 });

    const polish = page.getByRole("button", { name: /润色场景/ });
    await polish.waitFor({ state: "visible", timeout: 5_000 });
    await polish.click();
    const aiDialog = page.getByRole("dialog", { name: "AI 发送确认" });
    await aiDialog.waitFor({ state: "visible", timeout: 5_000 });
    const quickGroupToggle = page.getByLabel("包含 写作速查卡片");
    check("场景雷达 AI 确认弹窗可见且速查卡片为独立可排除组", await quickGroupToggle.isVisible());
    await quickGroupToggle.uncheck();
    check("发送前可排除速查上下文组", (await quickGroupToggle.isChecked()) === false);
    await page.screenshot({ path: path.join(evidenceDirectory, "packaged-stage2-ai-context.png"), fullPage: true });

    const report = {
      generatedAt: new Date().toISOString(),
      sourceExecutablePath,
      runtime,
      allPass: checks.every((item) => item.ok),
      checks
    };
    writeFileSync(path.join(evidenceDirectory, "packaged-stage2-log.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8");
    console.log(`[packaged-stage2] ALL PASS — ${checks.length} checks`);
  } finally {
    await app.close();
  }
}

main()
  .catch((error) => {
    console.error(`[packaged-stage2] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    process.exitCode = 1;
  })
  .finally(() => {
    try {
      rmSync(temporaryRoot, { recursive: true, force: true });
    } catch {
      // Best-effort cleanup of this script's own temporary directory only.
    }
  });
