// @vitest-environment node
/**
 * BD-256（WCAG 3.3.1 错误识别）静态账：替换面板的**后端**校验错误，
 * code 必须活着过 IPC，屏上必须是给人读的中文，出错的那一格必须被指认。
 *
 * 与运行时那本的分工：npm run audit:error-identification
 * （scripts/audit-error-identification.mjs，BD-244 立、本批扩到 10 站 16 例）
 * 真去点「预览替换」，量文案与控件；这本静态账管「不许塌回去」：
 * 主进程的编码、渲染层的解码、面板的指认分支，三头都钉住。
 *
 * 立账依据（运行时实测 /tmp/bd256k-probe.log，产品未改那轮）：
 * 正则模式填「(未闭合」／「(?=a)b」／400 个 a，三次都是
 *   bannerText="Error invoking remote method 'creation:replacePlanCreate': ReplacePlanError: 正则表达式无效。重新预览"
 *   findInvalid="false"｜findRefs=null。
 * 同屏负对照（普通文本留空查找）却全对：findInvalid="true"、findRefs="replace-error-banner"。
 * 红基线（扩账后、产品未改）/tmp/bd256-red.log：10 站 16 例、绿 13、红 3、exit 1。
 * 干净那轮 /tmp/bd256-green.log：10 站 16 例、绿 16、红 0、exit 0。
 *
 * 根因与修法：Electron 的 IPC 错误过桥只保留 message，ReplacePlanError.code 被丢掉，
 * 渲染层 toErrorView() 退化成 transaction-failed，而面板的指认条件是
 * code === "invalid-input"。修法只在两端：主进程编 "[code] 正文"，
 * creation-service 解回 code ＋ 剥掉 IPC 包装串。ReplacePanel.tsx 一行没动。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../../../..");
const read = (rel: string): string => readFileSync(path.join(repoRoot, rel), "utf8");

const census = () => read("scripts/audit-error-identification.mjs");
const mainIpc = () => read("electron/main/creation-ipc.ts");
const service = () => read("src/services/creation-service.ts");
const panel = () => read("src/features/creation/replace/ReplacePanel.tsx");
const pkg = () => read("package.json");

describe("BD-256 普查扩账的形状（3.3.1 运行时那本）", () => {
  it("命令名不变——本批是扩既有那本，不是另立一本同类账", () => {
    expect(pkg()).toContain('"audit:error-identification": "node scripts/audit-error-identification.mjs"');
  });

  it("新增站点在场：后端正则校验失败，三条用例、开换位测试", () => {
    const text = census();
    expect(text).toContain('label: "查找替换·后端正则校验失败（换位测试）"');
    expect(text).toContain('name: "正则括号没闭合"');
    expect(text).toContain('name: "受限正则不支持环视断言"');
    expect(text).toContain('name: "正则超长（400 字符）"');
    const site = text.slice(text.indexOf('label: "查找替换·后端正则校验失败'));
    expect(site.slice(0, 2200)).toContain("swap: true");
    expect(site.slice(0, 2200)).toContain('scope: ".replace-panel"');
  });

  it("三条用例都填真的坏输入，且都点「预览替换」——不是摆设", () => {
    const text = census();
    expect(text).toContain('.fill("(未闭合")');
    expect(text).toContain('.fill("(?=a)b")');
    expect(text).toContain('.fill("a".repeat(400))');
    expect(text).toContain('[data-testid="replace-mode-regex"]');
    expect(text.match(/\[data-testid="replace-preview"\]/g)?.length ?? 0).toBeGreaterThanOrEqual(4);
  });

  it("负对照还在：空查找那条绿基线不许被删，替换为那一格不许被牵连", () => {
    const text = census();
    expect(text).toContain('label: "绿基线·查找替换空查找（自家既有形状）"');
    expect(text).toContain('mustNot: [\'[data-testid="replace-replace"]\']');
  });

  it("判据【说人话】接进了走查主干，不是只写在注释里", () => {
    const text = census();
    expect(text).toContain("function saysHuman(message)");
    expect(text).toContain("else if (!saysHuman(r.message)) bad =");
    expect(text).toContain("错误文本把传输层包装串甩给了用户");
  });

  it("口径只认传输层形状：两种包装串都认，中文正文里的「错误」二字不误伤", () => {
    const text = census();
    expect(text).toContain("const IPC_WRAPPER = /Error invoking remote method|(?:^|\\s)[A-Za-z]*Error:/;");
    expect(text).toContain('saysHuman("这个错误来自你的输入。")');
  });

  it("自证针 A…H 八条，喂的是走查同一支 saysHuman()", () => {
    const text = census();
    for (const name of ["A IPC 包装串不算说人话", "B 干净中文不许误伤", "C 裸 XxxError 前缀不算说人话", "D 空文本不算说人话", "E 空查找负对照文案不许误伤", "F 中文里带错误字样不许误伤", "G 后端校验站在场且三例开换位", "H 空查找绿基线仍在"]) {
      expect(text, `缺针 ${name}`).toContain(name);
    }
    expect(text).toContain("if (!needlesOk)");
  });

  it("三个出口分得清：针不自洽 / 有红 / 有跳过", () => {
    const text = census();
    expect(text).toContain("ERROR_IDENT_NEEDLE_FAIL");
    expect(text).toContain("ERROR_IDENT_RED");
    expect(text).toContain("ERROR_IDENT_SKIP");
    expect(text).toContain("ERROR_IDENT_OK");
  });

  it("账头如实记着红基线、干净那轮与根因，不是事后编的", () => {
    const text = census();
    expect(text).toContain("/tmp/bd256k-probe.log");
    expect(text).toContain('findInvalid = "false"');
    expect(text).toContain("ReplacePlanError.code 被丢掉");
    expect(text).toContain("面板一行没动");
    expect(text).toContain("/tmp/bd256-red.log");
    expect(text).toContain("站点 10 个｜用例 16 个｜绿 13｜红 3");
    expect(text).toContain("站点 10 个｜用例 16 个｜绿 16｜红 0");
  });

  it("牙的实测与勘误都记在账头：T1…T7、S1…S16，作废与改期望不许悄悄抹掉", () => {
    const text = census();
    for (const tooth of ["· T1 ", "· T2 ", "· T3 ", "· T4 ", "· T5 ", "· T6 ", "· T7 ", "静态 S1…S16"]) {
      expect(text, `缺牙记录 ${tooth}`).toContain(tooth);
    }
    expect(text).toContain("TS6133");
    expect(text).toContain("TS2345");
    expect(text).toContain("TS2367");
    expect(text).toContain("本档作废");
    expect(text).toContain("第四次跑 TOOTH_OK");
    expect(text).toContain("S0／S99 都是 19 passed");
  });

  it("T6 撞出来的后端 SQL 缺陷只记线索，不许混进本批的修法里", () => {
    const text = census();
    expect(text).toContain("no such column: s.project_id");
    expect(text).toContain("另类记线索");
    // 本批只动过桥两端，不许顺手去改后端 SQL 或前端正则校验。
    expect(service()).not.toContain("project_id");
    expect(mainIpc()).not.toContain("no such column");
  });
});

describe("BD-256 产品修法的形状（两端，面板不动）", () => {
  it("主进程：两个替换 handler 都走编码，code 编成 \"[code] 正文\"", () => {
    const text = mainIpc();
    expect(text).toContain("const rethrowWithReplaceCode = (error: unknown): never =>");
    expect(text).toContain("throw new Error(`[${code}] ${message}`);");
    const create = text.slice(text.indexOf('ipcMain.handle("creation:replacePlanCreate"'));
    expect(create.slice(0, 400)).toContain("return rethrowWithReplaceCode(error);");
    const apply = text.slice(text.indexOf('ipcMain.handle("creation:replacePlanApply"'));
    expect(apply.slice(0, 900)).toContain("return rethrowWithReplaceCode(error);");
  });

  it("主进程：只认形状合法的 code，认不出就原样抛，不许把 undefined 编进消息", () => {
    const text = mainIpc();
    expect(text).toContain('if (typeof code === "string" && /^[a-z-]+$/.test(code))');
    expect(text).toContain("throw error;");
  });

  it("渲染层：解码剥掉 IPC 包装串，并把 code 挂回 Error 上", () => {
    const text = service();
    expect(text).toContain("function reviveReplaceError(error: unknown): never");
    expect(text).toContain("/^Error invoking remote method '[^']+':\\s*/");
    expect(text).toContain("/^[A-Za-z]*Error:\\s*/");
    expect(text).toContain("throw Object.assign(new Error(matched[2]), { code: matched[1] });");
  });

  it("渲染层：解不出 code 时落到 transaction-failed，但正文仍是剥过包装的人话", () => {
    const text = service();
    expect(text).toContain('throw Object.assign(new Error(body), { code: "transaction-failed" });');
  });

  it("渲染层：两个入口都接了解码，不是只修了预览那一条", () => {
    const text = service();
    const create = text.slice(text.indexOf("export async function replacePlanCreate"));
    expect(create.slice(0, 300)).toContain("return reviveReplaceError(error);");
    const apply = text.slice(text.indexOf("export async function replacePlanApply"));
    expect(apply.slice(0, 400)).toContain("return reviveReplaceError(error);");
  });

  it("面板那两行指认逻辑原样保留——它本来就写对了，不许被『顺手改掉』", () => {
    const text = panel();
    expect(text).toContain('aria-invalid={error?.code === "invalid-input"}');
    expect(text).toContain('aria-describedby={error?.code === "invalid-input" ? "replace-error-banner" : undefined}');
    expect(text).toContain('id="replace-error-banner"');
    expect(text).toContain('role="alert"');
  });

  it("空查找那条前端校验仍在（同屏负对照靠它成立）", () => {
    const text = panel();
    expect(text).toContain('setError({ code: "invalid-input", message: "查找内容不能为空。" });');
  });

  it("后端本来就产得出 invalid-input——修的是过桥，不是去前端重写一套正则校验", () => {
    const plan = read("electron/main/creation-workspace/replace-plan.ts");
    expect(plan).toContain("正则表达式无效。");
    expect(plan).toContain("受限正则不支持环视断言或后向引用。");
    expect(plan).toContain("正则长度不能超过");
    const panelText = panel();
    expect(panelText).not.toContain("new RegExp(");
  });
});