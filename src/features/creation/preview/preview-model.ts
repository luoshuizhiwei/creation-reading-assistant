import type { ProjectExportBlock, ProjectExportChapter, ProjectExportView } from "@/types/creation";

/**
 * 全书只读预览的纯模型层（阶段 4-B）。
 *
 * 这里只做「导出视图 → 通读页模型」的确定性映射，不读取 DOM、不发 IPC，
 * 因此可以脱离 Electron 与 React 直接单测。所有字数都直接沿用导出视图里
 * 由 `scenes.non_ws_count` 派生的 `wordCount`，不在此处重新统计文本——
 * 否则就会出现第二个字数真源。
 */

/** 通读页渲染角色：把导出块类型收敛为语义标签。 */
export type PreviewBlockRole = "paragraph" | "letter" | "centered" | "note" | "break";

export interface PreviewBlock {
  key: string;
  role: PreviewBlockRole;
  text: string;
}

export interface PreviewScene {
  id: string;
  title: string;
  summary: string | null;
  status: string | null;
  wordCount: number;
  blocks: PreviewBlock[];
  /** 除分场符外没有任何文本时为 true；通读页据此渲染「本场景暂无正文」。 */
  empty: boolean;
}

export interface PreviewChapter {
  id: string;
  /** 章节锚点 DOM id，供「跳转到章节」使用。 */
  anchorId: string;
  volumeTitle: string;
  /** 该卷在本通读页中首次出现时为 true，用于渲染卷分隔标题。 */
  startsVolume: boolean;
  heading: string;
  wordCount: number;
  scenes: PreviewScene[];
}

export interface PreviewModel {
  title: string;
  wordCount: number;
  volumeCount: number;
  chapterCount: number;
  sceneCount: number;
  emptySceneCount: number;
  chapters: PreviewChapter[];
}

export const PREVIEW_CHAPTER_ANCHOR_PREFIX = "preview-chapter-";

export function previewChapterAnchor(chapterId: string): string {
  return `${PREVIEW_CHAPTER_ANCHOR_PREFIX}${chapterId}`;
}

/** 未知块类型按普通段落处理：宁可多渲染一段，也不静默丢掉作者的正文。 */
export function previewBlockRole(kind: string): PreviewBlockRole {
  switch (kind) {
    case "quoteLetter":
      return "letter";
    case "centeredText":
      return "centered";
    case "authorNote":
      return "note";
    case "sceneBreak":
      return "break";
    default:
      return "paragraph";
  }
}

/** 章节标题：沿用成稿导出的同一约定（显示编号 + 标题）；标题全空时给可读占位。 */
export function previewChapterHeading(chapter: Pick<ProjectExportChapter, "displayNumber" | "title">): string {
  const heading = [chapter.displayNumber, chapter.title].filter(Boolean).join(" ").trim();
  return heading || "未命名章节";
}

function isBlank(block: PreviewBlock): boolean {
  return block.role === "break" || block.text.trim() === "";
}

function toBlocks(scene: { blocks?: ProjectExportBlock[]; text?: string }): PreviewBlock[] {
  const source = scene.blocks ?? [];
  if (source.length > 0) {
    return source.map((block, index) => ({
      key: `block-${index}`,
      role: previewBlockRole(block.kind),
      text: block.text ?? ""
    }));
  }
  // 预览读通道固定 includeBlocks，这里的纯文本回退只是兜底：
  // 即便块数据缺失，通读页也不该整章空白。
  return (scene.text ?? "")
    .split(/\n{2,}/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((text, index) => ({ key: `text-${index}`, role: "paragraph" as const, text }));
}

export function buildPreviewModel(view: ProjectExportView): PreviewModel {
  const chapters: PreviewChapter[] = [];
  let sceneCount = 0;
  let emptySceneCount = 0;
  let previousVolumeId: string | null = null;

  for (const volume of view.volumes) {
    for (const chapter of volume.chapters) {
      const scenes = chapter.scenes.map((scene): PreviewScene => {
        const blocks = toBlocks(scene);
        const empty = blocks.every(isBlank);
        sceneCount += 1;
        if (empty) emptySceneCount += 1;
        return {
          id: scene.id,
          title: scene.title,
          summary: scene.summary ?? null,
          status: scene.status ?? null,
          wordCount: scene.wordCount ?? 0,
          blocks,
          empty
        };
      });
      chapters.push({
        id: chapter.id,
        anchorId: previewChapterAnchor(chapter.id),
        volumeTitle: volume.title,
        startsVolume: volume.id !== previousVolumeId,
        heading: previewChapterHeading(chapter),
        wordCount: chapter.wordCount ?? scenes.reduce((sum, scene) => sum + scene.wordCount, 0),
        scenes
      });
      previousVolumeId = volume.id;
    }
  }

  return {
    title: view.title,
    wordCount: view.wordCount ?? chapters.reduce((sum, chapter) => sum + chapter.wordCount, 0),
    volumeCount: view.volumes.length,
    chapterCount: chapters.length,
    sceneCount,
    emptySceneCount,
    chapters
  };
}
