import { useEffect, useMemo, useState } from "react";
import { Flag, RotateCcw, Trash2 } from "lucide-react";
import { Tabs } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type {
  CreationProjectSummary,
  SnapshotInfo,
  SnapshotPreviewView,
  SnapshotSubjectType,
  TrashImpactView,
  TrashItem
} from "@/types/creation";
import { CreateMilestoneDialog } from "./CreateMilestoneDialog";
import { RestoreSnapshotDialog } from "./RestoreSnapshotDialog";
import { PurgeTrashDialog } from "./PurgeTrashDialog";
import {
  buildMilestoneCommand,
  buildSnapshotProtectionReason,
  classifySnapshotCategory,
  SNAPSHOT_CATEGORY_LABEL,
  SNAPSHOT_RETENTION_HINT,
  snapshotSubjectTitle,
  type RestoreSnapshotConfirmResult
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

// 同时映射运行时的原始 subject_type（scene-autosave / structure-operation），
// 避免自动快照 / 保护快照在列表里显示原始枚举值。
const SNAPSHOT_SUBJECT_LABEL: Record<string, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片",
  "scene-autosave": "场景",
  "structure-operation": "结构"
};

export function HistoryPage({ project }: HistoryPageProps) {
  const [tab, setTab] = useState<"trash" | "snapshots">("trash");
  const [trash, setTrash] = useState<TrashItem[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotInfo[]>([]);

  const [showCreateMilestone, setShowCreateMilestone] = useState(false);
  const [creatingMilestone, setCreatingMilestone] = useState(false);
  const [restoringSnapshot, setRestoringSnapshot] = useState<SnapshotInfo | null>(null);
  const [snapshotPreview, setSnapshotPreview] = useState<SnapshotPreviewView | null>(null);
  const [snapshotPreviewBusy, setSnapshotPreviewBusy] = useState(false);
  const [snapshotPreviewError, setSnapshotPreviewError] = useState<string | null>(null);
  const [purgingItem, setPurgingItem] = useState<TrashItem | null>(null);
  const [trashImpact, setTrashImpact] = useState<TrashImpactView | null>(null);
  const [trashImpactBusy, setTrashImpactBusy] = useState(false);
  const [trashImpactError, setTrashImpactError] = useState<string | null>(null);

  const {
    loadTrash, restoreTrash, purgeTrash, loadSnapshots, previewSnapshot,
    restoreSnapshotWithProtection, loadTrashImpact, runStructure, loadNavigation, loadCards, loadOutline
  } = useCreationActions();
  const navigation = useCreationStore((state) => state.navigations[project.id]);
  const outline = useCreationStore((state) => state.outlines?.[project.id]);
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

  const handleOpenPurge = async (item: TrashItem) => {
    setPurgingItem(item);
    setTrashImpact(null);
    setTrashImpactError(null);
    setTrashImpactBusy(true);
    try {
      const impact = await loadTrashImpact({
        kind: "trash.impact",
        projectId: project.id,
        entity: item.entity,
        entityId: item.id
      });
      if (impact) setTrashImpact(impact);
      else setTrashImpactError("无法读取永久删除影响，已禁止删除。请稍后重试。");
    } catch (error) {
      setTrashImpactError(error instanceof Error ? error.message : "无法读取永久删除影响，已禁止删除。");
    } finally {
      setTrashImpactBusy(false);
    }
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
  const handleConfirmRestore = async (): Promise<RestoreSnapshotConfirmResult> => {
    const snapshot = restoringSnapshot;
    if (!snapshot) return { ok: false, error: "快照无效。" };

    try {
      const result = await restoreSnapshotWithProtection({
        type: "snapshot.restoreWithProtection",
        projectId: project.id,
        snapshotId: snapshot.id,
        protectionReason: buildSnapshotProtectionReason(snapshot)
      });
      if (result?.ok) {
        showToast({ tone: "success", title: "已恢复到目标版本（恢复前保护已保留）" });
        // 刷新快照列表与项目视图（导航 / 卡片 / 大纲），保持各视图一致。
        if (tab === "snapshots") await refreshSnapshots();
        void loadNavigation(project.id);
        void loadCards({ projectId: project.id });
        void loadOutline(project.id);
        return { ok: true, protectionSnapshotId: result.protectionSnapshotId };
      }
      return { ok: false, error: "恢复失败，当前内容未被覆盖。对话框与预览已保留，请重试。" };
    } catch (e) {
      return {
        ok: false,
        error: "恢复失败，当前内容未被覆盖。" + (e instanceof Error ? ` 原因：${e.message}` : "")
      };
    }
  };

  const handleOpenRestore = async (snapshot: SnapshotInfo) => {
    setRestoringSnapshot(snapshot);
    setSnapshotPreview(null);
    setSnapshotPreviewError(null);
    setSnapshotPreviewBusy(true);
    try {
      const preview = await previewSnapshot({
        kind: "snapshot.preview",
        projectId: project.id,
        snapshotId: snapshot.id
      });
      if (preview) setSnapshotPreview(preview);
      else setSnapshotPreviewError("无法读取权威恢复预览，已禁止恢复。请稍后重试。");
    } catch (error) {
      setSnapshotPreviewError(error instanceof Error ? error.message : "无法读取权威恢复预览，已禁止恢复。");
    } finally {
      setSnapshotPreviewBusy(false);
    }
  };

  const canCreateMilestone = useMemo(() => {
    // 只要项目可用即可打开，空场景/空卡片会在对话框内提示
    return true;
  }, []);

  return (
    <section className="history-page" aria-label="历史与回收站">
      <div className="history-header-bar">
        <Tabs<"trash" | "snapshots">
          value={tab}
          onChange={setTab}
          items={[
            { id: "trash", label: "回收站" },
            { id: "snapshots", label: "版本快照" }
          ]}
          ariaLabel="历史视图"
        />
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
                    onClick={() => void handleOpenPurge(item)}
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
            const category = classifySnapshotCategory(snapshot);
            return (
              <li key={snapshot.id} className="history-item">
                <div className="history-item-head">
                  <span className="history-item-type">
                    {SNAPSHOT_SUBJECT_LABEL[snapshot.subjectType] ?? snapshot.subjectType}
                    <span
                      className={`snapshot-category-badge snapshot-category-${category}`}
                    >
                      {SNAPSHOT_CATEGORY_LABEL[category]}
                    </span>
                  </span>
                  <strong>{snapshot.reason || "(未命名里程碑)"}</strong>
                  <small>{new Date(snapshot.createdAt).toLocaleString("zh-CN")}</small>
                  <span className="history-item-subject">对象：{subjectTitle}</span>
                  <div className="history-item-actions">
                    <button
                      type="button"
                      onClick={() => void handleOpenRestore(snapshot)}
                      title="从快照恢复（会先创建保护快照）"
                    >
                      <RotateCcw size={14} /> 恢复
                    </button>
                  </div>
                </div>
                <span className="history-item-retention">{SNAPSHOT_RETENTION_HINT[category]}</span>
              </li>
            );
          })}
        </ul>
      )}

      {showCreateMilestone && (
        <CreateMilestoneDialog
          projectId={project.id}
          navigation={navigation}
          outline={outline}
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
          preview={snapshotPreview}
          previewBusy={snapshotPreviewBusy}
          previewError={snapshotPreviewError}
          onCancel={() => setRestoringSnapshot(null)}
          onConfirm={handleConfirmRestore}
        />
      )}

      {purgingItem && (
        <PurgeTrashDialog
          item={purgingItem}
          impact={trashImpact}
          impactBusy={trashImpactBusy}
          impactError={trashImpactError}
          onCancel={() => setPurgingItem(null)}
          onConfirm={handleConfirmPurge}
        />
      )}
    </section>
  );
}
