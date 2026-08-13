import { useEffect, useMemo, useState } from "react";
import { Flag, RotateCcw, Trash2 } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type {
  CreationProjectSummary,
  SnapshotInfo,
  SnapshotSubjectType,
  TrashItem
} from "@/types/creation";
import { CreateMilestoneDialog } from "./CreateMilestoneDialog";
import { RestoreSnapshotDialog } from "./RestoreSnapshotDialog";
import { PurgeTrashDialog } from "./PurgeTrashDialog";
import {
  buildMilestoneCommand,
  buildRestoreErrorMessage,
  estimateTrashChildCount,
  planRestoreWithProtection,
  snapshotSubjectTitle
} from "./history-models";
import "./history-local.css";

interface HistoryPageProps {
  project: CreationProjectSummary;
}

const ENTITY_LABEL: Record<string, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片"
};

const SNAPSHOT_SUBJECT_LABEL: Record<SnapshotSubjectType, string> = {
  scene: "场景",
  card: "卡片"
};

export function HistoryPage({ project }: HistoryPageProps) {
  const [tab, setTab] = useState<"trash" | "snapshots">("trash");
  const [trash, setTrash] = useState<TrashItem[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotInfo[]>([]);

  const [showCreateMilestone, setShowCreateMilestone] = useState(false);
  const [creatingMilestone, setCreatingMilestone] = useState(false);
  const [restoringSnapshot, setRestoringSnapshot] = useState<SnapshotInfo | null>(null);
  const [purgingItem, setPurgingItem] = useState<TrashItem | null>(null);

  const { loadTrash, restoreTrash, purgeTrash, loadSnapshots, runStructure, loadNavigation, loadCards } =
    useCreationActions();
  const navigation = useCreationStore((state) => state.navigations[project.id]);
  const cards = useCreationStore((state) =>
    state.cardProjectId === project.id ? state.cards : []
  );
  const showToast = useUIStore((state) => state.showToast);

  const refreshTrash = async () => setTrash(await loadTrash(project.id));
  const refreshSnapshots = async () =>
    setSnapshots(await loadSnapshots({ kind: "snapshot.list", projectId: project.id }));

  useEffect(() => {
    void refreshTrash();
    // 预加载导航和卡片，供里程碑对话框选择对象
    void loadNavigation(project.id);
    void loadCards({ projectId: project.id });
  }, [project.id]);

  useEffect(() => {
    if (tab === "snapshots") void refreshSnapshots();
  }, [tab, project.id]);

  const getSubjectTitle = (snapshot: SnapshotInfo): string =>
    snapshotSubjectTitle(snapshot, navigation, cards);

  // ---------- 回收站：恢复 ----------
  const handleRestoreTrash = async (item: TrashItem) => {
    if (await restoreTrash(project.id, item.entity, item.id)) {
      showToast({ tone: "success", title: `已恢复 ${ENTITY_LABEL[item.entity] ?? "条目"}：${item.title}` });
      void refreshTrash();
    }
  };

  // ---------- 回收站：永久删除 ----------
  const handleConfirmPurge = async (): Promise<{ ok: boolean; error?: string | null }> => {
    if (!purgingItem) return { ok: false, error: "条目无效。" };
    const ok = await purgeTrash(project.id, purgingItem.entity, purgingItem.id);
    if (ok) {
      showToast({
        tone: "success",
        title: `已永久删除 ${ENTITY_LABEL[purgingItem.entity] ?? "条目"}：${purgingItem.title}`
      });
      void refreshTrash();
      return { ok: true };
    }
    return { ok: false, error: "永久删除失败，请稍后重试。" };
  };

  // ---------- 快照：创建命名里程碑 ----------
  const handleCreateMilestone = async (
    subjectType: SnapshotSubjectType,
    subjectId: string,
    reason: string
  ): Promise<boolean> => {
    setCreatingMilestone(true);
    try {
      const command = buildMilestoneCommand({
        projectId: project.id,
        subjectType,
        subjectId,
        reason
      });
      const ok = await runStructure(command);
      if (ok) {
        showToast({ tone: "success", title: "里程碑已创建" });
        if (tab === "snapshots") await refreshSnapshots();
      }
      return ok;
    } finally {
      setCreatingMilestone(false);
    }
  };

  // ---------- 快照：先保护再恢复 ----------
  const handleConfirmRestore = async (): Promise<{ ok: boolean; error?: string | null }> => {
    const snapshot = restoringSnapshot;
    if (!snapshot) return { ok: false, error: "快照无效。" };

    const plan = planRestoreWithProtection({
      projectId: project.id,
      snapshot,
      userConfirmed: true
    });
    const protectionCmd = plan.actions.find((a) => a.kind === "create-protection")?.command as
      | { type: "snapshot.create"; projectId: string; subjectType: SnapshotSubjectType; subjectId: string; reason: string }
      | undefined;
    if (!protectionCmd) {
      return { ok: false, error: "无法生成恢复计划，请重试。" };
    }

    // Step 1: 创建保护快照
    const protectionOk = await runStructure(protectionCmd);

    if (!protectionOk) {
      // 保护失败：中止恢复
      return { ok: false, error: buildRestoreErrorMessage("protection-failed") };
    }

    // Step 2: 保护成功后，执行目标快照恢复
    try {
      const restoreCmd = plan.actions.find((a) => a.kind === "restore-target")?.command as
        | { type: "snapshot.restore"; projectId: string; snapshotId: string }
        | undefined;
      if (!restoreCmd) {
        return { ok: false, error: buildRestoreErrorMessage("restore-failed") };
      }
      const restoreOk = await runStructure(restoreCmd);
      if (restoreOk) {
        showToast({ tone: "success", title: "已恢复到目标版本（保护快照已保留）" });
        if (tab === "snapshots") await refreshSnapshots();
        return { ok: true };
      }
      return { ok: false, error: buildRestoreErrorMessage("restore-failed") };
    } catch (e) {
      return {
        ok: false,
        error: buildRestoreErrorMessage("restore-failed") + (e instanceof Error ? ` 原因：${e.message}` : "")
      };
    }
  };

  const canCreateMilestone = useMemo(() => {
    // 只要项目可用即可打开，空场景/空卡片会在对话框内提示
    return true;
  }, []);

  return (
    <section className="history-page" aria-label="历史与回收站">
      <div className="history-header-bar">
        <div className="history-tabs" role="tablist" aria-label="历史视图">
          <button
            type="button"
            role="tab"
            aria-selected={tab === "trash"}
            className={tab === "trash" ? "active" : ""}
            onClick={() => setTab("trash")}
          >
            回收站
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "snapshots"}
            className={tab === "snapshots" ? "active" : ""}
            onClick={() => setTab("snapshots")}
          >
            版本快照
          </button>
        </div>
        {tab === "snapshots" && (
          <button
            type="button"
            className="history-create-btn"
            onClick={() => setShowCreateMilestone(true)}
            disabled={!canCreateMilestone || creatingMilestone}
            title="为场景或卡片创建命名里程碑快照"
          >
            <Flag size={14} /> 创建里程碑
          </button>
        )}
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
                <span className="history-item-type">
                  {ENTITY_LABEL[item.entity] ?? item.entity}
                </span>
                <strong>{item.title}</strong>
                <small>{new Date(item.deletedAt).toLocaleString("zh-CN")}</small>
                <span className="history-item-subject"></span>
                <div className="history-item-actions">
                  <button type="button" onClick={() => void handleRestoreTrash(item)} title="恢复">
                    <RotateCcw size={14} /> 恢复
                  </button>
                  <button
                    type="button"
                    className="confirming"
                    onClick={() => setPurgingItem(item)}
                    title="永久删除"
                  >
                    <Trash2 size={14} /> 永久删除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )
      ) : snapshots.length === 0 ? (
        <p className="history-empty">
          还没有命名快照。点击右上角「创建里程碑」为场景或卡片创建命名快照，随时把对象恢复到该版本。
        </p>
      ) : (
        <ul className="history-list">
          {snapshots.map((snapshot) => {
            const subjectTitle = getSubjectTitle(snapshot);
            return (
              <li key={snapshot.id} className="history-item">
                <span className="history-item-type">
                  {SNAPSHOT_SUBJECT_LABEL[snapshot.subjectType] ?? snapshot.subjectType}
                </span>
                <strong>{snapshot.reason || "(未命名里程碑)"}</strong>
                <small>{new Date(snapshot.createdAt).toLocaleString("zh-CN")}</small>
                <span className="history-item-subject">对象：{subjectTitle}</span>
                <div className="history-item-actions">
                  <button
                    type="button"
                    onClick={() => setRestoringSnapshot(snapshot)}
                    title="从快照恢复（会先创建保护快照）"
                  >
                    <RotateCcw size={14} /> 恢复
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {showCreateMilestone && (
        <CreateMilestoneDialog
          projectId={project.id}
          navigation={navigation}
          cards={cards}
          onCancel={() => setShowCreateMilestone(false)}
          onSubmit={handleCreateMilestone}
          busy={creatingMilestone}
        />
      )}

      {restoringSnapshot && (
        <RestoreSnapshotDialog
          snapshot={restoringSnapshot}
          subjectTitle={getSubjectTitle(restoringSnapshot)}
          onCancel={() => setRestoringSnapshot(null)}
          onConfirm={handleConfirmRestore}
        />
      )}

      {purgingItem && (
        <PurgeTrashDialog
          item={purgingItem}
          childCount={estimateTrashChildCount(navigation, purgingItem)}
          onCancel={() => setPurgingItem(null)}
          onConfirm={handleConfirmPurge}
        />
      )}
    </section>
  );
}
