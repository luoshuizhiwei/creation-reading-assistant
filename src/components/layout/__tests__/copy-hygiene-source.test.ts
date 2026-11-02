// @vitest-environment node
/**
 * BD-233 账本：用户可见文案的「内部口径」普查判据 + 本批修复锚。
 *
 * 实测基线（两轮摸底，都跑在 xvfb 下的活体 Electron 上）：
 *  · 改前：`/tmp/bd233b-survey.log` —— 12 屏扫描，命中屏次 12、独特文案 3 条
 *    （dock 那两条在所有非项目页常驻命中；写作统计的页签 title 命中 1 条）。
 *    编辑目标弹层那句**屏上正文**摸底时因导航时序没点到，静态确认在 GoalEditorDialog；
 *  · 改后：`npm run audit:copy-hygiene` —— 15 个范围全扫，独特命中 0 条，COPY_HYGIENE_OK。
 *
 * 这一族不是 WCAG 条款而是**文案缺陷**：把写代码时的依据（设计文档段落编号、
 * 「这条写入还缺后端依赖」、内部代号）抄进给用户看的气泡和正文。判它而不是判「语气」的
 * 底线是：这些话用户**读不懂也做不了什么**，而文档一改就过期。
 * 另记一笔摸底时的旁证：disabled 的 chip 在 Chromium 里根本不弹原生 title 气泡，
 * 所以那颗锁住的页签上写的说明实际只有下面的 stats-note 能到达用户——两处都得是人话。
 *
 * 牙齿账（三条，逐条实测过；每次都是「备份 → 次数断言的替换 → 必要的 build → 只红在点名的那几面 →
 * 按 sha256 逐字节还原 → 再 build → 复验绿」，日志 /tmp/bd233-t{a,b,c}.log）：
 *  T-A（判据侧）：把 INTERNAL_TALK 整串换成一个不可能命中的词，同时把 dock 那条 title 换回带
 *      设计文档编号的旧写法（屏上真有漏口）→ 普查照样 15/15、0 条、COPY_HYGIENE_OK，即**漏判**；
 *      而本账同时红 4 面（判据① + 落点① + 反向①②）。这条证明的是「判红词组还在」被钉住了——
 *      判据被人摘掉时普查不会自己喊，只有账本会。
 *  T-B（产品侧）：只把 dock 主题那条 title 换回旧写法 → 普查 COPY_HYGIENE_RED，独特文案恰好 1 条
 *      （[项目·首页] title @ span.desktop-dock-label；常驻 dock 在 15 个范围里重复命中，靠去重
 *      只记 1 条，证明第③面的去重不是把命中吞掉），本账同时红 3 面（落点① + 反向①②）。
 *  T-C（产品侧）：只把编辑目标的 stats-note 换回「…需等待目标更新接口接入」那句 → 普查
 *      COPY_HYGIENE_RED 1 条，且命中范围就是「弹层·编辑目标」（屏上文字 @ p.stats-note），
 *      证明那一个弹层范围不是摆设；本账同时红 2 面（落点③ + 反向①），另有既有的
 *      goal-editor-dialog.test.tsx「metricLocked 时…」一条跟着红——它钉的就是这句文案。
 *
 * 路径账（__tests__/ 起算）：../../../../ = 仓库根；../ = layout/；
 * ../../../features/... = src/features/...；../../../services/... = src/services/...
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../scripts/audit-copy-hygiene.mjs");
const pkg = read("../../../../package.json");
const dock = read("../DesktopDock.tsx");
const stats = read("../../../features/creation/stats/StatsPage.tsx");
const goal = read("../../../features/creation/stats/GoalEditorDialog.tsx");
const service = read("../../../services/creation-service.ts");
const ioClient = read("../../../features/creation/cards/import-export/card-import-export-client.ts");
const replaceTypes = read("../../../features/creation/replace/types.ts");

const count = (hay: string, needle: string) => hay.split(needle).length - 1;

/** 剥掉注释后的源码：只留「会被渲染或被执行」的部分，反向锁按它判。 */
const stripComments = (s: string) =>
  s.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/[^\n]*$/gm, "");

describe("BD-233 文案口吻普查判据骨架", () => {
  it("①判红词组三面齐：文档引用 / 依赖说明 / 内部代号", () => {
    expect(census).toContain("const INTERNAL_TALK = ");
    expect(census).toMatch(/§\\\\s\*\\\\d/);
    expect(census).toContain("SEAM|seam|TODO|FIXME");
    expect(census).toContain("待集成|待接入|尚未接入|未接入主进程|接口接入|写入接口|需目标更新");
    expect(census).toMatch(/批次\\\\s\*\[A-Za-z\]\{1,3\}/);
  });

  it("②通道三面齐：屏上文字 + title/aria-label/placeholder + describedby 指向的节点", () => {
    expect(census).toContain('push("屏上文字", el, node.textContent)');
    expect(census).toContain('"[title],[aria-label],[aria-describedby],[placeholder]"');
    expect(census).toContain('push("describedby", target, target.textContent)');
    // aria-hidden 子树与 script/style 不扫：那上面的话没人看见，判红只会制造噪声
    expect(census).toContain(`el.closest('[aria-hidden="true"], template, script, style')`);
  });

  it("③常驻 dock 的重复命中先按「通道+宿主+文字」去重，出口契约是 COPY_HYGIENE_RED/OK", () => {
    expect(census).toContain("x.channel === hit.channel && x.host === hit.host && x.text === hit.text");
    expect(census).toContain("COPY_HYGIENE_RED：");
    expect(census).toContain("COPY_HYGIENE_OK");
    expect(census).toMatch(/process\.exit\(1\)/);
  });

  it("④范围表点名 15 档，含本批两条实测漏口所在的弹层与浮层", () => {
    expect(census).toContain('["弹层·编辑目标"');
    expect(census).toContain('["浮层·命令面板"');
    expect(census).toContain('async () => workbenchView("写作统计", ".stats-page")');
    expect(count(census, 'button:has-text("编辑目标")')).toBe(1);
    const scopes = census.match(/^\s*\["/gm) || [];
    expect(scopes.length).toBe(15);
  });

  it("⑤普查挂在 package.json 的 audit 名下，别退化成只能手跑的脚本", () => {
    expect(pkg).toContain('"audit:copy-hygiene": "node scripts/audit-copy-hygiene.mjs"');
  });
});

describe("BD-233 本批五条修复的落点", () => {
  it("①dock 两条 label：改成对用户有用的话", () => {
    expect(dock).toContain('title="主题：在浅色、深色、跟随系统之间切换"');
    expect(dock).toContain('title="正文保存状态：与编辑器行内那条状态一致"');
  });

  it("②写作统计页签：说清作用域（预览口径），不再交代实现依赖", () => {
    expect(stats).toContain('title="只切换本页预览的字数口径；要改项目主指标请进「编辑目标」"');
  });

  it("③编辑目标：锁住的页签与那条正文都是人话，且两句结论一致", () => {
    expect(goal).toContain('title={metricLocked ? "当前版本还不能修改主指标" : WORD_METRIC_LABELS[metric]}');
    expect(goal).toContain('<p className="stats-note">当前版本还不能修改主指标：这里的选择只影响本页预览，不会写入项目。</p>');
  });
});

describe("BD-233 反向锁：内部口径不许回到渲染字符串", () => {
  it("①剥掉注释后，三个文件里再也没有文档编号、内部代号与依赖说明", () => {
    for (const [name, src] of [["DesktopDock.tsx", dock], ["StatsPage.tsx", stats], ["GoalEditorDialog.tsx", goal]]) {
      const body = stripComments(src);
      expect(body, `${name} 又被写进了设计文档段落编号`).not.toMatch(/§\s*\d/);
      expect(body, `${name} 又冒出内部代号`).not.toMatch(/SEAM|seam|TODO|FIXME/);
      expect(body, `${name} 又向用户交代实现依赖没接上`).not.toMatch(/待集成|待接入|尚未接入|需目标更新|接口接入|写入接口/);
    }
  });

  it("②title 属性这一路单独再钉一次（注释里的编号合法，属性里的不合法）", () => {
    expect(dock, "dock 的 title 里不许出现括号式的文档引用").not.toMatch(/title="[^"]*（[^"]*§/);
    expect(stats, "统计页签的 title 里不许出现依赖说明").not.toMatch(/title="[^"]*(接口|待集成)/);
  });

  it("③三条兜底 throw 保持原样：本批没动它们，账本也不许顺手改口吻", () => {
    // 它们是「装坏的机器才走到的路」，屏上没有；改它们属于另一批的活。
    expect(service).toContain('throw new Error("创作项目数据接口尚未就绪。")');
    expect(ioClient).toContain('throw new Error("卡片导入导出接口尚未就绪（SEAM 未接入主进程）。")');
    expect(replaceTypes).toContain('"seam-not-wired": "替换计划接口尚未接入（待公共 seam 接线）。"');
  });
});
