import { useEffect, useState } from "react";
import { RotateCcw, Trash2 } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { CreationProjectSummary, SnapshotInfo, TrashItem } from "@/types/creation";

interface HistoryPageProps {
  project: CreationProjectSummary;
}

const ENTITY_LABEL: Record<string, string> = { volume: "卷", chapter: "章", scene: "场景", card: "卡片" };

export function HistoryPage({ project }: HistoryPageProps) {
  const [tab, setTab] = useState<"trash" | "snapshots">("trash");
  const [trash, setTrash] = useState<TrashItem[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotInfo[]>([]);
  const [confirmId, setConfirmId] = useState<string>();
  const { loadTrash, restoreTrash, purgeTrash, loadSnapshots, runStructure } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);

  const refreshTrash = async () => setTrash(await loadTrash(project.id));
  const refreshSnapshots = async () =>
    setSnapshots(await loadSnapshots({ kind: "snapshot.list", projectId: project.id }));

  useEffect(() => {
    void refreshTrash();
  }, [project.id]);

  useEffect(() => {
    if (tab === "snapshots") void refreshSnapshots();
  }, [tab, project.id]);

  const doRestore = async (item: TrashItem) => {
    if (await restoreTrash(project.id, item.entity, item.id)) {
      showToast({ tone: "success", title: "已恢复" });
      void refreshTrash();
    }
  };

  const doPurge = async (item: TrashItem) => {
    if (confirmId !== item.id) {
      setConfirmId(item.id);
      return;
    }
    if (await purgeTrash(project.id, item.entity, item.id)) {
      showToast({ tone: "success", title: "已永久删除" });
      setConfirmId(undefined);
      void refreshTrash();
    }
  };

  const restoreSnapshot = async (snapshotId: string) => {
    if (await runStructure({ type: "snapshot.restore", projectId: project.id, snapshotId })) {
      showToast({ tone: "success", title: "已从快照恢复" });
    }
  };

  return (
    <section className="history-page" aria-label="历史与回收站">
      <div className="history-tabs" role="tablist" aria-label="历史视图">
        <button type="button" className={tab === "trash" ? "active" : ""} onClick={() => setTab("trash")}>
          回收站
        </button>
        <button type="button" className={tab === "snapshots" ? "active" : ""} onClick={() => setTab("snapshots")}>
          版本快照
        </button>
      </div>

      {tab === "trash" ? (
        trash.length === 0 ? (
          <p className="history-empty">
            回收站是空的。删除卷、章、场景或卡片后会出现在这里，可以恢复或永久删除。
          </p>
        ) : (
          <ul className="history-list">
            {trash.map((item) => (
              <li key={`${item.entity}-${item.id}`} className="history-item">
                <span className="history-item-type">{ENTITY_LABEL[item.entity] ?? item.entity}</span>
                <strong>{item.title}</strong>
                <small>{new Date(item.deletedAt).toLocaleString("zh-CN")}</small>
                <div className="history-item-actions">
                  <button type="button" onClick={() => void doRestore(item)} title="恢复">
                    <RotateCcw size={14} /> 恢复
                  </button>
                  <button
                    type="button"
                    className={confirmId === item.id ? "confirming" : ""}
                    onClick={() => void doPurge(item)}
                    title="永久删除"
                  >
                    <Trash2 size={14} /> {confirmId === item.id ? "确认永久删除？" : "永久删除"}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )
      ) : snapshots.length === 0 ? (
        <p className="history-empty">
          还没有命名快照。在场景正文或卡片上可创建快照里程碑，随时把对象恢复到该版本。
        </p>
      ) : (
        <ul className="history-list">
          {snapshots.map((snapshot) => (
            <li key={snapshot.id} className="history-item">
              <span className="history-item-type">{snapshot.subjectType === "scene" ? "场景" : "卡片"}</span>
              <strong>{snapshot.reason || "(未命名)"}</strong>
              <small>{new Date(snapshot.createdAt).toLocaleString("zh-CN")}</small>
              <div className="history-item-actions">
                <button type="button" onClick={() => void restoreSnapshot(snapshot.id)} title="从快照恢复">
                  <RotateCcw size={14} /> 恢复
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
