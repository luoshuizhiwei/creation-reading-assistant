import type { ReactNode } from "react";
import { AnimatedPanel } from "@/components/interaction";

/**
 * SettingsShell — 分区外壳：标题 + 纵向堆叠的分组卡片。
 * 分区级「重置」按钮由各分区的分组通过 actions 挂载（语义仍是按分区重置）。
 */
export function SettingsShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title}>
      <div className="grid gap-4">{children}</div>
    </section>
  );
}

/**
 * SettingsGroup — 分区内带小标题的分组卡片。
 * actions 放组级操作按钮（如「恢复阅读默认」「重置本分区」）。
 */
export function SettingsGroup({
  title,
  description,
  actions,
  children,
  className = ""
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <AnimatedPanel className={`rounded-[var(--radius-2)] border border-paper-line bg-paper-panel p-4 shadow-lift ${className}`}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-paper-ink">{title}</h2>
          {description && <p className="mt-1 text-xs leading-5 text-paper-muted">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">{actions}</div>}
      </div>
      {children}
    </AnimatedPanel>
  );
}
