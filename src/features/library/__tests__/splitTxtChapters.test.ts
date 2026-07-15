/**
 * Unit tests for splitTxtChapters — pure logic verification.
 *
 * Run with vitest:  npx vitest run src/features/library/__tests__/splitTxtChapters.test.ts
 * (requires `npm install -D vitest` first)
 */
import { describe, it, expect } from "vitest";
import { splitTxtChapters } from "../ReaderPage";
import type { TxtChapter } from "../ReaderPage";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function titles(chapters: TxtChapter[]): string[] {
  return chapters.map((c) => c.title);
}

function body(content: string, chapter: TxtChapter): string {
  return content.slice(chapter.contentStart, chapter.endIndex);
}

// ---------------------------------------------------------------------------
// 1. Standard chapter patterns
// ---------------------------------------------------------------------------

describe("splitTxtChapters — standard patterns", () => {
  it("matches 第X章 with Chinese number", () => {
    const text = "第一章 初入江湖\n正文内容";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    expect(ch[0].title).toBe("第一章 初入江湖");
  });

  it("matches 第X章 with Arabic digits", () => {
    const text = "第100章 最终决战\n结局";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    expect(ch[0].title).toBe("第100章 最终决战");
  });

  it("matches 第X回", () => {
    const text = "第一回 楔子\n内容";
    const ch = splitTxtChapters(text);
    expect(ch[0].title).toContain("第一回");
  });

  it("matches 第X节", () => {
    const text = "第三节 课堂\n内容";
    const ch = splitTxtChapters(text);
    expect(ch[0].title).toContain("第三节");
  });

  it("matches 第X卷/部/集/篇/幕", () => {
    for (const kw of ["卷", "部", "集", "篇", "幕"]) {
      const text = `第二${kw} 新篇章\n内容`;
      const ch = splitTxtChapters(text);
      expect(ch.length).toBeGreaterThanOrEqual(1);
      expect(ch[0].title).toContain(`第二${kw}`);
    }
  });

  it("matches 两 as digit (第二百二十章)", () => {
    const text = "第二百二十章 风云\n内容";
    const ch = splitTxtChapters(text);
    expect(ch[0].title).toContain("第二百二十章");
  });

  it("matches uppercase Chinese digits (壹贰叁)", () => {
    const text = "第壹章 开始\n内容";
    const ch = splitTxtChapters(text);
    expect(ch[0].title).toContain("第壹章");
  });
});

// ---------------------------------------------------------------------------
// 2. Special keyword headings
// ---------------------------------------------------------------------------

describe("splitTxtChapters — special keywords", () => {
  for (const kw of ["序", "序言", "前言", "后记", "附录", "引子", "楔子", "尾声", "番外", "正文"]) {
    it(`matches standalone "${kw}"`, () => {
      const text = `${kw}\n一些内容在这里`;
      const ch = splitTxtChapters(text);
      expect(ch.length).toBe(1);
      expect(ch[0].title).toBe(kw);
    });

    it(`matches "${kw}" with subtitle after colon`, () => {
      const text = `${kw}：那年夏天\n回忆录内容`;
      const ch = splitTxtChapters(text);
      expect(ch.length).toBe(1);
      expect(ch[0].title).toContain(kw);
    });

    it(`matches "${kw}" with subtitle after space`, () => {
      const text = `${kw} 副标题\n内容`;
      const ch = splitTxtChapters(text);
      expect(ch.length).toBe(1);
      expect(ch[0].title).toContain(kw);
    });
  }

  it("matches English keywords case-insensitively", () => {
    const text = "Prologue\nOnce upon a time\nEpilogue\nThe end";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    expect(ch[0].title).toBe("Prologue");
    expect(ch[1].title).toBe("Epilogue");
  });
});

// ---------------------------------------------------------------------------
// 3. False positive avoidance
// ---------------------------------------------------------------------------

describe("splitTxtChapters — false positive avoidance", () => {
  it("does NOT match '序位骑士冲了过来'", () => {
    const text = "序位骑士冲了过来\n战斗开始了";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(0);
  });

  it("does NOT match '番外的人来到了城里'", () => {
    const text = "番外的人来到了城里\n他们在酒馆歇脚";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(0);
  });

  it("does NOT match '尾声已经响起' in a sentence", () => {
    const text = "尾声已经响起\n故事结束";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(0);
  });

  it("does NOT match '正文内容很长很长' as a heading", () => {
    const text = "正文内容很长很长很长很长";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// 4. Reversed volume format
// ---------------------------------------------------------------------------

describe("splitTxtChapters — reversed volume format", () => {
  it("matches 卷一 standalone", () => {
    const text = "卷一\n起始内容";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    expect(ch[0].title).toBe("卷一");
  });

  it("matches 卷二 风起云涌", () => {
    const text = "卷二 风起云涌\n故事继续";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    expect(ch[0].title).toContain("卷二");
  });

  it("matches multiple volumes: 卷一 then 卷二", () => {
    const text = "卷一 起源\n第一章的内容\n\n卷二 征途\n第二章的内容";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    expect(ch[0].title).toContain("卷一");
    expect(ch[1].title).toContain("卷二");
  });
});

// ---------------------------------------------------------------------------
// 5. contentStart and endIndex correctness
// ---------------------------------------------------------------------------

describe("splitTxtChapters — contentStart / endIndex", () => {
  it("contentStart skips title line (\\n)", () => {
    const text = "第一章 开始\n这是正文第一段\n\n第二段";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    const b = body(text, ch[0]);
    expect(b).not.toContain("第一章");
    expect(b).toContain("这是正文第一段");
  });

  it("contentStart skips title line (\\r\\n)", () => {
    const text = "第一章 开始\r\n这是正文第一段\r\n\r\n第二段";
    const ch = splitTxtChapters(text);
    const b = body(text, ch[0]);
    expect(b).not.toContain("第一章");
    expect(b).toContain("这是正文第一段");
  });

  it("endIndex trims trailing blank lines", () => {
    const text = "第一章\n正文A\n\n\n第二章\n正文B";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    const bodyA = body(text, ch[0]);
    expect(bodyA).not.toMatch(/\n\n$/);
    expect(bodyA).toContain("正文A");
  });

  it("handles file without trailing newline", () => {
    const text = "第一章 开始\n正文内容没有尾部换行";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    const b = body(text, ch[0]);
    expect(b).toBe("正文内容没有尾部换行");
  });

  it("handles title on last line with no body", () => {
    const text = "第一章 正文\n第二章 大结局";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    expect(body(text, ch[1])).toBe("");
  });

  it("handles consecutive headings without body", () => {
    const text = "第一章\n第二章 新的开始\n正文内容";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    expect(body(text, ch[0])).toBe("");
    expect(body(text, ch[1])).toContain("正文内容");
  });
});

// ---------------------------------------------------------------------------
// 6. Prologue (content before first heading)
// ---------------------------------------------------------------------------

describe("splitTxtChapters — prologue content", () => {
  it("preserves content before first chapter heading", () => {
    const text = "本书讲述了一个少年的故事...\n\n第一章 少年出发\n正文内容";
    const ch = splitTxtChapters(text);
    // Should have prologue + chapter
    expect(ch.length).toBe(2);
    expect(ch[0].title).toBe("");
    const prologue = body(text, ch[0]);
    expect(prologue).toContain("本书讲述了一个少年的故事");
  });

  it("does not add prologue when file starts with heading", () => {
    const text = "第一章 开始\n内容";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(1);
    expect(ch[0].title).not.toBe("");
  });

  it("prologue endIndex trims trailing blank lines", () => {
    const text = "简介内容\n\n\n第一章 开始\n正文";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(2);
    const prologue = body(text, ch[0]);
    expect(prologue).toBe("简介内容");
  });
});

// ---------------------------------------------------------------------------
// 7. Mixed patterns
// ---------------------------------------------------------------------------

describe("splitTxtChapters — mixed chapter patterns", () => {
  it("mixes 第X章 and special keywords", () => {
    const text = "引子\n楔子内容\n\n第一章 开始\n正文\n\n尾声\n结局";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(3);
    expect(titles(ch)).toEqual(["引子", "第一章 开始", "尾声"]);
  });

  it("mixes 卷X and 第X章", () => {
    const text = "卷一 起源\n\n第一章 开始\n正文\n\n卷二 征途\n\n第二章 继续\n更多正文";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(4);
    expect(titles(ch)).toEqual(["卷一 起源", "第一章 开始", "卷二 征途", "第二章 继续"]);
  });
});

// ---------------------------------------------------------------------------
// 8. Single chapter / no chapter (fallback)
// ---------------------------------------------------------------------------

describe("splitTxtChapters — single / no chapter", () => {
  it("returns 1 chapter for single heading", () => {
    const text = "正文\n这是一本没有章节的书";
    const ch = splitTxtChapters(text);
    // "正文" matches as special keyword
    expect(ch.length).toBe(1);
  });

  it("returns 0 chapters when no headings found", () => {
    const text = "这是一段普通的文本\n没有任何章节标题\n只有段落";
    const ch = splitTxtChapters(text);
    expect(ch.length).toBe(0);
  });

  it("returns empty array for empty string", () => {
    const ch = splitTxtChapters("");
    expect(ch.length).toBe(0);
  });
});
