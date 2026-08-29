import { useMemo } from "react";
import { Check, Sparkles, X } from "lucide-react";
import { diffParagraphs, diffStats } from "@/features/creation/ai/diff-paragraphs";
import "./scene-candidate.css";

/**
 * 场景候选评审（调研 D-C2 切片 2）：AI 输出永不直接改正文，
 * 以段落 diff 呈现；采纳走保护快照 + revision 校验（由 WritingDesk 的回调实现）。
 */

export interface SceneCandidate {
  action: string;
  content: string;
  model: string;
}

interface SceneCandidateReviewProps {
  candidate: SceneCandidate;
  currentBodyText: string;
  busy: boolean;
  onAccept(candidateText: string): void;
  onDiscard(): void;
}

export function SceneCandidateReview({ candidate, currentBodyText, busy, onAccept, onDiscard }: SceneCandidateReviewProps) {
  const entries = useMemo(() => diffParagraphs(currentBodyText, candidate.content), [candidate.content, currentBodyText]);
  const stats = useMemo(() => diffStats(entries), [entries]);

  return (
    <div className="writing-reanchor-backdrop" role="presentation">
      <section className="writing-reanchor-dialog scene-candidate-dialog" role="dialog" aria-modal="true" aria-label="AI 候选评审" data-testid="scene-candidate-review">
        <p className="desktop-card-label">AI Candidate</p>
        <h4>
          <Sparkles size={14} /> AI 候选 · {candidate.action}
        </h4>
        <p className="scene-candidate-summary">
          新增 {stats.added} 段 · 删除 {stats.removed} 段 · 保留 {stats.same} 段 · 模型 {candidate.model}
        </p>
        <div className="scene-candidate-diff" role="list">
          {entries.map((entry, index) => (
            <p
              key={index}
              role="listitem"
              className={`scene-candidate-line ${entry.type === "add" ? "add" : entry.type === "remove" ? "remove" : "same"}`}
            >
              {entry.type === "add" && <em>+</em>}
              {entry.type === "remove" && <em>−</em>}
              {entry.type === "same" && <em aria-hidden="true">=</em>}
              <span>{entry.text}</span>
            </p>
          ))}
        </div>
        <p className="scene-candidate-note">采纳前会自动创建保护快照；正文保存校验 revision，冲突时不会覆盖他人修改。</p>
        <div className="writing-reanchor-actions">
          <button type="button" disabled={busy} onClick={onDiscard}>
            丢弃候选
          </button>
          <button type="button" className="scene-candidate-accept" disabled={busy} data-testid="scene-candidate-accept" onClick={() => onAccept(candidate.content)}>
            <Check size={13} /> {busy ? "保存中…" : "采纳为正文"}
          </button>
          <button type="button" aria-label="关闭候选评审" disabled={busy} onClick={onDiscard} className="scene-candidate-close">
            <X size={13} />
          </button>
        </div>
      </section>
    </div>
  );
}
