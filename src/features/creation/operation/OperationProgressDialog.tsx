import type { OperationKind, OperationPhase, OperationState, ResourceIntegrityReport } from "../../../types/operation";
import { NON_INTERRUPTIBLE_PHASES, RESOURCE_INTEGRITY_CATEGORY_LABEL, RESOURCE_INTEGRITY_CATEGORY_ORDER } from "../../../types/operation";
import { ResourceIntegrityScanPanel } from "./ResourceIntegrityScanPanel";
import "./operation.css";

const KIND_TITLE: Record<OperationKind, string> = {
  "backup.create": "创建完整备份",
  "backup.restore": "恢复完整备份",
  "backup.export-encrypted": "导出加密备份",
  "backup.import-encrypted": "导入加密备份",
  "bundle.export": "导出项目包",
  "bundle.import": "导入项目包",
  "bundle.export-encrypted": "导出加密项目包",
  "bundle.import-encrypted": "导入加密项目包",
  "resource.scan": "资源完整性扫描"
};

const PHASE_LABEL: Record<OperationPhase, string> = {
  validating: "校验中",
  scanning: "扫描中",
  hashing: "校验文件哈希",
  copying: "复制文件中",
  database: "写入数据库",
  committing: "提交中（不可中断）",
  cleanup: "清理临时文件",
  done: "完成"
};

export interface OperationProgressDialogProps {
  state: OperationState;
  isCommitting: boolean;
  onCancel: () => void;
  onClose: () => void;
  onReset: () => void;
}

export function OperationProgressDialog({
  state,
  isCommitting,
  onCancel,
  onClose,
  onReset
}: OperationProgressDialogProps): JSX.Element {
  const { kind, status, progress } = state;
  const isTerminal = status === "completed" || status === "cancelled" || status === "failed";

  // 仅在非 indeterminate 且有总量时计算真实百分比；否则不显示任何伪造百分比。
  const percent =
    !progress.indeterminate && progress.total !== null && progress.total > 0
      ? Math.min(100, Math.round((progress.completed / progress.total) * 100))
      : null;

  const cancelDisabled = status !== "running" || isCommitting;
  const closeDisabled = !isTerminal;

  return (
    <div className="history-modal-overlay" role="presentation" onClick={closeDisabled ? undefined : onClose}>
      <div
        className="history-modal operation-dialog"
        role="dialog"
        aria-modal="true"
        aria-label={`${KIND_TITLE[kind]}进度`}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="history-modal-head">
          <h3>{KIND_TITLE[kind]}</h3>
          <button
            type="button"
            className="history-modal-close"
            onClick={onClose}
            disabled={closeDisabled}
            aria-label="关闭"
            autoFocus
          >
            ×
          </button>
        </div>

        <div className="history-modal-body">
          {status === "running" || status === "cancelling" ? (
            <div className="operation-progress">
              <p className="operation-phase">{PHASE_LABEL[progress.phase] ?? progress.phase}</p>
              {percent !== null ? (
                <div className="operation-bar" aria-label={`进度 ${percent}%`}>
                  <div className="operation-bar-fill" style={{ width: `${percent}%` }} />
                  <span className="operation-bar-text">{percent}%</span>
                </div>
              ) : progress.total !== null && progress.total > 0 ? (
                <p className="operation-count">
                  已完成 {progress.completed} / {progress.total}
                </p>
              ) : progress.bytesTotal !== null && progress.bytesTotal > 0 ? (
                <p className="operation-count">
                  已传输 {(progress.bytesCompleted / 1024 / 1024).toFixed(1)} /{" "}
                  {(progress.bytesTotal / 1024 / 1024).toFixed(1)} MB
                </p>
              ) : (
                <p className="operation-indeterminate" aria-live="polite">
                  正在处理，请稍候…
                </p>
              )}
              {isCommitting ? (
                <p className="operation-commit-note">正在完成安全提交，此阶段不可取消，请勿关闭应用。</p>
              ) : null}
            </div>
          ) : null}

          {status === "completed" ? (
            <div className="operation-result operation-success" role="status">
              {kind === "resource.scan" ? (
                <ResourceIntegrityScanPanel
                  report={(state.result?.result ?? null) as ResourceIntegrityReport | null}
                />
              ) : (
                <p>操作已完成。{state.deferredCancel ? "（取消请求落在不可中断阶段，已在安全边界后完成，无法撤销。）" : ""}</p>
              )}
            </div>
          ) : null}

          {status === "cancelled" ? (
            <div className="operation-result operation-cancelled" role="status">
              <p>操作已取消。已进行的安全阶段被回滚，当前数据未被破坏。</p>
            </div>
          ) : null}

          {status === "failed" ? (
            <div className="operation-result operation-failed" role="alert">
              <p className="operation-error-title">操作失败</p>
              {/* 错误保持可见，不依赖短暂 toast。 */}
              <p className="operation-error-message">{state.error?.message ?? "未知错误。"}</p>
              {state.error?.code ? (
                <p className="operation-error-code">错误码：{state.error.code}</p>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="history-modal-foot">
          {!isTerminal ? (
            <>
              <button
                type="button"
                className="history-btn-cancel"
                onClick={onClose}
                disabled={closeDisabled}
              >
                关闭
              </button>
              <button
                type="button"
                className="history-btn-danger"
                onClick={onCancel}
                disabled={cancelDisabled}
              >
                {status === "cancelling" ? "正在取消…" : "取消"}
              </button>
            </>
          ) : (
            <>
              {(status === "cancelled" || status === "failed") && kind !== "resource.scan" ? (
                <button type="button" className="history-btn-cancel" onClick={onReset}>
                  重试
                </button>
              ) : null}
              <button type="button" className="history-btn-confirm" onClick={onClose}>
                关闭
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
