// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardCoverImage } from "@/features/creation/cards/components/CardCoverImage";
import type { ResourceInfo } from "@/types/creation";

function resourceOf(overrides: Partial<ResourceInfo> = {}): ResourceInfo {
  return {
    id: "resource-cover",
    projectId: null,
    cardId: "card-1",
    ownerScope: "card",
    role: "attachment",
    relativePath: "resources/cards/card-1/cover.png",
    sha256: "a".repeat(64),
    size: 1024,
    originalName: "封面.png",
    createdAt: "2026-09-12T00:00:00.000Z",
    ...overrides
  } as ResourceInfo;
}

afterEach(() => cleanup());

describe("CardCoverImage 全局卡片封面", () => {
  it("未设置封面时给出空态，不渲染 img", () => {
    render(<CardCoverImage cardId="card-1" resources={[]} title="林墨" />);
    expect(screen.getByText("林墨 尚未设置封面")).toBeDefined();
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("只有附件没有封面时同样走空态", () => {
    render(<CardCoverImage cardId="card-1" resources={[resourceOf({ role: "attachment" })]} title="林墨" />);
    expect(screen.getByText("林墨 尚未设置封面")).toBeDefined();
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("有封面时 img 指向 creation-asset 只读协议，且带可读 alt", () => {
    render(<CardCoverImage cardId="card-1" resources={[resourceOf({ role: "cover" })]} title="林墨" />);
    const image = screen.getByRole("img", { name: "林墨 封面" }) as HTMLImageElement;
    expect(image.getAttribute("src")).toBe("creation-asset://card/card-1/resource-cover");
    // 加载中要有明确提示，避免空白区域被误认为没有封面。
    expect(screen.getByText("正在读取封面…")).toBeDefined();
  });

  it("加载成功后移除加载提示", () => {
    render(<CardCoverImage cardId="card-1" resources={[resourceOf({ role: "cover" })]} title="林墨" />);
    fireEvent.load(screen.getByRole("img", { name: "林墨 封面" }));
    expect(screen.queryByText("正在读取封面…")).toBeNull();
  });

  it("加载失败时给出失败态而不是静默空白", () => {
    render(<CardCoverImage cardId="card-1" resources={[resourceOf({ role: "cover" })]} title="林墨" />);
    fireEvent.error(screen.getByRole("img", { name: "林墨 封面" }));
    expect(screen.getByRole("alert").textContent).toContain("封面读取失败");
  });

  it("封面资源 ID 变化后 URL 随之变化，不需要额外的缓存破除参数", () => {
    const { rerender } = render(
      <CardCoverImage cardId="card-1" resources={[resourceOf({ role: "cover" })]} title="林墨" />
    );
    const before = (screen.getByRole("img", { name: "林墨 封面" }) as HTMLImageElement).getAttribute("src");
    rerender(
      <CardCoverImage
        cardId="card-1"
        resources={[resourceOf({ role: "cover", id: "resource-cover-2" })]}
        title="林墨"
      />
    );
    const after = (screen.getByRole("img", { name: "林墨 封面" }) as HTMLImageElement).getAttribute("src");
    expect(before).toBe("creation-asset://card/card-1/resource-cover");
    expect(after).toBe("creation-asset://card/card-1/resource-cover-2");
  });
});
