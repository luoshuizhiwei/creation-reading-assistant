import { FontPicker, NumberStepper, Slider } from "@/components/ui";
import type { ReaderSettings } from "@/types/library";
import { BackgroundPicker } from "./BackgroundPicker";

/**
 * 可共享的排版控制项。
 * 这个联合类型同时是桌面端「按本书覆盖」未来的 key 口径
 * （对齐 Android 端 ReaderOverrideKey 的设计）。
 */
export type ReaderTypographyItem =
  | "fontSize"
  | "lineHeight"
  | "paragraphSpacing"
  | "letterSpacing"
  | "pageMargin"
  | "fontFamily"
  | "readerBackground";

export const ALL_TYPOGRAPHY_ITEMS: ReaderTypographyItem[] = [
  "fontSize",
  "lineHeight",
  "paragraphSpacing",
  "letterSpacing",
  "pageMargin",
  "fontFamily",
  "readerBackground"
];

export interface ReaderTypographyControlsProps {
  settings: ReaderSettings;
  /** 统一写回口径：只吐 Partial<ReaderSettings>，由调用方接到各自的持久化通道 */
  onPatch(patch: Partial<ReaderSettings>): void;
  /** 渲染哪些控制项，默认全量 */
  items?: ReaderTypographyItem[];
  /** 字体选择器里是否显示「导入字体…」项，默认 true */
  showFontImport?: boolean;
}

/**
 * ReaderTypographyControls — 设置页阅读器分区与阅读抽屉共用的排版控件组。
 * 取值范围与既有实现完全一致，未做任何调整。
 */
export function ReaderTypographyControls({
  settings,
  onPatch,
  items = ALL_TYPOGRAPHY_ITEMS,
  showFontImport = true
}: ReaderTypographyControlsProps) {
  const visible = new Set(items);

  return (
    <div className="grid gap-4">
      {visible.has("fontSize") && (
        <div data-setting-id="reader.fontSize">
          <Slider
            label="字号"
            min={12}
            max={32}
            step={1}
            value={settings.fontSize}
            onChange={(fontSize) => onPatch({ fontSize })}
            ticks
          />
        </div>
      )}

      {visible.has("lineHeight") && (
        <div data-setting-id="reader.lineHeight">
          <Slider
            label="行距"
            min={1.2}
            max={2.5}
            step={0.1}
            format={(value) => value.toFixed(1)}
            value={settings.lineHeight}
            onChange={(lineHeight) => onPatch({ lineHeight })}
            ticks
          />
        </div>
      )}

      {visible.has("paragraphSpacing") && (
        <div data-setting-id="reader.paragraphSpacing">
          <Slider
            label="段间距"
            min={0.5}
            max={3.0}
            step={0.1}
            format={(value) => value.toFixed(1)}
            value={settings.paragraphSpacing ?? 1.0}
            onChange={(paragraphSpacing) => onPatch({ paragraphSpacing })}
          />
        </div>
      )}

      {visible.has("letterSpacing") && (
        <div data-setting-id="reader.letterSpacing">
          <Slider
            label="字间距"
            min={0}
            max={0.5}
            step={0.01}
            format={(value) => value.toFixed(2)}
            value={settings.letterSpacing ?? 0}
            onChange={(letterSpacing) => onPatch({ letterSpacing })}
          />
        </div>
      )}

      {visible.has("pageMargin") && (
        <NumberStepper
          label="页边距"
          min={24}
          max={120}
          step={4}
          unit="px"
          value={settings.pageMargin}
          onChange={(pageMargin) => onPatch({ pageMargin })}
        />
      )}

      {visible.has("fontFamily") && (
        <div data-setting-id="reader.fontFamily">
          <FontPicker
            label="字体"
            value={settings.fontFamily}
            showImportItem={showFontImport}
            onChange={(fontFamily) => onPatch({ fontFamily })}
          />
        </div>
      )}

      {visible.has("readerBackground") && (
        <BackgroundPicker
          settingId="reader.readerBackground"
          value={settings.readerBackground}
          onChange={(readerBackground) => onPatch({ readerBackground })}
        />
      )}
    </div>
  );
}
