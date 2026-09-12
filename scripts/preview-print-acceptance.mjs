/**
 * Stage 4-B 真实 Electron 验收：全书只读预览与打印。
 *
 * 运行构建产物（`out/main/index.js` + 真实 preload + 真实渲染包）在隔离 profile 中启动，
 * 覆盖单元测试与契约测试都碰不到的部分：
 * 1. `@media print` 样式表是否真的随 lazy chunk 注入并被加载；
 * 2. 应用外壳是 `height:100vh; overflow:hidden` 的网格，`printToPDF` 是否真的能分页，
 *    而不是被裁成一页；
 * 3. 打印 PDF 的页数是否由正文体量驱动（多章 > 单章），而不是固定产出；
 * 4. 通读页在真实窗口尺寸与窄窗口下是否真的可见、可读、无编辑入口。
 *
 * 未覆盖（如实记录，不冒充）：
 * - 系统打印对话框（`webContents.print` 的 `silent:false` 分支）无法在自动化中确认真实
 *   打印机输出，仅验证其 IPC 契约与失败上报；本脚本只走 `pdf` 分支。
 * - electron-builder 打包态（asar）由阶段 4 收口的打包验收覆盖，不在本脚本内。
 */
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-preview-print");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-stage4-preview-"));
const profileDir = path.join(temporaryRoot, "profile");
const multiChapterPdf = path.join(temporaryRoot, "multi-chapter.pdf");
const singleChapterPdf = path.join(temporaryRoot, "single-chapter.pdf");

mkdirSync(profileDir, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[stage4-preview] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function settlePaint(page, delay = 420) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await page.waitForTimeout(delay);
}

/** PDF 页数：同时取页树声明的 /Count 与 /Type /Page 对象数，两者都记录以便交叉验证。 */
function pdfPageCount(buffer) {
  const text = buffer.toString("latin1");
  const declared = [...text.matchAll(/\/Count\s+(\d+)/g)].map((match) => Number(match[1]));
  const pageObjects = [...text.matchAll(/\/Type\s*\/Page[^s]/g)].length;
  return { declared: declared.length > 0 ? Math.max(...declared) : 0, pageObjects };
}

async function waitForFile(filePath, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (existsSync(filePath) && statSync(filePath).size > 0) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`等待导出文件超时：${filePath}`);
}

/**
 * 正文段落文本。`page.evaluate` 只能序列化数据，不能传函数，因此这里预先生成纯数据。
 * 每章 40 段、每段约 45 字（约 1800 字），单章正文必然超过一页。
 * 这样多章项目的页数下限可以写死为「章数 × 2」：只要打印分页被 flex 祖先吞掉或内容被
 * 截断，页数就会显著低于下限，而不是悄悄少印几页。
 */
const PARAGRAPHS_PER_CHAPTER = 40;
const PARAGRAPHS_PER_PAGE_LOWER_BOUND = 2;
const MULTI_CHAPTER_TEXTS = [1, 2, 3].map((chapterIndex) =>
  Array.from({ length: PARAGRAPHS_PER_CHAPTER }, (_, index) =>
    `第${chapterIndex}章第${index + 1}段：雨落在旧城墙上，主角沿着长巷走到尽头，又在转角处停下，听见远处的钟声。`
  )
);
const SINGLE_CHAPTER_TEXTS = Array.from({ length: PARAGRAPHS_PER_CHAPTER }, (_, index) =>
  `单章第${index + 1}段：雨落在旧城墙上，主角沿着长巷走到尽头，又在转角处停下，听见远处的钟声。`
);

async function main() {
  check("Electron 与主进程产物存在", existsSync(electronPath) && existsSync(mainJs), mainJs);

  const app = await electron.launch({
    executablePath: electronPath,
    // --in-process-gpu：本机装有虚拟显示适配器，Chromium 独立 GPU 进程会 FATAL 退出。
    // 该开关不改变 DOM/CSS 布局、IPC 或 SQLite 行为，验收断言口径不变。
    args: ["--in-process-gpu", mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });

  try {
    // 保存对话框只重定向到本次临时目录，绝不写入用户真实路径。
    await app.evaluate(({ dialog }, target) => {
      globalThis.__stage4SaveTarget = target;
      dialog.showSaveDialog = async () => ({ canceled: false, filePath: globalThis.__stage4SaveTarget });
    }, multiChapterPdf);

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData")
    }));
    check(
      "开发态真实 Electron 且用户数据根隔离到临时目录",
      runtime.isPackaged === false &&
        path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(profileDir).toLowerCase()),
      JSON.stringify(runtime)
    );

    const seed = await page.evaluate(async ({ multiTexts, singleTexts }) => {
      const toParagraphs = (texts) => texts.map((text) => ({ type: "paragraph", content: [{ type: "text", text }] }));
      const workflow = ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"];
      const multi = await window.api.creation.createProject({
        title: "Stage4 多章通读",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: workflow
      });
      await window.api.creation.runStructure({ type: "chapter.create", projectId: multi.project.id, title: "第二章" });
      await window.api.creation.runStructure({ type: "chapter.create", projectId: multi.project.id, title: "第三章" });

      // chapter.create 不会附带默认场景，必须显式补齐，否则空章节无法承载正文。
      let navigation = await window.api.creation.readProjectNavigation(multi.project.id);
      for (const chapter of navigation?.chapters ?? []) {
        if (chapter.scenes.length === 0) {
          await window.api.creation.runStructure({ type: "scene.create", chapterId: chapter.id, title: "默认场景" });
        }
      }
      navigation = await window.api.creation.readProjectNavigation(multi.project.id);
      const chapters = navigation?.chapters ?? [];
      let sceneCount = 0;
      for (let chapterIndex = 0; chapterIndex < chapters.length; chapterIndex += 1) {
        for (const scene of chapters[chapterIndex].scenes) {
          const body = await window.api.creation.readSceneBody(scene.id);
          const saved = await window.api.creation.updateSceneBody({
            sceneId: scene.id,
            baseRevision: body?.revision ?? scene.revision,
            body: {
              type: "doc",
              content: [
                ...toParagraphs(multiTexts[chapterIndex] ?? multiTexts[0]),
                { type: "sceneBreak" },
                { type: "quoteLetter", content: [{ type: "text", text: "此信为证，不必再问。" }] },
                { type: "authorNote", content: [{ type: "text", text: "这里回头补一段伏笔。" }] }
              ]
            }
          });
          if (!saved.ok) throw new Error(saved.error.message);
          sceneCount += 1;
        }
      }

      const single = await window.api.creation.createProject({
        title: "Stage4 单章通读",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: workflow
      });
      const singleNavigation = await window.api.creation.readProjectNavigation(single.project.id);
      const singleScene = singleNavigation.chapters[0].scenes[0];
      const singleBody = await window.api.creation.readSceneBody(singleScene.id);
      const singleSaved = await window.api.creation.updateSceneBody({
        sceneId: singleScene.id,
        baseRevision: singleBody?.revision ?? singleScene.revision,
        body: { type: "doc", content: toParagraphs(singleTexts) }
      });
      if (!singleSaved.ok) throw new Error(singleSaved.error.message);

      return {
        multiProjectId: multi.project.id,
        singleProjectId: single.project.id,
        multiChapterCount: chapters.length,
        sceneCount
      };
    }, { multiTexts: MULTI_CHAPTER_TEXTS, singleTexts: SINGLE_CHAPTER_TEXTS });

    check("隔离工作区已写入多章与单章两个项目", seed.multiChapterCount === 3 && seed.sceneCount === 3, JSON.stringify(seed));

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    // ---- 通读页在真实 UI 中打开 ----
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 多章通读" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "全书预览", exact: true }).click();
    await page.waitForSelector(".preview-page", { timeout: 20_000 });
    await page.waitForSelector(".preview-chapter", { timeout: 20_000 });

    check("项目导航新增「全书预览」并可进入只读通读页", true, "aria-label=全书预览");

    // 打印样式表必须随 lazy chunk 真正加载，否则 @media print 完全不生效。
    const printStylesheetLoaded = await page.evaluate(() =>
      Array.from(document.querySelectorAll('link[rel="stylesheet"]')).some((link) =>
        /PreviewPage-.*\.css/.test(link.getAttribute("href") ?? "")
      )
    );
    check("打印样式表随预览页 chunk 注入并被加载", printStylesheetLoaded, "PreviewPage-*.css");

    const printRuleApplied = await page.evaluate(() => {
      for (const sheet of Array.from(document.styleSheets)) {
        let rules;
        try {
          rules = sheet.cssRules;
        } catch {
          continue;
        }
        for (const rule of Array.from(rules ?? [])) {
          if (rule instanceof CSSMediaRule && rule.conditionText.includes("print")) {
            const text = Array.from(rule.cssRules).map((item) => item.cssText).join("");
            if (text.includes(".desktop-sidebar") && text.includes("overflow: visible")) return true;
          }
        }
      }
      return false;
    });
    check("打印规则含外壳解裁切与隐藏侧栏", printRuleApplied, "@media print → .desktop-sidebar / overflow: visible");

    const structure = await page.evaluate(() => ({
      chapters: document.querySelectorAll(".preview-chapter").length,
      headings: Array.from(document.querySelectorAll(".preview-chapter-heading")).map((node) => node.textContent?.trim() ?? ""),
      scenes: document.querySelectorAll(".preview-scene").length,
      blocks: document.querySelectorAll(".preview-block").length,
      letters: document.querySelectorAll(".preview-block--letter").length,
      notes: document.querySelectorAll(".preview-block--note").length,
      breaks: document.querySelectorAll(".preview-block--break").length,
      editable: document.querySelectorAll('.preview-page [contenteditable], .preview-page textarea, .preview-page input').length,
      summary: document.querySelector(".preview-toolbar-meta p")?.textContent?.trim() ?? ""
    }));
    check("按章节渲染：3 章 / 3 场景全部出现", structure.chapters === 3 && structure.scenes === 3, JSON.stringify(structure.headings));
    check(
      "块级语义渲染到位：引文、作者按、分场符各有独立表现",
      structure.letters >= 3 && structure.notes >= 3 && structure.breaks >= 3,
      `letters=${structure.letters} notes=${structure.notes} breaks=${structure.breaks} blocks=${structure.blocks}`
    );
    check("只读：通读页内不存在任何编辑控件", structure.editable === 0, `editable=${structure.editable}`);
    check("工具条汇总与导出视图同源字数", /共 1 卷 · 3 章 · 3 个场景/.test(structure.summary), structure.summary);

    // 元素存在不等于用户看得见：以通读页自身为参照确认正文落在可视范围内。
    const visibility = await page.evaluate(() => {
      const within = (selector, referenceSelector) => {
        const box = document.querySelector(selector)?.getBoundingClientRect();
        const bounds = document.querySelector(referenceSelector)?.getBoundingClientRect();
        if (!box || !bounds) return false;
        return box.height > 0 && box.width > 0 && box.top >= bounds.top - 1 && box.bottom <= bounds.bottom + 1;
      };
      const firstBlock = document.querySelector(".preview-block");
      return {
        documentVisible: within(".preview-document", ".preview-page"),
        headingVisible: within(".preview-chapter-heading", ".preview-page"),
        firstBlockVisible: firstBlock ? firstBlock.getBoundingClientRect().height > 0 : false,
        toolbarVisible: within(".preview-toolbar", ".preview-page")
      };
    });
    check(
      "通读页、章标题、正文与工具条都真的落在可视范围内",
      visibility.documentVisible && visibility.headingVisible && visibility.firstBlockVisible && visibility.toolbarVisible,
      JSON.stringify(visibility)
    );

    // 切到打印媒体后读取「计算样式」：这正是 printToPDF 消费的样式，能直接暴露
    // 「规则写了但被更高优先级覆盖」「外壳仍是 flex」这类只靠源码断言看不出的问题。
    await page.emulateMedia({ media: "print" });
    const printMedia = await page.evaluate(() => {
      const style = (selector) => {
        const element = document.querySelector(selector);
        return element ? getComputedStyle(element) : null;
      };
      const secondChapter = document.querySelectorAll(".preview-chapter")[1];
      const workbench = style(".project-workbench");
      const stage = style(".desktop-stage");
      return {
        sidebarDisplay: style(".desktop-sidebar")?.display ?? null,
        titlebarDisplay: style(".desktop-titlebar")?.display ?? null,
        toolbarDisplay: style(".preview-toolbar")?.display ?? null,
        heroDisplay: style(".creation-writing-hero")?.display ?? null,
        navDisplay: style(".project-nav")?.display ?? null,
        workbenchDisplay: workbench?.display ?? null,
        workbenchHeight: workbench?.height ?? null,
        workbenchOverflow: workbench?.overflow ?? null,
        stackDisplay: style(".creation-writing-stack")?.display ?? null,
        stageOverflow: stage?.overflow ?? null,
        documentBackground: style(".preview-document")?.backgroundColor ?? null,
        chapterBreakBefore: secondChapter ? getComputedStyle(secondChapter).breakBefore : null
      };
    });
    check(
      "打印媒体计算样式：外壳解裁切为静态块、chrome（含 hero/项目导航）隐藏、逐章强制分页生效",
      printMedia.sidebarDisplay === "none" &&
        printMedia.titlebarDisplay === "none" &&
        printMedia.toolbarDisplay === "none" &&
        printMedia.heroDisplay === "none" &&
        printMedia.navDisplay === "none" &&
        printMedia.workbenchDisplay === "block" &&
        printMedia.workbenchOverflow === "visible" &&
        printMedia.stackDisplay === "block" &&
        printMedia.chapterBreakBefore === "page",
      JSON.stringify(printMedia)
    );
    await settlePaint(page);
    await page.screenshot({ path: path.join(evidenceDirectory, "preview-print-media.png") });
    await page.emulateMedia({ media: null });
    await settlePaint(page);

    await settlePaint(page);
    await page.screenshot({ path: path.join(evidenceDirectory, "preview-dark-default.png") });

    // ---- 窄窗口（低于 900px 断点）----
    await app.evaluate(({ BrowserWindow }) => {
      const [window] = BrowserWindow.getAllWindows();
      window?.setSize(820, 680);
    });
    await settlePaint(page, 600);
    const narrow = await page.evaluate(() => {
      const document_ = document.querySelector(".preview-document");
      const select = document.querySelector(".preview-jump select");
      return {
        documentWidth: document_?.getBoundingClientRect().width ?? 0,
        selectWidth: select?.getBoundingClientRect().width ?? 0,
        toolbarButtons: document.querySelectorAll(".preview-toolbar-actions button").length
      };
    });
    check(
      "窄窗口下正文与工具条仍在可视区且操作入口完整",
      narrow.documentWidth > 200 && narrow.toolbarButtons === 2,
      JSON.stringify(narrow)
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "preview-dark-narrow.png") });

    await app.evaluate(({ BrowserWindow }) => {
      const [window] = BrowserWindow.getAllWindows();
      window?.setSize(1360, 860);
    });
    await settlePaint(page, 600);

    // ---- 导出打印版 PDF（多章）----
    await page.getByRole("button", { name: /导出打印版 PDF/ }).click();
    await waitForFile(multiChapterPdf);
    const multiBuffer = readFileSync(multiChapterPdf);
    const multiPages = pdfPageCount(multiBuffer);
    copyFileSync(multiChapterPdf, path.join(evidenceDirectory, "multi-chapter.pdf"));
    // 下限 = 章数 × 每章至少页数。低于下限说明分页被吞掉或内容被截断。
    const multiPageLowerBound = seed.multiChapterCount * PARAGRAPHS_PER_PAGE_LOWER_BOUND;
    check(
      "多章项目导出为真实 PDF，且分页未被外壳裁掉（页数达到章数×2 下限）",
      multiBuffer.subarray(0, 4).toString("latin1") === "%PDF" && multiPages.pageObjects >= multiPageLowerBound,
      `bytes=${multiBuffer.length} pages=${JSON.stringify(multiPages)} lowerBound=${multiPageLowerBound}`
    );

    // ---- 单章项目对照：页数必须更少，证明分页由正文体量驱动 ----
    await app.evaluate(({ dialog }, target) => {
      globalThis.__stage4SaveTarget = target;
      void dialog;
    }, singleChapterPdf);
    await page.getByRole("button", { name: "项目列表", exact: true }).click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 单章通读" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "全书预览", exact: true }).click();
    await page.waitForSelector(".preview-chapter", { timeout: 20_000 });
    await page.getByRole("button", { name: /导出打印版 PDF/ }).click();
    await waitForFile(singleChapterPdf);
    const singleBuffer = readFileSync(singleChapterPdf);
    const singlePages = pdfPageCount(singleBuffer);
    copyFileSync(singleChapterPdf, path.join(evidenceDirectory, "single-chapter.pdf"));
    check(
      "分页由正文体量驱动：单章项目页数明显少于多章项目",
      singlePages.pageObjects >= PARAGRAPHS_PER_PAGE_LOWER_BOUND && singlePages.pageObjects < multiPages.pageObjects,
      `single=${JSON.stringify(singlePages)} multi=${JSON.stringify(multiPages)}`
    );

    const evidence = {
      runtime,
      seed,
      structure,
      visibility,
      narrow,
      pdf: {
        multiChapter: { bytes: multiBuffer.length, ...multiPages },
        singleChapter: { bytes: singleBuffer.length, ...singlePages }
      },
      checks
    };
    await import("node:fs/promises").then((fs) =>
      fs.writeFile(path.join(evidenceDirectory, "evidence.json"), JSON.stringify(evidence, null, 2), "utf8")
    );

    const passed = checks.filter((item) => item.ok).length;
    console.log(`[stage4-preview] ${passed}/${checks.length} checks passed.`);
    console.log(`[stage4-preview] evidence: ${evidenceDirectory}`);
  } finally {
    // Electron 关闭偶尔会挂住事件循环；这里限时关闭，避免验收脚本以超时退出。
    await Promise.race([app.close().catch(() => undefined), new Promise((resolve) => setTimeout(resolve, 10_000))]);
    rmSync(temporaryRoot, { recursive: true, force: true });
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(`[stage4-preview] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
    process.exit(1);
  });
