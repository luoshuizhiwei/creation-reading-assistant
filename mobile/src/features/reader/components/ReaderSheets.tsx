import { defaultReaderSettings, type ReaderDrawerTab, type ReaderSheet } from "../reader-model";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import type { MobileBook, MobileReaderSettings, MobileSnapshot } from "../../../types/mobile";
import { formatDuration } from "../../../utils/format";
import { READER_BACKGROUND_OPTIONS } from "../reader-constants";

interface ReaderSheetsProps {
  readerSheet: ReaderSheet;
  setReaderSheet: React.Dispatch<React.SetStateAction<ReaderSheet>>;
  readerDrawerTab: ReaderDrawerTab;
  setReaderDrawerTab: React.Dispatch<React.SetStateAction<ReaderDrawerTab>>;
  book: MobileBook;
  document: MobileReaderDocument;
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  snapshot: MobileSnapshot;
  bookBookmarks: MobileSnapshot["notes"];
  bookInspirations: MobileSnapshot["inspirations"];
  currentProgress: number;
  chapterLabel: string;
  progressLabel: string;
  savedBookReadingMs: number;
  activeReadingMs: number;
  readerSpeed: number;
  estimatedRemainingMs?: number;
  jumpToChapter: (target: MobileReaderDocument["toc"][number] | undefined) => void;
  jumpToChapterFromDrawer: (target: MobileReaderDocument["toc"][number] | undefined) => void;
  jumpToReaderProgress: (progressPercent: number) => void;
  moveChapter: (direction: -1 | 1) => void;
  addReaderInspiration: () => Promise<void>;
  addReaderBookmark: () => Promise<void>;
  saveProgress: (progressPercent?: number) => Promise<void>;
  recentChapterIds: string[];
  aiConfigured: boolean;
}

export function ReaderSheets(props: ReaderSheetsProps) {
  const {
    readerSheet,
    setReaderSheet,
    readerDrawerTab,
    setReaderDrawerTab,
    book,
    document,
    settings,
    onSettingsChange,
    snapshot,
    bookBookmarks,
    bookInspirations,
    currentProgress,
    chapterLabel,
    progressLabel,
    savedBookReadingMs,
    activeReadingMs,
    readerSpeed,
    estimatedRemainingMs,
    jumpToChapter,
    jumpToChapterFromDrawer,
    jumpToReaderProgress,
    moveChapter,
    addReaderInspiration,
    addReaderBookmark,
    saveProgress,
    recentChapterIds,
    aiConfigured
  } = props;

  return (
    <>
      {/* 目录左侧 drawer（刺猬猫风格），含目录 + 书签 tab */}
      {readerSheet === "toc-drawer" && (
        <>
          <div className="reader-drawer-mask" onClick={() => setReaderSheet(null)} />
          <aside className="reader-toc-drawer-sheet" role="dialog" aria-modal="true">
            <header className="reader-toc-drawer-header">
              <h3>目录</h3>
              <button className="ghost-button reader-icon-button" onClick={() => setReaderSheet(null)} aria-label="关闭目录">
                ←
              </button>
            </header>
            <div className="reader-toc-drawer-tabs">
              <button className={readerDrawerTab === "toc" ? "active" : ""} onClick={() => setReaderDrawerTab("toc")}>目录</button>
              <button className={readerDrawerTab === "bookmarks" ? "active" : ""} onClick={() => setReaderDrawerTab("bookmarks")}>书签 {bookBookmarks.length}</button>
            </div>
            <div className="reader-toc-drawer-body">
              {readerDrawerTab === "toc" && (
                <div className="reader-toc-list qd-toc-list">
                  {document.toc.length ? (
                    <>
                      {(() => {
                        const recentItems = recentChapterIds
                          .map((id) => document.toc.find((item) => item.id === id))
                          .filter((item): item is NonNullable<typeof item> => !!item && item.level > 0)
                          .slice(0, 5);
                        let chapterCounter = 0;
                        const tocNodes = document.toc.map((item) => {
                          if (item.level === 0) {
                            return (
                              <div key={item.id} className="reader-toc-volume">
                                <span>{item.title}</span>
                              </div>
                            );
                          }
                          chapterCounter += 1;
                          return (
                            <a
                              key={item.id}
                              href={`#${item.id}`}
                              className={item.level === 2 ? "reader-toc-sub" : ""}
                              onClick={(event) => {
                                event.preventDefault();
                                jumpToChapterFromDrawer(item);
                                setReaderSheet(null);
                              }}
                            >
                              <span className="reader-toc-index">{chapterCounter}</span>
                              <span className="reader-toc-title">{item.title}</span>
                            </a>
                          );
                        });
                        return (
                          <>
                            {recentItems.length > 0 && (
                              <div className="reader-toc-recent">
                                <span className="reader-toc-recent-label">最近浏览</span>
                                {recentItems.map((item) => (
                                  <a
                                    key={`recent-${item.id}`}
                                    href={`#${item.id}`}
                                    className="reader-toc-recent-item"
                                    onClick={(event) => {
                                      event.preventDefault();
                                      jumpToChapterFromDrawer(item);
                                      setReaderSheet(null);
                                    }}
                                  >
                                    <span>{item.title}</span>
                                  </a>
                                ))}
                              </div>
                            )}
                            {tocNodes}
                          </>
                        );
                      })()}
                    </>
                  ) : <p className="empty-hint">这本书暂未识别到目录。</p>}
                </div>
              )}
              {readerDrawerTab === "bookmarks" && (
                <div className="reader-note-list">
                  <button onClick={() => void addReaderBookmark()}>在当前位置添加书签</button>
                  {bookBookmarks.length ? bookBookmarks.map((item) => (
                    <article key={item.id} className="reader-note-item">
                      <strong>{item.title}</strong>
                      <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                      {item.excerpt && <p>{item.excerpt}</p>}
                      <button onClick={() => { jumpToReaderProgress(item.progressPercent ?? 0); setReaderSheet(null); }}>跳转</button>
                    </article>
                  )) : <p className="empty-hint">还没有书签。阅读时点底部"进度"即可保存当前位置。</p>}
                </div>
              )}
            </div>
          </aside>
        </>
      )}

      {/* 主题底部 sheet（太阳入口）：外观主题 + 日夜间 */}
      {readerSheet === "theme-sheet" && (
        <>
          <div className="reader-sheet-mask" onClick={() => setReaderSheet(null)} />
          <aside className="reader-bottom-sheet-panel reader-theme-sheet" role="dialog" aria-modal="true">
            <header className="reader-sheet-handle">
              <span className="reader-sheet-grabber" />
            </header>
            <h4 className="reader-sheet-title">主题外观</h4>
            <div className="reader-theme-grid">
              {READER_BACKGROUND_OPTIONS.map(([key, label]) => (
                <button
                  key={key}
                  className={`reader-theme-btn reader-theme-${key} ${settings.readerBackground === key ? "active" : ""}`}
                  onClick={() => onSettingsChange({ ...settings, readerBackground: key })}
                >
                  {label}
                </button>
              ))}
            </div>
          </aside>
        </>
      )}

      {/* 设置底部 sheet（起点风格）：不全覆盖，移除外观主题卡片 */}
      {readerSheet === "settings-sheet" && (
        <>
          <div className="reader-sheet-mask" onClick={() => setReaderSheet(null)} />
          <aside className="reader-bottom-sheet-panel reader-settings-sheet" role="dialog" aria-modal="true">
            <header className="reader-sheet-handle">
              <span className="reader-sheet-grabber" />
            </header>
            <h4 className="reader-sheet-title">阅读设置</h4>
            <div className="reader-settings-panel">
              <section className="reader-settings-card">
                <h4 className="reader-settings-card-header">阅读模式</h4>
                <div className="reader-settings-options">
                  <button
                    className={settings.readerMode === "paged" ? "active" : ""}
                    onClick={() => onSettingsChange({ ...settings, readerMode: "paged" })}
                  >
                    左右翻页
                  </button>
                  <button
                    className={settings.readerMode === "scroll" ? "active" : ""}
                    onClick={() => onSettingsChange({ ...settings, readerMode: "scroll" })}
                  >
                    上下滚动
                  </button>
                </div>
              </section>

              <section className="reader-settings-card">
                <h4 className="reader-settings-card-header">排版</h4>
                <div className="reader-typography-row">
                  <span className="reader-setting-label">字号</span>
                  <div className="reader-typography-control">
                    <button className="reader-step-btn" onClick={() => onSettingsChange({ ...settings, fontSize: Math.max(15, settings.fontSize - 1) })} aria-label="缩小字号">A-</button>
                    <input type="range" min="15" max="28" value={settings.fontSize} onChange={(e) => onSettingsChange({ ...settings, fontSize: Number(e.target.value) })} />
                    <button className="reader-step-btn" onClick={() => onSettingsChange({ ...settings, fontSize: Math.min(28, settings.fontSize + 1) })} aria-label="放大字号">A+</button>
                    <span className="reader-setting-value">{settings.fontSize}</span>
                  </div>
                </div>
                <div className="reader-typography-row">
                  <span className="reader-setting-label">行距</span>
                  <div className="reader-typography-control">
                    <input
                      type="range"
                      min="1.4"
                      max="2.2"
                      step="0.05"
                      value={settings.lineHeight}
                      onChange={(e) => onSettingsChange({ ...settings, lineHeight: Number(e.target.value) })}
                    />
                    <span className="reader-setting-value">{settings.lineHeight.toFixed(2)}</span>
                  </div>
                </div>
                <div className="reader-typography-row">
                  <span className="reader-setting-label">段距</span>
                  <div className="reader-typography-control">
                    <input
                      type="range"
                      min="0.5"
                      max="1.5"
                      step="0.05"
                      value={settings.paragraphSpacing}
                      onChange={(e) => onSettingsChange({ ...settings, paragraphSpacing: Number(e.target.value) })}
                    />
                    <span className="reader-setting-value">{settings.paragraphSpacing.toFixed(2)}em</span>
                  </div>
                </div>
                <div className="reader-typography-row">
                  <span className="reader-setting-label">边距</span>
                  <div className="reader-typography-control">
                    <input type="range" min="10" max="42" value={settings.pageMargin} onChange={(e) => onSettingsChange({ ...settings, pageMargin: Number(e.target.value) })} />
                    <span className="reader-setting-value">{settings.pageMargin}</span>
                  </div>
                </div>
              </section>

              <section className="reader-settings-card">
                <h4 className="reader-settings-card-header">显示</h4>
                <div className="reader-typography-row">
                  <span className="reader-setting-label">亮度</span>
                  <div className="reader-typography-control">
                    <input type="range" min="45" max="100" value={settings.brightness} onChange={(e) => onSettingsChange({ ...settings, brightness: Number(e.target.value) })} />
                    <span className="reader-setting-value">{settings.brightness}%</span>
                  </div>
                </div>
                <div className="reader-settings-toggles">
                  <label className="reader-toggle-row">
                    <span>沉浸模式</span>
                    <input type="checkbox" checked={settings.immersiveMode ?? false} onChange={() => onSettingsChange({ ...settings, immersiveMode: !settings.immersiveMode })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>中文排版优化</span>
                    <input type="checkbox" checked={settings.chineseTypography ?? false} onChange={() => onSettingsChange({ ...settings, chineseTypography: !settings.chineseTypography })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>常亮显示</span>
                    <input type="checkbox" checked={settings.keepAwake} onChange={() => onSettingsChange({ ...settings, keepAwake: !settings.keepAwake })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>显示进度条</span>
                    <input type="checkbox" checked={settings.showProgressBar} onChange={() => onSettingsChange({ ...settings, showProgressBar: !settings.showProgressBar })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>粗体文字</span>
                    <input type="checkbox" checked={settings.fontWeight === "bold"} onChange={() => onSettingsChange({ ...settings, fontWeight: settings.fontWeight === "bold" ? "regular" : "bold" })} />
                  </label>
                </div>
              </section>

              <section className="reader-settings-card">
                <h3 className="reader-settings-card-title">阅读辅助</h3>
                <div className="reader-settings-toggles">
                  <label className="reader-toggle-row">
                    <span>显示 AI 解读按钮</span>
                    <input type="checkbox" checked={settings.showAIExplainButton ?? true} onChange={() => onSettingsChange({ ...settings, showAIExplainButton: !settings.showAIExplainButton })} />
                  </label>
                  {!aiConfigured && settings.showAIExplainButton && (
                    <p className="reader-settings-hint">未配置 AI 服务时，选中工具栏不会显示该按钮。</p>
                  )}
                  <label className="reader-toggle-row">
                    <span>阅读节奏提示</span>
                    <input type="checkbox" checked={settings.readingRhythmReminderEnabled ?? true} onChange={() => onSettingsChange({ ...settings, readingRhythmReminderEnabled: !settings.readingRhythmReminderEnabled })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>TTS 高亮当前句段</span>
                    <input type="checkbox" checked={settings.highlightTTSSentence ?? true} onChange={() => onSettingsChange({ ...settings, highlightTTSSentence: !settings.highlightTTSSentence })} />
                  </label>
                  <label className="reader-toggle-row">
                    <span>TTS 与文字进度同步</span>
                    <input type="checkbox" checked={settings.ttsSyncToReader ?? true} onChange={() => onSettingsChange({ ...settings, ttsSyncToReader: !settings.ttsSyncToReader })} />
                  </label>
                </div>
                <div className="reader-settings-row">
                  <span className="reader-setting-label">节奏提示间隔</span>
                  <div className="reader-typography-control reader-assist-select">
                    <select
                      value={settings.readingRhythmReminderMinutes ?? 30}
                      onChange={(e) => onSettingsChange({ ...settings, readingRhythmReminderMinutes: Number(e.target.value) })}
                    >
                      <option value={0}>关闭</option>
                      <option value={15}>15 分钟</option>
                      <option value={30}>30 分钟</option>
                      <option value={45}>45 分钟</option>
                      <option value={60}>60 分钟</option>
                    </select>
                  </div>
                </div>
                <div className="reader-settings-row">
                  <span className="reader-setting-label">护眼提醒</span>
                  <div className="reader-typography-control reader-assist-select">
                    <select
                      value={settings.eyeCareReminderMinutes ?? 30}
                      onChange={(e) => onSettingsChange({ ...settings, eyeCareReminderMinutes: Number(e.target.value) })}
                    >
                      <option value={0}>关闭</option>
                      <option value={15}>15 分钟</option>
                      <option value={30}>30 分钟</option>
                      <option value={45}>45 分钟</option>
                      <option value={60}>60 分钟</option>
                    </select>
                  </div>
                </div>
              </section>

              <section className="reader-settings-card reader-settings-card-footer">
                <button className="secondary-button" onClick={() => onSettingsChange(defaultReaderSettings)}>重置所有设置</button>
              </section>
            </div>
          </aside>
        </>
      )}

      {/* 进度面板：点击进度按钮弹出，含统计行 + 上下章 + 进度滑块 */}
      {readerSheet === "progress-popover" && (
        <div className="reader-progress-panel" role="dialog">
          <div className="reader-stat-row reader-menu-stats">
            <span><strong>{formatDuration(savedBookReadingMs + activeReadingMs)}</strong>阅读</span>
            <span><strong>{readerSpeed ? `${readerSpeed}` : "—"}</strong>字/分</span>
            <span><strong>{estimatedRemainingMs ? formatDuration(estimatedRemainingMs) : "—"}</strong>读完</span>
            <span><strong>{snapshot.inspirations.filter((item) => item.source?.bookId === book.id).length}</strong>灵感</span>
            <span><strong>{bookBookmarks.length}</strong>书签</span>
          </div>
          <div className="reader-progress-panel-main">
            <button onClick={() => moveChapter(-1)} className="reader-progress-chapter-btn" aria-label="上一章">上一章</button>
            <div className="reader-progress-panel-center">
              <span className="reader-progress-chip reader-menu-chip" aria-live="polite">
                <span>{chapterLabel}</span>
                <strong>{progressLabel}</strong>
              </span>
              {settings.showProgressBar && (
                <input
                  className="reader-progress-slider reader-progress-panel-slider"
                  type="range"
                  min="0"
                  max="100"
                  step="0.1"
                  value={currentProgress}
                  onChange={(event) => {
                    const next = Number(event.target.value);
                    jumpToReaderProgress(next);
                    void saveProgress(next);
                  }}
                />
              )}
            </div>
            <button onClick={() => moveChapter(1)} className="reader-progress-chapter-btn" aria-label="下一章">下一章</button>
          </div>
        </div>
      )}
    </>
  );
}
