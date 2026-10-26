import { useEffect, useMemo, useRef, useState } from "react";
import { Flag, RotateCcw, Trash2 } from "lucide-react";
import { Button, Tabs } from "@/components/ui";
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

  // BD-197：回收站的「恢复/永久删除」成功后条目随列表刷新整行卸载，握着焦点的
  // 按钮被移除、焦点跌回 body（WCAG 2.4.3，BD-190 收件箱删行的同型缺陷）——
  // 键盘用户要从文档开头重新 Tab。动作前记下被移除行的键与当时的行序，列表
  // 换血后把焦点交接给原位置的下一行（末尾则退上一行）；删空退到列表容器。
  const rowActionRefs = useRef(new Map<string, HTMLElement>());
  const pendingFocusRef = useRef<{ key: string; order: string[] } | null>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const emptyRef = useRef<HTMLParagraphElement>(null);
  const rowKey = (item: TrashItem) => `${item.entity}-${item.id}`;
  useEffect(() => {
    const pending = pendingFocusRef.current;
    if (!pending) return;
    const current = trash.map(rowKey);
    if (current.includes(pending.key)) return; // 条目没走（失败/未刷新），不动焦点
    pendingFocusRef.current = null;
    const idx = pending.order.indexOf(pending.key);
    const survivors = current.filter((key) => key !== pending.key);
    const handoffKey = survivors[idx] ?? survivors[idx - 1] ?? null;
    const target = handoffKey ? rowActionRefs.current.get(handoffKey) : null;
    // 删空时 ul 随空态换掉，退路是空态段落（tabIndex=-1），焦点仍留在原地区域。
    (target?.isConnected ? target : listRef.current ?? emptyRef.current)?.focus({ preventScroll: true });
  }, [trash]);

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
    pendingFocusRef.current = { key: rowKey(item), order: trash.map(rowKey) };
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
    // BD-197：点「永久删除」时先记下行序快照——确认发生在对话框里，届时若再取
    // trash 已是同一数组没问题，但交接判据要在条目消失前成立，与恢复流动作对称。
    pendingFocusRef.current = { key: rowKey(item), order: trash.map(rowKey) };
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

  const handleCancelPurge = () => {
    // BD-197：取消即作废交接预约，防止下一次无关的列表刷新误把焦点搬走。
    pendingFocusRef.current = null;
    setPurgingItem(null);
  };

  // PurgeTrashDialog 成功路径原本也借 onCancel 关窗——成功必须保留交接预约，
  // 与取消分开：确认成功走这条，只关窗不清预约。
  const handleClosePurged = () => setPurgingItem(null);

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
          <Button variant="tonal" onClick={() => setShowCreateMilestone(true)} disabled={!canCreateMilestone || creatingMilestone} title="为场景或卡片创建命名里程碑快照">
            <Flag size={14} /> 创建里程碑
          </Button>
        )}
      </div>

      {tab === "trash" ? (
        trash.length === 0 ? (
          <p className="history-empty" ref={emptyRef} tabIndex={-1}>
            回收站是空的。删除卷、章、场景或卡片后会出现在这里，可以恢复或永久删除。
          </p>
        ) : (
          <ul className="history-list" ref={listRef} tabIndex={-1}>
            {trash.map((item) => (
              <li key={`${item.entity}-${item.id}`} className="history-item">
                <span className="history-item-type">
                  {ENTITY_LABEL[item.entity] ?? item.entity}
                </span>
                <strong>{item.title}</strong>
                <small>{new Date(item.deletedAt).toLocaleString("zh-CN")}</small>
                <span className="history-item-subject"></span>
                <div className="history-item-actions">
                  <Button size="sm" variant="outline" onClick={() => void handleRestoreTrash(item)} title="恢复" aria-label={`恢复：${item.title}`} ref={(node) => {
                    // BD-197：交接锚点——每行记「恢复」按钮；卸载时清账防 stale。
                    const key = rowKey(item);
                    if (node) rowActionRefs.current.set(key, node);
                    else rowActionRefs.current.delete(key);
                  }}>
                    <RotateCcw size={14} /> 恢复
                  </Button>
                  <Button size="sm" variant="danger-outline" onClick={() => void handleOpenPurge(item)} title="永久删除" aria-label={`永久删除：${item.title}`}>
                    <Trash2 size={14} /> 永久删除
                  </Button>
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
                    <Button size="sm" variant="outline" onClick={() => void handleOpenRestore(snapshot)} title="从快照恢复（会先创建保护快照）">
                      <RotateCcw size={14} /> 恢复
                    </Button>
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
          onCancel={handleCancelPurge}
          onCloseSuccess={handleClosePurged}
          onConfirm={handleConfirmPurge}
        />
      )}
    </section>
  );
}
