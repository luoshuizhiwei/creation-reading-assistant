// @vitest-environment jsdom
/**
 * BD-119 回归护栏（渲染级）：目录（TOC）读态行文字用不透明 paper-muted，
 * 已读由行尾 Check 图标 + title 表达，而非低 alpha 压暗。源码级正则断言见
 * 同目录 toc-read-contrast-source.test.ts（node 环境，import.meta.url 才是 file: 协议）。
 */
import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TocList } from "../TocList";
import type { TocEntry } from "../tree";

const entries: TocEntry[] = [
  { id: "c1", label: "第一章 序幕", level: 1 },
  { id: "c2", label: "第二章 雨夜", level: 1 },
];

describe("TocList 读态文字对比度（渲染）", () => {
  it("读态行文字为不透明 paper-muted，无斜杠透明度，且靠 Check+title 标记已读", () => {
    render(
      <TocList
        entries={entries}
        onJump={() => {}}
        emptyText="暂无目录"
        readIds={new Set(["c1"])}
      />,
    );
    const readRow = screen.getByRole("button", { name: /第一章/ });
    expect(readRow.className).not.toMatch(/text-paper-muted\/\d/);
    expect(readRow.className).toMatch(/(^|\s)text-paper-muted(\s|$)/);
    expect(readRow.getAttribute("title")).toContain("已读");
    // 未读行不应带已读 title。
    const unreadRow = screen.getByRole("button", { name: /第二章/ });
    expect(unreadRow.getAttribute("title")).not.toContain("已读");
  });
});
