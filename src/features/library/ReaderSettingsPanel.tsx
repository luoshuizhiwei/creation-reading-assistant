import { useCallback, useEffect, useState } from "react";
import { Button, Field, TextInput } from "@/components/ui";
import { getDesktopApi } from "@/services/ipc-client";
import type { ReaderPreset, ReaderSettings } from "@/types/library";

export function ReaderSettingsPanel({
  settings,
  onChange,
  onReset
}: {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
  onReset?: () => void;
}) {
  const tracking = settings.tracking;
  const [installedFonts, setInstalledFonts] = useState<string[]>([]);
  const [presetName, setPresetName] = useState("");

  const refreshFonts = useCallback(async () => {
    try {
      const fonts = await getDesktopApi().reader.getInstalledFonts();
      setInstalledFonts(fonts);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { void refreshFonts(); }, [refreshFonts]);

  return (
    <div className="grid gap-4">
      {/* 预设选择 */}
      <div className="flex items-center gap-2 pb-3 border-b border-paper-line">
        <select
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
          className="paper-input h-9 flex-1"
        >
          <option value="">选择预设...</option>
          {settings.presets?.map((p: ReaderPreset) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
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
            void getDesktopApi().reader.savePreset(preset);
            onChange({ presets: [...(settings.presets || []), preset] });
            setPresetName("");
          }}
          className="px-2 py-1.5 text-sm bg-paper-panel border border-paper-line rounded-md hover:bg-paper-soft"
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
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-8 text-center">{settings.fontSize}</span>
        </div>
      </Field>
      {/* 字体选择 */}
      <div className="flex items-center gap-2">
        <label className="text-sm text-paper-muted shrink-0">字体</label>
        <select
          value={settings.fontFamily || ""}
          onChange={(e) => onChange({ fontFamily: e.target.value || undefined })}
          className="paper-input h-9 flex-1"
        >
          <option value="">系统默认</option>
          {installedFonts.map((f) => {
            const name = f.replace(/\.[^.]+$/, "");
            return <option key={f} value={name}>{name}</option>;
          })}
        </select>
        <button
          onClick={async () => {
            const result = await getDesktopApi().reader.chooseFont();
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
          className="px-2 py-1.5 text-sm bg-paper-panel border border-paper-line rounded-md hover:bg-paper-soft"
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
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{settings.lineHeight.toFixed(1)}</span>
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
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{(settings.paragraphSpacing ?? 1.0).toFixed(1)}</span>
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
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{(settings.letterSpacing ?? 0).toFixed(2)}</span>
        </div>
      </Field>
      <Field label="页边距">
        <TextInput type="number" min={24} max={120} value={settings.pageMargin} onChange={(event) => onChange({ pageMargin: Number(event.target.value) })} />
      </Field>
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">书籍背景</span>
        <select
          className="paper-input h-9"
          value={settings.readerBackground}
          onChange={(event) => onChange({ readerBackground: event.target.value as ReaderSettings["readerBackground"] })}
        >
          <option value="white">白纸</option>
          <option value="warm">暖纸</option>
          <option value="green">护眼</option>
          <option value="night">夜间</option>
          <option value="amber">琥珀</option>
          <option value="parchment">羊皮纸</option>
          <option value="beans">绿豆沙</option>
        </select>
      </label>
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">EPUB 样式</span>
        <select
          className="paper-input h-9"
          value={settings.epubStyleMode}
          onChange={(event) => onChange({ epubStyleMode: event.target.value as ReaderSettings["epubStyleMode"] })}
        >
          <option value="publisher">保留原书样式</option>
          <option value="unified">统一阅读样式</option>
        </select>
      </label>
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">繁简转换</span>
        <select
          className="paper-input h-9"
          value={settings.textConversion || "none"}
          onChange={(event) => onChange({ textConversion: event.target.value as ReaderSettings["textConversion"] })}
        >
          <option value="none">不转换</option>
          <option value="s2t">简体 → 繁体</option>
          <option value="t2s">繁体 → 简体</option>
        </select>
      </label>

      <div className="mt-2 border-t border-paper-line pt-4">
        <div className="mb-1 text-sm font-semibold text-paper-ink">高级阅读记录</div>
        <p className="mb-3 text-xs leading-5 text-paper-muted">这些选项只影响计时和进度保存，不会改变正文内容。</p>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input type="checkbox" checked={settings.restoreLastPosition} onChange={(event) => onChange({ restoreLastPosition: event.target.checked })} />
          自动恢复上次位置
        </label>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input
            type="checkbox"
            checked={tracking.trackReadingSessions}
            onChange={(event) => onChange({ tracking: { ...tracking, trackReadingSessions: event.target.checked } })}
          />
          自动记录阅读会话
        </label>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input
            type="checkbox"
            checked={tracking.showReadingStatsCards}
            onChange={(event) => onChange({ tracking: { ...tracking, showReadingStatsCards: event.target.checked } })}
          />
          显示当前会话卡片
        </label>
        <Field label="空闲暂停秒数">
          <p className="text-xs leading-5 text-paper-muted">多久没有翻页、滚动或点击后，暂停计入有效阅读时间。</p>
          <TextInput
            type="number"
            min={15}
            max={600}
            value={Math.round(tracking.idleTimeoutMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, idleTimeoutMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
        <Field label="进度保存间隔秒数">
          <p className="text-xs leading-5 text-paper-muted">阅读时多久自动保存一次当前位置，数值越小越不容易丢进度。</p>
          <TextInput
            type="number"
            min={1}
            max={60}
            value={Math.round(tracking.progressSaveIntervalMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, progressSaveIntervalMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
        <Field label="会话持久化秒数">
          <p className="text-xs leading-5 text-paper-muted">阅读计时多久写入一次本地文件，用来防止异常退出后时长丢失。</p>
          <TextInput
            type="number"
            min={5}
            max={300}
            value={Math.round(tracking.sessionPersistIntervalMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, sessionPersistIntervalMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
      </div>
    </div>
  );
}
