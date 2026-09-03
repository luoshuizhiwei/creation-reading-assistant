/**
 * 共享目录模块单测：树派生（含层级钳制）、展开可见行、祖先路径、搜索过滤、
 * EPUB 当前项匹配（fragment 优先）与滚动锚点选择。
 */
import { describe, it, expect } from "vitest";
import { buildTocTree, flattenVisibleTree, collectAncestorIds, collectParentIds, treeMaxDepth, type TocEntry } from "../toc/tree";
import { filterTocEntries } from "../toc/filter";
import { findCurrentTocItem, lastAnchorBeforeThreshold, normalizeEpubHref } from "../toc/current";
import type { EpubTocItem } from "@/types/library";

function entriesOf(levels: number[]): TocEntry[] {
  return levels.map((level, index) => ({ id: `e${index}`, label: `条目 ${index}`, level }));
}

describe("buildTocTree", () => {
  it("builds a hierarchy from flat entries", () => {
    const tree = buildTocTree(entriesOf([1, 2, 3, 2, 1, 2]));
    expect(tree.map((n) => n.entry.id)).toEqual(["e0", "e4"]);
    expect(tree[0].children.map((n) => n.entry.id)).toEqual(["e1", "e3"]);
    expect(tree[0].children[0].children.map((n) => n.entry.id)).toEqual(["e2"]);
  });

  it("clamps level jumps to previous + 1", () => {
    const tree = buildTocTree(entriesOf([1, 4]));
    expect(tree[0].children[0].entry.level).toBe(2);
  });

  it("treats level 0 as top level (sibling of level 1)", () => {
    const tree = buildTocTree(entriesOf([0, 1]));
    expect(tree).toHaveLength(2);
    expect(tree.every((n) => n.entry.level === 1)).toBe(true);
  });

  it("computes max depth", () => {
    expect(treeMaxDepth(buildTocTree(entriesOf([1, 2, 3])))).toBe(3);
    expect(treeMaxDepth(buildTocTree(entriesOf([1, 1])))).toBe(1);
  });
});

describe("flattenVisibleTree / ancestors", () => {
  const tree = buildTocTree(entriesOf([1, 2, 3, 2]));

  it("shows only depth-1 rows when nothing expanded", () => {
    const rows = flattenVisibleTree(tree, new Set());
    expect(rows.map((r) => r.node.entry.id)).toEqual(["e0"]);
    expect(rows[0].hasChildren).toBe(true);
    expect(rows[0].expanded).toBe(false);
  });

  it("shows nested rows along expanded path", () => {
    const rows = flattenVisibleTree(tree, new Set(["e0", "e1"]));
    expect(rows.map((r) => r.node.entry.id)).toEqual(["e0", "e1", "e2", "e3"]);
  });

  it("collects ancestor path for expand-to-current", () => {
    expect(collectAncestorIds(tree, "e2")).toEqual(["e0", "e1"]);
    expect(collectAncestorIds(tree, "e0")).toEqual([]);
    expect(collectAncestorIds(tree, "missing")).toEqual([]);
  });

  it("collects all parent ids for collapse-all", () => {
    expect(collectParentIds(tree)).toEqual(["e0", "e1"]);
  });
});

describe("filterTocEntries", () => {
  const entries = [
    { id: "a", label: "第一章 起点", level: 1 },
    { id: "b", label: "第二章 风起", level: 1 }
  ];

  it("returns all entries for empty query", () => {
    expect(filterTocEntries(entries, "  ")).toHaveLength(2);
  });

  it("matches case-insensitively", () => {
    expect(filterTocEntries(entries, "风起").map((e) => e.id)).toEqual(["b"]);
    expect(filterTocEntries([{ id: "c", label: "Chapter One", level: 1 }], "chapter")).toEqual([{ id: "c", label: "Chapter One", level: 1 }]);
  });
});

describe("findCurrentTocItem", () => {
  const toc: EpubTocItem[] = [
    { id: "t0", label: "卷一", href: "chapter1.xhtml" },
    { id: "t1", label: "第一节", href: "chapter1.xhtml#sec-1" },
    { id: "t2", label: "第二节", href: "chapter1.xhtml#sec-2" },
    { id: "t3", label: "卷二", href: "chapter2.xhtml" }
  ];

  it("prefers exact href-with-fragment match over first normalized match", () => {
    expect(findCurrentTocItem(toc, "chapter1.xhtml#sec-2")?.id).toBe("t2");
    expect(findCurrentTocItem(toc, "chapter1.xhtml#sec-1")?.id).toBe("t1");
  });

  it("falls back to first normalized match when no fragment matches", () => {
    expect(findCurrentTocItem(toc, "chapter1.xhtml#unknown")?.id).toBe("t0");
    expect(findCurrentTocItem(toc, "chapter1.xhtml")?.id).toBe("t0");
  });

  it("returns undefined without href or on miss", () => {
    expect(findCurrentTocItem(toc, undefined)).toBeUndefined();
    expect(findCurrentTocItem(toc, "missing.xhtml")).toBeUndefined();
  });

  it("normalizeEpubHref strips fragments", () => {
    expect(normalizeEpubHref("a.xhtml#b")).toBe("a.xhtml");
    expect(normalizeEpubHref(undefined)).toBeUndefined();
  });
});

describe("lastAnchorBeforeThreshold", () => {
  it("returns the last anchor at or above the threshold", () => {
    const offsets = [
      { id: "a", offsetTop: 0 },
      { id: "b", offsetTop: 500 },
      { id: "c", offsetTop: 1200 }
    ];
    expect(lastAnchorBeforeThreshold(offsets, 40)).toBe("a");
    expect(lastAnchorBeforeThreshold(offsets, 600)).toBe("b");
    expect(lastAnchorBeforeThreshold(offsets, 5000)).toBe("c");
  });

  it("returns undefined when all anchors are below the threshold", () => {
    expect(lastAnchorBeforeThreshold([{ id: "a", offsetTop: 300 }], 40)).toBeUndefined();
    expect(lastAnchorBeforeThreshold([], 40)).toBeUndefined();
  });
});
