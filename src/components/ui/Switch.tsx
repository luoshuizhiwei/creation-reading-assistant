import { useId, type ReactNode } from "react";

export interface SwitchProps {
  checked: boolean;
  onChange(checked: boolean): void;
  label: ReactNode;
  /** 辅助说明，显示在标签下方 */
  description?: ReactNode;
  disabled?: boolean;
  className?: string;
}

/**
 * Switch — 统一样式开关
 *
 * 原生 checkbox（sr-only）保证键盘/读屏行为，视觉由
 * settings-controls.css 的 .paper-switch-* 重绘。
 */
export function Switch({ checked, onChange, label, description, disabled = false, className = "" }: SwitchProps) {
  const inputId = useId();
  return (
    <div className={`flex items-center justify-between gap-3 ${className}`}>
      <label htmlFor={inputId} className="grid gap-0.5 text-sm text-paper-ink">
        <span>{label}</span>
        {description && <span className="text-xs leading-5 text-paper-muted">{description}</span>}
      </label>
      <span className="relative inline-flex shrink-0">
        <input
          id={inputId}
          type="checkbox"
          role="switch"
          className="paper-switch-input"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
        />
        <span className="paper-switch-track" aria-hidden="true" />
      </span>
    </div>
  );
}
