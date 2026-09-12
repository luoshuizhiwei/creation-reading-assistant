/**
 * Stage 3 packaged Electron acceptance for outline metadata and immersive writing.
 *
 * Runs a copied production package with isolated portable data and APPDATA. The
 * native save dialog is redirected to this script's temporary directory only.
 */
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const packagedDirectory = process.env.CREATION_PACKAGED_DIRECTORY
  ? path.resolve(process.env.CREATION_PACKAGED_DIRECTORY)
  : path.join(root, "release-beta-stage3-final", "win-unpacked");
const sourceExecutablePath = path.join(packagedDirectory, "创作阅读助手.exe");
const evidenceDirectory = path.join(root, "output", "playwright");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-packaged-stage3-"));
const isolatedPackagedDirectory = path.join(temporaryRoot, "packaged-app");
const executablePath = path.join(isolatedPackagedDirectory, "创作阅读助手.exe");
const roamingDirectory = path.join(temporaryRoot, "Roaming");
const localDirectory = path.join(temporaryRoot, "Local");
const exportedOutlinePath = path.join(temporaryRoot, "stage3-outline.md");

mkdirSync(roamingDirectory, { recursive: true });
mkdirSync(localDirectory, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[packaged-stage3] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
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
    await app.evaluate(({ dialog }, saveFilePath) => {
      dialog.showSaveDialog = async () => ({ canceled: false, filePath: saveFilePath });
    }, exportedOutlinePath);

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData")
    }));
    check(
      "真实打包态且用户数据根隔离",
      runtime.isPackaged === true && path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(roamingDirectory).toLowerCase()),
      JSON.stringify(runtime)
    );

    const seed = await page.evaluate(async () => {
      const navigation = await window.api.creation.createProject({
        title: "Stage 3 打包验收",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
      });
      const chapter = navigation.chapters[0];
      const scene = chapter.scenes[0];
      const saved = await window.api.creation.updateSceneBody({
        sceneId: scene.id,
        baseRevision: scene.revision,
        body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "雨夜里，主角发现了关键线索。" }] }] }
      });
      if (!saved.ok) throw new Error(saved.error.message);
      await window.api.creation.runStructure({
        type: "scene.updatePlanning",
        sceneId: scene.id,
        planning: { targetWords: 40, goal: "发现线索" }
      });
      await window.api.creation.runStructure({
        type: "scene.updateMeta",
        sceneId: scene.id,
        baseRevision: saved.result.revision,
        summary: "雨夜线索改变行动方向。",
        status: "drafting"
      });
      await window.api.creation.runStructure({
        type: "chapter.setStatus",
        chapterId: chapter.id,
        status: "写作中",
        baseRevision: chapter.revision
      });
      const outline = await window.api.creation.readProjectOutline(navigation.project.id);
      return {
        projectId: navigation.project.id,
        sceneId: scene.id,
        wordCount: outline?.wordCount ?? 0
      };
    });
    check("隔离工作区已写入正文、目标、摘要与独立场景状态", seed.wordCount > 0, JSON.stringify(seed));

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage 3 打包验收" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "大纲", exact: true }).click();
    await page.waitForSelector(".outline-page", { timeout: 20_000 });

    const bookCountText = await page.locator(".outline-book-word-count").innerText();
    check("全书字数汇总在大纲页可见", bookCountText.includes(String(seed.wordCount)), bookCountText);
    const aggregateText = await page.locator(".outline-page-main").innerText();
    check("卷、章和场景字数汇总同时可见", (aggregateText.match(new RegExp(`${seed.wordCount}字`, "g")) ?? []).length >= 3, aggregateText.replace(/\s+/g, " ").slice(0, 500));

    await page.locator(".outline-scene-main").first().click();
    const summaryInput = page.getByPlaceholder("这一场发生什么、推动了什么变化？");
    await summaryInput.fill("打包界面修改后的场景摘要。");
    const statusField = page.locator(".scene-planning-full").filter({ hasText: "场景状态" }).locator("select");
    await statusField.selectOption("revising");
    await page.getByRole("button", { name: "保存场景卡" }).click();
    await page.waitForFunction(async ({ projectId, sceneId }) => {
      const outline = await window.api.creation.readProjectOutline(projectId);
      const scenes = [
        ...(outline?.volumes ?? []).flatMap((volume) => volume.chapters.flatMap((chapter) => chapter.scenes)),
        ...(outline?.looseChapters ?? []).flatMap((chapter) => chapter.scenes)
      ];
      const scene = scenes.find((item) => item.id === sceneId);
      return scene?.summary === "打包界面修改后的场景摘要。" && scene.status === "revising";
    }, { projectId: seed.projectId, sceneId: seed.sceneId });
    const distinctState = await page.evaluate(async ({ projectId, sceneId }) => {
      const outline = await window.api.creation.readProjectOutline(projectId);
      const chapter = outline?.volumes[0]?.chapters[0];
      const scene = chapter?.scenes.find((item) => item.id === sceneId);
      return { chapterStatus: chapter?.status, sceneStatus: scene?.status, summary: scene?.summary };
    }, { projectId: seed.projectId, sceneId: seed.sceneId });
    check("场景状态与章节工作流状态分别保存", distinctState.sceneStatus === "revising" && distinctState.chapterStatus === "写作中", JSON.stringify(distinctState));

    await page.evaluate(() => {
      for (const selector of [".outline-page-main", ".outline-page-side"]) {
        const element = document.querySelector(selector);
        if (element instanceof HTMLElement) element.scrollTop = 0;
      }
    });
    await page.screenshot({ path: path.join(evidenceDirectory, "packaged-stage3-outline.png"), fullPage: true });
    await page.getByRole("button", { name: "导出 Markdown 大纲" }).click();
    await page.getByText("Markdown 大纲已导出", { exact: true }).waitFor({ state: "visible", timeout: 10_000 });
    check("Markdown 大纲通过真实 IPC 写入文件", existsSync(exportedOutlinePath), exportedOutlinePath);
    const exportedOutline = readFileSync(exportedOutlinePath, "utf8");
    check(
      "大纲文件包含汇总、摘要、状态与目标且不含正文",
      exportedOutline.includes(`全书 ${seed.wordCount} 字`) &&
        exportedOutline.includes("打包界面修改后的场景摘要。") &&
        exportedOutline.includes("修订中") &&
        exportedOutline.includes("目标 40 字") &&
        !exportedOutline.includes("雨夜里，主角发现了关键线索。"),
      exportedOutline
    );

    await page.getByRole("button", { name: "写作", exact: true }).click();
    await page.waitForSelector(".writing-desk", { timeout: 20_000 });
    await page.waitForSelector(".ProseMirror", { state: "visible", timeout: 20_000 });
    const progress = page.getByLabel("场景目标进度").first();
    await progress.waitFor({ state: "visible", timeout: 10_000 });
    const progressText = await progress.innerText();
    check("写作区底部显示场景目标进度", progressText.includes("目标 40") && progressText.includes("%"), progressText);

    const typewriterButton = page.getByRole("button", { name: "打字机滚动" }).first();
    await typewriterButton.click();
    check("既有打字机模式仍可启用", (await typewriterButton.getAttribute("aria-pressed")) === "true");
    await page.getByRole("button", { name: "专注模式" }).first().click();
    await page.waitForSelector(".desktop-root--focus", { timeout: 5_000 });
    const focusState = await page.evaluate(() => {
      const hidden = (selector) => {
        const element = document.querySelector(selector);
        return !element || getComputedStyle(element).display === "none";
      };
      return {
        titlebarHidden: hidden(".desktop-titlebar"),
        appNavHidden: hidden(".desktop-sidebar"),
        projectHeaderHidden: hidden(".creation-writing-hero"),
        projectNavHidden: hidden(".project-nav"),
        writingHeaderHidden: hidden(".writing-manuscript-head"),
        outlineRailHidden: hidden(".writing-outline"),
        rightRailHidden: hidden(".writing-margin"),
        statusVisible: !hidden(".writing-focus-status"),
        editorVisible: !hidden(".scene-editor") && !hidden(".ProseMirror")
      };
    });
    check("壳级专注仅保留正文与极简状态", Object.values(focusState).every(Boolean), JSON.stringify(focusState));
    const focusGeometry = await page.evaluate(() => {
      const rect = (selector) => {
        const element = document.querySelector(selector);
        if (!(element instanceof HTMLElement)) return null;
        const box = element.getBoundingClientRect();
        return { top: box.top, bottom: box.bottom, height: box.height, width: box.width };
      };
      const scroll = document.querySelector(".writing-scroll");
      const editor = document.querySelector(".scene-editor");
      return {
        root: rect(".desktop-root"),
        workbench: rect(".desktop-workbench"),
        stage: rect(".desktop-stage"),
        canvas: rect(".desktop-canvas"),
        page: rect(".creation-writing-page"),
        stack: rect(".creation-writing-stack"),
        projectWorkbench: rect(".project-workbench"),
        projectMain: rect(".project-workbench-main"),
        desk: rect(".writing-desk"),
        scroll: scroll instanceof HTMLElement ? { ...rect(".writing-scroll"), topOffset: scroll.scrollTop, scrollHeight: scroll.scrollHeight, clientHeight: scroll.clientHeight } : null,
        editor: rect(".scene-editor"),
        prose: rect(".ProseMirror"),
        text: editor?.textContent?.replace(/\s+/g, " ").trim() ?? "",
        color: editor instanceof HTMLElement ? getComputedStyle(editor).color : ""
      };
    });
    check(
      "专注状态下正文编辑器实际位于写作台可视区域",
      Boolean(
        focusGeometry.desk && focusGeometry.editor &&
        focusGeometry.editor.top >= focusGeometry.desk.top - 1 &&
        focusGeometry.editor.bottom <= focusGeometry.desk.bottom + 1 &&
        focusGeometry.editor.height > 100 &&
        focusGeometry.text.includes("雨夜里")
      ),
      JSON.stringify(focusGeometry)
    );
    await page.evaluate(() => {
      const scroll = document.querySelector(".writing-scroll");
      if (scroll instanceof HTMLElement) scroll.scrollTop = 0;
    });
    await page.screenshot({ path: path.join(evidenceDirectory, "packaged-stage3-focus.png"), fullPage: true });

    await page.keyboard.press("Escape");
    await page.waitForFunction(() => !document.querySelector(".desktop-root")?.classList.contains("desktop-root--focus"));
    check("ESC 退出专注并恢复应用壳", await page.locator(".desktop-sidebar").isVisible());
    check("退出专注后打字机开关保持启用", (await typewriterButton.getAttribute("aria-pressed")) === "true");

    const report = {
      generatedAt: new Date().toISOString(),
      sourceExecutablePath,
      runtime,
      allPass: checks.every((item) => item.ok),
      checks
    };
    writeFileSync(path.join(evidenceDirectory, "packaged-stage3-log.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8");
    console.log(`[packaged-stage3] ALL PASS — ${checks.length} checks`);
  } finally {
    await app.close();
  }
}

main()
  .catch((error) => {
    console.error(`[packaged-stage3] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    process.exitCode = 1;
  })
  .finally(() => {
    try {
      rmSync(temporaryRoot, { recursive: true, force: true });
    } catch {
      // Best-effort cleanup of this script's own temporary directory only.
    }
  });
