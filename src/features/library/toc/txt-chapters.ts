/**
 * 中文 TXT 章节识别（与 Android 端 TxtChapterDetector 同源的 TypeScript 移植）。
 *
 * 设计原则与 Android 端一致：宁可少认，不可乱认。
 * - 只认有明确章节词的形态（第X章/卷X/序章/Chapter N），不认裸数字开头；
 * - 标题必须独占一行且足够短（MAX_TITLE_LENGTH）；
 * - 强单位（章/节/回/卷…）可直连副标题但带负向断言（节课/回合/部分/集合…是正文词）；
 * - 弱单位（话/场/幕/折）与具名专名（序章/楔子/番外…）后面必须是行尾、编号或分隔符；
 * - 长文本识别结果过密（平均章节 < MIN_AVG_CHAPTER_CHARS）判定为误报风暴整体作废，
 *   并按编号样式候选自动嗅探（晋江/豆瓣/盐选系的「1、」「一、」「【1】」「纯数字」行），
 *   全部不过则维持无目录。
 *
 * 与 Android 端的差异：本模块面向桌面端滚动阅读器，返回值沿用既有 TxtChapter 契约
 * （startIndex/contentStart/endIndex + 空标题序章 + 「正文」降级），识别不到时返回
 * 空数组而不是「全文」单章——桌面 UI 以 `chapters.length > 1` 决定是否展示目录。
 */

export interface TxtChapter {
  title: string;
  startIndex: number;
  endIndex: number;
  contentStart: number;
}

/** 章节标题行的长度上限。超过这个长度的行是正文，不是标题。 */
export const MAX_TITLE_LENGTH = 40;

/** 识别结果的平均章节长度低于此值，判定为误报风暴，整体作废。 */
export const MIN_AVG_CHAPTER_CHARS = 300;

/** 正文短于此值不启用密度守卫与自动嗅探：短文本（样本/片段/测试用例）目录没有意义。 */
export const MIN_SNIFF_TEXT_CHARS = 3000;

/** 数字：阿拉伯、全角、汉字大小写、廿/卅（章回体二十/三十缩写）。 */
const NUM = "[0-9０-９零〇○一二三四五六七八九十百千万两廿卅壹贰叁肆伍陆柒捌玖拾佰仟]";

/** 行内空白。正则的 \s 不含全角空格（U+3000），必须显式列出。 */
const WS = "[ \\t　]";

/** 标题常见的左括号前缀（【第一章】风起）。不收圆括号，避免吃进正文括注。 */
const OPEN = "[【〔〖「『〈［\\[]";

/**
 * 专名/弱单位与副标题之间允许的分隔符：只能是空白或标点，绝不能是汉字或数字——
 * 这是挡住「序章节奏太慢了」「楔子钉进了木头缝里」这类误报的关键。
 */
const SEP =
  "[ \\t　,，.．。、:：;；·・\\-—―－_~～!！?？|｜(（)）\\[\\]【】〔〕〖〗「」『』〈〉《》\"“”'‘’]";

/**
 * 强单位：后面可直接跟副标题。负向断言来自 legado：
 * 节课/回合/回来/回事/回去/部分/部赛/部游/部队/篇张/集合/集和/册封 都是正文里的词。
 */
const STRONG_UNIT = "(?:[章節]|节(?!课)|回(?![合来事去])|卷|部(?![分赛游队])|篇(?!张)|集(?![合和])|册(?!封))";

/** 弱单位：话音/话说/场面/幕后……与下一字成词太常见，副标题前必须有分隔符。 */
const WEAK_UNIT = "[话話场場幕折]";

// 1. 第X章 / 第 1 节 / 第一百二十三回 / 第壹卷 / 第３册，可带「正文」前缀或左括号前缀。
//    副标题负向断言：紧跟「之/的」的是正文指代（第两百章之后的内容 / 第三章的约定），
//    真实章节标题不会用「的/之」开头。
const P_STRONG = new RegExp(
  `^${OPEN}?${WS}*(?:正文${WS}{0,4})?第${WS}*${NUM}{1,12}${WS}*${STRONG_UNIT}(?!${WS}*[之的])[^\\n]{0,30}$`
);
// 2. 第X话 / 第X场 / 第X幕 / 第X折：弱单位，副标题前必须有分隔符
const P_WEAK = new RegExp(
  `^${OPEN}?${WS}*(?:正文${WS}{0,4})?第${WS}*${NUM}{1,12}${WS}*${WEAK_UNIT}(?:${SEP}[^\\n]{0,30})?$`
);
// 3. 卷一 / 卷之十二 / 部X / 篇X / 册X 的省略写法（reversedVolumePattern 语义）
const P_REVERSED_VOLUME = new RegExp(
  `^${OPEN}?${WS}*[卷部篇册]${WS}*之?${WS}*${NUM}{1,12}(?:${SEP}[^\\n]{0,30})?$`
);
// 4. 上卷 / 中部 / 下册 分组
const P_POSITION_GROUP = new RegExp(`^${OPEN}?${WS}*[上中下]${WS}*[卷部篇册](?:${SEP}[^\\n]{0,30})?$`);
// 5. 具名特殊章节。可带编号（番外一/番外篇二），副标题前必须有分隔符。
//    不收「结局/大结局」：独立成行的「结局」更像叙事内容（Android 端同表有它，
//    桌面语料回归判定为误报；「第X章 大结局」形态已由强单位模式覆盖）。
const P_NAMED = new RegExp(
  `^${OPEN}?${WS}*(?:序章|序言|序幕|自序|楔子|前言|引子|引言|后记|後記|尾声|尾聲|终章|終章|终幕|終幕|番外篇|番外|外传|外傳|附录|附錄|正文|序)(?:${WS}*${NUM}{1,8})?(?:${SEP}[^\\n]{0,30})?$`
);
// 6. 平台专属章型（起点感言 / 刺猬猫轻小说系），与具名同等弱单位待遇
const P_PLATFORM = new RegExp(
  `^${OPEN}?${WS}*(?:最终章|最終章|最终话|最終話|末章|末話|间章|間章|幕间|幕間|上架感言|完本感言|完结感言|新书感言)(?:${WS}*${NUM}{1,8})?(?:${SEP}[^\\n]{0,30})?$`
);
// 7. Chapter 1 / CHAPTER IV
const P_ENGLISH_CHAPTER = /^chapter\s+[0-9ivxlcdm]{1,12}\b[^\n]{0,30}$/i;
// 8. 英文具名（Prologue / Epilogue / Preface…），\b 防止吃进 prologues 等派生词
const P_ENGLISH_NAMED = /^(?:prologue|epilogue|preface|introduction|afterword)\b[^\n]{0,30}$/i;

const BUILTIN_PATTERNS: RegExp[] = [
  P_STRONG,
  P_WEAK,
  P_REVERSED_VOLUME,
  P_POSITION_GROUP,
  P_NAMED,
  P_PLATFORM,
  P_ENGLISH_CHAPTER,
  P_ENGLISH_NAMED,
];

/**
 * builtin 兜底失败后的自动嗅探顺序（编号样式目录）。每个候选都要过密度验证
 * （≥3 章 + 平均章长 ≥ MIN_AVG_CHAPTER_CHARS），全部不过就维持无目录。
 */
const SNIFF_RULE_ORDER = ["num-dot", "cn-num-dot", "bracketed", "num-bare"] as const;

const SNIFF_PATTERNS: Record<(typeof SNIFF_RULE_ORDER)[number], RegExp[]> = {
  "num-dot": [/^[ \t　]{0,4}[0-9０-９]{1,4}\s*[.、．:：,，]\s*(?![0-9０-９])\S.{0,29}$/],
  "cn-num-dot": [/^[ \t　]{0,4}[零〇○一二三四五六七八九十百千两]{1,8}\s*[、.．]\s*\S.{0,29}$/],
  bracketed: [
    new RegExp(
      `^[ \\t　]{0,4}[【〔\\[（(]\\s*(?:第?\\s*${NUM}{1,12}\\s*[章节回卷部篇]?|[0-9０-９]{1,4})\\s*[】〕\\]）)]\\s*.{0,30}$`
    ),
  ],
  "num-bare": [/^[ \t　]{0,4}[0-9０-９]{1,4}[ \t　]*$/],
};

/** 单行是否构成章节标题（builtin 标准四类）。抽出便于单测与语料回归。 */
export function isTxtChapterTitle(line: string): boolean {
  const t = line.trim();
  if (!t || t.length > MAX_TITLE_LENGTH) return false;
  return BUILTIN_PATTERNS.some((pattern) => pattern.test(t));
}

export function isTxtChapterTitleByRule(line: string, ruleId: (typeof SNIFF_RULE_ORDER)[number]): boolean {
  const t = line.trim();
  if (!t || t.length > MAX_TITLE_LENGTH) return false;
  return SNIFF_PATTERNS[ruleId].some((pattern) => pattern.test(t));
}

interface ChapterMark {
  /** 标题行在全文中的起始偏移（原始行首，保证偏移连续、无空洞） */
  start: number;
  title: string;
}

function collectMarks(content: string, isTitle: (line: string) => boolean): ChapterMark[] {
  const marks: ChapterMark[] = [];
  const n = content.length;
  let lineStart = 0;
  let i = 0;
  while (i <= n) {
    if (i === n || content[i] === "\n") {
      const title = content.slice(lineStart, i).trim();
      if (title.length >= 1 && title.length <= MAX_TITLE_LENGTH && isTitle(title)) {
        marks.push({ start: lineStart, title });
      }
      lineStart = i + 1;
    }
    i++;
  }
  return marks;
}

function buildChapters(content: string, marks: ChapterMark[], options: { builtinNormalization?: boolean } = {}): TxtChapter[] {
  if (marks.length === 0) return [];
  const n = content.length;
  const chapters: TxtChapter[] = marks.map((mark) => {
    const nl = content.indexOf("\n", mark.start);
    return {
      title: mark.title,
      startIndex: mark.start,
      contentStart: nl === -1 ? n : nl + 1,
      endIndex: 0,
    };
  });

  // 「正文」是单卷本书的降级章节标记：存在任何显式章节词时按正文处理。
  // 用户手动修正的章节表不做此归一化——用户明确要的章节就是章节。
  const applyBuiltin = options.builtinNormalization !== false;
  let kept = chapters;
  if (applyBuiltin) {
    const hasExplicitHeading = chapters.some((c) => c.title !== "正文");
    if (hasExplicitHeading) kept = chapters.filter((c) => c.title !== "正文");
  }

  // 第一个章节前若有内容（书名页、简介），单独成序章，否则会丢失
  if (kept.length > 0 && kept[0].startIndex > 0) {
    kept.unshift({ title: "", startIndex: 0, contentStart: 0, endIndex: 0 });
  }

  // endIndex 在章节表定形后统一计算（序章插入/正文剔除后仍连续无缝隙）
  for (let i = 0; i < kept.length; i++) {
    const rawEnd = i + 1 < kept.length ? kept[i + 1].startIndex : n;
    let trimmed = rawEnd;
    while (trimmed > kept[i].contentStart) {
      if (content[trimmed - 1] === "\n" || content[trimmed - 1] === "\r") {
        trimmed--;
      } else {
        break;
      }
    }
    kept[i].endIndex = trimmed;
  }
  return kept;
}

/** 密度验证：≥3 章 + 平均章长达标。 */
function densityValid(content: string, chapters: TxtChapter[]): boolean {
  return chapters.length >= 3 && content.length / chapters.length >= MIN_AVG_CHAPTER_CHARS;
}

/**
 * 从整篇 TXT 正文里识别章节。
 *
 * 识别不到（无命中 / 长文本误报风暴且嗅探失败）时返回空数组——调用方以
 * `length > 1` 判断是否展示目录与启用分章渲染。
 */
export function splitTxtChapters(content: string): TxtChapter[] {
  if (!content) return [];
  const marks = collectMarks(content, isTxtChapterTitle);
  let built = buildChapters(content, marks);
  const guarded = content.length >= MIN_SNIFF_TEXT_CHARS;
  if (guarded && built.length > 0 && !densityValid(content, built)) {
    built = [];
  }
  if (built.length > 0) return built;

  // builtin 失败（无命中 / 误报风暴）：长文本先嗅探编号样式目录
  if (guarded) {
    let best: TxtChapter[] = [];
    for (const ruleId of SNIFF_RULE_ORDER) {
      const candidate = buildChapters(content, collectMarks(content, (line) => isTxtChapterTitleByRule(line, ruleId)));
      if (densityValid(content, candidate) && candidate.length > best.length) {
        best = candidate;
      }
    }
    if (best.length > 0) return best;
  }
  return [];
}

/**
 * 用户手动修正的章节表（TxtTocOverrides）→ 章节列表。
 * startIndex 规范化到所在行行首；丢弃非递增/越界的条目；title 原样保留（允许「正文」）。
 * 与启发式识别共用同一组装逻辑（序章、endIndex 收尾裁剪），保证锚点契约一致。
 */
export function chaptersFromOverrides(content: string, chapters: Array<{ title: string; startIndex: number }>): TxtChapter[] {
  if (!content || chapters.length === 0) return [];
  const marks: ChapterMark[] = [];
  let prevLineStart = -1;
  for (const entry of chapters) {
    const startIndex = typeof entry.startIndex === "number" && Number.isInteger(entry.startIndex) ? entry.startIndex : -1;
    if (startIndex < 0 || startIndex >= content.length) continue;
    // 规范化到行首：选择设为章节起点时可能落在行中间；同一行只保留最早一条
    const lineStart = content.lastIndexOf("\n", startIndex - 1) + 1;
    if (lineStart <= prevLineStart) continue;
    prevLineStart = lineStart;
    const nl = content.indexOf("\n", startIndex);
    const titleLine = content.slice(lineStart, nl === -1 ? content.length : nl).trim();
    const title = entry.title.trim() || titleLine || "未命名章节";
    marks.push({ start: lineStart, title: title.slice(0, MAX_TITLE_LENGTH + 20) });
  }
  return buildChapters(content, marks, { builtinNormalization: false });
}
