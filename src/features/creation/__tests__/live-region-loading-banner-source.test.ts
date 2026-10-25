// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-192 源码账：条件挂载的 LOADING 横幅必须由常驻宿主区的换文本播报。
 *
 * BD-173/187/191 修掉的是「结果横幅插入即哑」；这一族是同款的「忙态横幅插入即哑」：
 * `{loading && <p role="status">正在搜索…}</p>`、`{!outline ? <p role="status">…</p> : tree}`、
 * 以及 RelationGraphView 的整根早返回——忙态节点每次都是连内容一起新插入的，
 * polite 播报从未生效，重试/重载的再忙也听不到（BD-175/180 判据）。修复形态：
 *  1) 常驻宿主里一条 sr-only 播报区随状态换文本（忙态文案必须住在区域表达式里）；
 *  2) 可见 loading/state 块退回纯视觉——不得再自带 role="status"；
 *     role="alert" 的可见错误块不在此列（插入即播合法，BD-189）。
 * 整页早返回的加载根（StatsPage/PreviewPage/CreationProjectsPage 的
 * creation-writing-loading）与 EmptyState 族属 BD-193 的案。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const PERSISTENT = /<(?:span|p) className="sr-only" role="status"/g;

const hosts: Array<{ file: string; regions: number; banned: RegExp[] }> = [
  {
    file: "../../search/SearchPanel.tsx",
    regions: 1,
    banned: [/className="uni-search-state"[^>]*role="status"/, /role="status">\s*正在搜索…/]
  },
  {
    file: "../editor/desk/WritingDeskOutlineSidebar.tsx",
    regions: 2,
    banned: [/className="outline-loading"[^>]*role="status"/, /role="status">\s*正在读取大纲…/]
  },
  {
    file: "../outline/OutlinePage.tsx",
    regions: 2,
    banned: [/className="outline-loading"[^>]*role="status"/, /role="status">\s*正在读取大纲…/]
  },
  {
    file: "../cards/CardsPage.tsx",
    regions: 1,
    banned: [/className="cards-empty"[^>]*role="status"/, /role="status">\s*正在读取卡片…/]
  },
  {
    file: "../cards/components/CardListSidebar.tsx",
    regions: 1,
    banned: [/className="cards-empty"[^>]*role="status"/, /role="status">\s*正在读取卡片…/]
  },
  {
    file: "../cards/RelationGraphView.tsx",
    regions: 1,
    banned: [/className="relation-graph-state"[^>]*role="status"/, /role="status">\s*正在读取关系图…/]
  }
];

describe("BD-192 源码账：忙态横幅播报常驻化", () => {
  for (const host of hosts) {
    it(`${host.file.split("../").pop()}：常驻播报区在位数达标，可见忙态块不再自带 status`, () => {
      const src = read(host.file);
      const found = src.match(PERSISTENT)?.length ?? 0;
      expect(found, `应有 ${host.regions} 条常驻 sr-only 播报区，实为 ${found}`).toBeGreaterThanOrEqual(host.regions);
      for (const banned of host.banned) {
        expect(banned.test(src), `回退：${banned.source} 又把忙态挂回了插入即哑的可见块`).toBe(false);
      }
      expect(/BD-192/.test(src), "缺少 BD-192 判据注释").toBe(true);
    });
  }

  it("SearchPanel：忙态并入既有常驻区的换文本表达式", () => {
    const src = read("../../search/SearchPanel.tsx");
    expect(
      /const resultAnnouncement = loading[\s\S]{0,40}"正在搜索…"/.test(src),
      "回退：resultAnnouncement 不再随 loading 换忙态文本"
    ).toBe(true);
  });

  it("大纲侧栏与大纲页：忙态文案住在区域表达式里", () => {
    for (const file of ["../editor/desk/WritingDeskOutlineSidebar.tsx", "../outline/OutlinePage.tsx"]) {
      const src = read(file);
      expect(
        /className="sr-only" role="status">\s*\{!outline \? "正在读取大纲…" : ""\}/.test(src),
        `回退：${file} 的常驻区不再随 !outline 换文本`
      ).toBe(true);
    }
  });

  it("CardsPage 看板流与 CardListSidebar 列表流各守自己的常驻区", () => {
    const board = read("../cards/CardsPage.tsx");
    expect(
      /className="sr-only" role="status">\s*\{mode === "cards" && view === "board" && cardsLoading \? "正在读取卡片…" : ""\}/.test(board),
      "回退：看板流常驻区换文本表达式被拆掉（或 gating 丢失导致与列表流双播）"
    ).toBe(true);
    const list = read("../cards/components/CardListSidebar.tsx");
    expect(
      /className="sr-only" role="status">\s*\{cardsLoading \? "正在读取卡片…" : ""\}/.test(list),
      "回退：列表流常驻区不再随 cardsLoading 换文本"
    ).toBe(true);
  });

  it("RelationGraphView 收敛为单根：早返回加载态不得回潮", () => {
    const src = read("../cards/RelationGraphView.tsx");
    expect(/const busy = loading && !graph;/.test(src), "回退：busy 判据被拆，区域换文本失去落点").toBe(true);
    expect(
      /className="sr-only" role="status">\s*\{busy \? "正在读取关系图…" : ""\}/.test(src),
      "回退：常驻区不再随 busy 换文本"
    ).toBe(true);
    expect(
      /if \(loading\) return|if \(error\) return|if \(!graph\) return/.test(src),
      "回退：整根早返回又回来了——状态切换会连播报区一起重挂载"
    ).toBe(false);
  });
});
