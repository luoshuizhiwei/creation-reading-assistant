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
 *
 * BD-206 起自带书库种子（首启建 schema → round4-seed.cjs 播 TXT/EPUB/MD → 重开），
 * 覆盖阅读器正文面：TXT/EPUB、阅读设置抽屉、目录面板、TXT 搜索浮层、新建项目向导。
 * epub.js 正文 iframe 豁免（环由书内 CSS 管）。
 *
 * BD-241 把这张网从「走过 16 个范围」扩成「走过全仓每一处撤焦点环的声明」，同时收紧判据：
 *  · 覆盖面：新增 项目·关系图 / 设置四个非默认分区 / 弹层·写作速查·全局搜索 / 卡片库 /
 *    项目·大纲行内改名态。摸底实测（/tmp/bd241-probe*.log 四趟）：16 处 `outline: none|0`
 *    里本体无环的 9 处，此前只有 4 处真被这张网判过，另外 5 面各卡一个可达性条件
 *    （视图 tab、另一屏、非默认分区、条件挂载、状态门 + blur 即卸载）。
 *  · 判据：删掉 `anyShadow`（有任意阴影就放行——常驻投影从不随聚焦变化也会假绿）；
 *    修掉 `ringShadow` 那颗假牙（`/0 0 0 [1-9]/` 是作者写法，computed 序列化成
 *    `rgb(...) 0px 0px 0px 3px`，原正则对本网读到的值恒不命中）；本体无确定环的一律走
 *    **聚焦差分**（同一元素 blur 前后逐项比）→ **容器代持**（往上三级找随聚焦才出现的环，
 *    并要求几何包住焦点元素、面积 ≤8 倍，防页面级容器替全场背书）。收紧后复跑：原有
 *    553 个停靠点全部走本体环直判、差分分支 0 次触发（/tmp/bd241-fr-delta.log），
 *    即这次收紧没有把任何已判过的面改口，只把新补进来的面按新口径判。
 *  · 本批由这把网抓到的产品缺陷：写作速查面板的搜索输入与卡片字段输入
 *    （`.writing-quick-search input, .writing-quick-field input { outline: 0 }`，
 *    随写作台懒加载分块排在主包之后，与全局 `input:focus-visible` 同为 0-1-1 平手、
 *    平手输在加载顺序）——改前实测 outline="none 0px"、shadow=none、容器聚焦前后逐字相同，
 *    即彻底无指示；修在 writing-quick-reference.css，由该块自己画环。
 */
import { spawnSync } from "node:child_process";
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

// BD-206：阅读器范围要真书——首启建 schema（workspace.sqlite），再用 round4 种子
// 播 TXT/EPUB/MD 三本资料（与 probe-reader.mjs 同一套路），然后重开正式实例。
{
  const { app } = await launch();
  await app.close();
}
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const { app, page } = await launch();

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
  // BD-206：向导弹层也是键盘停靠面，先扫再走流程（Dialog 有焦点陷阱，Tab 出得去）。
  await sweep("弹层·新建项目向导", { maxStops: 30 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("焦点普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

/**
 * 页面内单次判定：当前 activeElement 的可见指示。
 *
 * BD-241 收紧判据：原先 `hasIndicator = outlineOk || ringShadow || anyShadow` 里
 *  · `ringShadow` 的 `/0 0 0 [1-9]/` 是按**作者写法**写的，而 computed box-shadow 序列化成
 *   `rgb(...) 0px 0px 0px 3px` —— 这条正则对本网读到的 computed 恒不命中，是颗假牙；
 *  · `anyShadow` 只要「有任意阴影」就放行，包括从不随聚焦变化的常驻投影 —— 会假绿。
 * 现在本体只认**实线 outline 环**（outlineOk，含修好后的纯环阴影）为「确定有指示」；
 * 其余一律交给 sweep() 里的**聚焦差分**（同一元素 blur 前后逐项比对）与**容器代持**
 * （往上三级找随聚焦出现的环，BD-126 的关系图搜索壳、设置页 stepper 用的就是这个形状）。
 * 实测：现有 16 个范围的 553 个停靠点**全部**走 outlineOk 直判通过（差分分支 0 次触发，
 * /tmp/bd241-fr-delta.log），所以这次收紧不会把任何已判过的面改口；新增的范围里
 * 关系图搜索框正是靠容器代持判绿的。
 */
const inspectScript = () => {
  const el = document.activeElement;
  if (!el || el === document.body) return null;
  const cs = getComputedStyle(el);
  const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean);
  const id = `${el.tagName.toLowerCase()}.${cls.join(".") || "(无类名)"}[${el.getAttribute("aria-label") || el.textContent?.trim().slice(0, 12) || ""}]`;
  const skip = el.matches("[tabindex='-1']") || el.matches(":disabled") || el.closest("[inert]")
    // BD-206：epub.js 正文在 iframe 里，焦点进 iframe 时主文档 activeElement 是
    // <iframe> 壳——环由书内 CSS 管，不归 App 判据，豁免。
    || el.tagName.toLowerCase() === "iframe";
  const outlineOk = cs.outlineStyle !== "none" && parseFloat(cs.outlineWidth) >= 1;
  // computed 序列化是 "rgb(...) 0px 0px 0px 3px"，作者写法 "0 0 0 3px" 只可能出现在未序列化的
  // 场合——两种形状都认，但只作为本体纯环的补充，不再拿「有阴影」当放行理由。
  const ringShadow = /0px 0px 0px [1-9]/.test(cs.boxShadow) || /0 0 0 [1-9]/.test(cs.boxShadow);
  // range 输入框的环画在 ::-webkit-slider-thumb 伪元素上，宿主 computed 看不到、
  // 伪元素 computed 读取恒为 none（见文件头）——交给 sweep() 里的像素差分判。
  const isRange = el.tagName.toLowerCase() === "input" && el.type === "range";
  if (isRange) el.setAttribute("data-ring-probe", "1");
  // BD-241：本体没有确定环的停靠点打上行，交给 sweep() 做聚焦差分／容器代持（两者都要
  // 离焦基线，所以 blur 之后得能按这个标尺找回同一元素）。
  el.removeAttribute("data-ring-delta");
  const needsDelta = !skip && !isRange && !(outlineOk || ringShadow);
  if (needsDelta) el.setAttribute("data-ring-delta", "1");
  return {
    id,
    skip,
    isRange,
    needsDelta,
    outline: `${cs.outlineStyle} ${cs.outlineWidth}`,
    boxShadow: cs.boxShadow.slice(0, 80),
    focused: {
      outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`,
      shadow: cs.boxShadow.slice(0, 60),
      borderColor: cs.borderTopColor,
      background: cs.backgroundColor
    },
    hasIndicator: outlineOk || ringShadow,
    ringOnly: outlineOk || ringShadow
  };
};

const reds = new Map();
const seenAll = new Set();
const seenProbe = new Set();
const unmeasurable = [];
// BD-241：判据去向点名——本体环／聚焦差分／容器代持／range 像素差分，各多少个。
const tally = new Map();
const bump = (kind) => tally.set(kind, (tally.get(kind) || 0) + 1);

/**
 * @param judgeCurrentFirst BD-241：状态门后的停靠点（大纲行内改名态输入框）先判**当前**
 *   焦点元素再开始 Tab——这类元素 blur 即卸载，不进 Tab 序列就再也回不去。
 */
async function sweep(label, { resetFocus = true, maxStops = 120, judgeCurrentFirst = false } = {}) {
  if (resetFocus) {
    await page.evaluate(() => document.activeElement?.blur?.());
    await page.waitForTimeout(120);
  }
  const seen = new Map();
  let stuck = 0;
  let last = null;
  for (let i = 0; i < maxStops; i += 1) {
    if (!(i === 0 && judgeCurrentFirst)) {
      await page.keyboard.press("Tab");
      await page.waitForTimeout(40);
    }
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
    // —— BD-241：本体没有确定环的停靠点，走聚焦差分 → 容器代持，两道都判不上才算红 ——
    // 聚焦差分：同一元素 blur 前后逐项比（outline / box-shadow / border-color / background）。
    // 「有常驻阴影」不等于「聚焦看得见」——原先 anyShadow 那条就是把这两件事混了。
    // 容器代持：往上三级找**随聚焦出现**的环（BD-126 的关系图搜索壳、设置页 stepper 的形状），
    // 并硬性要求该容器在几何上包住焦点元素、面积 ≤8 倍——否则页面级容器会替全场背书。
    // 大纲行内改名那类「blur 即提交卸载」的输入框拿不到基线：不猜、不判绿，单独记「不可测」
    // 并每次点名（这一族实测本体就有实线环，走不到这里，记下来只为防止将来悄悄漏判）。
    if (info.needsDelta) {
      const onChain = await page.evaluate(() => {
        const el = document.querySelector("[data-ring-delta]");
        const out = [];
        let n = el?.parentElement;
        for (let i = 0; n && i < 3; i += 1, n = n.parentElement) {
          const acs = getComputedStyle(n);
          const r = n.getBoundingClientRect();
          out.push({
            id: `${n.tagName.toLowerCase()}.${(n.getAttribute("class") || "").split(/\s+/).filter(Boolean).join(".") || "(无类名)"}`,
            outline: `${acs.outlineStyle} ${acs.outlineWidth} ${acs.outlineColor}`,
            shadow: acs.boxShadow.slice(0, 60),
            borderColor: acs.borderTopColor,
            x: r.x, y: r.y, w: r.width, h: r.height
          });
        }
        return out;
      });
      await page.evaluate(() => document.activeElement?.blur?.());
      await page.waitForTimeout(150);
      const probe = await page.evaluate((onChain) => {
        const el = document.querySelector("[data-ring-delta]");
        if (!el) return null; // blur 即卸载的输入框，基线取不到
        const f = getComputedStyle(el);
        const own = {
          outline: `${f.outlineStyle} ${f.outlineWidth} ${f.outlineColor}`,
          shadow: f.boxShadow.slice(0, 60),
          borderColor: f.borderTopColor,
          background: f.backgroundColor
        };
        const chain = [];
        let n = el.parentElement;
        for (let i = 0; n && i < 3; i += 1, n = n.parentElement) {
          const acs = getComputedStyle(n);
          chain.push({ shadow: acs.boxShadow.slice(0, 60), outline: `${acs.outlineStyle} ${acs.outlineWidth}`, borderColor: acs.borderTopColor });
        }
        el.removeAttribute("data-ring-delta");
        el.focus();
        return { own, chain, rect: (({ x, y, width, height }) => ({ x, y, width, height }))(el.getBoundingClientRect()), onChain };
      }, onChain);
      await page.waitForTimeout(60);
      if (!probe) {
        info.unmeasurable = true;
        info.hasIndicator = true; // 不判红，但每次点名，见输出末尾「不可测」清单
      } else {
        const on = info.focused;
        const off = probe.own;
        const changed = on.outline !== off.outline || on.shadow !== off.shadow
          || on.borderColor !== off.borderColor || on.background !== off.background;
        if (changed) {
          info.hasIndicator = true;
          info.judgedBy = "聚焦差分";
        } else {
          const eRect = probe.rect;
          const focusedChain = probe.onChain; // blur 前读的祖先链（含几何）
          const offChain = probe.chain; // blur 后读的同一批祖先
          const hasRing = (v) => /0px 0px 0px [1-9]|0 0 0 [1-9]/.test(v.shadow || "") || /solid [1-9]/.test(v.outline || "");
          const holder = focusedChain.find((onAnc, i) => {
            const offAnc = offChain[i];
            if (!onAnc || !offAnc) return false;
            // 必须是「随聚焦才出现」的环／边框变化，常驻的不算代持
            const appears = (hasRing(onAnc) && !hasRing(offAnc)) || onAnc.shadow !== offAnc.shadow
              || onAnc.borderColor !== offAnc.borderColor;
            if (!appears) return false;
            // 几何上包住焦点元素，且面积不超过 8 倍——否则页面级容器能替整场背书
            const encloses = onAnc.x <= eRect.x + 1 && onAnc.y <= eRect.y + 1
              && onAnc.x + onAnc.w >= eRect.x + eRect.width - 1
              && onAnc.y + onAnc.h >= eRect.y + eRect.height - 1;
            const areaRatio = (onAnc.w * onAnc.h) / Math.max(1, eRect.width * eRect.height);
            return encloses && areaRatio <= 8;
          });
          if (holder) {
            info.hasIndicator = true;
            info.judgedBy = `容器代持 ${holder.id}`;
          } else {
            info.hasIndicator = false;
            info.judgedBy = "本体无环·聚焦无变化·无容器代持";
          }
        }
      }
    }
    seen.set(info.id, seen.has(info.id) ? "done" : "first");
    if (info.skip) continue;
    if (!seenAll.has(`${label}|${info.id}`)) {
      seenAll.add(`${label}|${info.id}`);
      if (info.unmeasurable) unmeasurable.push(`${info.id}  [${label}]`);
      const kind = info.pixelProbe ? "range 像素差分"
        : info.unmeasurable ? "不可测（blur 即卸载）"
        : info.judgedBy ? (info.judgedBy.startsWith("容器代持") ? "容器代持" : info.judgedBy)
        : "本体环";
      bump(kind);
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

// —— BD-206：阅读器与向导弹层。开书失败的面逐个记跳过、不拖垮整网。——
const tryScope = async (label, prep, opts) => {
  try {
    await prep();
    await sweep(label, opts);
  } catch (e) {
    console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 120)}`);
  }
};

// —— BD-241：把「撤了焦点环、但这张网从没走到」的面补进走查 ——
// 摸底账（/tmp/bd241-probe.log、-probe2.log、-probe3.log、-probe4.log，四趟独立实测）：
// 全仓 16 处 `outline: none|0` 声明里 9 处本体无环，其中真被本网判过的只有 4 处，其余 5 面
// 各因一个可达性条件没走进来——关系图要切视图 tab、卡片库是另一屏、设置只走了默认分区、
// 速查面板的搜索输入只在「全局搜索」tab 下挂载、大纲行内改名态要键盘 Enter 才出现且
// blur 即卸载。补进来之后的实测去向：
//   · 关系图搜索框 → 容器代持（span.relation-graph-search 聚焦时才有 3px 环 + 边框换色，BD-126 的形状）
//   · 卡片库屏 / 设置各分区 / 大纲改名态 → 本体实线环（全局 `input:focus-visible` 在特异性或顺序上占得上风）
//   · 速查面板搜索输入 → **改前红**（同分块的 `outline: 0` 与全局环平手、加载顺序压过全局），
//     BD-241 已在 writing-quick-reference.css 里把环自己画回来，现判本体环。
await tryScope("项目·关系图", async () => {
  await workbenchView("设定卡", ".cards-page");
  await page.locator(".cards-page button", { hasText: "关系图" }).first().click();
  await page.waitForSelector(".relation-graph", { timeout: 15000 });
  await page.waitForTimeout(700);
}, { maxStops: 80 });

for (const cat of ["阅读器", "AI 助手", "数据与存储", "关于 / 调试"]) {
  await tryScope(`设置·${cat}`, async () => {
    await goto("设置");
    await page.waitForSelector(".settings-nav", { timeout: 10000 });
    await page.locator(".settings-nav button", { hasText: cat }).first().click();
    await page.waitForSelector(".settings-forms", { timeout: 10000 });
    await page.waitForTimeout(500);
  }, { maxStops: 90 });
}

await tryScope("弹层·写作速查·全局搜索", async () => {
  await workbenchView("写作", ".writing-desk");
  await page.keyboard.press("Control+Shift+k");
  await page.waitForTimeout(600);
  await page.locator(".writing-quick-tabs button", { hasText: "全局搜索" }).first().click();
  await page.waitForSelector(".writing-quick-search input", { timeout: 10000 });
  await page.waitForTimeout(400);
}, { maxStops: 140 });
await page.keyboard.press("Control+Shift+k").catch(() => {});
await page.waitForTimeout(300);

await tryScope("卡片库", async () => {
  await goto("卡片库");
  await page.waitForSelector(".global-card-library", { timeout: 15000 });
  await page.waitForTimeout(500);
});

// 大纲行内改名态：必须**键盘**激活——鼠标点「改名」后 autoFocus 进来的输入框不满足
// :focus-visible 的键盘模态（会假红），而且它 onBlur 就提交卸载、进不了后续 Tab 序列，
// 所以用 judgeCurrentFirst 先判当前焦点元素再开始走。
await tryScope("项目·大纲行内改名态", async () => {
  await workbenchView("大纲", ".project-workbench");
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.waitForTimeout(150);
  for (let i = 0; i < 200; i += 1) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(40);
    if (await page.evaluate(() => !!document.activeElement?.matches?.('button[aria-label^="改名"]'))) break;
  }
  await page.keyboard.press("Enter");
  await page.waitForTimeout(400);
  if (!(await page.locator(".outline-inline-edit input").count())) throw new Error("改名态未出现");
}, { resetFocus: false, judgeCurrentFirst: true, maxStops: 8 });

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
}, { maxStops: 70 });
await page.keyboard.press("Escape").catch(() => {}); // BD-203 族：抽屉 Escape 先关自己
await page.waitForTimeout(350);
await tryScope("阅读器·TXT目录面板", async () => {
  // 目录面板默认就是展开的——只在收起态才点「展开目录」，别把开着的关掉。
  const expand = page.locator('button[aria-label="展开目录"]');
  if (await expand.count()) await expand.first().click();
  await page.waitForTimeout(500);
}, { maxStops: 80 });
await page.locator('button[aria-label="收起目录"]').first().click().catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·TXT搜索浮层", async () => {
  await page.keyboard.press("Control+f");
  await page.waitForTimeout(500);
}, { maxStops: 40 });
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

await tryScope("阅读器·EPUB", async () => {
  await page.locator('button[aria-label="返回书库"]').first().click();
  await page.waitForSelector('.desktop-library-row[data-format="epub"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="epub"]').first().click();
  await page.waitForSelector(".paper-topbar", { timeout: 30000 });
  await page.waitForTimeout(1800); // epub.js 渲染正文
});
await tryScope("弹层·EPUB设置抽屉", async () => {
  await page.locator(".paper-topbar button", { hasText: "设置" }).first().click();
  await page.waitForTimeout(500);
}, { maxStops: 70 });
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

console.log(`扫描停靠点 ${seenAll.size} 个（按范围×元素去重）。判据去向：${[...tally.entries()].map(([k, v]) => `${k} ${v}`).join("、")}。`);
if (unmeasurable.length) {
  console.log(`不可测（blur 即卸载、拿不到离焦基线）${unmeasurable.length} 个，逐个点名不静默：`);
  for (const u of unmeasurable) console.log(`  · ${u}`);
}
if (reds.size > 0) {
  console.log(`FOCUS_RING_RED：${reds.size} 个键盘停靠点无任何可见指示：`);
  for (const r of reds.values()) {
    console.log(`  - ${r.id}  [${r.where.join("、")}]  outline=${r.outline} shadow=${r.boxShadow}${r.pixelProbe ? ` ${r.pixelProbe}` : ""}${r.judgedBy ? ` ${r.judgedBy}` : ""}`);
  }
  await app.close();
  process.exit(1);
}
console.log(`FOCUS_RING_OK：${seenAll.size} 个停靠点全部有可见指示。`);
await app.close();
