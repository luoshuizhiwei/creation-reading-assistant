import { useMemo } from "react";
import { Check, Sparkles, X } from "lucide-react";
import { Button } from "@/components/ui";
import { diffParagraphs, diffStats } from "@/features/creation/ai/diff-paragraphs";
import { mergeSceneBody, sceneAiActionLabel, type SceneAiAdoptMode } from "@/features/creation/ai/scene-ai-actions";
import "./scene-candidate.css";

/**
 * 场景候选评审（调研 D-C2 切片 2）：AI 输出永不直接改正文，
 * 以段落 diff 呈现；采纳走保护快照 + revision 校验（由 WritingDesk 的回调实现）。
 *
 * Stage 4-D 追加 mode：
 * - replace（润色 / 精简）：候选是整篇新正文，diff 与采纳都以候选整篇为对象；
 * - append（续写）：AI 只返回新增段落，diff 按「原正文 + 新段落」合成预览，
 *   避免把续写结果显示成「整篇被删除再重写」。
 *
 * 采纳回调一律回传**候选原文**（不是合成文本）：落库方式由调用方按 mode 决定
 * ——追加走文档层拼接（原块不动），替换走纯文本转文档。
 */

export interface SceneCandidate {
  action: string;
  content: string;
  model: string;
  /** 采纳语义：replace 替换整篇，append 追加到正文末尾。缺省 replace。 */
  mode?: SceneAiAdoptMode;
}

interface SceneCandidateReviewProps {
  candidate: SceneCandidate;
  currentBodyText: string;
  busy: boolean;
  onAccept(candidateText: string): void;
  onDiscard(): void;
}

export function SceneCandidateReview({ candidate, currentBodyText, busy, onAccept, onDiscard }: SceneCandidateReviewProps) {
  const mode: SceneAiAdoptMode = candidate.mode ?? "replace";
  const targetText = useMemo(
    () => (mode === "append" ? mergeSceneBody(currentBodyText, candidate.content) : candidate.content),
    [candidate.content, currentBodyText, mode]
  );
  const entries = useMemo(() => diffParagraphs(currentBodyText, targetText), [currentBodyText, targetText]);
  const stats = useMemo(() => diffStats(entries), [entries]);
  const adoptLabel = mode === "append" ? "追加到正文" : "采纳为正文";

  return (
    <div className="writing-reanchor-backdrop" role="presentation">
      <section className="writing-reanchor-dialog scene-candidate-dialog" role="dialog" aria-modal="true" aria-label="AI 候选评审" data-testid="scene-candidate-review">
        <p className="desktop-card-label">AI Candidate</p>
        <h4>
          <Sparkles size={14} /> AI 候选 · {sceneAiActionLabel(candidate.action)}
        </h4>
        <p className="scene-candidate-summary">
          新增 {stats.added} 段 · 删除 {stats.removed} 段 · 保留 {stats.same} 段 · 模型 {candidate.model}
        </p>
        {mode === "append" && (
          <p className="scene-candidate-summary" data-testid="scene-candidate-append-hint">
            续写候选：采纳后追加到当前正文末尾，原正文保持不变。
          </p>
        )}
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
          <Button variant="outline" disabled={busy} onClick={onDiscard}>
            丢弃候选
          </Button>
          <Button disabled={busy} data-testid="scene-candidate-accept" onClick={() => onAccept(candidate.content)}>
            <Check size={13} /> {busy ? "保存中…" : adoptLabel}
          </Button>
          <button type="button" aria-label="关闭候选评审" disabled={busy} onClick={onDiscard} className="scene-candidate-close">
            <X size={13} />
          </button>
        </div>
      </section>
    </div>
  );
}
