// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-188 源码账：逐字/逐选择刷新的读数不得做 polite live region。
 *
 * 与 BD-187 相反方向的失效：live region 里放每敲一键、每扩展一步选区就刷新的
 * 读数（字数、锚点预览），播报会刷屏并把真正该被读到的信息淹没。这类读数是
 * 「扫一眼」的常驻显示，正确形态是普通可见节点。jsdom 渲染专注 HUD 与批注
 * 页签成本高、且「不该播报」在 jsdom 里不可断言，这里钉三件可静态核对的事：
 *  1) 案发节点不得再带 role="status" / aria-live；
 *  2) 裸类名节点仍在原位（读数本身没有被顺手删掉）；
 *  3) 两处都留有 BD-188 判据注释，防止后人「补 a11y」时把 live 加回来。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

describe("BD-188 源码账：高频易变读数不得挂 live region", () => {
  it("WritingDesk 专注 HUD：字数读数所在节点无 role/aria-live，节点仍在", () => {
    const src = read("../editor/WritingDesk.tsx");
    expect(/className="writing-focus-status">/.test(src), "专注 HUD 节点被删或改形").toBe(true);
    expect(/writing-focus-status[^>]*role="status"/.test(src), "回退：HUD 又带 role=status，字数每敲一键刷屏").toBe(false);
    expect(/writing-focus-status[^>]*aria-live/.test(src), "回退：HUD 又带 aria-live").toBe(false);
    expect(/BD-188/.test(src), "缺少 BD-188 判据注释").toBe(true);
  });

  it("WritingDeskMargin 锚点读数：选区每变一次就刷新的节点无 aria-live/role，节点仍在", () => {
    const src = read("../editor/desk/WritingDeskMargin.tsx");
    expect(/className="writing-annotation-selection">/.test(src), "锚点读数节点被删或改形").toBe(true);
    expect(/writing-annotation-selection[^>]*aria-live/.test(src), "回退：锚点读数又带 aria-live，拖选过程刷屏").toBe(false);
    expect(/writing-annotation-selection[^>]*role="status"/.test(src), "回退：锚点读数又带 role=status").toBe(false);
    expect(/BD-188/.test(src), "缺少 BD-188 判据注释").toBe(true);
  });
});
