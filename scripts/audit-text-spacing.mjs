/**
 * 文本间距普查（WCAG 1.4.12 Text Spacing，AA）。批次 BD-224 立。
 *
 * 1.4.12 的测试方法不是「读代码看有没有写死行高」，而是把用户能设的那四档间距
 * 真的装到文档上，再看内容还完不完整：行距到字号的 1.5 倍、段后距到字号的 2 倍、
 * 字距 0.12em、词距 0.16em，其余（宽高）一律不改。全站 230 处固定 px 字号与
 * 一圈 leading-[var(--leading-*)] 令牌档都是在这种用户设置下才现原形的。
 *
 * 注入用 `html[data-bd224-spacing] *{…!important}`，靠一个属性开关在同一份文档里
 * 来回切「基线 / 加距」两态：省掉每屏重跑一遍导航，也让每条判定都能报出
 * 「间距新增」还是「基线已如此」——前者是 1.4.12 的本体，后者顺带记档给别的 SC。
 * 开关只动 line-height / letter-spacing / word-spacing / p 的 margin-bottom，
 * 不碰 width/height/overflow，正是这条 SC 规定的「不改动其它属性」。
 *
 * 三条判据（都在加了距的那一态判）：
 *   ① 文字被裁走且摸不到：叶子文本行盒越过祖先链上某个 overflow hidden/clip 的框
 *     （auto/scroll 的那一轴算「摸得到」，不判红——BD-223 已记过 scrollIntoViewIfNeeded
 *     会把 hidden 盒子滚出来，运行时够不着 ≠ 程序够不着，这里反过来用同一套口径）；
 *   ② 文字互相压字：两个互不包含的叶子文本行盒相交 >2px（父块的多行行盒靠「叶子优先」
 *     剔掉，否则一段带 inline 子节点的话会自己压自己；行盒先按祖先链裁成「真被画出来的
 *     那块」再比，滚动容器里滚出可视区的正文不参与压字——Range 行盒不受 overflow 裁剪，
 *     不这么处理就会把页脚和读屏节点误报成压字）；
 *   ③ 读写文本要二维滚动：竖直滚动的阅读流里，含 p/li 的横滚容器 scrollWidth 越过
 *     clientWidth——正文既上下滚又左右滚。这一条也先跟基线比：基线就得横滚（比如窄档
 *     侧栏里比容器宽 22px 的场景任务卡，aside.outline-page-side 实测 262/240）不是
 *     「设了间距才这样」，那是 1.4.10 的局部横滚容器、BD-222 已定为记档，本批只判
 *     加距新冒出来的那一截。
 *
 * 注入自证：加距后必须真读出「行距 = 1.5×字号、字距 = 0.12×字号」这两条不变量，且与
 * 基线不同，读不出就是「注入未咬到」，判红而不是判绿（普查若从不变红，它证不了任何事）。
 *
 * 两档视口，不是一档。宽档 1360 是常规使用档；窄档 540 是本 app 可达的最窄 CSS px
 * （窗口下限 1080 ÷ 缩放上限 200%，BD-222 立的口径）。加窄档不是顺手：本批把写作台
 * 顶栏从钉死 46px 改成「底高」后，540 档的标题组被操作按钮挤到 20px 宽、顶栏长到
 * 939px，导航条整个跑到 860px 的隐藏页框之外——修 1.4.12 的动作把 1.4.10 的地雷
 * 顶了出来，而这类「加距后长太高、下面的东西再也摸不到」只有窄档才现形（实拍
 * /tmp/hero-ab-probe.log：nowrap 变体在 540 两态与 1080 加距态都是 nav inFrame=N）。
 * 窄档只走与顶栏、正文最相关的 9 屏（书库 / 设置 / 工作台七屏），阅读器与卡片列
 * 在 540 是收起态、判它属于 1.4.10 的另一节，不在本批重复开火。
 *
 * 覆盖面也进判据：判定视图数不足（< SPACING_MIN=20，宽档 16 + 窄档 9 的下限）即视为
 * 覆盖塌陷——导航断了、种子没播上都会静默少测，不许用「零 RED」蒙过。
 * 窄档另有单独下限 NARROW_MIN=8：整条链断在宽档时窄档会一起掉，两条下限分开报，才看得出
 * 是「顶栏哨位空了」还是「常规档导航断了」。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-text-spacing.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const SPACING_MIN = 20;
/** 窄档单独设下限：窄档只走 9 屏，塌到 8 屏以下就说明顶栏哨位没人站。 */
const NARROW_MIN = 8;

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-spacing-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

// 四档用户间距；只碰这四个性质，宽高与 overflow 一律不动。
const SPACING_CSS = `
html[data-bd224-spacing] * {
  line-height: 1.5 !important;
  letter-spacing: 0.12em !important;
  word-spacing: 0.16em !important;
}
html[data-bd224-spacing] p { margin-bottom: 2em !important; }
`;

const launch = async () => {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

// 首启建 schema，再播书库种子（TXT 七章、EPUB、MD）。
{
  const first = await launch();
  await first.app.close();
}
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

let exitCode = 0;
let handle = null;

try {
  const { app, page } = await launch();
  handle = { app, page };

  const reds = [];
  const notes = [];
  const judged = [];
  const skipped = [];

  /** 换档：窗口高度固定 900，只改宽。1360 = 常规档，540 = 窗口下限 1080 ÷ 缩放上限 200%。 */
  const setTier = async (width) => {
    await app.evaluate(({ BrowserWindow }, nextWidth) => {
      BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: nextWidth, height: 900 });
    }, width);
    await page.waitForTimeout(600);
  };

  /** 挂上间距样式表（同一文档只挂一次），并读回开关两态的 computed 行距自证注入咬得住。 */
  const armSpacing = async (label) => {
    await page.evaluate((css) => {
      if (document.querySelector("style[data-bd224-spacing]")) return;
      const tag = document.createElement("style");
      tag.setAttribute("data-bd224-spacing", "");
      tag.textContent = css;
      document.head.appendChild(tag);
    }, SPACING_CSS);
    const proof = await page.evaluate(() => {
      const nodes = Array.from(document.querySelectorAll("body p, body li, body span, body div"));
      const picks = nodes
        .filter((n) => Array.from(n.childNodes).some((c) => c.nodeType === 3 && c.textContent.trim().length > 2))
        .slice(0, 40);
      if (picks.length === 0) return { baseline: "无文本元素", spaced: "无文本元素", bit: false };
      document.documentElement.removeAttribute("data-bd224-spacing");
      const before = picks.map((el) => {
        const st = getComputedStyle(el);
        return { lh: st.lineHeight, ls: st.letterSpacing, fs: st.fontSize };
      });
      document.documentElement.setAttribute("data-bd224-spacing", "");
      let moved = -1;
      for (let i = 0; i < picks.length; i += 1) {
        const st = getComputedStyle(picks[i]);
        // 期望值：行距 = 1.5×字号，字距 = 0.12×字号；两者都落地且与基线不同才算咬到。
        const fs = parseFloat(st.fontSize);
        const lh = parseFloat(st.lineHeight);
        const ls = parseFloat(st.letterSpacing);
        if (Math.abs(lh - fs * 1.5) < 0.6 && Math.abs(ls - fs * 0.12) < 0.06
          && (lh !== parseFloat(before[i].lh) || ls !== parseFloat(before[i].ls))) { moved = i; break; }
      }
      if (moved < 0) return { baseline: before[0].lh, spaced: "未达 1.5×字号/0.12em", bit: false };
      return { baseline: `${before[moved].lh} / ${before[moved].ls}`, spaced: `${getComputedStyle(picks[moved]).lineHeight} / ${getComputedStyle(picks[moved]).letterSpacing}`, bit: true };
    });
    if (!proof.bit) reds.push(`${label}：注入未咬到——加距后读不到 1.5×字号 / 0.12em 的行距字距（基线 ${proof.baseline}，加距 ${proof.spaced}），间距样式没落地`);
    return proof;
  };

  /** 一态扫描：只读几何，不改布局开关。 */
  const scan = async () => page.evaluate(() => {
    const nameOf = (el) => {
      const cls = typeof el.className === "string" ? el.className.split(" ").filter(Boolean).slice(0, 2).join(".") : "";
      return `${el.tagName.toLowerCase()}${cls ? `.${cls}` : ""}`;
    };
    const ownText = (el) => Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    const textEls = [];
    for (const el of Array.from(document.querySelectorAll("body *"))) {
      if (el instanceof SVGElement) continue;
      if (!ownText(el)) continue;
      const st = getComputedStyle(el);
      if (st.display === "none" || st.visibility === "hidden" || Number(st.opacity) < 0.05) continue;
      textEls.push(el);
    }
    const inSet = new Set(textEls);
    const hasTextChild = new WeakSet();
    for (const el of textEls) {
      for (let p = el.parentElement; p; p = p.parentElement) if (inSet.has(p)) hasTextChild.add(p);
    }
    const rows = [];
    // 逐轴找「第一个挡住它的祖先」：visible 的跳过；auto/scroll 的那一轴用户滚得到，
    // 不算丢失；只有 hidden/clip 才是真裁走。更靠内的滚动容器会接管，外面的
    // hidden 壳不能再定罪——Range 行盒不受祖先 overflow 裁剪，不这么处理就会把
    // 「内层滚出去的正文」误报成「被外层 hidden 裁走」。
    const firstClipper = (el, axis) => {
      for (let p = el.parentElement; p; p = p.parentElement) {
        const pr = p.getBoundingClientRect();
        if (pr.width <= 2 || pr.height <= 2) return { kind: "sr" };
        const v = getComputedStyle(p)[axis];
        if (v === "hidden" || v === "clip") return { kind: "cut", host: p };
        if (v === "auto" || v === "scroll") return { kind: "scroll" };
      }
      return { kind: "none" };
    };
    // 真正被画出来的那块：Range 行盒不受祖先 overflow 裁剪，压字判定必须先按祖先链
    // 裁一遍（hidden/clip/auto/scroll 都会挡住绘制），否则「滚出可视区的正文」会
    // 和固定在视口里的页脚/读屏节点报成互相压字。
    const painted = (el, r) => {
      let left = r.left, top = r.top, right = r.right, bottom = r.bottom;
      for (let p = el.parentElement; p; p = p.parentElement) {
        const c = getComputedStyle(p);
        if (c.overflowX === "visible" && c.overflowY === "visible") continue;
        const pr = p.getBoundingClientRect();
        if (c.overflowX !== "visible") { left = Math.max(left, pr.left); right = Math.min(right, pr.right); }
        if (c.overflowY !== "visible") { top = Math.max(top, pr.top); bottom = Math.min(bottom, pr.bottom); }
        if (right - left < 3 || bottom - top < 3) return null;
      }
      return { x: left, y: top, w: right - left, h: bottom - top };
    };
    for (const el of textEls) {
      if (hasTextChild.has(el)) continue;
      const own = el.getBoundingClientRect();
      // 读屏专用节点（1×1 那族）本身就没打算画出来，不参与裁字/压字判定。
      if (own.width <= 2 || own.height <= 2) continue;
      const rg = document.createRange();
      rg.selectNodeContents(el);
      const rects = Array.from(rg.getClientRects());
      if (typeof rg.detach === "function") rg.detach();
      const y = firstClipper(el, "overflowY");
      if (y.kind === "sr") continue;
      const x = firstClipper(el, "overflowX");
      for (const r of rects) {
        if (r.width < 3 || r.height < 3) continue;
        rows.push({
          el,
          name: nameOf(el),
          x: r.x,
          y: r.y,
          w: r.width,
          h: r.height,
          hostY: y.host ?? null,
          hostX: x.kind === "cut" ? x.host : null,
          ink: painted(el, r)
        });
      }
    }
    const cut = [];
    const cutSeen = new Set();
    for (const it of rows) {
      const report = (host, axis, over) => {
        const key = `${it.name}|${nameOf(host)}|${axis}`;
        if (cutSeen.has(key)) return;
        cutSeen.add(key);
        cut.push({ key, text: `「${it.name}」的文字越出 ${nameOf(host)}（${axis}:${getComputedStyle(host)[axis]}）${over}px` });
      };
      if (it.hostY) {
        const hr = it.hostY.getBoundingClientRect();
        const over = Math.round(Math.max(0, it.y + it.h - hr.bottom, hr.top - it.y));
        if (over > 2) report(it.hostY, "overflowY", over);
      }
      if (it.hostX) {
        const hr = it.hostX.getBoundingClientRect();
        const over = Math.round(Math.max(0, it.x + it.w - hr.right, hr.left - it.x));
        if (over > 2) report(it.hostX, "overflowX", over);
      }
    }
    const over = [];
    const overSeen = new Set();
    const inkRows = rows.filter((it) => it.ink);
    inkRows.sort((a, b) => a.ink.y - b.ink.y);
    for (let i = 0; i < inkRows.length; i += 1) {
      for (let j = i + 1; j < inkRows.length && inkRows[j].ink.y < inkRows[i].ink.y + inkRows[i].ink.h; j += 1) {
        const a = inkRows[i].ink, b = inkRows[j].ink;
        const ea = inkRows[i], eb = inkRows[j];
        if (ea.el === eb.el || ea.el.contains(eb.el) || eb.el.contains(ea.el)) continue;
        const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
        const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
        if (ox <= 2 || oy <= 2) continue;
        const key = [ea.name, eb.name].sort().join("|");
        if (overSeen.has(key)) continue;
        overSeen.add(key);
        const spot = (it) => `${(it.el.textContent || "").trim().replace(/\s+/g, " ").slice(0, 12)}@${Math.round(it.ink.x)},${Math.round(it.ink.y)} ${Math.round(it.ink.w)}×${Math.round(it.ink.h)}`;
        over.push({ key, text: `「${ea.name}」×「${eb.name}」压字 ${Math.round(ox)}x${Math.round(oy)}px（${spot(ea)} ／ ${spot(eb)}）` });
      }
    }
    const twod = [];
    const twodSeen = new Set();
    for (const el of Array.from(document.querySelectorAll("body *"))) {
      if (el instanceof SVGElement) continue;
      const st = getComputedStyle(el);
      if (st.overflowX !== "auto" && st.overflowX !== "scroll") continue;
      if (el.scrollWidth <= el.clientWidth + 2) continue;
      // 只判阅读流：容器里得有真段落/列表项，且自身不是 nowrap 的控件行。
      if (!el.querySelector("p, li")) continue;
      if (st.whiteSpace === "nowrap") continue;
      const key = nameOf(el);
      if (twodSeen.has(key)) continue;
      twodSeen.add(key);
      twod.push({ key, text: `${key} 里的正文要横向滚动 ${el.scrollWidth - el.clientWidth}px 才读得完（纵向页面已在滚）` });
    }
    return { cut, over, twod, boxes: rows.length };
  });

  /** 同一屏跑基线 / 加距两态，判红只看加距态，并标注每条问题是不是加距才冒出来的。 */
  const measure = async (label) => {
    const proof = await armSpacing(label);
    const spaced = await scan();
    await page.evaluate(() => document.documentElement.removeAttribute("data-bd224-spacing"));
    const base = await scan();
    await page.evaluate(() => document.documentElement.setAttribute("data-bd224-spacing", ""));
    const baseCut = new Set(base.cut.map((c) => c.key));
    const baseOver = new Set(base.over.map((c) => c.key));
    const baseTwod = new Set(base.twod.map((c) => c.key));

    const freshCut = spaced.cut.filter((c) => !baseCut.has(c.key));
    const freshOver = spaced.over.filter((c) => !baseOver.has(c.key));
    const freshTwod = spaced.twod.filter((c) => !baseTwod.has(c.key));
    if (freshCut.length) {
      reds.push(`${label}：${freshCut.length} 处文字在加距后被裁走且摸不到 —— ${freshCut.slice(0, 4).map((c) => c.text).join("；")}`);
    }
    if (freshOver.length) {
      reds.push(`${label}：${freshOver.length} 处文字在加距后互相压字 —— ${freshOver.slice(0, 4).map((c) => c.text).join("；")}`);
    }
    if (freshTwod.length) {
      reds.push(`${label}：${freshTwod.length} 处正文加距后需要二维滚动 —— ${freshTwod.slice(0, 3).map((c) => c.text).join("；")}`);
    }
    const alsoBase = spaced.cut.filter((c) => baseCut.has(c.key))
      .concat(spaced.over.filter((c) => baseOver.has(c.key)))
      .concat(spaced.twod.filter((c) => baseTwod.has(c.key)));
    if (alsoBase.length) {
      notes.push(`${label}：${alsoBase.length} 处裁字/压字/二维滚动在基线就存在（归 1.4.10/其它 SC，本批不重复判红）—— ${alsoBase.slice(0, 3).map((c) => c.text).join("；")}`);
    }
    const clean = !freshCut.length && !freshOver.length && !freshTwod.length;
    judged.push(label);
    if (clean) console.log(`PASS ${label} 加距后无裁字/压字/二维滚动（行盒 ${spaced.boxes} 个，基线行盒 ${base.boxes} 个，注入 lh ${proof.baseline}→${proof.spaced}）`);
    return clean;
  };

  const side = async (name, tier) => {
    const tag = `${name} @${tier}`;
    try {
      await page.locator(".desktop-sidebar button", { hasText: name }).first().click();
      await page.waitForTimeout(900);
      await measure(tag);
    } catch (error) {
      skipped.push(`${tag}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
    }
  };

  const workbenchView = async (name, tier) => {
    const tag = `工作台·${name} @${tier}`;
    try {
      const btn = page.locator(".project-nav button", { hasText: name }).first();
      if (!(await btn.isVisible().catch(() => false))) {
        skipped.push(`${tag}：项目导航里没有这一项`);
        return;
      }
      await btn.click();
      await page.waitForTimeout(1000);
      await measure(tag);
    } catch (error) {
      skipped.push(`${tag}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
    }
  };

  /**
   * 项目页 → 新建向导（顺手判定这一屏，弹层是表单最满的一屏）→ 建出「间距验收项目」
   * → 工作台。项目已存在时退回「打开项目」路径，保证同一份脚本可重跑。
   */
  const enterWorkbench = async (tier, createIfNeeded) => {
    try {
      await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
      await page.waitForTimeout(900);
      const create = page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first();
      if (createIfNeeded && (await create.isVisible().catch(() => false))) {
        await create.click();
        const wizard = page.locator("[role=dialog]").first();
        await wizard.waitFor({ state: "visible", timeout: 15000 });
        await wizard.getByRole("button", { name: /下一步/ }).click();
        await page.waitForTimeout(600);
        await measure(`弹层·新建向导 @${tier}`);
        await page.locator("input[placeholder='例如：测试项目']").first().fill("间距验收项目");
        await wizard.getByRole("button", { name: /下一步/ }).click();
        await page.locator("input[placeholder='例如：500000']").first().fill("50000");
        await wizard.getByRole("button", { name: /创建项目/ }).click();
        await page.waitForTimeout(2400);
      } else {
        const open = page.getByRole("button", { name: "打开项目：间距验收项目" }).first();
        if (await open.isVisible().catch(() => false)) {
          await open.click();
          await page.waitForTimeout(1800);
        } else {
          const back = page.locator(".project-nav-back").first();
          if (await back.isVisible().catch(() => false)) {
            await back.click();
            await page.waitForTimeout(900);
          }
          await page.locator(".project-home-list button").first().click();
          await page.waitForTimeout(1600);
        }
      }
      await measure(`工作台·概览 @${tier}`);
    } catch (error) {
      skipped.push(`工作台·概览 @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
    }
  };

  /** 阅读器正文 + 侧栏三页（卡片最密的一列）。只在宽档跑：窄档侧栏是收起态，归 1.4.10 另判。 */
  const readerPass = async (tier) => {
    try {
      await page.locator(".desktop-library-row").first().click();
      await page.waitForTimeout(2600);
      await measure(`阅读器 @${tier}`);
      const tabNames = [/^目录/, /^高亮/, /^书签/];
      for (const re of tabNames) {
        let tab = page.getByRole("button", { name: re }).first();
        if (!(await tab.isVisible().catch(() => false))) {
          const expand = page.getByRole("button", { name: "展开目录" }).first();
          if (!(await expand.isVisible().catch(() => false))) break;
          await expand.click();
          await page.waitForTimeout(700);
        }
        tab = page.getByRole("button", { name: re }).first();
        if (!(await tab.isVisible().catch(() => false))) continue;
        await tab.click();
        await page.waitForTimeout(700);
        await measure(`阅读器·侧栏${String(re).replace(/^\/\^|\/$/g, "")} @${tier}`);
      }
      const back = page.getByRole("button", { name: "返回书库" });
      if (await back.isVisible().catch(() => false)) {
        await back.click();
        await page.waitForTimeout(800);
      }
    } catch (error) {
      skipped.push(`阅读器 @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
    }
  };

  /**
   * 一档走完。宽档 16 屏（书库→阅读器三页→卡片库→收件箱→设置→新建向导→工作台七屏）；
   * 窄档 9 屏（书库→设置→工作台七屏）——窄档是顶栏那条横排的哨位，本批的窄洞
   * （标题被挤成竖排、导航条跑出隐藏页框）只有在这一档才现形。
   */
  const tierPass = async (tier, createIfNeeded) => {
    await setTier(tier);
    await side("书库", tier);
    if (tier >= 1360) {
      await readerPass(tier);
      await side("卡片库", tier);
      await side("收件箱", tier);
    }
    await side("设置", tier);
    await enterWorkbench(tier, createIfNeeded);
    for (const name of ["写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) await workbenchView(name, tier);
  };

  await tierPass(1360, true);
  await tierPass(540, false);

  await page.evaluate(() => document.documentElement.removeAttribute("data-bd224-spacing"));
  await handle.app.close();
  handle = null;

  const tiers = { 1360: 0, 540: 0 };
  for (const name of judged) tiers[Number(name.split(" @").pop())] += 1;
  console.log(`加距扫描完成；判定 ${judged.length} 条屏（宽档 @1360 ${tiers[1360]} 屏、窄档 @540 ${tiers[540]} 屏——540 = 窗口下限 1080 ÷ 缩放上限 200%）。`);
  if (tiers[540] < NARROW_MIN) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`SPACING_RED：窄档只判定到 ${tiers[540]} 条屏（要求 ≥${NARROW_MIN}），顶栏那条横排没人站岗，覆盖不足无法证明。`);
    exitCode = 1;
  } else if (judged.length < SPACING_MIN) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`SPACING_RED：只判定到 ${judged.length} 条屏（要求 ≥${SPACING_MIN}），覆盖不足无法证明。`);
    exitCode = 1;
  } else if (reds.length) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`SPACING_RED：${reds.length} 条判据在用户文本间距设置下破了（注入未咬到 / 文字被裁走 / 文字互相压字 / 正文要二维滚动）——WCAG 1.4.12 文本间距不达标。`);
    exitCode = 1;
  } else {
    for (const note of notes) console.log(`  记档 ${note}`);
    console.log(`SPACING_OK：${judged.length} 条屏在 1.5 行距 / 0.12em 字距 / 0.16em 词距 / 2em 段距下没有裁字、压字，正文不需二维滚动。`);
  }
  if (skipped.length) console.log(`跳过 ${skipped.length} 条：${skipped.join("；")}`);
} catch (error) {
  console.log(`SPACING_RED：普查异常中止——${String((error && error.message) || error).split("\n")[0].slice(0, 160)}`);
  exitCode = 1;
} finally {
  if (handle) await handle.app.close().catch(() => {});
  rmSync(profileDir, { recursive: true, force: true });
}
process.exit(exitCode);
