import { useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  FileText,
  GripVertical,
  Merge,
  Pencil,
  Plus,
  Scissors,
  Trash2
} from "lucide-react";
import type {
  CreationOutlineChapter,
  CreationOutlineScene,
  CreationOutlineVolume,
  CreationProjectOutline,
  StructureCommand
} from "@/types/creation";

interface OutlineTreeProps {
  outline: CreationProjectOutline;
  workflow: string[];
  selectedSceneId?: string;
  onSelectScene: (sceneId: string) => void;
  runStructure: (command: StructureCommand) => Promise<boolean>;
}

type ConfirmTarget =
  | { kind: "volume"; id: string }
  | { kind: "chapter"; id: string }
  | { kind: "scene"; id: string };

type EditingTarget =
  | { kind: "volume"; id: string; title: string }
  | { kind: "chapter"; id: string; title: string }
  | { kind: "scene"; id: string; title: string };

function volumeSiblingIds(outline: CreationProjectOutline): string[] {
  return outline.volumes.map((volume) => volume.id);
}

function chapterSiblingIds(volume: CreationOutlineVolume | undefined): string[] {
  return volume?.chapters.map((chapter) => chapter.id) ?? [];
}

function sceneSiblingIds(chapter: CreationOutlineChapter | undefined): string[] {
  return chapter?.scenes.map((scene) => scene.id) ?? [];
}

/**
 * 把「上移/下移」转为 beforeId 语义：上移 → before 前一个；下移 → before 后一个的下一个。
 * 返回 undefined 表示不需要变化（已在边界）。
 */
function shiftTarget(siblingIds: string[], id: string, direction: "up" | "down"): string | undefined {
  const at = siblingIds.indexOf(id);
  if (direction === "up") return at > 0 ? siblingIds[at - 1] : undefined;
  return at >= 0 && at < siblingIds.length - 2 ? siblingIds[at + 2] : undefined;
}

export function OutlineTree({
  outline,
  workflow,
  selectedSceneId,
  onSelectScene,
  runStructure
}: OutlineTreeProps) {
  const [expandedVolumes, setExpandedVolumes] = useState<Set<string>>(() => new Set(outline.volumes.map((v) => v.id)));
  const [expandedChapters, setExpandedChapters] = useState<Set<string>>(() => new Set(outline.volumes.flatMap((v) => v.chapters.map((c) => c.id))));
  const [confirm, setConfirm] = useState<ConfirmTarget>();
  const [editing, setEditing] = useState<EditingTarget>();

  const toggleVolume = (id: string) =>
    setExpandedVolumes((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });

  const toggleChapter = (id: string) =>
    setExpandedChapters((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });

  const addVolume = async () => {
    const title = window.prompt("新卷名称", "新卷");
    if (!title?.trim()) return;
    await runStructure({ type: "volume.create", projectId: outline.project.id, title: title.trim() });
  };

  const addChapter = async (volume: CreationOutlineVolume) => {
    const title = window.prompt("新章节名", "新章节");
    if (!title?.trim()) return;
    await runStructure({ type: "chapter.create", projectId: outline.project.id, volumeId: volume.id, title: title.trim() });
    setExpandedVolumes((current) => new Set(current).add(volume.id));
  };

  const addScene = async (chapter: CreationOutlineChapter) => {
    const title = window.prompt("新场景名", "新场景");
    if (!title?.trim()) return;
    const volume = outline.volumes.find((item) => item.chapters.some((c) => c.id === chapter.id));
    if (volume) setExpandedVolumes((current) => new Set(current).add(volume.id));
    setExpandedChapters((current) => new Set(current).add(chapter.id));
    await runStructure({ type: "scene.create", chapterId: chapter.id, title: title.trim() });
  };

  const confirmDelete = (target: ConfirmTarget) => {
    if (confirm?.kind === target.kind && confirm.id === target.id) {
      void runDelete(target);
      setConfirm(undefined);
    } else {
      setConfirm(target);
    }
  };

  const runDelete = async (target: ConfirmTarget) => {
    if (target.kind === "volume") await runStructure({ type: "volume.delete", volumeId: target.id });
    else if (target.kind === "chapter") await runStructure({ type: "chapter.delete", chapterId: target.id });
    else await runStructure({ type: "scene.delete", sceneId: target.id });
  };

  const startEdit = (target: EditingTarget) => setEditing(target);

  const commitEdit = async () => {
    if (!editing) return;
    const title = editing.title.trim();
    if (!title) {
      setEditing(undefined);
      return;
    }
    if (editing.kind === "volume") {
      await runStructure({ type: "volume.rename", volumeId: editing.id, title, baseRevision: 1 });
    } else if (editing.kind === "chapter") {
      await runStructure({ type: "chapter.rename", chapterId: editing.id, title, baseRevision: 1 });
    } else {
      await runStructure({ type: "scene.rename", sceneId: editing.id, title, baseRevision: 1 });
    }
    setEditing(undefined);
  };

  const moveVolume = async (volume: CreationOutlineVolume, direction: "up" | "down") => {
    const before = shiftTarget(volumeSiblingIds(outline), volume.id, direction);
    if (before === undefined) return;
    await runStructure({ type: "volume.reorder", volumeId: volume.id, beforeVolumeId: before });
  };

  const moveChapter = async (volume: CreationOutlineVolume, chapter: CreationOutlineChapter, direction: "up" | "down") => {
    const before = shiftTarget(chapterSiblingIds(volume), chapter.id, direction);
    if (before === undefined) return;
    await runStructure({ type: "chapter.reorder", chapterId: chapter.id, beforeChapterId: before });
  };

  const moveChapterToVolume = async (chapter: CreationOutlineChapter, targetVolumeId: string) => {
    await runStructure({ type: "chapter.move", chapterId: chapter.id, targetVolumeId });
  };

  const moveScene = async (chapter: CreationOutlineChapter, scene: CreationOutlineScene, direction: "up" | "down") => {
    const before = shiftTarget(sceneSiblingIds(chapter), scene.id, direction);
    if (before === undefined) return;
    await runStructure({ type: "scene.reorder", sceneId: scene.id, beforeSceneId: before });
  };

  const moveSceneToChapter = async (scene: CreationOutlineScene, targetChapterId: string) => {
    await runStructure({ type: "scene.move", sceneId: scene.id, targetChapterId });
  };

  const splitChapterAtScene = async (chapter: CreationOutlineChapter, scene: CreationOutlineScene) => {
    const title = window.prompt("新章节名", "新章节");
    if (!title?.trim()) return;
    await runStructure({
      type: "chapter.split",
      chapterId: chapter.id,
      splitSceneId: scene.id,
      newChapterTitle: title.trim()
    });
  };

  const mergeChapterToPrevious = async (volume: CreationOutlineVolume, chapter: CreationOutlineChapter) => {
    const at = volume.chapters.findIndex((item) => item.id === chapter.id);
    if (at <= 0) return;
    await runStructure({
      type: "chapter.merge",
      sourceChapterId: chapter.id,
      targetChapterId: volume.chapters[at - 1].id
    });
  };

  const setStatus = async (chapter: CreationOutlineChapter, status: string) => {
    await runStructure({ type: "chapter.setStatus", chapterId: chapter.id, status, baseRevision: chapter.revision });
  };

  const volumeButtons = (volume: CreationOutlineVolume) => (
    <span className="outline-node-actions">
      <button type="button" title="新建章节" onClick={() => void addChapter(volume)}><Plus size={12} /></button>
      <button type="button" title="上移" onClick={() => void moveVolume(volume, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" onClick={() => void moveVolume(volume, "down")}><ChevronDown size={12} /></button>
      <button type="button" title="改名" onClick={() => startEdit({ kind: "volume", id: volume.id, title: volume.title })}><Pencil size={12} /></button>
      <button
        type="button"
        title="删除卷（含其章节与场景）"
        className={confirm?.kind === "volume" && confirm.id === volume.id ? "confirming" : ""}
        onClick={() => confirmDelete({ kind: "volume", id: volume.id })}
      >
        <Trash2 size={12} />
      </button>
    </span>
  );

  const chapterButtons = (volume: CreationOutlineVolume, chapter: CreationOutlineChapter) => (
    <span className="outline-node-actions">
      <button type="button" title="新建场景" onClick={() => void addScene(chapter)}><Plus size={12} /></button>
      <button type="button" title="上移" onClick={() => void moveChapter(volume, chapter, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" onClick={() => void moveChapter(volume, chapter, "down")}><ChevronDown size={12} /></button>
      {outline.volumes.length > 1 && (
        <select
          className="outline-move-select"
          aria-label="移动到卷"
          value=""
          onChange={(event) => {
            if (event.target.value) void moveChapterToVolume(chapter, event.target.value);
          }}
        >
          <option value="" disabled>移卷</option>
          {outline.volumes.filter((item) => item.id !== volume.id).map((item) => (
            <option key={item.id} value={item.id}>{item.title}</option>
          ))}
        </select>
      )}
      {volume.chapters.findIndex((item) => item.id === chapter.id) > 0 && (
        <button type="button" title="并入上一章" onClick={() => void mergeChapterToPrevious(volume, chapter)}><Merge size={12} /></button>
      )}
      <button type="button" title="改名" onClick={() => startEdit({ kind: "chapter", id: chapter.id, title: chapter.title })}><Pencil size={12} /></button>
      <button
        type="button"
        title="删除章节（含其场景）"
        className={confirm?.kind === "chapter" && confirm.id === chapter.id ? "confirming" : ""}
        onClick={() => confirmDelete({ kind: "chapter", id: chapter.id })}
      >
        <Trash2 size={12} />
      </button>
    </span>
  );

  const sceneButtons = (chapter: CreationOutlineChapter, scene: CreationOutlineScene) => (
    <span className="outline-node-actions">
      <button type="button" title="上移" onClick={() => void moveScene(chapter, scene, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" onClick={() => void moveScene(chapter, scene, "down")}><ChevronDown size={12} /></button>
      {chapter.scenes.length > 0 && (
        <select
          className="outline-move-select"
          aria-label="移动到章节"
          value=""
          onChange={(event) => {
            if (event.target.value) void moveSceneToChapter(scene, event.target.value);
          }}
        >
          <option value="" disabled>移章</option>
          {outline.volumes.flatMap((v) => v.chapters).filter((item) => item.id !== chapter.id).map((item) => (
            <option key={item.id} value={item.id}>{item.title}</option>
          ))}
        </select>
      )}
      <button type="button" title="从该场景拆为新章" onClick={() => void splitChapterAtScene(chapter, scene)}><Scissors size={12} /></button>
      <button type="button" title="改名" onClick={() => startEdit({ kind: "scene", id: scene.id, title: scene.title })}><Pencil size={12} /></button>
      <button
        type="button"
        title="删除场景"
        className={confirm?.kind === "scene" && confirm.id === scene.id ? "confirming" : ""}
        onClick={() => confirmDelete({ kind: "scene", id: scene.id })}
      >
        <Trash2 size={12} />
      </button>
    </span>
  );

  const renderTitle = (target: EditingTarget) =>
    editing?.kind === target.kind && editing.id === target.id ? (
      <span className="outline-inline-edit">
        <input
          autoFocus
          value={editing.title}
          onChange={(event) => setEditing({ ...editing, title: event.target.value })}
          onKeyDown={(event) => {
            if (event.key === "Enter") void commitEdit();
            if (event.key === "Escape") setEditing(undefined);
          }}
          onBlur={() => void commitEdit()}
        />
      </span>
    ) : null;

  const renderScene = (chapter: CreationOutlineChapter, scene: CreationOutlineScene) => (
    <div
      key={scene.id}
      className={`outline-scene ${scene.id === selectedSceneId ? "active" : ""}`}
    >
      <GripVertical size={12} className="outline-grip" />
      <button type="button" className="outline-scene-main" onClick={() => onSelectScene(scene.id)}>
        <FileText size={12} />
        <span className="outline-scene-title">{scene.title}</span>
        <small className="outline-word-count">{scene.wordCount.toLocaleString("zh-CN")}字</small>
      </button>
      {renderTitle({ kind: "scene", id: scene.id, title: scene.title })}
      {sceneButtons(chapter, scene)}
    </div>
  );

  const renderChapter = (volume: CreationOutlineVolume, chapter: CreationOutlineChapter) => {
    const expanded = expandedChapters.has(chapter.id);
    return (
      <div key={chapter.id} className="outline-chapter">
        <div className="outline-chapter-row">
          <button
            type="button"
            className="outline-chapter-toggle"
            aria-expanded={expanded}
            onClick={() => toggleChapter(chapter.id)}
          >
            {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
          <span className="outline-chapter-number">{chapter.displayNumber ?? ""}</span>
          {renderTitle({ kind: "chapter", id: chapter.id, title: chapter.title }) ?? (
            <span className="outline-chapter-title">{chapter.title}</span>
          )}
          <select
            className="outline-status-select"
            aria-label="章节状态"
            value={chapter.status}
            onChange={(event) => void setStatus(chapter, event.target.value)}
          >
            {workflow.map((step) => <option key={step} value={step}>{step}</option>)}
          </select>
          {chapterButtons(volume, chapter)}
        </div>
        {expanded && (
          <div className="outline-scene-list">
            {chapter.scenes.map((scene) => renderScene(chapter, scene))}
            {chapter.scenes.length === 0 && <span className="outline-empty-hint">暂无场景</span>}
          </div>
        )}
      </div>
    );
  };

  const renderVolume = (volume: CreationOutlineVolume) => {
    const expanded = expandedVolumes.has(volume.id);
    return (
      <div key={volume.id} className="outline-volume">
        <div className="outline-volume-row">
          <button
            type="button"
            className="outline-volume-toggle"
            aria-expanded={expanded}
            onClick={() => toggleVolume(volume.id)}
          >
            {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
          {renderTitle({ kind: "volume", id: volume.id, title: volume.title }) ?? (
            <span className="outline-volume-title">{volume.title}</span>
          )}
          <small className="outline-volume-count">{volume.chapters.length}章</small>
          {volumeButtons(volume)}
        </div>
        {expanded && (
          <div className="outline-chapter-list">
            {volume.chapters.map((chapter) => renderChapter(volume, chapter))}
            {volume.chapters.length === 0 && <span className="outline-empty-hint">暂无章节</span>}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="outline-tree">
      <div className="outline-toolbar">
        <span className="outline-toolbar-label">大纲</span>
        <button type="button" className="outline-add-volume" onClick={() => void addVolume()}>
          <Plus size={12} /> 新建卷
        </button>
      </div>
      <div className="outline-tree-scroll">
        {outline.volumes.map(renderVolume)}
        {outline.looseChapters.length > 0 && (
          <div className="outline-volume">
            <div className="outline-volume-row"><span className="outline-volume-title">未分卷</span></div>
            <div className="outline-chapter-list">
              {outline.looseChapters.map((chapter) => renderChapter(
                { id: "loose", projectId: outline.project.id, title: "未分卷", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1, chapters: outline.looseChapters },
                chapter
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
