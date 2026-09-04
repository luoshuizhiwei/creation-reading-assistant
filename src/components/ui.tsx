import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "quiet" }>(function Button({
  children,
  className = "",
  variant = "primary",
  ...props
}, ref) {
  const base =
    "inline-flex h-9 items-center justify-center gap-2 rounded-lg px-3 text-sm font-medium transition duration-150 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-copper/40";
  const variants = {
    primary: "bg-copper text-white shadow-[0_6px_16px_rgba(184,64,26,0.22)] hover:-translate-y-0.5 hover:bg-copper-dark hover:shadow-lift",
    secondary: "border border-paper-line bg-paper-panel text-paper-ink hover:-translate-y-0.5 hover:border-copper/50 hover:bg-paper-soft/60 hover:shadow-lift",
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
