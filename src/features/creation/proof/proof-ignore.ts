import type { ProofIssue, ProofLocation, ProofRule, ProofScanScope, ProofView } from "@/types/creation";

/** 规则中文名；新增规则时此处与 RULE_DESCRIPTIONS 必须同步。 */
export const PROOF_RULE_LABELS: Array<{ rule: ProofRule; label: string }> = [
  { rule: "repeatedChar", label: "连续重复字" },
  { rule: "unbalancedPunctuation", label: "成对标点" },
  { rule: "abnormalSpacing", label: "异常空格" },
  { rule: "longParagraph", label: "超长段落" },
  { rule: "bannedWord", label: "禁用词" },
  { rule: "mixedPunctuation", label: "中英混用标点" },
  { rule: "crutchWord", label: "口头禅" },
  { rule: "paragraphStartRepeat", label: "段落开头重复" },
  { rule: "aliasInconsistency", label: "别名一致性" },
  { rule: "suspectedTypo", label: "疑似错拼" }
];

export const PROOF_RULE_DESCRIPTIONS: Record<ProofRule, string> = {
  repeatedChar: "同一汉字连续出现 3 次及以上（「他他他」）",
  unbalancedPunctuation: "「」（）《》等成对标点开闭数量不等",
  abnormalSpacing: "段首半角空格、连续全角空格、半角全角混用",
  longParagraph: "单段超过阈值字符（可在下方调整）",
  bannedWord: "命中你输入的禁用词列表",
  mixedPunctuation: "汉字紧邻半角标点（,.!?;:），常见于网页粘贴",
  crutchWord: "突然/顿时/仿佛等叙述词单场景出现 ≥3 次",
  paragraphStartRepeat: "连续 3 段以上以同一字开头（刻意排比可忽略）",
  aliasInconsistency: "同一张卡片被多种称呼指代，只提示全书较罕见的那种",
  suspectedTypo: "与卡片主名/别名仅差一个字，疑似写错的人名或术语"
};

export function proofRuleLabel(rule: ProofRule): string {
  return PROOF_RULE_LABELS.find((item) => item.rule === rule)?.label ?? rule;
}

/** 位置的人读描述；段落序号为 -1 表示该规则以整场为粒度。 */
export function proofLocationLabel(location: ProofLocation): string {
  return location.paragraphIndex < 0 ? "整场" : `第 ${location.paragraphIndex + 1} 段`;
}

/** 扫描范围摘要：明确告诉用户「扫描了什么」。 */
export function formatProofScanScope(scope: ProofScanScope): string {
  const entity =
    scope.kind === "scene"
      ? scope.label.replace(/^单场景扫描：/, "")
      : `${scope.volumeCount} 卷 / ${scope.chapterCount} 章 / ${scope.sceneCount} 场景`;
  return `${scope.kind === "scene" ? "单场景扫描" : "全书扫描"}：${entity}`;
}

export interface ProofTotals {
  /** 主文案：未忽略的问题数。 */
  text: string;
  /** 已忽略数量文案；为 0 时不展示。 */
  ignoredText: string | null;
  /** 截断提示；未截断时为 null。 */
  truncatedText: string | null;
  /** 是否被截断。 */
  truncated: boolean;
}

/**
 * 结果计数文案。
 * 未忽略总数与已忽略总数分开呈现，避免「已经忽略过」被误读成「没有问题」。
 */
export function summarizeProofTotals(view: ProofView, shownCount: number): ProofTotals {
  const truncated = view.truncated;
  const text = truncated
    ? `共 ${view.total} 处问题（列表仅显示前 ${shownCount} 组）`
    : `共 ${view.total} 处问题`;
  return {
    text,
    ignoredText: view.ignoredCount > 0 ? `${view.ignoredCount} 处已忽略` : null,
    truncatedText: truncated ? "结果超出上限，已按章节顺序截断" : null,
    truncated
  };
}

/** 分组的稳定 key：同一场景同一规则只出现一次。 */
export function proofGroupKey(issue: ProofIssue): string {
  return `${issue.sceneId}-${issue.rule}`;
}

/** 位置在分组内的稳定 key。 */
export function proofLocationRowKey(issue: ProofIssue, location: ProofLocation): string {
  return `${proofGroupKey(issue)}-${location.locationKey}`;
}
