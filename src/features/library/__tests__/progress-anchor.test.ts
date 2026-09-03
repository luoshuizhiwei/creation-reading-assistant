/**
 * 进度锚定纯函数验证：保存（滚动→字符坐标）与恢复（字符坐标→新布局滚动位）往返、
 * 布局变化下的稳定性、边界（顶端/底端/无锚点）以及当前章判定。
 */
import { describe, it, expect } from "vitest";
import { computeTextAnchor, computeAnchorScrollTop, currentAnchorIdFromSpans, type AnchorSpan } from "../toc/anchor";

// 三个章节的内容坐标系布局：章顶 0 / 1000 / 2000，内容总高 3000
const spans: AnchorSpan[] = [
  { id: "c0", top: 0, title: "第一章", charStart: 0, charEnd: 800 },
  { id: "c1", top: 1000, title: "第二章", charStart: 800, charEnd: 1800 },
  { id: "c2", top: 2000, title: "第三章", charStart: 1800, charEnd: 2600 }
];

const SCROLL_HEIGHT = 3000;
const CLIENT_HEIGHT = 600;
const CONTENT_LENGTH = 2600;

describe("computeTextAnchor (save)", () => {
  it("maps scrollTop inside a chapter to chapterRef + charOffset", () => {
    const anchor = computeTextAnchor(spans, 1500, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    expect(anchor.chapterRef).toBe("c1");
    expect(anchor.headingPath).toEqual(["第二章"]);
    // 章内比例 0.5 → 字符 800 + 0.5*1000 = 1300
    expect(anchor.charOffset).toBe(1300);
  });

  it("returns global fraction without chapterRef above the first anchor", () => {
    const lateFirst: AnchorSpan[] = [{ id: "c0", top: 200, title: "第一章", charStart: 0, charEnd: 800 }, ...spans.slice(1)];
    const anchor = computeTextAnchor(lateFirst, 0, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    expect(anchor.chapterRef).toBeUndefined();
    expect(anchor.charOffset).toBe(0);
  });

  it("clamps to the end of content", () => {
    const anchor = computeTextAnchor(spans, 3000, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    expect(anchor.chapterRef).toBe("c2");
    expect(anchor.charOffset).toBeLessThanOrEqual(CONTENT_LENGTH);
  });
});

describe("computeAnchorScrollTop (restore)", () => {
  it("round-trips save→restore within the same layout", () => {
    const scrollTop = 1500;
    const anchor = computeTextAnchor(spans, scrollTop, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    const restored = computeAnchorScrollTop(spans, anchor, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    expect(restored).toBeCloseTo(scrollTop, -1); // ±10px 内
  });

  it("stays proportionally correct after layout change (font size grew)", () => {
    const scrollTop = 1500; // 第二章 50% 处
    const anchor = computeTextAnchor(spans, scrollTop, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    // 大字号后章节顶部与总高近似翻倍
    const biggerSpans: AnchorSpan[] = [
      { id: "c0", top: 0, title: "第一章", charStart: 0, charEnd: 800 },
      { id: "c1", top: 2000, title: "第二章", charStart: 800, charEnd: 1800 },
      { id: "c2", top: 4000, title: "第三章", charStart: 1800, charEnd: 2600 }
    ];
    const restored = computeAnchorScrollTop(biggerSpans, anchor, 6000, CLIENT_HEIGHT, CONTENT_LENGTH);
    // 仍应落在第二章内，且接近其 50% 位置（2000 + 0.5*2000 = 3000）
    expect(restored).toBeGreaterThan(2000);
    expect(restored).toBeLessThan(4000);
    expect(Math.abs(restored - 3000)).toBeLessThan(60);
  });

  it("falls back to scrollTop semantics via global fraction when chapter anchor is missing", () => {
    const restored = computeAnchorScrollTop([], { charOffset: CONTENT_LENGTH / 2 }, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH);
    expect(restored).toBe(Math.round((SCROLL_HEIGHT - CLIENT_HEIGHT) / 2));
  });

  it("returns undefined without any usable anchor", () => {
    expect(computeAnchorScrollTop(spans, {}, SCROLL_HEIGHT, CLIENT_HEIGHT, CONTENT_LENGTH)).toBeUndefined();
  });
});

describe("currentAnchorIdFromSpans", () => {
  it("picks the last anchor above scrollTop + peek", () => {
    expect(currentAnchorIdFromSpans(spans, 0)).toBe("c0");
    expect(currentAnchorIdFromSpans(spans, 1200)).toBe("c1");
    // 40px 阈值：章顶进入视口顶 40px 内即视为当前章（与目录高亮判定一致）
    expect(currentAnchorIdFromSpans(spans, 980)).toBe("c1");
    expect(currentAnchorIdFromSpans(spans, 900)).toBe("c0");
  });
});
