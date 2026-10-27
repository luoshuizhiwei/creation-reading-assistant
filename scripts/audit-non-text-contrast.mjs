/**
 * BD-210 常驻普查：WCAG 1.4.11 Non-text Contrast（AA），运行时活体实测。
 *
 * 判据口径（W3C Understanding 1.4.11）：识别 UI 组件**状态与边界**所需的视觉信息，
 * 对相邻颜色 ≥3:1。「相邻」= 组件内外两侧都要能分辨；组件若有比边界更强的识别
 * 方式，边界线不再单独判——
 *  · 带可见文字标签的按钮/链接：文字就是强识别方式（文字对比归 audit-contrast），
 *    其边框/填色不判（labeled 豁免）。
 *  · 纯字段（input/select/textarea）：边界是唯一识别信息，边框、填色、乃至最近的
 *    复合控件壳边，任一条对内外两侧 ≥3 即过（shell 候选）。
 *  · 图标钮：图标色 ↔ 承托面 ≥3（Understanding 的下拉箭头例）。
 *  · range 滑杆：**状态识别由滑块（thumb）承担，细轨算装饰**——thumb 的描边/面色
 *    对轨道与外侧 ≥3 即过；轨道色只作候选。thumb/track 的 getComputedStyle 伪元素
 *    读数在 Electron 33 实测不可靠（无样式时回吐默认黑 rgb(0,0,0)，19.92 假绿）——
 *    因此滑杆取色走**作者规则定罪**：扫 CSSOM 里命中该元素的 ::-webkit-slider-thumb /
 *    ::-webkit-slider-runnable-track 规则（剔除 :hover/:focus/:active 态），
 *    var() 沿元素自定义属性解链后取色。作者没画 thumb ⇒ thumbBlind，不投票。
 * 豁免：disabled / inert 子树（Understanding 明列非活动控件不在条款要求内）；
 * UA 默认绘制的原生控件（checkbox/radio、未 appearance:none 的 select/range）。
 * 文本 input 即便 appearance:auto 也**不豁免**——它的边界渲染由作者样式负责。
 * 遮挡过滤与活体判定复用 BD-208/BD-209 判据（elementFromPoint 三内侧点）。
 * 主题：本网跑浅色档；修复用的 --border-field/--action-primary 是主题联动令牌，
 * 深色档值（#a7afb9/#7fa5d9 级）同过 ≥3 地板（对 --surface-1 深色底 #1b1f24 实测
 * ≥4.8，见 /tmp/bd210-probe4.log 修复后复跑账）。
 *
 * 判红证据链（本批修复的实测起点，/tmp/bd210-probe4.log）：
 *  · .paper-input/.cards-input 族边界 1.21–1.49（--border-strong/--paper-line 档）；
 *  · 全局搜索壳边 1.06–1.21（color-mix(--border-subtle) 档）；
 *  · TXT 底部进度滑杆 1.3（半透明轨 + 无作者 thumb）。
 * 牙（teeth）锚：产品侧改动见 styles.css --border-field、editorial-studio.css
 * 字段族/desktop-canvas 重映射/.uni-search-shell/.reader-progress-slider 与
 * ReaderBottomBar.tsx 的滑杆类名。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-nontext-audit-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
  const app = await electron.launch({ executablePath: electronPath, args: [mainJs], cwd: root, env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir } });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};
// 阅读器/资料范围要真书——首启建 schema，再用 round4 种子播 TXT/EPUB/MD，然后重开。
{ const { app } = await launch(); await app.close(); }
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], { cwd: root, encoding: "utf8", env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") }, windowsHide: true, timeout: 60000 });
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);
const { app, page } = await launch();

const goto = async (label) => { await page.locator(".desktop-sidebar button", { hasText: label }).first().click(); await page.waitForTimeout(400); };
const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await goto("项目");
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await page.locator(".project-home-item-main").first().click();
  await page.waitForSelector(".project-workbench", { timeout: 15000 });
};
const workbenchView = async (navLabel, selector) => {
  await inWorkbench();
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};
async function createProject() {
  await goto("项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await probe("弹层·新建项目向导");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("非文本对比普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

const collectScript = () => {
  const parse = (s) => {
    const m = /rgba?\(([^)]+)\)/.exec(s || "");
    if (m) {
      const p = m[1].split(/[,\s/]+/).filter(Boolean).map((x) => Number.parseFloat(x));
      return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
    }
    // Chromium 33 起 computed color 走现代语法：color(srgb 0.18 0.2 0.23 / 0.5)
    const ms = /color\(\s*srgb\s+([^)]+)\)/.exec(s || "");
    if (ms) {
      let t = ms[1].split("/");
      const a = t.length > 1 ? Number.parseFloat(t[1]) : 1;
      const p = t[0].trim().split(/[\s,]+/).map((x) => Number.parseFloat(x) * 255);
      return { r: p[0], g: p[1], b: p[2], a };
    }
    return null;
  };
  const hexTo = (g) => {
    const h = g.slice(1);
    const full = h.length === 3 ? h.split("").map((x) => x + x).join("") : h.slice(0, 6);
    return { r: parseInt(full.slice(0, 2), 16), g: parseInt(full.slice(2, 4), 16), b: parseInt(full.slice(4, 6), 16), a: 1 };
  };
  const COLOR_RE = /rgba?\([^)]+\)|color\(\s*srgb[^)]+\)|#[0-9a-fA-F]{3,8}/g;
  const colorTokens = (s) => {
    const found = [];
    for (const g of (s || "").match(COLOR_RE) || []) {
      if (/^#/.test(g)) { found.push(hexTo(g)); continue; }
      const c = parse(g);
      if (c) found.push(c);
    }
    return found;
  };
  const lum = (c) => {
    const f = (v) => { const x = v / 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  };
  const ratio = (a, b) => { const la = lum(a), lb = lum(b); return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05); };
  // 沿链找首个不透明底色；includeSelf=true 含自身。
  const solidBg = (el, includeSelf) => {
    let cur = includeSelf ? el : el.parentElement;
    while (cur) {
      const c = parse(getComputedStyle(cur).backgroundColor);
      if (c && c.a >= 1) return { c, node: cur.tagName.toLowerCase() + "." + (cur.getAttribute("class") || "").split(/\s+/)[0] };
      cur = cur.parentElement;
    }
    const html = parse(getComputedStyle(document.documentElement).backgroundColor);
    return { c: html || { r: 255, g: 255, b: 255, a: 1 }, node: "html" };
  };
  const resolveVars = (s, cs) => {
    let out2 = s || "";
    let prev = null;
    for (let i = 0; i < 3 && out2 !== prev; i += 1) {
      prev = out2;
      out2 = out2.replace(/var\((--[A-Za-z0-9-]+)(?:\s*,\s*[^()]*)?\)/g, (m, name) => cs.getPropertyValue(name).trim() || "0 0 0");
    }
    return out2;
  };
  // 作者规则定罪：命中 el 的伪元素规则（剔除交互态），合并声明并解 var 链。
  const authorPseudo = (el, pseudo) => {
    const merged = {};
    for (const sheet of document.styleSheets) {
      let rules;
      try { rules = sheet.cssRules; } catch { continue; }
      for (const rule of rules) {
        if (!rule.selectorText || !rule.style) continue;
        for (const part of rule.selectorText.split(",")) {
          if (!part.includes(pseudo) || /:hover|:focus|:active|:disabled/.test(part)) continue;
          const base = part.split("::")[0].trim();
          if (!base) continue;
          let ok = false;
          try { ok = el.matches(base); } catch { continue; }
          if (!ok) continue;
          for (const prop of ["background", "background-color", "background-image", "border", "border-color", "border-top-color"]) {
            const v = rule.style.getPropertyValue(prop);
            if (v) merged[prop] = v;
          }
        }
      }
    }
    const cs = getComputedStyle(el);
    const colors = [];
    for (const v of Object.values(merged)) colors.push(...colorTokens(resolveVars(v, cs)));
    return { merged, colors, has: Object.keys(merged).length > 0 };
  };
  const out = { rows: [], counts: { F: 0, R: 0, B2: 0, C: 0, noSurface: 0, occluded: 0, live: 0, thumbBlind: 0, translucent: 0, labeled: 0, ua: 0 } };
  const TARGETS = document.querySelectorAll('a[href],button,input,select,textarea,[role=button],[role=menuitem],[role=tab],[role=switch]');
  for (const el of TARGETS) {
    const tag = el.tagName.toLowerCase();
    if (tag === "iframe" || el.disabled || el.closest("[inert]")) continue;
    const cs = getComputedStyle(el);
    if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) === 0 || cs.pointerEvents === "none") continue;
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) continue;
    const inx = Math.min(4, r.width / 2), iny = Math.min(4, r.height / 2);
    let reachable = false;
    for (const [px, py] of [[r.x + r.width / 2, r.y + r.height / 2], [r.x + inx, r.y + iny], [r.right - inx, r.bottom - iny]]) {
      const hit = document.elementFromPoint(px, py);
      if (hit && (hit === el || el.contains(hit))) { reachable = true; break; }
    }
    if (!reachable) { out.counts.occluded += 1; continue; }
    out.counts.live += 1;
    const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean);
    const id = `${tag}.${cls.join(".").slice(0, 80) || "(无类名)"}[${el.getAttribute("aria-label") || el.textContent?.trim().slice(0, 12) || ""}]`;
    const outer = solidBg(el, false);
    const inner = solidBg(el, true);
    const sides = ["borderTopWidth", "borderRightWidth", "borderBottomWidth", "borderLeftWidth"]
      .map((k) => parseFloat(cs[k]) || 0)
      .some((w) => w >= 1 && cs.borderStyle !== "none" && cs.borderStyle !== "hidden");
    const ownBg = parse(cs.backgroundColor);
    const hasFill = ownBg && ownBg.a > 0;
    const texts = el.textContent?.trim() || "";
    const hasSvg = Boolean(el.querySelector("svg"));
    if (tag === "input" && (el.type === "checkbox" || el.type === "radio")) { out.counts.ua += 1; continue; }
    // UA 默认绘制例外（1.4.11 User Agent Control）：外观归浏览器的 select/range。
    if (tag === "select" && cs.appearance !== "none") { out.counts.ua += 1; continue; }
    if (tag === "input" && el.type === "range" && cs.appearance !== "none") { out.counts.ua += 1; continue; }
    const isField = tag === "select" || tag === "textarea" ||
      (tag === "input" && !["button", "checkbox", "radio", "submit", "reset", "image", "range", "file", "color"].includes(el.type));
    let bucket;
    if (isField) bucket = "F";
    else if (tag === "input" && el.type === "range") bucket = "R";
    else if (texts) { out.counts.labeled += 1; continue; }
    else if (hasSvg) bucket = "C";
    else if (sides || hasFill) bucket = "B2";
    else { out.counts.noSurface += 1; continue; }
    out.counts[bucket] += 1;
    const row = { id, bucket, w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.x), y: Math.round(r.y) };
    const best = [0];
    const pushPair = (c) => { if (c && c.a >= 1) { best.push(+ratio(c, inner.c).toFixed(2), +ratio(c, outer.c).toFixed(2)); } };
    const gradColors = colorTokens(cs.backgroundImage && cs.backgroundImage !== "none" ? cs.backgroundImage : "").slice(0, 2);
    // 复合控件壳：「输入裸奔、壳供线」的边界归壳判（BD-210）。爬升 ≤4 层两类候选：
    //  ① 各层祖先**真实供线**的框色（uni-search 壳边这类——真边界由供线者出）；
    //  ② 首个不透明**填色**祖先的面色（dock 色点的承托面这类——外侧身份由底面出）。
    // ⚠ 一类命中不停航、也不止收一层（首跑误判记账）：uni-search 输入上方第一层是
    // .uni-search-head（只有 border-bottom-color 有值、底色不透明），早停或「只收首个有框
    // 祖先」都会把真壳边 .uni-search-shell 的 --border-field 供线挡在视野外——实测红成
    // 1.17，而产品侧的壳边修复是生效的。故：框色按「祖先 × 四条边」全收（同色去重），
    // 底色仍取首个不透明者（承托面只有一层是真外侧）。
    const shellBorderColors = [];
    let shellFill = null;
    let shellFillNode = "";
    {
      const sideNames = ["Top", "Right", "Bottom", "Left"];
      let cur = el.parentElement;
      for (let d = 0; d < 4 && cur && cur !== document.body; d += 1, cur = cur.parentElement) {
        const pcs = getComputedStyle(cur);
        if (pcs.borderStyle !== "none" && pcs.borderStyle !== "hidden") {
          for (const side of sideNames) {
            if ((parseFloat(pcs[`border${side}Width`]) || 0) < 1) continue;
            const sbc = parse(pcs[`border${side}Color`]);
            if (sbc && sbc.a >= 1 && !shellBorderColors.some((c) => c.r === sbc.r && c.g === sbc.g && c.b === sbc.b)) {
              shellBorderColors.push(sbc);
            }
          }
        }
        const pBg = parse(pcs.backgroundColor);
        if (!shellFill && pBg && pBg.a > 0) {
          shellFill = pBg;
          shellFillNode = `${cur.tagName.toLowerCase()}.${(cur.getAttribute("class") || "").split(/\s+/)[0]}`;
        }
      }
    }
    if (bucket === "C") {
      const ic = parse(cs.color);
      row.icon = ic ? `rgb(${ic.r},${ic.g},${ic.b})` : cs.color;
      row.backing = `rgb(${inner.c.r},${inner.c.g},${inner.c.b})@${inner.node}`;
      let b = ic ? +ratio(ic, inner.c).toFixed(2) : 0;
      if (sides) { const bc = parse(cs.borderTopColor); if (bc) b = Math.max(b, +ratio(bc, inner.c).toFixed(2), +ratio(bc, outer.c).toFixed(2)); }
      if (hasFill) b = Math.max(b, +ratio({ ...ownBg, a: 1 }, outer.c).toFixed(2));
      row.rBack = b;
      row.pass = b >= 3;
      out.rows.push(row);
      continue;
    }
    if (sides) {
      const bc = parse(cs.borderTopColor);
      if (bc && bc.a >= 1) { row.border = `rgb(${bc.r},${bc.g},${bc.b})`; pushPair(bc); }
      else if (bc) row.border = "透明";
    }
    if (hasFill) {
      row.fill = `rgb(${ownBg.r},${ownBg.g},${ownBg.b}) a=${ownBg.a}`;
      if (ownBg.a < 1) { out.counts.translucent += 1; row.translucent = true; }
      pushPair({ ...ownBg, a: 1 });
    }
    if (gradColors.length) {
      row.gradient = gradColors.map((g) => `rgb(${g.r},${g.g},${g.b})`).join(" ");
      for (const g of gradColors) pushPair(g);
    }
    if (bucket === "R") {
      const th = authorPseudo(el, "::-webkit-slider-thumb");
      const tr = authorPseudo(el, "::-webkit-slider-runnable-track");
      const mozTh = authorPseudo(el, "::-moz-range-thumb");
      const thumbColors = [...th.colors, ...(th.has ? [] : mozTh.colors)];
      if (thumbColors.length) {
        row.thumb = thumbColors.map((c) => `rgb(${c.r},${c.g},${c.b})`).join(" ");
        for (const c of thumbColors) pushPair(c);
      } else {
        out.counts.thumbBlind += 1;
        row.thumb = "查无作者规则";
      }
      if (tr.colors.length) {
        row.track = tr.colors.map((c) => `rgb(${c.r},${c.g},${c.b})`).join(" ");
        for (const c of tr.colors) pushPair(c);
      }
    }
    if (shellBorderColors.length || shellFill) {
      row.shellNode = `框=${shellBorderColors.map((c) => `rgb(${c.r},${c.g},${c.b})`).join("/") || "无"} 底壳=${shellFillNode || "无"}`;
      for (const sbc of shellBorderColors) pushPair(sbc);
      if (shellFill) pushPair({ ...shellFill, a: 1 });
    }
    row.best = Math.max(...best.filter((v) => typeof v === "number" && !Number.isNaN(v)));
    row.inner = `rgb(${inner.c.r},${inner.c.g},${inner.c.b})@${inner.node}`;
    row.outer = `rgb(${outer.c.r},${outer.c.g},${outer.c.b})@${outer.node}`;
    row.pass = row.best >= 3;
    out.rows.push(row);
  }
  return out;
};

const allRows = new Map();
const totals = { F: 0, R: 0, B2: 0, C: 0, noSurface: 0, occluded: 0, live: 0, thumbBlind: 0, translucent: 0, labeled: 0, ua: 0 };
let scopeCount = 0;
async function probe(label) {
  scopeCount += 1;
  const r = await page.evaluate(collectScript).catch(() => null);
  if (!r) { console.log(`跳过「${label}」`); return; }
  for (const k of Object.keys(totals)) totals[k] += r.counts[k] || 0;
  for (const row of r.rows) {
    const key = row.id + "|" + row.bucket;
    if (!allRows.has(key)) allRows.set(key, { ...row, where: [label] });
    else if (!allRows.get(key).where.includes(label)) allRows.get(key).where.push(label);
  }
}

await createProject();
const scopes = [
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["项目·大纲", async () => workbenchView("大纲", ".project-workbench")],
  ["项目·概览", async () => workbenchView("概览", ".overview-page")],
  ["项目·全书预览", async () => workbenchView("全书预览", ".preview-page")],
  ["项目·写作统计", async () => workbenchView("写作统计", ".stats-page")],
  ["项目·版本历史", async () => workbenchView("版本历史", ".history-page")],
  ["书库", async () => { await goto("书库"); await page.waitForSelector(".desktop-library-row", { timeout: 10000 }).catch(() => {}); }],
  ["收件箱", async () => { await goto("收件箱"); await page.waitForSelector(".inbox-list", { timeout: 10000 }).catch(() => {}); }],
  ["资料阅读", async () => { await goto("资料阅读"); await page.waitForTimeout(500); }],
  ["设置", async () => { await goto("设置"); await page.waitForSelector(".desktop-page-stack", { timeout: 10000 }).catch(() => {}); }]
];
for (const [label, prep] of scopes) { await prep().catch(() => {}); await probe(label); }

const tryScope = async (label, prep) => {
  try { await prep(); await probe(label); } catch (e) { console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 120)}`); }
};
await tryScope("弹层·全局搜索", async () => {
  await workbenchView("写作", ".writing-desk");
  await page.keyboard.press("Control+Shift+f");
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);
await tryScope("弹层·写作速查", async () => {
  await page.keyboard.press("Control+Shift+k");
  await page.waitForTimeout(600);
});
await tryScope("弹层·写作速查·条目详情", async () => {
  // 速查面板选中条目后右侧详情才挂出 .writing-quick-detail .cards-input /
  // .writing-quick-field 字段——BD-210 补上这段覆盖，别让字段族只测了默认页。
  // ⚠ 二跑记账：「本项目」tab 看的是写作台当前打开项目的卡片，播卡播到 projects[0]
  // 时这边列表照样是空的（5s 超时）。普查不猜当前项目 id，改走「全局搜索」tab——
  // 它按 cardsList({ search }) 跨项目检索（WritingQuickReferencePanel.tsx:112-133），
  // 只要库里存在这张卡就能点进详情。播卡改为幂等：先查后建，普查多跑也不堆重复卡。
  await page.evaluate(async () => {
    const existing = await window.api.creation.cardsList({ kind: "cards.list", search: "普查卡片" });
    if (existing.length > 0) return;
    const projects = await window.api.creation.listProjects();
    const project = projects.find((p) => p.title === "非文本对比普查项目") ?? projects[0];
    await window.api.creation.runStructure({ type: "card.create", projectId: project.id, kind: "character", title: "普查卡片", aliases: [], fields: {}, tags: [] });
  });
  // 面板是 toggle 钮——上一范围（弹层·写作速查）已把它开着，再按一次会关掉。开了就不再按。
  if (!(await page.locator(".writing-quick-tabs").isVisible().catch(() => false))) {
    await page.keyboard.press("Control+Shift+k");
    await page.waitForTimeout(600);
  }
  await page.locator(".writing-quick-tabs button", { hasText: "全局搜索" }).first().click({ timeout: 5000 });
  await page.waitForTimeout(300);
  await page.locator(".writing-quick-search input").fill("普查卡片");
  // 检索有 240ms 防抖 + 异步 IPC（见面板 settledQuery 注释），给足落定时间。
  await page.waitForTimeout(1500);
  await page.locator(".writing-quick-list li > button").first().click({ timeout: 8000 });
  await page.waitForTimeout(400);
});
await page.keyboard.press("Control+Shift+k").catch(() => {});
await page.waitForTimeout(300);

await tryScope("阅读器·TXT", async () => {
  await goto("资料阅读");
  await page.waitForSelector('.desktop-library-row[data-format="txt"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="txt"]').first().click();
  await page.waitForSelector('button[aria-label="阅读设置"]', { timeout: 20000 });
  await page.waitForTimeout(900);
});
await tryScope("阅读器·TXT设置抽屉", async () => {
  await page.locator('button[aria-label="阅读设置"]').first().click();
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(350);
await tryScope("阅读器·TXT目录面板", async () => {
  const expand = page.locator('button[aria-label="展开目录"]');
  if (await expand.count()) await expand.first().click();
  await page.waitForTimeout(500);
});
await page.locator('button[aria-label="收起目录"]').first().click().catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·TXT搜索浮层", async () => {
  await page.keyboard.press("Control+f");
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·EPUB", async () => {
  await page.locator('button[aria-label="返回书库"]').first().click();
  await page.waitForSelector('.desktop-library-row[data-format="epub"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="epub"]').first().click();
  await page.waitForSelector(".paper-topbar", { timeout: 30000 });
  await page.waitForTimeout(1800);
});
await tryScope("弹层·EPUB设置抽屉", async () => {
  await page.locator(".paper-topbar button", { hasText: "设置" }).first().click();
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

console.log(`普查范围 ${scopeCount} 个；活体控件 ${totals.live} 个（判 ${totals.F + totals.R + totals.B2 + totals.C}：字段 ${totals.F}、滑杆 ${totals.R}、表面 ${totals.B2}、图标钮 ${totals.C}）；豁免：文字标签 ${totals.labeled}、UA 原生 ${totals.ua}；遮挡过滤 ${totals.occluded}；thumb 查无作者规则 ${totals.thumbBlind}；半透明待核 ${totals.translucent}。`);
const fails = [...allRows.values()].filter((r) => !r.pass);
if (fails.length > 0) {
  console.log(`NONTEXTCONTRAST_RED：${fails.length} 个非文本边界对相邻色不达 3:1：`);
  for (const f of fails) {
    const parts = [`边框=${f.border || "无"}`, `填色=${f.fill || "无"}`];
    if (f.gradient) parts.push(`渐变=${f.gradient}`);
    if (f.track) parts.push(`轨道=${f.track}`);
    if (f.thumb) parts.push(`滑块=${f.thumb}`);
    if (f.shellNode) parts.push(`壳=${f.shellNode}`);
    console.log(`  - [${f.where.join("、")}] ${f.bucket} ${f.id} best=${f.best ?? f.rBack} ${parts.join(" ")} 内=${f.inner || f.backing || ""} 外=${f.outer || ""}`);
  }
  await app.close();
  process.exit(1);
}
console.log(`NONTEXTCONTRAST_OK：${scopeCount} 个范围、${totals.F + totals.R + totals.B2 + totals.C} 个活体非文本边界全部 ≥3:1。`);
await app.close();
