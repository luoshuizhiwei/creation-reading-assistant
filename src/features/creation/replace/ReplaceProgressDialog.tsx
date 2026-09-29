import { Button } from "@/components/ui";
import type { ReplaceApplyResultView, ReplaceErrorView, ReplacePlanProgress } from "./types";
import { describeReplaceError } from "./types";

interface ReplaceProgressDialogProps {
  open: boolean;
  progress: ReplacePlanProgress | null;
  isApplying: boolean;
  error: ReplaceErrorView | null;
  result: ReplaceApplyResultView | null;
  onCancel: () => void;
  onClose: () => void;
}

export function ReplaceProgressDialog({
  open,
  progress,
  isApplying,
  error,
  result,
  onCancel,
  onClose
}: ReplaceProgressDialogProps) {
  if (!open) return null;

  const hasDenominator = progress !== null && progress.totalScenes > 0;
  const percent = hasDenominator
    ? Math.round((progress!.completedScenes / progress!.totalScenes) * 100)
    : null;

  const phaseText =
    progress?.phase === "planning"
      ? "正在生成替换计划…"
      : progress?.phase === "scanning"
        ? "正在扫描场景与命中…"
        : isApplying
          ? "正在应用替换（事务中，不可取消）…"
          : "正在处理…";

  return (
    <div className="replace-progress-overlay" role="dialog" aria-label="替换进度" data-testid="replace-progress-dialog">
      <div className="replace-progress-card">
        {error ? (
          <div className="replace-progress-error" data-testid="replace-error">
            <h3>替换未能完成</h3>
            <p data-testid="replace-error-message">{describeReplaceError(error)}</p>
            <p className="replace-error-code" data-testid="replace-error-code">
              代码：{error.code}
            </p>
            <Button variant="outline" size="sm" onClick={onClose} data-testid="replace-error-close">
              关闭
            </Button>
          </div>
        ) : result ? (
          <div className="replace-progress-done" data-testid="replace-done">
            <h3>替换完成</h3>
            <p data-testid="replace-done-detail">
              应用 {result.appliedHitCount} 处命中，影响 {result.modifiedSceneIds.length} 个场景（已创建保护快照与变更记录）。
            </p>
            <Button size="sm" onClick={onClose} data-testid="replace-done-close">
              完成
            </Button>
          </div>
        ) : (
          <div className="replace-progress-active">
            <h3>{phaseText}</h3>
            {percent !== null ? (
              <p data-testid="replace-percent">{percent}%</p>
            ) : (
              <p data-testid="replace-indeterminate">处理中，请稍候…</p>
            )}
            {progress && (
              <p className="replace-progress-detail" data-testid="replace-progress-detail">
                场景 {progress.completedScenes}/{progress.totalScenes}
                {progress.totalHits > 0 ? ` · 命中 ${progress.completedHits}/${progress.totalHits}` : ""}
              </p>
            )}
            {!isApplying && (
              <Button
                variant="outline"
                size="sm"
                onClick={onCancel}
                data-testid="replace-cancel"
                aria-label="取消"
              >
                取消
              </Button>
            )}
            {isApplying && (
              <p className="replace-progress-locked" data-testid="replace-apply-locked">
                已进入事务，正在安全提交，不可中途取消。
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
