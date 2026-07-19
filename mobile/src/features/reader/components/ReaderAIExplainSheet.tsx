import { useEffect, useState } from "react";
import { X, Sparkles, Loader2, AlertCircle, RefreshCw } from "lucide-react";
import type { InspirationSourceSnapshot } from "../../../../../src/types/inspiration";
import { addMobileInspiration, type MobileSnapshot } from "../../../services/mobile-storage";
import { explainSelectedText } from "../../../services/mobile-ai";
import type { MobileBook } from "../../../types/mobile";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import type { ReaderLocator } from "../engine-v2/types";

interface ReaderAIExplainSheetProps {
  book: MobileBook;
  snapshot: MobileSnapshot;
  selectionText: string;
  currentProgress: number;
  currentChapter?: MobileReaderDocument["toc"][number];
  currentLocator?: ReaderLocator | null;
  onSave: (nextSnapshot: MobileSnapshot, savedId: string) => void;
  onClose: () => void;
}

export function ReaderAIExplainSheet({
  book,
  snapshot,
  selectionText,
  currentProgress,
  currentChapter,
  currentLocator,
  onSave,
  onClose
}: ReaderAIExplainSheetProps) {
  const [result, setResult] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const runExplain = async () => {
    if (!selectionText.trim()) return;
    setLoading(true);
    setError("");
    setResult("");
    try {
      const text = await explainSelectedText(selectionText, undefined, book.title);
      setResult(text);
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 解读失败，请检查网络或 AI 配置。");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void runExplain();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSaveToInspiration = async () => {
    const body = result.trim();
    if (!body) return;
    const title = `AI 解读：${selectionText.slice(0, 24)}${selectionText.length > 24 ? "…" : ""}`;
    const source: Partial<InspirationSourceSnapshot> = {
      bookId: book.id,
      bookTitle: book.title,
      bookAuthor: book.author,
      format: book.format,
      chapterTitle: currentChapter?.title,
      locationLabel: currentProgress ? `${currentProgress.toFixed(1)}%` : "当前位置附近",
      progressPercent: currentProgress,
      excerpt: selectionText || undefined,
      createdFrom: "reader-selection",
      locator: currentLocator ?? undefined
    };
    const next = await addMobileInspiration(snapshot, {
      title,
      body,
      tags: [book.title, "AI 解读", "阅读灵感"],
      categoryIds: book.categoryIds ?? [],
      source
    });
    const saved = next.inspirations[0];
    onSave(next, saved.id);
  };

  const hasContent = selectionText.trim().length > 0;

  return (
    <>
      <div className="reader-sheet-mask" onClick={onClose} />
      <aside className="reader-bottom-sheet-panel reader-ai-explain-sheet" role="dialog" aria-modal="true">
        <header className="reader-sheet-handle">
          <span className="reader-sheet-grabber" />
        </header>
        <div className="reader-ai-explain-header">
          <h4 className="reader-sheet-title">AI 解读</h4>
          <button className="ghost-button reader-icon-button" onClick={onClose} aria-label="关闭">
            <X size={20} />
          </button>
        </div>

        {selectionText && (
          <div className="reader-ai-explain-source">
            <span>来源摘录</span>
            <blockquote>{selectionText}</blockquote>
          </div>
        )}

        <div className="reader-ai-explain-body">
          {!hasContent && <p className="empty-hint">请先选中一段正文，再使用 AI 解读。</p>}

          {loading && (
            <div className="reader-ai-explain-loading">
              <Loader2 size={24} className="ai-spin" />
              <span>正在解读选中文本…</span>
            </div>
          )}

          {error && (
            <div className="reader-ai-explain-error">
              <AlertCircle size={18} />
              <span>{error}</span>
              <button className="secondary-button" onClick={() => void runExplain()}>
                <RefreshCw size={14} /> 重试
              </button>
            </div>
          )}

          {!loading && result && (
            <>
              <div className="reader-ai-explain-result">{result}</div>
              <div className="reader-ai-explain-actions">
                <button onClick={() => void handleSaveToInspiration()}>
                  <Sparkles size={16} /> 存入灵感
                </button>
                <button className="secondary-button" onClick={onClose}>
                  关闭
                </button>
              </div>
            </>
          )}
        </div>
      </aside>
    </>
  );
}
