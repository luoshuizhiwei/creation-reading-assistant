// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-193 源码账：整页/整块早返回的加载根不得自带 polite 播报。
 *
 * BD-192 收的是宿主常驻组件内的 loading 横幅；这一族更进一步——整个返回根按状态
 * 切换（`if (loading) return <section role="status">…`），忙态与结果两根互换成
 * 员，任何挂在根上的 role="status" 都是插入即哑（BD-175/180/192 判据），重试
 * （PreviewPage 失败页按钮会再进 loading）与重开项目（CPP）永远听不到。
 * 判据：
 *  1) 有真实忙态循环的（PreviewPage、CreationProjectsPage 两处加载根），忙态文案
 *     必须住在常驻区表达式里，可见加载块不带 role="status"；
 *  2) 只有挂载头几帧忙态、组件内无循环的（StatsPage），role="status" 属死装饰，
 *     必须摘除——到达播报需上抬宿主，另案，不许就地造假常驻区。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const LOADING_SECTION_WITH_ROLE = /className="creation-writing-loading" role="status"/;

describe("BD-193 源码账：早返回加载根的播报常驻化", () => {
  it("PreviewPage：同一常驻区包住四个返回支，可见加载块不再自带 status", () => {
    const src = read("../preview/PreviewPage.tsx");
    expect(/BD-193/.test(src), "缺少 BD-193 判据注释").toBe(true);
    expect(
      /const loadingAnnouncement = \(\s*<span className="sr-only" role="status">\{loading \? "正在读取全书预览…" : ""\}/.test(src),
      "忙态必须住在常驻区表达式里"
    ).toBe(true);
    const usages = src.match(/\{loadingAnnouncement\}/g)?.length ?? 0;
    expect(usages, `常驻区必须包住全部四个返回支（loading/失败/空/正文），实为 ${usages}`).toBe(4);
    expect(LOADING_SECTION_WITH_ROLE.test(src), "回退：可见加载块又自带 status——插入即哑").toBe(false);
  });

  it("CreationProjectsPage：页面根常驻区镜像两处加载分支，可见块摘 role", () => {
    const src = read("../CreationProjectsPage.tsx");
    expect(/BD-193/.test(src), "缺少 BD-193 判据注释").toBe(true);
    expect(
      /className="sr-only" role="status">\s*\{\(selected && view === "writing" && !navigation\) \|\| \(!selected && loading\) \? "正在打开项目写作台…" : ""\}/.test(src),
      "回退：忙态没镜像进页面根常驻区（或互斥条件被拆导致双播）"
    ).toBe(true);
    const matches = src.match(new RegExp(LOADING_SECTION_WITH_ROLE.source, "g"))?.length ?? 0;
    expect(matches, "回退：两处加载根仍带着插入即哑的 role=status").toBe(0);
  });

  it("StatsPage：挂载即哑的死装饰已摘除，不许用假常驻区糊弄", () => {
    const src = read("../stats/StatsPage.tsx");
    expect(/BD-193/.test(src), "缺少 BD-193 判据注释").toBe(true);
    expect(LOADING_SECTION_WITH_ROLE.test(src), "回退：加载根又把 role=status 挂了回去").toBe(false);
    // 该组件内没有第二次忙态循环；若有人就地造 <span sr-only> 假常驻区，也不解决问题。
    expect(/sr-only" role="status"|sr-only role="status"/.test(src), "StatsPage 不该就地加假常驻区（到达播报另案）").toBe(false);
  });

  it("账外防线：全仓不得再出现 creation-writing-loading 自带 status 的形态", () => {
    for (const rel of ["../preview/PreviewPage.tsx", "../CreationProjectsPage.tsx", "../stats/StatsPage.tsx"]) {
      expect(LOADING_SECTION_WITH_ROLE.test(read(rel)), `${rel} 回退`).toBe(false);
    }
  });
});
