/**
 * 键盘焦点可见指示普查（BD-205 立）。
 *
 * 为什么要在运行时量：§2.3 焦点环的全站兜底是 editorial-studio.css 的
 * `button/input/textarea/select/[tabindex]:focus-visible` 元素级规则，而各功能样式块
 * 里有一族 `outline: none` 的压环写法——压不压得掉取决于特异度 **和** 发射顺序
 * （静态 4 张按行序、块级 16 张在其后），纸面推演两头都有误判：
 *  `.uni-search-input` 看着像被 styles.css:97 的压法坑，实测却有 2px 环（真正兜底是
 *  editorial 的元素级规则，特异度比类名那条高）；BD-126 当年不实测就修错了关系图。
 *  同族「类级 outline:none 压掉全局环」的形状（relation-graph、paper-stepper）修过
 *  多次，但从没一张普查网把「每个键盘停靠点都有可见指示」钉成常驻判据。
 *
 * 判据（停靠点有指示 = 以下任一）：
 *  · outline 实线且宽度 ≥1px；
 *  · box-shadow 里带 `0 0 0 Npx` 纯环（本仓 §2.3 的替代形状，如 .pe-input:focus）；
 *  · 其他非 none 的 box-shadow（保守放行，红名单人工复核时再收紧）。
 * 跳过 disabled 与 [tabindex="-1"] 的程序化着陆点（PageTransition 容器等——它们是
 *  WCAG 2.4.3 的落点，不是 Tab 序列成员；2.4.7 管的是键盘可聚焦序列）。
 *
 * range 输入框走**像素差分**，不走 computed 读取：本仓 Electron 33.4.11 / Chromium
 * 130 实测 `getComputedStyle(el, "::-webkit-slider-thumb").boxShadow` 恒为 "none"——
 * 连无条件命中的基础规则（--shadow-control-thumb）都读不出来，伪元素读取在这里
 * 不可信。BD-205 初判「:focus-visible::-webkit-slider-thumb 永不命中」即栽在这把坏
 * 尺子上：像素取证（CSSOM 隔离两条 thumb 规则各改醒目色 + 真键盘聚焦裁剪区截图差分）
 * 证明该规则**实际绘制**，产品无缺陷，是普查假阳性。range 判据改为：聚焦态裁剪图
 * 与两张离焦基线图比对——离焦基线稳定且聚焦图与它不同 = 有可见指示；基线不稳
 * （区域疑似有动画）保守放行。脚本聚焦后键盘模态仍在（像素取证实测 :focus-visible
 * 依然亮），所以探针结束后 focus() 归位、Tab 序列不中断。差分基准的牙齿教训：
 * box-shadow 是单属性，聚焦环一挂上就**顶掉** thumb 常驻接触影（BD-86 量的那 311 px）
 * ——抹环注入若写成 none，聚焦图会因接触影消失仍与离焦图不同（假过牙）；牙齿必须把
 * 聚焦态 box-shadow 回填成接触影原值，制造真正「聚焦前后逐像素相同」的破坏。
 *
 * 遍历方式：真键盘 Tab（Blink 的 :focus-visible 判据依赖键盘交互模态，程序 focus()
 * 在部分元素上不点亮环，会假红）。每个范围 Tab 到回环或步数上限为止，按
 * tag+class+aria-label 去重。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-focus-rings.mjs
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-focusrings-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

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

const goto = async (label) => {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};
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
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("焦点普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

/** 页面内单次判定：当前 activeElement 的可见指示。 */
const inspectScript = () => {
  const el = document.activeElement;
  if (!el || el === document.body) return null;
  const cs = getComputedStyle(el);
  const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean);
  const id = `${el.tagName.toLowerCase()}.${cls.join(".") || "(无类名)"}[${el.getAttribute("aria-label") || el.textContent?.trim().slice(0, 12) || ""}]`;
  const skip = el.matches("[tabindex='-1']") || el.matches(":disabled") || el.closest("[inert]");
  const outlineOk = cs.outlineStyle !== "none" && parseFloat(cs.outlineWidth) >= 1;
  const ringShadow = /0 0 0 [1-9]/.test(cs.boxShadow);
  const anyShadow = cs.boxShadow !== "none";
  // range 输入框的环画在 ::-webkit-slider-thumb 伪元素上，宿主 computed 看不到、
  // 伪元素 computed 读取恒为 none（见文件头）——交给 sweep() 里的像素差分判。
  const isRange = el.tagName.toLowerCase() === "input" && el.type === "range";
  if (isRange) el.setAttribute("data-ring-probe", "1");
  return {
    id,
    skip,
    isRange,
    outline: `${cs.outlineStyle} ${cs.outlineWidth}`,
    boxShadow: cs.boxShadow.slice(0, 80),
    hasIndicator: outlineOk || ringShadow || anyShadow,
    ringOnly: outlineOk || ringShadow
  };
};

const reds = new Map();
const seenAll = new Set();
const seenProbe = new Set();

async function sweep(label, { resetFocus = true, maxStops = 120 } = {}) {
  if (resetFocus) {
    await page.evaluate(() => document.activeElement?.blur?.());
    await page.waitForTimeout(120);
  }
  const seen = new Map();
  let stuck = 0;
  let last = null;
  for (let i = 0; i < maxStops; i += 1) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(40);
    const info = await page.evaluate(inspectScript);
    if (!info) { stuck += 1; if (stuck > 5) break; continue; }
    if (info.id === last) { stuck += 1; if (stuck > 10) break; } else { stuck = 0; last = info.id; }
    if (seen.has(info.id)) {
      // 回环再两圈即停
      if (i > 0 && seen.get(info.id) === "done") break;
    }
    if (info.isRange) {
      // 像素差分：聚焦图对比两张离焦基线（见文件头）。探针结束后 focus() 归位，
      // 键盘模态不丢，Tab 序列照常往下走。
      const pkey = `${label}|${info.id}`;
      const cleanup = () => page.evaluate(() => {
        document.querySelectorAll("[data-ring-probe]").forEach((n) => n.removeAttribute("data-ring-probe"));
      });
      if (!info.skip && !seenProbe.has(pkey)) {
        seenProbe.add(pkey);
        const clip = await page.evaluate(() => {
          const el = document.querySelector("[data-ring-probe]");
          if (!el) return null;
          const r = el.getBoundingClientRect();
          return {
            x: Math.max(0, r.x - 8),
            y: Math.max(0, r.y - 8),
            width: r.width + 16,
            height: r.height + 16
          };
        });
        if (clip) {
          const shotOn = await page.screenshot({ clip });
          await page.evaluate(() => document.activeElement?.blur?.());
          await page.waitForTimeout(160);
          const shotOff1 = await page.screenshot({ clip });
          await page.waitForTimeout(340);
          const shotOff2 = await page.screenshot({ clip });
          await page.evaluate(() => {
            const el = document.querySelector("[data-ring-probe]");
            el?.removeAttribute("data-ring-probe");
            el?.focus();
          });
          const stable = Buffer.compare(shotOff1, shotOff2) === 0;
          info.hasIndicator = stable ? Buffer.compare(shotOn, shotOff1) !== 0 : true;
          info.pixelProbe = stable ? (info.hasIndicator ? "像素差分=有环" : "像素差分=无变化") : "基线不稳·保守放行";
        } else {
          await cleanup();
        }
      } else {
        await cleanup();
      }
    }
    seen.set(info.id, seen.has(info.id) ? "done" : "first");
    if (info.skip) continue;
    if (!seenAll.has(`${label}|${info.id}`)) {
      seenAll.add(`${label}|${info.id}`);
      if (!info.hasIndicator) {
        const key = info.id;
        if (!reds.has(key)) reds.set(key, { where: [label], ...info });
        else if (!reds.get(key).where.includes(label)) reds.get(key).where.push(label);
      }
    }
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

for (const [label, prep] of scopes) {
  await prep().catch(() => {});
  await sweep(label);
}

// 弹层：全局搜索（Ctrl+Shift+F）与写作速查面板（写作台内 Ctrl+Shift+K）
await workbenchView("写作", ".writing-desk").catch(() => {});
await page.keyboard.press("Control+Shift+f");
await page.waitForTimeout(500);
await sweep("弹层·全局搜索", { maxStops: 40 });
await page.keyboard.press("Escape");
await page.waitForTimeout(300);
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(600);
await sweep("弹层·写作速查", { maxStops: 60 });
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(300);

console.log(`扫描停靠点 ${seenAll.size} 个（按范围×元素去重）。`);
if (reds.size > 0) {
  console.log(`FOCUS_RING_RED：${reds.size} 个键盘停靠点无任何可见指示：`);
  for (const r of reds.values()) {
    console.log(`  - ${r.id}  [${r.where.join("、")}]  outline=${r.outline} shadow=${r.boxShadow}${r.pixelProbe ? ` ${r.pixelProbe}` : ""}`);
  }
  await app.close();
  process.exit(1);
}
console.log(`FOCUS_RING_OK：${seenAll.size} 个停靠点全部有可见指示。`);
await app.close();
