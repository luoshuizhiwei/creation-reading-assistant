import { Library, Lightbulb, Trash2 } from "lucide-react";
import { InboxItemDetail } from "./InboxItemDetail";
import type { InboxItem } from "@/types/creation";
import type { AIRunAction, AISettings } from "@/types/ai";
import type { InboxDraft, SaveStatus } from "./types";

export interface InboxItemListProps {
  items: InboxItem[];
  filteredItems: InboxItem[];
  selectedId?: string;
  onSelectId: (id: string) => void;
  confirmingId: string | null;
  onConfirmDelete: (id: string | null) => void;
  onDelete: (id: string) => void;
  convertingIds: Set<string>;
  onConvertToCard: (item: InboxItem) => void;
  hasMore: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;

  // Selected item detail
  selectedItem?: InboxItem;
  draft: InboxDraft;
  saveStatus: SaveStatus;
  onDraftChange: (patch: Partial<InboxDraft>) => void;
  onSaveDraft: () => void;

  // AI polish
  aiAvailable: boolean;
  aiSettings: AISettings | null;
  aiBusy?: AIRunAction;
  isAIRunning: boolean;
  onRequestAI: (action: Exclude<AIRunAction, "consistency">) => void;
  onAdoptVariant: (variant: Record<string, unknown>) => void;
  onCopyVariant: (content: string) => void;
  onRemoveVariant: (variantId: string) => void;
}

/**
 * 灵感卡片流、单条展开、AI 操作入口与无限分页加载更多
 */
export function InboxItemList({
  items,
  filteredItems,
  selectedId,
  onSelectId,
  confirmingId,
  onConfirmDelete,
  onDelete,
  convertingIds,
  onConvertToCard,
  hasMore,
  loadingMore,
  onLoadMore,
  selectedItem,
  draft,
  saveStatus,
  onDraftChange,
  onSaveDraft,
  aiAvailable,
  aiSettings,
  aiBusy,
  isAIRunning,
  onRequestAI,
  onAdoptVariant,
  onCopyVariant,
  onRemoveVariant
}: InboxItemListProps) {
  return (
    <div className="inbox-editor-grid">
      <ul className="inbox-list">
        {filteredItems.length === 0 ? (
          <li className="stats-card">
            <p className="stats-note">
              {items.length === 0
                ? "收件箱为空。点击「新建想法」开始收集，旧灵感迁移后也会出现在这里。"
                : "当前筛选下没有条目，切换筛选或新建想法。"}
            </p>
          </li>
        ) : (
          filteredItems.map((item) => (
            <li
              key={item.id}
              className={`stats-card inbox-item ${
                item.status === "used" ? "inbox-item--used" : ""
              } ${item.id === selectedId ? "inbox-item--selected" : ""}`}
              onClick={() => onSelectId(item.id)}
            >
              <span className="inbox-item-main">
                <span className="inbox-item-title">
                  {item.title}
                  {item.legacyId && <em>旧灵感</em>}
                  {item.status === "used" && <em className="used">已转卡片</em>}
                </span>
                <span className="inbox-item-body">
                  {item.body.length > 120 ? `${item.body.slice(0, 120)}…` : item.body}
                </span>
                <span className="inbox-item-meta">
                  {item.tags.length > 0 && <>标签：{item.tags.join("，")}</>}
                  {item.tags.length > 0 && " · "}
                  AI 候选 {item.variants.length} 个
                  {item.source && " · 有来源"}
                  {item.updatedAt &&
                    ` · ${new Date(item.updatedAt).toLocaleDateString("zh-CN")}`}
                </span>
              </span>
              <span className="inbox-item-actions">
                {item.status !== "used" && (
                  <button
                    type="button"
                    disabled={convertingIds.has(item.id)}
                    onClick={(event) => {
                      event.stopPropagation();
                      onConvertToCard(item);
                    }}
                  >
                    <Library size={13} />{" "}
                    {convertingIds.has(item.id) ? "正在转卡…" : "转为资料卡"}
                  </button>
                )}
                <button
                  type="button"
                  className={confirmingId === item.id ? "confirming" : ""}
                  onClick={(event) => {
                    event.stopPropagation();
                    if (confirmingId === item.id) onDelete(item.id);
                    else onConfirmDelete(item.id);
                  }}
                >
                  <Trash2 size={13} />
                  {confirmingId === item.id ? "确认移出" : "移出"}
                </button>
              </span>
            </li>
          ))
        )}
        {hasMore && (
          <li className="stats-card inbox-load-more">
            <button
              type="button"
              disabled={loadingMore}
              onClick={onLoadMore}
            >
              {loadingMore ? "正在加载更多…" : "加载更多"}
            </button>
            <span className="stats-note">已加载 {items.length} 条</span>
          </li>
        )}
        {!hasMore && items.length > 0 && (
          <li className="stats-card inbox-load-more">
            <span className="stats-note">已加载全部 {items.length} 条</span>
          </li>
        )}
      </ul>

      {!selectedItem ? (
        <div className="stats-card inbox-detail-empty">
          <p className="stats-note">
            <Lightbulb size={14} /> 从左侧选择一个条目查看与编辑，或新建一条想法。
          </p>
        </div>
      ) : (
        <InboxItemDetail
          item={selectedItem}
          draft={draft}
          saveStatus={saveStatus}
          onDraftChange={onDraftChange}
          onSaveDraft={onSaveDraft}
          aiAvailable={aiAvailable}
          aiSettings={aiSettings}
          aiBusy={aiBusy}
          isAIRunning={isAIRunning}
          onRequestAI={onRequestAI}
          onAdoptVariant={onAdoptVariant}
          onCopyVariant={onCopyVariant}
          onRemoveVariant={onRemoveVariant}
        />
      )}
    </div>
  );
}
