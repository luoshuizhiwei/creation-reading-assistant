import { useCallback, useEffect, useMemo, useState } from "react";
import { Download, Save, X } from "lucide-react";
import { Select, Tabs } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import { findChapterLocation } from "@/features/creation/outline/outline-impact";
import { SCENE_STATUS_OPTIONS } from "@/features/creation/scene-status";
import type { CreationProjectOutline, ScenePlanning, SceneStatus, StructureApplyResult } from "@/types/creation";

interface OutlinePageProps {
  project: { id: string; title: string; setup: { chapterWorkflow: string[] } };
}

/** 场景任务卡表单：视角/时间/地点/出场/目标/冲突/结果/情绪/目标字数（蓝图 §5.3）。 */

function ScenePlanningForm({ scene, onSaved }: {
  projectId: string;
  scene: { id: string; revision: number; summary?: string; status?: SceneStatus; planning?: ScenePlanning } | null;
  onSaved(): void;
}) {
  const { runStructure, loadScene } = useCreationActions();
  const cards = useCreationStore((state) => state.cards);
  const [planning, setPlanning] = useState<ScenePlanning>({});
  const [summary, setSummary] = useState("");
  const [status, setStatus] = useState<SceneStatus>("planned");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setPlanning({ ...(scene?.planning ?? {}) });
    setSummary(scene?.summary ?? "");
    setStatus(scene?.status ?? "planned");
  }, [scene]);

  if (!scene) {
    return <p className="cards-relations-empty">在左侧选择一个场景，编辑它的任务卡字段。</p>;
  }

  const set = (patch: Partial<ScenePlanning>) => setPlanning((current) => ({ ...current, ...patch }));
  const characterCards = cards.filter((card) => card.kind === "character");
  const locationCards = cards.filter((card) => card.kind === "location");

  const save = async () => {
    setSaving(true);
    const planningOk = await runStructure({ type: "scene.updatePlanning", sceneId: scene.id, planning });
    const metaOk = planningOk && await runStructure({
      type: "scene.updateMeta",
      sceneId: scene.id,
      baseRevision: scene.revision,
      summary,
      status
    });
    setSaving(false);
    if (metaOk) {
      await loadScene(scene.id);
      onSaved();
    }
  };

  const inputClass = "cards-input";

  return (
    <div className="scene-planning-form">
      <div className="scene-planning-grid">
        <label className="scene-planning-full">
          <span>场景摘要</span>
          <textarea className={inputClass} rows={3} maxLength={2000} value={summary} onChange={(event) => setSummary(event.target.value)} placeholder="这一场发生什么、推动了什么变化？" />
        </label>
        <label className="scene-planning-full">
          <span>场景状态</span>
          <Select value={status} onChange={(event) => setStatus(event.target.value as SceneStatus)} options={SCENE_STATUS_OPTIONS} />
          <small className="scene-status-help">场景状态独立于左侧章节工作流状态。</small>
        </label>
        <label>
          <span>视角角色</span>
          <Select
            value={planning.perspectiveCardId ?? ""}
            onChange={(event) => set({ perspectiveCardId: event.target.value || null })}
            options={[
              { value: "", label: "未设置" },
              ...characterCards.map((card) => ({ value: card.id, label: card.title }))
            ]}
          />
        </label>
        <label>
          <span>时间 / 相对时间</span>
          <input className={inputClass} value={planning.time ?? ""} onChange={(event) => set({ time: event.target.value || null })} placeholder="例如：入夜后、三年前" />
        </label>
        <label>
          <span>地点（背景）</span>
          <Select
            value={planning.locationCardId ?? ""}
            onChange={(event) => set({ locationCardId: event.target.value || null })}
            options={[
              { value: "", label: "未设置" },
              ...locationCards.map((card) => ({ value: card.id, label: card.title }))
            ]}
          />
        </label>
        <label>
          <span>目标字数</span>
          <input
            className={inputClass}
            type="number"
            min={1}
            max={1000000}
            value={planning.targetWords ?? ""}
            onChange={(event) => set({ targetWords: event.target.value ? Number(event.target.value) : null })}
            placeholder="例如：2500"
          />
        </label>
        <label className="scene-planning-cast">
          <span>出场卡片</span>
          <select
            className={inputClass}
            multiple
            value={planning.castCardIds ?? []}
            onChange={(event) => set({ castCardIds: Array.from(event.target.selectedOptions).map((option) => option.value) })}
          >
            {cards.filter((card) => card.kind === "character" || card.kind === "item").map((card) => (
              <option key={card.id} value={card.id}>{card.title}</option>
            ))}
          </select>
        </label>
        {(["goal", "conflict", "outcome", "emotion"] as const).map((key) => (
          <label key={key} className="scene-planning-full">
            <span>{key === "goal" ? "目标" : key === "conflict" ? "冲突" : key === "outcome" ? "结果" : "情绪"}</span>
            <input className={inputClass} value={planning[key] ?? ""} onChange={(event) => set({ [key]: event.target.value || null })} />
          </label>
        ))}
      </div>
      <div className="scene-planning-actions">
        <button type="button" className="scene-planning-save" onClick={() => void save()} disabled={saving}>
          <Save size={13} /> {saving ? "保存中…" : "保存场景卡"}
        </button>
      </div>
    </div>
  );
}

export function OutlinePage({ project }: OutlinePageProps) {
  const {
    loadOutline,
    loadCards,
    runStructure,
    previewStructure,
    applyStructureWithProtection,
    revertStructure,
    exportDraft
  } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const outlines = useCreationStore((state) => state.outlines);
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const selectScene = useCreationStore((state) => state.selectScene);
  const [view, setView] = useState<"tree" | "board">("tree");
  const [lastProtectedApply, setLastProtectedApply] = useState<StructureApplyResult | null>(null);
  const [revertBusy, setRevertBusy] = useState(false);
  const [revertError, setRevertError] = useState<string | null>(null);
  const [exportingOutline, setExportingOutline] = useState(false);

  useEffect(() => {
    void loadOutline(project.id);
    void loadCards({ projectId: project.id });
  }, [loadCards, loadOutline, project.id]);

  const outline = outlines[project.id] as CreationProjectOutline | undefined;
  const workflow = project.setup.chapterWorkflow;

  const selectedScene = useMemo(() => {
    if (!outline) return null;
    for (const volume of outline.volumes) {
      for (const chapter of volume.chapters) {
        const scene = chapter.scenes.find((item) => item.id === selectedSceneId);
        if (scene) return scene;
      }
    }
    for (const chapter of outline.looseChapters ?? []) {
      const scene = chapter.scenes.find((item) => item.id === selectedSceneId);
      if (scene) return scene;
    }
    return null;
  }, [outline, selectedSceneId]);

  const refresh = useCallback(() => {
    void loadOutline(project.id);
  }, [loadOutline, project.id]);

  const runStructureForTree = useCallback(
    async (command: Parameters<typeof runStructure>[0]): Promise<boolean> => {
      const ok = await runStructure(command);
      if (ok) refresh();
      return ok;
    },
    [refresh, runStructure]
  );

  const applyStructureForTree = useCallback(
    async (command: Parameters<typeof applyStructureWithProtection>[0]) => {
      const result = await applyStructureWithProtection(command);
      if (result) refresh();
      return result;
    },
    [applyStructureWithProtection, refresh]
  );

  const handleProtectedApplied = useCallback((result: StructureApplyResult) => {
    setLastProtectedApply(result);
    setRevertError(null);
  }, []);

  const revertLastProtectedApply = useCallback(async () => {
    if (!lastProtectedApply || revertBusy) return;
    setRevertBusy(true);
    setRevertError(null);
    try {
      const result = await revertStructure({
        type: "structure.revert",
        projectId: project.id,
        protectionSnapshotId: lastProtectedApply.protectionSnapshotId,
        expectedAppliedRevisions: lastProtectedApply.affected
      });
      if (!result) {
        setRevertError("无法撤回：大纲可能已被后续修改。请保留当前内容并重新检查。");
        return;
      }
      setLastProtectedApply(null);
      refresh();
    } catch (error) {
      setRevertError(error instanceof Error ? error.message : "无法撤回本次重组。");
    } finally {
      setRevertBusy(false);
    }
  }, [lastProtectedApply, project.id, refresh, revertBusy, revertStructure]);

  const [selectedChapterIds, setSelectedChapterIds] = useState<Set<string>>(new Set());
  const toggleChapterSelected = useCallback((id: string) => {
    setSelectedChapterIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const handleSceneReorder = useCallback(
    (sceneId: string, beforeSceneId?: string) => runStructureForTree({ type: "scene.reorder", sceneId, beforeSceneId }),
    [runStructureForTree]
  );

  const handleSceneMove = useCallback(
    (sceneId: string, targetChapterId: string, beforeSceneId?: string) => runStructureForTree({ type: "scene.move", sceneId, targetChapterId, beforeSceneId }),
    [runStructureForTree]
  );

  const handleChapterSetStatus = useCallback(
    (chapterId: string, status: string) => {
      if (!outline) return Promise.resolve(false);
      const revision = findChapterLocation(outline, chapterId)?.chapter.revision ?? 1;
      return runStructureForTree({ type: "chapter.setStatus", chapterId, status, baseRevision: revision });
    },
    [runStructureForTree, outline]
  );

  const handleChaptersSetStatus = useCallback(
    (chapterIds: string[], status: string) => runStructureForTree({ type: "chapters.setStatus", chapterIds, status }),
    [runStructureForTree]
  );

  const exportMarkdownOutline = useCallback(async () => {
    setExportingOutline(true);
    const result = await exportDraft(project.id, "outline-markdown");
    setExportingOutline(false);
    if (!result.canceled && result.filePath) {
      showToast({ tone: "success", title: "Markdown 大纲已导出", body: result.filePath });
    }
  }, [exportDraft, project.id, showToast]);

  return (
    <section className="outline-page" aria-label="大纲">
      <header className="outline-page-head">
        <Tabs<"tree" | "board">
          variant="pill"
          value={view}
          onChange={setView}
          items={[
            { id: "tree", label: "大纲树" },
            { id: "board", label: "场景卡板" }
          ]}
          ariaLabel="大纲视图切换"
        />
        <p className="outline-page-hint">树与卡片板共享同一数据与排序；选中场景可在右侧编辑任务卡。</p>
        <span className="outline-book-word-count">全书 {Number(outline?.wordCount ?? 0).toLocaleString("zh-CN")} 字</span>
        <button type="button" className="outline-export-button" disabled={!outline || exportingOutline} onClick={() => void exportMarkdownOutline()}>
          <Download size={14} /> {exportingOutline ? "导出中…" : "导出 Markdown 大纲"}
        </button>
      </header>
      {lastProtectedApply && (
        <div className="outline-revert-bar" role="status">
          <span>大纲安全重组已应用，并已创建保护快照。</span>
          <button type="button" aria-label="撤回本次重组" disabled={revertBusy} onClick={() => void revertLastProtectedApply()}>
            {revertBusy ? "撤回中…" : "撤回"}
          </button>
          <button type="button" aria-label="关闭撤回提示" disabled={revertBusy} onClick={() => { setLastProtectedApply(null); setRevertError(null); }}>
            <X size={12} />
          </button>
          {revertError && <span className="outline-revert-error" role="alert">{revertError}</span>}
        </div>
      )}
      <div className="outline-page-body">
        <div className="outline-page-main">
          {outline ? (
            view === "tree" ? (
              <OutlineTree
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={selectScene}
                runStructure={runStructureForTree}
                previewStructure={previewStructure}
                applyStructureWithProtection={applyStructureForTree}
                onProtectedApplied={handleProtectedApplied}
              />
            ) : (
              <CardBoard
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={selectScene}
                onSceneReorder={handleSceneReorder}
                onSceneMove={handleSceneMove}
                onChapterSetStatus={handleChapterSetStatus}
                onChaptersSetStatus={handleChaptersSetStatus}
                previewStructure={previewStructure}
                applyStructureWithProtection={applyStructureForTree}
                onProtectedApplied={handleProtectedApplied}
                selectedChapterIds={selectedChapterIds}
                onToggleChapterSelected={toggleChapterSelected}
              />
            )
          ) : (
            <p className="outline-loading" role="status">正在读取大纲…</p>
          )}
        </div>
        <aside className="outline-page-side" aria-label="场景任务卡">
          <p className="desktop-card-label">Scene card</p>
          <h3>场景任务卡</h3>
          <ScenePlanningForm
            projectId={project.id}
            scene={selectedScene ? {
              id: selectedScene.id,
              revision: selectedScene.revision,
              summary: selectedScene.summary,
              status: selectedScene.status,
              planning: selectedScene.planning
            } : null}
            onSaved={refresh}
          />
          {selectedScene && (
            <button type="button" className="scene-planning-clear" onClick={() => selectScene("")}>
              <X size={12} /> 取消选择
            </button>
          )}
        </aside>
      </div>
    </section>
  );
}
