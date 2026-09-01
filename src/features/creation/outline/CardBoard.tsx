import  { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, FileText, LayoutGrid, ListTree } from "lucide-react";
import type {
  CreationOutlineChapter,
  CreationProjectOutline,
  ProtectedStructureCommand,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructurePreviewCommand,
  StructurePreviewView
} from "@/types/creation";
import { computeBatchStatusImpact, computeSceneMoveImpact } from "./outline-impact";
import { reorderTarget } from "./outline-reorder";
import { StructureImpactDialog } from "./StructureImpactDialog";
import "./outline-reorg.css";

interface CardBoardProps {
  outline: CreationProjectOutline;
  workflow: string[];
  selectedSceneId?: string;
  onSelectScene: (sceneId: string) => void;
  /**
   * 可选编辑能力：独立大纲页接线后启用；缺省时卡板保持只读。
   * 所有编辑回调返回 Promise<boolean>：调用方据此进入 busy、显式报错、避免重复提交，
   * 不采用乐观成功；失败（false / 抛错）时调用方保留对话框并展示错误。
   */
  onSceneReorder?: (sceneId: string, beforeSceneId?: string) => Promise<boolean>;
  onSceneMove?: (sceneId: string, targetChapterId: string, beforeSceneId?: string) => Promise<boolean>;
  onChapterSetStatus?: (chapterId: string, status: string) => Promise<boolean>;
  onChaptersSetStatus?: (chapterIds: string[], status: string) => Promise<boolean>;
  previewStructure?: (command: StructurePreviewCommand) => Promise<StructurePreviewView | null>;
  applyStructureWithProtection?: (command: StructureApplyWithProtectionCommand) => Promise<StructureApplyResult | null>;
  onProtectedApplied?: (result: StructureApplyResult) => void;
  selectedChapterIds?: ReadonlyArray<string> | ReadonlySet<string>;
  onToggleChapterSelected?: (chapterId: string) => void;
}

type GroupMode = "chapter" | "status";

function chapterKey(chapter: CreationOutlineChapter): string {
  return [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
}

export function CardBoard({
  outline,
  workflow,
  selectedSceneId,
  onSelectScene,
  onSceneReorder,
  onSceneMove,
  onChapterSetStatus,
  onChaptersSetStatus,
  previewStructure,
  applyStructureWithProtection,
  onProtectedApplied,
  selectedChapterIds,
  onToggleChapterSelected
}: CardBoardProps) {
  const [mode, setMode] = useState<GroupMode>("chapter");
  const [pendingOp, setPendingOp] = useState<
    | { type: "sceneMove"; sceneId: string; targetChapterId: string }
    | { type: "batchStatus"; status: string }
    | null
  >(null);
  const [batchStatus, setBatchStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<StructurePreviewView | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const submittingRef = useRef(false);

  /**
   * 统一的编辑事务包装：进入 busy、清除旧错误、执行回调。
   * 返回 false 或抛错时设置错误且不关闭调用方对话框（避免乐观成功）。
   * submittingRef 防止并发重复提交（与按钮 disabled 双保险）。
   */
  const runEdit = async (action: () => Promise<boolean>): Promise<boolean> => {
    if (submittingRef.current) return false;
    submittingRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const ok = await action();
      if (!ok) setError("操作未成功，请重试。");
      return ok;
    } catch (e) {
      setError(e instanceof Error ? e.message : "操作失败。");
      return false;
    } finally {
      submittingRef.current = false;
      setBusy(false);
    }
  };

  const selectedSet = selectedChapterIds ? new Set(selectedChapterIds) : null;
  const editable = Boolean(onSceneReorder || onSceneMove || onChapterSetStatus || onChaptersSetStatus);

  const flatChapters = outline.volumes.flatMap((volume) =>
    volume.chapters.map((chapter) => ({ volumeTitle: volume.title, chapter }))
  );

  const allScenes = flatChapters.flatMap(({ volumeTitle, chapter }) =>
    chapter.scenes.map((scene) => ({ volumeTitle, chapter, scene }))
  );

  const scenesByStatus = new Map<string, typeof allScenes>();
  for (const item of allScenes) {
    const list = scenesByStatus.get(item.chapter.status) ?? [];
    list.push(item);
    scenesByStatus.set(item.chapter.status, list);
  }

  const pendingCommand = (): ProtectedStructureCommand | null => {
    if (!pendingOp) return null;
    if (pendingOp.type === "sceneMove") {
      return { type: "scene.move", sceneId: pendingOp.sceneId, targetChapterId: pendingOp.targetChapterId };
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    return { type: "chapters.setStatus", chapterIds: ids, status: pendingOp.status };
  };

  useEffect(() => {
    setPreview(null);
    setError(null);
    if (!pendingOp || !previewStructure) return;
    const command = pendingCommand();
    if (!command) return;
    let active = true;
    setPreviewBusy(true);
    void previewStructure({ type: "structure.preview", projectId: outline.project.id, command })
      .then((result) => {
        if (!active) return;
        if (result) setPreview(result);
        else setError("无法取得权威影响预览，请重试。");
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : "无法取得权威影响预览，请重试。");
      })
      .finally(() => {
        if (active) setPreviewBusy(false);
      });
    return () => { active = false; };
    // selectedSet 每次渲染都是新 Set；选中项变化会通过 pendingOp 的重新发起获得新预览。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outline.project.id, pendingOp, previewStructure]);

  const applyPending = async (): Promise<boolean> => {
    if (!pendingOp) return false;
    if (previewStructure && (!preview || preview.stale)) return false;
    if (applyStructureWithProtection) {
      const result = await applyStructureWithProtection({
        type: "structure.applyWithProtection",
        projectId: outline.project.id,
        planId: preview?.planId ?? "",
        protectionReason: `卡板安全重组：${pendingOp.type}`
      });
      if (!result) return false;
      onProtectedApplied?.(result);
      return true;
    }
    if (pendingOp.type === "sceneMove") {
      return onSceneMove!(pendingOp.sceneId, pendingOp.targetChapterId);
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    return onChaptersSetStatus!(ids, pendingOp.status);
  };

  const renderSceneActions = (chapter: CreationOutlineChapter, sceneId: string) => {
    if (!editable) return null;
    const sceneIds = chapter.scenes.map((item) => item.id);
    const upTarget = reorderTarget(sceneIds, sceneId, "up");
    const downTarget = reorderTarget(sceneIds, sceneId, "down");
    const upBefore = upTarget.canMove ? upTarget.beforeId : undefined;
    const downBefore = downTarget.canMove ? downTarget.beforeId : undefined;
    return (
      <span className="card-board-card-actions">
        {onSceneReorder && (
          <span className="card-board-reorder">
            <button type="button" title="上移" disabled={!upTarget.canMove || busy} onClick={() => void runEdit(() => onSceneReorder(sceneId, upBefore))}>
              <ChevronUp size={12} />
            </button>
            <button type="button" title="下移" disabled={!downTarget.canMove || busy} onClick={() => void runEdit(() => onSceneReorder(sceneId, downBefore))}>
              <ChevronDown size={12} />
            </button>
          </span>
        )}
        {onSceneMove && (
          <select
            className="card-board-move-select"
            aria-label="移动到章节"
            value=""
            onChange={(event) => {
              if (event.target.value) setPendingOp({ type: "sceneMove", sceneId, targetChapterId: event.target.value });
            }}
          >
            <option value="" disabled>移章</option>
            {flatChapters
              .filter((item) => item.chapter.id !== chapter.id)
              .map((item) => (
                <option key={item.chapter.id} value={item.chapter.id}>
                  {chapterKey(item.chapter)}
                </option>
              ))}
          </select>
        )}
      </span>
    );
  };

  const renderCard = (item: (typeof allScenes)[number]) => (
    <div key={item.scene.id} className={`card-board-card ${item.scene.id === selectedSceneId ? "active" : ""}`}>
      <button type="button" className="card-board-card-main" onClick={() => onSelectScene(item.scene.id)}>
        <span className="card-board-card-title">
          <FileText size={13} />
          {item.scene.title}
        </span>
        <span className="card-board-card-meta">
          {chapterKey(item.chapter)}
          {item.volumeTitle && ` · ${item.volumeTitle}`}
          <em>{item.scene.wordCount.toLocaleString("zh-CN")}字</em>
        </span>
        <span className="card-board-card-status">{item.chapter.status}</span>
      </button>
      {renderSceneActions(item.chapter, item.scene.id)}
    </div>
  );

  const renderPendingImpact = () => {
    if (!pendingOp) return null;
    if (pendingOp.type === "sceneMove") {
      const impact = computeSceneMoveImpact(outline, pendingOp.sceneId, pendingOp.targetChapterId);
      if (!impact) return null;
      return (
        <StructureImpactDialog
          title="场景跨章移动影响预览"
          description="场景将从源章移动到目标章。确认后才会写入。"
          rows={preview?.rows ?? impact.rows}
          busy={busy || previewBusy}
          error={error}
          notice={preview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
          confirmDisabled={Boolean(previewStructure && (!preview || preview.stale))}
          confirmLabel="确认移动"
          onCancel={() => {
            setError(null);
            setPendingOp(null);
          }}
          onConfirm={() => void runEdit(applyPending).then((ok) => {
            if (ok) setPendingOp(null);
          })}
        />
      );
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    const impact = computeBatchStatusImpact(outline, ids, pendingOp.status);
    return (
      <StructureImpactDialog
        title="批量状态影响预览"
        description="以下章节的状态将被统一修改。确认后才会写入。"
        rows={preview?.rows ?? impact.rows}
        busy={busy || previewBusy}
        error={error}
        notice={preview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
        confirmDisabled={Boolean(previewStructure && (!preview || preview.stale))}
        confirmLabel="确认修改"
        onCancel={() => {
          setError(null);
          setPendingOp(null);
        }}
        onConfirm={() => void runEdit(applyPending).then((ok) => {
          if (ok) setPendingOp(null);
        })}
      />
    );
  };

  return (
    <div className="card-board">
      <div className="card-board-toolbar">
        <span className="outline-toolbar-label">卡片板</span>
        <div className="card-board-mode" role="group" aria-label="分组方式">
          <button type="button" className={mode === "chapter" ? "active" : ""} onClick={() => setMode("chapter")}>
            <ListTree size={12} /> 按章节
          </button>
          <button type="button" className={mode === "status" ? "active" : ""} onClick={() => setMode("status")}>
            <LayoutGrid size={12} /> 按状态
          </button>
        </div>
      </div>
      {error && (
        <div className="card-board-error" role="alert">
          <span>{error}</span>
          <button type="button" className="card-board-error-close" aria-label="关闭错误提示" onClick={() => setError(null)}>
            ×
          </button>
        </div>
      )}
      {onChaptersSetStatus && selectedSet && selectedSet.size > 0 && (
        <div className="card-board-batch-bar">
          <span className="card-board-batch-count">已选 {selectedSet.size} 章</span>
          <select aria-label="批量状态" value={batchStatus} onChange={(event) => setBatchStatus(event.target.value)}>
            <option value="" disabled>设置状态</option>
            {workflow.map((step) => (
              <option key={step} value={step}>
                {step}
              </option>
            ))}
          </select>
          <button type="button" disabled={!batchStatus} onClick={() => setPendingOp({ type: "batchStatus", status: batchStatus })}>
            应用
          </button>
        </div>
      )}
      <div className="card-board-scroll">
        {allScenes.length === 0 && <p className="card-board-empty">暂无场景。在左侧大纲中新建卷、章节或场景。</p>}
        {mode === "chapter"
          ? flatChapters.map(({ volumeTitle, chapter }) => (
              <section key={chapter.id} className="card-board-group">
                <header className="card-board-group-head">
                  {onToggleChapterSelected && selectedSet && (
                    <label className="card-board-select">
                      <input
                        type="checkbox"
                        checked={selectedSet.has(chapter.id)}
                        onChange={() => onToggleChapterSelected(chapter.id)}
                        aria-label={`选择 ${chapterKey(chapter)}`}
                      />
                    </label>
                  )}
                  <span className="card-board-chapter-key">{chapterKey(chapter)}</span>
                  {volumeTitle && <small>{volumeTitle}</small>}
                  <em>
                    {chapter.scenes.length}场景 ·{" "}
                    {chapter.scenes.reduce((sum, s) => sum + s.wordCount, 0).toLocaleString("zh-CN")}字
                  </em>
                  {onChapterSetStatus && (
                    <select
                      className="outline-status-select"
                      aria-label="章节状态"
                      value={chapter.status}
                      disabled={busy}
                      onChange={(event) => void runEdit(() => onChapterSetStatus(chapter.id, event.target.value))}
                    >
                      {workflow.map((step) => (
                        <option key={step} value={step}>
                          {step}
                        </option>
                      ))}
                    </select>
                  )}
                </header>
                <div className="card-board-grid">
                  {chapter.scenes.map((scene) => renderCard({ volumeTitle, chapter, scene }))}
                  {chapter.scenes.length === 0 && <span className="card-board-empty-hint">暂无场景</span>}
                </div>
              </section>
            ))
          : workflow.map((status) => {
              const items = scenesByStatus.get(status) ?? [];
              if (items.length === 0) return null;
              return (
                <section key={status} className="card-board-group">
                  <header className="card-board-group-head">
                    <span className="card-board-status-key">{status}</span>
                    <em>
                      {items.length}场景 ·{" "}
                      {items.reduce((sum, item) => sum + item.scene.wordCount, 0).toLocaleString("zh-CN")}字
                    </em>
                  </header>
                  <div className="card-board-grid">{items.map(renderCard)}</div>
                </section>
              );
            })}
      </div>
      {pendingOp && renderPendingImpact()}
    </div>
  );
}
