import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import "@/features/settings/settings-controls.css";

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "quiet" }>(function Button({
  children,
  className = "",
  variant = "primary",
  ...props
}, ref) {
  const base =
    "inline-flex h-9 items-center justify-center gap-2 rounded-lg px-3 text-sm font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-45 focus-visible:outline-none focus-visible:[box-shadow:var(--focus-ring)]";
  const variants = {
    // D-1：主按钮阴影原为 rgba(184,64,26,.22)（旧铜色硬编码），与 bg-copper
    // 实际解析到的印刷蓝无关，是主题迁移遗留。规格 §2.5 定为按钮一律无阴影，
    // 反馈只靠颜色变化；hover 位移与 active 缩放同样移除（排版抖动来源）。
    primary: "bg-copper text-white hover:bg-copper-dark",
    secondary: "border border-paper-line bg-paper-panel text-paper-ink hover:border-copper/50 hover:bg-paper-soft/60",
    quiet: "text-paper-muted hover:bg-paper-soft/70 hover:text-paper-ink"
  };
  return (
    <button ref={ref} data-variant={variant} className={`${base} ${variants[variant]} ${className}`} {...props}>
      {children}
    </button>
  );
});

export function TextInput({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`paper-input h-9 ${className}`}
      {...props}
    />
  );
}

export function TextArea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={`paper-input px-3 py-2 leading-6 ${className}`}
      {...props}
    />
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1.5 text-sm text-paper-muted">
      <span className="font-medium text-paper-ink">{label}</span>
      {children}
    </label>
  );
}

export function ShellPanel({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`paper-panel ${className}`}>{children}</section>;
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="grid h-full place-items-center px-8 py-10 text-center" role="status" aria-live="polite">
      <div className="max-w-sm">
        <h2 className="paper-title text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-paper-muted">{body}</p>
      </div>
    </div>
  );
}

export * from "./ui/Dialog";
export * from "./ui/Tabs";
export * from "./ui/Select";
export * from "./ui/Spinner";
export * from "./ui/Slider";
export * from "./ui/Switch";
export * from "./ui/NumberStepper";
export * from "./ui/FontPicker";
