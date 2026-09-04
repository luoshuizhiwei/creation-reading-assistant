import {
  useEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode
} from "react";
import { AlertOctagon, AlertTriangle, CheckCircle2, HelpCircle, Info, X, XCircle } from "lucide-react";
import { Button } from "@/components/ui";
import { useUIStore, type ToastTone } from "@/stores/ui-store";

/**
 * 键盘焦点可见环（等价 :focus-visible 的常见降级实现）：
 * 指针按下后聚焦不显示（鼠标点击），Tab / 脚本聚焦显示；
 * 焦点元素上按任意键视为键盘交互并恢复显示。
 * 用于无法改动样式表时给裸 button/input 提供可见焦点指示。
 */
export function useFocusRing() {
  const [ringVisible, setRingVisible] = useState(false);
  const pointerActiveRef = useRef(false);

  const ringStyle: CSSProperties | undefined = ringVisible
    ? { outline: "2px solid var(--copper)", outlineOffset: "2px" }
    : undefined;

  const handlers = {
    onFocus: () => {
      if (!pointerActiveRef.current) setRingVisible(true);
    },
    onBlur: () => {
      // 指针状态随失焦复位，避免下一次键盘聚焦被旧的 pointer 标记卡住。
      pointerActiveRef.current = false;
      setRingVisible(false);
    },
    onPointerDown: (event: ReactPointerEvent<HTMLElement>) => {
      // 点击按钮任意后代（span/strong/svg）都属于指针操作，不显示键盘焦点环。
      if (event.currentTarget.contains(event.target as Node)) pointerActiveRef.current = true;
      setRingVisible(false);
    },
    onKeyDown: (event: ReactKeyboardEvent<HTMLElement>) => {
      if (event.target === event.currentTarget) pointerActiveRef.current = false;
      setRingVisible(true);
    }
  };

  return { ringStyle, handlers };
}

/** 带键盘焦点环的按钮：替代裸 <button>，无需修改全局样式表。 */
export function RingButton({ className, style, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  const ring = useFocusRing();
  return (
    <button {...ring.handlers} {...props} style={{ ...ring.ringStyle, ...style }} className={className}>
      {children}
    </button>
  );
}

const toastIcon: Record<ToastTone, typeof CheckCircle2> = {
  success: CheckCircle2,
  info: Info,
  warning: AlertTriangle,
  error: XCircle
};

const toastClass: Record<ToastTone, string> = {
  success: "border-moss/25 bg-moss-soft text-moss",
  info: "border-copper/25 bg-paper-panel text-paper-ink",
  warning: "border-amber-300 bg-amber-50 text-amber-800",
  error: "border-red-200 bg-red-50 text-red-700"
};

export function ToastCenter() {
  const toasts = useUIStore((state) => state.toasts);
  const dismissToast = useUIStore((state) => state.dismissToast);

  return (
    <div className="pointer-events-none absolute right-5 top-5 z-[70] grid w-[min(380px,calc(100vw-40px))] gap-2">
      {toasts.map((toast) => {
        const Icon = toastIcon[toast.tone];
        return (
          <article key={toast.id} className={`motion-toast pointer-events-auto rounded-xl border p-3 shadow-paper ${toastClass[toast.tone]}`}>
            <div className="flex items-start gap-3">
              <Icon className="mt-0.5 shrink-0" size={17} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-semibold">{toast.title}</div>
                {toast.body && <div className="mt-1 text-xs leading-5 opacity-80">{toast.body}</div>}
              </div>
              <button className="rounded-md p-1 opacity-70 hover:bg-white/35 hover:opacity-100" onClick={() => dismissToast(toast.id)} title="关闭提示">
                <X size={14} />
              </button>
            </div>
          </article>
        );
      })}
    </div>
  );
}

export function ConfirmDialog() {
  const request = useUIStore((state) => state.confirmRequest);
  const resolveConfirm = useUIStore((state) => state.resolveConfirm);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!request) return undefined;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        resolveConfirm(false);
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [request, resolveConfirm]);

  useEffect(() => {
    if (!request) return;
    confirmRef.current?.focus();
  }, [request]);

  if (!request) return null;

  const danger = request.tone === "danger";
  const warning = request.tone === "warning";
  const Icon = danger ? AlertOctagon : warning ? AlertTriangle : HelpCircle;
  return (
    <div className="absolute inset-0 z-[80] grid place-items-center bg-paper-ink/18 px-6 backdrop-blur-sm" onClick={() => resolveConfirm(false)}>
      <section className="motion-dialog w-[min(460px,100%)] overflow-hidden rounded-2xl border border-paper-line bg-paper-panel shadow-paper" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-message" onClick={(e) => e.stopPropagation()}>
        <div className={`h-1 ${danger ? "bg-red-500" : warning ? "bg-amber-500" : "bg-copper"}`} />
        <div className="p-5">
          <div className="flex items-start gap-3">
            <div className={`rounded-full p-2 ${danger ? "bg-red-50 text-red-700" : warning ? "bg-amber-50 text-amber-800" : "bg-copper/10 text-copper"}`}>
              <Icon size={18} />
            </div>
            <div className="min-w-0 flex-1">
              <h2 id="confirm-dialog-title" className="paper-title text-lg font-semibold text-paper-ink">{request.title}</h2>
              <p id="confirm-dialog-message" className="mt-2 text-sm leading-6 text-paper-muted">{request.body}</p>
            </div>
          </div>
          <div className="mt-5 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => resolveConfirm(false)}>
              {request.cancelLabel}
            </Button>
            <Button ref={confirmRef} className={danger ? "bg-red-700 hover:bg-red-800" : ""} onClick={() => resolveConfirm(true)}>
              {request.confirmLabel}
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}

export function PageTransition({ screenKey, children }: { screenKey: string; children: ReactNode }) {
  return (
    <div key={screenKey} className="motion-page h-full">
      {children}
    </div>
  );
}

export function AnimatedPanel({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return (
    <div className={`motion-panel ${className}`} style={{ animationDelay: `${delay}ms` }}>
      {children}
    </div>
  );
}

export function InlineNotice({ children, tone = "info", className = "" }: { children: ReactNode; tone?: ToastTone; className?: string }) {
  return <div className={`motion-notice rounded-xl border p-3 text-sm leading-6 ${toastClass[tone]} ${className}`}>{children}</div>;
}
