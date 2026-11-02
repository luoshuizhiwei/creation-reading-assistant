// @vitest-environment jsdom
/**
 * BD-226 账本：WCAG 3.2.4「一致地识别」（Level AA）——同一功能在全应用始终是同一份图案、
 * 同一个动作始终是同一个名字。
 *
 * 主判据是运行时普查 scripts/audit-consistent-identification.mjs（活体走 17 屏，按
 * 「美术签名＝子元素全部几何属性＋CSS 朝向」聚合）。本台账管运行时管不到的三件事：
 *   ① 站点逐处钉住画法：8 颗重排钮（大纲树 6 颗、场景卡板 2 颗）必须是上下箭头，
 *      尖括号一律不许再混进重排；
 *   ② 反向锁：展开/收起那两颗卷头/章头**不许被顺手改成箭头**——它们是尖括号的合法宿主，
 *      改了就等于把 3.2.4 的约定反过来违反（这一侧最容易被「统一图标」的冲动误伤）；
 *   ③ 普查自己的骨架：判据表、朝向进签名、自证针走真判据、覆盖下限。
 *      骨架没了，普查会安静地变成一条恒真断言——历史上它已经靠这条抓到我自己的错
 *      （针下在走查末尾那一屏，供体不在，两条判据都没被检验）。
 *
 * 修之前实测的三条红（/tmp/bd226-baseline.log）：
 *   ①图案 9b709a4c（向下尖括号）同时表示 收起（8 件）与 下移（13 件）；
 *   ②「上移」两种图案：21b0db03×12（大纲树，右尖括号旋转 270°）＋ 20cd08a2×1（卡板，上尖括号）；
 *   ③图案 c354905c（FolderInput）挂出两个名字：「导入包」×9 与「导入项目包」×2。
 *      （①②的件数出自 /tmp/bd226-baseline.log；③的逐名计数与「同一图案跨 4 屏」出自 /tmp/sc324-survey3.log:27）
 *
 * 路径账（__tests__/ 起算）：../../../../ = 仓库根；../ = creation/；../outline/ = creation/outline/。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../scripts/audit-consistent-identification.mjs");
const pkg = read("../../../../package.json");
const outlineTree = read("../outline/OutlineTree.tsx");
const cardBoard = read("../outline/CardBoard.tsx");
const projectsPage = read("../CreationProjectsPage.tsx");
const homePage = read("../home/ProjectHomePage.tsx");

const count = (hay: string, needle: string) => hay.split(needle).length - 1;

describe("BD-226 站点账：重排一律上下箭头", () => {
  it("大纲树：卷/章/场景三排重排钮共 3 上 3 下，全是箭头", () => {
    expect(count(outlineTree, "<ArrowUp size={12} />")).toBe(3);
    expect(count(outlineTree, "<ArrowDown size={12} />")).toBe(3);
    // 旧画法：右尖括号靠 CSS 旋转画成向上。注释里还留着这个描述，所以锁必须钉在 JSX 形态上。
    expect(outlineTree).not.toContain("rotate-270");
    expect(outlineTree).not.toContain("rotate-90");
    expect(outlineTree).not.toContain("<ChevronUp");
    expect(outlineTree).not.toContain("<ChevronDown size={12}");
    expect(outlineTree).not.toContain("<ArrowDown size={13}");
  });

  it("大纲树：六颗重排钮的名字仍带着对象（BD-169/171 的口径不许被换图时顺手丢掉）", () => {
    for (const label of ["上移卷", "下移卷", "上移章节", "下移章节", "上移场景", "下移场景"]) {
      expect(outlineTree, `重排钮的可及名前缀「${label}」丢了`).toContain(label);
    }
    expect(outlineTree).toContain('title="上移"');
    expect(outlineTree).toContain('title="下移"');
  });

  it("场景卡板：1 上 1 下，第二套画法（ChevronUp/Down）退场", () => {
    expect(count(cardBoard, "<ArrowUp size={12} />")).toBe(1);
    expect(count(cardBoard, "<ArrowDown size={12} />")).toBe(1);
    expect(cardBoard).toContain('import { ArrowDown, ArrowUp, FileText } from "lucide-react";');
    expect(cardBoard).not.toContain("<ChevronUp");
    expect(cardBoard).not.toContain("<ChevronDown");
    expect(cardBoard).toContain("上移场景");
    expect(cardBoard).toContain("下移场景");
  });

  it("反向锁：展开/收起两颗卷头章头还是尖括号，且只此一处宿主", () => {
    expect(count(outlineTree, "{expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}")).toBe(2);
    expect(outlineTree).toContain('className="outline-volume-toggle"');
    expect(outlineTree).toContain('className="outline-chapter-toggle"');
    expect(outlineTree).toContain("收起卷：");
    expect(outlineTree).toContain("展开卷：");
    expect(outlineTree).toContain("收起章节：");
    expect(outlineTree).toContain("展开章节：");
  });

  it("三处都留了 BD-226 的现场说明，防止后来人把箭头改回尖括号", () => {
    expect(outlineTree).toContain("BD-226（WCAG 3.2.4 一致地识别）");
    expect(cardBoard).toContain("BD-226");
    expect(projectsPage).toContain("BD-226（WCAG 3.2.4）");
  });
});

describe("BD-226 站点账：同一个动作同一个名字", () => {
  it("头部「导入项目包」换成与项目·首页同名的全称，短名彻底退场", () => {
    expect(projectsPage).toContain('aria-label="导入项目包" title="导入项目包"');
    expect(projectsPage).toContain("<span>导入项目包</span>");
    expect(projectsPage).not.toContain('aria-label="导入包"');
    expect(projectsPage).not.toContain("<span>导入包</span>");
  });

  it("项目·首页那颗同名动作一直是全称（两边一致的另一半锚在这里）", () => {
    expect(homePage).toContain("<FolderInput size={16} />");
    expect(homePage).toContain("导入项目包");
  });

  it("同排的导出/加密钮没被顺手改字（只有撞了同名基准的这颗该动）", () => {
    expect(projectsPage).toContain('aria-label="导出包" title="导出项目包"');
    expect(projectsPage).toContain('aria-label="加密导出" title="导出加密项目包"');
    expect(projectsPage).toContain('aria-label="加密导入" title="导入加密项目包"');
    expect(projectsPage).toContain('aria-label="导入旧稿" title="导入旧稿"');
  });

  it("普查登记了这张图的名字基准（③判据）", () => {
    expect(census).toContain('namePrefix: "导入项目包"');
    expect(census).toContain('const off = [...v.names.keys()].filter((n) => !n.startsWith(entry.namePrefix));');
  });

  it("宽度实测留在注释里：换成全称不是拍脑袋（1360 820→844px，540 只有图标 36px）", () => {
    expect(projectsPage).toContain("820→844px");
    expect(projectsPage).toContain("/tmp/bd226-width-probe.log");
  });
});

describe("BD-226 普查骨架：口径没了判据就是空的", () => {
  it("npm 脚本已挂上（不挂就等于不常驻）", () => {
    expect(pkg).toContain('"audit:identification": "node scripts/audit-consistent-identification.mjs"');
  });

  it("美术签名＝全部几何属性＋CSS 朝向；尺寸不进签名", () => {
    expect(census).toContain('a.name !== "class" && a.name !== "style"');
    expect(census).toContain('n.tagName + ":" + at');
    expect(census).toContain("Math.atan2(b, a)");
    expect(census).toContain('+ "@rot" + rot');
    expect(census).toContain("尺寸（12/13/16）不进签名");
    // 两条反例是口径的一部分，删掉它们等于允许别人把签名改窄
    expect(census).toContain("Download 与 Upload");
    expect(census).toContain("旋转 270°");
  });

  it("动词族取名字里最后命中的词，归不出的只记档不判红", () => {
    expect(census).toContain("const i = name.lastIndexOf(v);");
    expect(census).toContain("if (!r.verb) { if (r.sigs.length) notes.push(");
  });

  it("三条判红方向都在，且未登记的图案/功能族同样判红", () => {
    for (const marker of ["①一图多功能", "②一功多图", "③名称不一致", "②未登记的功能族", "①未登记的图案"]) {
      expect(census, `判据「${marker}」丢了`).toContain(marker);
    }
    expect(census).toContain('const pics = arts.filter(([h]) => h !== "（无图标）");');
  });

  it("登记的哈希是实测值：上移/下移/展开三个族钉住", () => {
    expect(census).toContain('"上移": { one: true, sigs: ["947e078f"]');
    expect(census).toContain('"下移": { one: true, sigs: ["8aabcc39"]');
    expect(census).toContain('"展开": { one: true, sigs: ["a4a8bf05", "（无图标）"]');
    expect(census).toContain('"收起": { one: true, sigs: ["9b709a4c", "（无图标）"]');
  });

  it("自证针走真判据、走正式表的副本，且落回大纲那一屏", () => {
    expect(census).toContain("收起针·BD226 Needle");
    expect(census).toContain("下移针·BD226 Needle");
    expect(census).toContain("const needleTable = structuredClone(ICONS);");
    expect(census).toContain("const needleReds = findings(needleTable);");
    expect(census).toContain("judge(needleRows.filter((r) => r.name.includes(\"Needle\")), \"自证·注入针\", needleTable);");
    // 自证这一腿自己也要有牙：把针体整段短路（return 在函数头）时，三条失败 push 都不会发生，
    // 只看失败条数就全是绿的。基准因此是「跑完了没有」这个标记，不是失败条数。
    expect(census).toContain('const selfTest = { ran: false, reds: [], caughtA: false, caughtB: false };');
    expect(census).toContain("selfTest.ran = true;");
    expect(census).toContain('if (!selfTest.ran) selfTest.reds.push("自证失败：runNeedles() 没跑到底——①②两条判据本次根本没被检验，判绿不可信。");');
    expect(census).toContain("reds.push(...selfTest.reds);");
    expect(census).toContain("if (!selfTest.caughtA) selfTest.reds.push(");
    expect(census).toContain("if (!selfTest.caughtB) selfTest.reds.push(");
    // 针必须在回到大纲之后下：供体（下移、卷头）只在这一屏同时存在
    expect(census.indexOf("await backToOutline.click().catch(() => {});")).toBeLessThan(census.indexOf("await runNeedles();"));
  });

  it("覆盖下限与退出码：走查屏数、四个关键族的实例数", () => {
    expect(census).toContain("const SCREEN_MIN = 16;");
    expect(census).toContain('const FLOOR = { "上移": 6, "下移": 6, "收起": 4, "关闭": 8 };');
    expect(census).toContain("process.exit(exitCode);");
    expect(census).toContain("return reds.length ? 1 : 0;");
    expect(census).toContain("IDENT_OK");
    expect(census).toContain("IDENT_FAIL");
  });

  it("导出模式只导现场，不许冒充判据通过", () => {
    expect(census).toContain('if (process.env.BD226_DUMP === "1") {');
    expect(census).toContain("BD226_DUMP 完成：判定 ${judged.length} 屏（未做判红，仅导出现场）。");
    expect(census).toContain("if (code === -1) exitCode = 0;");
  });

  it("走查含卡板与折叠态——重排的第二宿主和右尖括号都在这两步", () => {
    expect(census).toContain('measure("工作台·大纲·场景卡板")');
    expect(census).toContain('measure("工作台·大纲·折叠态")');
    expect(census).toContain('page.getByRole("group", { name: "大纲视图切换" })');
    expect(census).toContain('没点到「场景卡板」');
  });
});
