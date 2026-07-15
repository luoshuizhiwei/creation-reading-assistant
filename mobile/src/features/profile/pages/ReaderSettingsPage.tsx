import { useMemo } from "react";
import { CheckCircle2 } from "lucide-react";
import { type MobileReaderSettings } from "../../../types/mobile";
import { defaultReaderSettings } from "../../reader/reader-model";
import { READER_BACKGROUND_OPTIONS } from "../../reader/reader-constants";
import { clampFontSize, clampLineHeight, clampParagraphSpacing } from "../profile-helpers";
import type { ProfileSubPage } from "../ProfilePage";

interface ReaderSettingsPageProps {
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
}

export function ReaderSettingsPage({ settings, onSettingsChange, onSetActivePage, onConfirm }: ReaderSettingsPageProps) {
  const safeSettings = useMemo(() => ({
    ...defaultReaderSettings,
    ...settings,
    fontSize: clampFontSize(settings.fontSize),
    lineHeight: clampLineHeight(settings.lineHeight),
    paragraphSpacing: clampParagraphSpacing(settings.paragraphSpacing),
    pageMargin: Math.min(42, Math.max(10, Math.round(settings.pageMargin)))
  }), [settings]);

  const update = (patch: Partial<MobileReaderSettings>) => {
    onSettingsChange({ ...safeSettings, ...patch });
  };

  const reset = () => {
    onConfirm({
      title: "重置阅读设置",
      message: "确认将字号、行距、段距、边距、主题、翻页模式等恢复为默认值？阅读进度、书籍、书签和笔记不会受影响。",
      onConfirm: () => {
        onSettingsChange(defaultReaderSettings);
      }
    });
  };

  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">阅读体验</p>
          <h1>阅读设置</h1>
        </div>
      </header>

      <section className="subpage-card reader-settings-card">
        <p className="subtle">以下设置与阅读器内设置使用同一数据源，修改后立即生效，重启后保持。</p>

        <div className="reader-settings-section">
          <h4>阅读模式</h4>
          <div className="reader-settings-options">
            <button className={safeSettings.readerMode === "paged" ? "active" : ""} onClick={() => update({ readerMode: "paged" })}>
              左右翻页
            </button>
            <button className={safeSettings.readerMode === "scroll" ? "active" : ""} onClick={() => update({ readerMode: "scroll" })}>
              上下滚动
            </button>
          </div>
        </div>

        <div className="reader-settings-section">
          <h4>排版</h4>
          <label className="reader-settings-range-row">
            <span>字号</span>
            <input
              type="range"
              min={12}
              max={32}
              value={safeSettings.fontSize}
              onChange={(event) => update({ fontSize: Number(event.target.value) })}
            />
            <span className="reader-settings-value">{safeSettings.fontSize}</span>
          </label>
          <label className="reader-settings-range-row">
            <span>行距</span>
            <input
              type="range"
              min={1.2}
              max={2.5}
              step={0.05}
              value={safeSettings.lineHeight}
              onChange={(event) => update({ lineHeight: Number(event.target.value) })}
            />
            <span className="reader-settings-value">{safeSettings.lineHeight.toFixed(2)}</span>
          </label>
          <label className="reader-settings-range-row">
            <span>段距</span>
            <input
              type="range"
              min={0.5}
              max={2}
              step={0.05}
              value={safeSettings.paragraphSpacing}
              onChange={(event) => update({ paragraphSpacing: Number(event.target.value) })}
            />
            <span className="reader-settings-value">{safeSettings.paragraphSpacing.toFixed(2)}em</span>
          </label>
          <label className="reader-settings-range-row">
            <span>边距</span>
            <input
              type="range"
              min={10}
              max={42}
              value={safeSettings.pageMargin}
              onChange={(event) => update({ pageMargin: Number(event.target.value) })}
            />
            <span className="reader-settings-value">{safeSettings.pageMargin}px</span>
          </label>
        </div>

        <div className="reader-settings-section">
          <h4>阅读主题</h4>
          <div className="reader-theme-grid">
            {READER_BACKGROUND_OPTIONS.map(([key, label]) => (
              <button
                key={key}
                className={`reader-theme-btn reader-theme-${key} ${safeSettings.readerBackground === key ? "active" : ""}`}
                onClick={() => update({ readerBackground: key })}
              >
                {label}
                {safeSettings.readerBackground === key && <CheckCircle2 size={14} />}
              </button>
            ))}
          </div>
        </div>

        <div className="reader-settings-section">
          <h4>显示与辅助</h4>
          <label className="reader-settings-toggle-row">
            <span>屏幕常亮</span>
            <input
              type="checkbox"
              checked={safeSettings.keepAwake}
              onChange={(event) => update({ keepAwake: event.target.checked })}
            />
          </label>
          <label className="reader-settings-toggle-row">
            <span>显示进度条</span>
            <input
              type="checkbox"
              checked={safeSettings.showProgressBar}
              onChange={(event) => update({ showProgressBar: event.target.checked })}
            />
          </label>
          <label className="reader-settings-toggle-row">
            <span>粗体文字</span>
            <input
              type="checkbox"
              checked={safeSettings.fontWeight === "bold"}
              onChange={(event) => update({ fontWeight: event.target.checked ? "bold" : "regular" })}
            />
          </label>
        </div>
      </section>

      <section className="subpage-card danger-zone-card">
        <h4>危险操作</h4>
        <p className="subtle">重置阅读设置只会恢复字号、行距、主题等默认值，不会删除阅读进度、书籍、书签和笔记。</p>
        <button className="danger-button" onClick={reset}>
          重置阅读设置
        </button>
      </section>
    </div>
  );
}
