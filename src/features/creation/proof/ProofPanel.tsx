import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCircle2, EyeOff, FileWarning, Undo2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import type { ProofIssue, ProofLocation, ProofRule, ProofView } from "@/types/creation";
import {
  PROOF_RULE_DESCRIPTIONS,
  PROOF_RULE_LABELS,
  formatProofScanScope,
  proofGroupKey,
  proofLocationLabel,
  proofLocationRowKey,
  proofRuleLabel,
  summarizeProofTotals
} from "./proof-ignore";

interface ProofPanelProps {
  projectId: string;
  onClose(): void;
}

export function ProofPanel({ projectId, onClose }: ProofPanelProps) {
  const {
    runProof,
    ignoreProofLocation,
    unignoreProofLocation
  } = useCreationActions();
  const [rules, setRules] = useState<Set<ProofRule>>(new Set(PROOF_RULE_LABELS.map((item) => item.rule)));
  const [bannedWords, setBannedWords] = useState("");
  const [maxParagraphChars, setMaxParagraphChars] = useState(500);
  const [includeIgnored, setIncludeIgnored] = useState(false);
  const [view, setView] = useState<ProofView | null>(null);
  const [running, setRunning] = useState(false);
  const [ran, setRan] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** 正在提交忽略/取消忽略的位置键，用于禁用按钮防重复点击。 */
  const [pending, setPending] = useState<string | null>(null);

  /**
   * @param options.includeIgnored 显式传入，避免切换开关时读到尚未生效的 state。
   */
  const scan = useCallback(
    async (options?: { includeIgnored?: boolean }) => {
      setRunning(true);
      setError(null);
      const banned = bannedWords
        .split(/[,，、\n]/)
        .map((word) => word.trim())
        .filter(Boolean);
      try {
        const next = await runProof({
          projectId,
          rules: rules.size > 0 ? [...rules] : undefined,
          bannedWords: banned,
          maxParagraphChars,
          includeIgnored: options?.includeIgnored ?? includeIgnored
        });
        setView(next);
        setRan(true);
      } catch {
        setError("校对扫描失败，请重试；正文未被修改。");
      } finally {
        setRunning(false);
      }
    },
    [bannedWords, includeIgnored, maxParagraphChars, projectId, rules, runProof]
  );

  const execute = useCallback(() => scan(), [scan]);

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

  const toggleIgnore = useCallback(
    async (issue: ProofIssue, location: ProofLocation) => {
      setPending(location.locationKey);
      try {
        if (location.ignored) {
          await unignoreProofLocation({
            projectId,
            sceneId: issue.sceneId,
            rule: issue.rule,
            locationKey: location.locationKey
          });
        } else {
          await ignoreProofLocation({
            projectId,
            sceneId: issue.sceneId,
            rule: issue.rule,
            locationKey: location.locationKey,
            matchedText: location.matchedText
          });
        }
        await scan();
      } finally {
        setPending(null);
      }
    },
    [ignoreProofLocation, projectId, scan, unignoreProofLocation]
  );

  const totals = useMemo(
    () => (view ? summarizeProofTotals(view, view.issues.length) : null),
    [view]
  );

  const renderIssue = (issue: ProofIssue, ignoredGroup: boolean) => (
    <li key={proofGroupKey(issue)} className="creation-proof-item">
      <span className="creation-proof-item-rule">{proofRuleLabel(issue.rule)}</span>
      <span className="creation-proof-item-main">
        <span className="creation-proof-item-message">{issue.message}</span>
        <span className="creation-proof-item-meta">
          {issue.sceneTitle} · {issue.chapterTitle}
        </span>
        {issue.snippet && <span className="creation-proof-item-snippet">{issue.snippet}</span>}
        <span className="creation-proof-item-desc">{PROOF_RULE_DESCRIPTIONS[issue.rule]}</span>
        <ul className="creation-proof-locations">
          {issue.locations.map((location) => {
            const busy = pending === location.locationKey;
            return (
              <li key={proofLocationRowKey(issue, location)} className="creation-proof-location">
                <span className="creation-proof-location-pos">{proofLocationLabel(location)}</span>
                <span className="creation-proof-location-text">{location.detail ?? location.matchedText}</span>
                <button
                  type="button"
                  className="creation-proof-ignore"
                  disabled={busy}
                  onClick={() => void toggleIgnore(issue, location)}
                  aria-label={
                    location.ignored
                      ? `取消忽略${proofLocationLabel(location)}的${proofRuleLabel(issue.rule)}问题`
                      : `忽略${proofLocationLabel(location)}的${proofRuleLabel(issue.rule)}问题`
                  }
                >
                  {location.ignored ? (
                    <>
                      <Undo2 size={12} /> 取消忽略
                    </>
                  ) : (
                    <>
                      <EyeOff size={12} /> 忽略此处
                    </>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
        {ignoredGroup && (
          <span className="creation-proof-item-desc">该分组的全部位置都被忽略，因此不计入上方总数。</span>
        )}
      </span>
    </li>
  );

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

        {view && (
          <div className="creation-proof-summary" role="status" aria-live="polite">
            <span className="creation-proof-scope">扫描范围：{formatProofScanScope(view.scanScope)}</span>
            {totals && (
              <span className="creation-proof-totals">
                <strong>{totals.text}</strong>
                {totals.ignoredText && <span className="creation-proof-ignored-count">{totals.ignoredText}</span>}
                {view.ignoreRecordCount > 0 && (
                  <span className="creation-proof-ignored-count">
                    本项目已保存 {view.ignoreRecordCount} 条忽略记录
                  </span>
                )}
              </span>
            )}
            {totals?.truncatedText && <span className="creation-proof-truncated">{totals.truncatedText}</span>}
          </div>
        )}

        <div className="creation-proof-controls">
          <div className="creation-proof-rules" role="group" aria-label="校对规则">
            {PROOF_RULE_LABELS.map(({ rule, label }) => (
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
          <div className="creation-proof-inline">
            <label className="creation-proof-banned">
              <span>禁用词（逗号或换行分隔）</span>
              <input
                className="paper-input h-9"
                placeholder="例如：水字数、错别字词"
                value={bannedWords}
                onChange={(event) => setBannedWords(event.target.value)}
              />
            </label>
            <label className="creation-proof-banned">
              <span>超长段落阈值（100–5000 字）</span>
              <input
                className="paper-input h-9"
                type="number"
                min={100}
                max={5000}
                step={50}
                value={maxParagraphChars}
                onChange={(event) => {
                  const parsed = Number(event.target.value);
                  if (Number.isFinite(parsed)) setMaxParagraphChars(Math.round(parsed));
                }}
              />
            </label>
          </div>
          <label className="creation-proof-toggle">
            <input
              type="checkbox"
              checked={includeIgnored}
              onChange={(event) => {
                const next = event.target.checked;
                setIncludeIgnored(next);
                void scan({ includeIgnored: next });
              }}
            />
            同时显示已忽略的问题
          </label>
          <div className="creation-proof-run">
            <Button onClick={() => void execute()} disabled={running}>
              {running ? "检查中…" : ran ? "重新检查" : "开始检查"}
            </Button>
          </div>
        </div>

        <div className="creation-search-results">
          {running && <p className="creation-search-state" role="status">正在逐场景检查…</p>}
          {!running && error && <p className="creation-search-state creation-search-state--error">{error}</p>}
          {!running && !error && ran && view && view.total === 0 && (
            <p className="creation-search-state">
              <CheckCircle2 size={15} /> 未发现问题，正文很干净。
            </p>
          )}
          {!running && !ran && (
            <p className="creation-search-state">只做本地检查，绝不修改正文；问题仅提示，可逐个位置忽略。</p>
          )}
          {!running && view && view.issues.length > 0 && (
            <ul className="creation-proof-list">{view.issues.map((issue) => renderIssue(issue, false))}</ul>
          )}
          {!running && view && includeIgnored && view.ignoredIssues.length > 0 && (
            <>
              <p className="creation-proof-section-title">已忽略（{view.ignoredIssues.length} 组）</p>
              <ul className="creation-proof-list">
                {view.ignoredIssues.map((issue) => renderIssue(issue, true))}
              </ul>
            </>
          )}
          {!running && view && includeIgnored && view.ignoredTruncated && (
            <p className="creation-search-state">已忽略的问题超出上限，未全部列出。</p>
          )}
        </div>
      </div>
    </div>
  );
}
