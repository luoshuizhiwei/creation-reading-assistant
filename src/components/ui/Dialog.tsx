import { useEffect, useRef, type KeyboardEvent, type ReactNode } from "react";
import { X } from "lucide-react";

/**
 * BD-127：打开中的弹窗栈（后进先出）。
 *
 * 每个 Dialog 原先各自往 `document` 挂一个 Escape 监听，且弹窗内层 div 又
 * `stopPropagation()`——焦点在弹窗里时（打开后必然如此），document 上的监听根本收不到
 * 事件，Esc 一直关不掉弹窗；真叠了两层弹窗时，若事件真到了 document，又会把两层一起关。
 * 现在只认栈顶：Esc 只关最上面那一层。
 */
type DialogEntry = { close: () => void };
const openDialogs: DialogEntry[] = [];
let escapeListening = false;

/** BD-128：弹窗内可聚焦元素（同首焦点查询口径），Tab 陷阱按它首尾成环。 */
const FOCUSABLE_SELECTOR =
  "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";

function closeTopDialog() {
  openDialogs[openDialogs.length - 1]?.close();
}

function handleDocumentEscape(event: globalThis.KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (openDialogs.length === 0) return;
  event.preventDefault();
  closeTopDialog();
}

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
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const entryRef = useRef<DialogEntry | null>(null);

  // BD-127：入栈/出栈 + 单一 document Escape 监听（焦点不在弹窗内时也兜住）。
  useEffect(() => {
    if (!open) return;
    const entry: DialogEntry = { close: () => onCloseRef.current?.() };
    entryRef.current = entry;
    openDialogs.push(entry);
    if (!escapeListening) {
      document.addEventListener("keydown", handleDocumentEscape);
      escapeListening = true;
    }
    return () => {
      const index = openDialogs.indexOf(entry);
      if (index !== -1) openDialogs.splice(index, 1);
      if (entryRef.current === entry) entryRef.current = null;
      if (openDialogs.length === 0 && escapeListening) {
        document.removeEventListener("keydown", handleDocumentEscape);
        escapeListening = false;
      }
    };
  }, [open]);

  // BD-127：打开时聚焦首个可操作元素；关闭时把焦点还给打开前的元素（否则键盘用户
  // 关闭弹窗后落到 body，下次 Tab 从页面开头重新走）。元素已卸载则跳过。
  useEffect(() => {
    if (!open) return;
    const restoreTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = requestAnimationFrame(() => {
      const el = dialogRef.current?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      el?.focus();
    });
    return () => {
      cancelAnimationFrame(frame);
      if (restoreTo && restoreTo.isConnected) restoreTo.focus({ preventScroll: true });
    };
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
      {/* 批次 AN：原本挂 shadow-2xl（Tailwind 刻度外档，0 25px 50px -12px 黑 .25）。
        * §5.3 把对话框定为 --shadow-3，这里改成属性形式 [box-shadow:var(--shadow-3)]——
        * 只有属性形式才真发射 box-shadow，方括号工具类 shadow-[var(...)] 在产物里只产
        * --tw-shadow-color（批次 K 实测并钉成硬红）。border 留着：--shadow-3 是纯投影档、
        * 不含 hairline 环，AE 的双拼规矩不管它。本组件没有家族类接管，所以这是**真改版**。 */}
      <div
        ref={dialogRef}
        className={`relative z-10 w-full ${width} rounded-[var(--radius-3)] border border-paper-line bg-paper-panel [box-shadow:var(--shadow-3)] ${className}`}
        onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
          // BD-127：焦点在弹窗内时事件到不了 document（这里 stopPropagation 拦住了
          // 外层快捷键），所以就地关栈顶；document 监听只负责焦点不在弹窗内的兜底。
          if (e.key === "Escape") {
            e.stopPropagation();
            closeTopDialog();
            return;
          }
          // BD-128：aria-modal 承诺了「弹窗外是惰性的」，但浏览器不会自动这么做——
          // 实测 Tab 到最后一个控件后焦点直接跑到弹窗后的窗口控制按钮上。这里按首尾成环。
          if (e.key === "Tab") {
            const root = dialogRef.current;
            if (!root) return;
            const items = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
            if (items.length === 0) {
              e.preventDefault();
              return;
            }
            const first = items[0];
            const last = items[items.length - 1];
            const active = document.activeElement;
            if (e.shiftKey) {
              if (active === first || !root.contains(active)) {
                e.preventDefault();
                last.focus();
              }
            } else if (active === last || !root.contains(active)) {
              e.preventDefault();
              first.focus();
            }
          }
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
