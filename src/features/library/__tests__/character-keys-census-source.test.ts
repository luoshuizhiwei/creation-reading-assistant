// @vitest-environment node
/**
 * BD-250 源码账：裸单键快捷键普查（WCAG 2.1.4）的口径与产品那条「关断退路」。
 *
 * 这本账读文件，不读 DOM——DOM 那半边由 scripts/audit-character-keys.mjs 在真 app 里量。
 * 两边各钉一件事：
 *   · 「连按两次才算命中」「瞬时件不进签名」「焦点先 blur 到 body」这三条是本账的判据命门。
 *     实测教训摆在那儿：探针①②只按一次，抓到过三个假反应（大纲的 k、项目列表的 i、
 *     EPUB 的「.」），前两个在源码里根本没有监听。谁把这两层简化回去，本账当场红。
 *   · 产品那条开关（reader.spacePaging）不许悄悄退回去：普查会红，但红在每日账里；
 *     本账把「当初为什么补这一笔、为什么走例外①而不是例外③」钉在文件上。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-character-keys.mjs");
const pkg = at("package.json");
const types = at("src/types/library.ts");
const store = at("electron/main/settings-store.ts");
const epub = at("src/features/library/EpubReaderPage.tsx");
const section = at("src/features/settings/sections/ReaderSection.tsx");
const more = at("src/features/library/ReaderSettingsMorePanel.tsx");
const registry = at("src/features/navigation/registry.ts");

describe("BD-250 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:character-keys": "node scripts/audit-character-keys.mjs"');
  });

  it("扫描表只放免 Shift 打得出的字符键：26 字母＋10 数字＋空格＋10 个未档标点", () => {
    expect(census).toContain('  ..."abcdefghijklmnopqrstuvwxyz".split(""),');
    expect(census).toContain('  ..."0123456789".split(""),');
    const keysBlock = census.slice(census.indexOf("const KEYS = ["), census.indexOf("const KNOWN_SHORTCUTS"));
    expect(keysBlock).toContain('" ", "-", "=", "[", "]"');
    expect(keysBlock).toContain('";", "\'", ",", ".", "/"');
    // 带 Shift 的标点 press 出来是组合键，混进来就等于把「单键」口径放宽到和弦
    expect(keysBlock, "扫描表里冒出了需要 Shift 的标点——那不是 2.1.4 的单键").not.toMatch(/"[?!@+:]"/);
  });

  it("登记表把「哪个键由哪条设置项关掉」写死，判据②才有的放矢", () => {
    expect(census).toContain('const KNOWN_SHORTCUTS = [');
    expect(census).toContain('{ key: " ", name: "空格翻页（EPUB 阅读器）", setting: "reader.spacePaging" }');
  });

  it("命中要连按两次：一次性漂移记 drift，不许算命中（探针①②的假红就出在只按一次）", () => {
    expect(census).toContain("if (!first.changed && !first.prevented) return { quiet: true };");
    expect(census).toContain("if (!(second.changed || second.prevented)) return { drift: first.what };");
    expect(census).toContain("return { hit: { key, bare: first.bare && second.bare");
  });

  it("签名要剔掉瞬时件（toast、role=status/alert、aria-live），否则异步沉降会被当成快捷键反应", () => {
    expect(census).toContain(
      `clone.querySelectorAll('[class*="toast" i],[role="alert"],[role="status"],[aria-live]').forEach((n) => n.remove());`
    );
  });

  it("例外③靠「焦点先 blur 到 body」把关：焦点不在 body 的按键一律记挡住，不判红也不判绿", () => {
    expect(census).toContain("document.activeElement?.blur?.(); window.__ck.events = [];");
    expect(census).toContain('if (before.focus !== "body") return { blocked: before.focus };');
  });

  it("退路核验要先进「阅读器」分区：设置页默认停在「外观」，reader.* 那一档根本没渲染", () => {
    // 实测踩过：产品里开关已经有了，普查只走到「设置」屏就报「这条退路在产品里还不存在」。
    // 少点一次分区页签＝把真退路判成没退路，也＝把「产品没做」的红变成永远修不掉的红。
    expect(census).toContain('await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();');
    expect(census).toContain("const gotoReaderSettings = async () => {");
    expect((census.match(/await gotoReaderSettings\(\);/g) ?? []).length, "拨开关和拨回都要走分区页签").toBeGreaterThanOrEqual(2);
    // 开关本体是 pointer-events:none 的隐形 input（settings-controls.css:94），触点只能是 label；
    // 状态只能读 isChecked()——原生 checkbox 的 aria-checked 不落 DOM，读属性会把「拨得动」判成「拨不动」。
    expect(census).toContain("const label = box.locator(\"label\").first();");
    expect(census).toContain("const on = await input.isChecked();");
    // 只看代码行（注释里要留下「为什么不走 aria-checked」的教训，不算引用）
    const readBody = census
      .slice(census.indexOf("const readSwitch ="), census.indexOf("const gotoReaderSettings"))
      .split("\n")
      .filter((l) => !l.trim().startsWith("//"))
      .join("\n");
    expect(readBody, "读开关状态不许退回读 DOM 属性（aria-checked 不落 DOM，会永久判成拨不动）").not.toContain("aria-checked");
  });

  it("失明只看分母：0 屏才叫失明，扫到屏而零命中是合规", () => {
    expect(census).toContain("if (!sweptSites) {");
    expect(census).toContain("一屏都没扫到");
    expect(census).toContain("const main = verdicts(records, sweptSites);");
    // 正式表与针共用同一个判定函数，不许为针另写一套条件（也不许把判定抄三遍）
    expect((census.match(/verdicts\(/g) ?? []).length, "verdicts 的调用点看着不对").toBeGreaterThanOrEqual(9);
  });

  it("三种红各管一件事：没机制、找不到开关、拨关后仍触发（外加开关拨不动）", () => {
    expect(census).toContain("却没有任何关断／改键机制登记");
    expect(census).toContain("这条退路在产品里还不存在");
    expect(census).toContain("机制是假的");
    expect(census).toContain("退路是装饰");
  });

  it("四种退出标记按名字钉住", () => {
    expect(census).toContain("CHARACTER_KEYS_OK");
    expect(census).toContain("CHARACTER_KEYS_RED");
    expect(census).toContain("CHARACTER_KEYS_SKIP");
    expect(census).toContain("CHARACTER_KEYS_NEEDLE_FAIL");
  });

  it("退路核验必须自带正向对照：拨关前先确认这把键「开着的时候」真的会反应", () => {
    // 牙 T1 第一轮实测：把开关判断整条摘掉（真·假机制）却仍然全绿——因为书页翻到末尾，
    // 「拨到关后复按没变化」是空跑出来的。这一手（对照＋倒回中段）就是那一轮补上的。
    expect(census).toContain("if (!control.hit) {");
    expect(census).toContain('return { level: "unproven"');
    expect(census).toContain('else if (r.escape === "unproven") rows.push({ level: "skip"');
    expect((census.match(/await rewindInReader\(\);/g) ?? []).length, "拨关前后各要倒回一次，少一次对照就可能空跑").toBe(2);
    expect(census).toContain("const rewindInReader = async () => {");
  });

  it("自证针八支各管一件事，短路任何一支都别想全绿", () => {
    const body = census.slice(census.indexOf("function runNeedles()"), census.indexOf("const needleResults"));
    expect(body).toContain('verdicts([{ site: "针A屏", key: "q", bare: true, escape: "none" }], 3)');
    expect(body).toContain('verdicts([{ site: "针B屏", key: " ", bare: true, escape: "ok" }], 3)');
    expect(body).toContain('verdicts([{ site: "针C屏", key: " ", bare: true, escape: "fake" }], 3)');
    expect(body).toContain('escape: "stuck"');
    expect(body).toContain("verdicts([], 0)");
    expect(body).toContain("verdicts([], 14)");
    expect(body).toContain('verdicts([{ site: "针G屏", key: " ", bare: true, escape: "unproven" }], 3)');
    expect((body.match(/results\.push\(/g) ?? []).length, "针的支数漂了").toBe(8);
  });

  it("站点表 14 屏、书库自带 fixture（阅读器进不去就等于没量）", () => {
    const sites = (census.match(/\{ label: "/g) ?? []).length;
    expect(sites, "SITES 表条数看着不对").toBe(14);
    expect(census).toContain('await page.waitForSelector(".desktop-library-row", { timeout: 15000 });');
    // 两种格式的阅读器挂载标志不是一个东西（EPUB=.paper-topbar，TXT/MD=.reader-host）；
    // 只等前者会让 TXT 屏常年「进不去」，分母少一个屏而数字看着还是绿的——摸底那轮真踩到了。
    expect(census).toContain('await page.waitForFunction(() => Boolean(document.querySelector(".paper-topbar, .reader-host")), null, { timeout: 30000 });');
    // fixture 尺寸实测钉死：3 章 × 12 段是「空格两次都反应」能稳定命中的形状。
    // 加大它反而会让命中掉到 0（epubjs 分页没完成时第一算变化、第二不＝一次性漂移，
    // 而命中 0 在本账是合规）——T1b 实测就是这么把假机制放过去的，见普查头注那段。
    expect(census).toContain('const titles = ["第一章 雨夜来信", "第二章 阁楼的锁", "第三章 旧运河"];');
    expect(census).toContain("Array.from({ length: 12 }, (_, k) => `<p>${PARA}");
    expect(census).toContain("const epubFixture = seedLibrary();");
    expect(census).toContain("await writeEpubFixture(epubFixture);");
  });

  it("头注留下的数字要跟着两张表走（文档漂了就当没测过）", () => {
    // 14 屏 × 每屏 47 键（26 字母＋10 数字＋11 个免 Shift 标点/空格）＝ 658 个扫描点
    const sites = (census.match(/\{ label: "/g) ?? []).length;
    expect(sites).toBe(14);
    expect(census, "头注的站点数与 SITES 表不符").toContain("站点 14 个｜扫描点 658 处（14×47）");
    expect(census, "头注要留下干净那轮的出处").toContain("干净那轮实测");
  });
});

describe("BD-250 产品退路（空格翻页的关断机制）", () => {
  it("ReaderSettings 有 spacePaging 这个档", () => {
    expect(types).toContain("spacePaging: boolean;");
  });

  it("主进程默认开、且旧设置文件缺这一档时回落默认值（不许把老用户直接判成关）", () => {
    expect(store).toContain("spacePaging: true,");
    expect(store).toContain('spacePaging: typeof raw.spacePaging === "boolean" ? raw.spacePaging : defaults.spacePaging,');
  });

  it("EPUB 页只把空格这一支接在开关上，方向键与 PgDn 不许一起被关掉", () => {
    const line = epub.split("\n").find((l) => l.includes("ArrowRight") && l.includes("PageDown") && l.includes('event.key === " "'));
    expect(line, "翻页那条判断被改写了——重读 EpubReaderPage").toBeTruthy();
    expect(line).toContain('(event.key === " " && settings?.spacePaging !== false)');
    expect(epub).toContain("[turnPage, settingsDrawerOpen, settings?.spacePaging]");
  });

  it("设置页那条开关登记了 data-setting-id，且全仓只出现一次（普查靠它定位）", () => {
    expect(section).toContain('<div data-setting-id="reader.spacePaging">');
    expect(section).toContain("checked={settings.reader.spacePaging}");
    expect(section).toContain('label="空格翻页"');
    const occurrences = [section, more].join("\n").match(/data-setting-id="reader\.spacePaging"/g) ?? [];
    expect(occurrences.length, "同一个 data-setting-id 出现在两处，普查的 .first() 就不唯一了").toBe(1);
  });

  it("阅读抽屉里也有同一开关的另一条入口，且只在 EPUB 下露出", () => {
    const at = more.indexOf("阅读抽屉里也要能当场关掉");
    expect(at, "抽屉里那条入口的说明被删了？").toBeGreaterThanOrEqual(0);
    const block = more.slice(at, at + 900);
    expect(block, "抽屉里的空格翻页开关没写在「仅 EPUB」条件里").toContain('{format === "epub" && (');
    expect(block).toContain("checked={settings.spacePaging}");
    expect(block).toContain('label="空格翻页"');
  });

  it("设置搜索登记表里有这一条（用户搜「快捷键」「单键」要能找到退路）", () => {
    expect(registry).toContain('S("reader", "spacePaging", "格式与行为", "空格翻页"');
  });
});
