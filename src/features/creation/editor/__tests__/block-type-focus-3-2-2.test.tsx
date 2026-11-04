// @vitest-environment jsdom
/**
 * BD-246（WCAG 3.2.2 On Input）·「块类型」下拉框不许一改值就把人搬进正文
 *
 * 运行时普查（scripts/audit-context-change.mjs）量到的是：按一次方向键选块类型，焦点从
 * select.scene-block-select「块类型」搬到 div.tiptap.ProseMirror「场景正文编辑区」——
 * 之后的方向键改的是文档光标，四档块类型键盘浏览不动。修法是「只有按下这一刻正文本来
 * 有焦点」才把焦点交还：正打字的人选完接着打（写作流不许断），纯键盘走到下拉框的人焦点
 * 留在原地。这本账把这两条钉住，并保证命令本身还照样执行（不是把 .focus() 顺手删了就算）。
 */
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import type { SceneBodyView } from "@/types/creation";

const noop = () => undefined;

function viewOf(text: string): SceneBodyView {
  return {
    sceneId: "scene-a",
    projectId: "p1",
    chapterId: "ch1",
    title: "场景 A",
    body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
    revision: 1,
    updatedAt: ""
  } as unknown as SceneBodyView;
}

async function mount(handleBox: { current: SceneEditorHandle | null }) {
  let container: HTMLElement;
  await act(async () => {
    ({ container } = render(
      <SceneEditor
        ref={(value) => { handleBox.current = value; }}
        view={viewOf("测试正文")}
        onSave={async () => undefined}
        focusMode={false}
        onToggleFocusMode={noop}
        typewriter={false}
        onToggleTypewriter={noop}
      />
    ));
  });
  return container!;
}

afterEach(cleanup);

describe("BD-246 块类型下拉框的焦点去向（3.2.2 On Input）", () => {
  it("键盘走到下拉框再改值：焦点留在下拉框里，可以继续浏览四档", async () => {
    const handle = { current: null as SceneEditorHandle | null };
    const container = await mount(handle);
    const select = screen.getByLabelText("块类型") as HTMLSelectElement;

    // 键盘到达：Tab 进控件（此前正文没有焦点），再按方向键换档
    select.focus();
    fireEvent.keyDown(select, { key: "ArrowDown" });
    expect(document.activeElement).toBe(select);

    await act(async () => {
      fireEvent.change(select, { target: { value: "quoteLetter" } });
    });

    expect(document.activeElement, "改块类型把焦点搬进了正文——键盘用户再也浏览不动剩下的选项（BD-246 实测缺陷②）").toBe(select);
    expect(container.querySelector(".tiptap.ProseMirror")).not.toBe(document.activeElement);
    // 非空断言：命令本身照样执行（不许用「不切块」来冒充「不抢焦点」）
    expect(handle.current?.isDirty(), "改了块类型却没动文档——focus 被顺手删掉了").toBe(true);
  });

  // 「正文原本有焦点 → 改完值把焦点交还正文」这条鼠标分支**不在这里测**：jsdom 里
  // ProseMirror 拿不到 DOM 焦点（实测 handle.focus() 之后 activeElement 仍是 body），
  // 任何断言都恒成立＝假绿。它由运行时普查的 blockTypeMouseFlow() 在真 Blink 里测
  // （scripts/audit-context-change.mjs），形状侧由 context-change-family-source.test.ts
  // 钉住 onMouseDown 快照 + onKeyDown 作废这两句。这里只量「不许把焦点搬走」这一半。
});
