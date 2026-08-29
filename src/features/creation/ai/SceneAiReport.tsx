import { FileSearch, X } from "lucide-react";
import "./scene-candidate.css";

/**
 * AI 一致性检查报告（调研 D-C2 切片 3）：输出是「问题报告」而非改写候选，
 * 只读呈现、不落库、不改正文；需要复查时重新发起检查。
 */

interface SceneAiReportProps {
  actionLabel: string;
  content: string;
  model: string;
  onClose(): void;
}

export function SceneAiReport({ actionLabel, content, model, onClose }: SceneAiReportProps) {
  return (
    <div className="writing-reanchor-backdrop" role="presentation">
      <section className="writing-reanchor-dialog scene-candidate-dialog" role="dialog" aria-modal="true" aria-label="AI 一致性检查报告" data-testid="scene-ai-report">
        <p className="desktop-card-label">AI Report</p>
        <h4>
          <FileSearch size={14} /> {actionLabel}报告
        </h4>
        <p className="scene-candidate-summary">模型 {model} · 报告只做提示，不会修改任何正文或数据。</p>
        <div className="scene-candidate-diff scene-ai-report-body">
          {content
            .split(/\n{2,}|\n(?=【)/)
            .filter((block) => block.trim())
            .map((block, index) => (
              <p key={index} className="scene-candidate-line same">
                <em aria-hidden="true">·</em>
                <span>{block.trim()}</span>
              </p>
            ))}
        </div>
        <div className="writing-reanchor-actions">
          <button type="button" onClick={onClose} data-testid="scene-ai-report-close">
            <X size={13} /> 关闭报告
          </button>
        </div>
      </section>
    </div>
  );
}
