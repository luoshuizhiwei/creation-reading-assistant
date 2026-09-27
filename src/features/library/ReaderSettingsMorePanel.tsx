import { useState } from "react";
import { RotateCcw } from "lucide-react";
import { Button, Select, Switch, TextInput } from "@/components/ui";
import { ReaderTrackingControls } from "@/features/settings/reader/ReaderTrackingControls";
import { buildPresetFromSettings } from "@/features/settings/reader/preset-utils";
import { savePreset } from "@/services/reader-service";
import type { ReaderPreset, ReaderSettings } from "@/types/library";

export interface ReaderSettingsMorePanelProps {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
  onReset?: () => void;
  /** 当前阅读格式：EPUB 样式只对 EPUB 生效 */
  format?: "txt" | "md" | "epub";
}

/**
 * 阅读抽屉「更多设置」二级视图：承载低频项
 * （EPUB 样式、繁简转换、位置恢复、阅读记录、预设保存、恢复默认），
 * 主视图只保留排版高频项。不离开阅读页。
 */
export function ReaderSettingsMorePanel({ settings, onChange, onReset, format = "txt" }: ReaderSettingsMorePanelProps) {
  const [presetName, setPresetName] = useState("");
  const [saveMessage, setSaveMessage] = useState("");

  const handleSavePreset = async () => {
    const name = presetName.trim();
    if (!name) return;
    const preset: ReaderPreset = buildPresetFromSettings(settings, name);
    await savePreset(preset);
    onChange({ presets: [...(settings.presets ?? []), preset] });
    setPresetName("");
    setSaveMessage(`已保存预设「${name}」`);
    setTimeout(() => setSaveMessage(""), 2000);
  };

  return (
    <div className="grid gap-5">
      {format === "epub" && (
        <Select
          label="EPUB 样式"
          value={settings.epubStyleMode}
          onChange={(event) => onChange({ epubStyleMode: event.target.value as ReaderSettings["epubStyleMode"] })}
          options={[
            { value: "publisher", label: "保留原书样式" },
            { value: "unified", label: "统一阅读样式" }
          ]}
        />
      )}

      <Select
        label="繁简转换"
        value={settings.textConversion || "none"}
        onChange={(event) => onChange({ textConversion: event.target.value as ReaderSettings["textConversion"] })}
        options={[
          { value: "none", label: "不转换" },
          { value: "s2t", label: "简体 → 繁体" },
          { value: "t2s", label: "繁体 → 简体" }
        ]}
      />

      <Switch
        checked={settings.restoreLastPosition}
        onChange={(restoreLastPosition) => onChange({ restoreLastPosition })}
        label="自动恢复上次位置"
      />

      <div className="grid gap-3 border-t border-paper-line pt-4">
        <div className="text-sm font-semibold text-paper-ink">阅读记录</div>
        <ReaderTrackingControls
          tracking={settings.tracking}
          onPatch={(trackingPatch) => onChange({ tracking: { ...settings.tracking, ...trackingPatch } })}
        />
      </div>

      <div className="grid gap-2 border-t border-paper-line pt-4">
        <div className="text-sm font-semibold text-paper-ink">保存当前排版为预设</div>
        <div className="flex items-center gap-2">
          <TextInput
            value={presetName}
            onChange={(event) => setPresetName(event.target.value)}
            placeholder="预设名称"
            aria-label="阅读预设名称"
            className="flex-1"
          />
          <Button variant="secondary" disabled={!presetName.trim()} onClick={() => void handleSavePreset()}>
            保存
          </Button>
        </div>
        {saveMessage && <div className="text-xs text-paper-muted">{saveMessage}</div>}
      </div>

      {onReset && (
        <div className="border-t border-paper-line pt-4">
          <Button variant="secondary" className="w-full" onClick={onReset}>
            <RotateCcw size={15} />
            恢复阅读默认
          </Button>
        </div>
      )}
    </div>
  );
}
