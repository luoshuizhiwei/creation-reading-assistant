/**
 * 桌面端 P0 收口 Electron smoke（隔离 profile，绝不触碰真实数据）。
 *
 * 覆盖（真实 Electron + 真实 IPC + UI 驱动）：
 * 1. 未预载项目章节跳转：搜索项目 B 的真实 chapter 结果 → 进入 B 的写作视图并定位首场景；
 * 2. 跨项目卡片跳转：搜索前明确切回项目 A，再点击项目 B 的卡片结果 → B 的 cards 视图；
 * 3. 第 1050 条收件箱搜索：1050 条条目中第 1050 条含独特词 → 搜索命中并深链选中；
 * 4. 摘录 IPC 持久化：直接调用 preload/IPC（inbox.create / card.create reference，
 *    摘录命令结构）→ workspace 落库验证。
 *
 * 未覆盖（如实记录，不冒充）：
 * - 阅读器 UI 选文 → ExcerptPicker → production destination 的完整 UI 摘录链路
 *   未在本 smoke 自动化；摘录由三层测试（共享命令映射 / renderer adapter+hook /
 *   Electron workspace contract）+ 本 smoke 的 IPC 持久化分别验证。
 * - leave guard 拒绝后搜索保持、收件箱深链无重复由 renderer hook/组件测试覆盖
 *   （use-search-actions.test.tsx / inbox-page.test.tsx）。
 */
import { mkdtempSync, existsSync, writeFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

const profileDir = mkdtempSync(path.join(os.tmpdir(), "creation-smoke-profile-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");
const outDir = path.join(__dirname, "visual-evidence");

const evidence = [];
function record(name, ok, detail) {
  evidence.push({ name, ok, detail });
  console.log(`[smoke] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
}

async function main() {
  if (!existsSync(electronPath)) throw new Error("Electron binary not found.");
  if (!existsSync(mainJs)) throw new Error("Main bundle not found. Run electron-vite build first.");

  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(1200);

  // ---------- 1. UI 创建项目 A ----------
  const createFirst = page.locator(".project-home-create-first").first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  await page.waitForSelector(".creation-wizard", { timeout: 15000 });
  await page.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(200);
  await page.locator(".creation-field input").first().fill("测试项目A");
  await page.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(200);
  await page.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForSelector(".project-nav-back", { timeout: 15000 });
  await page.waitForTimeout(500);
  // 返回首页
  await page.locator(".project-nav-back").click();
  await page.waitForSelector(".project-home-list", { timeout: 15000 });
  await page.waitForTimeout(600);
  record("UI 创建项目 A", true, "向导三步创建成功并返回首页");

  // ---------- 2. IPC 准备项目 B / 正文 / 卡片 / 1050 条收件箱 ----------
  const setup = await page.evaluate(async () => {
    const api = window.api.creation;
    const projectB = await api.createProject({
      title: "测试项目B",
      template: "blank",
      weeklyUpdateDays: [],
      chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
    });
    const sceneB = projectB.chapters[0].scenes[0];
    await api.updateSceneBody({
      sceneId: sceneB.id,
      baseRevision: sceneB.revision,
      body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "项目B的独特场景正文内容" }] }] }
    });
    await api.runStructure({
      type: "card.create",
      projectId: projectB.project.id,
      kind: "character",
      title: "项目B独特角色卡"
    });
    for (let index = 1; index <= 1050; index += 1) {
      await api.inboxCreate({
        type: "inbox.create",
        title: index === 1050 ? "第1050条独特搜索词条目" : `批量条目${index}`,
        body: `内容${index}`
      });
    }
    const cards = await api.cardsList({ kind: "cards.list", projectId: projectB.project.id });
    const inboxCount = await api.inboxCount();
    return { projectBId: projectB.project.id, sceneBId: sceneB.id, sceneTitle: sceneB.title, cards, inboxCount };
  });
  record("IPC 准备项目 B + 卡片 + 1050 条收件箱", setup.cards.length >= 1 && setup.inboxCount.total === 1050, JSON.stringify({ cards: setup.cards.length, inboxTotal: setup.inboxCount.total }));

  // IPC 创建的项目不在 store.projects 中：reload 让 renderer 重新拉取项目列表（含 B）。
  await page.reload();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForSelector(".project-home-list", { timeout: 30000 });
  await page.waitForTimeout(800);
  record("reload 后项目列表包含 A 与 B", await page.evaluate(() => (document.body.textContent ?? "").includes("测试项目B")), "首页显示项目 B");

  // ---------- 3. 未预载项目章节跳转（真实 chapter 结果） ----------
  // 给 B 的第一章改名为独特标题，搜索命中 kind=chapter 结果后点击（不用 scene 结果冒充 chapter）。
  const chapterSetup = await page.evaluate(async () => {
    const api = window.api.creation;
    const projects = await api.listProjects();
    const projectB = projects.find((p) => p.title === "测试项目B");
    const nav = await api.readProjectNavigation(projectB.id);
    const chapterId = nav.chapters[0].id;
    await api.runStructure({ type: "chapter.rename", chapterId, title: "项目B独特章节", baseRevision: nav.chapters[0].revision });
    const chapterNav = await api.readProjectNavigation(projectB.id);
    return { chapterId, chapterTitle: chapterNav.chapters[0].title, firstSceneId: chapterNav.chapters[0].scenes[0].id, firstSceneTitle: chapterNav.chapters[0].scenes[0].title };
  });
  record("IPC 准备项目 B 独特章节名", chapterSetup.chapterTitle === "项目B独特章节", JSON.stringify(chapterSetup));

  await page.keyboard.press("Control+k");
  await page.waitForSelector(".uni-search-input", { timeout: 5000 });
  await page.fill(".uni-search-input", "项目B独特章节");
  await page.waitForTimeout(900);
  // 明确选择「章节」分组下的结果（kind=chapter），而不是场景结果
  const chapterGroup = page.locator(".uni-search-group", { hasText: "章节" }).first();
  const chapterResult = chapterGroup.locator("li button", { hasText: "项目B独特章节" }).first();
  if (await chapterResult.isVisible()) {
    await chapterResult.click();
    await page.waitForTimeout(1200);
    const probe = await page.evaluate((sceneTitle) => {
      const overlay = document.querySelector(".uni-search-overlay");
      const heroH2 = document.querySelector(".creation-writing-hero h2")?.textContent ?? "";
      const bodyText = document.body.textContent ?? "";
      return {
        overlayOpen: Boolean(overlay),
        heroH2,
        hasProjectB: bodyText.includes("测试项目B"),
        hasWritingDesc: bodyText.includes("在场景中连续写作"),
        hasChapterFirstScene: bodyText.includes(sceneTitle)
      };
    }, chapterSetup.firstSceneTitle);
    const ok = !probe.overlayOpen && probe.hasProjectB && probe.hasWritingDesc && probe.hasChapterFirstScene;
    record("未预载项目章节跳转（真实 chapter 结果）", ok, JSON.stringify(probe));
  } else {
    record("未预载项目章节跳转（真实 chapter 结果）", false, "章节分组未命中项目 B 独特章节");
  }
  await page.keyboard.press("Escape");

  // ---------- 4. 跨项目卡片跳转（搜索前明确切回项目 A） ----------
  // 当前停在项目 B 的写作视图；先返回首页，再进入项目 A，证明从 A 搜索并跳到 B 的卡片。
  const backButton = page.locator(".project-nav-back").first();
  if (await backButton.isVisible()) {
    await backButton.click();
    await page.waitForSelector(".project-home-list", { timeout: 15000 });
  }
  await page.locator(".project-home-item-main", { hasText: "测试项目A" }).first().click();
  await page.waitForSelector(".project-nav-back", { timeout: 15000 });
  await page.waitForTimeout(600);
  const onProjectA = await page.evaluate(() => {
    const heroH2 = document.querySelector(".creation-writing-hero h2")?.textContent ?? "";
    return heroH2 === "测试项目A";
  });
  record("搜索前切回项目 A", onProjectA, onProjectA ? "已进入项目 A 工作台" : "未回到项目 A");

  await page.keyboard.press("Control+k");
  await page.waitForSelector(".uni-search-input", { timeout: 5000 });
  await page.fill(".uni-search-input", "项目B独特角色卡");
  await page.waitForTimeout(700);
  const cardResult = page.locator(".uni-search-group li button", { hasText: "项目B独特角色卡" }).first();
  if (await cardResult.isVisible()) {
    await cardResult.click();
    await page.waitForTimeout(1200);
    const probe = await page.evaluate(() => {
      const overlay = document.querySelector(".uni-search-overlay");
      const heroH2 = document.querySelector(".creation-writing-hero h2")?.textContent ?? "";
      const heroP = document.querySelector(".creation-writing-hero p")?.textContent?.slice(0, 80) ?? "";
      const bodyText = document.body.textContent ?? "";
      const cardIndex = bodyText.indexOf("项目B独特角色卡");
      const activeCardTitle = document.querySelector(".cards-board-card.active strong, .cards-list-item.active strong")?.textContent ?? "";
      const detailTitle = document.querySelector(".cards-detail-card h3")?.textContent ?? "";
      return {
        overlayOpen: Boolean(overlay),
        heroH2,
        heroP,
        hasCardsDesc: bodyText.includes("管理角色、地点"),
        hasCardTitle: cardIndex >= 0,
        activeCardTitle,
        detailTitle,
        cardContext: cardIndex >= 0 ? bodyText.slice(Math.max(0, cardIndex - 60), cardIndex + 40).replace(/\s+/g, " ") : ""
      };
    });
    // 项目、cards view、active 卡片与详情必须都属于 B；仅列表中出现标题不算跳转成功。
    const ok = !probe.overlayOpen
      && probe.heroH2 === "测试项目B"
      && probe.hasCardsDesc
      && probe.hasCardTitle
      && probe.activeCardTitle === "项目B独特角色卡"
      && probe.detailTitle === "项目B独特角色卡";
    record("跨项目卡片跳转（A→B）", ok, JSON.stringify(probe));
  } else {
    record("跨项目卡片跳转（A→B）", false, "搜索未命中项目 B 卡片");
  }
  await page.keyboard.press("Escape");

  // ---------- 5. 第 1050 条收件箱搜索 ----------
  await page.keyboard.press("Control+k");
  await page.waitForSelector(".uni-search-input", { timeout: 5000 });
  await page.fill(".uni-search-input", "第1050条独特搜索词条目");
  await page.waitForTimeout(2500); // 多页扫描 1050 条需要时间
  const inboxResult = page.locator(".uni-search-group li button", { hasText: "第1050条独特搜索词条目" }).first();
  const inboxHit = await inboxResult.isVisible();
  record("第 1050 条收件箱搜索", inboxHit, inboxHit ? "命中第 1050 条" : "未命中");
  if (inboxHit) {
    await inboxResult.click();
    await page.waitForTimeout(800);
    const onInbox = await page.evaluate(() => !!document.querySelector(".inbox-page"));
    const selectedTitle = await page.evaluate(() => document.querySelector(".inbox-item--selected .inbox-item-title")?.textContent?.trim() ?? "");
    const selected = selectedTitle === "第1050条独特搜索词条目";
    record("收件箱深链选中", onInbox && selected, `inbox 页 + 选中条目标题=${selectedTitle || "未选中"}`);
  }
  await page.keyboard.press("Escape");

  // ---------- 6. 摘录 IPC 持久化（收件箱） ----------
  // 注意：本步骤直接调用 IPC 命令（inbox.create，摘录命令结构），验证 preload→IPC→workspace 落库。
  // 阅读器 UI 选文 → ExcerptPicker → production destination 的完整链路未在此自动化（见 smoke 头部说明）。
  const excerptInbox = await page.evaluate(async () => {
    const api = window.api.creation;
    const result = await api.inboxCreate({
      type: "inbox.create",
      title: "摘录：资料书",
      body: "摘录的正文片段",
      kind: "note",
      status: "inbox",
      tags: ["摘录", "TXT"],
      source: { bookId: "b1", bookTitle: "资料书", format: "txt", excerpt: "摘录的正文片段" }
    });
    const items = await api.inboxList({ kind: "inbox.list", limit: 5 });
    const hit = items.some((item) => item.title === "摘录：资料书" && (item.source && item.source.bookTitle) === "资料书");
    return { itemId: result.itemId, hit };
  });
  record("摘录 IPC 持久化（收件箱 inbox.create）", excerptInbox.hit, `itemId=${excerptInbox.itemId}`);

  // ---------- 7. 摘录 IPC 持久化（reference 资料卡） ----------
  const excerptCard = await page.evaluate(async () => {
    const api = window.api.creation;
    const projects = await api.listProjects();
    const target = projects.find((p) => p.title === "测试项目B") ?? projects[0];
    const result = await api.runStructure({
      type: "card.create",
      projectId: target.id,
      kind: "reference",
      title: "摘录：资料书二",
      tags: ["摘录", "EPUB"],
      content: { bookId: "b2", bookTitle: "资料书二", format: "epub", excerpt: "EPUB 选文" }
    });
    const cards = await api.cardsList({ kind: "cards.list", projectId: target.id });
    const hit = cards.some((card) => card.kind === "reference" && card.title === "摘录：资料书二");
    return { entityId: result.entityId, hit };
  });
  record("摘录 IPC 持久化（reference 资料卡 card.create）", excerptCard.hit, `entityId=${excerptCard.entityId}`);

  // ---------- 截图存档（可复核） ----------
  await page.screenshot({ path: path.join(outDir, "smoke-final.png") });
  record("smoke 截图", true, "scripts/visual-evidence/smoke-final.png");

  await app.close();
  const allPass = evidence.every((item) => item.ok);
  writeFileSync(
    path.join(outDir, "smoke-log.json"),
    `${JSON.stringify({ generatedAt: new Date().toISOString(), allPass, items: evidence }, null, 2)}\n`,
    "utf8"
  );
  console.log(`\n[smoke] ${allPass ? "ALL PASS" : "HAS FAILURES"} — ${evidence.length} checks. Log: scripts/visual-evidence/smoke-log.json`);
  process.exit(allPass ? 0 : 1);
}

main().catch((error) => {
  console.error(`[smoke] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
  process.exitCode = 1;
}).finally(() => {
  try {
    rmSync(profileDir, { recursive: true, force: true });
  } catch {
    // best-effort cleanup
  }
});
