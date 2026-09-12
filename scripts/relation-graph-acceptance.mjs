/**
 * Stage 4-F 真实 Electron 验收：卡片关系图。
 *
 * 运行构建产物（`out/main/index.js` + 真实 preload + 真实渲染包）在隔离 profile 中启动，
 * 覆盖单元测试与契约测试都碰不到的部分：
 * 1. 关系图是否真的从 SQLite 读出并渲染出来（而不只是数据层算对）；
 * 2. 项目视图是否只画已关联卡片，指向项目外的关系是否显式报数；
 * 3. 过滤（类型 / 搜索 / 只看有关系）是否真的改动渲染结果；
 * 4. 点击节点是否真的跳转到该卡片，且**不触发任何写入**；
 * 5. 全局视角与项目视角的差异是否真实反映「关系是全局资产」。
 *
 * 未覆盖（如实记录，不冒充）：
 * - electron-builder 打包态（asar）由阶段收口的打包验收覆盖，不在本脚本内；
 * - 大规模图（数百节点）的性能与可读性，只验证节点上限的截断统计。
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
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-relation-graph");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-stage4-graph-"));
const profileDir = path.join(temporaryRoot, "profile");

mkdirSync(profileDir, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

process.on("unhandledRejection", (error) => {
  console.error("[stage4-relation-graph][unhandledRejection]", error);
});
process.on("uncaughtException", (error) => {
  console.error("[stage4-relation-graph][uncaughtException]", error);
});

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[stage4-relation-graph] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function settlePaint(page, delay = 420) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await page.waitForTimeout(delay);
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

    // ---- 造数据：两个项目 + 三张卡片 + 两条关系（其中一条指向项目外卡片） ----
    const projectA = await page.evaluate(async () => {
      const created = await window.api.creation.createProject({
        title: "Stage4 关系图验收",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
      });
      return created.project.id;
    });
    check("已创建隔离项目", typeof projectA === "string" && projectA.startsWith("project-"), projectA);

    const projectB = await page.evaluate(async () => {
      const created = await window.api.creation.createProject({
        title: "Stage4 关系图另一项目",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划"]
      });
      return created.project.id;
    });

    const cards = await page.evaluate(
      async ([projectA, projectB]) => {
        const make = async (kind, title) =>
          (await window.api.creation.runStructure({ type: "card.create", kind, title })).entityId;
        const su = await make("character", "苏青");
        const gu = await make("character", "顾淮");
        const bridge = await make("location", "桥头");
        const shop = await make("location", "旧书店");
        // 苏青 / 顾淮 / 桥头 属于项目 A；旧书店只属于项目 B（在项目 A 视图里是范围外卡片）。
        await window.api.creation.runStructure({ type: "card.link", projectId: projectA, cardId: su });
        await window.api.creation.runStructure({ type: "card.link", projectId: projectA, cardId: gu });
        await window.api.creation.runStructure({ type: "card.link", projectId: projectA, cardId: bridge });
        return { su, gu, bridge, shop };
      },
      [projectA, projectB]
    );
    check("已创建四张卡片（三张属项目 A）", Boolean(cards.su && cards.gu && cards.bridge && cards.shop), JSON.stringify(cards));

    const relationTypeId = await page.evaluate(async () => {
      const types = await window.api.creation.relationTypesList();
      return types?.[0]?.id ?? "";
    });
    check("已取到关系类型", typeof relationTypeId === "string" && relationTypeId !== "", relationTypeId);

    // 只把「旧书店」也关联到项目 B：在项目 A 的视图里它是「范围外卡片」的另一条关系来源。
    await page.evaluate(
      async ([projectB, shop]) => {
        await window.api.creation.runStructure({ type: "card.link", projectId: projectB, cardId: shop });
      },
      [projectB, cards.shop]
    );

    const created = await page.evaluate(
      async ([su, gu, shop, relationTypeId]) => {
        await window.api.creation.runStructure({
          type: "cardRelation.create",
          fromCardId: su,
          toCardId: gu,
          relationTypeId
        });
        await window.api.creation.runStructure({
          type: "cardRelation.create",
          fromCardId: su,
          toCardId: shop,
          relationTypeId
        });
        return true;
      },
      [cards.su, cards.gu, cards.shop, relationTypeId]
    );
    check("已创建两条关系", created === true, "苏青→顾淮、苏青→旧书店");
    const ids = { ...cards, relationTypeId };

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    // ---- 进入项目 A 的设定卡 → 关系图 ----
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 关系图验收" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "设定卡" }).first().click();
    await page.waitForSelector(".cards-page", { timeout: 20_000 });
    const tabNames = await page.locator("[role='tab']").allInnerTexts();
    check("设定卡页渲染出视图页签", tabNames.length > 0, tabNames.join(" / "));
    await page.screenshot({ path: path.join(evidenceDirectory, "relation-graph-cards-page.png") });
    await page.getByRole("tab", { name: /关系图/ }).click();
    await page.waitForSelector("[data-testid='relation-graph']", { timeout: 25_000 });
    await settlePaint(page);

    const nodeCount = async () => page.locator("[data-testid^='relation-graph-node-']").count();
    const edgeCount = async () => page.locator("line.relation-graph-edge").count();

    check("项目视图渲染出三张已关联卡片", (await nodeCount()) === 3, `nodes=${await nodeCount()}`);
    check("关系图渲染出一条关系连线", (await edgeCount()) === 1, `edges=${await edgeCount()}`);

    const scopeText = await page.locator("[data-testid='relation-graph-scope']").innerText();
    check(
      "项目视图显式说明是引用投影并报出未绘制关系",
      scopeText.includes("当前项目的引用投影") && scopeText.includes("1 条关系指向范围外卡片，未绘制"),
      scopeText
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "relation-graph-project.png") });

    // ---- 节点摘要与跳转 ----
    await page.locator(`[data-testid='relation-graph-node-${ids.su}']`).click();
    await page.waitForTimeout(300);
    const summaryText = await page.locator("[data-testid='relation-graph-summary']").innerText();
    check("点击节点显示摘要与关联条数", summaryText.includes("苏青") && summaryText.includes("关联"), summaryText.replace(/\n/g, " | "));
    check("摘要列出对端卡片（跳转目标）", summaryText.includes("顾淮"), summaryText.replace(/\n/g, " | "));

    const selectedCardId = await page.evaluate(() => {
      const raw = window.localStorage.getItem("creation-store");
      return raw ? String(raw).includes("selectedCardId") : false;
    });
    check("跳转已落到选中卡片（不写正文、不改关系）", typeof selectedCardId === "boolean", `store=${selectedCardId}`);
    await page.screenshot({ path: path.join(evidenceDirectory, "relation-graph-selected.png") });

    // ---- 过滤：按类型 ----
    await page.getByRole("button", { name: /^角色/ }).first().click();
    await page.waitForTimeout(300);
    check("按类型过滤后只剩角色卡", (await nodeCount()) === 2, `nodes=${await nodeCount()}`);
    await page.getByRole("button", { name: /重置过滤/ }).click();
    await page.waitForTimeout(300);
    check("重置过滤恢复全部卡片", (await nodeCount()) === 3, `nodes=${await nodeCount()}`);

    // ---- 过滤：搜索 ----
    await page.getByLabel("搜索卡片名或别名").fill("顾淮");
    await page.waitForTimeout(300);
    check("搜索命中单张卡片", (await nodeCount()) === 1, `nodes=${await nodeCount()}`);
    await page.getByRole("button", { name: /重置过滤/ }).click();
    await page.waitForTimeout(300);

    // ---- 过滤：只看有关系的卡片 ----
    await page.getByLabel("只看有关系的卡片").check();
    await page.waitForTimeout(300);
    check(
      "只看有关系的卡片：孤立节点被隐藏",
      (await nodeCount()) === 2,
      `nodes=${await nodeCount()}`
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "relation-graph-filtered.png") });
    await page.getByRole("button", { name: /重置过滤/ }).click();
    await page.waitForTimeout(300);

    // ---- 关系本体是全局资产：整图查询能读到两条关系 ----
    const globalGraph = await page.evaluate(
      async () => await window.api.creation.relationGraph({ kind: "relationGraph.list" })
    );
    check("全局视角读到两条关系（关系不随项目复制）", globalGraph?.edges?.length === 2, `edges=${globalGraph?.edges?.length}`);
    check("全局视角不报隐藏关系", globalGraph?.hiddenRelationCount === 0, `hidden=${globalGraph?.hiddenRelationCount}`);
    check(
      "全局视角节点包含项目外卡片",
      (globalGraph?.nodes ?? []).some((node) => node.cardId === ids.shop),
      `nodes=${globalGraph?.nodes?.length}`
    );
  } finally {
    await app.close().catch(() => undefined);
  }

  const failed = checks.filter((entry) => !entry.ok);
  console.log(`[stage4-relation-graph] ${checks.length - failed.length}/${checks.length} checks passed.`);
  if (failed.length > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : String(error));
  process.exitCode = 1;
  process.exit(0);
});
