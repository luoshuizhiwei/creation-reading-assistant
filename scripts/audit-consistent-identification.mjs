#!/usr/bin/env node
/**
 * BD-226 常驻普查：WCAG 3.2.4「一致地识别」——同一功能是否始终用同一份图案，同一图案是否始终表示同一功能。
 *
 * 为什么值得常驻：3.2.4 对用户的意思是「学到一次就够用」。同一个向下尖括号在大纲树里既表示
 * 「下移这一卷」又表示「收起这一卷」，用户学到 ▼=收起 之后照样会点错；「上移」这颗在大纲树是
 * 旋转 270° 的右尖括号、在场景卡板是上尖括号、在卡片类型编辑器是上箭头——换一屏就得重新学一遍。
 * 这类漂移肉眼回归看不出来，源码 grep 也抓不住（源码里是组件名，用户看到的是渲染出来的几何＋
 * CSS 朝向），所以必须在运行时按「美术签名」聚合。
 *
 * 方法（口径写死在这里，改口径就是改判据）：
 *   1) 逐屏采集所有「有可及名的可交互件」，对件内每个 <svg> 求美术签名＝
 *      子元素全部几何属性（path d / polyline points / line 端点 / circle 半径…，去掉 class/style）
 *      ＋该 svg 自身的 CSS 旋转角。两条缺一不可，都留了实测反例：
 *        · 只取 d 会把 lucide 的 Download 与 Upload 并成一张图（两者只有 polyline/line 不同），
 *          普查一度报出「同一个图标既叫导入又叫导出」的假红（见 /tmp/sc324-survey.log A 轴）。
 *        · 不记朝向会把「旋转 270° 画成向上」的右尖括号与「下一步」的右尖括号并成一张图，
 *          报出「同一个图标既叫上移又叫下一步」的假红（见 /tmp/sc324-survey2.log）。
 *      尺寸（12/13/16）不进签名：用户看到的是图案本身，不是它被画成几像素。
 *   2) 可及名归到动词族（表见 VERBS，取名字里最后命中的那个词），归不出动词的名字
 *      （「润色场景」「待处理收件箱」之类）不参与判红，只记档——那些不归这条 SC 管。
 *   3) 三条判红方向：①一张图案表示多种功能；②一种功能用了多种图案（仅对登记为「单一图案」的族判，
 *      纯文字钮「（无图标）」不计入图案数）；③登记了名称基准的图案，挂出的可及名必须以基准开头
 *      （「导入项目包」这个动作不许在另一屏叫成「导入包」）。
 *      已审的图案与功能族逐条写在 DECLARED_SIGS / DECLARED_FAMILY 里并带理由；
 *      出现没登记的图案或功能族同样判红——新增图标要先看它跟谁撞车，再写理由放行。
 *   4) 自证针走真判据：在「大纲」那一屏（同时有下移与收起两颗供体）注入
 *      「真下箭头美术＋名字以收起开头」与「真下尖括号美术＋名字以下移开头」两枚假件，
 *      喂进正式统计表的副本、跑同一个 findings()，必须分别被 ①② 抓到；抓不到即自证失败 exit=1。
 *      针不许污染正式表，也不许为针另写判定条件。
 *      针体跑完才置 selfTest.ran，判红先看这个标记——否则把针体整段短路（return 在函数头）
 *      也能全绿：这条是牙齿自检 T-C 实测出来的（旧版把 runNeedles 首行改成 return，普查照样 IDENT_OK）。
 *
 * 基线（修之前实测，脚本跑在还原后的旧源码上，/tmp/bd226-baseline.log）：
 *   ①图案 9b709a4c（向下尖括号）同时表示 收起（8 件）与 下移（13 件）；
 *   ②「上移」两种图案：21b0db03×12（大纲树，旋转右尖括号）＋ 20cd08a2×1（场景卡板，上尖括号）；
 *   ③图案 c354905c（FolderInput）挂出两个名字：「导入包」×9 与「导入项目包」×2
 *     （逐名计数在 /tmp/sc324-survey3.log:37，那张 dump 里这条叫法对照是唯一出处）。
 * 最终态：IDENT_OK——重排一律上下箭头（ArrowUp/ArrowDown，本仓卡片类型编辑器本来就这么画），
 *   尖括号只表示展开/收起；导入项目包在项目·首页与工作台头部同名。
 */
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { _electron as electron } from "playwright-core";

const root = "/data/creation-reading-assistant";
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-ident-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/* ---------- 判据表（改这里等于改判据，台账会当场红） ---------- */

// 动词族：只收「同一动作会在多屏反复出现」的词；取名字里最后命中的那个词
// （「打开全局搜索：搜索项目…」因此归到搜索而不是打开）。
const VERBS = ["上移", "下移", "收起", "展开", "关闭", "导入", "导出", "新建", "改名", "删除", "移除", "移出", "搜索", "查找", "同步", "撤销", "重做", "打开", "载入"];

// 功能族 → 允许的图案。one:true＝这一族全应用只许一张图案（同一种动作不许换图）；
// one:false＝这一族按对象不同本来就换图（导入项目包/导入旧稿/导入卡片是不同动作），只拦未登记的新图案。
// 「（无图标）」表示该族里存在纯文字钮，它不是一张图案。
const DECLARED_FAMILY = {
  "上移": { one: true, sigs: ["947e078f"], why: "重排＝上下箭头，全应用一套；同一种动作换屏不许换图（BD-226）" },
  "下移": { one: true, sigs: ["8aabcc39"], why: "同上；旧版下移与收起共用下尖括号，是基线红①" },
  "收起": { one: true, sigs: ["9b709a4c", "（无图标）"], why: "尖括号只管展开/收起；「收起检查器」是纯文字钮" },
  "展开": { one: true, sigs: ["a4a8bf05", "（无图标）"], why: "收起的反向态（走查会点卷头把它逼出来，哈希为实测值）" },
  "撤销": { one: true, sigs: ["07995fa6"], why: "撤销在概览/写作/速查三处必须同一张图" },
  "重做": { one: true, sigs: ["6e0d6cef"], why: "同上" },
  "同步": { one: true, sigs: ["26cc2f51"], why: "标题栏同步设置钉住防换图" },
  "查找": { one: true, sigs: ["e8e310f0"], why: "查找替换在 9 屏同一张图" },
  "改名": { one: true, sigs: ["cbf492fa"], why: "卷/章/场景三处改名同一支铅笔" },
  "新建": { one: true, sigs: ["18db90aa"], why: "所有「新建 X」共用一个加号，只是对象不同" },
  "关闭": { one: false, sigs: ["97fc5599", "e6c5bbba"], why: "叉＝关掉浮层/窗口；写作速查那颗画的是面板开关（PanelRightClose），语义是收起侧栏，保留" },
  "删除": { one: false, sigs: ["2f9d4f59"], why: "垃圾桶；与移出/移除同图，见 DECLARED_SIGS" },
  "移出": { one: false, sigs: ["2f9d4f59"], why: "收件箱「移出」＝把这条从列表去掉，与删除同一动作族" },
  "移除": { one: false, sigs: ["2f9d4f59"], why: "书库「移除书籍」＝从书架去掉，不删文件" },
  "导入": { one: false, sigs: ["c354905c", "9adcfd7f", "d5881d55", "badc0e11", "d9e069ef"], why: "导入的对象不同（项目包/旧稿/加密备份/资料/卡片），图案按对象区分是有意为之" },
  "导出": { one: false, sigs: ["465d96d7", "05ab11da", "eaaf98aa", "19fa2527"], why: "同上：成稿/项目包/加密备份/PDF 各一张" },
  "搜索": { one: false, sigs: ["18f10943", "（无图标）"], why: "放大镜；全局搜索命令与工作区「搜索项目」是不同入口" },
  "打开": { one: false, sigs: ["9e96a3ab", "2c49e32e", "（无图标）"], why: "打开数据目录（文件夹）／打开某本书（书）／纯文字项" },
  "载入": { one: false, sigs: ["2c49e32e"], why: "载入演示项目与「打开某本书」同为一本书进来看，共用书本图" },
};

// 图案 → 允许表示的功能族；可选 namePrefix＝这张图挂出的可及名必须以它开头（判据③的名称基准）。
const DECLARED_SIGS = {
  "947e078f": { icon: "ArrowUp 上箭头", verbs: ["上移"], why: "重排专用（BD-226 统一）" },
  "8aabcc39": { icon: "ArrowDown 下箭头", verbs: ["下移"], why: "重排专用（BD-226 统一）" },
  "9b709a4c": { icon: "ChevronDown 下尖括号", verbs: ["收起", "展开"], why: "展开/收起一族：收起画▼、展开画▶，两态同族，不与重排共用" },
  "a4a8bf05": { icon: "ChevronRight 右尖括号", verbs: ["展开"], why: "折叠态的树/目录" },
  "2f9d4f59": { icon: "Trash2 垃圾桶", verbs: ["删除", "移出", "移除"], why: "都是「去掉这一条」，只是对象域不同（大纲/收件箱/书架）" },
  "c354905c": { icon: "FolderInput 文件夹进箭头", verbs: ["导入"], namePrefix: "导入项目包", why: "③名称基准：同一个导入项目包动作在项目·首页与工作台头部必须同名（基线红③）" },
  "97fc5599": { icon: "X 叉", verbs: ["关闭"], why: "关浮层/窗口一族" },
  "e6c5bbba": { icon: "PanelRightClose 面板开关", verbs: ["关闭"], why: "写作速查抽屉的收起钮，画的是面板不是叉" },
  "18db90aa": { icon: "Plus 加号", verbs: ["新建"], why: "新建一族" },
  "cbf492fa": { icon: "Pencil 铅笔", verbs: ["改名"], why: "改名一族" },
  "18f10943": { icon: "Search 放大镜", verbs: ["搜索"], why: "搜索一族" },
  "e8e310f0": { icon: "查找替换", verbs: ["查找"], why: "只此一处功能" },
  "07995fa6": { icon: "Undo2 撤销", verbs: ["撤销"], why: "只此一功能" },
  "6e0d6cef": { icon: "Redo2 重做", verbs: ["重做"], why: "只此一功能" },
  "26cc2f51": { icon: "同步", verbs: ["同步"], why: "只此一功能" },
  "9e96a3ab": { icon: "FolderOpen 打开文件夹", verbs: ["打开"], why: "打开数据目录" },
  "2c49e32e": { icon: "BookOpen 书", verbs: ["打开", "载入"], why: "打开一本书与载入演示项目同为「让这本书进来」" },
  "465d96d7": { icon: "Download 下箭头托盘", verbs: ["导出"], why: "导出一族" },
  "05ab11da": { icon: "FolderOutput 文件夹出箭头", verbs: ["导出"], why: "导出项目包" },
  "eaaf98aa": { icon: "加密导出", verbs: ["导出"], why: "加密备份导出" },
  "19fa2527": { icon: "Printer 打印", verbs: ["导出"], why: "导出打印版 PDF" },
  "9adcfd7f": { icon: "FileUp 文件进箭头", verbs: ["导入"], why: "导入旧稿" },
  "d5881d55": { icon: "Unlock 开锁", verbs: ["导入"], why: "加密导入要先解锁" },
  "badc0e11": { icon: "导入资料", verbs: ["导入"], why: "书库按格式导入（TXT/Markdown、EPUB）" },
  "d9e069ef": { icon: "Upload 上箭头托盘", verbs: ["导入"], why: "卡片 CSV/Markdown 导入" },
};

/* ---------- 运行时 ---------- */

const launch = async () => {
  const app = await electron.launch({
    executablePath: electronPath, args: [mainJs, "--disable-dev-shm-usage"], cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir },
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

const judged = [];
const notes = [];
const ICONS = {};
/* 自证针的落点必须能被外部查证「真的跑完了」：上一版把针体短路掉（return 在函数头），
   三条失败 push 都在针体内，于是判据全绿——自证这一腿自己就是可以静默跳过的。
   ran 这个标记由针体最后一行置位，判红时先看它在不在。 */
const selfTest = { ran: false, reds: [], caughtA: false, caughtB: false };

/* 判定：纯函数，正式表与针的副本表都走这一个 findings() */
const findings = (table) => {
  const reds = [];
  const sigEntries = Object.entries(table).filter(([k]) => k.startsWith("sig:"));
  const verbEntries = Object.entries(table).filter(([k]) => k.startsWith("verb:"));
  for (const [k, v] of verbEntries) {
    const verb = k.slice(5);
    const declared = DECLARED_FAMILY[verb];
    const arts = [...v.sigs.entries()].sort((a, b) => b[1] - a[1]);
    if (!declared) {
      reds.push(`②未登记的功能族「${verb}」：${arts.length} 种图案（${arts.map(([h, n]) => `${h}×${n}`).join("、").slice(0, 120)}）——新出现的动作族要先审过写进 DECLARED_FAMILY 再放行。`);
      continue;
    }
    // 「（无图标）」是纯文字钮，不是一张额外的图案，否则「收起检查器」这类同族无图标件会被误判
    const pics = arts.filter(([h]) => h !== "（无图标）");
    const unknown = arts.filter(([h]) => !declared.sigs.includes(h)).map(([h, n]) => `${h}×${n}`);
    if (declared.one && pics.length > 1) {
      reds.push(`②一功多图：「${verb}」这一种动作用了 ${pics.length} 种图案 —— ${pics.map(([h, n]) => `${h}×${n}`).join("、")}（理由：${declared.why}）`);
    } else if (unknown.length) {
      reds.push(`②冒出没登记的图案：「${verb}」出现 ${unknown.join("、")}（允许：${declared.sigs.join(" / ")}；理由：${declared.why}）`);
    }
    const seen = new Set(arts.map(([h]) => h));
    const missing = declared.sigs.filter((h) => !seen.has(h));
    if (missing.length) notes.push(`功能「${verb}」登记的图案本次没出现：${missing.join(", ")}（宿主屏/状态不同，不判红）`);
  }
  for (const [k, v] of sigEntries) {
    const h = k.slice(4);
    const verbs = [...v.verbs.keys()];
    const entry = DECLARED_SIGS[h];
    if (!entry) {
      const multi = verbs.length > 1 ? "（一图多功能，且未登记）" : "";
      reds.push(`①未登记的图案：${h} 表示「${verbs.join("」「")}」${multi} —— 叫法「${[...v.names.keys()].slice(0, 4).join("、")}」。新增图标要先看它跟谁撞车，再把签名和理由写进 DECLARED_SIGS。`);
      continue;
    }
    const extra = verbs.filter((x) => !entry.verbs.includes(x));
    if (extra.length) {
      const who = [...new Set([...[...v.names.keys()].filter((n) => n.includes("Needle")), ...v.names.keys()])].slice(0, 5).join("、");
      reds.push(`①一图多功能：图案 ${h}（${entry.icon}）除登记的 ${entry.verbs.join("/")} 外还表示了 ${extra.join("/")} —— 叫法「${who}」`);
    } else if (verbs.length > 1) {
      notes.push(`图案 ${h}（${entry.icon}）按登记同时表示 ${verbs.join("/")}，理由：${entry.why}`);
    }
    if (entry.namePrefix) {
      const off = [...v.names.keys()].filter((n) => !n.startsWith(entry.namePrefix));
      if (off.length) reds.push(`③名称不一致：图案 ${h}（${entry.icon}）挂出的可及名「${off.join("」「")}」不以基准「${entry.namePrefix}」开头——同一个动作在别的屏换了名字`);
    }
  }
  return reds;
};

const main = async () => {
  { const first = await launch(); await first.app.close(); }
  const seed = spawnSync(electronPath, [path.join(root, "scripts", "round4-seed.cjs"), profileDir], {
    cwd: root, encoding: "utf8",
    env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
    timeout: 60000 });
  if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

  const { app, page } = await launch();
  await app.evaluate(({ BrowserWindow }) => {
    BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: 1360, height: 900 });
  });
  await page.waitForTimeout(600);

  /* 采集：件名 + 件内每张图标的「美术＋朝向」签名 */
  const collect = () => page.evaluate((verbList) => {
    const sel = "button, a, [role=button], [role=link], summary, [role=tab], [role=checkbox], [role=switch]";
    const out = [];
    const accName = (el) => {
      const lab = el.getAttribute && el.getAttribute("aria-label");
      if (lab && lab.trim()) return lab.trim();
      const txt = (el.textContent || "").replace(/\s+/g, " ").trim();
      if (txt) return txt;
      const ttl = el.getAttribute && el.getAttribute("title");
      return ttl && ttl.trim() ? ttl.trim() : "";
    };
    const sigOf = (svg) => {
      const parts = [];
      for (const n of svg.querySelectorAll("*")) {
        const at = [...n.attributes].filter((a) => a.name !== "class" && a.name !== "style")
          .sort((a, b) => a.name.localeCompare(b.name)).map((a) => a.name + "=" + a.value).join(",");
        parts.push(n.tagName + ":" + at);
      }
      let rot = 0;
      const tf = getComputedStyle(svg).transform;
      const m = tf && tf.match(/matrix\(([^)]+)\)/);
      if (m) {
        const [a, b] = m[1].split(",").map(Number);
        rot = Math.round(Math.atan2(b, a) * 180 / Math.PI);
        rot = ((rot % 360) + 360) % 360;
      }
      return (parts.length ? parts.join(";") : "empty") + "@rot" + rot;
    };
    for (const el of document.querySelectorAll(sel)) {
      const r = el.getBoundingClientRect();
      const st = getComputedStyle(el);
      if (!r.width || !r.height || st.visibility === "hidden" || st.display === "none") continue;
      if (r.bottom < 0 || r.top > innerHeight + 4) continue;
      const name = accName(el);
      const svgs = [...el.querySelectorAll("svg")].slice(0, 4);
      let verb = "";
      let verbAt = -1;
      for (const v of verbList) {
        const i = name.lastIndexOf(v);
        if (i >= 0 && i >= verbAt) { verbAt = i; verb = v; }
      }
      out.push({ name: name.slice(0, 60), verb, sigs: svgs.map(sigOf), hidden: el.getAttribute && el.getAttribute("aria-hidden") === "true" });
    }
    return out;
  }, VERBS);

  const judge = (rows, screen, table) => {
    judged.push(screen);
    for (const r of rows) {
      if (r.hidden || !r.name) continue;
      if (!r.verb) { if (r.sigs.length) notes.push(`${screen}：「${r.name}」归不出动词族，不判（图标 ${r.sigs.length} 张）`); continue; }
      for (const sg of r.sigs) {
        const h = createHash("sha1").update(sg).digest("hex").slice(0, 8);
        const A = table["sig:" + h] || (table["sig:" + h] = { verbs: new Map(), names: new Map() });
        A.verbs.set(r.verb, (A.verbs.get(r.verb) || 0) + 1);
        if (!A.names.has(r.name)) A.names.set(r.name, new Set());
        A.names.get(r.name).add(screen);
        const B = table["verb:" + r.verb] || (table["verb:" + r.verb] = { sigs: new Map() });
        B.sigs.set(h, (B.sigs.get(h) || 0) + 1);
      }
      if (!r.sigs.length) {
        const B = table["verb:" + r.verb] || (table["verb:" + r.verb] = { sigs: new Map() });
        B.sigs.set("（无图标）", (B.sigs.get("（无图标）") || 0) + 1);
      }
    }
  };

  const measure = async (screen, table) => {
    const rows = await collect();
    judge(rows, screen, table || ICONS);
    return rows.length;
  };

  /* 自证针：下在「大纲」这一屏——那里同时有「下移」与「收起」两颗供体。
     若把针放到走查末尾，那时人已经在书库，供体不存在，两条判据都不会被检验（第一版就栽过）。 */
  const runNeedles = async () => {
    await page.evaluate(() => {
      const mk = (label, donorSel) => {
        const donor = document.querySelector(donorSel);
        const b = document.createElement("button");
        b.type = "button";
        b.setAttribute("aria-label", label);
        b.style.cssText = "position:fixed;left:8px;top:8px;width:40px;height:40px;z-index:99999";
        if (donor) b.appendChild(donor.cloneNode(true));
        document.body.appendChild(b);
        return b;
      };
      // 针 A：借真·下移那颗的箭头美术，名字起成「收起针…」→ 该被 ①（一图多功能）抓到
      // 针 B：借真·展开收起那颗的尖括号美术，名字起成「下移针…」→ 该被 ②（一功多图）抓到
      window.__bd226Needles = [
        mk("收起针·BD226 Needle", "button[aria-label^='下移'] svg"),
        mk("下移针·BD226 Needle", "[aria-expanded='true'].outline-volume-toggle svg"),
      ];
      window.__bd226NeedleMissing = window.__bd226Needles.filter((b) => !b.querySelector("svg")).map((b) => b.getAttribute("aria-label"));
    });
    await page.waitForTimeout(300);
    const missingDonors = await page.evaluate(() => window.__bd226NeedleMissing || []);
    const needleRows = await collect();
    await page.evaluate(() => { (window.__bd226Needles || []).forEach((n) => n.remove()); window.__bd226Needles = []; });
    const needleTable = structuredClone(ICONS);
    const judgedBefore = judged.length;
    judge(needleRows.filter((r) => r.name.includes("Needle")), "自证·注入针", needleTable);
    judged.length = judgedBefore; // 针那一屏不算进正式覆盖面
    const needleReds = findings(needleTable);
    selfTest.caughtA = needleReds.some((r) => r.includes("收起针"));
    selfTest.caughtB = needleReds.some((r) => r.includes("下移针") || r.includes("②一功多图：「下移」"));
    if (missingDonors.length) selfTest.reds.push(`自证失败：针没借到美术（${missingDonors.join("、")}）——供体不在当前屏，两条判据都没被检验。`);
    if (!selfTest.caughtA) selfTest.reds.push("自证失败：注入的「下箭头美术＋收起开头名字」没被 ①抓到——「一图多功能」这条判据是空的。");
    if (!selfTest.caughtB) selfTest.reds.push("自证失败：注入的「尖括号美术＋下移开头名字」没被 ②抓到——「一功多图」这条判据是空的。");
    selfTest.ran = true;
    console.log(`自证：注入两针 ①抓到=${selfTest.caughtA ? "是" : "否"}、②抓到=${selfTest.caughtB ? "是" : "否"}（供体缺 ${missingDonors.length} 颗）`);
  };

  /* ---- 走查：与 1.4.12 / 3.1.2 同一批屏，另加「场景卡板」与「折叠态」——
     卡板是重排钮的第二处宿主，折叠态才有右尖括号；不点进去这两条判据就是瞎的。 ---- */
  const side = async (name) => {
    await page.locator(".desktop-sidebar button", { hasText: name }).first().click();
    await page.waitForTimeout(800);
    await measure(name);
  };

  await side("项目");
  await page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(600);
  await measure("弹层·新建向导");
  await page.locator("input[placeholder='例如：测试项目']").first().fill("一致识别普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(2400);
  await measure("工作台·概览");

  for (const v of ["写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
    const btn = page.locator(".project-nav button", { hasText: v }).first();
    if (await btn.isVisible().catch(() => false)) { await btn.click(); await page.waitForTimeout(900); await measure(`工作台·${v}`); }
  }

  // 场景卡板：这组切换是 role="group" + 每颗 aria-pressed（Tabs.tsx：没有 tabpanel 就不冒充 tablist），
  // 按 group 名取；找错会静默跳过这一屏，判据对卡板就瞎了。
  const viewGroup = page.getByRole("group", { name: "大纲视图切换" });
  const backToOutline = page.locator(".project-nav button", { hasText: "大纲" }).first();
  if (await backToOutline.isVisible().catch(() => false)) { await backToOutline.click(); await page.waitForTimeout(900); }
  const boardTab = viewGroup.getByRole("button", { name: "场景卡板" }).first();
  if (await boardTab.isVisible().catch(() => false)) {
    await boardTab.click();
    await page.waitForTimeout(1000);
    await measure("工作台·大纲·场景卡板");
    const treeTab = viewGroup.getByRole("button", { name: "大纲树" }).first();
    if (await treeTab.isVisible().catch(() => false)) { await treeTab.click(); await page.waitForTimeout(700); }
    // 折叠态：展开态看不到「收起的反向那颗」（右尖括号），点一下卷头把它逼出来，哈希才有实测值
    const volToggle = page.locator(".outline-volume-toggle").first();
    if (await volToggle.isVisible().catch(() => false)) {
      await volToggle.click();
      await page.waitForTimeout(800);
      await measure("工作台·大纲·折叠态");
      await volToggle.click();
      await page.waitForTimeout(600);
    } else {
      notes.push("没点到卷头——「展开」态的图案本次采不到，该族只能靠登记值对账。");
    }
  } else {
    notes.push("没点到「场景卡板」——重排钮的第二处宿主没被扫到。");
  }

  // 无论上面几步顺不顺，针都要落回大纲树这一屏再下
  await backToOutline.click().catch(() => {});
  await page.waitForTimeout(800);
  await runNeedles();

  await page.locator(".project-nav button", { hasText: "写作" }).first().click();
  await page.waitForTimeout(800);
  await page.keyboard.press("Control+Shift+K");
  await page.waitForTimeout(900);
  await measure("抽屉·写作速查");
  await page.keyboard.press("Control+Shift+K");
  await page.waitForTimeout(600);

  await side("收件箱");
  await side("卡片库");
  await side("世界观");
  await side("设置");
  await side("书库");

  if (process.env.BD226_DUMP === "1") {
    console.log("=== BD226_DUMP：功能族 → 图案（美术＋朝向签名） ===");
    for (const [k, v] of Object.entries(ICONS).filter(([x]) => x.startsWith("verb:"))) {
      const arts = [...v.sigs.entries()].sort((a, b) => b[1] - a[1]);
      console.log(`${k.slice(5)}: ` + arts.map(([h, n]) => `"${h}": ${n}`).join(", "));
    }
    console.log("\n=== BD226_DUMP：图案 → 功能族与叫法 ===");
    for (const [k, v] of Object.entries(ICONS).filter(([x]) => x.startsWith("sig:"))) {
      console.log(`${k.slice(4)} → ${[...v.verbs.keys()].join(" + ")} ｜ ${[...v.names.keys()].slice(0, 4).join("、")}`);
    }
    await app.close();
    console.log(`BD226_DUMP 完成：判定 ${judged.length} 屏（未做判红，仅导出现场）。`);
    return -1;
  }

  /* ---- 正式判定 ---- */
  const reds = findings(ICONS);

  // 覆盖下限：走查屏数与关键功能族实例数，防止某一步静默失败把判据掏空
  const SCREEN_MIN = 16;
  const FLOOR = { "上移": 6, "下移": 6, "收起": 4, "关闭": 8 };
  if (judged.length < SCREEN_MIN) reds.push(`走查只剩 ${judged.length} 屏（下限 ${SCREEN_MIN}）——前面某几步没跑到，覆盖面不足。`);
  for (const [vb, min] of Object.entries(FLOOR)) {
    const got = ICONS["verb:" + vb] ? [...ICONS["verb:" + vb].sigs.values()].reduce((s, x) => s + x, 0) : 0;
    if (got < min) reds.push(`功能族「${vb}」只采到 ${got} 件（下限 ${min}）——这一族的判定几乎没被检验。`);
  }
  // 自证这一腿自己也要被验尸：针体没跑完（提前 return、异常被吞、供体那屏没到）
  // 时三条 push 全都不会发生，光看 notes 是绿的——所以基准是 ran 标记，不是失败条数。
  if (!selfTest.ran) selfTest.reds.push("自证失败：runNeedles() 没跑到底——①②两条判据本次根本没被检验，判绿不可信。");
  reds.push(...selfTest.reds);

  for (const n of notes.slice(0, 24)) console.log("  记档 " + n);
  if (notes.length > 24) console.log(`  记档 …另有 ${notes.length - 24} 条`);
  const sigCount = Object.keys(ICONS).filter((k) => k.startsWith("sig:")).length;
  const famCount = Object.keys(ICONS).filter((k) => k.startsWith("verb:")).length;
  console.log(`一致识别扫描完成：判定 ${judged.length} 屏，功能族 ${famCount} 个，图案 ${sigCount} 张。`);
  if (reds.length) {
    console.log(`判红 ${reds.length} 项：`);
    for (const r of reds) console.log("  ✗ " + r);
  }
  await app.close();
  return reds.length ? 1 : 0;
};

let exitCode = 0;
try {
  const code = await main();
  if (code === -1) exitCode = 0;
  else if (code === 0) {
    console.log("IDENT_OK：重排一律上下箭头、尖括号只管展开/收起，同一功能在全程走查里始终是同一张图案；导入项目包同名。");
    exitCode = 0;
  } else { console.log("IDENT_FAIL：见上面判红项。"); exitCode = 1; }
} catch (error) {
  console.error(error);
  exitCode = 1;
}
process.exit(exitCode);
