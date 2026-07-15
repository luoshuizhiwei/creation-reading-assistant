import { useState } from "react";
import { X, FileText, MessageSquare, ListChecks, Loader2 } from "lucide-react";
import { askAboutContent, extractKeyPoints, summarizeChapter } from "../../../services/mobile-ai";

type AIAssistTab = "summary" | "qa" | "keypoints";

interface ReaderAIAssistSheetProps {
  bookTitle: string;
  chapterLabel: string;
  chapterText: string;
  selectedText: string;
  onClose: () => void;
}

export function ReaderAIAssistSheet({
  bookTitle,
  chapterLabel,
  chapterText,
  selectedText,
  onClose
}: ReaderAIAssistSheetProps) {
  const [tab, setTab] = useState<AIAssistTab>("summary");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState("");
  const [keyPoints, setKeyPoints] = useState("");
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");

  const contextText = selectedText || chapterText;
  const hasContext = contextText.trim().length > 0;

  const handleSummary = async () => {
    if (!hasContext) return;
    setLoading(true);
    setError("");
    setSummary("");
    try {
      const result = await summarizeChapter(chapterLabel, chapterText);
      setSummary(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成摘要失败。");
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPoints = async () => {
    if (!hasContext) return;
    setLoading(true);
    setError("");
    setKeyPoints("");
    try {
      const result = await extractKeyPoints(chapterLabel, chapterText);
      setKeyPoints(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "提取要点失败。");
    } finally {
      setLoading(false);
    }
  };

  const handleAsk = async () => {
    if (!question.trim() || !hasContext) return;
    setLoading(true);
    setError("");
    setAnswer("");
    try {
      const result = await askAboutContent(question.trim(), contextText, bookTitle);
      setAnswer(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 问答失败。");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="ai-assist-overlay" role="dialog" aria-modal="true">
      <div className="ai-assist-backdrop" onClick={onClose} />
      <aside className="ai-assist-panel">
        <header className="ai-assist-header">
          <h2>AI 阅读辅助</h2>
          <button onClick={onClose} aria-label="关闭"><X size={20} /></button>
        </header>

        <div className="ai-assist-tabs">
          <button className={tab === "summary" ? "active" : ""} onClick={() => setTab("summary")}>
            <FileText size={16} />
            <span>章节摘要</span>
          </button>
          <button className={tab === "qa" ? "active" : ""} onClick={() => setTab("qa")}>
            <MessageSquare size={16} />
            <span>内容问答</span>
          </button>
          <button className={tab === "keypoints" ? "active" : ""} onClick={() => setTab("keypoints")}>
            <ListChecks size={16} />
            <span>要点提取</span>
          </button>
        </div>

        <div className="ai-assist-context">
          {selectedText ? (
            <small>已选中 {selectedText.length} 字作为上下文</small>
          ) : (
            <small>当前章节：{chapterLabel}</small>
          )}
        </div>

        <div className="ai-assist-body">
          {tab === "summary" && (
            <>
              <button
                className="ai-assist-run-btn"
                onClick={() => void handleSummary()}
                disabled={loading || !hasContext}
              >
                {loading ? <Loader2 size={16} className="ai-spin" /> : <FileText size={16} />}
                生成章节摘要
              </button>
              {summary && <div className="ai-assist-result">{summary}</div>}
            </>
          )}

          {tab === "qa" && (
            <>
              <div className="ai-qa-input-row">
                <input
                  type="text"
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  placeholder="对选中文本或当前章节提问…"
                  onKeyDown={(e) => { if (e.key === "Enter") void handleAsk(); }}
                />
                <button
                  onClick={() => void handleAsk()}
                  disabled={loading || !question.trim() || !hasContext}
                >
                  {loading ? <Loader2 size={16} className="ai-spin" /> : "提问"}
                </button>
              </div>
              {answer && <div className="ai-assist-result">{answer}</div>}
            </>
          )}

          {tab === "keypoints" && (
            <>
              <button
                className="ai-assist-run-btn"
                onClick={() => void handleKeyPoints()}
                disabled={loading || !hasContext}
              >
                {loading ? <Loader2 size={16} className="ai-spin" /> : <ListChecks size={16} />}
                提取关键要点
              </button>
              {keyPoints && <div className="ai-assist-result">{keyPoints}</div>}
            </>
          )}

          {error && <div className="ai-assist-error">{error}</div>}
          {!hasContext && <p className="ai-assist-empty">当前没有可分析的文本内容。</p>}
        </div>
      </aside>
    </div>
  );
}
