import  { useEffect, useRef, useState } from "react";
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
import { Button } from "@/components/ui";
import type {
  ChapterNumberingKind,
  CreationOutlineChapter,
  CreationOutlineScene,
  CreationOutlineVolume,
  CreationProjectOutline,
  StructureCommand,
  ProtectedStructureCommand,
  StructurePreviewCommand,
  StructurePreviewView,
  StructureApplyWithProtectionCommand,
  StructureApplyResult
} from "@/types/creation";
import {
  computeChapterMoveImpact,
  computeMergeImpact,
  computeSplitImpact,
  findChapterLocation,
  findSceneLocation
} from "./outline-impact";
import { StructureImpactDialog } from "./StructureImpactDialog";
import { reorderTarget } from "./outline-reorder";
import "./outline-reorg.css";

interface OutlineTreeProps {
  outline: CreationProjectOutline;
  workflow: string[];
  selectedSceneId?: string;
  onSelectScene: (sceneId: string) => void;
  runStructure: (command: StructureCommand) => Promise<boolean>;
  previewStructure?: (command: StructurePreviewCommand) => Promise<StructurePreviewView | null>;
  applyStructureWithProtection?: (command: StructureApplyWithProtectionCommand) => Promise<StructureApplyResult | null>;
  onProtectedApplied?: (result: StructureApplyResult) => void;
}

type ConfirmTarget =
  | { kind: "volume"; id: string }
  | { kind: "chapter"; id: string }
  | { kind: "scene"; id: string };

type EditingTarget =
  | { kind: "volume"; id: string; title: string }
  | { kind: "chapter"; id: string; title: string }
  | { kind: "scene"; id: string; title: string };

interface PromptState {
  title: string;
  defaultValue: string;
  submit(title: string): void;
}

type PendingOperation =
  | { type: "split"; chapterId: string; sceneId: string; title: string }
  | { type: "merge"; chapterId: string }
  | { type: "chapterMove"; chapterId: string; targetVolumeId: string }
  | { type: "sceneMove"; sceneId: string; targetChapterId: string }
  | { type: "numbering"; chapterId: string; numbering: ChapterNumberingKind; customNumber?: string; baseRevision: number };

function commandForPending(outline: CreationProjectOutline, pending: PendingOperation): ProtectedStructureCommand | null {
  if (pending.type === "split") {
    return { type: "chapter.split", chapterId: pending.chapterId, splitSceneId: pending.sceneId, newChapterTitle: pending.title };
  }
  if (pending.type === "merge") {
    const impact = computeMergeImpact(outline, pending.chapterId);
    return impact?.valid
      ? { type: "chapter.merge", sourceChapterId: pending.chapterId, targetChapterId: impact.targetChapter.id }
      : null;
  }
  if (pending.type === "sceneMove") {
    return { type: "scene.move", sceneId: pending.sceneId, targetChapterId: pending.targetChapterId };
  }
  if (pending.type === "numbering") {
    return {
      type: "chapter.setNumbering",
      chapterId: pending.chapterId,
      numbering: pending.numbering,
      customNumber: pending.customNumber,
      baseRevision: pending.baseRevision
    };
  }
  return { type: "chapter.move", chapterId: pending.chapterId, targetVolumeId: pending.targetVolumeId };
}

function PromptDialog({ prompt, onCancel }: { prompt: PromptState; onCancel(): void }) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const commit = () => {
    const title = inputRef.current?.value ?? "";
    if (!title.trim()) return;
    prompt.submit(title.trim());
  };

  return (
    <div className="absolute inset-0 z-[80] grid place-items-center bg-paper-ink/18 px-6 backdrop-blur-sm" onClick={onCancel}>
      <section
        className="motion-dialog w-[min(420px,100%)] overflow-hidden rounded-2xl border border-paper-line bg-paper-panel shadow-paper"
        role="dialog"
        aria-modal="true"
        aria-labelledby="outline-prompt-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="h-1 bg-copper" />
        <div className="p-5">
          <h2 id="outline-prompt-title" className="paper-title text-base font-semibold text-paper-ink">{prompt.title}</h2>
          <input
            ref={inputRef}
            className="paper-input mt-3 h-9 w-full"
            defaultValue={prompt.defaultValue}
            onKeyDown={(event) => {
              if (event.key === "Enter") commit();
              if (event.key === "Escape") onCancel();
            }}
          />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={onCancel}>取消</Button>
            <Button onClick={commit}>确定</Button>
          </div>
        </div>
      </section>
    </div>
  );
}

function volumeSiblingIds(outline: CreationProjectOutline): string[] {
  return outline.volumes.map((volume) => volume.id);
}

function chapterSiblingIds(volume: CreationOutlineVolume | undefined): string[] {
  return volume?.chapters.map((chapter) => chapter.id) ?? [];
}

function sceneSiblingIds(chapter: CreationOutlineChapter | undefined): string[] {
  return chapter?.scenes.map((scene) => scene.id) ?? [];
}



export function OutlineTree({
  outline,
  workflow,
  selectedSceneId,
  onSelectScene,
  runStructure,
  previewStructure,
  applyStructureWithProtection,
  onProtectedApplied
}: OutlineTreeProps) {
  const [expandedVolumes, setExpandedVolumes] = useState<Set<string>>(() => new Set(outline.volumes.map((v) => v.id)));
  const [expandedChapters, setExpandedChapters] = useState<Set<string>>(() => new Set(outline.volumes.flatMap((v) => v.chapters.map((c) => c.id))));
  const [confirm, setConfirm] = useState<ConfirmTarget>();
  const [editing, setEditing] = useState<EditingTarget>();
  const [prompt, setPrompt] = useState<PromptState>();
  const [pendingOp, setPendingOp] = useState<PendingOperation | null>(null);
  const [impactBusy, setImpactBusy] = useState(false);
  const [impactPreview, setImpactPreview] = useState<StructurePreviewView | null>(null);
  const [impactError, setImpactError] = useState<string | null>(null);

  useEffect(() => {
    setImpactPreview(null);
    setImpactError(null);
    if (!pendingOp || !previewStructure) return;
    const command = commandForPending(outline, pendingOp);
    if (!command) return;
    let active = true;
    setImpactBusy(true);
    void previewStructure({ type: "structure.preview", projectId: outline.project.id, command })
      .then((preview) => {
        if (!active) return;
        if (preview) setImpactPreview(preview);
        else setImpactError("无法取得权威影响预览，请重试。");
      })
      .catch((error: unknown) => {
        if (active) setImpactError(error instanceof Error ? error.message : "无法取得权威影响预览，请重试。");
      })
      .finally(() => {
        if (active) setImpactBusy(false);
      });
    return () => { active = false; };
  }, [outline, pendingOp, previewStructure]);

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

  const addVolume = () => {
    setPrompt({
      title: "新卷名称",
      defaultValue: "新卷",
      submit: async (title) => {
        await runStructure({ type: "volume.create", projectId: outline.project.id, title });
      }
    });
  };

  const addChapter = (volume: CreationOutlineVolume) => {
    setPrompt({
      title: "新章节名",
      defaultValue: "新章节",
      submit: async (title) => {
        await runStructure({ type: "chapter.create", projectId: outline.project.id, volumeId: volume.id, title });
        setExpandedVolumes((current) => new Set(current).add(volume.id));
      }
    });
  };

  const addScene = (chapter: CreationOutlineChapter) => {
    setPrompt({
      title: "新场景名",
      defaultValue: "新场景",
      submit: async (title) => {
        const volume = outline.volumes.find((item) => item.chapters.some((c) => c.id === chapter.id));
        if (volume) setExpandedVolumes((current) => new Set(current).add(volume.id));
        setExpandedChapters((current) => new Set(current).add(chapter.id));
        await runStructure({ type: "scene.create", chapterId: chapter.id, title });
      }
    });
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
      const revision = outline.volumes.find((item) => item.id === editing.id)?.revision ?? 1;
      await runStructure({ type: "volume.rename", volumeId: editing.id, title, baseRevision: revision });
    } else if (editing.kind === "chapter") {
      const revision = findChapterLocation(outline, editing.id)?.chapter.revision ?? 1;
      await runStructure({ type: "chapter.rename", chapterId: editing.id, title, baseRevision: revision });
    } else {
      const revision = findSceneLocation(outline, editing.id)?.scene.revision ?? 1;
      await runStructure({ type: "scene.rename", sceneId: editing.id, title, baseRevision: revision });
    }
    setEditing(undefined);
  };

  const moveVolume = async (volume: CreationOutlineVolume, direction: "up" | "down") => {
    const target = reorderTarget(volumeSiblingIds(outline), volume.id, direction);
    if (!target.canMove) return;
    await runStructure({ type: "volume.reorder", volumeId: volume.id, beforeVolumeId: target.beforeId });
  };

  const moveChapter = async (volume: CreationOutlineVolume, chapter: CreationOutlineChapter, direction: "up" | "down") => {
    const target = reorderTarget(chapterSiblingIds(volume), chapter.id, direction);
    if (!target.canMove) return;
    await runStructure({ type: "chapter.reorder", chapterId: chapter.id, beforeChapterId: target.beforeId });
  };

  const moveChapterToVolume = (chapter: CreationOutlineChapter, targetVolumeId: string) => {
    setPendingOp({ type: "chapterMove", chapterId: chapter.id, targetVolumeId });
  };

  const moveScene = async (chapter: CreationOutlineChapter, scene: CreationOutlineScene, direction: "up" | "down") => {
    const target = reorderTarget(sceneSiblingIds(chapter), scene.id, direction);
    if (!target.canMove) return;
    await runStructure({ type: "scene.reorder", sceneId: scene.id, beforeSceneId: target.beforeId });
  };

  const moveSceneToChapter = (scene: CreationOutlineScene, targetChapterId: string) => {
    setPendingOp({ type: "sceneMove", sceneId: scene.id, targetChapterId });
  };

  const splitChapterAtScene = (chapter: CreationOutlineChapter, scene: CreationOutlineScene) => {
    setPrompt({
      title: "拆章后的新章节名",
      defaultValue: "新章节",
      submit: (title) => {
        setPrompt(undefined);
        setPendingOp({ type: "split", chapterId: chapter.id, sceneId: scene.id, title });
      }
    });
  };

  const mergeChapterToPrevious = (volume: CreationOutlineVolume, chapter: CreationOutlineChapter) => {
    const at = volume.chapters.findIndex((item) => item.id === chapter.id);
    if (at <= 0) return;
    setPendingOp({ type: "merge", chapterId: chapter.id });
  };

  const setStatus = async (chapter: CreationOutlineChapter, status: string) => {
    await runStructure({ type: "chapter.setStatus", chapterId: chapter.id, status, baseRevision: chapter.revision });
  };

  const setNumbering = (chapter: CreationOutlineChapter, mode: ChapterNumberingKind) => {
    if (mode === "custom") {
      setPrompt({
        title: "自定义章节编号（例如：外传一、尾声）",
        defaultValue: chapter.customNumber ?? "",
        submit: (text) => {
          setPrompt(undefined);
          setPendingOp({
            type: "numbering",
            chapterId: chapter.id,
            numbering: "custom",
            customNumber: text.trim() || undefined,
            baseRevision: chapter.revision
          });
        }
      });
      return;
    }
    setPendingOp({ type: "numbering", chapterId: chapter.id, numbering: mode, baseRevision: chapter.revision });
  };

  const confirmPending = async () => {
    if (!pendingOp) return;
    const command = commandForPending(outline, pendingOp);
    if (!command) return;
    if (previewStructure && (!impactPreview || impactPreview.stale)) return;
    setImpactBusy(true);
    setImpactError(null);
    try {
      if (applyStructureWithProtection) {
        const result = await applyStructureWithProtection({
          type: "structure.applyWithProtection",
          projectId: outline.project.id,
          planId: impactPreview?.planId ?? "",
          protectionReason: `大纲安全重组：${pendingOp.type}`
        });
        if (!result) {
          setImpactError("操作未成功，预览可能已过期，请取消后重试。");
          return;
        }
        onProtectedApplied?.(result);
      } else {
        const ok = await runStructure(command as StructureCommand);
        if (!ok) {
          setImpactError("操作未成功，请重试。");
          return;
        }
      }
      setPendingOp(null);
    } catch (error) {
      setImpactError(error instanceof Error ? error.message : "操作失败，请重试。");
    } finally {
      setImpactBusy(false);
    }
  };

  const volumeButtons = (volume: CreationOutlineVolume) => {
    const up = reorderTarget(volumeSiblingIds(outline), volume.id, "up");
    const down = reorderTarget(volumeSiblingIds(outline), volume.id, "down");
    return (
    <span className="outline-node-actions">
      <button type="button" title="新建章节" onClick={() => void addChapter(volume)}><Plus size={12} /></button>
      <button type="button" title="上移" disabled={!up.canMove} onClick={() => void moveVolume(volume, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" disabled={!down.canMove} onClick={() => void moveVolume(volume, "down")}><ChevronDown size={12} /></button>
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
  };

  const chapterButtons = (volume: CreationOutlineVolume, chapter: CreationOutlineChapter) => {
    const up = reorderTarget(chapterSiblingIds(volume), chapter.id, "up");
    const down = reorderTarget(chapterSiblingIds(volume), chapter.id, "down");
    return (
    <span className="outline-node-actions">
      <button type="button" title="新建场景" onClick={() => void addScene(chapter)}><Plus size={12} /></button>
      <button type="button" title="上移" disabled={!up.canMove} onClick={() => void moveChapter(volume, chapter, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" disabled={!down.canMove} onClick={() => void moveChapter(volume, chapter, "down")}><ChevronDown size={12} /></button>
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
      <select
        className="outline-numbering-select"
        aria-label="章节编号模式"
        value={chapter.numbering}
        onChange={(event) => setNumbering(chapter, event.target.value as ChapterNumberingKind)}
      >
        <option value="auto">自动</option>
        <option value="prologue">序章</option>
        <option value="extra">番外</option>
        <option value="custom">自定义</option>
      </select>
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
  };

  const sceneButtons = (chapter: CreationOutlineChapter, scene: CreationOutlineScene) => {
    const up = reorderTarget(sceneSiblingIds(chapter), scene.id, "up");
    const down = reorderTarget(sceneSiblingIds(chapter), scene.id, "down");
    return (
    <span className="outline-node-actions">
      <button type="button" title="上移" disabled={!up.canMove} onClick={() => void moveScene(chapter, scene, "up")}><ChevronRight size={12} className="rotate-270" /></button>
      <button type="button" title="下移" disabled={!down.canMove} onClick={() => void moveScene(chapter, scene, "down")}><ChevronDown size={12} /></button>
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
  };

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
        <span className="outline-scene-title" title={scene.summary || scene.title}>{scene.title}</span>
        <small className={`outline-scene-status outline-scene-status--${scene.status ?? "planned"}`}>
          {scene.status === "done" ? "完成" : scene.status === "revising" ? "修订" : scene.status === "drafting" ? "起草" : "规划"}
        </small>
        <small className="outline-scene-goal">{scene.planning?.targetWords ? `目标 ${scene.planning.targetWords.toLocaleString("zh-CN")}` : ""}</small>
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
          <small className="outline-chapter-words">{Number(chapter.wordCount ?? chapter.scenes.reduce((sum, scene) => sum + scene.wordCount, 0)).toLocaleString("zh-CN")}字</small>
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
          <small className="outline-volume-count">{volume.chapters.length}章 · {Number(volume.wordCount ?? volume.chapters.reduce((sum, chapter) => sum + chapter.scenes.reduce((sceneSum, scene) => sceneSum + scene.wordCount, 0), 0)).toLocaleString("zh-CN")}字</small>
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

  const renderPendingImpact = () => {
    if (!pendingOp) return null;
    if (pendingOp.type === "sceneMove" || pendingOp.type === "numbering") {
      return (
        <StructureImpactDialog
          title={pendingOp.type === "sceneMove" ? "场景跨章移动影响预览" : "章节编号变更影响预览"}
          description={pendingOp.type === "sceneMove" ? "场景将移动到目标章节。确认后才会写入。" : "章节编号规则将改变。确认后才会写入。"}
          rows={impactPreview?.rows ?? []}
          notice={impactPreview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
          error={impactError}
          confirmDisabled={!impactPreview || impactPreview.stale}
          confirmLabel={pendingOp.type === "sceneMove" ? "确认移动" : "确认修改"}
          busy={impactBusy}
          onCancel={() => setPendingOp(null)}
          onConfirm={() => void confirmPending()}
        />
      );
    }
    if (pendingOp.type === "split") {
      const impact = computeSplitImpact(outline, pendingOp.chapterId, pendingOp.sceneId, pendingOp.title);
      if (!impact) return null;
      return (
        <StructureImpactDialog
          title="拆章影响预览"
          description="按场景边界拆分：原章保留拆分点之前的场景，其余移入新章。确认后才会写入。"
          rows={impactPreview?.rows ?? []}
          notice={impact.invalidReason ?? (impactPreview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined)}
          error={impactError}
          confirmDisabled={!impact.valid || !impactPreview || impactPreview.stale}
          confirmLabel="确认拆章"
          busy={impactBusy}
          onCancel={() => setPendingOp(null)}
          onConfirm={() => void confirmPending()}
        />
      );
    }
    if (pendingOp.type === "merge") {
      const impact = computeMergeImpact(outline, pendingOp.chapterId);
      if (!impact) return null;
      return (
        <StructureImpactDialog
          title="并入上一章影响预览"
          description="源章的场景将并入目标章，源章被软删除。确认后才会写入。"
          rows={impactPreview?.rows ?? []}
          notice={impact.invalidReason ?? (impactPreview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined)}
          error={impactError}
          confirmDisabled={!impact.valid || !impactPreview || impactPreview.stale}
          confirmLabel="确认并入"
          busy={impactBusy}
          onCancel={() => setPendingOp(null)}
          onConfirm={() => void confirmPending()}
        />
      );
    }
    const impact = computeChapterMoveImpact(outline, pendingOp.chapterId, pendingOp.targetVolumeId);
    if (!impact) return null;
    return (
      <StructureImpactDialog
        title="跨卷移动影响预览"
        description="章节将移入目标卷，原卷与目标卷编号自动重排。确认后才会写入。"
        rows={impactPreview?.rows ?? []}
        notice={impactPreview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
        error={impactError}
        confirmDisabled={!impactPreview || impactPreview.stale}
        confirmLabel="确认移动"
        busy={impactBusy}
        onCancel={() => setPendingOp(null)}
        onConfirm={() => void confirmPending()}
      />
    );
  };

  return (
    <div className="outline-tree">
      <div className="outline-toolbar">
        <span className="outline-toolbar-label">大纲</span>
        <Button variant="outline" size="sm" className="shrink-0" onClick={() => void addVolume()}>
          <Plus size={12} /> 新建卷
        </Button>
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
      {prompt && <PromptDialog prompt={prompt} onCancel={() => setPrompt(undefined)} />}
      {pendingOp && renderPendingImpact()}
    </div>
  );
}
