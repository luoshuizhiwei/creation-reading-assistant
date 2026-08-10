import { useCallback, useEffect, useMemo, useState } from "react";
import { ListTree, LayoutGrid, Save, X } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import type { CreationProjectOutline, ScenePlanning } from "@/types/creation";

interface OutlinePageProps {
  project: { id: string; title: string; setup: { chapterWorkflow: string[] } };
}

/** 场景任务卡表单：视角/时间/地点/出场/目标/冲突/结果/情绪/目标字数（蓝图 §5.3）。 */
function ScenePlanningForm({ projectId, scene, onSaved }: { projectId: string; scene: { id: string; planning?: ScenePlanning } | null; onSaved(): void }) {
  const { runStructure } = useCreationActions();
  const cards = useCreationStore((state) => state.cards);
  const [planning, setPlanning] = useState<ScenePlanning>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setPlanning({ ...(scene?.planning ?? {}) });
  }, [scene]);

  if (!scene) {
    return <p className="cards-relations-empty">在左侧选择一个场景，编辑它的任务卡字段。</p>;
  }

  const set = (patch: Partial<ScenePlanning>) => setPlanning((current) => ({ ...current, ...patch }));
  const characterCards = cards.filter((card) => card.kind === "character");
  const locationCards = cards.filter((card) => card.kind === "location");

  const save = async () => {
    setSaving(true);
    const ok = await runStructure({ type: "scene.updatePlanning", sceneId: scene.id, planning });
    setSaving(false);
    if (ok) {
      onSaved();
    }
  };

  const inputClass = "cards-input";

  return (
    <div className="scene-planning-form">
      <div className="scene-planning-grid">
        <label>
          <span>视角角色</span>
          <select className={inputClass} value={planning.perspectiveCardId ?? ""} onChange={(event) => set({ perspectiveCardId: event.target.value || undefined })}>
            <option value="">未设置</option>
            {characterCards.map((card) => <option key={card.id} value={card.id}>{card.title}</option>)}
          </select>
        </label>
        <label>
          <span>时间 / 相对时间</span>
          <input className={inputClass} value={planning.time ?? ""} onChange={(event) => set({ time: event.target.value || undefined })} placeholder="例如：入夜后、三年前" />
        </label>
        <label>
          <span>地点（背景）</span>
          <select className={inputClass} value={planning.locationCardId ?? ""} onChange={(event) => set({ locationCardId: event.target.value || undefined })}>
            <option value="">未设置</option>
            {locationCards.map((card) => <option key={card.id} value={card.id}>{card.title}</option>)}
          </select>
        </label>
        <label>
          <span>目标字数</span>
          <input
            className={inputClass}
            type="number"
            min={1}
            max={1000000}
            value={planning.targetWords ?? ""}
            onChange={(event) => set({ targetWords: event.target.value ? Number(event.target.value) : undefined })}
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
            <input className={inputClass} value={planning[key] ?? ""} onChange={(event) => set({ [key]: event.target.value || undefined })} />
          </label>
        ))}
      </div>
      <div className="scene-planning-actions">
        <button type="button" className="scene-planning-save" onClick={() => void save()} disabled={saving}>
          <Save size={13} /> {saving ? "保存中…" : "保存任务卡"}
        </button>
      </div>
    </div>
  );
}

export function OutlinePage({ project }: OutlinePageProps) {
  const { loadOutline, loadCards, runStructure } = useCreationActions();
  const outlines = useCreationStore((state) => state.outlines);
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const selectScene = useCreationStore((state) => state.selectScene);
  const [view, setView] = useState<"tree" | "board">("tree");

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

  return (
    <section className="outline-page" aria-label="大纲">
      <header className="outline-page-head">
        <div className="creation-project-tabs" role="group" aria-label="大纲视图">
          <button type="button" className={view === "tree" ? "active" : ""} onClick={() => setView("tree")}>
            <ListTree size={13} /> 大纲树
          </button>
          <button type="button" className={view === "board" ? "active" : ""} onClick={() => setView("board")}>
            <LayoutGrid size={13} /> 场景卡板
          </button>
        </div>
        <p className="outline-page-hint">树与卡片板共享同一数据与排序；选中场景可在右侧编辑任务卡。</p>
      </header>
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
              />
            ) : (
              <CardBoard
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={selectScene}
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
            scene={selectedScene ? { id: selectedScene.id, planning: selectedScene.planning } : null}
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
