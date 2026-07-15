import {
  Palette,
  Trash2,
  Highlighter,
  NotebookPen,
  Sparkles,
  Download
} from "lucide-react";
import { defaultReaderSettings, type ReaderDrawerTab, type ReaderPanel as ReaderPanelType, type ReaderSheet } from "../reader-model";
import type { ReaderSearchResult } from "../reader-search";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import type { MobileBook, MobileHighlight, MobileReaderSettings, MobileSnapshot } from "../../../types/mobile";
import type { HighlightColor } from "../../../../../src/types/library";
import { formatDuration } from "../../../utils/format";
import { deleteMobileBook, exportBookExcerpts } from "../../../services/mobile-storage";
import { HIGHLIGHT_COLOR_OPTIONS, colorLabel } from "../hooks/useReaderAnnotations";

interface ReaderPanelProps {
  readerPanel: ReaderPanelType;
  setReaderPanel: React.Dispatch<React.SetStateAction<ReaderPanelType | null>>;
  readerDrawerTab: ReaderDrawerTab;
  setReaderDrawerTab: React.Dispatch<React.SetStateAction<ReaderDrawerTab>>;
  setReaderSheet: React.Dispatch<React.SetStateAction<ReaderSheet>>;
  book: MobileBook;
  document: MobileReaderDocument;
  snapshot: MobileSnapshot;
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onBack: () => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
  onMessage: (message: string) => void;
  onOpenInspiration: (inspirationId: string) => void;
  loadError?: string;
  readerSearchQuery: string;
  setReaderSearchQuery: React.Dispatch<React.SetStateAction<string>>;
  readerSearchResults: ReaderSearchResult[];
  bookNotes: MobileSnapshot["notes"];
  bookBookmarks: MobileSnapshot["notes"];
  bookHighlights: MobileHighlight[];
  bookInspirations: MobileSnapshot["inspirations"];
  currentProgress: number;
  activeReadingMs: number;
  chapterLabel: string;
  noteDraft: string;
  setNoteDraft: React.Dispatch<React.SetStateAction<string>>;
  jumpToChapter: (target: MobileReaderDocument["toc"][number] | undefined) => void;
  jumpToReaderProgress: (progressPercent: number) => void;
  jumpToSearchResult: (result: ReaderSearchResult) => void;
  addReaderInspiration: () => Promise<void>;
  addReaderNote: () => Promise<void>;
  addReaderBookmark: () => Promise<void>;
  deleteReaderHighlight: (highlightId: string) => Promise<void>;
  updateReaderHighlightColor: (highlightId: string, color: HighlightColor) => Promise<void>;
  highlightToNote: (highlight: MobileHighlight) => Promise<void>;
  highlightToInspiration: (highlight: MobileHighlight) => void;
}

export function ReaderPanel(props: ReaderPanelProps) {
  const {
    readerPanel,
    setReaderPanel,
    readerDrawerTab,
    setReaderDrawerTab,
    setReaderSheet,
    book,
    document,
    snapshot,
    settings,
    onSettingsChange,
    onSnapshotChange,
    onBack,
    onConfirm,
    onMessage,
    onOpenInspiration,
    loadError,
    readerSearchQuery,
    setReaderSearchQuery,
    readerSearchResults,
    bookNotes,
    bookBookmarks,
    bookHighlights,
    bookInspirations,
    currentProgress,
    activeReadingMs,
    chapterLabel,
    noteDraft,
    setNoteDraft,
    jumpToChapter,
    jumpToReaderProgress,
    jumpToSearchResult,
    addReaderInspiration,
    addReaderNote,
    addReaderBookmark,
    deleteReaderHighlight,
    updateReaderHighlightColor,
    highlightToNote,
    highlightToInspiration
  } = props;

  return (
    <section className={`reader-panel reader-panel-${readerPanel}`} role="dialog" aria-modal="true">
      <header className="reader-panel-header">
        <button className="ghost-button reader-icon-button" onClick={() => setReaderPanel(null)} aria-label="返回阅读">
          ←
        </button>
        <div>
          <p>{book.title}</p>
          <h2>
            {readerPanel === "settings" ? "阅读设置" :
              readerPanel === "book-info" ? "书籍信息" :
              readerDrawerTab === "toc" ? "目录" :
              readerDrawerTab === "search" ? "搜索" :
              readerDrawerTab === "highlights" ? "高亮" :
              readerDrawerTab === "bookmarks" ? "书签" :
              readerDrawerTab === "inspirations" ? "灵感记录" : "笔记"}
          </h2>
        </div>
      </header>

      {readerPanel !== "settings" && readerPanel !== "book-info" && (
        <div className="reader-drawer-tabs reader-panel-tabs">
          {([
            ["toc", "目录"],
            ["search", `搜索 ${readerSearchResults.length}`],
            ["highlights", `高亮 ${bookHighlights.length}`],
            ["bookmarks", `书签 ${bookBookmarks.length}`],
            ["notes", `笔记 ${bookNotes.length}`],
            ["inspirations", `灵感 ${bookInspirations.length}`]
          ] as Array<[ReaderDrawerTab, string]>).map(([key, label]) => (
            <button key={key} className={readerDrawerTab === key ? "active" : ""} onClick={() => setReaderDrawerTab(key)}>
              {label}
            </button>
          ))}
        </div>
      )}

      <div className="reader-panel-body">
        {readerPanel === "book-info" && (() => {
          const totalReadingMs = snapshot.progress.find((item) => item.bookId === book.id)?.totalReadingTimeMs ?? 0;
          const sessions = snapshot.sessions.filter((s) => s.bookId === book.id);
          return (
            <div className="reader-info-panel">
              <div className="reader-info-cover-large">{book.title}</div>
              <h3>{book.title}</h3>
              <p className="reader-info-author">{book.author || "作者未知"} · {book.format.toUpperCase()}</p>

              <section className="reader-info-stats-group">
                <h4 className="reader-info-section-title">阅读统计</h4>
                <div className="reader-info-stat-grid">
                  <div>
                    <span className="reader-info-stat-value">{formatDuration(activeReadingMs)}</span>
                    <span className="reader-info-stat-label">本次已读</span>
                  </div>
                  <div>
                    <span className="reader-info-stat-value">{formatDuration(totalReadingMs)}</span>
                    <span className="reader-info-stat-label">累计阅读</span>
                  </div>
                  <div>
                    <span className="reader-info-stat-value">{sessions.length}</span>
                    <span className="reader-info-stat-label">阅读次数</span>
                  </div>
                  <div>
                    <span className="reader-info-stat-value">{currentProgress.toFixed(1)}%</span>
                    <span className="reader-info-stat-label">阅读进度</span>
                  </div>
                </div>
              </section>

              <section className="reader-info-stats-group">
                <h4 className="reader-info-section-title">正文信息</h4>
                <dl className="reader-info-dl">
                  <div><dt>章节数</dt><dd>{document.toc.length} 章</dd></div>
                  <div><dt>总字数</dt><dd>{document.wordCount.toLocaleString("zh-CN")} 字</dd></div>
                  <div><dt>当前章节</dt><dd>{chapterLabel}</dd></div>
                  <div><dt>来源文件</dt><dd>{book.originalFileName || book.originalFilePath || "本地导入"}</dd></div>
                </dl>
              </section>

              {loadError && <p className="reader-error-line">正文读取失败：{loadError}</p>}

              <div className="reader-info-actions">
                <button onClick={() => { setReaderPanel("settings"); }}>
                  <Palette size={16} /><span>阅读设置</span>
                </button>
                <button
                  className="text-button-danger"
                  onClick={() =>
                    onConfirm({
                      title: "删除本书",
                      message: `确定从书架移除《${book.title}》吗？本地正文文件、阅读进度和笔记将一并移除，此操作不可撤销。`,
                      onConfirm: async () => {
                        try {
                          const next = await deleteMobileBook(snapshot, book.id);
                          onSnapshotChange(next);
                          setReaderPanel(null);
                          onBack();
                        } catch (error) {
                          onMessage(`删除失败：${error instanceof Error ? error.message : String(error)}`);
                        }
                      }
                    })
                  }
                >
                  <Trash2 size={16} /><span>删除本书</span>
                </button>
              </div>
            </div>
          );
        })()}

        {readerPanel === "settings" && (
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
                <div className="reader-settings-options reader-typography-options">
                  <button className={settings.lineHeight < 1.6 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, lineHeight: 1.5 })}>紧凑</button>
                  <button className={settings.lineHeight >= 1.6 && settings.lineHeight < 1.9 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, lineHeight: 1.75 })}>标准</button>
                  <button className={settings.lineHeight >= 1.9 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, lineHeight: 2.1 })}>宽松</button>
                </div>
              </div>
              <div className="reader-typography-row">
                <span className="reader-setting-label">段距</span>
                <div className="reader-settings-options reader-typography-options">
                  <button className={settings.paragraphSpacing < 0.9 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, paragraphSpacing: 0.8 })}>小</button>
                  <button className={settings.paragraphSpacing >= 0.9 && settings.paragraphSpacing < 1.3 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, paragraphSpacing: 1.1 })}>中</button>
                  <button className={settings.paragraphSpacing >= 1.3 ? "active" : ""} onClick={() => onSettingsChange({ ...settings, paragraphSpacing: 1.5 })}>大</button>
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

            <section className="reader-settings-card reader-settings-card-footer">
              <p className="subtle">切换实时预览，退出时自动保存。二级页面会先返回正文；正文中按返回键会返回书架。</p>
              <button className="secondary-button" onClick={() => onSettingsChange(defaultReaderSettings)}>重置所有设置</button>
            </section>
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "toc" && (
          <div className="reader-toc-list qd-toc-list">
            {document.toc.length ? (
              <>
                {(() => {
                  let chapterCounter = 0;
                  return document.toc.map((item) => {
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
                          jumpToChapter(item);
                        }}
                      >
                        <span className="reader-toc-index">{chapterCounter}</span>
                        <span className="reader-toc-title">{item.title}</span>
                      </a>
                    );
                  });
                })()}
              </>
            ) : <p className="empty-hint">这本书暂未识别到目录。</p>}
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "search" && (
          <div className="reader-search-panel">
            <label>
              <span>搜索当前书籍</span>
              <input value={readerSearchQuery} onChange={(event) => setReaderSearchQuery(event.target.value)} placeholder="输入书名、人名、设定或句子片段" autoFocus />
            </label>
            {readerSearchQuery.trim() ? (
              <>
                <p className="search-summary">找到 {readerSearchResults.length} 处，最多显示前 80 条。</p>
                <div className="reader-search-results">
                  {readerSearchResults.length ? readerSearchResults.map((result) => (
                    <button key={result.id} onClick={() => jumpToSearchResult(result)}>
                      <strong>{result.progressPercent.toFixed(1)}%</strong>
                      <span>{result.snippet}</span>
                    </button>
                  )) : <p className="empty-hint">没有搜到。可以换一个更短的关键词。</p>}
                </div>
              </>
            ) : (
              <p className="empty-hint">搜索会在当前 TXT / Markdown / EPUB 正文中查找，点击结果后直接跳到正文位置。</p>
            )}
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "highlights" && (
          <div className="reader-note-list">
            {bookHighlights.length > 0 && (
              <button
                className="secondary-button reader-export-btn"
                onClick={() => {
                  const text = exportBookExcerpts(snapshot, book.id);
                  void navigator.clipboard.writeText(text).then(
                    () => onMessage("书摘已复制到剪贴板。"),
                    () => onMessage("复制失败，请重试。")
                  );
                }}
              >
                <Download size={16} /><span>导出书摘</span>
              </button>
            )}
            {(() => {
              // 按章节分组
              const groups = new Map<string, MobileHighlight[]>();
              for (const item of bookHighlights) {
                const key = item.chapterTitle || "未分类";
                const list = groups.get(key) ?? [];
                list.push(item);
                groups.set(key, list);
              }
              if (!groups.size) {
                return <p className="empty-hint">还没有高亮。阅读时选中文字，点"高亮"即可保存。</p>;
              }
              return Array.from(groups.entries()).map(([chapter, items]) => (
                <div key={chapter} className="reader-highlight-group">
                  <h4 className="reader-highlight-group-title">{chapter}</h4>
                  {items.map((item) => (
                    <article key={item.id} className={`reader-note-item reader-highlight-item reader-highlight-${item.color}`}>
                      <div className="reader-highlight-text">{item.text}</div>
                      <span className="reader-highlight-meta">{colorLabel(item.color)} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                      {item.note && <p className="reader-highlight-note">{item.note}</p>}
                      <div className="reader-highlight-color-picker" role="group" aria-label="切换高亮颜色">
                        {HIGHLIGHT_COLOR_OPTIONS.map((opt) => (
                          <button
                            key={opt.color}
                            className={`reader-color-dot reader-color-${opt.color} ${item.color === opt.color ? "is-active" : ""}`}
                            onClick={() => void updateReaderHighlightColor(item.id, opt.color)}
                            aria-label={`${opt.label}色`}
                          />
                        ))}
                      </div>
                      <div className="reader-highlight-actions">
                        <button onClick={() => jumpToReaderProgress(item.progressPercent ?? 0)}><Highlighter size={14} />跳转</button>
                        <button onClick={() => void highlightToNote(item)}><NotebookPen size={14} />转笔记</button>
                        <button onClick={() => highlightToInspiration(item)}><Sparkles size={14} />转灵感</button>
                        <button className="text-button-danger" onClick={() => void deleteReaderHighlight(item.id)}><Trash2 size={14} />删除</button>
                      </div>
                    </article>
                  ))}
                </div>
              ));
            })()}
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "bookmarks" && (
          <div className="reader-note-list">
            <button onClick={() => void addReaderBookmark()}>在当前位置添加书签</button>
            {bookBookmarks.length ? bookBookmarks.map((item) => (
              <article key={item.id} className="reader-note-item">
                <strong>{item.title}</strong>
                <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                {item.excerpt && <p>{item.excerpt}</p>}
                <button onClick={() => jumpToReaderProgress(item.progressPercent ?? 0)}>跳转</button>
              </article>
            )) : <p className="empty-hint">还没有书签。阅读时点底部"书签"即可保存当前位置。</p>}
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "notes" && (
          <div className="reader-note-list">
            <textarea value={noteDraft} onChange={(event) => setNoteDraft(event.target.value)} placeholder="写一条阅读笔记；如果你选中了正文，也会一起保存为摘录。" />
            <button onClick={() => void addReaderNote()}>保存笔记</button>
            {bookNotes.length ? bookNotes.map((item) => (
              <article key={item.id} className="reader-note-item">
                <strong>{item.title}</strong>
                <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                {item.excerpt && <blockquote>{item.excerpt}</blockquote>}
                <p>{item.body}</p>
                <button onClick={() => jumpToReaderProgress(item.progressPercent ?? 0)}>跳转</button>
              </article>
            )) : <p className="empty-hint">还没有笔记。可以先选中文字，再打开这里保存。</p>}
          </div>
        )}

        {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "inspirations" && (
          <div className="reader-note-list">
            <button onClick={() => void addReaderInspiration()}>把当前位置记为灵感</button>
            {bookInspirations.length ? bookInspirations.map((item) => (
              <article key={item.id} className="reader-note-item">
                <strong>{item.title}</strong>
                <span>{item.source?.locationLabel || `${item.source?.progressPercent?.toFixed(1) ?? 0}%`}</span>
                {item.source?.excerpt && <blockquote>{item.source.excerpt}</blockquote>}
                <p>{item.body || "还没有正文。"}</p>
                <button onClick={() => onOpenInspiration(item.id)}>打开灵感</button>
              </article>
            )) : <p className="empty-hint">这本书还没有灵感记录。选中文字后点"灵感"，来源会单独保存。</p>}
          </div>
        )}
      </div>
    </section>
  );
}
