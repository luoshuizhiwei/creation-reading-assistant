import { Button, Dialog, Spinner } from "@/components/ui";
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
    <Dialog
      open={true}
      title={title}
      onClose={busy ? undefined : onCancel}
      width="max-w-[480px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>
            取消
          </Button>
          <Button type="button" variant="primary" onClick={onConfirm} disabled={busy || confirmDisabled}>
            {busy ? <><Spinner size={14} className="mr-1.5" /> 执行中…</> : confirmLabel}
          </Button>
        </>
      }
    >
      {description && <p className="mb-3 text-sm text-paper-ink/70">{description}</p>}
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
    </Dialog>
  );
}
