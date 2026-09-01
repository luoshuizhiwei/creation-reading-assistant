import { Button } from "@/components/ui";
import type { ImpactRow } from "./outline-impact";
import "./outline-reorg.css";

interface StructureImpactDialogProps {
  title: string;
  rows: ImpactRow[];
  description?: string;
  /** 操作不合法时的提示（如拆章点为首场景），会禁用确认按钮。 */
  notice?: string;
  /** 命令执行失败的报错。 */
  error?: string | null;
  confirmLabel?: string;
  confirmDisabled?: boolean;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * 结构重组的本地影响预览 + 明确确认对话框。
 * 取消时只调用 onCancel，不触发任何写命令；确认时调用 onConfirm。
 * 视觉沿用编辑出版工作室的 motion-dialog / paper-* 体系（全局类，不在此文件定义）。
 */
export function StructureImpactDialog({
  title,
  rows,
  description,
  notice,
  error,
  confirmLabel = "确认执行",
  confirmDisabled = false,
  busy = false,
  onCancel,
  onConfirm
}: StructureImpactDialogProps) {
  return (
    <div
      className="absolute inset-0 z-[90] grid place-items-center bg-paper-ink/18 px-6 backdrop-blur-sm"
      onClick={busy ? undefined : onCancel}
    >
      <section
        className="motion-dialog w-[min(480px,100%)] overflow-hidden rounded-2xl border border-paper-line bg-paper-panel shadow-paper"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="h-1 bg-copper" />
        <div className="p-5">
          <h2 className="paper-title text-base font-semibold text-paper-ink">{title}</h2>
          {description && <p className="mt-2 text-sm text-paper-ink/70">{description}</p>}
          <dl className="outline-impact-list">
            {rows.map((row, index) => (
              <div className="outline-impact-row" key={index}>
                <dt>{row.label}</dt>
                <dd>{row.value}</dd>
              </div>
            ))}
          </dl>
          {notice && (
            <p className="outline-impact-notice" role="alert">
              {notice}
            </p>
          )}
          {error && (
            <p className="outline-impact-error" role="alert">
              {error}
            </p>
          )}
          <div className="mt-4 flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>
              取消
            </Button>
            <Button type="button" variant="primary" onClick={onConfirm} disabled={busy || confirmDisabled}>
              {busy ? "执行中…" : confirmLabel}
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}
