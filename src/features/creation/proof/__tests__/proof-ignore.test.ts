// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  PROOF_RULE_DESCRIPTIONS,
  PROOF_RULE_LABELS,
  formatProofScanScope,
  proofGroupKey,
  proofLocationLabel,
  proofLocationRowKey,
  proofRuleLabel,
  summarizeProofTotals
} from "@/features/creation/proof/proof-ignore";
import type { ProofIssue, ProofLocation, ProofRule, ProofScanScope, ProofView } from "@/types/creation";

function location(overrides: Partial<ProofLocation> = {}): ProofLocation {
  return {
    locationKey: "repeatedChar#0#aaaaaaaa",
    paragraphIndex: 0,
    matchedText: "他他他",
    snippet: null,
    detail: null,
    ignored: false,
    ...overrides
  };
}

function issue(overrides: Partial<ProofIssue> = {}): ProofIssue {
  return {
    sceneId: "scene-1",
    chapterId: "chapter-1",
    chapterTitle: "第一章",
    sceneTitle: "开场",
    rule: "repeatedChar",
    message: "连续重复字「他他他」",
    snippet: null,
    count: 1,
    ignoredCount: 0,
    locations: [location()],
    ...overrides
  };
}

function scope(overrides: Partial<ProofScanScope> = {}): ProofScanScope {
  return {
    kind: "project",
    label: "全书扫描：2 卷 / 8 章 / 30 场景",
    volumeCount: 2,
    chapterCount: 8,
    sceneCount: 30,
    rules: ["repeatedChar"],
    bannedWords: [],
    maxParagraphChars: 500,
    ...overrides
  };
}

function view(overrides: Partial<ProofView> = {}): ProofView {
  return {
    projectId: "project-1",
    scanScope: scope(),
    issues: [issue()],
    ignoredIssues: [],
    scannedScenes: 30,
    affectedScenes: 1,
    total: 1,
    ignoredCount: 0,
    rawTotal: 1,
    truncated: false,
    ignoredTruncated: false,
    ignoreRecordCount: 0,
    ...overrides
  };
}

describe("校对规则元信息", () => {
  it("每条规则都有中文名与说明", () => {
    const rules = PROOF_RULE_LABELS.map((item) => item.rule);
    expect(rules).toContain("aliasInconsistency");
    expect(rules).toContain("suspectedTypo");
    for (const rule of rules) {
      expect(proofRuleLabel(rule)).not.toBe(rule);
      expect(PROOF_RULE_DESCRIPTIONS[rule].length).toBeGreaterThan(0);
    }
  });

  it("未知规则回落到原始键，不抛错", () => {
    expect(proofRuleLabel("futureRule" as ProofRule)).toBe("futureRule");
  });
});

describe("proofLocationLabel", () => {
  it("段落级位置显示段号（1 起）", () => {
    expect(proofLocationLabel(location({ paragraphIndex: 0 }))).toBe("第 1 段");
    expect(proofLocationLabel(location({ paragraphIndex: 2 }))).toBe("第 3 段");
  });

  it("场景级位置显示整场", () => {
    expect(proofLocationLabel(location({ paragraphIndex: -1 }))).toBe("整场");
  });
});

describe("formatProofScanScope", () => {
  it("全书扫描显式给出卷/章/场景数", () => {
    expect(formatProofScanScope(scope())).toBe("全书扫描：2 卷 / 8 章 / 30 场景");
  });

  it("单场景扫描去掉前缀后给出章节与场景名", () => {
    expect(
      formatProofScanScope(scope({ kind: "scene", label: "单场景扫描：第一章 · 开场" }))
    ).toBe("单场景扫描：第一章 · 开场");
  });
});

describe("summarizeProofTotals", () => {
  it("未忽略总数单独呈现，不把已忽略算成已解决", () => {
    const result = summarizeProofTotals(view({ total: 7, ignoredCount: 3, rawTotal: 10 }), 7);
    expect(result.text).toBe("共 7 处问题");
    expect(result.ignoredText).toBe("3 处已忽略");
    expect(result.truncated).toBe(false);
  });

  it("没有已忽略项时不展示已忽略文案", () => {
    const result = summarizeProofTotals(view(), 1);
    expect(result.ignoredText).toBeNull();
  });

  it("截断时说明列表只显示部分", () => {
    const result = summarizeProofTotals(view({ total: 500, truncated: true }), 200);
    expect(result.text).toBe("共 500 处问题（列表仅显示前 200 组）");
    expect(result.truncatedText).not.toBeNull();
    expect(result.truncated).toBe(true);
  });
});

describe("稳定 key", () => {
  it("分组 key 由场景与规则组成", () => {
    expect(proofGroupKey(issue())).toBe("scene-1-repeatedChar");
  });

  it("位置 key 在同一分组内按位置键区分", () => {
    const target = issue();
    const first = location({ locationKey: "repeatedChar#0#aaaaaaaa" });
    const second = location({ locationKey: "repeatedChar#1#aaaaaaaa" });
    expect(proofLocationRowKey(target, first)).not.toBe(proofLocationRowKey(target, second));
  });
});
