import { Check, Copy, Save, Sparkles, X } from "lucide-react";
import { Button, Select } from "@/components/ui";
import { AI_LABELS, STATUS_LABELS, TYPE_LABELS, type InboxDraft, type SaveStatus } from "./types";
import type { InboxItem } from "@/types/creation";
import type { AISettings } from "@/types/ai";
import type { InspirationStatus, InspirationType, InspirationVariantKind } from "@/types/inspiration";

export interface InboxItemDetailProps {
  item: InboxItem;
  draft: InboxDraft;
  saveStatus: SaveStatus;
  onDraftChange: (patch: Partial<InboxDraft>) => void;
  onSaveDraft: () => void;
  aiAvailable: boolean;
  aiSettings: AISettings | null;
  aiBusy?: InspirationVariantKind;
  isAIRunning: boolean;
  onRequestAI: (action: InspirationVariantKind) => void;
  onAdoptVariant: (variant: Record<string, unknown>) => void;
  onCopyVariant: (content: string) => void;
  onRemoveVariant: (variantId: string) => void;
}

export function InboxItemDetail({
  item,
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
}: InboxItemDetailProps) {
  const source = item.source as
    | {
        bookTitle?: string;
        bookAuthor?: string;
        locationLabel?: string;
        chapterTitle?: string;
        progressPercent?: number;
        excerpt?: string;
        format?: string;
        createdAt?: string;
      }
    | null
    | undefined;

  return (
    <div className="stats-card inbox-detail">
      <div className="inbox-detail-head">
        <div>
          <div className="desktop-card-label">Editor</div>
          <h2 className="paper-title mt-1 text-xl font-semibold">条目正文</h2>
        </div>
        <div className="flex items-center gap-2">
          <span className={`inbox-save-status inbox-save-status--${saveStatus}`}>
            {saveStatus === "saving"
              ? "保存中…"
              : saveStatus === "saved"
              ? "已保存"
              : saveStatus === "unsaved"
              ? "未保存"
              : "只读"}
          </span>
          <Button
            size="sm"
            variant="tonal"
            onClick={onSaveDraft}
            disabled={saveStatus === "saving"}
          >
            <Save size={15} /> 保存
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <label className="grid gap-1.5 text-sm text-paper-muted">
          <span className="font-medium text-paper-ink">标题</span>
          <input
            className="paper-input h-9"
            value={draft.title}
            onChange={(event) => onDraftChange({ title: event.target.value })}
          />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <Select
            label="类型"
            value={draft.type}
            onChange={(event) => onDraftChange({ type: event.target.value })}
          >
            {(Object.keys(TYPE_LABELS) as InspirationType[]).map((value) => (
              <option key={value} value={value}>
                {TYPE_LABELS[value]}
              </option>
            ))}
            {!TYPE_LABELS[draft.type as InspirationType] && (
              <option value={draft.type}>{draft.type}</option>
            )}
          </Select>
          <Select
            label="状态"
            value={draft.status}
            onChange={(event) => onDraftChange({ status: event.target.value })}
          >
            {(Object.keys(STATUS_LABELS) as InspirationStatus[]).map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
            {!STATUS_LABELS[draft.status as InspirationStatus] && (
              <option value={draft.status}>{draft.status}</option>
            )}
          </Select>
        </div>
        <label className="grid gap-1.5 text-sm text-paper-muted">
          <span className="font-medium text-paper-ink">标签，逗号分隔</span>
          <input
            className="paper-input h-9"
            value={draft.tags}
            onChange={(event) => onDraftChange({ tags: event.target.value })}
            placeholder="修罗场, 系统流, 反差"
          />
        </label>
        <label className="grid gap-1.5 text-sm text-paper-muted">
          <span className="font-medium text-paper-ink">平台标签，逗号分隔</span>
          <input
            className="paper-input h-9"
            value={draft.platformTags}
            onChange={(event) => onDraftChange({ platformTags: event.target.value })}
            placeholder="番茄, 起点, 刺猬猫"
          />
        </label>
      </div>

      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">正文</span>
        <textarea
          className="paper-input mt-2 min-h-[200px] resize-y"
          value={draft.body}
          onChange={(event) => onDraftChange({ body: event.target.value })}
        />
      </label>

      {source && (
        <div className="mt-3 rounded-[var(--radius-2)] border border-paper-line bg-paper-soft/45 p-4 text-sm text-paper-muted">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="font-semibold text-paper-ink">来源卡片</div>
            {source.format && <span className="paper-chip uppercase">{source.format}</span>}
          </div>
          <div className="grid gap-1.5 text-xs leading-5">
            <div>
              来源书籍：
              <span className="text-paper-ink">
                {source.bookTitle ?? source.bookAuthor ?? "未知书籍"}
              </span>
              {source.bookAuthor ? ` · 作者：${source.bookAuthor}` : ""}
            </div>
            <div>
              位置：
              {source.locationLabel ??
                source.chapterTitle ??
                (typeof source.progressPercent === "number"
                  ? `${source.progressPercent.toFixed(1)}%`
                  : "未记录")}
            </div>
            {source.excerpt && (
              <blockquote className="mt-2 rounded-[var(--radius-2)] border border-paper-line bg-paper-panel/75 p-3 text-paper-ink">
                <div className="mb-1 text-[11px] font-semibold text-paper-muted">来源摘录</div>
                <div className="whitespace-pre-wrap leading-6">{source.excerpt}</div>
              </blockquote>
            )}
          </div>
        </div>
      )}

      <div className="mt-4 rounded-[var(--radius-2)] border border-paper-line bg-paper-panel">
        <div className="desktop-card-label">AI polish</div>
        <h2 className="paper-title mt-1 text-lg font-semibold">AI 候选版本</h2>
        {!aiAvailable ? (
          <div className="mt-2 rounded-md border border-paper-line bg-paper-soft/40 p-3 text-xs leading-6 text-paper-muted">
            {aiSettings?.enabled
              ? "已启用 AI 助手，但尚未配置 API Key。请先在设置中心保存 Key 后使用 AI 打磨；在此之前不会产生任何网络请求。"
              : "AI 助手未启用。开启并配置 Key 后，这里可以生成候选版本（不会自动覆盖正文）。"}
          </div>
        ) : (
          <p className="mt-2 text-xs leading-5 text-paper-muted">
            所有输出都进入候选版本，不会覆盖正文；开启后正文会发送到你配置的 AI 服务。
          </p>
        )}
        {aiAvailable && (
          <div className="mt-3 grid gap-2">
            {(
              Object.entries(AI_LABELS) as Array<[InspirationVariantKind, string]>
            ).map(([action, label]) => (
              <Button
                key={action}
                size="sm"
                variant="outline"
                // 左对齐：原 grid 族规则里有 justify-content:flex-start，容器仍用
                // grid 撑满整行，所以文字必须回到左侧。`!` 不是多余的：Tailwind
                // 同类工具按「值」排序发射，justify-start 排在组件 base 的
                // justify-center 之前，不加 important 会被 base 压掉（实测发射顺序）。
                className="!justify-start"
                disabled={isAIRunning || Boolean(aiBusy)}
                onClick={() => onRequestAI(action)}
              >
                <Sparkles size={15} className="text-copper" />
                {aiBusy === action ? "生成中..." : label}
              </Button>
            ))}
          </div>
        )}

        <div className="mt-3 grid gap-3">
          {item.variants.length === 0 ? (
            <div className="rounded-md border border-dashed border-paper-line p-4 text-sm text-paper-muted">
              还没有候选版本。{aiAvailable ? "点击上方按钮生成。" : "启用 AI 并配置 Key 后可生成。"}
            </div>
          ) : (
            item.variants.map((variant) => {
              const kind = String(variant.kind ?? "polish") as InspirationVariantKind;
              const model = typeof variant.model === "string" ? variant.model : "AI";
              const createdAt =
                typeof variant.createdAt === "string"
                  ? new Date(variant.createdAt).toLocaleString("zh-CN")
                  : "";
              const content = typeof variant.content === "string" ? variant.content : "";
              return (
                <article
                  key={String(variant.id)}
                  className="rounded-[var(--radius-2)] border border-paper-line bg-paper-soft/40 p-4"
                >
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <div className="text-xs font-medium text-copper">
                      {AI_LABELS[kind]} · {model}
                      {createdAt && <span className="ml-2 text-paper-muted">{createdAt}</span>}
                    </div>
                    <Button
                      variant="icon"
                      size="sm"
                      className="shrink-0"
                      title="移除候选"
                      aria-label="移除候选"
                      onClick={() => void onRemoveVariant(String(variant.id))}
                    >
                      <X size={14} />
                    </Button>
                  </div>
                  <div className="line-clamp-[8] whitespace-pre-wrap text-sm leading-7 text-paper-ink">
                    {content}
                  </div>
                  <div className="mt-3 flex gap-2">
                    <Button size="sm" variant="outline" onClick={() => void onCopyVariant(content)}>
                      <Copy size={12} /> 复制
                    </Button>
                    <Button size="sm" variant="tonal" onClick={() => void onAdoptVariant(variant)}>
                      <Check size={12} /> 采纳为正文
                    </Button>
                  </div>
                </article>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
