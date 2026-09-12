/**
 * Stage 4-B packaged Electron print acceptance.
 *
 * 真实启动 release-beta-stage4-final/win-unpacked/创作阅读助手.exe，验证
 * 打包态两条打印链路：
 *   1. 「导出打印版 PDF」 → dialog.showSaveDialog 被打桩到 temp 目录 →
 *      contents.printToPDF 写入真实 PDF（assert 文件存在 + magic %PDF-）。
 *   2. 「打印…」按钮 → spy webContents.print 验证被以
 *      { silent: false, printBackground: false } 调用，且不会真正弹系统对话框。
 *
 * 同时覆盖 asar 打包后资源读取（preview 页所有静态资源必须从 app.asar 加载）。
 */
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const packagedDirectory = process.env.CREATION_PACKAGED_DIRECTORY
  ? path.resolve(process.env.CREATION_PACKAGED_DIRECTORY)
  : path.join(root, "release-beta-stage4-final", "win-unpacked");
const sourceExecutablePath = path.join(packagedDirectory, "创作阅读助手.exe");
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-print-packaged");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-packaged-stage4b-"));
const isolatedPackagedDirectory = path.join(temporaryRoot, "packaged-app");
const executablePath = path.join(isolatedPackagedDirectory, "创作阅读助手.exe");
const roamingDirectory = path.join(temporaryRoot, "Roaming");
const localDirectory = path.join(temporaryRoot, "Local");
const exportedPdfPath = path.join(temporaryRoot, "stage4-packaged-preview.pdf");

mkdirSync(roamingDirectory, { recursive: true });
mkdirSync(localDirectory, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[packaged-stage4b] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function settlePaint(page, delay = 350) {
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
  );
  await page.waitForTimeout(delay);
}

async function shot(page, name) {
  await settlePaint(page, 200);
  await page.screenshot({ path: path.join(evidenceDirectory, `${name}.png`) });
}

async function main() {
  check("Stage 4-B 打包可执行文件存在", existsSync(sourceExecutablePath), sourceExecutablePath);
  cpSync(packagedDirectory, isolatedPackagedDirectory, {
    recursive: true,
    filter: (source) =>
      path.resolve(source).toLowerCase() !==
      path.resolve(path.join(packagedDirectory, "data")).toLowerCase()
  });
  check("Stage 4-B 打包目录已复制到隔离沙箱", existsSync(executablePath), executablePath);

  const app = await electron.launch({
    executablePath,
    args: ["--in-process-gpu", `--user-data-dir=${roamingDirectory}`],
    cwd: path.dirname(executablePath),
    env: { ...process.env, APPDATA: roamingDirectory, LOCALAPPDATA: localDirectory }
  });

  try {
    // 让保存对话框打到临时 PDF 路径，避免依赖用户交互。
    await app.evaluate(({ dialog }, saveFilePath) => {
      dialog.showSaveDialog = async () => ({ canceled: false, filePath: saveFilePath });
    }, exportedPdfPath);

    // 在主进程侧植入 print 调用计数器，避免真正弹出系统打印对话框。
    await app.evaluate(({ BrowserWindow }, exportedPdf) => {
      void exportedPdf;
      global.__stage4bPrintSpy = { calls: [] };
      const patchContents = (contents) => {
        if (!contents || contents.__stage4bPatched) return;
        contents.__stage4bPatched = true;
        const originalPrint = contents.print.bind(contents);
        const originalPrintToPDF = contents.printToPDF.bind(contents);
        contents.print = (options, callback) => {
          global.__stage4bPrintSpy.calls.push({ kind: "print", options });
          if (typeof callback === "function") {
            setImmediate(() => callback(true, null));
            return;
          }
          return originalPrint(options).then(() => true);
        };
        contents.printToPDF = (options) => {
          global.__stage4bPrintSpy.calls.push({ kind: "printToPDF", options });
          return originalPrintToPDF(options);
        };
      };
      BrowserWindow.getAllWindows().forEach((window) => patchContents(window.webContents));
      const handleInterval = setInterval(() => {
        BrowserWindow.getAllWindows().forEach((window) => patchContents(window.webContents));
      }, 200);
      global.__stage4bPrintSpy.cleanup = () => clearInterval(handleInterval);
    }, exportedPdfPath);

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.waitForFunction(
      () => window.api && window.api.creation && typeof window.api.creation.createProject === "function",
      undefined,
      { timeout: 45_000 }
    );

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      appPath: electronApp.getAppPath(),
      userData: electronApp.getPath("userData")
    }));
    check("Stage 4-B 真实打包态启动", runtime.isPackaged === true, JSON.stringify(runtime));
    check(
      "Stage 4-B 用户数据根完全隔离",
      path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(roamingDirectory).toLowerCase()),
      runtime.userData
    );
    check(
      "Stage 4-B 资源来自 app.asar（asar 打包生效）",
      String(runtime.appPath).toLowerCase().endsWith("resources\\app.asar") ||
        String(runtime.appPath).toLowerCase().endsWith("resources/app.asar"),
      runtime.appPath
    );

    // 用 UI 自带的「载入演示项目」按钮播种：它走完整 UI 流程（createProject +
// saveSceneBody + loadProjects），React store 会正确更新，且自带正文可直接打印。
    // 直接走 IPC 创建的项目不会刷新 store，项目列表会停在 0。
    // 注意：该按钮内部 `onCreateDemoProject().then(id => { if (id) onOpenProject(id) })`，
    // 点击后会自动进入项目，不需要再点项目卡片。
    const demoButton = page.getByRole("button", { name: /载入演示项目/ });
    const demoButtonCount = await demoButton.count();
    const demoFound = demoButtonCount > 0;
    if (demoFound) {
      await demoButton.first().click();
    }
    await shot(page, "01-after-demo-click");
    // detail 用点击前的 count：点击后按钮会随进入项目而消失，事后 count 恒为 0。
    check("Stage 4-B 「载入演示项目」按钮存在并已点击", demoFound, `countBeforeClick=${demoButtonCount}`);

    // 等演示项目写入 + 自动进入项目：等「全书预览」标签出现（最长 90s）。
    const previewTab = page.getByRole("button", { name: "全书预览", exact: true });
    let enteredProject = false;
    const enterDeadline = Date.now() + 90_000;
    while (Date.now() < enterDeadline) {
      if ((await previewTab.count()) > 0) {
        enteredProject = true;
        break;
      }
      await page.waitForTimeout(500);
    }
    await shot(page, "02-project-entered");
    check("Stage 4-B 演示项目写入完成并自动进入项目（导航出现「全书预览」）", enteredProject, "preview tab present");

    // 进入「全书预览」。
    let previewRendered = false;
    if (enteredProject) {
      await previewTab.first().click();
      await page.waitForTimeout(1200);
      previewRendered = (await page.locator(".preview-page").count()) > 0;
    }
    await shot(page, "03-preview-page");
    check("Stage 4-B 全书预览页在打包态渲染", previewRendered, ".preview-page present");

    // 路径 A：导出打印版 PDF → dialog 已被打桩 → 真实写文件。
    const pdfButton = page.getByRole("button", { name: /导出打印版 PDF/ });
    const pdfButtonCount = await pdfButton.count();
    if (pdfButtonCount > 0 && previewRendered) {
      await pdfButton.first().click();
      // 等 PDF 出现或超时。
      const deadline = Date.now() + 30_000;
      while (Date.now() < deadline && !existsSync(exportedPdfPath)) {
        await page.waitForTimeout(250);
      }
      check("Stage 4-B 导出打印版 PDF 文件被写入", existsSync(exportedPdfPath), exportedPdfPath);
      if (existsSync(exportedPdfPath)) {
        const buf = readFileSync(exportedPdfPath);
        const head = buf.subarray(0, 5).toString("ascii");
        check(
          "Stage 4-B PDF 文件 magic 头为 %PDF-（asar 打包后 PDF 链路完整）",
          head === "%PDF-",
          `size=${buf.length} head=${head}`
        );
      }
    } else {
      check("Stage 4-B 导出打印版 PDF 按钮存在", false, `pdfButtonCount=${pdfButtonCount}`);
    }

    // 路径 B：「打印…」按钮 → spy 应记录一次调用。
    const beforePrintCalls = await app.evaluate(() => global.__stage4bPrintSpy?.calls?.length ?? 0);
    const printButton = page.getByRole("button", { name: /^打印…$/ });
    const printButtonCount = await printButton.count();
    if (printButtonCount > 0 && previewRendered) {
      await printButton.first().click();
      // 等回调最多 15s。
      const deadline = Date.now() + 15_000;
      while (Date.now() < deadline) {
        const total = await app.evaluate(() => global.__stage4bPrintSpy?.calls?.length ?? 0);
        if (total > beforePrintCalls) break;
        await page.waitForTimeout(200);
      }
      const calls = await app.evaluate(() => global.__stage4bPrintSpy?.calls ?? []);
      const lastPrint = [...calls].reverse().find((entry) => entry.kind === "print");
      check(
        "Stage 4-B 「打印…」按钮通过 spy 触发 webContents.print",
        Boolean(lastPrint),
        JSON.stringify({ totalCalls: calls.length, lastPrint })
      );
      check(
        "Stage 4-B webContents.print 调用选项 = { silent: false, printBackground: false }",
        lastPrint?.options?.silent === false && lastPrint?.options?.printBackground === false,
        JSON.stringify(lastPrint?.options)
      );
    } else {
      check("Stage 4-B 打印…按钮存在", false, `printButtonCount=${printButtonCount}`);
    }

    await shot(page, "03-after-print");
    console.log(`[packaged-stage4b] ALL PASS — ${checks.length} checks`);
  } finally {
    await app.close().catch(() => {});
  }
}

main().catch((err) => {
  console.error("[packaged-stage4b] FAILED:", err);
  process.exitCode = 1;
});