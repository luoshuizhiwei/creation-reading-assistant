// @vitest-environment node
/**
 * BD-253 源码账：行内状态消息可播报普查（WCAG 4.1.3 非错误状态维度）的口径与那一处 DOM 修法。
 *
 * 这本账读文件，不读 DOM——「宿主是不是变化前就在」「有没有被 aria-hidden 藏着」那半边
 * 由 scripts/audit-status-messages.mjs 在真 app 里量。两边各钉一件事：
 *   · 判据的命门（有 live 宿主 / 宿主先在 / 没被藏起来 / 画得出才算测 / 变化要真发生 /
 *     一次哑就算红 / 不许失明）每一条都是实测或历史批次踩出来的，谁简化回去本账当场红。
 *   · 两处**刻意静音**的豁免形态（dock 保存灯复述编辑器行内那条、专注 HUD 逐键刷新）
 *     不许被「顺手补个 aria-live」修掉——那正是 BD-233/BD-188 拦下的刷屏。
 *   · 收件箱那盏灯的修法只许是就地 aria-live="polite"，不许改成条件挂载、不许塞进
 *     aria-hidden 包裹、也不许另加一个常驻区把同一句话说两遍。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-status-messages.mjs");
const pkg = at("package.json");
const inboxDetail = at("src/features/creation/inbox/components/InboxItemDetail.tsx");
const inboxPage = at("src/features/creation/inbox/InboxPage.tsx");
const sceneEditor = at("src/features/creation/editor/SceneEditor.tsx");
const dock = at("src/components/layout/DesktopDock.tsx");
const desk = at("src/features/creation/editor/WritingDesk.tsx");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

const siteBlock = () => census.slice(census.indexOf("const SITES = ["), census.indexOf("const ENTER = {"));

describe("BD-253 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:status-messages": "node scripts/audit-status-messages.mjs"');
  });

  it("站点 6 个：该播报 4 ＋ 刻意静音负对照 2，标签逐字钉住", () => {
    const labels = [...siteBlock().matchAll(/label: "([^"]+)"/g)].map((m) => m[1]);
    expect(labels).toEqual([
      "收件箱·保存徽标",
      "写作·场景保存徽标",
      "速查面板·保存徽标",
      "底栏·缩放百分比",
      "底栏·正文保存灯（刻意静音·复述编辑器行内那条）",
      "专注 HUD 字数（刻意静音·逐键刷新）"
    ]);
    const kinds = [...siteBlock().matchAll(/kind: "([^"]+)"/g)].map((m) => m[1]);
    expect(kinds.filter((k) => k === "announce").length).toBe(4);
    expect(kinds.filter((k) => k === "mute").length).toBe(2);
  });

  it("只有 dock 保存灯声明 twinSel（静音的理由必须指着一条真读数）", () => {
    expect([...siteBlock().matchAll(/twinSel: "([^"]+)"/g)].map((m) => m[1])).toEqual([".scene-save-status"]);
    expect(siteBlock()).toContain('sel: ".desktop-dock-status"');
    expect(siteBlock()).toContain('sel: ".writing-focus-status"');
  });

  it("判据①：role=status / role=alert / aria-live≠off 才算 live 宿主，显式 off 跳过但继续往上找", () => {
    const code = codeOf(census);
    expect(code).toContain('if (live === "off") continue;');
    expect(code).toContain('if (role === "status" || role === "alert" || (live && live !== "off")) {');
    expect(code).toContain("for (let node = el; node; node = node.parentElement) {");
    expect(code).toContain('return { host: null, pre: false, hidden: false };');
  });

  it("判据②：先给所有 live 区打「先在」标记，再装监听器（顺序反了这条就恒真）", () => {
    const code = codeOf(census);
    expect(code).toContain('el.setAttribute("data-bd253-pre", "1");');
    expect(code).toContain('pre: node.getAttribute("data-bd253-pre") === "1",');
    const body = code.slice(code.indexOf("const measureSite ="), code.indexOf("/* ---------- 判定表"));
    expect(body.indexOf("await markLive()")).toBeGreaterThan(-1);
    expect(body.indexOf("await markLive()")).toBeLessThan(body.indexOf("await installWatcher("));
  });

  it("判据③：宿主落在 aria-hidden 子树里不算能播报", () => {
    expect(codeOf(census)).toContain('hidden: node.closest(\'[aria-hidden="true"]\') !== null');
    expect(codeOf(census)).toContain("return Boolean(row.host) && row.pre === true && row.hidden !== true;");
  });

  it("判据④：祖先链 opacity 乘出来的绘制不透明度 <0.99 记 invisible，不算绿", () => {
    const code = codeOf(census);
    expect(code).toContain("painted *= Number(s.opacity);");
    expect(code).toContain('if (s.display === "none" || s.visibility === "hidden") painted = 0;');
    expect(code).toContain('if (obs.painted < 0.99) return { level: "invisible"');
    // 淡入过渡要等稳定再判（量具踩过的第二件事）。
    expect(code).toContain("for (let i = 0; i < 10 && painted < 0.99; i += 1) {");
  });

  it("缩放读数必须在整宽侧栏的屏上量（工作台轨栏里它 display:none）", () => {
    const block = siteBlock();
    const zoom = block.slice(block.indexOf("底栏·缩放百分比"), block.indexOf("底栏·正文保存灯"));
    expect(zoom).toContain('enter: "wideSidebar"');
    expect(codeOf(census)).toContain('wideSidebar: async () => { await gotoSidebar("收件箱");');
    expect(census).toContain("desktop-sidebar--rail .desktop-dock-value");
  });

  it("判据⑤：动作后一次变化都没有记 unproven（空跑不算绿）", () => {
    const code = codeOf(census);
    expect(code).toContain('if (changes.length === 0) return { level: "unproven"');
    expect(code).toContain("const changes = rows.slice(1);");
  });

  it("判据⑥：测到 0 个站点先报失明红；站点枚举为 0 记 absent 只跳过", () => {
    const code = codeOf(census);
    expect(code).toContain('if (!measured) rows.push({ level: "red"');
    expect(code).toContain("普查失明");
    expect(code).toContain('level: "absent", why: "徽标枚举为 0（站点缺席）"');
    expect(code).toContain('if (chips === 0) return { site: site.label, level: "absent"');
  });

  it("一次哑就算红，不按多数票（silent 是 filter 出来的，不是比例）", () => {
    const code = codeOf(census);
    expect(code).toContain("const silent = changes.filter((row) => !audible(row));");
    expect(code).toContain("if (silent.length) {");
    expect(code).not.toContain("silent.length / changes.length");
    expect(code).not.toContain("silent.length > changes.length");
  });

  it("豁免口径：刻意静音的读数挂了 live 记 flood；被复述那条不再 live 记 excuse", () => {
    const code = codeOf(census);
    expect(code).toContain('if (site.kind === "mute") {');
    expect(code).toContain("const live = changes.filter((row) => row.host);");
    expect(code).toContain('return { level: "flood"');
    expect(code).toContain("if (site.twinSel && !obs.twinLive) {");
    expect(code).toContain('return { level: "excuse"');
    expect(code).toContain('return { level: "muteok"');
  });

  it("twinLive 是运行时读出来的，不是写死的 1", () => {
    const code = codeOf(census);
    expect(code).toContain("const twinLiveOf = (sel) => page.evaluate(");
    expect(code).toContain("const twinLive = site.twinSel ? await twinLiveOf(site.twinSel) : null;");
  });

  it("自证针 14 根，且喂进同一个 levelOf()/verdicts()（不另写一套判定）", () => {
    const block = census.slice(census.indexOf("function runNeedles()"));
    const names = [...block.matchAll(/results\.push\(\["([A-N]) /g)].map((m) => m[1]);
    expect(names).toEqual(["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"]);
    expect(block).toContain("const v = levelOf(site, { painted: 1, twinLive: 1, ...obs });");
    expect(block).toContain("return verdicts([{ site: site.label, ...v }], 1);");
    expect(block).toContain("has(verdicts([], 0), \"red\")");
    // 判定不许在针里就地重写：针只能调 levelOf/verdicts。
    expect(block).not.toContain('level: "silent"');
    expect(block).not.toContain('level: "flood"');
  });

  it("退出码分级：针没过 / 红 / 跳过 / 全绿各一条标记", () => {
    const code = codeOf(census);
    expect(code).toContain("STATUS_MESSAGES_NEEDLE_FAIL");
    expect(code).toContain("STATUS_MESSAGES_RED");
    expect(code).toContain("STATUS_MESSAGES_SKIP");
    expect(code).toContain("STATUS_MESSAGES_OK");
    expect(code).toContain("process.exit(2);");
    expect(code.indexOf("STATUS_MESSAGES_NEEDLE_FAIL")).toBeLessThan(code.indexOf("STATUS_MESSAGES_RED"));
  });

  it("与既有账的分工写在 header 里（错误腿 / 异步结果腿 / 两条反向豁免）", () => {
    expect(census).toContain("audit-error-identification.mjs");
    expect(census).toContain("writing-desk-volatile-readouts-not-live.test.tsx");
    expect(census).toContain("BD-233");
    expect(census).toContain("BD-175");
    expect(census).toContain("https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html");
  });

  it("速查面板站点必须切「本项目」范围再点卡片（默认范围空列表 ⇒ 站点会被判缺席）", () => {
    const code = codeOf(census);
    expect(code).toContain('page.locator(".writing-quick-tabs button", { hasText: "本项目" })');
    expect(code).toContain('throw new Error("速查「本项目」列表里没有卡片（种子没建出来）");');
    expect(code).toContain('throw new Error("速查卡片详情没打开");');
    expect(census).toContain("选中后徽标= 0");
  });

  it("种子有校验：速记建不出来 / 详情打不开就抛，不带着空分母往下跑", () => {
    const code = codeOf(census);
    expect(code).toContain('throw new Error("速记没建出来（.inbox-item 枚举为 0）");');
    expect(code).toContain('throw new Error("条目详情没打开");');
    expect(code).toContain("if (seeded) {");
    expect(code).toContain('CREATION_READER_CAPTURE_PROFILE: profileDir');
    expect(code).toContain("rmSync(profileDir, { recursive: true, force: true });");
  });

  it("红基线与站点数逐字记档（红 1 处：收件箱三次变化全无宿主）", () => {
    expect(census).toContain("听不到 1 处");
    expect(census).toContain("「未保存」「保存中…」「已保存」三次变化，live 宿主=无");
    expect(census).toContain("/tmp/bd253-status3.log");
    expect(census).toContain("/tmp/bd253-status2.log");
    expect(census).toContain("听不到 0 处｜");
  });

  it("牙齿记录：运行时 T1…T10 ＋ 静态 S1…S22，每档还原并重建", () => {
    for (const tooth of ["T1 ", "T2 ", "T3 ", "T4 ", "T5 ", "T6 ", "T7 ", "T8 ", "T9 ", "T10 "]) {
      expect(census).toContain(tooth);
    }
    expect(census).toContain("静态 S1…S22");
    expect(census).toContain("立刻逐字节还原**并重建**");
    expect(census).toContain("/tmp/bd253-teeth-runtime.log");
    expect(census).toContain("/tmp/bd253-teeth-static.log");
  });

  it("产品修法：收件箱那盏灯就地 aria-live=\"polite\"，与编辑器双胞胎逐字同形", () => {
    expect(inboxDetail).toContain('className={`inbox-save-status inbox-save-status--${saveStatus}`} aria-live="polite"');
    expect(sceneEditor).toContain('className={`scene-save-status scene-save-status--${sessionState.status}`} aria-live="polite"');
    // 同族一种写法：不另加 role="status"。
    expect(inboxDetail).not.toContain('inbox-save-status--${saveStatus}`} role="status"');
    expect(inboxDetail).toContain("BD-253");
  });

  it("产品修法不许退化成条件挂载或藏起来（首帧哑 / aria-hidden 两条判据）", () => {
    const line = inboxDetail.split("\n").find((l) => l.includes("inbox-save-status inbox-save-status--")) ?? "";
    expect(line).not.toContain("&&");
    expect(line).not.toContain("aria-hidden");
    expect(line).not.toContain('aria-live="off"');
    // 灯所在的详情头是常挂的：整段 head 不许被 saveStatus 条件包住。
    const head = inboxDetail.slice(inboxDetail.indexOf('<div className="inbox-detail-head">'), inboxDetail.indexOf("条目正文") + 200);
    expect(head).not.toContain("saveStatus &&");
  });

  it("不许另加一个常驻区把同一句话说两遍（BD-233 拦的重复播报）", () => {
    expect(inboxDetail).not.toContain('className="sr-only" role="status"');
    expect(inboxPage).not.toContain("saveStatus === \"saving\" ? \"保存中");
    // 只数 JSX 元素行上的 aria-live：注释里要留教训原文，不算挂了第二个区。
    const liveInDetail = inboxDetail.split("\n").filter((l) => l.includes("<span") && l.includes("aria-live=")).length;
    expect(liveInDetail).toBe(1);
  });

  it("两处刻意静音的豁免形态还在：dock 保存灯与专注 HUD 都不许挂 live", () => {
    const dockLamp = dock.slice(dock.indexOf("className={`desktop-dock-status"), dock.indexOf("desktop-dock-status-text"));
    expect(dockLamp).not.toContain("aria-live");
    expect(dockLamp).not.toContain('role="status"');
    expect(dock).toContain("刻意不做 aria-live");
    // 锚点不带尾部的 ">"：牙齿正是往这个标签里塞 aria-live，带上 ">" 会让锚点整段消失、
    // 切片变空、断言假过（S21 第一版就这么哑掉的，如实记档）。
    const hudStart = desk.indexOf('className="writing-focus-status"');
    expect(hudStart).toBeGreaterThan(-1);
    const hud = desk.slice(hudStart, desk.indexOf("Esc 退出专注") + 40);
    expect(hud).not.toContain("aria-live");
    expect(hud).not.toContain('role="status"');
    expect(desk).toContain("BD-188");
  });

  it("被复述的那条（编辑器行内保存灯）必须仍是 live 区——静音理由的靠山", () => {
    expect(sceneEditor).toContain('scene-save-status--${sessionState.status}`} aria-live="polite"');
    expect(census).toContain("别处不说了，这里就得说");
  });
});