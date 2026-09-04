import type { ReactNode } from "react";
import { RotateCcw } from "lucide-react";
import { AnimatedPanel } from "@/components/interaction";
import { Button } from "@/components/ui";
import type { AppSettingsPatch, SettingsSection } from "@/types/settings";

export function Section({
  title,
  section,
  children,
  onReset
}: {
  title: string;
  section: SettingsSection;
  children: ReactNode;
  onReset: (section: SettingsSection) => void;
}) {
  return (
    <AnimatedPanel className="rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-paper-ink">{title}</h2>
        <Button variant="quiet" onClick={() => onReset(section)}>
          <RotateCcw size={15} />
          重置
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-4">{children}</div>
    </AnimatedPanel>
  );
}

export function patchNumericSetting(
  rawValue: string,
  min: number,
  max: number,
  buildPatch: (value: number) => AppSettingsPatch,
  patchSettings: (patch: AppSettingsPatch) => void
) {
  if (rawValue.trim() === "") return;
  const value = Number(rawValue);
  if (!Number.isFinite(value)) return;
  void patchSettings(buildPatch(Math.min(max, Math.max(min, value))));
}
