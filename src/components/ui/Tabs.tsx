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
 * Tabs — 统一分段切换原语
 *
 * 这一组按钮切换的是整块视图，没有配套的 role="tabpanel"/aria-controls，
 * 所以不用 tablist/tab 语义（读屏器会承诺方向键切换的标签页交互，实际不成立）；
 * 按本库的分段按钮约定，用 role="group" + 每颗 aria-pressed 暴露当前档位。
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
      role="group"
      aria-label={ariaLabel}
      className={`flex items-center gap-1 ${
        variant === "pill"
          ? "rounded-md bg-paper-soft p-1 border border-paper-line"
          : "border-b border-paper-line"
      } ${className}`}
    >
      {items.map((item) => {
        const active = item.id === value;
        return (
          <button
            key={item.id}
            type="button"
            aria-pressed={active}
            disabled={item.disabled}
            onClick={() => onChange(item.id)}
            className={`${
              variant === "pill"
                ? `px-3 py-1 text-xs font-medium rounded-md transition ${
                    // 批次 AP：选中态原本挂 shadow-sm，按规格 §2.5「按钮一律无阴影」删掉。
                    // 这个药丸是普通 <button>，§2.5 管的就是按钮角色——阴影棘轮豁免
                    // 刻度内令牌，但 §2.5 连令牌也不豁免，所以还法只有「不画投影」这一条。
                    // 选中态的辨识度交给纯色彩反馈（§2.1 指定的方向）：字色 copper 压 panel
                    // 晨 6.15:1 / 夜 6.53:1，未选中 muted 压 soft 晨 4.70:1 / 夜 7.72:1，
                    // 两者本身色差就大，外加 font-semibold 与 aria-pressed。
                    active
                      ? "bg-paper-panel text-copper font-semibold"
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
