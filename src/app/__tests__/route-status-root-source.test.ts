// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-198 源码账：路由级到达播报必须住在全局根级常驻区。
 *
 * 判据链：PageTransition 以 screen 为 key，路由一切换整棵子树重挂——页面内部
 * 任何 role="status" 都只能「插入即带内容」，是哑的（BD-175/180）。所以播报区
 * 必须满足：① 唯一、挂在 App 根 JSX、位于屏幕分支与 PageTransition 之前，永不
 * 卸载；② 只镜像 ui-store.routeStatus 一个表达式；③ 忙态生产者（开书入口、
 * 阅读器页、统计页）一律经总线换文本，页面里不许再就地造假常驻区；④ 入口的
 * 播报必须发生在 IPC await 之前（await 之后的播报晚了——路由挂上去才有页面，
 * 区在页外，正是要绕开的那堵墙），失败清账只清自己的文本。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const ROOT_REGION_RE = /<span className="sr-only" role="status">\{routeStatus\}<\/span>/;

describe("BD-198 源码账：根级常驻区与生产者接线", () => {
  it("App：唯一根级常驻区，先于一切屏幕分支，只镜像 routeStatus", () => {
    const src = read("../App.tsx");
    expect(src).toContain("BD-198");
    expect(
      /const routeStatus = useUIStore\(\(state\) => state\.routeStatus\);/.test(src),
      "回退：App 没订阅 routeStatus"
    ).toBe(true);
    const hits = src.match(new RegExp(ROOT_REGION_RE.source, "g")) ?? [];
    expect(hits.length, `根级常驻区必须全 App 恰一枚，实为 ${hits.length}`).toBe(1);
    const regionAt = src.search(ROOT_REGION_RE);
    const branchAt = src.indexOf('{screen === "reader" ? (');
    const transitionAt = src.indexOf("<PageTransition");
    expect(branchAt, "回退：屏幕分支结构被改动，账约失效需重订").toBeGreaterThan(-1);
    expect(transitionAt, "回退：PageTransition 不见了，账约失效需重订").toBeGreaterThan(-1);
    expect(regionAt, "根级常驻区必须在屏幕分支之前（先于一切页面挂载）").toBeLessThan(branchAt);
    expect(regionAt).toBeLessThan(transitionAt);
  });

  it("ui-store：routeStatus 单槽总线，setRouteStatus 直写", () => {
    const src = read("../../stores/ui-store.ts");
    expect(src).toContain("BD-198");
    expect(src).toMatch(/routeStatus: string;/);
    expect(src).toMatch(/setRouteStatus: \(status: string\) => void;/);
    expect(src).toMatch(/routeStatus: "",/);
    expect(src).toMatch(/setRouteStatus: \(status\) => set\(\{ routeStatus: status \}\),/);
  });

  it("useRouteStatus：忙写槽、就绪/卸载只清 own 文本（所有权协议）", () => {
    const src = read("../../hooks/useRouteStatus.ts");
    expect(src).toContain("BD-198");
    expect(src).toMatch(/if \(busy\) \{\s*setRouteStatus\(text\);/);
    expect(src).toMatch(/\} else if \(useUIStore\.getState\(\)\.routeStatus === text\) \{/);
    expect(
      /return \(\) => \{\s*if \(useUIStore\.getState\(\)\.routeStatus === text\) setRouteStatus\(""\);/.test(src),
      "回退：卸载不再核对 own 文本"
    ).toBe(true);
    expect(src).toMatch(/export const EPUB_OPENING_STATUS = "正在打开 EPUB…"/);
    expect(src).toMatch(/export const STATS_LOADING_STATUS = "正在读取统计…"/);
  });

  it("EpubReaderPage：覆盖层与播报共一条谓词，页内不得再就地造区", () => {
    const src = read("../../features/library/EpubReaderPage.tsx");
    expect(src).toContain("BD-198");
    expect(src).toMatch(
      /const readerOpening = loading && activeBook\?\.format === "epub" && Boolean\(settings\) && Boolean\(epubUrl\);/
    );
    expect(src).toMatch(/useRouteStatus\(readerOpening, EPUB_OPENING_STATUS\);/);
    const hookAt = src.indexOf("useRouteStatus(readerOpening, EPUB_OPENING_STATUS)");
    const earlyReturnAt = src.indexOf('if (!activeBook || activeBook.format !== "epub" || !settings || !epubUrl)');
    expect(hookAt, "回退：钩子不见了").toBeGreaterThan(-1);
    expect(earlyReturnAt, "回退：空态早返回结构变了，账约失效需重订").toBeGreaterThan(-1);
    expect(hookAt, "钩子必须先于空态早返回——否则卸载清账接不上").toBeLessThan(earlyReturnAt);
    expect(src).toMatch(/\{readerOpening && \(/);
    expect(
      /\{loading && \(\s*\n\s*(?:\/\/[^\n]*\n\s*)*<div className="absolute inset-0/.test(src),
      "回退：覆盖层条件退回裸 loading（空态分支里它永远为真）"
    ).toBe(false);
    expect(src, "回退：阅读器页又就地造了假常驻区").not.toMatch(/sr-only[^>]*role="status"/);
  });

  it("StatsPage：!stats 忙态喂总线，钩子先于早返回；页内无假常驻区", () => {
    const src = read("../../features/creation/stats/StatsPage.tsx");
    expect(src).toContain("BD-198");
    expect(src).toMatch(/useRouteStatus\(!stats, STATS_LOADING_STATUS\);/);
    const hookAt = src.indexOf("useRouteStatus(!stats, STATS_LOADING_STATUS)");
    const earlyReturnAt = src.indexOf("if (!stats) {\n    return <section");
    expect(hookAt).toBeGreaterThan(-1);
    expect(earlyReturnAt, "回退：加载根结构变了，账约失效需重订").toBeGreaterThan(-1);
    expect(hookAt, "钩子必须先于早返回：早返回不跳钩子，但接线顺序错了说明有人搬动过").toBeLessThan(earlyReturnAt);
    expect(src, "回退：StatsPage 又就地造了假常驻区").not.toMatch(/sr-only[^>]*role="status"/);
  });

  it("开书入口（书库）：播报先于 IPC await，失败清账核对 own 文本", () => {
    const src = read("../../hooks/useLibraryActions.ts");
    expect(src).toContain("BD-198");
    const announceAt = src.indexOf("if (isEpub) useUIStore.getState().setRouteStatus(EPUB_OPENING_STATUS);");
    const awaitAt = src.indexOf("await executeAction", announceAt);
    expect(announceAt, "回退：入口不再同步换文本").toBeGreaterThan(-1);
    expect(awaitAt, "回退：executeAction 调用结构变了，账约失效需重订").toBeGreaterThan(announceAt);
    expect(src).toMatch(/opened === undefined && useUIStore\.getState\(\)\.routeStatus === EPUB_OPENING_STATUS/);
  });

  it("开书入口（搜索直达）：播报先于 openEpub，抛错清账后继续上抛", () => {
    const src = read("../../hooks/useSearchActions.ts");
    expect(src).toContain("BD-198");
    const announceAt = src.indexOf("if (isEpub) useUIStore.getState().setRouteStatus(EPUB_OPENING_STATUS);");
    const openAt = src.indexOf("await openEpub(intent.bookId)", announceAt);
    expect(announceAt, "回退：搜索入口不再同步换文本").toBeGreaterThan(-1);
    expect(openAt, "回退：openEpub 调用结构变了，账约失效需重订").toBeGreaterThan(announceAt);
    expect(src).toMatch(/isEpub && useUIStore\.getState\(\)\.routeStatus === EPUB_OPENING_STATUS/);
    expect(src, "回退：清账路径吞掉了异常").toMatch(/setRouteStatus\(""\);\s*\}\s*throw error;/);
  });
});
