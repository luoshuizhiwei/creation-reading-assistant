// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-247 源码账：常驻普查 scripts/audit-consistent-labels.mjs 的口径与基准不许悄悄漂。
 *
 * 那本账在运行时按「按下去之后屏上还剩哪些组件」判同一功能（判据见它自己的头注），
 * 它的前提条件全靠源码里的几处写法撑着：噪声过滤器、族切片、按完退回原档、站点验明正身、
 * 自证针跑的是同一份 findings()。这些写法一旦被"顺手重构"掉，普查不会报错——它会安静地
 * 变瞎（BD-246 实测过三种假信号：漏 await site.open()、selectedIndex 被 React 复位、
 * 空跑照样打 OK）。这里把口径逐条钉住，并把普查登记的基准与两处产品源码的说法钉成同一份：
 * 改产品措辞必须同时改基准，两边都改才放行。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const census = read("../../../../scripts/audit-consistent-labels.mjs");
const pkg = read("../../../../package.json");
const deskSrc = read("../editor/desk/WritingDeskOutlineSidebar.tsx");
const pageSrc = read("../outline/OutlinePage.tsx");

/** 写作台侧栏那把岛：组名 + 两颗档的可见文本。 */
function deskNames(src: string) {
  const block = /className="writing-outline-view-switch" role="group" aria-label="([^"]+)">([\s\S]*?)<\/div>/.exec(src);
  expect(block, "侧栏找不到 .writing-outline-view-switch 这把切换（认领口径漂了）").toBeTruthy();
  const options = [...block![2].matchAll(/>\s*([^<>]+?)\s*<\/button>/g)].map((m) => m[1].trim());
  expect(options.length, "侧栏这把切换应正好两颗档（多一颗少一颗都是认领漂了）").toBe(2);
  return { group: block![1], options };
}

/** 大纲页那把 Tabs：ariaLabel + items 的 label 顺序（按「含『大纲树』」认领，不预设组名）。 */
function pageNames(src: string) {
  const blocks = src.match(/<Tabs[\s\S]*?\/>/g) ?? [];
  const tab = blocks.find((b) => b.includes("大纲树"));
  expect(tab, "大纲页找不到那把含『大纲树』的 Tabs（认领口径漂了）").toBeTruthy();
  const group = /ariaLabel="([^"]+)"/.exec(tab!)![1];
  const options = [...tab!.matchAll(/label:\s*"([^"]+)"/g)].map((m) => m[1]);
  expect(options.length, "大纲页这把 Tabs 应正好两颗档").toBe(2);
  return { group, options };
}

/** 普查里登记的基准（DECLARED_FAMILIES / DECLARED_CONTROLS）。 */
function baseline(src: string) {
  const fam = /"card-board":\s*\{\s*name:\s*"([^"]+)"/.exec(src);
  const ctrl = /group:\s*"([^"]+)",\s*\n\s*order:\s*\[([^\]]*)\]/.exec(src);
  expect(fam, "普查里没有 card-board 的档名基准").toBeTruthy();
  expect(ctrl, "普查里没有 DECLARED_CONTROLS 的组名与档序基准").toBeTruthy();
  return {
    boardName: fam![1],
    group: ctrl![1],
    order: (ctrl![2].match(/"([^"]+)"/g) ?? []).map((s) => s.replace(/"/g, ""))
  };
}

describe("BD-247 同一说法普查：口径与基准", () => {
  it("普查已登记成常驻命令（不是一次性脚本）", () => {
    expect(pkg).toContain('"audit:consistent-labels": "node scripts/audit-consistent-labels.mjs"');
  });

  it("指纹口径：噪声过滤器、族切片、页面内外同一份判定", () => {
    // 噪声前缀表必须真被喂进页面内的那份判定——两边各写一套就等于口径漂移。
    expect(census, "噪声前缀表不见了").toContain('const NOISE_PREFIX = [');
    expect(census, "工具类前缀至少得挡住 bg-/text- 这两族").toContain('"bg-"');
    expect(census).toContain('"text-"');
    // 这两条是探针逼出来的：Tabs 的选中态字重与 flex 收缩系数会随「哪一档正在显示」变化，
    // 它们不是目的地（/tmp/bd247-probe4.mjs）。
    expect(census, "font-/shrink- 这类状态工具类不该再进指纹").toContain('"font-"');
    expect(census).toContain('"shrink-"');
    expect(census, "页面内那份判定必须收同一张前缀表").toContain("}, NOISE_PREFIX);");
    expect(census, "族切片必须取前两级").toContain('token.split("-").slice(0, 2).join("-")');
    // 纯小写中划线 + 长度门槛：带 [ ] : ( ) 的工具类进不了指纹。
    expect(census).toContain("/^[a-z][a-z0-9-]*$/");
  });

  it("测量纪律：每档按完退回原档、站点开起来并验明正身", () => {
    expect(census, "按完各档必须退回本来按下的那颗，否则下一屏从脏状态开始").toContain("sw.opts.find((o) => o.pressed)");
    expect(census).toContain("await clickOption(origin.k)");
    expect(census, "站点必须真开起来").toContain("await site.open();");
    expect(census, "空跑必须出声：侧栏切换验不过就抛").toContain("点侧栏「${label}」没能切到该屏");
    expect(census, "空跑必须出声：页签切换验不过就抛").toContain("点页签「${label}」没能切到该视图");
  });

  it("判据③只管文字：纯色块档记 ℹ️ 另账，不许算红", () => {
    expect(census, "判据③必须先认领 ariaOnly 再判").toContain('if (v.textMode === "ariaOnly") {');
    expect(census, "ariaOnly 走记名不走红").toContain("本账不判红");
    expect(census).toContain("audit-consistent-identification（BD-226）");
    // 初版那条误伤口径（「可见文本是空的」当红）不许回来——它把 12 屏的主题色块全判红过。
    expect(census, "判据③又回到「可见文本为空即红」的旧口径了").not.toContain("可见文本是空的（只有可及名）");
    expect(census, "可见文本的存在性应过 textMode 记录，不该再进红因").not.toContain("if (v.noText)");
  });

  it("自证针：喂副本走同一份 findings()，含一支反向针守着判据③不误伤", () => {
    expect(census, "针 A/B/C/E 必须喂正式表的副本").toContain("const copy = records.slice();");
    expect(census, "针 D 必须先摘掉该族的正式行再喂，否则会落进判据①的多名字分支").toContain("records.filter((r) => !r.own.some((t) => familyOf(t) === \"outline-tree\"))");
    expect(census).toContain("findings(copy)");
    expect(census).toContain("findings(dcopy)");
    expect(census, "针 E 断言的是『不被判据③算红』").toContain("!e");
    // 先验针再判红：把针体短路也别想全绿。
    const needleIdx = census.indexOf("if (!needleSelfTest.ran || !needleSelfTest.ok)");
    const redIdx = census.indexOf("if (reds.length > 0)");
    expect(needleIdx, "找不到针未跑/未过的拦截").toBeGreaterThan(-1);
    expect(redIdx, "找不到红判定").toBeGreaterThan(-1);
    expect(needleIdx, "针的拦截必须排在红判定之前（否则短路针体也能打 OK）").toBeLessThan(redIdx);
    for (const marker of ["LABELS_NEEDLE_FAIL", "LABELS_RED", "LABELS_SKIP", "LABELS_OK"]) {
      expect(census, `结账标记 ${marker} 不见了`).toContain(marker);
    }
  });

  it("基准与两处产品措辞钉成同一份（改说法必须两边一起改）", () => {
    const b = baseline(census);
    const desk = deskNames(deskSrc);
    const page = pageNames(pageSrc);
    expect(desk.group, "侧栏组名与普查基准不符").toBe(b.group);
    expect(page.group, "大纲页组名与普查基准不符").toBe(b.group);
    expect(desk.options, "侧栏档序与普查基准不符").toEqual(b.order);
    expect(page.options, "大纲页档序与普查基准不符").toEqual(b.order);
    expect(b.order[1], "基准里 card-board 族的档名与档序第二项应是同一个").toBe(b.boardName);
    // BD-247 修掉的正是这一对漂移：侧栏不许再退回「大纲视图」/「卡片板」。
    expect(deskSrc, "侧栏组名退回了旧叫法").not.toContain('aria-label="大纲视图"');
    expect(desk.options, "侧栏第二档退回了旧叫法").not.toEqual(expect.arrayContaining(["卡片板"]));
  });

  it("判据②的身份只用「共同覆盖」的族，档序另按档位顺序单独记账", () => {
    // 这条口径是牙齿逼出来的：初版拿各自的全部族当身份，侧栏与大纲页只差两颗工具类就判成
    // 两套机制，真缺陷只报出判据①（/tmp/bd247-teeth-runtime.log 的 R1）。
    expect(census, "两套切换算不算同一功能：看被 ≥2 处共同覆盖的族").toContain("(famControls.get(f) || new Set()).size >= 2");
    expect(census, "档序签名要按档位先后列出共有族").toContain("c.perOption.map");
    expect(census, "身份桶按共同族集合建，次序在桶内单独比").toContain('const id = c.shared.join(",")');
    expect(census, "自证针 F（档序对调）不见了").toContain("needleOrd");
    expect(census, "针 F 没进 ok 式（等于白跑）").toContain("a && b && c && d && f && !e");
  });

  it("普查头注留下了本轮实测的账（文档与判据不许脱节）", () => {
    // SITES 表加一站，头注那句实测账就得跟着重量——静态核对得住站点数，量不出来的数字
    // （档记录／ℹ️／红）由那句「干净那轮实测」的出处日志兜底。
    const sites = (census.match(/\{ label: "/g) ?? []).length;
    expect(sites, "SITES 表条数看着不对").toBeGreaterThanOrEqual(10);
    expect(census, "头注的站点数与 SITES 表不符（文档漂了就当没测过）").toContain(`站点 ${sites} 个｜档记录`);
    expect(census, "头注要留下干净那轮的出处").toContain("干净那轮实测");
    expect(census).toContain("LABELS_OK/exit 0");
  });
});
