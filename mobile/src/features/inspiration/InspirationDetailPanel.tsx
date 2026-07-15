import { useState } from "react";
import { ArrowLeft, BookOpen, ChevronDown, ChevronUp, Copy, MoreHorizontal, Pencil, Sparkles } from "lucide-react";
import type { AIRunAction } from "../../../../src/types/ai";
import {
  addMobileInspirationVariant,
  updateMobileInspiration,
  type MobileSnapshot
} from "../../services/mobile-storage";
import { runMobileAIAction } from "../../services/mobile-ai";
import { aiActions, getInspirationStatusLabel, getInspirationTypeLabel } from "./inspiration-helpers";

function formatDetailTime(value: string | undefined) {
  if (!value) return "时间未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "时间未知";
  return date.toLocaleString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false });
}

export function InspirationDetailPanel({
  item,
  snapshot,
  variantsExpanded,
  onVariantsExpandedChange,
  onSnapshotChange,
  onMessage,
  onBack,
  onEdit,
  onMore,
  onOpenSource
}: {
  item: MobileSnapshot["inspirations"][number];
  snapshot: MobileSnapshot;
  variantsExpanded: boolean;
  onVariantsExpandedChange: (expanded: boolean) => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
  onBack: () => void;
  onEdit: () => void;
  onMore: () => void;
  onOpenSource: () => void;
}) {
  const [aiBusy, setAiBusy] = useState<string>();

  const runInspirationAI = async (action: AIRunAction) => {
    const content = [item.body, item.source?.excerpt].filter(Boolean).join("\n\n").trim();
    if (!content) {
      onMessage("先写一点正文或来源摘录，再使用 AI 打磨。");
      return;
    }
    setAiBusy(action);
    try {
      const result = await runMobileAIAction({
        action,
        title: item.title,
        content,
        platform: item.platformTags[0]
      });
      const next = await addMobileInspirationVariant(snapshot, item.id, result);
      onSnapshotChange(next);
      onVariantsExpandedChange(true);
      onMessage("AI 候选已保存，原文没有被覆盖。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : "AI 操作失败，请检查设置后重试。");
    } finally {
      setAiBusy(undefined);
    }
  };

  const copyVariant = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      onMessage("候选内容已复制。");
    } catch {
      onMessage("复制失败，请长按文字手动复制。");
    }
  };

  const adoptVariant = async (content: string) => {
    const next = await updateMobileInspiration(snapshot, item.id, { body: content });
    onSnapshotChange(next);
    onMessage("已采用为正文，原候选仍然保留。");
  };

  const removeVariant = async (variantId: string) => {
    const next = await updateMobileInspiration(snapshot, item.id, {
      variants: item.variants.filter((variant) => variant.id !== variantId)
    });
    onSnapshotChange(next);
    onMessage("候选版本已删除。");
  };

  return (
    <div className="screen-stack inspiration-detail-v2">
      <header className="inspiration-subpage-topbar">
        <button onClick={onBack} aria-label="返回灵感列表"><ArrowLeft size={23} /></button>
        <h1>灵感详情</h1>
        <div className="inspiration-detail-actions-top">
          <button onClick={onEdit} aria-label="编辑灵感"><Pencil size={21} /></button>
          <button onClick={onMore} aria-label="更多操作"><MoreHorizontal size={22} /></button>
        </div>
      </header>

      <div className="inspiration-detail-scroll">
        <article className="inspiration-detail-article">
          <div className="inspiration-detail-kicker">
            <span>{getInspirationTypeLabel(item.type)}</span>
            <span>{getInspirationStatusLabel(item.status)}</span>
            <time dateTime={item.updatedAt}>更新于 {formatDetailTime(item.updatedAt)}</time>
          </div>
          <h2>{item.title?.trim() || "未命名灵感"}</h2>
          <div className="inspiration-detail-body">{item.body?.trim() || <span className="inspiration-muted">还没有正文。</span>}</div>
          {item.tags?.length > 0 && <div className="inspiration-detail-tags">{item.tags.map((tag) => <span key={tag}>#{tag}</span>)}</div>}
        </article>

        {item.source && (
          <section className="inspiration-source-card-v2">
            <div className="inspiration-section-title"><BookOpen size={18} /><h3>来源</h3></div>
            <button className="inspiration-source-link" onClick={onOpenSource}>
              <span>{item.source.bookTitle ? `《${item.source.bookTitle}》` : "来源书籍已不可用"}</span>
              {item.source.bookAuthor && <small>{item.source.bookAuthor}</small>}
            </button>
            {(item.source.chapterTitle || item.source.locationLabel || item.source.progressPercent != null) && (
              <p className="inspiration-source-location">
                {[item.source.chapterTitle, item.source.locationLabel, item.source.progressPercent != null ? `${item.source.progressPercent.toFixed(1)}%` : undefined].filter(Boolean).join(" · ")}
              </p>
            )}
            {item.source.excerpt && <blockquote>{item.source.excerpt}</blockquote>}
          </section>
        )}

        <section className="inspiration-ai-v2">
          <button className="inspiration-ai-toggle" onClick={() => onVariantsExpandedChange(!variantsExpanded)} aria-expanded={variantsExpanded}>
            <span><Sparkles size={18} /><strong>AI 候选版本</strong><small>{item.variants.length ? `${item.variants.length} 个` : "暂无"}</small></span>
            {variantsExpanded ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
          </button>
          {variantsExpanded && (
            <div className="inspiration-ai-content">
              <p>AI 只生成辅助候选，不会自动覆盖正文。</p>
              <div className="inspiration-ai-actions">
                {aiActions.map(([action, label]) => (
                  <button key={action} disabled={Boolean(aiBusy)} onClick={() => void runInspirationAI(action)}>
                    {aiBusy === action ? "生成中…" : label}
                  </button>
                ))}
              </div>
              {item.variants.length > 0 ? (
                <div className="inspiration-variant-list-v2">
                  {item.variants.map((variant) => (
                    <article key={variant.id}>
                      <header><strong>{aiActions.find(([action]) => action === variant.kind)?.[1] ?? variant.kind}</strong><time>{formatDetailTime(variant.createdAt)}</time></header>
                      <p>{variant.content}</p>
                      <footer>
                        <button onClick={() => void copyVariant(variant.content)}><Copy size={16} />复制</button>
                        <button onClick={() => void adoptVariant(variant.content)}>采用为正文</button>
                        <button className="danger" onClick={() => void removeVariant(variant.id)}>删除</button>
                      </footer>
                    </article>
                  ))}
                </div>
              ) : <p className="inspiration-muted">需要时再主动生成候选。</p>}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
