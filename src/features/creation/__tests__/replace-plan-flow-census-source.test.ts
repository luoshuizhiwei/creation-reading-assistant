// @vitest-environment node
/**
 * BD-257 源码账：查找替换**合法输入**链路普查的口径与两处产品修法的形状。
 *
 * 这本账读文件，不读 DOM——「面板画没画出来」「预览报不报错」「编辑器换没换正文」那半边
 * 由 scripts/audit-replace-plan-flow.mjs 在真 app 里量。两边各钉一件事：
 *   · 判据的命门（画得出才算测 / 动作要真发生 / 负对照必须被拦下 / 合法输入不许报错 /
 *     预览必须出计划 / 应用必须完成 / 应用后编辑器必须刷新 / 不许失明）每一条都对应一处
 *     真实缺陷或历史批次的教训，谁简化回去本账当场红。
 *   · 两处产品修法（场景枚举 SQL 取 c.project_id；提交事件里的场景 revision 取提交后的真实值）
 *     不许退化——退回去，真实 app 里的合法输入就又不通了。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-replace-plan-flow.mjs");
const pkg = at("package.json");
const replaceStore = at("electron/main/creation-workspace/replace-store.ts");
const workspaceIndex = at("electron/main/creation-workspace/index.ts");
const sceneActions = at("src/hooks/creation/useSceneEditorActions.ts");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

const siteBlock = () => census.slice(census.indexOf("const SITES = ["), census.indexOf("/* ---------- 页内量具"));
/** 判据函数 levelOf() 本体（不含判定表）。 */
const judgeBlock = () => {
  const code = codeOf(census);
  return code.slice(code.indexOf("function levelOf(site, obs) {"), code.indexOf("function verdicts(records, measured) {"));
};
/** 逐站测量里的一站：从它的名字起到下一站（或判据段）止。 */
const measureBlock = (name: string, next: string) => {
  const code = codeOf(census);
  const start = code.indexOf(`${name}: async () => {`);
  return code.slice(start, code.indexOf(next, start));
};

describe("BD-257 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:replace-plan-flow": "node scripts/audit-replace-plan-flow.mjs"');
  });

  it("站点 3 个：合法正则预览 / 打字保存后预览并应用 / 空查找负对照，标签与测法逐字钉住", () => {
    const block = siteBlock();
    expect([...block.matchAll(/id: "([A-Z])"/g)].map((m) => m[1])).toEqual(["A", "B", "C"]);
    expect([...block.matchAll(/label: "([^"]+)"/g)].map((m) => m[1])).toEqual([
      "正则·合法正则「abc」预览（空场景）",
      "普通文本·打字保存后预览并应用（当前场景）",
      "空查找·负对照"
    ]);
    expect([...block.matchAll(/kind: "([^"]+)"/g)].map((m) => m[1])).toEqual(["plan", "apply", "reject"]);
    expect([...block.matchAll(/measure: "([^"]+)"/g)].map((m) => m[1])).toEqual(["regexPreview", "typePreviewApply", "emptyFind"]);
  });

  it("站点预期值：A 空场景命中 0、B 命中 2 且目标词是「回声塔」、C 被拦下且文案逐字", () => {
    const block = siteBlock();
    expect(block).toContain("expect: { hits: 0 }");
    expect(block).toContain("expect: { hits: 2, word: TARGET_WORD }");
    expect(block).toContain('expect: { caught: true, banner: "查找内容不能为空。" }');
    const code = codeOf(census);
    expect(code).toContain('const TYPED_SENTENCE = "回声塔的灯照着回声塔的街。";');
    expect(code).toContain('const TARGET_WORD = "回声塔";');
    expect(code).toContain('const REPLACEMENT = "钟鼓楼";');
  });

  it("判据①：面板与错误横幅、计划视图、完成横幅都画得出才算「出现了」（祖先链 opacity 乘出来 <0.99 记 invisible）", () => {
    const code = codeOf(census);
    expect(code).toContain('if (!obs.panel) return { level: "absent", why: "面板枚举为 0（站点缺席）" };');
    expect(code).toContain('if (obs.painted < 0.99) return { level: "invisible"');
    expect(code).toContain("painted *= Number(style.opacity);");
    expect(code).toContain('if (style.display === "none" || style.visibility === "hidden") painted = 0;');
    expect(code).toContain("return rect.width > 1 && rect.height > 1 && paintedOf(el) >= 0.99;");
    expect(code).toContain("bannerVisible: drawn(banner),");
    expect(code).toContain("planVisible: drawn(plan),");
    expect(code).toContain("doneVisible: drawn(done),");
  });

  it("判据②：保存灯观察者先挂上再打字（顺序反了，「本来就写着已保存」就成了证据）", () => {
    const body = measureBlock("typePreviewApply", "emptyFind: async () => {");
    const watch = body.indexOf("if (!(await installLampWatcher()))");
    const type = body.indexOf("await page.keyboard.type(TYPED_SENTENCE, { delay: 60 });");
    expect(watch).toBeGreaterThan(-1);
    expect(watch).toBeLessThan(type);
    expect(type).toBeLessThan(body.indexOf("await waitForSavedLamp(15000);"));
    const code = codeOf(census);
    expect(code).toContain("if (trail.length === 0 && current === at) return;");
    expect(code).toContain("window.__bd257lampObs.observe(document.body, { childList: true, characterData: true, subtree: true });");
    expect(code).toContain('throw new Error("保存灯枚举为 0（.scene-save-status 缺席），保存无从取证");');
  });

  it("判据②：灯一次都没变、没走到「已保存」、字没落进编辑器，都记 unproven 不算绿", () => {
    const judge = judgeBlock();
    expect(judge).toContain('const lampOk = obs.lampTrail.length > 0 && obs.lampTrail.includes("已保存");');
    expect(judge).toContain("if (!lampOk) {");
    expect(judge).toContain("保存灯一次都没变（观察者装上之后没出现过变化）——空跑，没测到保存");
    expect(judge).toContain("保存灯没走到「已保存」（轨迹：");
    expect(judge).toContain("if (!obs.typedLanded) {");
    expect(judge).toContain("打进去的字没落进编辑器正文");
    expect(codeOf(census)).toContain("typedLanded: bodyBefore.includes(TYPED_SENTENCE),");
    // unproven 在判定表里是跳过，不是绿也不是红。
    expect(codeOf(census)).toContain("——不计绿也不计红");
  });

  it("判据③：负对照的期望写在站点表里；空查找没被拦下、文案不对都该红", () => {
    const judge = judgeBlock();
    expect(judge).toContain('if (site.kind === "reject") {');
    expect(judge).toContain("if (!site.expect.caught) {");
    expect(judge).toContain("if (!obs.bannerVisible) {");
    expect(judge).toContain("空查找没被拦下：没有可见错误横幅");
    expect(judge).toContain("if (obs.bannerText !== site.expect.banner) {");
    expect(judge).toContain("空查找的错误文案不对");
    expect(judge).toContain("负对照的期望接反了");
  });

  it("判据④：合法输入的预览出可见错误横幅即红（缺陷①的形状）", () => {
    expect(judgeBlock()).toContain('if (obs.bannerVisible) return { level: "red", why: `合法输入预览报错（错误横幅「${obs.bannerText}」）` };');
  });

  it("判据⑤：预览必须画得出计划视图，命中读数等于预期；读不出数不许当成 0 放过", () => {
    const code = codeOf(census);
    expect(code).toContain("if (!obs.planVisible) {");
    expect(code).toContain("if (hitsOf(obs.hitsText) !== site.expect.hits) {");
    expect(code).toContain("命中读数不对：实测「");
    expect(code).toContain("return matched ? Number(matched[1]) : null;");
    expect(code).toContain('[data-testid="replace-plan-view"]');
  });

  it("判据⑥：点「应用替换」后必须画得出完成横幅", () => {
    const code = codeOf(census);
    expect(code).toContain("if (!obs.doneVisible) {");
    expect(code).toContain("点「应用替换」后没出完成横幅");
    expect(code).toContain(`panel.querySelector('[data-testid="replace-done-banner"]')`);
  });

  it("判据⑦：应用后编辑器正文必须含替换后的整句、且不再含目标词，没换就点名「未刷新」", () => {
    const judge = judgeBlock();
    expect(judge).toContain("const refreshed = obs.bodyAfter.includes(obs.expectedBody) && !obs.bodyAfter.includes(site.expect.word);");
    expect(judge).toContain("应用后编辑器正文未刷新");
    const body = measureBlock("typePreviewApply", "emptyFind: async () => {");
    expect(body).toContain("const expected = TYPED_SENTENCE.split(TARGET_WORD).join(REPLACEMENT);");
    expect(body).toContain("{ timeout: 5000 }");
  });

  it("判据⑧：测到 0 个站点先报失明红，分母为 0 的绿不算通过", () => {
    const code = codeOf(census);
    expect(code).toContain("if (!measured) {");
    expect(code).toContain("一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过");
    expect(code).toContain("const main = verdicts(records, measured);");
  });

  it("站 A 真去点正则模式、填合法正则「abc」、点「预览替换」（复刻 BD-256 牙 T6 撞见缺陷①的输入）", () => {
    const body = measureBlock("regexPreview", "typePreviewApply: async () => {");
    expect(body).toContain(`page.locator('[data-testid="replace-mode-regex"]').first().click();`);
    expect(body).toContain(`page.locator('[data-testid="replace-find"]').first().fill("abc");`);
    expect(body).toContain("await clickPreview();");
    expect(codeOf(census)).toContain(`page.locator('[data-testid="replace-preview"]').first().click();`);
  });

  it("站 B 真打字、真点普通文本、真点「应用替换」、真读编辑器正文", () => {
    const body = measureBlock("typePreviewApply", "emptyFind: async () => {");
    expect(body).toContain(`page.locator('[data-testid="replace-mode-plain"]').first().click();`);
    expect(body).toContain(`page.locator('[data-testid="replace-find"]').first().fill(TARGET_WORD);`);
    expect(body).toContain(`page.locator('[data-testid="replace-replace"]').first().fill(REPLACEMENT);`);
    expect(body).toContain(`page.locator('[data-testid="replace-apply"]').first().click();`);
    expect(body).toContain("bodyAfter = await readEditorText();");
    expect(codeOf(census)).toContain(`page.locator('[aria-label="场景正文编辑区"]').first()`);
  });

  it("站 C 真把面板重开成干净状态、查找留空再点「预览替换」", () => {
    const body = measureBlock("emptyFind", "function hitsOf(text) {");
    expect(body).toContain("await ensurePanelClosed();");
    expect(body).toContain(`page.locator('[data-testid="replace-find"]').first().fill("");`);
    expect(body).toContain("await clickPreview();");
  });

  it("自证针 16 根，喂进走查同一支 levelOf()/verdicts()，不在针里就地重写判定", () => {
    const block = census
      .slice(census.indexOf("function runNeedles() {"), census.indexOf("const needleResults = runNeedles();"))
      .replace(/\s+/g, " ");
    const names = [...block.matchAll(/results\.push\(\[ ?"([A-Z]) ([^"]+)"/g)].map((m) => m[1]);
    expect(names).toEqual(["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P"]);
    expect(block).toContain("G 负对照期望接反该红");
    expect(block).toContain("P 站点表三站齐备少一站不许算绿");
    expect(block).toContain("verdicts([{ site: site.label, ...levelOf(site, { panel: true, painted: 1, ...obs }) }], 1)");
    expect(block).toContain('has(verdicts([], 0), "red")');
    // 判定不许在针里就地重写：针只能调 levelOf/verdicts（P 针数站点表）。
    expect(block).not.toContain('level: "');
  });

  it("针的结果接到出口上：判针先于判红，红先于跳过；跳过 exit 2", () => {
    const code = census.slice(census.indexOf("const needleResults = runNeedles();"));
    expect(code).toContain("const needleResults = runNeedles();");
    expect(code).toContain("const needleOk = needleResults.every(([, ok]) => ok);");
    expect(code).toContain("if (!needleOk) {");
    expect(code).toContain("REPLACE_PLAN_FLOW_NEEDLE_FAIL 自证针没过，本轮判定不可信");
    expect(code).toContain("REPLACE_PLAN_FLOW_RED 合法输入建不出计划、应用后编辑器不刷新，或者负对照失效、普查失明");
    expect(code).toContain("REPLACE_PLAN_FLOW_SKIP 有站点没测成（画不出来／空跑／缺席／进不去）");
    expect(code).toContain("REPLACE_PLAN_FLOW_OK 合法输入建得出计划、应用得下去、编辑器跟着换正文，空查找也照旧被拦下");
    expect(code).toContain('console.log("REPLACE_PLAN_FLOW_SKIP 有站点没测成（画不出来／空跑／缺席／进不去）");\n  process.exit(2);');
    expect(code.indexOf("if (!needleOk) {")).toBeLessThan(code.indexOf("if (reds.length > 0) {"));
    expect(code.indexOf("if (reds.length > 0) {")).toBeLessThan(code.indexOf("if (skips.length > 0) {"));
    expect(code.indexOf("if (skips.length > 0) {")).toBeLessThan(code.indexOf("REPLACE_PLAN_FLOW_OK"));
  });

  it("结构在一次性 profile 里现建，不碰真实用户数据，跑完删掉", () => {
    const code = codeOf(census);
    expect(code).toContain('const profileDir = mkdtempSync(path.join(tmpdir(), "creation-replace-flow-"));');
    expect(code).toContain("env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }");
    expect(code).toContain("rmSync(profileDir, { recursive: true, force: true });");
    expect(code).not.toContain("--user-data-dir");
  });

  it("来由记在 header 里：两处真实缺陷、上一棒探针账、与既有账的分工", () => {
    expect(census).toContain("no such column: s.project_id");
    expect(census).toContain("scratch/desktop-survey/probe-replace-valid.mjs");
    expect(census).toContain("probe-tooth-t1.log");
    expect(census).toContain("probe-after-restore.log");
    expect(census).toContain("scripts/audit-error-identification.mjs");
    expect(census).toContain("scripts/audit-status-messages.mjs");
    expect(census).toContain("verify:creation-replace");
    expect(census).toContain("useSceneEditorActions.handleEvent");
    expect(census).toContain("BD-256");
    expect(census).toContain("replace-plan-flow-census-source.test.ts");
  });

  it("牙齿记录：运行时 T1…T4 ＋ 静态 S1…S36，每档还原并重建", () => {
    for (const tooth of ["T1 replace-store.ts", "T2 index.ts", "T3 站 C", "T4 判据②"]) {
      expect(census).toContain(tooth);
    }
    expect(census).toContain("静态 S1…S36");
    expect(census).toContain("立刻逐字节还原**并重建**");
    expect(census).toContain("teeth-runtime-summary.txt");
    expect(census).toContain("teeth-static-summary.txt");
    expect(census).toContain("r0-green.log");
  });

  it("产品修法：三条场景枚举 SQL 取 c.project_id（scenes 表从来没有 project_id 这一列）", () => {
    const code = codeOf(replaceStore);
    expect(code.split("SELECT s.id, c.project_id, s.chapter_id,").length - 1).toBe(3);
    expect(code.split("FROM scenes s JOIN chapters c ON c.id = s.chapter_id").length - 1).toBe(3);
    expect(code).toContain("AND c.project_id = ?");
    expect(code).not.toContain("s.project_id");
  });

  it("产品修法：提交事件里的场景 revision 取提交后的真实值，不许再写死 0", () => {
    const start = workspaceIndex.indexOf("async applyReplacePlan(");
    const body = workspaceIndex.slice(start, workspaceIndex.indexOf("async sessionUpdate(command: SessionUpdateCommand)", start));
    expect(body.length).toBeGreaterThan(0);
    expect(body).toContain('entity: "scene" as const,');
    expect(body).toContain('action: "updated" as const,');
    expect(body).toContain("revision: store.getScene(sceneId)?.revision ?? 0");
    expect(body).not.toMatch(/action: "updated" as const,\s*revision: 0\b/);
  });

  it("渲染层的重读条件还在（事件 revision 的靠山）：change.revision 大于已加载的才重读正文", () => {
    expect(sceneActions).toContain("if (!isOwnSave && (!current || change.revision > current.revision)) {");
    expect(sceneActions).toContain("void loadScene(change.id);");
  });
});
