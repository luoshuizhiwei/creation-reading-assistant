// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { CardListSidebar } from "@/features/creation/cards/components/CardListSidebar";

afterEach(cleanup);

/**
 * BD-192：列表侧栏的「正在读取卡片…」此前是条件挂载的 <p role="status">——
 * aside 明明常驻，重载时那条 p 却连内容一起新插入，polite 播报是哑的
 * （BD-175/180/187 判据）。播报交给侧栏常驻区换文本，可见 p 退回纯视觉。
 */
describe("CardListSidebar 读卡忙态常驻播报（BD-192）", () => {
  it("读卡中常驻区带忙态，落定后同一节点清空、可见 p 不带 status", () => {
    const { container, rerender } = render(
      <CardListSidebar cards={[]} cardsLoading onSelectCard={() => {}} typeNameMap={new Map()} />
    );
    const region = container.querySelector('aside.cards-list-pane > span.sr-only[role="status"]');
    expect(region?.textContent, "回退：忙态没落进常驻区，播报是哑的").toBe("正在读取卡片…");

    const visible = container.querySelector("p.cards-empty");
    expect(visible?.textContent).toBe("正在读取卡片…");
    expect(visible?.getAttribute("role"), "回退：可见 p 又自带 status，插入即哑").toBeNull();

    rerender(<CardListSidebar cards={[]} cardsLoading={false} onSelectCard={() => {}} typeNameMap={new Map()} />);
    expect(
      container.querySelector('aside.cards-list-pane > span.sr-only[role="status"]'),
      "回退：区域随忙态结束被卸载"
    ).toBe(region);
    expect(region?.textContent).toBe("");
  });

  it("空态文案可见但不播报——播报区只负责忙态换文本", () => {
    const { container } = render(
      <CardListSidebar cards={[]} cardsLoading={false} onSelectCard={() => {}} typeNameMap={new Map()} />
    );
    const region = container.querySelector('aside.cards-list-pane > span.sr-only[role="status"]');
    expect(region?.textContent).toBe("");
    const visible = container.querySelector("p.cards-empty");
    expect(visible?.textContent).toContain("还没有卡片");
    expect(visible?.getAttribute("role")).toBeNull();
  });
});
