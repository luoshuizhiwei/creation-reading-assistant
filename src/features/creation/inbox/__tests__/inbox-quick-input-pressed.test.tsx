// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { InboxQuickInput } from "@/features/creation/inbox/components/InboxQuickInput";

// BD-170：快捷分类胶囊的选中态此前只有 bg-copper 底色，读屏器分不出当前选的是哪一类。
afterEach(() => cleanup());

function renderQuickInput() {
  return render(
    <InboxQuickInput
      projects={[]}
      targetProjectId=""
      onTargetProjectChange={vi.fn()}
      onCreateNew={vi.fn()}
    />
  );
}

describe("收件箱快捷录入 · 分类胶囊暴露选中态（BD-170）", () => {
  it("默认选中「札记」，切换后状态跟着走", () => {
    renderQuickInput();
    const note = screen.getByRole("button", { name: "札记" });
    const plot = screen.getByRole("button", { name: "剧情点子" });
    expect(note.getAttribute("aria-pressed")).toBe("true");
    expect(plot.getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(plot);
    expect(plot.getAttribute("aria-pressed")).toBe("true");
    expect(note.getAttribute("aria-pressed")).toBe("false");
  });

  it("分类胶囊仍在带名的分组里", () => {
    renderQuickInput();
    const group = screen.getByRole("group", { name: "快捷分类" });
    expect(group.querySelectorAll("button[aria-pressed]").length).toBe(5);
  });
});
