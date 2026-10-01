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
  // 批次 C：这两条原先用的是 Tailwind 默认调色板（amber-*/red-*），那批颜色
  // 不随主题翻转——夜校里它们是把晨校的浅岛原样画在深色画布上，岛内文字实测
  // 掉到 3.95 / 3.14 / 1.14:1。改走 --warning / --proof-mark 令牌族，
  // 岛底用 --warning-tint / --proof-tint，边框用 color-mix（不能给 var() 套
  // Tailwind 的 /alpha 修饰符，那样整条声明会被静默丢弃）。
  // 实算：警示 5.11 / 4.99，校样红 4.85 / 5.11（晨 / 夜，均 ≥ AA 4.5）。
  warning: "border-[color:color-mix(in_srgb,var(--warning)_35%,var(--separator))] bg-[color:var(--warning-tint)] text-[color:var(--warning)]",
  error: "border-[color:color-mix(in_srgb,var(--proof-mark)_35%,var(--separator))] bg-[color:var(--proof-tint)] text-[color:var(--proof-mark)]"
};

export function ToastCenter() {
  const toasts = useUIStore((state) => state.toasts);
  const dismissToast = useUIStore((state) => state.dismissToast);

  return (
    <div className="pointer-events-none absolute right-5 top-5 z-[70] grid w-[min(380px,calc(100vw-40px))] gap-2">
      {toasts.map((toast) => {
        const Icon = toastIcon[toast.tone];
        return (
          <article key={toast.id} className={`motion-toast pointer-events-auto rounded-[var(--radius-3)] border p-3 shadow-paper ${toastClass[toast.tone]}`}>
            <div className="flex items-start gap-3">
              <Icon className="mt-0.5 shrink-0" size={17} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-semibold">{toast.title}</div>
                {toast.body && <div className="mt-1 text-xs leading-5">{toast.body}</div>}
              </div>
              {/* 关闭键原来是 `opacity-70 hover:bg-white/35 hover:opacity-100`，两个毛病：
                  ① opacity-70 让图标在静止态和岛底混色，实算 14px 的 × 在警示/错误
                     toast 上只剩 2.91 / 2.90:1（晨校），连非文字的 3:1 都不到；
                  ② hover 那层 35% 白正是批次 A 立的「写死白墨」不变量的镜像形态——
                     白不是前景而是叠加层，压在会随主题翻转的岛底上：夜校 error toast
                     里 #e07965 压过 35% 白叠过的 #33221e 只剩 1.41:1（按下反而看不清）。
                  改法：静止态不透明（× 走继承来的 tone 前景色，5.11 / 4.85 / 5.00 / 14.79），
                  hover 叠 10% 前景墨加深岛底。--text-primary 本身分主题，两主题都是同方向
                  的加深，hover 后最低 3.79:1，仍高于非文字的 3:1。
                  正文同时去掉 opacity-80：那层 80% 透明度把警示/错误正文从
                  5.11 / 4.85 压到 3.46 / 3.45（晨校），不满足 12px 正文的 AA 4.5；
                  被替换的旧 amber-800 也只有 4.41，一直没达标。层级靠字号与半粗体已经分开。 */}
              <button
                className="rounded-md p-1 hover:bg-[color:color-mix(in_srgb,var(--text-primary)_10%,transparent)]"
                onClick={() => dismissToast(toast.id)}
                title="关闭提示"
              >
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
      <section className="motion-dialog w-[min(460px,100%)] overflow-hidden rounded-[var(--radius-3)] border border-paper-line bg-paper-panel shadow-paper" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-message" onClick={(e) => e.stopPropagation()}>
        {/* 顶部 1px 状态条：实色底，走令牌族后晨/夜两边都过（红 5.45 / 5.59，
            琥珀 5.60 / 5.23 压在面板上）。原先的 bg-red-500(#ef4444) 在晨校面板只有
            3.57:1，且夜校还是同一支亮红——它不跟主题走。 */}
        <div className={`h-1 ${danger ? "bg-[color:var(--proof-mark)]" : warning ? "bg-[color:var(--warning)]" : "bg-copper"}`} />
        <div className="p-5">
          <div className="flex items-start gap-3">
            <div className={`rounded-full p-2 ${danger ? "bg-[color:var(--proof-tint)] text-[color:var(--proof-mark)]" : warning ? "bg-[color:var(--warning-tint)] text-[color:var(--warning)]" : "bg-copper/10 text-copper"}`}>
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
            {/* 危险确认按钮原先用 className 覆盖成 bg-red-700/hover:bg-red-800，
                这是「用工具类压掉组件自带底色」的老写法：Tailwind 同类工具按值排序发射，
                谁赢取决于两条声明的先后，而不是作者意图（第 6 步已经在 ui.tsx 里为
                justify-start 踩过一次）。而且 --proof-mark 就是本仓的危险实色底，
                白字压在 bg-red-700(#b91c1c) 上晨校 6.47、夜校仍是这层深红配浅墨
                （--fg-on-solid 在夜校是 #15181c）只剩 2.75:1——不跟主题走的底色，
                配上跟主题走的前景，正是批次 A 立的那条不变量的形状。
                组件的 danger-filled 两主题都是 5.74 / 6.01。 */}
            <Button variant={danger ? "danger-filled" : "primary"} ref={confirmRef} onClick={() => resolveConfirm(true)}>
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
