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
 *  · 跳过 disabled、透明度 <0.35、尺寸 <2px、视口外的元素：它们不承载可读信息。
 *
 * 范围：8 个主视图 + 4 个弹层（建项目向导、查找替换、本地校对、导出作品），
 * 每个范围在晨校与夜校各量一遍——两套底色是两套事实，夜校绿不代表晨校绿。
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
const sweepScript = ({ rootSelector, levels }) => {
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
  const bgOf = (el) => {
    const stack = [];
    let cur = el;
    while (cur) {
      const c = parse(getComputedStyle(cur).backgroundColor);
      if (c && c.a > 0) {
        stack.push(c);
        if (c.a > 0.99) break;
      }
      cur = cur.parentElement;
    }
    let base = stack.length ? stack[stack.length - 1] : { r: 21, g: 24, b: 28, a: 1 };
    if (base.a < 0.99) base = { r: 21, g: 24, b: 28, a: 1 };
    for (let i = stack.length - 2; i >= 0; i -= 1) base = over(stack[i], base);
    return base;
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
  if (!scope) return { found: false, rows: [] };
  const rows = [];
  for (const el of scope.querySelectorAll("*")) {
    const own = [...el.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent.trim()).join(" ").trim();
    if (!own) continue;
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    if (r.bottom < 0 || r.top > innerHeight || r.right < 0 || r.left > innerWidth) continue;
    if (el.closest("[disabled], [aria-disabled='true']")) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.display === "none" || Number(cs.opacity) < 0.35) continue;
    if (cs.color === "transparent" || cs.color === "rgba(0, 0, 0, 0)") continue;
    const fg = parse(cs.color);
    if (!fg) continue;
    const bg = bgOf(el);
    const solidFg = fg.a < 1 ? over(fg, bg) : fg;
    const l1 = lum(solidFg);
    const l2 = lum(bg);
    const ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
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
  return { found: true, rows };
};

const { app, page } = await launch();
await page.evaluate(() => { document.documentElement.dataset.appTheme = "dark"; });

await createProject(page);

/**
 * 跑一个范围：两主题各量一遍。先断言采到了足够多的文字，再断言没有跌破各自底线。
 * 采样下限对每个主题单独要求——某个主题下整块区域不可见（例如弹层在浅色下换了一套
 * 布局）同样会让采样塌掉，那也该报红而不是静默跳过。
 */
async function auditScope(label, rootSelector, minSamples) {
  for (const [theme, levels] of Object.entries(THEMES)) {
    await page.evaluate((t) => { document.documentElement.dataset.appTheme = t; }, theme);
    await page.waitForTimeout(250);
    const { found, rows } = await page.evaluate(sweepScript, { rootSelector: rootSelector ?? null, levels });
    if (!found) {
      check(`${theme} ${label} 范围可定位`, false, rootSelector);
      continue;
    }
    const bad = rows.filter((r) => r.ratio < r.floor);
    const worst = rows.reduce((a, r) => (a === null || r.ratio < a.ratio ? r : a), null);
    check(`${theme} ${label} 采样数 ≥${minSamples}`, rows.length >= minSamples, `实测 ${rows.length} 个文字元素`);
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

await app.close();
rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n[audit-contrast] ${failures.length} 项未通过：${failures.join(" | ")}`);
  process.exit(1);
}
console.log("\nCONTRAST_OK：12 个范围 × 晨校/夜校，文字全部达标。");
