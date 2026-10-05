import { useMemo, useState } from "react";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui";
import { countSignificantChars, estimatePackTokens } from "@/features/creation/ai/context-pack-format";

export { countSignificantChars };
import type { AiContextPack } from "@/features/creation/ai/build-ai-context";

/**
 * AI 发送前确认（调研 D-C2 + Novalist「首次 AI 数据告知」）：
 * 展示将发送的内容与去向；传入 AiContextPack 时按组展示字符/token 并可逐组排除，
 * 确认后通过 onConfirm 把合成文本交回调用方。勾选「记住选择」后不再拦截（单内容模式）。
 * 纯 UI 层门控，不改变 ai:run 通道本身。
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


interface AiSendConfirmDialogProps {
  actionLabel: string;
  title: string;
  /** 单内容模式（收件箱）：发送的就是这段文本。 */
  content: string;
  /** 分组模式（上下文包）：按组预览 + 逐组排除；与 content 互斥使用。 */
  pack?: AiContextPack;
  /** 目标提示：配置的 provider/model/baseUrl 摘要。 */
  target: string;
  busy: boolean;
  /**
   * pack 模式回传合成后的最终文本；单内容模式回传 null（调用方沿用 content）。
   * 第三个参数回传被排除的组 id：调用方可据此同步「不发送的上下文」
   * （如场景侧 sceneContext 必须跟随排除结果，避免排除了任务卡却仍把它写进提示词）。
   */
  onConfirm(finalContent: string | null, remember: boolean, excluded?: ReadonlySet<string>): void;
  onCancel(): void;
}

const PREVIEW_LIMIT = 280;

export function AiSendConfirmDialog({
  actionLabel,
  title,
  content,
  pack,
  target,
  busy,
  onConfirm,
  onCancel
}: AiSendConfirmDialogProps) {
  const [remember, setRemember] = useState(false);
  const [excluded, setExcluded] = useState<ReadonlySet<string>>(new Set());

  const packSummary = useMemo(() => estimatePackTokens(pack, excluded), [pack, excluded]);

  const handleConfirm = (): void => {
    if (pack) {
      const finalContent = pack.compose(excluded);
      if (finalContent.trim() === "") return; // 全部排除：不发送
      onConfirm(finalContent, remember, new Set(excluded));
      return;
    }
    onConfirm(null, remember);
  };

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-paper-ink/10 px-6 backdrop-blur-[1px]" role="presentation">
      {/* 批次 AM：原本挂 shadow-paper——jsdom 读真实产物 computed 值实测带与不带都是
        * var(--shadow-3)，家族 .motion-dialog 的 !important 一直压着它，零视觉。 */}
      <section className="motion-dialog w-[min(560px,100%)] rounded-[var(--radius-3)] border border-paper-line bg-paper-panel p-5" role="dialog" aria-modal="true" aria-label="AI 发送确认" data-testid="ai-send-confirm">
        <div className="flex items-start gap-3">
          <div className="rounded-full border border-copper/20 bg-copper/10 p-2 text-copper">
            <Sparkles size={18} />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="paper-title text-base font-semibold">发送给 AI：{actionLabel}</h2>
            {pack ? (
              <>
                <p className="mt-2 text-sm leading-6 text-paper-muted">
                  将发送 <strong className="text-paper-ink">{packSummary.chars.toLocaleString("zh-CN")}</strong> 个非空白字符
                  （约 {packSummary.tokens.toLocaleString("zh-CN")} token）到 {target}。
                  可按组排除内容；结果只会进入候选版本，不会覆盖你的正文。
                </p>
                <ul className="mt-3 max-h-48 space-y-1.5 overflow-auto" aria-label="将发送的内容分组">
                  {pack.groups.map((group) => {
                    const isExcluded = excluded.has(group.id);
                    return (
                      <li key={group.id}>
                        <label
                          className={`flex items-start gap-2 rounded-md border p-2 text-xs leading-5 ${
                            isExcluded ? "border-dashed border-paper-line text-paper-muted opacity-70" : "border-paper-line"
                          }`}
                        >
                          <input
                            type="checkbox"
                            className="mt-0.5"
                            checked={!isExcluded}
                            onChange={(event) => {
                              setExcluded((current) => {
                                const next = new Set(current);
                                if (event.target.checked) next.delete(group.id);
                                else next.add(group.id);
                                return next;
                              });
                            }}
                            aria-label={`包含 ${group.label}`}
                          />
                          <span className="min-w-0 flex-1">
                            <span className="font-semibold text-paper-ink">
                              {group.label} · {group.chars.toLocaleString("zh-CN")} 字 ≈ {group.estTokens} token
                            </span>
                            <span className="mt-0.5 block truncate text-paper-muted">
                              {group.content.slice(0, 120).replace(/\n/g, " ⏎ ")}
                            </span>
                          </span>
                        </label>
                      </li>
                    );
                  })}
                </ul>
              </>
            ) : (
              <>
                <p className="mt-2 text-sm leading-6 text-paper-muted">
                  将发送 <strong className="text-paper-ink">{countSignificantChars(content).toLocaleString("zh-CN")}</strong> 个非空白字符
                  {title.trim() ? `（标题：${title.trim()}）` : ""} 到 {target}。AI 返回的内容只会追加为候选版本，不会覆盖你的正文。
                </p>
                <pre className="mt-3 max-h-32 overflow-auto whitespace-pre-wrap rounded-md border border-paper-line bg-paper-soft/60 p-3 text-xs leading-5 text-paper-ink">
{content.trim().slice(0, PREVIEW_LIMIT) || "（正文为空，将只发送标题）"}
                </pre>
              </>
            )}
            <label className="mt-3 flex items-center gap-2 text-xs text-paper-muted">
              <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
              记住我的选择，之后不再询问
            </label>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="ghost" onClick={onCancel} disabled={busy}>
                取消
              </Button>
              <Button
                data-testid="ai-send-confirm-go"
                onClick={handleConfirm}
                disabled={busy || (pack !== undefined && excluded.size === pack.groups.length)}
              >
                {busy ? "生成中…" : "发送给 AI"}
              </Button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
