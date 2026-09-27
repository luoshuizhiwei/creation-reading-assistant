import { RotateCcw } from "lucide-react";
import { Button, Select, Slider } from "@/components/ui";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "@/types/settings";
import { SettingsGroup, SettingsShell } from "./SectionWrapper";

interface AppearanceSectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  resetSection: (section: SettingsSection) => void;
}

export function AppearanceSection({ settings, patchSettings, resetSection }: AppearanceSectionProps) {
  return (
    <SettingsShell title="外观">
      <SettingsGroup
        title="主题与缩放"
        actions={
          <Button variant="quiet" onClick={() => resetSection("appearance")}>
            <RotateCcw size={15} />
            重置本分区
          </Button>
        }
      >
        <div className="grid max-w-md gap-4">
          <div data-setting-id="appearance.theme">
            <Select
              label="应用主题"
              value={settings.appearance.theme}
              onChange={(event) => void patchSettings({ appearance: { theme: event.target.value as typeof settings.appearance.theme } })}
              options={[
                { value: "system", label: "跟随系统" },
                { value: "light", label: "浅色" },
                { value: "dark", label: "深色" }
              ]}
            />
          </div>
          <div data-setting-id="appearance.appFontScale">
            <Slider
              label="应用字体缩放"
              min={0.85}
              max={1.4}
              step={0.05}
              format={(value) => `${Math.round(value * 100)}%`}
              value={settings.appearance.appFontScale}
              onChange={(appFontScale) => void patchSettings({ appearance: { appFontScale } })}
            />
          </div>
        </div>
      </SettingsGroup>
    </SettingsShell>
  );
}
