/**
 * Stage 4-A 增强：卡片列表封面缩略图 —— 真实 Electron 验收（dev 模式）。
 *
 * 覆盖「端到端」这一段：真实挑选一张 PNG 作为卡片封面（打桩 showOpenDialog
 * 指向真实文件），验证：
 *   1. `cards.list` 一次性带出 `coverResourceId`（后端 SQL 子查询，非前端补查）；
 *   2. 列表项真的渲染出 `img[src^="creation-asset://"]`，无需点开详情；
 *   3. 图片字节能经主进程只读协议真正加载成功（onLoad，不是 onError 破图）；
 *   4. 无封面的卡片仍降级为占位，不出现 img。
 *
 * 组件三态与 SQL 契约分别由 card-cover-thumb.test.tsx 与
 * global-card-library-contract.ts 覆盖；本脚本补的是「真机能加载出图」。
 */
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-stage4a-"));
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-card-cover");
mkdirSync(evidenceDirectory, { recursive: true });

// 用一张真实存在的 PNG 作为封面素材（取既有验收截图，避免凭空造图）。
const screenshotSource = path.join(root, "output", "playwright", "stage4-relation-graph");
const samplePng = readdirSync(screenshotSource).find((name) => name.endsWith(".png"));
if (!samplePng) {
  console.error("[stage4-card-cover] 找不到可用的 PNG 素材");
  process.exit(1);
}
const coverFilePath = path.join(profileDirectory, "cover.png");
copyFileSync(path.join(screenshotSource, samplePng), coverFilePath);

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[stage4-card-cover] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function main() {
  check("封面素材 PNG 已就位", existsSync(coverFilePath), coverFilePath);

  const app = await electron.launch({
    args: ["--in-process-gpu", `--user-data-dir=${profileDirectory}`, path.join(root, "out", "main", "index.js")],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDirectory }
  });

  try {
    // 打桩「选择封面」对话框，指向真实 PNG。
    await app.evaluate(({ dialog }, coverPath) => {
      dialog.showOpenDialog = async () => ({ canceled: false, filePaths: [coverPath] });
    }, coverFilePath);

    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.waitForFunction(
      () => window.api && window.api.creation && typeof window.api.creation.cardsList === "function",
      undefined,
      { timeout: 45_000 }
    );

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData")
    }));
    check(
      "开发态真实 Electron 且用户数据根隔离到临时目录",
      runtime.isPackaged === false &&
        path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(profileDirectory).toLowerCase()),
      JSON.stringify(runtime)
    );

    // 进「卡片库」页。
    await page.getByRole("button", { name: "卡片库", exact: true }).first().click();
    await page.waitForTimeout(700);

    // 建两张卡：一张设封面、一张不设。
    const ids = await page.evaluate(async () => {
      await window.api.creation.listProjects();
      const covered = await window.api.creation.runStructure({
        type: "card.create",
        kind: "character",
        title: "有封面的角色",
        aliases: [],
        tags: []
      });
      const bare = await window.api.creation.runStructure({
        type: "card.create",
        kind: "character",
        title: "无封面的角色",
        aliases: [],
        tags: []
      });
      return {
        coveredId: covered?.entityId ?? "",
        bareId: bare?.entityId ?? ""
      };
    });
    check(
      "已创建两张卡片（一张待设封面）",
      ids.coveredId.startsWith("card-") && ids.bareId.startsWith("card-"),
      JSON.stringify(ids)
    );

    // 给第一张设封面（走真实 attachResource，dialog 已打桩）。
    // 两个真实形状陷阱：
    // 1. 签名是位置参数 (projectId, cardId, role)，不是对象；
    // 2. 结果字段是 `resourceId`，不是 `id`（ResourceResult 类型如此）。
    const attached = await page.evaluate(async (cardId) => {
      const result = await window.api.creation.attachResource(undefined, cardId, "cover");
      return { canceled: result?.canceled ?? null, resourceId: result?.resource?.resourceId ?? null };
    }, ids.coveredId);
    check(
      "封面已通过真实 attachResource 写入",
      attached.canceled === false && typeof attached.resourceId === "string",
      JSON.stringify(attached)
    );

    // 刷新列表，让 store 重新拉 cards.list。
    await page.reload();
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });
    await page.waitForFunction(
      () => window.api && window.api.creation && typeof window.api.creation.cardsList === "function",
      undefined,
      { timeout: 45_000 }
    );
    await page.getByRole("button", { name: "卡片库", exact: true }).first().click();
    await page.waitForTimeout(900);

    // 断言 1：cards.list 带出 coverResourceId（后端一次性查出）。
    const summary = await page.evaluate(async (expected) => {
      const list = await window.api.creation.cardsList({ kind: "cards.list" });
      const covered = list.find((item) => item.id === expected.coveredId);
      const bare = list.find((item) => item.id === expected.bareId);
      return {
        coveredCoverId: covered?.coverResourceId ?? null,
        bareCoverId: bare?.coverResourceId ?? null,
        total: list.length
      };
    }, ids);
    check(
      "cards.list 一次性带出 coverResourceId（有封面的卡）",
      typeof summary.coveredCoverId === "string" && summary.coveredCoverId.length > 0,
      JSON.stringify(summary)
    );
    check(
      "无封面的卡 coverResourceId 为 null（前端据此降级占位）",
      summary.bareCoverId === null,
      String(summary.bareCoverId)
    );

    // 断言 2：列表项渲染出 creation-asset 缩略图，且只有一张。
    const thumbs = await page.evaluate(() =>
      Array.from(document.querySelectorAll(".card-cover-thumb"))
        .filter((node) => node.tagName === "IMG")
        .map((node) => node.getAttribute("src"))
    );
    check(
      "列表里渲染出 1 张 creation-asset 缩略图（无需点开详情）",
      thumbs.length === 1 && thumbs[0].startsWith("creation-asset://card/"),
      JSON.stringify(thumbs)
    );

    // 断言 3：字节能真正加载（onLoad，不是破图）。
    const loaded = await page.evaluate(
      () =>
        new Promise((resolve) => {
          const image = document.querySelector("img.card-cover-thumb");
          if (!image) {
            resolve({ ok: false, reason: "no img" });
            return;
          }
          if (image.complete && image.naturalWidth > 0) {
            resolve({ ok: true, naturalWidth: image.naturalWidth, fromCache: true });
            return;
          }
          const timer = setTimeout(() => resolve({ ok: false, reason: "timeout" }), 15_000);
          image.addEventListener("load", () => {
            clearTimeout(timer);
            resolve({ ok: true, naturalWidth: image.naturalWidth });
          });
          image.addEventListener("error", () => {
            clearTimeout(timer);
            resolve({ ok: false, reason: "error event" });
          });
        })
    );
    check(
      "缩略图字节经只读协议真正加载成功（非破图）",
      loaded.ok === true && loaded.naturalWidth > 0,
      JSON.stringify(loaded)
    );

    await page.screenshot({ path: path.join(evidenceDirectory, "card-cover-list.png") });
    console.log(`[stage4-card-cover] ALL PASS — ${checks.length} checks`);
  } finally {
    await app.close();
  }
}

main().catch((err) => {
  console.error("[stage4-card-cover] FAILED:", err);
  process.exitCode = 1;
});
