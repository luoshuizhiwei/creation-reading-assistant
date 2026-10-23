/**
 * 两主题文字对比度普查（BD-104 立，BD-105 扩到晨校）。
 *
 * 为什么要在运行时量，而不是继续加令牌断言：BD-103 的导出对话框就是一课——
 * 令牌级对比度全绿，屏幕上却是 1.2:1，因为那两行文字用的是写死的晨校暖褐。
 * 静态守卫只能守住「看得见的写法」，任何绕过令牌的颜色、任何半透明叠色、
 * 任何继承来的颜色，都只有把元素真画出来、把底色逐级合成之后才量得准。
 *
 * 口径（与守卫的文字×表面矩阵同源）：
 *  · 正文 / 次级文字（--text-primary / --text-secondary）底线 4.5:1；
 *  · 三级文字（--text-tertiary，角标/计数/时间戳）底线 3.0:1；
 *  · 其余颜色（自定义/accent/半透明叠出来的）一律按 4.5:1 判——没有令牌身份就没有豁免。
 *  · 跳过 disabled、有效透明度 <0.35（BD-114 起：沿祖先链累乘 CSS opacity，父级 dim 也算）、
 *    尺寸 <2px、视口外的元素：它们不承载可读信息。
 *
 * 范围（BD-107 扩，BD-108 补正文纸面，BD-109 穿进 EPUB iframe，BD-110 补 markdown 正文）：12 个主视图 + 7 个弹层
 *   + 5 个设置分区 + 7 种 TXT 正文纸面 + 7 种 MD 正文纸面 + 1 个 EPUB 宿主壳 + 7 种 EPUB 正文纸面 + 1 个 EPUB 统一夜间正文，共 47 个范围。
 * BD-112 把第 6 个弹层（全局搜索 Ctrl+K）补进来：命中词 <mark class="uni-hit"> 是又一处
 * 「<mark> 上色」面，此前从没量过；连同弹层 chrome 一起纳入晨校/夜校。
 * BD-113 把第 7 个弹层（写作速查 Ctrl+Shift+K，右侧 .writing-quick-reference）补进来：它的面板底是
 * color-mix(--bg-surface 97%, --accent-hover) 这套别处没有的轻染色，里面全族文字走 --text-main /
 * --text-muted / --text-meta 三档 token——「token 在别的底上验过、在这个新染色底上没验过」的典型面。
 * 列表行那 3px kind 竖脊是自闭合装饰 <span>（无文字），sweep 按无文字跳过，不参与文字对比。
 * BD-108 补纸面是因为守卫第 7 步补钉对 .reader-shell-* 有一整族的豁免，理由写着
 * 「壳的 background 同为字面量、成对写死」——这个前提此前只是断言，没人量过。
 * BD-109 补 EPUB 正文是因为早先那个范围只量宿主壳、穿不进 iframe，于是夜纸 publisher
 * 下正文退回 UA 黑压在 #15100e 上 1.05:1 的缺陷没人量到——iframe 同源，现在穿进去量。
 * BD-110 补 markdown 正文是因为那圈纸面只开纯 TXT 书，MD 书走 dangerouslySetInnerHTML
 * 的 h1/blockquote/列表/表格/<a> 完全没被量过——同 BD-109 的盲区教训，未量的动态内容面最危险。
 * BD-111 让那圈 TXT 纸面再多量正文高亮 <mark class="txt-hl">：seed 播了五种颜色的真高亮，
 * 于是「高亮墨色跟着纸走、tint 不许把最坏一档压到 4.5 以下」成了每圈都过的常驻断言。
 * 每个都在晨校与夜校各量一遍——两套底色是两套事实，夜校绿不代表晨校绿。
 * BD-105 就是靠这条抓到收件箱筛选徽标 4.09:1：那一处在夜校是 4.85:1，只看夜校永远看不见。
 * 每个范围都要求「采样到足够多的文字元素」——采样数塌了说明选择器又漂了，
 * 这时报绿比报红更危险，所以采样下限本身就是一条断言。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   (pgrep Xvfb >/dev/null || (setsid Xvfb :99 -screen 0 2400x1600x24 &)); \
 *   DISPLAY=:99 node scripts/audit-contrast.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-darkcontrast-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const tokensCss = readFileSync(path.join(root, "src", "styles", "tokens.css"), "utf8");
const darkStart = tokensCss.indexOf(':root[data-app-theme="dark"]');
const lightBlock = tokensCss.slice(0, darkStart);
const darkBlock = tokensCss.slice(darkStart);
const tokenHex = (block, name) => {
  const m = new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`).exec(block);
  return m ? m[1] : null;
};
const levelsOf = (block) => ({
  primary: tokenHex(block, "--text-primary"),
  secondary: tokenHex(block, "--text-secondary"),
  tertiary: tokenHex(block, "--text-tertiary")
});
const THEMES = { dark: levelsOf(darkBlock), light: levelsOf(lightBlock) };

const failures = [];
let scopeTotal = 0;
const check = (name, ok, detail) => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? `  — ${detail}` : ""}`);
  if (!ok) failures.push(name);
};

async function launch() {
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
}

async function goto(page, label) {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
}

async function createProject(page) {
  await goto(page, "项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("夜校普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

/** 页面内测量：把文字元素的颜色与逐级合成后的实底都算出来。 */
const sweepScript = ({ rootSelector, levels, canvasSeed }) => {
  const parse = (c) => {
    const m = String(c).match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(",").map((x) => Number(x.trim()));
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  const lin = (v) => { const x = v / 255; return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4; };
  const lum = (c) => 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1
  });
  const colorsIn = (str) => {
    const out = [];
    for (const m of String(str).matchAll(/rgba?\(([^)]+)\)|#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g)) {
      if (m[0].startsWith("#")) {
        const hex = m[2].length === 3 ? m[2].split("").map((x) => x + x).join("") : m[2];
        out.push({ r: parseInt(hex.slice(0, 2), 16), g: parseInt(hex.slice(2, 4), 16), b: parseInt(hex.slice(4, 6), 16), a: 1 });
      } else {
        const q = m[1].split(",").map((x) => Number(x.trim()));
        out.push({ r: q[0], g: q[1], b: q[2], a: q.length > 3 ? q[3] : 1 });
      }
    }
    return out;
  };
  /**
   * 把 `background-image` 串按**绘制顺序**切成一层一层。CSS 的规矩：先写的层在上。
   * 切分要避开括号里的逗号（`radial-gradient(…, rgba(0,0,0,0) 38%), linear-gradient(…)`
   * 中间那个逗号才是层界），所以按括号深度扫描，只在 depth===0 时切。
   * 每一层内部再取色标——七种纸面都是「顶层 radial 高光 + 下层 linear 底色」两笔，
   * 顶层 radial 是半透明的（0.04~0.7），它必须压在下面那层 linear 上看，
   * 不能独立压在画布上；早先 bgCandidates 把两层色标拍平成一个数组、
   * 各自独立合成，暖纸/羊皮纸那档 radial 半透明白就被当成"直接盖在深色画布上"，
   * 于是白纸正文被量出 1.05:1 的假红（真实屏幕 ~14:1）。
   */
  const imageLayers = (str) => {
    const s = String(str);
    const parts = [];
    let depth = 0;
    let start = 0;
    for (let i = 0; i < s.length; i += 1) {
      const ch = s[i];
      if (ch === "(") depth += 1;
      else if (ch === ")") depth -= 1;
      else if (ch === "," && depth === 0) {
        parts.push(s.slice(start, i));
        start = i + 1;
      }
    }
    parts.push(s.slice(start));
    // 每层带上形状标签：linear-gradient 铺满整个盒子，其色标合成后就代表实际屏幕
    // 上文字可能压到的所有档；radial/ellipse 常常 `circle at 50% 0%`+`transparent 38%`
    // ——圆外那块**根本不画**，下层原色会露出，所以下层的候选必须保留。
    return parts.map((p) => ({
      partial: /radial-gradient|conic-gradient|repeating-radial-gradient|ellipse/.test(p),
      stops: colorsIn(p).filter((c) => c.a > 0)
    }));
  };
  /**
   * 返回「这块文字可能压在哪些实色上」的候选集合。
   * 关键：书封（.library-cover）与七种纸面（.reader-bg-*）的底色写在 background-image
   * 的渐变里，backgroundColor 是透明的——只摸 backgroundColor 会一路摸到画布，
   * 于是白字压墨色书封被算成 1.05:1（实际 8.9~15:1），纸面正文同理。
   *
   * BD-108 三处修正：
   *  ① 画布底色不能再写死 #15181c——晨校画布是 #eceff2，写死深色会让晨校量到的
   *     半透明层全压到深色底上，虚报。改成实读 body 的 backgroundColor（跟随主题）。
   *  ② `transparent` 色标计算值是 rgba(0,0,0,0)，colorsIn 把它当黑色收进来（a=0），
   *     over() 又把它「合成」回下层实色——纸面 radial 的 transparent 一档被当成
   *     黑色压在正文下面，白纸正文量出 1.05:1。全透明色标不贡献实色，直接丢。
   *  ③ 一个元素的 background-image 有**多层**时，必须按 CSS 绘制顺序合成：
   *     先算最下层（linear 纸底），把它的色标集当作 acc；上一层（radial 高光）
   *     的每个色标压在 acc 里每一档上，得到新一档 acc；顺次往上。
   *     顶层 radial 覆盖范围有限（circle at 50% 0%），它的 transparent 部分露出下层，
   *     所以下层原色也留在 acc 里做候选。
   */
  const canvas = canvasSeed
    // BD-109：穿进 EPUB iframe 时，正文实际压在宿主 viewerRef 的内联纸色上
    // （iframe body 是透明的，getComputedStyle 在本 frame 里摸不到那块纸）。
    // 由 Node 侧把宿主纸色传进来当画布种子，别再退回浅色默认值。
    ? { r: canvasSeed.r, g: canvasSeed.g, b: canvasSeed.b, a: canvasSeed.a ?? 1 }
    : (() => {
        const c = parse(getComputedStyle(document.body).backgroundColor);
        return c && c.a > 0.99 ? c : { r: 236, g: 239, b: 242, a: 1 };
      })();
  const bgCandidates = (el) => {
    let cur = el;
    let acc = [canvas];
    const stack = [];
    while (cur) {
      const cs = getComputedStyle(cur);
      const bg = parse(cs.backgroundColor);
      const layers = cs.backgroundImage && cs.backgroundImage !== "none" ? imageLayers(cs.backgroundImage) : [];
      if ((bg && bg.a > 0) || layers.some((l) => l.stops.length)) stack.push({ bg, layers });
      const opaqueSolid = bg && bg.a > 0.99 && !layers.length;
      if (opaqueSolid) break;
      cur = cur.parentElement;
    }
    // stack 从最里层元素向外摸，index 0 是后代、末位是祖先；
    // 绘制顺序反过来：祖先先画、后代压在上面，所以从末位往 0 走。
    for (let i = stack.length - 1; i >= 0; i -= 1) {
      const { bg, layers } = stack[i];
      // 元素自己：先把背景色（若有）压到 acc 上（bg 在所有 background-image 之下，但画在祖先之上）。
      let local = acc.slice();
      if (bg && bg.a > 0) {
        local = bg.a > 0.99 ? [bg] : local.map((base) => over(bg, base));
      }
      // 图像层：CSS 书写顺序是先写在上、后写在下；所以从后往前（下→上）逐层压。
      for (let j = layers.length - 1; j >= 0; j -= 1) {
        const { stops, partial } = layers[j];
        if (!stops.length) continue;
        const next = [];
        for (const stop of stops) {
          if (stop.a > 0.99) {
            next.push(stop);
          } else {
            for (const base of local) next.push(over(stop, base));
            // radial/conic 只画在局部，圆外那块整层不画——下层候选必须留着。
            // linear 铺满整盒，半透明一档也已经 over(base) 算过了，下层不会露出，
            // 于是**不**保留下层候选，免得把画布深色当成正文底。
            if (partial) for (const base of local) next.push(base);
          }
        }
        if (next.length) local = next;
      }
      acc = local;
    }
    return acc;
  };
  const hexOf = (c) => `#${[c.r, c.g, c.b].map((v) => Math.round(v).toString(16).padStart(2, "0")).join("")}`;
  const levelOf = (fg, bg) => {
    // 半透明前景先合成到实底上再比令牌：继承/叠色出来的值同样要归到它实际呈现的那一级
    const solid = fg.a < 1 ? over(fg, bg) : fg;
    const hex = hexOf(solid).toLowerCase();
    if (hex === levels.tertiary?.toLowerCase()) return "tertiary";
    if (hex === levels.secondary?.toLowerCase()) return "secondary";
    if (hex === levels.primary?.toLowerCase()) return "primary";
    return "custom";
  };
  const scope = rootSelector ? document.querySelector(rootSelector) : document.body;
  if (!scope) return { found: false, rows: [], decorative: 0 };
  // BD-114：文字真实呈现的浓度 = 自身颜色 alpha × 整条祖先链上的 CSS opacity 之积。
  // 旧判据只看元素自己的 cs.opacity，把「父级 label 挂 opacity-70、文字子元素本身 opacity:1」
  // 这种最典型的写法漏掉了——AI 发送确认里排除掉的分组正是靠父 label 的 opacity-70 压暗，
  // 晨校合成后只有 2.79:1 却一直报绿。这里沿祖先链把 opacity 累乘进前景 alpha。
  const effectiveOpacity = (node) => {
    let o = 1;
    for (let p = node; p && p.nodeType === 1; p = p.parentElement) o *= Number(getComputedStyle(p).opacity);
    return o;
  };
  const rows = [];
  let decorative = 0;
  for (const el of scope.querySelectorAll("*")) {
    const own = [...el.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent.trim()).join(" ").trim();
    if (!own) continue;
    // 纯标点（没有任何字母/数字/CJK）不承载文字信息，按装饰处理：阅读底栏那个
    // 用 --separator 上色的一竖 "|" 就是这种（实测 1.4:1），它的作用是把两组数字
    // 隔开，不是被人读出来的字。单独计数，免得这条豁免被拿去当挡箭牌。
    if (!/[\p{L}\p{N}]/u.test(own)) { decorative += 1; continue; }
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    if (r.bottom < 0 || r.top > innerHeight || r.right < 0 || r.left > innerWidth) continue;
    if (el.closest("[disabled], [aria-disabled='true']")) continue;
    const cs = getComputedStyle(el);
    const effOp = effectiveOpacity(el);
    if (cs.visibility === "hidden" || cs.display === "none" || effOp < 0.35) continue;
    if (cs.color === "transparent" || cs.color === "rgba(0, 0, 0, 0)") continue;
    const fg = parse(cs.color);
    if (!fg) continue;
    // 祖先链的 opacity 折进前景 alpha：半透明墨压到实底上才是用户真正看到的字色
    fg.a = fg.a * effOp;
    const candidates = bgCandidates(el);
    let bg = candidates[0];
    let ratio = Infinity;
    for (const cand of candidates) {
      const solidFg = fg.a < 1 ? over(fg, cand) : fg;
      const l1 = lum(solidFg);
      const l2 = lum(cand);
      const r = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
      if (r < ratio) { ratio = r; bg = cand; }
    }
    if (!Number.isFinite(ratio)) continue;
    const level = levelOf(fg, bg);
    const size = parseFloat(cs.fontSize);
    rows.push({
      text: own.slice(0, 24),
      cls: String(el.className).slice(0, 64),
      ratio: Number(ratio.toFixed(2)),
      floor: level === "tertiary" ? 3 : 4.5,
      level,
      size,
      color: cs.color,
      bg: `rgb(${Math.round(bg.r)}, ${Math.round(bg.g)}, ${Math.round(bg.b)})`
    });
  }
  return { found: true, rows, decorative };
};

// 正文纸面那 7 个范围要有一本真书可读：先空跑一次让 profile 建好目录，
// 再用 round4-seed 播种书库（它会写 book-txt-001.txt / EPUB / MD 与 library.json），
// 然后正式起一次。probe-round7 也是这个顺序——seed 直接开库会报「database 目录不存在」。
const warm = await launch();
await warm.app.close();
const seeded = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root, encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true, timeout: 60000
});
if (seeded.status !== 0) throw new Error(`seed failed: ${seeded.stderr}`);

const { app, page } = await launch();
await page.evaluate(() => { document.documentElement.dataset.appTheme = "dark"; });

await createProject(page);

/**
 * 跑一个范围：两主题各量一遍。先断言采到了足够多的文字，再断言没有跌破各自底线。
 * 采样下限对每个主题单独要求——某个主题下整块区域不可见（例如弹层在浅色下换了一套
 * 布局）同样会让采样塌掉，那也该报红而不是静默跳过。
 */
async function auditScope(label, rootSelector, minSamples) {
  scopeTotal += 1;
  for (const [theme, levels] of Object.entries(THEMES)) {
    await page.evaluate((t) => { document.documentElement.dataset.appTheme = t; }, theme);
    await page.waitForTimeout(250);
    const { found, rows, decorative } = await page.evaluate(sweepScript, { rootSelector: rootSelector ?? null, levels });
    if (!found) {
      check(`${theme} ${label} 范围可定位`, false, rootSelector);
      continue;
    }
    const bad = rows.filter((r) => r.ratio < r.floor);
    const worst = rows.reduce((a, r) => (a === null || r.ratio < a.ratio ? r : a), null);
    check(`${theme} ${label} 采样数 ≥${minSamples}`, rows.length >= minSamples, `实测 ${rows.length} 个文字元素（另跳过装饰性标点 ${decorative} 个）`);
    check(`${theme} ${label} 文字全部达标`, bad.length === 0,
      bad.length
        ? `${bad.length} 处低于底线，最差 ${Math.min(...bad.map((b) => b.ratio)).toFixed(2)}:1（"${bad.sort((a, b) => a.ratio - b.ratio)[0].text}" ${bad[0].color} on ${bad[0].bg}）`
        : `最低 ${worst ? worst.ratio.toFixed(2) : "-"}:1（"${worst ? worst.text : "-"}"）`);
    if (bad.length) {
      // 每处都点名：只说「最差」会让人只修一条，剩下几条下次再犯
      for (const b of bad.sort((x, y) => x.ratio - y.ratio).slice(0, 8)) {
        console.log(`        ${b.ratio}:1  ≤${b.floor}  "${b.text}"  ${b.color} on ${b.bg}  ${b.cls}`);
      }
    }
  }
}

const ok = async (label, fn) => {
  try {
    await fn();
    return true;
  } catch (error) {
    check(`${label} 可进入`, false, String(error.message).slice(0, 90));
    return false;
  }
};

// 向导排在创建项目之前：此刻首页必然是空态，「新建第一个项目」是唯一入口，
// 不依赖有项目时的工具栏按钮文案。
await ok("建项目向导", async () => {
  // 项目侧栏在写作台里点「项目」不会回首页，要先点返回（probe-round8 踩过的同一个坑）
  if (await page.locator(".project-nav-back").isVisible().catch(() => false)) {
    await page.locator(".project-nav-back").click();
    await page.waitForTimeout(400);
  }
  await goto(page, "项目");
  const createBtn = page.getByRole("button", { name: /新建项目/ }).first();
  await createBtn.waitFor({ state: "visible", timeout: 10000 });
  await createBtn.click();
  await page.locator("[role=dialog]").first().waitFor({ state: "visible", timeout: 15000 });
  await page.waitForTimeout(400);
});
await auditScope("建项目向导", "[role=dialog]", 5);
// 向导的关闭只挂在「取消」上（Dialog 的 Esc 由外层遮罩吃掉），点取消再等遮罩撤掉
await page.getByRole("button", { name: "取消" }).first().click();
await page.waitForSelector("[role=dialog]", { state: "detached", timeout: 8000 });
await page.waitForTimeout(400);


await createProject(page);

// ---------- 8 个主视图 ----------
await ok("项目", async () => { await goto(page, "项目"); await page.waitForSelector(".project-home, .project-workbench", { timeout: 10000 }); });
await auditScope("项目", null, 25);

const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await goto(page, "项目");
  // 从别的屏点「项目」通常直接回到上次的写作台；只有真落在项目列表时才需要点条目
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

await ok("写作", async () => { await workbenchView("写作", ".writing-desk"); });
await auditScope("写作", null, 30);
await ok("设定卡", async () => { await workbenchView("设定卡", ".cards-page"); });
await auditScope("设定卡", null, 30);
await ok("大纲", async () => { await workbenchView("大纲", ".project-workbench"); });
await auditScope("大纲", null, 25);

// 项目内其余四个视图（BD-107）：概览 / 全书预览 / 写作统计 / 版本历史。
// 导航文案取自 registry.ts 的 PROJECT_NAV_ITEMS，选择器取自各页根节点。
for (const [label, selector] of [
  ["概览", ".overview-page"],
  ["全书预览", ".preview-page"],
  ["写作统计", ".stats-page"],
  ["版本历史", ".history-page"]
]) {
  await ok(label, async () => { await workbenchView(label, selector); });
  await auditScope(label, null, 15);
}

for (const [label, selector] of [["书库", ".desktop-library-row"], ["收件箱", ".inbox-list"], ["设置", ".desktop-page-stack"]]) {
  await ok(label, async () => { await goto(page, label); await page.waitForSelector(selector, { timeout: 10000 }).catch(() => {}); });
  await auditScope(label, null, 25);
}
await ok("资料阅读", async () => { await goto(page, "资料阅读"); await page.waitForTimeout(500); });
await auditScope("资料阅读", null, 20);

// ---------- 4 个弹层 ----------
await ok("查找替换", async () => {
  await workbenchView("写作", ".writing-desk");
  await page.getByRole("button", { name: /查找替换/ }).first().click();
  await page.waitForSelector('[data-testid="replace-panel"]', { timeout: 8000 });
  await page.waitForTimeout(300);
});
await auditScope("查找替换", '[data-testid="replace-panel"]', 4);
await page.getByTestId("replace-panel-close").click().catch(() => {});
await page.waitForTimeout(300);

await ok("本地校对", async () => {
  await page.getByRole("button", { name: /校对/ }).first().click();
  await page.waitForSelector('[role="dialog"][aria-label="本地校对"]', { timeout: 10000 });
  await page.waitForTimeout(500);
});
await auditScope("本地校对", '[role="dialog"][aria-label="本地校对"]', 4);
await page.getByRole("button", { name: "关闭校对" }).click().catch(() => {});
await page.waitForTimeout(400);

await ok("导出作品", async () => {
  await page.getByRole("button", { name: /导出成稿/ }).first().click();
  await page.waitForSelector('[role="dialog"][aria-label="导出作品"]', { timeout: 10000 });
  await page.waitForTimeout(500);
});
await auditScope("导出作品", '[role="dialog"][aria-label="导出作品"]', 8);
await page.getByRole("button", { name: /关闭|取消/ }).last().click().catch(() => {});

await ok("命令面板", async () => {
  await workbenchView("写作", ".writing-desk");
  // 面板的快捷键是 Ctrl/⌘+P（CreationProjectsPage 的 keydown），
  // 界面上「搜索项目、收件箱、资料」那行提示写的 Ctrl K 是另一个全局搜索，别混。
  await page.keyboard.press("Control+p");
  await page.waitForSelector('[role="dialog"][aria-label="命令面板"]', { timeout: 8000 });
  await page.waitForTimeout(400);
});
await auditScope("命令面板", '[role="dialog"][aria-label="命令面板"]', 6);
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

// BD-112：全局搜索（Ctrl+K）弹层此前从没被量过——上面命令面板那条注释就特意点过
// 「Ctrl K 是另一个全局搜索，别混」。它命中词用 <mark class="uni-hit"> 上色（color:
// --paper-ink + copper 22% 底），是继 BD-111 之后另一处「<mark> 依赖 UA 默认色」风险的
// 面：这里给一个真有命中的查询，把弹层整块 + 命中 mark 一起纳入晨校/夜校对比度。
await ok("全局搜索", async () => {
  await goto(page, "书库");
  await page.waitForSelector(".desktop-canvas", { timeout: 10000 });
  await page.keyboard.press("Control+k");
  await page.waitForSelector(".uni-search-input", { timeout: 8000 });
  await page.locator(".uni-search-input").fill("测试");
  await page.waitForSelector(".uni-hit", { timeout: 8000 });
  await page.waitForTimeout(400);
});
await auditScope("全局搜索", ".uni-search-overlay", 8);
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

// BD-113：写作速查面板（Ctrl+Shift+K，右侧 .writing-quick-reference）此前从没被量过——这是第 7 个弹层。
// 面板底是 color-mix(--bg-surface 97%, --accent-hover)：一套别的视图都没有的轻染色背景。里面全族文字
// （h3 标题 / AI 上下文条 / 三 tab / 卡片列表 strong+small+em / 空态）都走 --text-main / --text-muted /
// --text-meta 三档 token。三档字色叠在这个独有染色底上，正是「token 在别处验过、但这个新底上没验过」的
// 面。开面板扫整块纳入晨校/夜校；列表行 3px kind 竖脊是自闭合装饰 span（无文字），sweep 自动跳过。
await ok("写作速查", async () => {
  await workbenchView("写作", ".writing-desk");
  await page.keyboard.press("Control+Shift+k");
  await page.waitForSelector(".writing-quick-reference", { timeout: 8000 });
  await page.waitForTimeout(400);
});
await auditScope("写作速查", ".writing-quick-reference", 6);
await page.keyboard.press("Control+Shift+k").catch(() => {});
await page.waitForTimeout(300);

// 设置按分区量（BD-107）：整屏只算「当前分区」，其余四个分区的控件从来没被量过。
// 下限取 4：设置表单绝大多数文字是 <label> / 按钮里的直接文字，一个分区能采到的
// 独立文字节点本来就是个位数（外观实测 5），要 8 条会把「页面没渲染」和「这屏字少」混在一起。
for (const section of ["外观", "阅读器", "AI 助手", "数据与存储", "关于 / 调试"]) {
  await ok(`设置·${section}`, async () => {
    await goto(page, "设置");
    await page.waitForSelector(".settings-nav", { timeout: 10000 });
    await page.locator(".settings-nav button", { hasText: section }).first().click();
    await page.waitForSelector(".settings-forms", { timeout: 10000 });
    await page.waitForTimeout(400);
  });
  await auditScope(`设置·${section}`, ".settings-forms", 4);
}

// ---------- 正文纸面（BD-108）----------
// 守卫对 .reader-shell-* / .reader-bg-* 的字面量整族豁免，前提是「壳的底与字成对写死、
// 不随主题翻转」。这里把七种纸面逐个开进真正文里量：纸面文字确实两主题同值（豁免成立），
// 而顶栏、目录面板、进度这些跟着主题走的部分也一并被扫到。
// 纸面壳的底色写在 .reader-bg-* 的渐变里（backgroundColor 是透明的），所以判据必须
// 把渐变色标一起收进候选，见上面 bgCandidates 的注释：取最坏一档，而不是取第一个色标。
const PAPER_OPTIONS = [
  ["白纸", 12], ["暖纸", 12], ["护眼", 12], ["夜间", 12], ["琥珀", 12], ["羊皮纸", 12], ["绿豆沙", 12]
];
for (const [label, minSamples] of PAPER_OPTIONS) {
  await ok(`纸面·${label}`, async () => {
    await goto(page, "设置");
    await page.waitForSelector(".settings-nav", { timeout: 10000 });
    await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
    const swatch = page.locator(`[role="radiogroup"][aria-label="书籍背景"] button[aria-label="${label}"]`).first();
    await swatch.waitFor({ state: "visible", timeout: 10000 });
    await swatch.click();
    await page.waitForTimeout(300);
    await goto(page, "书库");
    await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
    await page.locator(".desktop-library-row", { hasText: "测试 TXT 资料" }).first().click();
    await page.waitForSelector(".reader-host article", { timeout: 30000 });
    await page.waitForTimeout(600);
  });
  await auditScope(`纸面·${label}`, ".reader-host", minSamples);
  await page.getByRole("button", { name: "返回书库" }).first().click().catch(() => {});
  await page.waitForTimeout(400);
}

// BD-110：上面那圈纸面量的是纯 TXT 书（renderChapterParagraphs，只挂 mb-5 的 <p>）。
// Markdown 书走的是另一条渲染路——dangerouslySetInnerHTML 吐出的 h1/h2、blockquote、
// 列表项、表格 th/td、以及 [文本](url) 生成的 <a>，都是不受信的用户 DOM，此前没有任何
// 范围量过。这正是 BD-109 刚证明过的盲区：没被量到的动态内容面藏着真缺陷。这里补一圈
// 逐纸面的 markdown-reader 扫描，把「链接/引用/标题也一起跟着纸墨走」钉成回归网——
// 实测链接靠 Tailwind preflight 的 a{color:inherit} 继承 <article> 的 readerTextColor，
// 夜纸 15.90:1；若日后有人给 .markdown-reader a 上死色或改 preflight，这圈当场红。
for (const [label] of PAPER_OPTIONS) {
  await ok(`MD纸面·${label}`, async () => {
    await goto(page, "设置");
    await page.waitForSelector(".settings-nav", { timeout: 10000 });
    await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
    const swatch = page.locator(`[role="radiogroup"][aria-label="书籍背景"] button[aria-label="${label}"]`).first();
    await swatch.waitFor({ state: "visible", timeout: 10000 });
    await swatch.click();
    await page.waitForTimeout(300);
    await goto(page, "书库");
    await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
    await page.locator(".desktop-library-row", { hasText: "测试 Markdown 笔记" }).first().click();
    await page.waitForSelector(".reader-host article a", { timeout: 30000 });
    await page.waitForTimeout(600);
  });
  await auditScope(`MD纸面·${label}`, ".reader-host", 8);
  await page.getByRole("button", { name: "返回书库" }).first().click().catch(() => {});
  await page.waitForTimeout(400);
}

await ok("EPUB 阅读器", async () => {
  await goto(page, "书库");
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(".desktop-library-row", { hasText: "测试 EPUB 资料" }).first().click();
  await page.waitForSelector(".reader-root", { timeout: 30000 });
  await page.waitForTimeout(1200);
});
// 宿主壳（顶栏、翻页控件、目录侧栏）走常规 auditScope：
await auditScope("EPUB 阅读器", ".reader-root", 6);
await page.getByRole("button", { name: "返回书库" }).first().click().catch(() => {});
await page.waitForTimeout(400);

/**
 * BD-109：EPUB 正文在同源 iframe 里，早先这个范围只量宿主壳、穿不进 iframe，
 * 于是「夜纸 + publisher 保留原书样式」下正文退回 UA 黑、压在 #15100e 上 ≈1.05:1
 * 的缺陷一直没人量到（根因见 epub-engine.ts 的双 select 顶掉类名注释）。
 * 正文实际压在宿主 viewerRef 的内联纸色上（iframe body 是透明的），所以先从父文档
 * 往上摸到那块不透明纸色，作为画布种子传进 iframe，再复用同一套逐层合成扫描。
 */
async function epubIframeScope(label, minSamples) {
  scopeTotal += 1;
  const host = await page.evaluate(() => {
    const iframe = document.querySelector(".reader-root iframe");
    let cur = iframe ? iframe.parentElement : null;
    while (cur) {
      const bg = getComputedStyle(cur).backgroundColor;
      const m = bg.match(/rgba?\(([^)]+)\)/);
      if (m) {
        const p = m[1].split(",").map((x) => Number(x.trim()));
        if (p.length < 4 || p[3] > 0.99) return { r: p[0], g: p[1], b: p[2], a: 1 };
      }
      cur = cur.parentElement;
    }
    return null;
  });
  if (!host) { check(`EPUB·${label} 宿主纸色可定位`, false, "沿 iframe 祖先没找到不透明底色"); return; }
  const handle = await page.locator(".reader-root iframe").first().elementHandle();
  const frame = handle ? await handle.contentFrame() : null;
  if (!frame) { check(`EPUB·${label} iframe 可达`, false); return; }
  const { found, rows, decorative } = await frame.evaluate(sweepScript, { rootSelector: null, levels: THEMES.dark, canvasSeed: host });
  if (!found) { check(`EPUB·${label} iframe 正文可定位`, false, "document.body 为空"); return; }
  const bad = rows.filter((r) => r.ratio < r.floor);
  const worst = rows.reduce((a, r) => (a === null || r.ratio < a.ratio ? r : a), null);
  check(`EPUB·${label} 采样数 ≥${minSamples}`, rows.length >= minSamples, `实测 ${rows.length} 个正文元素（跳过装饰 ${decorative}）`);
  check(`EPUB·${label} 正文墨色跟着纸走`, bad.length === 0,
    bad.length
      ? `${bad.length} 处低于底线，最差 ${Math.min(...bad.map((b) => b.ratio)).toFixed(2)}:1（"${bad.sort((a, b) => a.ratio - b.ratio)[0].text}" ${bad[0].color} on ${bad[0].bg}）`
      : `最低 ${worst ? worst.ratio.toFixed(2) : "-"}:1（"${worst ? worst.text : "-"}"）`);
  if (bad.length) for (const b of bad.sort((x, y) => x.ratio - y.ratio).slice(0, 8)) {
    console.log(`        ${b.ratio}:1  ≤${b.floor}  "${b.text}"  ${b.color} on ${b.bg}  ${b.cls}`);
  }
}

// publisher 模式：逐纸面进 EPUB。出版方排版保留原色，但深色夜纸下必须被强制浅色墨，
// 否则 UA 黑字压在深底不可读——这正是 BD-109 修复要长期钉住的不变式。
for (const [label] of PAPER_OPTIONS) {
  await ok(`EPUB 纸面·${label}`, async () => {
    await goto(page, "设置");
    await page.waitForSelector(".settings-nav", { timeout: 10000 });
    await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
    const swatch = page.locator(`[role="radiogroup"][aria-label="书籍背景"] button[aria-label="${label}"]`).first();
    await swatch.waitFor({ state: "visible", timeout: 10000 });
    await swatch.click();
    await page.waitForTimeout(300);
    await goto(page, "书库");
    await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
    await page.locator(".desktop-library-row", { hasText: "测试 EPUB 资料" }).first().click();
    await page.waitForSelector(".reader-root iframe", { timeout: 30000 });
    await page.waitForTimeout(1200);
  });
  await epubIframeScope(`publisher·${label}`, 2);
  await page.getByRole("button", { name: "返回书库" }).first().click().catch(() => {});
  await page.waitForTimeout(300);
}

// unified 模式 × 夜间：这条路径由我们上色（取 readerTextColor），最易与纸色脱节。
await ok("EPUB 纸面·unified·夜间", async () => {
  await goto(page, "设置");
  await page.waitForSelector(".settings-nav", { timeout: 10000 });
  await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
  await page.locator('[data-setting-id="reader.epubStyleMode"] select').first().selectOption("unified");
  await page.locator(`[role="radiogroup"][aria-label="书籍背景"] button[aria-label="夜间"]`).first().click();
  await page.waitForTimeout(300);
  await goto(page, "书库");
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(".desktop-library-row", { hasText: "测试 EPUB 资料" }).first().click();
  await page.waitForSelector(".reader-root iframe", { timeout: 30000 });
  await page.waitForTimeout(1200);
});
await epubIframeScope("unified·夜间", 2);
// 复位默认 publisher + 白纸，别把这套设置留在 profile 里干扰其它判断。
await page.getByRole("button", { name: "返回书库" }).first().click().catch(() => {});
await goto(page, "设置");
await page.waitForSelector(".settings-nav", { timeout: 10000 });
await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
await page.locator('[data-setting-id="reader.epubStyleMode"] select').first().selectOption("publisher");
await page.locator(`[role="radiogroup"][aria-label="书籍背景"] button[aria-label="白纸"]`).first().click();
await page.waitForTimeout(200);

await app.close();
rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n[audit-contrast] ${failures.length} 项未通过：${failures.join(" | ")}`);
  process.exit(1);
}
const SCOPE_TOTAL = scopeTotal;
console.log(`\nCONTRAST_OK：${SCOPE_TOTAL} 个范围 × 晨校/夜校，文字全部达标。`);
