/**
 * Stage 4-E 真实 Electron 验收：校对的全书扫描、结果总数、按位置持久化忽略。
 *
 * 运行构建产物（`out/main/index.js` + 真实 preload + 真实渲染包）在隔离 profile 中启动，
 * 覆盖单元测试与契约测试都碰不到的部分：
 * 1. v11→v12 迁移在真实 SQLite 文件上是否可重复打开，忽略表是否真的落在磁盘；
 * 2. 扫描范围与未忽略/已忽略总数是否真的渲染出来（而不只是数据层算对）；
 * 3. 「忽略此处」是否只作用于被点的那一个位置，同场景另一段相同文本是否仍然报出；
 * 4. 忽略记录是否真的持久化——重启应用后仍然生效。
 *
 * 未覆盖（如实记录，不冒充）：
 * - electron-builder 打包态（asar）由阶段 4 收口的打包验收覆盖，不在本脚本内。
 * - 别名一致性与疑似错拼的召回率/误报率：本脚本只锁定已构造的样本，不做语料级评测。
 */
import { existsSync, mkdirSync, mkdtempSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-proof-ignore");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-stage4-proof-"));
const profileDir = path.join(temporaryRoot, "profile");

mkdirSync(profileDir, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[stage4-proof] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function settlePaint(page, delay = 420) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await page.waitForTimeout(delay);
}

/** 读取面板顶部的「共 N 处问题」数字。 */
async function readTotal(page) {
  const text = await page.locator(".creation-proof-totals strong").first().innerText();
  const matched = /共\s*(\d+)\s*处/.exec(text);
  return matched ? Number(matched[1]) : Number.NaN;
}

async function main() {
  check("Electron 与主进程产物存在", existsSync(electronPath) && existsSync(mainJs), mainJs);

  const app = await electron.launch({
    executablePath: electronPath,
    // --in-process-gpu：本机装有虚拟显示适配器，Chromium 独立 GPU 进程会 FATAL 退出。
    args: ["--in-process-gpu", mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });

  let projectId = "";
  let sceneId = "";

  try {
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

    const seed = await page.evaluate(async () => {
      const workflow = ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"];
      const created = await window.api.creation.createProject({
        title: "Stage4 校对验收",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: workflow
      });
      const id = created.project.id;
      // 词表：主名 + 别名，以及一个无别名的词条（用于疑似错拼基准）。
      await window.api.creation.runStructure({
        type: "card.create",
        projectId: id,
        kind: "character",
        title: "洛水之蔚",
        aliases: ["洛蔚"]
      });
      await window.api.creation.runStructure({
        type: "card.create",
        projectId: id,
        kind: "character",
        title: "慕辰",
        aliases: []
      });
      const navigation = await window.api.creation.readProjectNavigation(id);
      const scene = navigation.chapters[0].scenes[0];
      const body = await window.api.creation.readSceneBody(scene.id);
      const saved = await window.api.creation.updateSceneBody({
        sceneId: scene.id,
        baseRevision: body?.revision ?? scene.revision,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "洛水之蔚站在河边。" }] },
            { type: "paragraph", content: [{ type: "text", text: "洛水之蔚望着对岸。" }] },
            { type: "paragraph", content: [{ type: "text", text: "洛蔚终于开口说话。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕辰走过来。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕辰点了点头。" }] },
            { type: "paragraph", content: [{ type: "text", text: "慕宸没有说话。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他他他站在门口。" }] },
            { type: "paragraph", content: [{ type: "text", text: "他他他也惊呆了。" }] }
          ]
        }
      });
      if (!saved.ok) throw new Error(saved.error.message);
      return { projectId: id, sceneId: scene.id };
    });
    projectId = seed.projectId;
    sceneId = seed.sceneId;
    check("隔离工作区已写入卡片与含各类问题的正文", Boolean(projectId) && Boolean(sceneId), JSON.stringify(seed));

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    // ---- 打开项目并进入本地校对 ----
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 校对验收" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "本地校对" }).first().click();
    await page.waitForSelector(".creation-proof-shell", { timeout: 20_000 });
    await page.waitForSelector(".creation-proof-summary", { timeout: 20_000 });
    await settlePaint(page);

    const scopeText = await page.locator(".creation-proof-scope").first().innerText();
    check("扫描范围显式渲染为全书扫描", scopeText.includes("全书扫描"), scopeText);

    const totalBefore = await readTotal(page);
    check("结果总数显式渲染且非空", Number.isFinite(totalBefore) && totalBefore >= 4, `total=${totalBefore}`);

    const aliasVisible = await page.locator(".creation-proof-item", { hasText: "洛蔚" }).count();
    check("别名一致性命中少数派称呼「洛蔚」", aliasVisible >= 1, `matched=${aliasVisible}`);

    const typoVisible = await page.locator(".creation-proof-item", { hasText: "慕宸" }).count();
    check("疑似错拼命中「慕宸」（基准词「慕辰」）", typoVisible >= 1, `matched=${typoVisible}`);

    // ---- 重复字分组应有两个独立位置 ----
    const repeatedGroup = page.locator(".creation-proof-item", { hasText: "他他他" }).first();
    const locationCount = await repeatedGroup.locator(".creation-proof-location").count();
    check("同文本的两个段落被识别为两个独立位置", locationCount === 2, `locations=${locationCount}`);

    const seventh = repeatedGroup.getByRole("button", { name: /忽略第 7 段/ });
    const eighth = repeatedGroup.getByRole("button", { name: /忽略第 8 段/ });
    check(
      "两个位置各自带「忽略此处」按钮",
      (await seventh.count()) === 1 && (await eighth.count()) === 1,
      "第 7 段 / 第 8 段"
    );

    await page.screenshot({ path: path.join(evidenceDirectory, "proof-before-ignore.png") });

    // ---- 只忽略第 7 段 ----
    await seventh.click();
    await page.waitForFunction(
      (before) => {
        const node = document.querySelector(".creation-proof-totals strong");
        const matched = node ? /共\s*(\d+)\s*处/.exec(node.textContent ?? "") : null;
        return matched !== null && Number(matched[1]) === before - 1;
      },
      totalBefore,
      { timeout: 20_000 }
    );
    const totalAfter = await readTotal(page);
    check("忽略一处后未忽略总数恰好减一", totalAfter === totalBefore - 1, `${totalBefore} → ${totalAfter}`);

    const ignoredText = await page.locator(".creation-proof-ignored-count").first().innerText();
    check("已忽略数量单独呈现，不被算作已解决", ignoredText.includes("1 处已忽略"), ignoredText);

    const stillReported = await page.locator(".creation-proof-item", { hasText: "他他他" }).first().getByRole("button", { name: /忽略第 8 段/ }).count();
    check("同场景另一段的相同文本仍然报出（位置粒度而非文本粒度）", stillReported === 1, "第 8 段仍在列表中");

    const undoVisible = await page.locator(".creation-proof-item", { hasText: "他他他" }).first().getByRole("button", { name: /取消忽略第 7 段/ }).count();
    check("已忽略位置显示「取消忽略」入口", undoVisible === 1, "第 7 段");

    await page.screenshot({ path: path.join(evidenceDirectory, "proof-after-ignore.png") });

    // ---- 忽略记录必须落盘：重启应用后仍生效 ----
    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 校对验收" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "本地校对" }).first().click();
    await page.waitForSelector(".creation-proof-summary", { timeout: 20_000 });
    await settlePaint(page);

    const totalAfterReload = await readTotal(page);
    check(
      "忽略记录持久化：重启应用后总数保持已忽略状态",
      totalAfterReload === totalAfter,
      `reload=${totalAfterReload}, expected=${totalAfter}`
    );
    const persisted = await page.locator(".creation-proof-ignored-count", { hasText: "已保存" }).first().innerText();
    check(
      "重启后仍显示持久化的忽略记录条数",
      /本项目已保存\s*1\s*条忽略记录/.test(persisted),
      persisted
    );

    await page.screenshot({ path: path.join(evidenceDirectory, "proof-after-reload.png") });

    // ---- 取消忽略可恢复 ----
    await page.locator(".creation-proof-item", { hasText: "他他他" }).first().getByRole("button", { name: /取消忽略第 7 段/ }).click();
    await page.waitForFunction(
      (target) => {
        const node = document.querySelector(".creation-proof-totals strong");
        const matched = node ? /共\s*(\d+)\s*处/.exec(node.textContent ?? "") : null;
        return matched !== null && Number(matched[1]) === target;
      },
      totalBefore,
      { timeout: 20_000 }
    );
    check("取消忽略后问题回归", (await readTotal(page)) === totalBefore, `restored to ${totalBefore}`);
  } finally {
    await app.close().catch(() => undefined);
  }

  const failed = checks.filter((entry) => !entry.ok);
  console.log(`[stage4-proof] ${checks.length - failed.length}/${checks.length} checks passed.`);
  if (failed.length > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : String(error));
  process.exitCode = 1;
  process.exit(0);
});
