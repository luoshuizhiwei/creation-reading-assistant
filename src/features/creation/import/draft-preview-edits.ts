import type { DraftImportPreview, DraftImportVolumeInput } from "@/types/creation";

/** 可编辑的预览章（body 与 wordCount 保持只读展示用）。 */
export interface EditablePreviewChapter {
  key: string;
  title: string;
  body: string;
  wordCount: number;
  included: boolean;
}

export interface EditablePreviewVolume {
  key: string;
  title: string;
  chapters: EditablePreviewChapter[];
}

export interface EditableDraftPreview {
  projectTitle: string;
  volumes: EditablePreviewVolume[];
}

function chapterKey(volumeIndex: number, chapterIndex: number): string {
  return `v${volumeIndex}c${chapterIndex}`;
}

/** 把导入预览转成可编辑状态（默认全部包含）。 */
export function createEditablePreview(preview: DraftImportPreview): EditableDraftPreview {
  return {
    projectTitle: preview.projectTitle,
    volumes: preview.volumes.map((volume, volumeIndex) => ({
      key: `v${volumeIndex}`,
      title: volume.title,
      chapters: volume.chapters.map((chapter, chapterIndex) => ({
        key: chapterKey(volumeIndex, chapterIndex),
        title: chapter.title,
        body: chapter.body,
        wordCount: chapter.wordCount,
        included: true
      }))
    }))
  };
}

/** 重命名卷。 */
export function renameVolume(state: EditableDraftPreview, volumeKey: string, title: string): EditableDraftPreview {
  return {
    ...state,
    volumes: state.volumes.map((volume) => (volume.key === volumeKey ? { ...volume, title } : volume))
  };
}

/** 重命名章。 */
export function renameChapter(
  state: EditableDraftPreview,
  volumeKey: string,
  chapterKey: string,
  title: string
): EditableDraftPreview {
  return {
    ...state,
    volumes: state.volumes.map((volume) =>
      volume.key === volumeKey
        ? {
            ...volume,
            chapters: volume.chapters.map((chapter) => (chapter.key === chapterKey ? { ...chapter, title } : chapter))
          }
        : volume
    )
  };
}

export interface ToggleChapterResult {
  state: EditableDraftPreview;
  /** 本次操作是否生效（排除最后一个章节会被拒绝）。 */
  applied: boolean;
}

/** 排除/恢复章节；至少保留一章，排除最后一章时不生效。 */
export function toggleChapter(
  state: EditableDraftPreview,
  volumeKey: string,
  chapterKey: string
): ToggleChapterResult {
  const includedCount = state.volumes.reduce(
    (sum, volume) => sum + volume.chapters.filter((chapter) => chapter.included).length,
    0
  );
  const target = state.volumes.flatMap((volume) => volume.chapters).find((chapter) => chapter.key === chapterKey);
  if (!target || !target.included) {
    return {
      state: {
        ...state,
        volumes: state.volumes.map((volume) =>
          volume.key === volumeKey
            ? {
                ...volume,
                chapters: volume.chapters.map((chapter) => (chapter.key === chapterKey ? { ...chapter, included: true } : chapter))
              }
            : volume
        )
      },
      applied: true
    };
  }
  if (includedCount <= 1) return { state, applied: false };
  return {
    state: {
      ...state,
      volumes: state.volumes.map((volume) =>
        volume.key === volumeKey
          ? {
              ...volume,
              chapters: volume.chapters.map((chapter) => (chapter.key === chapterKey ? { ...chapter, included: false } : chapter))
            }
          : volume
      )
    },
    applied: true
  };
}

/** 生成 project.importDraft 输入；空卷自动折叠（若某卷全部被排除则不输出该卷）。 */
export function toImportInput(state: EditableDraftPreview): { title: string; volumes: DraftImportVolumeInput[] } {
  return {
    title: state.projectTitle.trim(),
    volumes: state.volumes
      .map((volume) => ({
        title: volume.title.trim() || "未命名卷",
        chapters: volume.chapters
          .filter((chapter) => chapter.included)
          .map((chapter) => ({
            title: chapter.title.trim() || "未命名章节",
            body: chapter.body
          }))
      }))
      .filter((volume) => volume.chapters.length > 0)
  };
}

/** 可编辑状态是否有效（至少一章、项目名非空）。 */
export function isEditablePreviewValid(state: EditableDraftPreview): boolean {
  if (!state.projectTitle.trim()) return false;
  return state.volumes.some((volume) => volume.chapters.some((chapter) => chapter.included));
}
