import type { ReactNode } from "react";

export interface TabItem<T extends string = string> {
  id: T;
  label: ReactNode;
  disabled?: boolean;
}

export interface TabsProps<T extends string = string> {
  value: T;
  onChange: (value: T) => void;
  items: TabItem<T>[];
  ariaLabel?: string;
  className?: string;
  tabClassName?: string;
  variant?: "pill" | "underline";
}

/**
 * Tabs — 统一标签页/分段切换原语
 *
 * 规范 role="tablist" / role="tab" / aria-selected，
 * 支持下划线风格（underline）和胶囊风格（pill）。
 */
export function Tabs<T extends string = string>({
  value,
  onChange,
  items,
  ariaLabel,
  className = "",
  tabClassName = "",
  variant = "underline"
}: TabsProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={`flex items-center gap-1 ${
        variant === "pill"
          ? "rounded-lg bg-paper-soft p-1 border border-paper-line"
          : "border-b border-paper-line"
      } ${className}`}
    >
      {items.map((item) => {
        const active = item.id === value;
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={active}
            disabled={item.disabled}
            onClick={() => onChange(item.id)}
            className={`${
              variant === "pill"
                ? `px-3 py-1 text-xs font-medium rounded-md transition ${
                    active
                      ? "bg-paper-panel text-copper shadow-sm font-semibold"
                      : "text-paper-muted hover:text-paper-ink"
                  }`
                : `px-3 py-2 text-sm font-medium transition border-b-2 -mb-px ${
                    active
                      ? "border-copper text-copper font-semibold"
                      : "border-transparent text-paper-muted hover:text-paper-ink"
                  }`
            } ${tabClassName}`}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
