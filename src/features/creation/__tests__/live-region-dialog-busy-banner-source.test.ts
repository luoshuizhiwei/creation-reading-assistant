// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-189 源码账：对话框内「打开之后才开始变化」的忙态/结果播报，必须由常驻区承载。
 *
 * 三个卡片系对话框此前都把「正在读取/正在统计」写在了随内容挂载的 role="status"
 * 节点上——挂载瞬间的文本读屏器不回读（BD-175/187 已判据），异步结果又完全无声。
 * 钉两件可静态核对的事：
 *  1) 每个案发文件都有一条常驻 `sr-only role="status"` 播报区；
 *  2) 当年那条自带 role="status" 的可见横幅不得回潮（回潮即播报又跟着内容挂载）。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const PERSISTENT = /<span className="sr-only" role="status">/;

const sites: Array<{ file: string; banned: RegExp[] }> = [
  { file: "../cards/ProjectCardLinkDialog.tsx", banned: [/project-card-link-empty"[^>]*role="status"/] },
  { file: "../cards/import-export/CardImportDialog.tsx", banned: [/migration-note"[^>]*role="status"/] },
  { file: "../cards/GlobalCardLibraryPage.tsx", banned: [/<p role="status">正在统计关联项目和引用影响/] }
];

describe("BD-189 源码账：对话框忙态横幅常驻化", () => {
  for (const site of sites) {
    it(`${site.file.split("../").pop()}：常驻播报区存在，可见横幅不再自带 role="status"`, () => {
      const src = read(site.file);
      expect(src.match(PERSISTENT)?.length ?? 0, "缺少常驻 sr-only 播报区").toBeGreaterThanOrEqual(1);
      for (const banned of site.banned) {
        expect(banned.test(src), `回退：${banned.source} 又把播报挂回了条件节点`).toBe(false);
      }
      expect(/BD-189/.test(src), "缺少 BD-189 判据注释").toBe(true);
    });
  }
});
