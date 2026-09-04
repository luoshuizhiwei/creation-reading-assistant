import { useCallback, useEffect, useState } from "react";
import { Button, Field, Select, TextInput } from "@/components/ui";
import { chooseFont, getInstalledFonts, savePreset } from "@/services/reader-service";
import type { ReaderPreset, ReaderSettings } from "@/types/library";

export function ReaderSettingsPanel({
  settings,
  onChange,
  onReset,
  format
}: {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
  onReset?: () => void;
  /** 当前阅读格式：EPUB 样式选项只对 EPUB 生效，其余格式的抽屉里隐藏 */
  format?: "txt" | "md" | "epub";
}) {
  const [installedFonts, setInstalledFonts] = useState<string[]>([]);
  const [presetName, setPresetName] = useState("");

  const refreshFonts = useCallback(async () => {
    try {
      setInstalledFonts(await getInstalledFonts());
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { void refreshFonts(); }, [refreshFonts]);

  return (
    <div className="grid gap-4">
      {/* 预设选择 */}
      <div className="flex items-center gap-2 pb-3 border-b border-paper-line">
        <div className="flex-1">
          <Select
            value=""
            onChange={(e) => {
              const preset = settings.presets?.find((p: ReaderPreset) => p.id === e.target.value);
              if (preset) {
                onChange({
                  fontSize: preset.fontSize,
                  lineHeight: preset.lineHeight,
                  pageMargin: preset.pageMargin,
                  paragraphSpacing: preset.paragraphSpacing,
                  letterSpacing: preset.letterSpacing,
                  readerBackground: preset.readerBackground,
                  ...(preset.fontFamily ? { fontFamily: preset.fontFamily } : {}),
                });
              }
              e.target.value = "";
            }}
            options={[
              { value: "", label: "选择预设..." },
              ...(settings.presets?.map((p: ReaderPreset) => ({ value: p.id, label: p.name })) ?? [])
            ]}
          />
        </div>
        <TextInput
          value={presetName}
          onChange={(event) => setPresetName(event.target.value)}
          placeholder="预设名称"
          className="w-28"
          aria-label="阅读预设名称"
        />
        <button
          onClick={() => {
            const name = presetName.trim();
            if (!name) return;
            const preset: ReaderPreset = {
              id: `preset-${Date.now()}`,
              name,
              fontSize: settings.fontSize,
              lineHeight: settings.lineHeight,
              pageMargin: settings.pageMargin,
              paragraphSpacing: settings.paragraphSpacing ?? 1.0,
              letterSpacing: settings.letterSpacing ?? 0,
              readerBackground: settings.readerBackground,
              fontFamily: settings.fontFamily,
            };
            void savePreset(preset);
            onChange({ presets: [...(settings.presets || []), preset] });
            setPresetName("");
          }}
          className="whitespace-nowrap px-2.5 py-1.5 text-sm bg-paper-panel border border-paper-line rounded-md hover:bg-paper-soft"
          title="保存当前设置为预设"
        >
          保存
        </button>
      </div>
      <div className="flex items-center justify-between gap-3">
        <div className="text-sm font-semibold text-paper-ink">阅读设置</div>
        {onReset && (
          <Button variant="secondary" className="h-8 px-2" onClick={onReset}>
            恢复默认
          </Button>
        )}
      </div>
      <Field label="字号">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={12}
            max={32}
            step={1}
            value={settings.fontSize}
            onChange={(e) => onChange({ fontSize: Number(e.target.value) })}
            className="flex-1"
          />
          <span className="text-sm text-paper-muted w-8 text-center">{settings.fontSize}</span>
        </div>
      </Field>
      {/* 字体选择 */}
      <div className="flex items-center gap-2">
        <label className="text-sm text-paper-muted shrink-0">字体</label>
        <div className="flex-1">
          <Select
            value={settings.fontFamily || ""}
            onChange={(e) => onChange({ fontFamily: e.target.value || undefined })}
            options={[
              { value: "", label: "系统默认" },
              ...installedFonts.map((f) => {
                const name = f.replace(/\.[^.]+$/, "");
                return { value: name, label: name };
              })
            ]}
          />
        </div>
        <button
          onClick={async () => {
            const result = await chooseFont();
            if (result) {
              const fontName = result.fileName.replace(/\.[^.]+$/, "");
              const styleId = `font-${fontName}`;
              let styleEl = document.getElementById(styleId) as HTMLStyleElement;
              if (!styleEl) {
                styleEl = document.createElement("style");
                styleEl.id = styleId;
                document.head.appendChild(styleEl);
              }
              styleEl.textContent = `@font-face { font-family: '${fontName}'; src: url('file://${result.filePath}'); }`;
              onChange({ fontFamily: fontName });
              void refreshFonts();
            }
          }}
          className="whitespace-nowrap px-2.5 py-1.5 text-sm bg-paper-panel border border-paper-line rounded-md hover:bg-paper-soft"
          title="导入字体文件"
        >
          导入
        </button>
      </div>
      <Field label="行距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={1.2}
            max={2.5}
            step={0.1}
            value={settings.lineHeight}
            onChange={(e) => onChange({ lineHeight: Number(e.target.value) })}
            className="flex-1"
          />
          <span className="text-sm text-paper-muted w-10 text-center">{settings.lineHeight.toFixed(1)}</span>
        </div>
      </Field>
      <Field label="段间距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={0.5}
            max={3.0}
            step={0.1}
            value={settings.paragraphSpacing ?? 1.0}
            onChange={(e) => onChange({ paragraphSpacing: Number(e.target.value) })}
            className="flex-1"
          />
          <span className="text-sm text-paper-muted w-10 text-center">{(settings.paragraphSpacing ?? 1.0).toFixed(1)}</span>
        </div>
      </Field>
      <Field label="字间距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={0}
            max={0.5}
            step={0.01}
            value={settings.letterSpacing ?? 0}
            onChange={(e) => onChange({ letterSpacing: Number(e.target.value) })}
            className="flex-1"
          />
          <span className="text-sm text-paper-muted w-10 text-center">{(settings.letterSpacing ?? 0).toFixed(2)}</span>
        </div>
      </Field>
      <Field label="页边距">
        <TextInput type="number" min={24} max={120} value={settings.pageMargin} onChange={(event) => onChange({ pageMargin: Number(event.target.value) })} />
      </Field>
      <Select
        label="书籍背景"
        value={settings.readerBackground}
        onChange={(event) => onChange({ readerBackground: event.target.value as ReaderSettings["readerBackground"] })}
        options={[
          { value: "white", label: "白纸" },
          { value: "warm", label: "暖纸" },
          { value: "green", label: "护眼" },
          { value: "night", label: "夜间" },
          { value: "amber", label: "琥珀" },
          { value: "parchment", label: "羊皮纸" },
          { value: "beans", label: "绿豆沙" }
        ]}
      />
      <div style={format && format !== "epub" ? { display: "none" } : undefined}>
        <Select
          label="EPUB 样式"
          value={settings.epubStyleMode}
          onChange={(event) => onChange({ epubStyleMode: event.target.value as ReaderSettings["epubStyleMode"] })}
          options={[
            { value: "publisher", label: "保留原书样式" },
            { value: "unified", label: "统一阅读样式" }
          ]}
        />
      </div>
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
    </div>
  );
}
