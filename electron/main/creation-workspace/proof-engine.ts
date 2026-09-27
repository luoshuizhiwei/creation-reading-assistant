/**
 * 审校纯函数簇：把场景正文拆成段落并按规则命中问题，全部无状态、不触库。
 *
 * 从 creation-workspace/index.ts 外迁而来（拆分切片 1），行为逐字保持：
 * 这里只放「给定段落文本 → 产出 ProofHit」的纯文本扫描逻辑及其私有辅助，
 * 规则的持久化/分组/忽略键拼装仍留在工作区类的 scanProof 方法里。
 */
import type { CreationDocument, ProofRule } from "./types";
import { CreationWorkspaceError } from "./types";
import { isRecord } from "./workspace-utils";

/**
 * 校对单条命中。
 * `detail` 是该位置的说明，同时作为「场景 × 规则」分组的消息基干：
 * 同一分组内多条命中只保留首条的基干并追加「（共 N 处）」。
 */
interface ProofHit {
  rule: ProofRule;
  /** 段落序号（0 起）；-1 表示该规则以整场为粒度。 */
  paragraphIndex: number;
  /** 命中文本（规范化前）。 */
  matchedText: string;
  snippet: string | null;
  detail: string;
}

export type ProofHits = ProofHit[];

/** 位置键使用的 32 位 FNV-1a 哈希；仅用于生成稳定短键，不承担安全职责。 */
function fnv1a32(text: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

/** 位置键的文本部分：去掉所有空白，避免排版微调导致键漂移。 */
function normalizeProofText(text: string): string {
  return text.replace(/\s+/g, "");
}

/**
 * 稳定位置键：`规则#段落序号#命中文本哈希`。
 * 段落序号让同一文本在场景内的不同段落互不牵连；
 * 忽略记录再叠加 project / scene 维度，因此跨项目、跨场景同样隔离。
 */
export function proofLocationKey(rule: ProofRule, paragraphIndex: number, matchedText: string): string {
  return `${rule}#${paragraphIndex}#${fnv1a32(normalizeProofText(matchedText))}`;
}

/** 生成上下文片段：命中前后各取若干字符。 */
function proofSnippet(text: string, start: number, end: number): string {
  const radius = 8;
  const from = Math.max(0, start - radius);
  const to = Math.min(text.length, end + radius * 2);
  return `${from > 0 ? "…" : ""}${text.slice(from, to)}${to < text.length ? "…" : ""}`;
}

/** 非空子串出现次数（不重叠）。 */
export function countOccurrences(haystack: string, needle: string): number {
  if (!needle) return 0;
  let count = 0;
  let index = haystack.indexOf(needle);
  while (index >= 0) {
    count += 1;
    index = haystack.indexOf(needle, index + needle.length);
  }
  return count;
}

/** 把「段落用 \n 拼接后的偏移」映射回段落序号。 */
function paragraphIndexAt(paragraphs: string[], offset: number): number {
  let cursor = 0;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const length = paragraphs[index]!.length;
    if (offset <= cursor + length) return index;
    cursor += length + 1;
  }
  return paragraphs.length - 1;
}

const HAN_CHARACTER_PATTERN = /\p{Script=Han}/u;
const HAN_ONLY_PATTERN = /^[\p{Script=Han}]+$/u;
/** 疑似错拼只比较 2..6 字的词条，避免长句掩码爆炸。 */
export const PROOF_TYPO_MAX_TERM_LENGTH = 6;
/** 词条在全书出现次数低于该值时不作为错拼基准，抑制一次性噪声。 */
const PROOF_TYPO_MIN_TERM_FREQUENCY = 2;

/** 成对标点：开闭符号对，用于不配对检测。 */
const PROOF_PAIR_PUNCTUATION: Array<[string, string]> = [
  ["「", "」"],
  ["『", "』"],
  ["（", "）"],
  ["《", "》"],
  ["【", "】"],
  ["“", "”"]
];

/** 校对词表条目：一张卡片的主名与别名。 */
export interface ProofDictionaryEntry {
  cardId: string;
  title: string;
  /** 主名 + 别名（去重、去空白、长度 ≥2）。 */
  variants: string[];
}

/** 段落文本：把场景正文按块拆成段落（含 sceneBreak 分隔符）。 */
export function sceneParagraphs(bodyJson: string): string[] {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return [];
  }
  const blocks: string[] = [];
  for (const block of document.content ?? []) {
    if (!isRecord(block)) continue;
    if (block.type === "sceneBreak") {
      blocks.push("");
      continue;
    }
    if (!Array.isArray(block.content)) continue;
    const parts: string[] = [];
    const collect = (nodes: unknown[]): void => {
      for (const node of nodes) {
        if (!isRecord(node)) continue;
        if (node.type === "text" && typeof node.text === "string") parts.push(node.text);
        else if (Array.isArray(node.content)) collect(node.content);
      }
    };
    collect(block.content);
    blocks.push(parts.join(""));
  }
  return blocks;
}

/** 连续重复字：同一汉字连续出现 ≥3 次。 */
export function findRepeatedChars(paragraphs: string[], out: ProofHits): void {
  const repeated = /([\p{Script=Han}])\1{2,}/gu;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    repeated.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = repeated.exec(paragraph)) !== null) {
      out.push({
        rule: "repeatedChar",
        paragraphIndex: index,
        matchedText: match[0],
        snippet: proofSnippet(paragraph, match.index, match.index + match[0].length),
        detail: `连续重复字「${match[0].slice(0, 6)}」`
      });
    }
  }
}

/** 成对标点：括号/引号开闭数量不等（按场景统计，定位到首次出现的段落）。 */
export function findUnbalancedPunctuation(paragraphs: string[], out: ProofHits): void {
  const joined = paragraphs.join("\n");
  for (const [open, close] of PROOF_PAIR_PUNCTUATION) {
    let openCount = 0;
    let closeCount = 0;
    let firstIndex = -1;
    for (let index = 0; index < joined.length; index += 1) {
      const character = joined[index]!;
      if (character === open) {
        if (firstIndex < 0) firstIndex = index;
        openCount += 1;
      } else if (character === close) {
        if (firstIndex < 0) firstIndex = index;
        closeCount += 1;
      }
    }
    if (openCount === closeCount || firstIndex < 0) continue;
    out.push({
      rule: "unbalancedPunctuation",
      paragraphIndex: paragraphIndexAt(paragraphs, firstIndex),
      matchedText: `${open}${close}`,
      snippet: proofSnippet(joined, firstIndex, firstIndex + 1),
      detail: `「${open}${close}」不配对（开 ${openCount} 个、闭 ${closeCount} 个）`
    });
  }
}

/** 异常空格：段首半角空格、连续 2+ 全角空格、半角与全角空格混用（逐段定位）。 */
export function findAbnormalSpacing(paragraphs: string[], out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    if (/^[ ]/.test(paragraph)) {
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: " ",
        snippet: proofSnippet(paragraph, 0, 1),
        detail: "段落以半角空格开头"
      });
    }
    if (/　{2,}/u.test(paragraph)) {
      const position = paragraph.search(/　{2,}/u);
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: "　　",
        snippet: proofSnippet(paragraph, position, position + 2),
        detail: "段落含连续两个以上全角空格"
      });
    }
    if (/[ ]/.test(paragraph) && /　/.test(paragraph)) {
      const position = Math.min(
        paragraph.indexOf(" ") >= 0 ? paragraph.indexOf(" ") : Number.MAX_SAFE_INTEGER,
        paragraph.indexOf("　") >= 0 ? paragraph.indexOf("　") : Number.MAX_SAFE_INTEGER
      );
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: "　",
        snippet: proofSnippet(paragraph, position, position + 1),
        detail: "段落同时出现半角与全角空格"
      });
    }
  }
}

/** 超长段落：单段字符数超过阈值。 */
export function findLongParagraphs(paragraphs: string[], maxChars: number, out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (paragraph.length <= maxChars) continue;
    out.push({
      rule: "longParagraph",
      paragraphIndex: index,
      matchedText: paragraph.slice(0, 40),
      snippet: `…${paragraph.slice(0, 60)}…`,
      detail: `段落 ${paragraph.length} 字符，超过 ${maxChars} 字符`
    });
  }
}

/** 禁用词：子串命中。 */
export function findBannedWords(paragraphs: string[], bannedWords: string[], out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    for (const word of bannedWords) {
      if (!word) continue;
      const position = paragraph.indexOf(word);
      if (position < 0) continue;
      out.push({
        rule: "bannedWord",
        paragraphIndex: index,
        matchedText: word,
        snippet: proofSnippet(paragraph, position, position + word.length),
        detail: `命中禁用词「${word}」`
      });
    }
  }
}

/** 中英混用标点：汉字紧邻半角标点（网页粘贴/输入法残留的高频问题）。 */
export function findMixedPunctuation(paragraphs: string[], out: ProofHits): void {
  // 数字间的半角点（3.5、1,000）不算；只抓汉字直接贴半角标点。
  const mixed = /[\p{Script=Han}][,.!?;:]|[,.!?;:][\p{Script=Han}]/gu;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    mixed.lastIndex = 0;
    const seen = new Set<string>();
    let match: RegExpExecArray | null;
    while ((match = mixed.exec(paragraph)) !== null) {
      if (seen.has(match[0])) continue;
      seen.add(match[0]);
      out.push({
        rule: "mixedPunctuation",
        paragraphIndex: index,
        matchedText: match[0],
        snippet: proofSnippet(paragraph, match.index, match.index + match[0].length),
        detail: "汉字紧邻半角标点（,.!?;:），疑似中英标点混用"
      });
    }
  }
}

/** 口头禅：叙述类高频副词在单个场景内出现过多（每词 ≥3 次才提示，避免噪声）。 */
const PROOF_CRUTCH_WORDS = ["突然", "顿时", "瞬间", "竟然", "居然", "仿佛", "似乎", "显然", "几乎", "一阵"];
export function findCrutchWords(paragraphs: string[], out: ProofHits): void {
  const joined = paragraphs.join("\n");
  for (const word of PROOF_CRUTCH_WORDS) {
    const count = countOccurrences(joined, word);
    if (count < 3) continue;
    const firstIndex = joined.indexOf(word);
    out.push({
      rule: "crutchWord",
      // 口头禅是场景级判断，位置粒度定为「整场 + 该词」。
      paragraphIndex: -1,
      matchedText: word,
      snippet: firstIndex >= 0 ? proofSnippet(joined, firstIndex, firstIndex + word.length) : null,
      detail: `「${word}」出现 ${count} 次，注意口头禅化`
    });
  }
}

/** 连续段落同字开头：≥3 个连续非空段落首字相同（刻意排比可忽略）。 */
export function findParagraphStartRepeat(paragraphs: string[], out: ProofHits): void {
  const meaningful = paragraphs
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0);
  let runStart = 0;
  for (let index = 1; index <= meaningful.length; index += 1) {
    const sameHead =
      index < meaningful.length &&
      meaningful[index]![0] === meaningful[runStart]![0];
    if (sameHead) continue;
    const runLength = index - runStart;
    if (runLength >= 3) {
      out.push({
        rule: "paragraphStartRepeat",
        paragraphIndex: runStart,
        matchedText: meaningful[runStart]![0] ?? "",
        snippet: meaningful.slice(runStart, runStart + 2).map((paragraph) => paragraph.slice(0, 16)).join(" / "),
        detail: `${runLength} 处连续段落以同一字开头（如为刻意排比可忽略）`
      });
    }
    runStart = index;
  }
}

/**
 * 别名一致性：同一张卡片在全书被多种称呼指代时，逐个提示「少数派称呼」所在位置。
 * 主导称呼按全书出现次数决定（并列时优先卡片主名），因此不会因为一次「全名 + 简称」
 * 的正常写法就报警——只有相对罕见的称呼才需要作者确认。
 */
export function findAliasInconsistency(
  paragraphs: string[],
  dictionary: ProofDictionaryEntry[],
  variantFrequency: Map<string, number>,
  out: ProofHits
): void {
  for (const entry of dictionary) {
    if (entry.variants.length < 2) continue;
    let dominant = entry.variants[0]!;
    let dominantCount = -1;
    for (const variant of entry.variants) {
      const count = variantFrequency.get(variant) ?? 0;
      if (count > dominantCount || (count === dominantCount && variant === entry.title)) {
        dominant = variant;
        dominantCount = count;
      }
    }
    if (dominantCount <= 0) continue;
    const minority = entry.variants.filter(
      (variant) => variant !== dominant && (variantFrequency.get(variant) ?? 0) > 0
    );
    if (minority.length === 0) continue;
    for (let index = 0; index < paragraphs.length; index += 1) {
      const paragraph = paragraphs[index]!;
      if (!paragraph) continue;
      for (const variant of minority) {
        const position = paragraph.indexOf(variant);
        if (position < 0) continue;
        out.push({
          rule: "aliasInconsistency",
          paragraphIndex: index,
          matchedText: variant,
          snippet: proofSnippet(paragraph, position, position + variant.length),
          detail: `「${entry.title}」全书以「${dominant}」为主，此处用了「${variant}」`
        });
      }
    }
  }
}

/**
 * 词表掩码索引：把每个词条的每个位置替换为通配符。
 * 文本侧对每个窗口生成同构掩码键即可 O(1) 找到「只差一个字」的词条，
 * 复杂度与词表规模无关（只与窗口长度相关）。
 */
export function buildTypoMaskIndex(dictionary: ProofDictionaryEntry[]): Map<string, string[]> {
  const index = new Map<string, string[]>();
  for (const entry of dictionary) {
    for (const variant of entry.variants) {
      if (variant.length < 2 || variant.length > PROOF_TYPO_MAX_TERM_LENGTH) continue;
      for (let position = 0; position < variant.length; position += 1) {
        const key = `${variant.length}:${variant.slice(0, position)}*${variant.slice(position + 1)}`;
        const bucket = index.get(key);
        if (bucket) {
          if (!bucket.includes(variant)) bucket.push(variant);
        } else {
          index.set(key, [variant]);
        }
      }
    }
  }
  return index;
}

/**
 * 疑似错拼：与项目词表（卡片主名/别名）仅差一个字的词，按位置提示。
 * 词表自身的任意子串都视为合法（「洛水之蔚」里的「水之」不算错），
 * 且基准词需在全书出现 ≥2 次，避免一次性噪声。
 */
export function findSuspectedTypos(
  paragraphs: string[],
  vocabulary: Set<string>,
  protectedSubstrings: Set<string>,
  maskIndex: Map<string, string[]>,
  variantFrequency: Map<string, number>,
  out: ProofHits
): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    const reported = new Set<string>();
    for (let start = 0; start < paragraph.length; start += 1) {
      if (!HAN_CHARACTER_PATTERN.test(paragraph[start]!)) continue;
      for (let length = 2; length <= PROOF_TYPO_MAX_TERM_LENGTH; length += 1) {
        const end = start + length;
        if (end > paragraph.length) break;
        const candidate = paragraph.slice(start, end);
        if (!HAN_ONLY_PATTERN.test(candidate)) break;
        if (vocabulary.has(candidate) || protectedSubstrings.has(candidate)) continue;
        let best = "";
        let bestCount = 0;
        for (let position = 0; position < length; position += 1) {
          const key = `${length}:${candidate.slice(0, position)}*${candidate.slice(position + 1)}`;
          const bucket = maskIndex.get(key);
          if (!bucket) continue;
          for (const term of bucket) {
            if (term === candidate) continue;
            const count = variantFrequency.get(term) ?? 0;
            if (count > bestCount) {
              best = term;
              bestCount = count;
            }
          }
        }
        if (bestCount < PROOF_TYPO_MIN_TERM_FREQUENCY) continue;
        if (reported.has(candidate)) continue;
        reported.add(candidate);
        out.push({
          rule: "suspectedTypo",
          paragraphIndex: index,
          matchedText: candidate,
          snippet: proofSnippet(paragraph, start, end),
          detail: `疑似「${best}」的错拼（全书出现 ${bestCount} 次）`
        });
      }
    }
  }
}

/** 全部校对规则（scanProof 的规则白名单与默认选择）。 */
export const PROOF_RULES = new Set<ProofRule>([
  "repeatedChar",
  "unbalancedPunctuation",
  "abnormalSpacing",
  "longParagraph",
  "bannedWord",
  "mixedPunctuation",
  "crutchWord",
  "paragraphStartRepeat",
  "aliasInconsistency",
  "suspectedTypo"
]);

/** 规则级兜底说明：命中没有 detail 时用于分组消息。 */
export const PROOF_RULE_BASE_MESSAGE: Record<ProofRule, string> = {
  repeatedChar: "存在连续重复字",
  unbalancedPunctuation: "成对标点数量不等",
  abnormalSpacing: "存在异常空格",
  longParagraph: "存在超长段落",
  bannedWord: "命中禁用词",
  mixedPunctuation: "疑似中英标点混用",
  crutchWord: "叙述词重复过多",
  paragraphStartRepeat: "连续段落以同一字开头",
  aliasInconsistency: "同一卡片出现多种称呼",
  suspectedTypo: "疑似错拼"
};

export function validateProofRule(value: unknown): ProofRule {
  if (typeof value !== "string" || !PROOF_RULES.has(value as ProofRule)) {
    throw new CreationWorkspaceError("invalid-input", "不支持的校对规则。");
  }
  return value as ProofRule;
}

export const DEFAULT_MAX_PARAGRAPH_CHARS = 500;
