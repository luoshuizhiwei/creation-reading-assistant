/**
 * TXT 章节识别跨平台语料验证（移植自 Android TxtChapterDetectorPlatformCorpusTest）。
 * 起点 / 晋江 / 番茄 / 刺猬猫（轻小说）/ 传统章回体 / 英文标题必须命中；
 * 正文形态必须不命中。防止后续收紧正则时悄悄丢掉某类平台。
 */
import { describe, it, expect } from "vitest";
import { isTxtChapterTitle, splitTxtChapters, MIN_SNIFF_TEXT_CHARS, MIN_AVG_CHAPTER_CHARS } from "../toc/txt-chapters";

const qidian = [
  "第一章 山边小村",
  "第1章 穿越",
  "第一千三百零五章 大战落幕",
  "第1204章 风起于青萍之末",
  "第001章 起点",
  "第零章 序",
  "序章",
  "序章：十年之前",
  "楔子",
  "第一章：初见",
  "第1章：初见",
  "第一卷 少年游",
  "第一卷：风起",
  "第1卷 第1章 起",
  "【第一章】风起",
  "第一章 标题（修）",
  "上架感言",
  "完本感言",
  "新书感言",
];

const jinjiangFanqie = [
  "第2章(vip)",
  "第3章（VIP）",
  "第12章 相遇（求收藏）",
  "第１章 全角数字也不怕",
  "第两百章 双喜临门",
  "第壹佰章 仿古数字",
];

const ciweimao = [
  "第1话 转生之后的事",
  "第一话",
  "最终话",
  "最终话 明天的日记",
  "最终章",
  "最終話",
  "间章",
  "间章 某个平常的午后",
  "幕间",
  "幕间 王都的钟声",
  "第2幕 开演",
  "第3折 惊变",
  "番外",
  "番外一",
  "番外：暑期特别篇",
  "尾声",
  "终章",
];

const chaptered = [
  "第一回 宴桃园豪杰三结义 斩黄巾英雄首立功",
  "第廿三回 小旋风柴进 Door客",
  "卷一 江湖夜雨",
  "卷之十二 大结局前夜",
  "上部 风云际会",
  "中部 暗流涌动",
  "下册 尘埃落定",
  "第四节 最后一课",
];

const english = ["Chapter 1 The Beginning", "CHAPTER IV", "chapter 12"];

const bodyLines = [
  "楔子钉进了木头缝里",
  "序幕拉开了",
  "最终章节里写到主角黑化了",
  "间章的两种写法都值得学",
  "上架感言写得情真意切",
  "第一节课 已经开始十分钟了",
  "这一部分讲完再回家",
  "部队在凌晨集结",
  "1. 他说今天天气很好",
  "一、那天的雨下了很久",
  "第两百章之后的内容更精彩",
  "话说天下大势，分久必合",
];

describe("TXT chapter corpus — platform titles", () => {
  it.each([
    ["qidian", qidian],
    ["jinjiang/fanqie", jinjiangFanqie],
    ["ciweimao/light-novel", ciweimao],
    ["classic chaptered", chaptered],
    ["english", english]
  ])("%s titles are all recognized", (_name, titles) => {
    for (const title of titles) {
      expect(isTxtChapterTitle(title), `标题未识别：${title}`).toBe(true);
    }
  });
});

describe("TXT chapter corpus — body lines are never titles", () => {
  it.each(bodyLines.map((line) => [line]))("rejects: %s", (line) => {
    expect(isTxtChapterTitle(line as string), `正文行被误判为标题：${line}`).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// 自动嗅探（移植自 Android TxtChapterDetectorAutoSniffTest 的六类场景）
// ---------------------------------------------------------------------------

const BODY_SENTENCE = "山间的风吹过树梢，远处传来溪水声响，他停下脚步望向山谷深处。";

function numberedNovel(titleOf: (i: number) => string, chapters: number, bodyChars: number): string {
  const parts: string[] = [];
  const body = BODY_SENTENCE.repeat(Math.ceil(bodyChars / BODY_SENTENCE.length));
  for (let i = 1; i <= chapters; i++) {
    parts.push(`${titleOf(i)}\n${body}`);
  }
  return parts.join("\n");
}

describe("splitTxtChapters — auto sniff for numbered-style books", () => {
  it("sniffs num-dot style (1、标题)", () => {
    const text = numberedNovel((i) => `${i}、暗流涌动`, 12, 400);
    const chapters = splitTxtChapters(text);
    expect(chapters.length).toBe(12);
    expect(chapters[0].title).toBe("1、暗流涌动");
  });

  it("sniffs cn-num-dot style (一、标题)", () => {
    const cn = ["一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"];
    const text = numberedNovel((i) => `${cn[i - 1]}、夜雨初晴`, 12, 400);
    const chapters = splitTxtChapters(text);
    expect(chapters.length).toBe(12);
    expect(chapters[0].title).toBe("一、夜雨初晴");
  });

  it("sniffs bracketed style (【1】标题)", () => {
    const text = numberedNovel((i) => `【${i}】旧信`, 10, 400);
    const chapters = splitTxtChapters(text);
    expect(chapters.length).toBe(10);
    expect(chapters[0].title).toBe("【1】旧信");
  });

  it("discards a false-positive storm and falls back to no toc", () => {
    // builtin 命中大量短"章"但平均章长 < 300 → 整体作废；正文无编号样式行 → 嗅探失败 → 空目录
    const text = numberedNovel((i) => `第一章 标题${i}`, 40, 70);
    expect(text.length).toBeGreaterThanOrEqual(MIN_SNIFF_TEXT_CHARS);
    expect(splitTxtChapters(text)).toEqual([]);
  });

  it("does not disturb a standard novel (第X章 with proper chapter length)", () => {
    const text = numberedNovel((i) => `第${i}章 烽火连城`, 10, 600);
    const chapters = splitTxtChapters(text);
    expect(chapters.length).toBe(10);
    expect(chapters[0].title).toBe("第1章 烽火连城");
  });

  it("does not sniff short texts", () => {
    const text = numberedNovel((i) => `${i}、短篇`, 5, 300);
    expect(text.length).toBeLessThan(MIN_SNIFF_TEXT_CHARS);
    expect(splitTxtChapters(text)).toEqual([]);
  });

  it("keeps small-text behavior untouched by density guard", () => {
    const text = "卷一 起源\n第一章的内容\n\n卷二 征途\n第二章的内容";
    const chapters = splitTxtChapters(text);
    expect(chapters.map((c) => c.title)).toEqual(["卷一 起源", "卷二 征途"]);
    expect(MIN_AVG_CHAPTER_CHARS).toBe(300);
  });
});
