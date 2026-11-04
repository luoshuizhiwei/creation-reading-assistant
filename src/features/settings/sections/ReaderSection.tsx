import { useState } from "react";
import { RotateCcw } from "lucide-react";
import { Button, FontPicker, Select, Switch, TextInput } from "@/components/ui";
import { BackgroundPicker } from "@/features/settings/reader/BackgroundPicker";
import { ReaderTrackingControls } from "@/features/settings/reader/ReaderTrackingControls";
import { ReaderTypographyControls } from "@/features/settings/reader/ReaderTypographyControls";
import { applyPreset, buildPresetFromSettings } from "@/features/settings/reader/preset-utils";
import { savePreset } from "@/services/reader-service";
import { resetReaderSettings } from "@/services/settings-service";
import type { ReaderPreset, ReaderTrackingSettings } from "@/types/library";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "@/types/settings";
import { SettingsGroup, SettingsShell } from "./SectionWrapper";

interface ReaderSectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  applySettings: (settings: AppSettings) => void;
  loadSettings: () => Promise<AppSettings | undefined>;
  resetSection: (section: SettingsSection) => void;
  showToast: (toast: { tone: "success" | "info" | "warning" | "error"; title: string; body?: string }) => void;
  setError: (error: string) => void;
}

const TYPOGRAPHY_ITEMS = ["fontSize", "lineHeight", "paragraphSpacing", "letterSpacing", "pageMargin"] as const;

export function ReaderSection({
  settings,
  patchSettings,
  applySettings,
  loadSettings,
  resetSection,
  showToast,
  setError
}: ReaderSectionProps) {
  const [presetName, setPresetName] = useState("");
  const [selectedPresetId, setSelectedPresetId] = useState("");

  const patchReader = (patch: Partial<AppSettings["reader"]>) => {
    void patchSettings({ reader: patch });
  };

  const patchReaderTracking = (trackingPatch: Partial<ReaderTrackingSettings>) => {
    patchReader({ tracking: { ...settings.reader.tracking, ...trackingPatch } });
  };

  const resetReaderDefaults = async () => {
    try {
      const next = await resetReaderSettings();
      applySettings(next);
      showToast({ tone: "success", title: "阅读设置已恢复默认", body: "字号、行距、页边距和书籍背景已回到初始值。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const handleApplyPreset = (presetId: string) => {
    setSelectedPresetId("");
    if (!presetId) return;
    const patch = applyPreset(settings.reader, presetId);
    if (patch) patchReader(patch);
  };

  const handleSavePreset = async () => {
    const name = presetName.trim();
    if (!name) return;
    const preset: ReaderPreset = buildPresetFromSettings(settings.reader, name);
    try {
      await savePreset(preset);
      setPresetName("");
      await loadSettings();
      showToast({ tone: "success", title: `已保存预设「${name}」`, body: "可在设置页或阅读抽屉的预设下拉中一键应用。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  return (
    <SettingsShell title="阅读器">
      <SettingsGroup
        title="排版"
        description="字号、行距、页边距和书籍背景只影响阅读页，不影响应用整体主题。"
        actions={
          <Button variant="secondary" onClick={() => void resetReaderDefaults()}>
            <RotateCcw size={15} />
            恢复阅读默认
          </Button>
        }
      >
        <ReaderTypographyControls settings={settings.reader} onPatch={patchReader} items={[...TYPOGRAPHY_ITEMS]} />
      </SettingsGroup>

      <SettingsGroup title="字体与背景">
        <div className="grid gap-4">
          <div data-setting-id="reader.fontFamily">
            <FontPicker label="字体" value={settings.reader.fontFamily} onChange={(fontFamily) => patchReader({ fontFamily })} />
          </div>
          <BackgroundPicker
            settingId="reader.readerBackground"
            value={settings.reader.readerBackground}
            onChange={(readerBackground) => patchReader({ readerBackground })}
          />
        </div>
      </SettingsGroup>

      <SettingsGroup title="格式与行为">
        <div className="grid gap-4">
          <div data-setting-id="reader.epubStyleMode">
            <Select
              label="EPUB 样式"
              value={settings.reader.epubStyleMode}
              onChange={(event) => patchReader({ epubStyleMode: event.target.value as typeof settings.reader.epubStyleMode })}
              options={[
                { value: "publisher", label: "保留原书样式" },
                { value: "unified", label: "统一阅读样式" }
              ]}
            />
          </div>
          <div data-setting-id="reader.textConversion">
            <Select
              label="繁简转换"
              value={settings.reader.textConversion || "none"}
              onChange={(event) => patchReader({ textConversion: event.target.value as typeof settings.reader.textConversion })}
              options={[
                { value: "none", label: "不转换" },
                { value: "s2t", label: "简体 → 繁体" },
                { value: "t2s", label: "繁体 → 简体" }
              ]}
            />
          </div>
          <div data-setting-id="reader.restoreLastPosition">
            <Switch
              checked={settings.reader.restoreLastPosition}
              onChange={(restoreLastPosition) => patchReader({ restoreLastPosition })}
              label="自动恢复上次位置"
            />
          </div>
        </div>
      </SettingsGroup>

      <SettingsGroup title="阅读记录">
        <ReaderTrackingControls tracking={settings.reader.tracking} onPatch={patchReaderTracking} />
      </SettingsGroup>

      <SettingsGroup
        title="预设"
        description="预设保存当前排版组合，可在设置页或阅读抽屉中一键应用（如通勤 / 夜间场景）。"
        actions={
          <Button variant="quiet" onClick={() => resetSection("reader")}>
            <RotateCcw size={15} />
            重置本分区
          </Button>
        }
      >
        <div data-setting-id="reader.presets" className="grid gap-3">
          <Select
            label="应用预设"
            value={selectedPresetId}
            onChange={(event) => handleApplyPreset(event.target.value)}
            options={[
              { value: "", label: "选择预设..." },
              ...(settings.reader.presets?.map((preset: ReaderPreset) => ({ value: preset.id, label: preset.name })) ?? [])
            ]}
          />
          <div className="flex items-center gap-2">
            <TextInput
              value={presetName}
              onChange={(event) => setPresetName(event.target.value)}
              placeholder="新预设名称"
              aria-label="阅读预设名称"
              className="flex-1"
            />
            <Button variant="secondary" disabled={!presetName.trim()} onClick={() => void handleSavePreset()}>
              保存当前排版
            </Button>
          </div>
        </div>
      </SettingsGroup>
    </SettingsShell>
  );
}
