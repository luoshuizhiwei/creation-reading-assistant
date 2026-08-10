import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, FileWarning, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import type { ProofIssue, ProofRule } from "@/types/creation";

interface ProofPanelProps {
  projectId: string;
  onClose(): void;
}

const RULE_LABELS: Array<{ rule: ProofRule; label: string }> = [
  { rule: "repeatedChar", label: "连续重复字" },
  { rule: "unbalancedPunctuation", label: "成对标点" },
  { rule: "abnormalSpacing", label: "异常空格" },
  { rule: "longParagraph", label: "超长段落" },
  { rule: "bannedWord", label: "禁用词" }
];

const RULE_DESCRIPTIONS: Record<ProofRule, string> = {
  repeatedChar: "同一汉字连续出现 3 次及以上（「他他他」）",
  unbalancedPunctuation: "「」（）《》等成对标点开闭数量不等",
  abnormalSpacing: "段首半角空格、连续全角空格、半角全角混用",
  longParagraph: "单段超过 500 字符（可调）",
  bannedWord: "命中你输入的禁用词列表"
};

export function ProofPanel({ projectId, onClose }: ProofPanelProps) {
  const { runProof } = useCreationActions();
  const [rules, setRules] = useState<Set<ProofRule>>(new Set(RULE_LABELS.map((item) => item.rule)));
  const [bannedWords, setBannedWords] = useState("");
  const [issues, setIssues] = useState<ProofIssue[]>([]);
  const [running, setRunning] = useState(false);
  const [ran, setRan] = useState(false);

  const execute = useCallback(async () => {
    setRunning(true);
    const banned = bannedWords
      .split(/[,，、\n]/)
      .map((word) => word.trim())
      .filter(Boolean);
    const view = await runProof({
      projectId,
      rules: rules.size > 0 ? [...rules] : undefined,
      bannedWords: banned
    });
    setIssues(view.issues);
    setRan(true);
    setRunning(false);
  }, [bannedWords, projectId, rules, runProof]);

  useEffect(() => {
    void execute();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleRule = (rule: ProofRule) => {
    setRules((current) => {
      const next = new Set(current);
      if (next.has(rule)) next.delete(rule);
      else next.add(rule);
      return next;
    });
  };

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="本地校对" aria-modal="true">
      <div className="creation-search-shell creation-proof-shell" role="search">
        <div className="creation-search-head">
          <FileWarning size={16} className="creation-search-head-icon" />
          <span className="creation-proof-title">本地校对</span>
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭校对">
            <X size={16} />
          </button>
        </div>

        <div className="creation-proof-controls">
          <div className="creation-proof-rules" role="group" aria-label="校对规则">
            {RULE_LABELS.map(({ rule, label }) => (
              <label key={rule} className={rules.has(rule) ? "active" : ""}>
                <input
                  type="checkbox"
                  checked={rules.has(rule)}
                  onChange={() => toggleRule(rule)}
                />
                {label}
              </label>
            ))}
          </div>
          <label className="creation-proof-banned">
            <span>禁用词（逗号或换行分隔）</span>
            <input
              className="paper-input h-9"
              placeholder="例如：水字数、错别字词"
              value={bannedWords}
              onChange={(event) => setBannedWords(event.target.value)}
            />
          </label>
          <div className="creation-proof-run">
            <Button onClick={() => void execute()} disabled={running}>
              {running ? "检查中…" : ran ? "重新检查" : "开始检查"}
            </Button>
          </div>
        </div>

        <div className="creation-search-results">
          {running && <p className="creation-search-state" role="status">正在逐场景检查…</p>}
          {!running && ran && issues.length === 0 && (
            <p className="creation-search-state"><CheckCircle2 size={15} /> 未发现问题，正文很干净。</p>
          )}
          {!running && !ran && (
            <p className="creation-search-state">只做本地检查，绝不修改正文；问题仅提示。</p>
          )}
          {!running && issues.length > 0 && (
            <ul className="creation-proof-list">
              {issues.map((issue) => {
                const description = RULE_DESCRIPTIONS[issue.rule];
                return (
                  <li key={`${issue.sceneId}-${issue.rule}`} className="creation-proof-item">
                    <span className="creation-proof-item-rule">{RULE_LABELS.find((item) => item.rule === issue.rule)?.label}</span>
                    <span className="creation-proof-item-main">
                      <span className="creation-proof-item-message">{issue.message}</span>
                      <span className="creation-proof-item-meta">
                        {issue.sceneTitle} · {issue.chapterTitle}
                      </span>
                      {issue.snippet && <span className="creation-proof-item-snippet">{issue.snippet}</span>}
                      <span className="creation-proof-item-desc">{description}</span>
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
