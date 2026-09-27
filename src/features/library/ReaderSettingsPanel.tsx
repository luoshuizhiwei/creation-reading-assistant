import { useState } from "react";
import { Select } from "@/components/ui";
import { ALL_TYPOGRAPHY_ITEMS, ReaderTypographyControls } from "@/features/settings/reader/ReaderTypographyControls";
import { applyPreset } from "@/features/settings/reader/preset-utils";
import type { ReaderPreset, ReaderSettings } from "@/types/library";

/**
 * 阅读抽屉主视图：预设应用 + 全部排版高频项。
 * EPUB 样式、繁简转换、阅读记录等低频项在抽屉的「更多设置」二级视图
 * （ReaderSettingsMorePanel）里，不再出现在主视图。
 */
export function ReaderSettingsPanel({
  settings,
  onChange
}: {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
}) {
  const [selectedPresetId, setSelectedPresetId] = useState("");

  return (
    <div className="grid gap-4">
      <div>
        <Select
          label="预设"
          aria-label="应用阅读预设"
          value={selectedPresetId}
          onChange={(event) => {
            const presetId = event.target.value;
            setSelectedPresetId("");
            if (!presetId) return;
            const patch = applyPreset(settings, presetId);
            if (patch) onChange(patch);
          }}
          options={[
            { value: "", label: "一键切换排版场景..." },
            ...(settings.presets?.map((preset: ReaderPreset) => ({ value: preset.id, label: preset.name })) ?? [])
          ]}
        />
      </div>
      <ReaderTypographyControls settings={settings} onPatch={onChange} items={ALL_TYPOGRAPHY_ITEMS} />
    </div>
  );
}
