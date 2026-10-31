/**
 * BD-223 常驻普查：懒加载路由 chunk 失败后的恢复通道（WCAG 1.4.4 的「不丢内容与功能」侧）。
 *
 * 判据口径：文本放大到 200% 不许丢内容与功能。本 app 的 8 个屏全部走 React.lazy + 动态
 * import；实测（同一文档内注入，本普查复刻的就是这条序列）：
 *   ① 把某条路由 chunk 改名 → 进那一屏 → import() 抛
 *      "Failed to fetch dynamically imported module: file:///…/SettingsPage-xxx.js"，
 *      ErrorBoundary 接管整屏；
 *   ② 文件改回来、再进同一屏：0ms 直接抛，而且**不再发请求**（requestfailed 停在 1）——
 *      Chromium 的模块图把这条 specifier 的失败永久记仇，文档内没有任何重试路径；
 *   ③ 同一条 URL 换个 ?r=2 的 specifier：3ms 成功。
 * 所以「就地重试」对这类失败是假承诺：旧实现「重试当前页面」做的是 setScreen("projects")，
 * 把用户甩去另一屏；整应用重载又落回默认的项目书架。用户唯一的真出路是自己再点回去。
 *
 * 本普查因此判四件事（每条屏都在 200% 缩放下注入；缩放档按「视觉倍率」量 = 窗口内容区 DIP 宽 ÷
 * 页面 CSS 宽，因为实测 webContents.getZoomFactor() 会在 reload 后留在 2 而画面已回到 100%，
 * 只信那个读数就等于在 100% 下判 200% 的题）：
 *   ① 注入必须真咬到：该 chunk 的 requestfailed ≥ 1，且屏内出现兜底卡（否则 LAZY_RED——
 *      注入没生效的绿是空转的绿）；
 *   ② 兜底卡本身在 200% 下要够得着：卡里每颗出路按钮要么整颗在视口内，要么所在容器可由用户滚动
 *      （实测旧写法把第三颗按钮裁到视口底外、祖先链全 overflow:hidden，鼠标滚轮滚不出来，
 *      WCAG 2.1.2 / 1.4.4）；
 *   ③ 兜底卡的**主按钮**（卡片里第一颗 button，用户视线与读屏顺序都在它身上）点下去必须
 *      把用户带回**原来那一屏**：≤15s 内该屏正文选择器出现且兜底卡消失；落到别的屏或
 *      仍停在卡里都判红。点之前先把 chunk 还原——真实世界里用户点这一颗时，故障源（磁盘瞬时
 *      占用、杀软锁文件）多半已经过去；「文件还缺着」那种情况谁来都救不了，不算可达性判据。
 *   ④ 覆盖下限：三条屏全要判定，少一条即判覆盖塌陷。
 * 每条屏判完都把改名的 chunk 还原并关掉窗口（三条屏共用一个文档时，实测跑到第三条渲染进程
 * 会自己 crash，那是 Electron 的事不是判据的事），结束再核对产物目录里没有 .hidden 残留
 * （退出前统一在 finally 里还原，绝不让 reds 分支把产物留在改名态）。
 *
 * 红判据：任一屏未咬到 / 出路按钮滚不进视口 / 主按钮没能带回原屏 / 判定数 < 3 / 产物残留 .hidden
 * → LAZY_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readdirSync, renameSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const assets = path.join(root, "out", "renderer", "assets");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-lazy-recovery-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");
const LAZY_MIN = 3;
const BOUNDARY_TEXT = "页面发生意外错误|这一屏没能载入|没能载入这一屏";
const settle = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const launch = async () => {
  const app = await electron.launch({ executablePath: electronPath, args: [mainJs], cwd: root, env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir } });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

{ const { app } = await launch(); await app.close(); }
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const targets = [
  { label: "设置", chunk: /^SettingsPage-.*\.js$/, body: ".settings-nav" },
  { label: "卡片库", chunk: /^GlobalCardLibraryPage-.*\.js$/, body: ".cards-page.global-card-library" },
  { label: "收件箱", chunk: /^InboxPage-.*\.js$/, body: ".inbox-page" }
];

const reds = [];
const notes = [];
let judged = 0;
let reqfail = 0;
let exitCode = 0;
const hidden = new Set();
const hideChunk = (pattern) => {
  const name = readdirSync(assets).find((f) => pattern.test(f));
  if (!name) throw new Error(`产物里找不到匹配 ${pattern} 的 chunk（构建是否过期？）`);
  renameSync(path.join(assets, name), path.join(assets, `${name}.hidden`));
  hidden.add(name);
  return name;
};
const restoreAll = () => {
  for (const name of hidden) {
    if (existsSync(path.join(assets, `${name}.hidden`))) renameSync(path.join(assets, `${name}.hidden`), path.join(assets, name));
  }
  hidden.clear();
};

try {
  for (const target of targets) {
    const label = target.label;
    const name = hideChunk(target.chunk);
    let outcome = "未判定";
    let handle = null;
    try {
      handle = await launch();
      const { app, page } = handle;
      const failedUrls = new Set();
      page.on("requestfailed", (r) => {
        reqfail += 1;
        failedUrls.add(r.url().split("/").pop());
      });
      const tap = async (keyCode) => {
        await app.evaluate(({ BrowserWindow }, args) => {
          const wc = BrowserWindow.getAllWindows()[0].webContents;
          wc.sendInputEvent({ type: "keyDown", keyCode: args, modifiers: ["control"] });
          wc.sendInputEvent({ type: "keyUp", keyCode: args, modifiers: ["control"] });
        }, keyCode);
        await page.waitForTimeout(120);
      };
      const factor = () => app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].webContents.getZoomFactor());
      // 缩放档以「视觉倍率」为准：窗口内容区 DIP 宽 ÷ 页面 CSS 宽。为什么不信两个现成的读数——
      //   · webContents.getZoomFactor() 实测会跨启动留在 2，而刚 reload 完的文档已经回到 100%；
      //   · 用「启动瞬间的 innerWidth」当 100% 基准同样错（第 2、3 次冷启动时 profile 里存着 200%，
      //     基准就量成 680，倍率永远算出 1.00，本普查第 2 轮正是这样误判的）。
      // 内容区宽不受页面缩放影响，除出来的才是这一屏真正的排版宽度——判的就是 200% 下够不够得着。
      const contentWidth = () => app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getContentBounds().width);
      const visualZoom = async () => (await contentWidth()) / (await page.evaluate(() => innerWidth));
      const ensureZoom2 = async () => {
        let escaped = false;
        for (let i = 0; i < 16 && (await visualZoom()) < 1.95; i += 1) {
          await tap("=");
          // 读数与画面脱钩的那一档要特殊处理：实测文档 reload 后 getZoomFactor 仍报 2、画面却回到
          // 100%，此时再按 "+" 会被 ZOOM_MAX 钳住、一步都不动。先往下退一档（setZoomFactor 真的
          // 落下去了），再继续按 "+"，倍率才回得来。
          if (!escaped && i >= 3 && (await visualZoom()) < 1.95) {
            escaped = true;
            await tap("-");
          }
        }
        const z = await visualZoom();
        if (z < 1.95 || z > 2.12) throw new Error(`缩放按不回 200%（视觉倍率 ${z.toFixed(2)}，getZoomFactor=${await factor()}），本条屏无法判定`);
        return z;
      };
      // 侧栏导航走键盘（聚焦 + Enter）：重载后的瞬时布局里指针命中会被 aside 自己截住
      // （实测 click 反复 "intercepts pointer events"），而键盘这条路径本来就是本 app 的可达性口径。
      const goto = async (navLabel) => {
        const nav = page.locator(".desktop-sidebar button", { hasText: navLabel }).first();
        await nav.scrollIntoViewIfNeeded({ timeout: 4000 }).catch(() => {});
        await nav.focus({ timeout: 8000 });
        await page.keyboard.press("Enter");
        await page.waitForTimeout(400);
      };
      const boundaryHere = () => page.evaluate((text) => new RegExp(text).test(document.body.innerText || ""), BOUNDARY_TEXT);

      const z = await ensureZoom2();
      if (!notes.some((n) => n.startsWith("注入全程 200%"))) {
        notes.push(`注入全程 200%（视觉倍率 ${z.toFixed(2)}，layout 视口 ${await page.evaluate(() => innerWidth)}×${await page.evaluate(() => innerHeight)} CSS px）`);
      }

      await goto(label);
      await page.waitForTimeout(2000);
      const bitten = failedUrls.has(name);
      const boundary = await boundaryHere().catch(() => false);
      if (!bitten || !boundary) {
        reds.push(`${label}：注入未咬到（chunk 请求失败=${bitten}，兜底卡=${boundary}）——判据失去意义`);
        outcome = "注入未咬到";
      } else {
        judged += 1;
        // 兜底卡自己的可达性：这张卡是「出错了唯一的出路」。判据是逐按钮的——200% 下每颗出路按钮
        // 要么整颗在视口内，要么所在容器**可由用户滚动**（overflow-y: auto/scroll）。为什么不能用
        // Playwright 的 scrollIntoViewIfNeeded 验：实测祖先链全是 overflow:hidden 而 sh>ch，那种盒
        // 程序化照样滚得动（CDP 一滚按钮就「进视口」了），鼠标滚轮与触摸却什么也滚不出来——用它判
        // 等于把裁掉的出路判成绿（这颗牙第一轮就是这么哑的）。WCAG 2.1.2 / 1.4.4。
        const unreachable = await page.evaluate(() => {
          const card = document.querySelector(".max-w-md");
          if (!card) return [];
          const hits = [];
          for (const b of card.querySelectorAll("button")) {
            const r = b.getBoundingClientRect();
            if (r.top >= -1 && r.bottom <= innerHeight + 1) continue;
            let el = b.parentElement;
            let scroller = null;
            while (el) {
              const oy = getComputedStyle(el).overflowY;
              if (oy === "auto" || oy === "scroll") {
                scroller = `${String(el.className).slice(0, 24)}（可滚高 ${el.scrollHeight}/${el.clientHeight}）`;
                break;
              }
              el = el.parentElement;
            }
            if (!scroller) {
              hits.push(`${(b.textContent || "").trim().slice(0, 14) || "(无名)"} y=${Math.round(r.top)}–${Math.round(r.bottom)} 越过视口底 ${innerHeight}，祖先里没有一只可滚容器（全 overflow:hidden，程序滚得动、鼠标滚轮滚不出来）`);
            }
          }
          return hits;
        });
        for (const hit of unreachable) reds.push(`${label}：兜底卡里的出路「${hit}」在 200% 下够不着`);
        // 点之前先还原 chunk：这一步测的是「故障源过去了以后，用户能不能靠主出路回到原屏」，
        // 文件还缺着谁都没辙，那不是可达性判据。
        restoreAll();
        const primary = page.locator(".max-w-md").first().locator("button").first();
        const primaryName = ((await primary.textContent().catch(() => "")) || "").trim();
        let activatedBy = "指针";
        try {
          await primary.click({ timeout: 5000 });
        } catch (error) {
          const line = String((error && error.message) || error).split("\n").find((l) => l.includes("intercepts")) || String((error && error.message) || error).split("\n")[0];
          reds.push(`${label}：主按钮「${primaryName.slice(0, 24) || "(无按钮)"}」指针点不到——${(line || "").trim().slice(0, 90)}`);
          activatedBy = "键盘";
          await primary.press("Enter").catch(() => {});
        }
        let back = false;
        let stillBoundary = true;
        const deadline = Date.now() + 15000;
        while (Date.now() < deadline) {
          await page.waitForTimeout(500);
          const state = await page
            .evaluate((arg) => ({
              body: !!document.querySelector(arg.body),
              boundary: new RegExp(arg.text).test(document.body.innerText || ""),
              elsewhere: !!document.querySelector(".project-home, .desktop-library-row")
            }), { body: target.body, text: BOUNDARY_TEXT })
            .catch(() => ({ body: false, boundary: true, elsewhere: false }));
          stillBoundary = state.boundary;
          if (state.body && !state.boundary) {
            back = true;
            break;
          }
          if (!state.boundary && !state.body && state.elsewhere) break;
        }
        if (!back) {
          reds.push(`${label}：主按钮「${primaryName.slice(0, 24) || "(无按钮)"}」没能把用户带回这一屏（${stillBoundary ? "仍停在兜底卡里" : "被带到了别的屏"}）——放大到 200% 时这一屏的内容与功能仍在丢失`);
          outcome = "未回到原屏";
        } else {
          const f = await ensureZoom2().catch(() => null);
          notes.push(`${label}：主按钮「${primaryName.slice(0, 24)}」（${activatedBy}激活）把用户带回这一屏（chunk=${name}，重载后视觉缩放=${f ?? "未知"}）`);
          outcome = "已回到原屏";
        }
      }
    } catch (error) {
      reds.push(`${label}：判定异常中止——${String((error && error.message) || error).split("\n")[0].slice(0, 120)}`);
      outcome = "判定异常";
    } finally {
      restoreAll();
      if (handle) await handle.app.close().catch(() => {});
      await settle(500);
    }
    console.log(`  · ${label}：${outcome}`);
  }

  console.log(`注入核对完成；200% 下判定 ${judged} 条屏，chunk 请求失败共 ${reqfail} 次。`);
  if (judged < LAZY_MIN) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`LAZY_RED：只判定到 ${judged} 条屏（要求 ≥${LAZY_MIN}），覆盖不足无法证明。`);
    exitCode = 1;
  } else if (reds.length) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`LAZY_RED：${reds.length} 条判据在懒加载失败的恢复通道上破了（注入未咬到 / 出路够不着 / 主按钮没能带回原屏）——WCAG 1.4.4 放大到 200% 时内容与功能丢失。`);
    exitCode = 1;
  } else {
    for (const note of notes) console.log(`  记档 ${note}`);
    const leftover = readdirSync(assets).filter((f) => f.endsWith(".hidden"));
    if (leftover.length) {
      console.log(`LAZY_RED：产物目录残留 ${leftover.length} 个 .hidden：${leftover.join(", ")}`);
      exitCode = 1;
    } else {
      console.log(`LAZY_OK：${judged} 条屏的 chunk 失败注入都真咬到，且兜底卡主按钮都能把用户带回原屏。`);
    }
  }
} catch (error) {
  console.log(`LAZY_RED：普查异常中止——${String((error && error.message) || error).split("\n")[0].slice(0, 140)}`);
  exitCode = 1;
} finally {
  restoreAll();
  rmSync(profileDir, { recursive: true, force: true });
}
process.exit(exitCode);
