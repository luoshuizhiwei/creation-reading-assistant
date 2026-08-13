import { describe, it, expect } from "vitest";
import {
  buildExcerptSource,
  capExcerpt,
  EXCERPT_MAX_LENGTH,
  isEmptyExcerpt,
  isDuplicateExcerpt,
  makeExcerptSignature,
  buildLocationLabel
} from "../excerpt-source";
import type { ExcerptSourceSnapshot } from "@/types/library";

const FIXED_NOW = "2026-08-12T10:00:00.000Z";

function fixedNow(): string {
  return FIXED_NOW;
}

// ---------------------------------------------------------------------------
// 1. TXT 摘录来源结构
// ---------------------------------------------------------------------------
describe("buildExcerptSource — TXT 格式结构", () => {
  const ctx = {
    bookId: "txt-1",
    bookTitle: "测试 TXT 资料",
    bookAuthor: "张三",
    format: "txt" as const,
    chapterTitle: "第三章 初见",
    progressPercent: 0.42,
    excerpt: "  这是选中的正文。  ",
    charOffset: 120,
    charLength: 10,
    scrollTop: 480,
    now: fixedNow
  };

  it("保留 bookId / bookTitle / format / author", () => {
    const src = buildExcerptSource(ctx);
    expect(src.bookId).toBe("txt-1");
    expect(src.bookTitle).toBe("测试 TXT 资料");
    expect(src.format).toBe("txt");
    expect(src.bookAuthor).toBe("张三");
  });

  it("保留 charOffset / charLength / scrollTop 定位字段", () => {
    const src = buildExcerptSource(ctx);
    expect(src.charOffset).toBe(120);
    expect(src.charLength).toBe(10);
    expect(src.scrollTop).toBe(480);
  });

  it("不设置 EPUB 专属字段 cfi / href", () => {
    const src = buildExcerptSource(ctx);
    expect(src.cfi).toBeUndefined();
    expect(src.href).toBeUndefined();
  });

  it("excerpt 被 trim 但不截断", () => {
    const src = buildExcerptSource(ctx);
    expect(src.excerpt).toBe("这是选中的正文。");
  });

  it("locationLabel 含章节和进度百分比", () => {
    const src = buildExcerptSource(ctx);
    expect(src.locationLabel).toBe("第三章 初见 · 42% 附近");
  });

  it("createdAt 使用注入的 now", () => {
    const src = buildExcerptSource(ctx);
    expect(src.createdAt).toBe(FIXED_NOW);
  });
});

// ---------------------------------------------------------------------------
// 2. Markdown 摘录来源结构
// ---------------------------------------------------------------------------
describe("buildExcerptSource — Markdown 格式结构", () => {
  const ctx = {
    bookId: "md-1",
    bookTitle: "设计文档",
    format: "md" as const,
    chapterTitle: "## 架构概览",
    progressPercent: 0.15,
    excerpt: "模块划分如下",
    href: "#架构概览",
    scrollTop: 200,
    now: fixedNow
  };

  it("保留 href 但不设置 cfi", () => {
    const src = buildExcerptSource(ctx);
    expect(src.href).toBe("#架构概览");
    expect(src.cfi).toBeUndefined();
  });

  it("format 为 md", () => {
    const src = buildExcerptSource(ctx);
    expect(src.format).toBe("md");
  });

  it("缺少 bookAuthor 时字段为 undefined", () => {
    const src = buildExcerptSource(ctx);
    expect(src.bookAuthor).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// 3. EPUB 摘录来源结构
// ---------------------------------------------------------------------------
describe("buildExcerptSource — EPUB 格式结构", () => {
  const ctx = {
    bookId: "epub-1",
    bookTitle: "EPUB 测试书",
    bookAuthor: "李四",
    format: "epub" as const,
    chapterTitle: "Chapter 2",
    progressPercent: 0.73,
    excerpt: "EPUB selected text",
    href: "chapter2.xhtml",
    cfi: "epubcfi(/6/4[chap2]!/4[body]/2/16)",
    now: fixedNow
  };

  it("保留 cfi 和 href 定位字段", () => {
    const src = buildExcerptSource(ctx);
    expect(src.cfi).toBe("epubcfi(/6/4[chap2]!/4[body]/2/16)");
    expect(src.href).toBe("chapter2.xhtml");
  });

  it("不设置 TXT/MD 专属字段 charOffset / charLength / scrollTop", () => {
    const src = buildExcerptSource(ctx);
    expect(src.charOffset).toBeUndefined();
    expect(src.charLength).toBeUndefined();
    expect(src.scrollTop).toBeUndefined();
  });

  it("locationLabel 含章节标题", () => {
    const src = buildExcerptSource(ctx);
    expect(src.locationLabel).toBe("Chapter 2 · 73% 附近");
  });
});

// ---------------------------------------------------------------------------
// 4. 选文为空时给出明确处理
// ---------------------------------------------------------------------------
describe("isEmptyExcerpt — 空选文检测", () => {
  it("空字符串为空", () => {
    expect(isEmptyExcerpt("")).toBe(true);
  });

  it("纯空白为空", () => {
    expect(isEmptyExcerpt("   \n\t  ")).toBe(true);
  });

  it("有实际内容不为空", () => {
    expect(isEmptyExcerpt("  有效内容  ")).toBe(false);
  });
});

describe("buildExcerptSource — 空选文仍归一化（trim 后为空字符串）", () => {
  it("空字符串 trim 后为空", () => {
    const src = buildExcerptSource({
      bookId: "b1", bookTitle: "书", format: "txt", excerpt: "   ",
      now: fixedNow
    });
    expect(src.excerpt).toBe("");
  });
});

describe("capExcerpt — 截断", () => {
  it("短文本不截断", () => {
    expect(capExcerpt("短文本")).toBe("短文本");
  });

  it("超长文本截断到上限", () => {
    const long = "A".repeat(EXCERPT_MAX_LENGTH + 100);
    const capped = capExcerpt(long);
    expect(capped.length).toBe(EXCERPT_MAX_LENGTH);
  });

  it("先 trim 再截断", () => {
    const padded = "  " + "B".repeat(10) + "  ";
    expect(capExcerpt(padded)).toBe("BBBBBBBBBB");
  });
});

// ---------------------------------------------------------------------------
// 5. 重复摘录检测（防双击）
// ---------------------------------------------------------------------------
describe("isDuplicateExcerpt — 重复检测", () => {
  const source: ExcerptSourceSnapshot = {
    bookId: "b1",
    bookTitle: "书",
    format: "txt",
    locationLabel: "1% 附近",
    excerpt: "同一段选文",
    charOffset: 50,
    createdAt: FIXED_NOW
  };

  it("没有上次记录时不视为重复", () => {
    expect(isDuplicateExcerpt(source, undefined)).toBe(false);
  });

  it("签名不同不视为重复", () => {
    const last = { signature: "other|text|0", at: Date.now() };
    expect(isDuplicateExcerpt(source, last)).toBe(false);
  });

  it("签名相同且在窗口内视为重复", () => {
    const sig = makeExcerptSignature(source);
    const last = { signature: sig, at: Date.now() };
    expect(isDuplicateExcerpt(source, last)).toBe(true);
  });

  it("签名相同但超出窗口不视为重复", () => {
    const sig = makeExcerptSignature(source);
    const last = { signature: sig, at: Date.now() - 5_000 };
    expect(isDuplicateExcerpt(source, last, 3_000)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// 6. buildLocationLabel 边界
// ---------------------------------------------------------------------------
describe("buildLocationLabel", () => {
  it("有章节和进度", () => {
    expect(buildLocationLabel("第一章", 0.5)).toBe("第一章 · 50% 附近");
  });

  it("无章节仅进度", () => {
    expect(buildLocationLabel(undefined, 0.3)).toBe("30% 附近");
  });

  it("进度为 undefined 时显示未知进度", () => {
    expect(buildLocationLabel("第二章", undefined)).toBe("第二章 · 未知进度");
  });

  it("无章节无进度", () => {
    expect(buildLocationLabel(undefined, undefined)).toBe("未知进度");
  });
});
