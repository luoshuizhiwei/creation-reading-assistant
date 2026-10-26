import { Loader2 } from "lucide-react";

export interface SpinnerProps {
  /** 尺寸（像素，默认 16） */
  size?: number;
  /** 额外的容器类名 */
  className?: string;
}

/**
 * Spinner — 统一加载指示器原语
 *
 * 纯视觉装饰，配合 Tailwind animate-spin 动画。
 *
 * BD-195：此前自带 role="status" + aria-label（默认「加载中…」），是两个缺陷的
 * 叠加——① 十个使用点全是「插入即带内容」的挂载形态，polite 从不播报（BD-175
 * 判据），装饰是死的；② aria-label 混进宿主可及名：按钮忙态名变成「加载中… 导出中…」、
 * 写进 aria-live 容器里变成重复叠读。播报归宿主的常驻区（BD-187/189/192/193 的
 * 形态），Spinner 退回纯视觉。
 */
export function Spinner({ size = 16, className = "" }: SpinnerProps) {
  return (
    <span className={`inline-flex items-center justify-center ${className}`}>
      <Loader2 size={size} className="animate-spin text-copper" aria-hidden="true" />
    </span>
  );
}
