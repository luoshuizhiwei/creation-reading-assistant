import { useEffect, useState } from "react";
import { RotateCcw } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { Button, Field, Select, TextInput } from "@/components/ui";
import { chooseFont, getInstalledFonts, savePreset } from "@/services/reader-service";
import { resetReaderSettings } from "@/services/settings-service";
import type { ReaderPreset, ReaderTrackingSettings } from "@/types/library";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "@/types/settings";
import { Section, patchNumericSetting } from "./SectionWrapper";

interface ReaderSectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  applySettings: (settings: AppSettings) => void;
  loadSettings: () => Promise<AppSettings | undefined>;
  resetSection: (section: SettingsSection) => void;
  showToast: (toast: { tone: "success" | "info" | "warning" | "error"; title: string; body?: string }) => void;
  setError: (error: string) => void;
}

export function ReaderSection({
  settings,
  patchSettings,
  applySettings,
  loadSettings,
  resetSection,
  showToast,
  setError
}: ReaderSectionProps) {
  const [readerMessage, setReaderMessage] = useState("");
  const [readerPresetName, setReaderPresetName] = useState("");
  const [selectedPresetId, setSelectedPresetId] = useState("");
  const [installedFonts, setInstalledFonts] = useState<string[]>([]);

  useEffect(() => {
    void getInstalledFonts().then(setInstalledFonts).catch(() => {});
  }, []);

  const resetReaderDefaults = async () => {
    try {
      const next = await resetReaderSettings();
      applySettings(next);
      setReaderMessage("阅读设置已恢复默认。");
      showToast({ tone: "success", title: "阅读设置已恢复默认", body: "字号、行距、页边距和书籍背景已回到初始值。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const patchReaderTracking = (trackingPatch: Partial<ReaderTrackingSettings>) => {
    const patch: AppSettingsPatch = {
      reader: {
        tracking: {
          ...settings.reader.tracking,
          ...trackingPatch
        }
      }
    };
    void patchSettings(patch);
  };

  return (
    <Section title="阅读器" section="reader" onReset={resetSection}>
      <div className="col-span-2 flex items-center justify-between rounded-xl border border-paper-line bg-paper-soft/35 p-3">
        <div>
          <div className="text-sm font-semibold text-paper-ink">阅读排版</div>
          <p className="mt-1 text-xs leading-5 text-paper-muted">字号、行距、页边距和书籍背景只影响阅读页，不影响应用整体主题。</p>
        </div>
        <Button variant="secondary" onClick={() => void resetReaderDefaults()}>
          <RotateCcw size={15} />
          恢复阅读默认
        </Button>
      </div>
      {readerMessage && (
        <InlineNotice tone="success" className="col-span-2 p-2 text-xs">
          {readerMessage}
        </InlineNotice>
      )}

      {/* 预设选择（受控组件） */}
      <div className="col-span-2 flex items-center gap-2">
        <div className="flex-1">
          <Select
            value={selectedPresetId}
            aria-label="选择阅读预设"
            onChange={(e) => {
              const presetId = e.target.value;
              setSelectedPresetId(presetId);
              const preset = settings.reader.presets?.find((p: ReaderPreset) => p.id === presetId);
              if (preset) {
                void patchSettings({
                  reader: {
                    fontSize: preset.fontSize,
                    lineHeight: preset.lineHeight,
                    pageMargin: preset.pageMargin,
                    paragraphSpacing: preset.paragraphSpacing,
                    letterSpacing: preset.letterSpacing,
                    readerBackground: preset.readerBackground,
                    ...(preset.fontFamily ? { fontFamily: preset.fontFamily } : {})
                  }
                }).then(() => {
                  setSelectedPresetId("");
                });
              } else {
                setSelectedPresetId("");
              }
            }}
            options={[
              { value: "", label: "选择预设..." },
              ...(settings.reader.presets?.map((p: ReaderPreset) => ({ value: p.id, label: p.name })) ?? [])
            ]}
          />
        </div>
        <TextInput
          value={readerPresetName}
          onChange={(event) => setReaderPresetName(event.target.value)}
          placeholder="预设名称"
          className="w-32"
        />
        <Button
          variant="secondary"
          disabled={!readerPresetName.trim()}
          onClick={() => {
            const name = readerPresetName.trim();
            if (!name) return;
            const preset: ReaderPreset = {
              id: `preset-${Date.now()}`,
              name,
              fontSize: settings.reader.fontSize,
              lineHeight: settings.reader.lineHeight,
              pageMargin: settings.reader.pageMargin,
              paragraphSpacing: settings.reader.paragraphSpacing ?? 1.0,
              letterSpacing: settings.reader.letterSpacing ?? 0,
              readerBackground: settings.reader.readerBackground,
              fontFamily: settings.reader.fontFamily
            };
            void savePreset(preset).then(() => {
              setReaderPresetName("");
              setReaderMessage(`已保存预设「${name}」`);
              setTimeout(() => setReaderMessage(""), 2000);
              return loadSettings();
            });
          }}
        >
          保存
        </Button>
      </div>

      <Field label="字号">
        <div className="flex items-center gap-2">
          <input
            type="range"
            min={12}
            max={32}
            step={1}
            value={settings.reader.fontSize}
            onChange={(e) => void patchSettings({ reader: { fontSize: Number(e.target.value) } })}
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-8 text-center">{settings.reader.fontSize}</span>
        </div>
      </Field>

      {/* 字体选择 */}
      <div className="col-span-2 flex items-center gap-2">
        <label className="text-sm text-paper-muted shrink-0">字体</label>
        <div className="flex-1">
          <Select
            value={settings.reader.fontFamily || ""}
            aria-label="选择正文字体"
            onChange={(e) => void patchSettings({ reader: { fontFamily: e.target.value || undefined } })}
            options={[
              { value: "", label: "系统默认" },
              ...installedFonts.map((f) => {
                const name = f.replace(/\.[^.]+$/, "");
                return { value: name, label: name };
              })
            ]}
          />
        </div>
        <Button
          variant="secondary"
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
              void patchSettings({ reader: { fontFamily: fontName } });
              void getInstalledFonts().then(setInstalledFonts).catch(() => {});
            }
          }}
        >
          导入
        </Button>
      </div>

      <Field label="行距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={1.2}
            max={2.5}
            step={0.1}
            value={settings.reader.lineHeight}
            onChange={(e) => void patchSettings({ reader: { lineHeight: Number(e.target.value) } })}
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{settings.reader.lineHeight.toFixed(1)}</span>
        </div>
      </Field>

      <Field label="段间距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={0.5}
            max={3.0}
            step={0.1}
            value={settings.reader.paragraphSpacing ?? 1.0}
            onChange={(e) => void patchSettings({ reader: { paragraphSpacing: Number(e.target.value) } })}
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{(settings.reader.paragraphSpacing ?? 1.0).toFixed(1)}</span>
        </div>
      </Field>

      <Field label="字间距">
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={0}
            max={0.5}
            step={0.01}
            value={settings.reader.letterSpacing ?? 0}
            onChange={(e) => void patchSettings({ reader: { letterSpacing: Number(e.target.value) } })}
            className="flex-1 accent-amber-700"
          />
          <span className="text-sm text-stone-600 w-10 text-center">{(settings.reader.letterSpacing ?? 0).toFixed(2)}</span>
        </div>
      </Field>

      <Field label="页边距">
        <TextInput
          type="number"
          min={24}
          max={120}
          value={settings.reader.pageMargin}
          onChange={(event) =>
            patchNumericSetting(event.target.value, 24, 120, (pageMargin) => ({ reader: { pageMargin } }), patchSettings)
          }
        />
      </Field>

      <Select
        label="书籍背景"
        value={settings.reader.readerBackground}
        onChange={(event) =>
          void patchSettings({ reader: { readerBackground: event.target.value as typeof settings.reader.readerBackground } })
        }
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

      <Select
        label="EPUB 样式"
        value={settings.reader.epubStyleMode}
        onChange={(event) =>
          void patchSettings({ reader: { epubStyleMode: event.target.value as typeof settings.reader.epubStyleMode } })
        }
        options={[
          { value: "publisher", label: "保留原书样式" },
          { value: "unified", label: "统一阅读样式" }
        ]}
      />

      <Select
        label="繁简转换"
        value={settings.reader.textConversion || "none"}
        onChange={(event) =>
          void patchSettings({ reader: { textConversion: event.target.value as typeof settings.reader.textConversion } })
        }
        options={[
          { value: "none", label: "不转换" },
          { value: "s2t", label: "简体 → 繁体" },
          { value: "t2s", label: "繁体 → 简体" }
        ]}
      />

      <div className="col-span-2 rounded-xl border border-paper-line bg-paper-soft/35 p-3">
        <div className="text-sm font-semibold text-paper-ink">高级阅读记录</div>
        <p className="mt-1 text-xs leading-5 text-paper-muted">
          下面这些秒数只控制阅读计时和位置保存：多久没有翻页会暂停计时、多久保存一次阅读位置、多久把会话时长写入本地文件。
        </p>
      </div>

      <label className="flex items-center gap-2 text-sm text-paper-muted">
        <input
          type="checkbox"
          checked={settings.reader.restoreLastPosition}
          onChange={(event) => void patchSettings({ reader: { restoreLastPosition: event.target.checked } })}
        />
        自动恢复上次位置
      </label>

      <label className="flex items-center gap-2 text-sm text-paper-muted">
        <input
          type="checkbox"
          checked={settings.reader.tracking.trackReadingSessions}
          onChange={(event) => patchReaderTracking({ trackReadingSessions: event.target.checked })}
        />
        自动记录阅读会话
      </label>

      <Field label="空闲超时秒数">
        <p className="text-xs leading-5 text-paper-muted">多久没有翻页、滚动或点击后，暂停计入有效阅读时间。</p>
        <TextInput
          type="number"
          min={15}
          max={600}
          value={Math.round(settings.reader.tracking.idleTimeoutMs / 1000)}
          onChange={(event) =>
            patchNumericSetting(
              event.target.value,
              15,
              600,
              (value) => ({ reader: { tracking: { ...settings.reader.tracking, idleTimeoutMs: value * 1000 } } }),
              patchSettings
            )
          }
        />
      </Field>

      <Field label="进度保存间隔秒数">
        <p className="text-xs leading-5 text-paper-muted">阅读时多久自动保存一次当前位置。</p>
        <TextInput
          type="number"
          min={1}
          max={60}
          value={Math.round(settings.reader.tracking.progressSaveIntervalMs / 1000)}
          onChange={(event) =>
            patchNumericSetting(
              event.target.value,
              1,
              60,
              (value) => ({ reader: { tracking: { ...settings.reader.tracking, progressSaveIntervalMs: value * 1000 } } }),
              patchSettings
            )
          }
        />
      </Field>

      <Field label="会话心跳秒数">
        <p className="text-xs leading-5 text-paper-muted">阅读页多久同步一次“还在读”的状态。</p>
        <TextInput
          type="number"
          min={2}
          max={60}
          value={Math.round(settings.reader.tracking.sessionHeartbeatMs / 1000)}
          onChange={(event) =>
            patchNumericSetting(
              event.target.value,
              2,
              60,
              (value) => ({ reader: { tracking: { ...settings.reader.tracking, sessionHeartbeatMs: value * 1000 } } }),
              patchSettings
            )
          }
        />
      </Field>

      <Field label="会话持久化间隔秒数">
        <p className="text-xs leading-5 text-paper-muted">阅读时长多久写入一次本地文件，异常退出时用于恢复统计。</p>
        <TextInput
          type="number"
          min={5}
          max={300}
          value={Math.round(settings.reader.tracking.sessionPersistIntervalMs / 1000)}
          onChange={(event) =>
            patchNumericSetting(
              event.target.value,
              5,
              300,
              (value) => ({ reader: { tracking: { ...settings.reader.tracking, sessionPersistIntervalMs: value * 1000 } } }),
              patchSettings
            )
          }
        />
      </Field>
    </Section>
  );
}
