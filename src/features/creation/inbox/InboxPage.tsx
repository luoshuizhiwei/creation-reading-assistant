import { useCallback, useEffect, useState } from "react";
import { ArchiveRestore, Inbox as InboxIcon, Library, Trash2 } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { InboxItem } from "@/types/creation";

interface InboxPageProps {
  /** 转为资料卡的目标项目 ID。 */
  projectId: string;
}

export function InboxPage({ projectId }: InboxPageProps) {
  const { loadInbox, deleteInbox, updateInbox, runStructure } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [items, setItems] = useState<InboxItem[]>([]);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setItems(await loadInbox({ limit: 200 }));
  }, [loadInbox]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleDelete = async (itemId: string) => {
    const ok = await deleteInbox({ itemId });
    if (ok) {
      showToast({ tone: "success", title: "已移出收件箱", body: "条目已软删除。" });
      await refresh();
    }
    setConfirmingId(null);
  };

  const handleToCard = async (item: InboxItem) => {
    const ok = await runStructure({
      type: "card.create",
      projectId,
      kind: "reference",
      title: item.title,
      tags: item.tags,
      fields: { note: item.body.slice(0, 2000) }
    });
    if (!ok) return;
    await updateInbox({ itemId: item.id, baseRevision: item.revision, status: "used" });
    showToast({ tone: "success", title: "已转为资料卡", body: `「${item.title}」已加入当前项目的资料卡。` });
    await refresh();
  };

  return (
    <section className="stats-page" aria-label="全局收件箱">
      <div className="stats-card inbox-summary">
        <h3><ArchiveRestore size={15} /> 全局收件箱</h3>
        <p className="stats-note">
          旧灵感迁移后的存放位置（兼容期内旧数据保持只读）。可把条目转为当前项目的资料卡，或删除不再需要的内容。
        </p>
      </div>
      {items.length === 0 ? (
        <div className="stats-card">
          <p className="stats-note"><InboxIcon size={14} /> 收件箱为空。旧数据迁移后，旧灵感会出现在这里。</p>
        </div>
      ) : (
        <ul className="inbox-list">
          {items.map((item) => (
            <li key={item.id} className={`stats-card inbox-item ${item.status === "used" ? "inbox-item--used" : ""}`}>
              <span className="inbox-item-main">
                <span className="inbox-item-title">
                  {item.title}
                  {item.legacyId && <em>旧灵感</em>}
                  {item.status === "used" && <em className="used">已转卡片</em>}
                </span>
                <span className="inbox-item-body">{item.body.length > 160 ? `${item.body.slice(0, 160)}…` : item.body}</span>
                <span className="inbox-item-meta">
                  {item.tags.length > 0 && <>标签：{item.tags.join("，")}</>}
                  {item.tags.length > 0 && " · "}
                  AI 候选 {item.variants.length} 个
                  {item.source && " · 有来源"}
                  {item.updatedAt && ` · ${new Date(item.updatedAt).toLocaleDateString("zh-CN")}`}
                </span>
              </span>
              <span className="inbox-item-actions">
                {item.status !== "used" && (
                  <button type="button" onClick={() => void handleToCard(item)}>
                    <Library size={13} /> 转为资料卡
                  </button>
                )}
                <button
                  type="button"
                  className={confirmingId === item.id ? "confirming" : ""}
                  onClick={() => {
                    if (confirmingId === item.id) void handleDelete(item.id);
                    else setConfirmingId(item.id);
                  }}
                >
                  <Trash2 size={13} />
                  {confirmingId === item.id ? "确认移出" : "移出"}
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
