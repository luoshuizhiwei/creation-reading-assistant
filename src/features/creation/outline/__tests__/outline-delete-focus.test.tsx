// @vitest-environment jsdom
/**
 * BD-220（BD-219 同族）：大纲树节点删除后的焦点交接。
 *
 * 两步确认删除卷/章/场景后节点整行卸载，焦点跌回 body（WCAG 2.4.3）。交接判据：
 * 按 DOM 顺序找原位置下一个删除钮；末尾退前一个；树删空退树容器。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { OutlineTree } from "../OutlineTree";
import { makeOutline } from "./fixtures";

afterEach(cleanup);

const workflow = ["草稿", "已完成"];

function tree(outline = makeOutline(), runStructure = vi.fn().mockResolvedValue(true)) {
  return (
    <OutlineTree
      outline={outline}
      workflow={workflow}
      selectedSceneId={undefined}
      onSelectScene={vi.fn()}
      runStructure={runStructure}
    />
  );
}

function deleteNode(label: string) {
  const button = screen.getByLabelText(label);
  button.focus();
  fireEvent.click(button);
  fireEvent.click(button);
}

describe("BD-220：大纲树删除后焦点交接", () => {
  it("删中间场景：焦点交给 DOM 顺序的下一个删除钮", () => {
    const outline = makeOutline();
    const runStructure = vi.fn().mockResolvedValue(true);
    const view = render(tree(outline, runStructure));

    deleteNode("删除场景「场景A」");
    expect(runStructure).toHaveBeenCalledWith({ type: "scene.delete", sceneId: "s1" });

    const next = makeOutline();
    next.volumes[0].chapters[0].scenes = next.volumes[0].chapters[0].scenes.filter((scene) => scene.id !== "s1");
    view.rerender(tree(next, runStructure));

    expect(document.activeElement).toBe(screen.getByLabelText("删除场景「场景B」"));
  });

  it("删末尾场景：末尾退前一个删除钮", () => {
    const outline = makeOutline();
    const runStructure = vi.fn().mockResolvedValue(true);
    const view = render(tree(outline, runStructure));

    deleteNode("删除场景「场景D」");
    const next = makeOutline();
    next.volumes[1].chapters[0].scenes = [];
    view.rerender(tree(next, runStructure));

    expect(document.activeElement).toBe(screen.getByLabelText("删除章节「第三章」（含其场景）"));
  });

  it("条目没走（结构未刷新）：不动焦点", () => {
    const outline = makeOutline();
    const runStructure = vi.fn().mockResolvedValue(false);
    render(tree(outline, runStructure));

    deleteNode("删除场景「场景A」");
    expect(document.activeElement).toBe(screen.getByLabelText("删除场景「场景A」"));
  });
});
