import { Loader2 } from "lucide-react";

export interface SpinnerProps {
  /** 尺寸（像素，默认 16） */
  size?: number;
  /** 额外的容器类名 */
  className?: string;
  /** 可访问性提示（默认「加载中…」） */
  label?: string;
}

/**
 * Spinner — 统一加载指示器原语
 *
 * 规范 role="status" 和 aria-label，配合 Tailwind animate-spin 动画。
 */
export function Spinner({ size = 16, className = "", label = "加载中…" }: SpinnerProps) {
  return (
    <span className={`inline-flex items-center justify-center ${className}`} role="status" aria-label={label}>
      <Loader2 size={size} className="animate-spin text-copper" />
    </span>
  );
}
