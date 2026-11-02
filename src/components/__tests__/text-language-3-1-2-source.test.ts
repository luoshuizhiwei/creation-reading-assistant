// @vitest-environment jsdom
/**
 * BD-225 账本：WCAG 3.1.2 部分语言（Level AA）+ 装饰性双语眉标的重复播报。
 *
 * 主判据是 scripts/audit-text-language.mjs（运行时活体：逐屏扫「进无障碍树的拉丁散文 run」
 * 的有效语言，并把 11 处装饰眉标按精确文本认成「不许在树里」）。本台账管两件运行时管不到的事：
 *   ① 11 处站点逐处钉住 lang="en" + aria-hidden="true"——其中 AI 报告 / AI 候选评审两颗弹层
 *      要接上 AI 后端才出得来，运行时判不到，只能在这里判；
 *   ② 同槽位的中文/动态眉标**不许被顺手藏掉**——它们下面没有同义标题，那份信息只有这一处，
 *      藏了就是丢内容（这一侧的锁比①更容易被「统一处理」误伤，所以逐处钉）。
 *
 * 11 处对照表（眉标 → 紧邻的中文正身）：
 *   Editor→条目正文 / AI polish→AI 候选版本 / Scene card→场景任务卡 / World bible→全局卡片库页头
 *   World building→创作背景设定 / New project→新建作品 / Index drawer→写作速查 / Manuscript→场景标题
 *   AI Report→{动作}报告 / AI Candidate→AI 候选 / Reading rhythm→阅读统计仪表板
 *
 * 路径账（__tests__/ 起算）：../../../ = 仓库根；../layout/ = src/components/layout/；
 * ../../features/ = src/features/。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-text-language.mjs");
const pkg = read("../../../package.json");

const MARK = "{/* BD-225 装饰眉标：与紧邻的中文标题同义复述，标 lang=en 且不进读屏树 */}";

/** 11 处装饰性英文眉标：文件、标签、文本、紧邻的中文正身。 */
const DECOR = [
  ["../../features/creation/inbox/components/InboxItemDetail.tsx", "div", "Editor", "条目正文"],
  ["../../features/creation/inbox/components/InboxItemDetail.tsx", "div", "AI polish", "AI 候选版本"],
  ["../../features/creation/outline/OutlinePage.tsx", "p", "Scene card", "场景任务卡"],
  ["../../features/creation/cards/GlobalCardLibraryPage.tsx", "span", "World bible", "全局卡片库"],
  ["../../features/creation/background/BackgroundPage.tsx", "span", "World building", "创作背景设定"],
  ["../../features/creation/CreateProjectWizard.tsx", "p", "New project", "新建作品"],
  ["../../features/creation/editor/WritingQuickReferencePanel.tsx", "p", "Index drawer", "写作速查"],
  ["../../features/creation/editor/desk/WritingDeskHeader.tsx", "p", "Manuscript", "场景标题"],
  ["../../features/creation/ai/SceneAiReport.tsx", "p", "AI Report", "报告"],
  ["../../features/creation/ai/SceneCandidateReview.tsx", "p", "AI Candidate", "AI 候选"],
  ["../../features/library/ReadingStatsPage.tsx", "div", "Reading rhythm", "阅读统计仪表板"]
];

/** 同槽位但带独有信息的中文/动态眉标：必须留在树里。 */
const KEEP_EXPOSED = [
  ["../../features/creation/cards/CardsPage.tsx", "span", "关联卡片"],
  ["../../features/creation/editor/desk/WritingDeskMargin.tsx", "p", "批注与引用"],
  ["../../features/creation/editor/desk/WritingDeskMargin.tsx", "p", "新锚点"],
  ["../../features/creation/cards/RelationGraphView.tsx", "p", "{selectedNode.kindName}"],
  ["../layout/DesktopFrame.tsx", "div", "工作台概览"]
];

describe("BD-225 站点账：11 处装饰眉标", () => {
  for (const [rel, tag, text, zh] of DECOR) {
    it(`${text}：标 en 且不进树（正身是紧邻的「${zh}」）`, () => {
      const code = read(rel);
      expect(code, `${rel}：这颗眉标丢了 lang="en"——中文语音包会逐字母硬拼它`)
        .toContain(`<${tag} className="desktop-card-label" lang="en" aria-hidden="true">${text}</${tag}>`);
      expect(code, `${rel}：这颗眉标丢了 aria-hidden——读屏会把它和「${zh}」念两遍`)
        .not.toContain(`<${tag} className="desktop-card-label">${text}</${tag}>`);
    });
  }

  it("11 处都有那行可 grep 的标记注释，一处不多一处不少", () => {
    const perFile = new Map<string, number>();
    for (const [rel] of DECOR) perFile.set(rel, (perFile.get(rel) ?? 0) + 1);
    let total = 0;
    for (const [rel, times] of perFile) {
      const got = read(rel).split(MARK).length - 1;
      expect(got, `${rel}：标记注释应有 ${times} 行`).toBe(times);
      total += got;
    }
    expect(total).toBe(11);
  });
});

describe("BD-225 反向锁：带独有信息的中文眉标不许顺手藏进装饰族", () => {
  for (const [rel, tag, text] of KEEP_EXPOSED) {
    it(`${text}：留在无障碍树里`, () => {
      const code = read(rel);
      expect(code, `${rel}：「${text}」被误标成 aria-hidden——它下面没有同义标题，藏了就是丢内容`)
        .toContain(`<${tag} className="desktop-card-label">${text}</${tag}>`);
      expect(code).not.toContain(`<${tag} className="desktop-card-label" aria-hidden="true">${text}</${tag}>`);
      expect(code).not.toContain(`<${tag} className="desktop-card-label" lang="en" aria-hidden="true">${text}</${tag}>`);
    });
  }
});

describe("BD-225 普查判据骨架", () => {
  it("①的针走的是真判据，且回溯不许停在 body（T-E2 实测：停在 body 时 9 处红全成绿的）", () => {
    expect(census).toContain('await measure("自证·注入针")');
    expect(census, "回溯又 break 在 body 了：有效语言恒为空，①整条判据静默空转")
      .not.toContain("if (p === document.body) break;");
    expect(census).toContain('document.documentElement.getAttribute("lang")');
    expect(census).toContain("Language Needle Probe");
    expect(census).toContain("自证失败");
  });

  it("②的名单与站点账同源：11 条精确文本，一条不多一条不少", () => {
    for (const [, , text] of DECOR) expect(census, `普查的 DECOR_EYEBROWS 少了「${text}」`).toContain(`"${text}"`);
    expect(census).toContain("const DECOR_EYEBROWS = [");
    expect(census).toContain("装饰性双语眉标仍在读屏里");
  });

  it("豁免按语义认（kbd / 文件格式名），散文词混进豁免表就红", () => {
    expect(census).toContain('closest("kbd")');
    expect(census).toContain("const FORMAT_WORDS = [");
    for (const prose of ["manuscript", "worldbible", "worldbuilding", "readingrhythm", "scenecard", "newproject"]) {
      expect(census, `豁免表里出现了散文词 "${prose}"——那是把 3.1.2 的口子挖大`)
        .not.toContain(`"${prose}"`);
    }
    expect(census).toContain("术语豁免");
  });

  it("用户内容整块排除：正文/通读/阅读器/书库行不归 app 的语言标注", () => {
    for (const sel of [".writing-scroll", ".preview-page", ".reader-page", ".desktop-library-row"]) {
      expect(census, `用户内容宿主清单丢了 ${sel}`).toContain(sel);
    }
  });

  it("覆盖下限与结论口径都在，且已挂进 npm scripts", () => {
    expect(census).toContain("const LANG_MIN = 12;");
    expect(census).toContain("LANGUAGE_OK");
    expect(census).toContain("LANGUAGE_RED");
    expect(census).toContain("process.exit(exitCode)");
    expect(census, "红与覆盖不足都必须把退出码置 1").toContain("exitCode = 1");
    expect(census, "覆盖不足必须单独报，不许用零红蒙过").toContain("覆盖不足无法证明");
    expect(JSON.parse(pkg).scripts["audit:text-language"]).toBe("node scripts/audit-text-language.mjs");
  });
});
