import { forwardRef, type SelectHTMLAttributes } from "react";
import { ChevronDown } from "lucide-react";

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options?: SelectOption[];
  label?: string;
}

/**
 * Select — 统一下拉选择器原语
 *
 * 封装原生 select + 自定义向下箭头，继承 paper-input 风格与焦点光晕。
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { options, label, className = "", children, ...props },
  ref
) {
  const select = (
    <div className="relative inline-flex items-center w-full">
      <select
        ref={ref}
        className={`paper-input h-9 w-full appearance-none pr-8 text-sm ${className}`}
        {...props}
      >
        {options
          ? options.map((opt) => (
              <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                {opt.label}
              </option>
            ))
          : children}
      </select>
      <ChevronDown
        size={14}
        className="pointer-events-none absolute right-2.5 text-paper-muted"
      />
    </div>
  );

  if (label) {
    return (
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">{label}</span>
        {select}
      </label>
    );
  }

  return select;
});
