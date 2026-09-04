import { Field, Select, TextInput } from "@/components/ui";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "@/types/settings";
import { Section, patchNumericSetting } from "./SectionWrapper";

interface AppearanceSectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  resetSection: (section: SettingsSection) => void;
}

export function AppearanceSection({ settings, patchSettings, resetSection }: AppearanceSectionProps) {
  return (
    <Section title="外观" section="appearance" onReset={resetSection}>
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
      <Field label="应用字体缩放">
        <TextInput
          type="number"
          min={0.85}
          max={1.4}
          step={0.05}
          value={settings.appearance.appFontScale}
          onChange={(event) =>
            patchNumericSetting(event.target.value, 0.85, 1.4, (appFontScale) => ({ appearance: { appFontScale } }), patchSettings)
          }
        />
      </Field>
    </Section>
  );
}
