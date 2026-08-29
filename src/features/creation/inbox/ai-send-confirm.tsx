import { useState } from "react";
import { Sparkles } from "lucide-react";

/**
 * AI 发送前确认（调研 D-C2 lite / Novalist「首次 AI 数据告知」）：
 * 每次调用前展示将发送的内容与去向；勾选「记住选择」后不再拦截。
 * 仅 UI 层拦截，不改变 ai:run 通道与上下文（当前实现只发送标题+正文，无隐藏上下文包）。
 */

const OPT_OUT_KEY = "creation.ai.sendConfirmOptOut.v1";

export function shouldConfirmAiSend(): boolean {
  try {
    return window.localStorage.getItem(OPT_OUT_KEY) !== "1";
  } catch {
    return true;
  }
}

export function rememberAiSendOptOut(): void {
  try {
    window.localStorage.setItem(OPT_OUT_KEY, "1");
  } catch {
    // 隐私模式等场景下存不进就每次询问，不致命。
  }
}

/** 非空白字符数（与写作台口径一致）。 */
export function countSignificantChars(text: string): number {
  return text.replace(/\s/g, "").length;
}

interface AiSendConfirmDialogProps {
  actionLabel: string;
  title: string;
  content: string;
  /** 目标提示：配置的 provider/model/baseUrl 摘要。 */
  target: string;
  busy: boolean;
  onConfirm(remember: boolean): void;
  onCancel(): void;
}

const PREVIEW_LIMIT = 280;

export function AiSendConfirmDialog({
  actionLabel,
  title,
  content,
  target,
  busy,
  onConfirm,
  onCancel
}: AiSendConfirmDialogProps) {
  const [remember, setRemember] = useState(false);
  const trimmed = content.trim();
  const preview = trimmed.length > PREVIEW_LIMIT ? `${trimmed.slice(0, PREVIEW_LIMIT)}…` : trimmed;

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-paper-ink/10 px-6 backdrop-blur-[1px]" role="presentation">
      <section className="motion-dialog w-[min(520px,100%)] rounded-2xl border border-paper-line bg-paper-panel p-5 shadow-paper" role="dialog" aria-modal="true" aria-label="AI 发送确认" data-testid="ai-send-confirm">
        <div className="flex items-start gap-3">
          <div className="rounded-full border border-copper/20 bg-copper/10 p-2 text-copper">
            <Sparkles size={18} />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="paper-title text-base font-semibold">发送给 AI：{actionLabel}</h2>
            <p className="mt-2 text-sm leading-6 text-paper-muted">
              将发送 <strong className="text-paper-ink">{countSignificantChars(content).toLocaleString("zh-CN")}</strong> 个非空白字符
              {title.trim() ? `（标题：${title.trim()}）` : ""} 到 {target}。AI 返回的内容只会追加为候选版本，不会覆盖你的正文。
            </p>
            <pre className="mt-3 max-h-32 overflow-auto whitespace-pre-wrap rounded-lg border border-paper-line bg-paper-soft/60 p-3 text-xs leading-5 text-paper-ink">
{preview || "（正文为空，将只发送标题）"}
            </pre>
            <label className="mt-3 flex items-center gap-2 text-xs text-paper-muted">
              <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
              记住我的选择，之后不再询问
            </label>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                className="rounded-md px-3 py-2 text-sm text-paper-muted hover:bg-paper-soft/70 hover:text-paper-ink"
                onClick={onCancel}
                disabled={busy}
              >
                取消
              </button>
              <button
                type="button"
                className="rounded-md bg-copper px-3 py-2 text-sm font-medium text-white shadow-lift hover:bg-copper-dark disabled:cursor-not-allowed disabled:opacity-45"
                data-testid="ai-send-confirm-go"
                onClick={() => onConfirm(remember)}
                disabled={busy}
              >
                {busy ? "生成中…" : "发送给 AI"}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
