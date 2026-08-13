import type {
  ChapterNumberingKind,
  CreationOutlineChapter,
  CreationOutlineScene,
  CreationOutlineVolume,
  CreationProjectOutline
} from "@/types/creation";

export interface ImpactRow {
  label: string;
  value: string;
}

export interface ChapterRef {
  id: string;
  display: string;
}

/** 章节展示名：编号 + 标题，缺省时回退标题。 */
export function chapterDisplay(chapter: CreationOutlineChapter): string {
  return [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
}

/** 在 outline 中定位章节及其所属卷（含 looseChapters）。 */
export function findChapterLocation(
  outline: CreationProjectOutline,
  chapterId: string
): { volume: CreationOutlineVolume; chapter: CreationOutlineChapter } | undefined {
  for (const volume of outline.volumes) {
    const chapter = volume.chapters.find((item) => item.id === chapterId);
    if (chapter) return { volume, chapter };
  }
  const loose = outline.looseChapters.find((item) => item.id === chapterId);
  if (loose) {
    const volume: CreationOutlineVolume = {
      projectId: outline.project.id,
      id: "__loose__",
      title: "未分卷",
      sortOrder: -1,
      createdAt: "",
      updatedAt: "",
      revision: 1,
      chapters: outline.looseChapters
    };
    return { volume, chapter: loose };
  }
  return undefined;
}

/** 在 outline 中定位场景及其所属章节与卷。 */
export function findSceneLocation(
  outline: CreationProjectOutline,
  sceneId: string
): { volume: CreationOutlineVolume; chapter: CreationOutlineChapter; scene: CreationOutlineScene } | undefined {
  for (const volume of outline.volumes) {
    for (const chapter of volume.chapters) {
      const scene = chapter.scenes.find((item) => item.id === sceneId);
      if (scene) return { volume, chapter, scene };
    }
  }
  return undefined;
}

export interface SplitImpact {
  kind: "split";
  valid: boolean;
  invalidReason?: string;
  sourceChapter: CreationOutlineChapter;
  newChapterTitle: string;
  movedScenes: CreationOutlineScene[];
  remainingScenes: CreationOutlineScene[];
  rows: ImpactRow[];
}

/**
 * 拆章影响预览。
 * 约束：拆分点不能是第一个场景——否则原章将变为空章，因此 valid 要求 index > 0。
 */
export function computeSplitImpact(
  outline: CreationProjectOutline,
  chapterId: string,
  splitSceneId: string,
  newChapterTitle: string
): SplitImpact | undefined {
  const found = findChapterLocation(outline, chapterId);
  if (!found) return undefined;
  const { chapter } = found;
  const index = chapter.scenes.findIndex((item) => item.id === splitSceneId);
  const valid = index > 0;
  const movedScenes = valid ? chapter.scenes.slice(index) : [];
  const remainingScenes = valid ? chapter.scenes.slice(0, index) : chapter.scenes;
  const rows: ImpactRow[] = [
    { label: "源章节", value: chapterDisplay(chapter) },
    { label: "拆分后新章", value: newChapterTitle || "（沿用默认标题）" },
    { label: "受影响场景数", value: String(movedScenes.length) },
    { label: "编号变化", value: valid ? "原章保留当前编号；新章自动获得后续编号" : "—" },
    { label: "将被软删除的章节", value: "无" }
  ];
  return {
    kind: "split",
    valid,
    invalidReason: valid
      ? undefined
      : "不能从第一个场景拆分：原章将变为空章。请选择第二个及之后的场景作为拆分点。",
    sourceChapter: chapter,
    newChapterTitle,
    movedScenes,
    remainingScenes,
    rows
  };
}

export interface MergeImpact {
  kind: "merge";
  valid: boolean;
  invalidReason?: string;
  sourceChapter: CreationOutlineChapter;
  targetChapter: CreationOutlineChapter;
  movedScenes: CreationOutlineScene[];
  rows: ImpactRow[];
}

/** 并入上一章影响预览（仅同卷相邻章节）。 */
export function computeMergeImpact(outline: CreationProjectOutline, chapterId: string): MergeImpact | undefined {
  const found = findChapterLocation(outline, chapterId);
  if (!found) return undefined;
  const { volume, chapter } = found;
  const at = volume.chapters.findIndex((item) => item.id === chapterId);
  if (at <= 0) {
    return {
      kind: "merge",
      valid: false,
      invalidReason: "该章是卷内首章，没有上一章可并入。",
      sourceChapter: chapter,
      targetChapter: chapter,
      movedScenes: [],
      rows: [{ label: "源章节", value: chapterDisplay(chapter) }]
    };
  }
  const target = volume.chapters[at - 1];
  const movedScenes = chapter.scenes;
  const rows: ImpactRow[] = [
    { label: "源章节（将被并入）", value: chapterDisplay(chapter) },
    { label: "目标章节", value: chapterDisplay(target) },
    { label: "移动场景数", value: String(movedScenes.length) },
    {
      label: "编号变化",
      value: `源章编号「${chapter.displayNumber ?? chapter.title}」将消失；目标章保持「${
        target.displayNumber ?? target.title
      }」`
    },
    { label: "将被软删除的章节", value: chapterDisplay(chapter) }
  ];
  return { kind: "merge", valid: true, sourceChapter: chapter, targetChapter: target, movedScenes, rows };
}

export interface ChapterMoveImpact {
  kind: "chapterMove";
  sourceChapter: CreationOutlineChapter;
  targetVolume: CreationOutlineVolume;
  rows: ImpactRow[];
}

/** 章节跨卷移动影响预览。 */
export function computeChapterMoveImpact(
  outline: CreationProjectOutline,
  chapterId: string,
  targetVolumeId: string
): ChapterMoveImpact | undefined {
  const found = findChapterLocation(outline, chapterId);
  if (!found) return undefined;
  const { volume, chapter } = found;
  const target = outline.volumes.find((item) => item.id === targetVolumeId);
  if (!target) return undefined;
  const rows: ImpactRow[] = [
    { label: "源章节", value: chapterDisplay(chapter) },
    { label: "源卷", value: volume.title },
    { label: "目标卷", value: target.title },
    { label: "编号变化", value: "移入新卷后，原卷与目标卷的章节编号均自动重排" },
    { label: "将被删除的章节", value: "无" }
  ];
  return { kind: "chapterMove", sourceChapter: chapter, targetVolume: target, rows };
}

export interface SceneMoveImpact {
  kind: "sceneMove";
  sourceScene: CreationOutlineScene;
  sourceChapter: CreationOutlineChapter;
  targetChapter: CreationOutlineChapter;
  rows: ImpactRow[];
}

/** 场景跨章移动影响预览。 */
export function computeSceneMoveImpact(
  outline: CreationProjectOutline,
  sceneId: string,
  targetChapterId: string
): SceneMoveImpact | undefined {
  const found = findSceneLocation(outline, sceneId);
  if (!found) return undefined;
  const { chapter, scene } = found;
  const targetFound = findChapterLocation(outline, targetChapterId);
  if (!targetFound) return undefined;
  const targetChapter = targetFound.chapter;
  const rows: ImpactRow[] = [
    { label: "源场景", value: scene.title },
    { label: "源章节", value: chapterDisplay(chapter) },
    { label: "目标章节", value: chapterDisplay(targetChapter) },
    { label: "受影响场景数", value: "1" },
    { label: "将被删除的章节", value: "无" }
  ];
  return { kind: "sceneMove", sourceScene: scene, sourceChapter: chapter, targetChapter, rows };
}

export interface BatchStatusImpact {
  kind: "batchStatus";
  status: string;
  chapters: ChapterRef[];
  count: number;
  rows: ImpactRow[];
}

/** 批量章节状态影响预览（仅作用于传入的章节）。 */
export function computeBatchStatusImpact(
  outline: CreationProjectOutline,
  chapterIds: string[],
  status: string
): BatchStatusImpact {
  const chapters: ChapterRef[] = chapterIds
    .map((id) => findChapterLocation(outline, id)?.chapter)
    .filter((chapter): chapter is CreationOutlineChapter => Boolean(chapter))
    .map((chapter) => ({ id: chapter.id, display: chapterDisplay(chapter) }));
  const rows: ImpactRow[] = [
    { label: "生效章节数", value: String(chapters.length) },
    { label: "新状态", value: status },
    { label: "受影响章节", value: chapters.map((item) => item.display).join("、") || "（无）" },
    { label: "将被删除的章节", value: "无" }
  ];
  return { kind: "batchStatus", status, chapters, count: chapters.length, rows };
}

export interface NumberingImpact {
  kind: "numbering";
  chapter: CreationOutlineChapter;
  numbering: ChapterNumberingKind;
  customNumber?: string;
  rows: ImpactRow[];
}

const NUMBERING_LABEL: Record<ChapterNumberingKind, string> = {
  auto: "自动编号（第 N 章）",
  prologue: "序章",
  extra: "番外",
  custom: "自定义编号"
};

/** 章节编号模式影响预览。 */
export function computeNumberingImpact(
  outline: CreationProjectOutline,
  chapterId: string,
  numbering: ChapterNumberingKind,
  customNumber?: string
): NumberingImpact | undefined {
  const found = findChapterLocation(outline, chapterId);
  if (!found) return undefined;
  const { chapter } = found;
  const rows: ImpactRow[] = [
    { label: "章节", value: chapterDisplay(chapter) },
    {
      label: "编号模式",
      value: numbering === "custom" && customNumber ? `自定义编号：${customNumber}` : NUMBERING_LABEL[numbering]
    },
    { label: "将被删除的章节", value: "无" }
  ];
  return { kind: "numbering", chapter, numbering, customNumber, rows };
}
