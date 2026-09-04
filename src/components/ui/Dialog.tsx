import { useEffect, useRef, type KeyboardEvent, type ReactNode } from "react";
import { X } from "lucide-react";

export interface DialogProps {
  /** 是否显示弹窗 */
  open: boolean;
  /** 标题，也用于 aria-label */
  title?: ReactNode;
  /** 自定义 aria-label（若 title 为复杂 ReactNode 时使用） */
  ariaLabel?: string;
  /** 点击蒙层或按 Escape 时触发 */
  onClose?: () => void;
  /** 弹窗宽度类（默认 max-w-md） */
  width?: string;
  /** 弹窗卡片额外类名 */
  className?: string;
  /** 蒙层/外层容器额外类名 */
  overlayClassName?: string;
  /** 测试标识 */
  dataTestId?: string;
  children: ReactNode;
  /** 底部操作栏内容（按钮组等） */
  footer?: ReactNode;
  /** 是否隐藏右上角关闭按钮（默认显示） */
  hideCloseButton?: boolean;
}

/**
 * Dialog — 统一弹窗原语
 *
 * 封装了：
 * - fixed inset-0 蒙层 + backdrop-blur
 * - Escape 键关闭监听
 * - 首次渲染自动聚焦
 * - aria-modal / role="dialog"
 * - 标题区 + 关闭按钮 + 内容区 + 可选底部操作栏
 *
 * 替代项目中 14 处各自手写相同结构的弹窗。
 */
export function Dialog({
  open,
  title,
  ariaLabel,
  onClose,
  width = "max-w-md",
  className = "",
  overlayClassName = "",
  dataTestId,
  children,
  footer,
  hideCloseButton = false
}: DialogProps) {

  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: globalThis.KeyboardEvent) => {
      if (e.key === "Escape") onClose?.();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onClose]);

  useEffect(() => {
    if (open) {
      requestAnimationFrame(() => {
        const el = dialogRef.current?.querySelector<HTMLElement>(
          "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])"
        );
        el?.focus();
      });
    }
  }, [open]);

  if (!open) return null;

  const resolvedAriaLabel = ariaLabel ?? (typeof title === "string" ? title : undefined);

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center p-4 ${overlayClassName}`}
      role="dialog"
      aria-modal="true"
      aria-label={resolvedAriaLabel}
      data-testid={dataTestId}
    >
      <div
        className="absolute inset-0 bg-paper-ink/30 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        ref={dialogRef}
        className={`relative z-10 w-full ${width} rounded-xl border border-paper-line bg-paper-panel shadow-2xl ${className}`}
        onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
          if (e.key === "Escape") e.stopPropagation();
        }}
      >
        {(title || !hideCloseButton) && (
          <div className="flex items-center justify-between border-b border-paper-line px-5 py-4">
            {title && (
              <h2 className="paper-title text-base font-semibold text-paper-ink">{title}</h2>
            )}
            {!hideCloseButton && (
              <button
                type="button"
                className="ml-auto rounded-md p-1 text-paper-muted hover:bg-paper-soft hover:text-paper-ink transition"
                aria-label="关闭"
                onClick={onClose}
              >
                <X size={16} />
              </button>
            )}
          </div>
        )}
        <div className="max-h-[70vh] overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-3 border-t border-paper-line px-5 py-3">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

export function DialogTitle({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <h2 className={`paper-title text-base font-semibold text-paper-ink ${className}`}>{children}</h2>
  );
}

export function DialogFooter({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`flex items-center justify-end gap-3 border-t border-paper-line px-5 py-3 ${className}`}>
      {children}
    </div>
  );
}
