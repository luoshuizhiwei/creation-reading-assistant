import type { ReaderPreset, ReaderSettings } from "@/types/library";

/** 从 ReaderSettings 提取预设覆盖的排版字段（与 ReaderPreset 口径一致） */
export function presetToSettingsPatch(preset: ReaderPreset): Partial<ReaderSettings> {
  return {
    fontSize: preset.fontSize,
    lineHeight: preset.lineHeight,
    pageMargin: preset.pageMargin,
    paragraphSpacing: preset.paragraphSpacing,
    letterSpacing: preset.letterSpacing,
    readerBackground: preset.readerBackground,
    ...(preset.fontFamily ? { fontFamily: preset.fontFamily } : {})
  };
}

/** 应用某个预设；找不到时返回 undefined */
export function applyPreset(settings: ReaderSettings, presetId: string): Partial<ReaderSettings> | undefined {
  const preset = settings.presets?.find((item) => item.id === presetId);
  return preset ? presetToSettingsPatch(preset) : undefined;
}

/** 用当前设置构建一个新预设 */
export function buildPresetFromSettings(settings: ReaderSettings, name: string): ReaderPreset {
  return {
    id: `preset-${Date.now()}`,
    name,
    fontSize: settings.fontSize,
    lineHeight: settings.lineHeight,
    pageMargin: settings.pageMargin,
    paragraphSpacing: settings.paragraphSpacing ?? 1.0,
    letterSpacing: settings.letterSpacing ?? 0,
    readerBackground: settings.readerBackground,
    fontFamily: settings.fontFamily
  };
}
