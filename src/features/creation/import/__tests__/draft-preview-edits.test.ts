import { describe, expect, it } from "vitest";
import type { DraftImportPreview } from "@/types/creation";
import {
  createEditablePreview,
  isEditablePreviewValid,
  renameChapter,
  renameVolume,
  toImportInput,
  toggleChapter
} from "../draft-preview-edits";

const SAMPLE: DraftImportPreview = {
  format: "docx",
  fileName: "旧稿.docx",
  projectTitle: "旧稿",
  totalChapters: 2,
  totalWords: 10,
  warnings: [],
  volumes: [
    {
      title: "第一卷",
      chapters: [
        { title: "第一章", body: "正文一", wordCount: 3 },
        { title: "第二章", body: "正文二", wordCount: 4 }
      ]
    },
    { title: "第二卷", chapters: [{ title: "第三章", body: "正文三", wordCount: 3 }] }
  ]
};

describe("draft-preview-edits", () => {
  it("初始状态全部包含，可重命名卷与章", () => {
    let state = createEditablePreview(SAMPLE);
    state = renameVolume(state, "v0", "第一卷（改）");
    state = renameChapter(state, "v0", "v0c0", "第一章（改）");
    expect(state.volumes[0]!.title).toBe("第一卷（改）");
    expect(state.volumes[0]!.chapters[0]!.title).toBe("第一章（改）");
  });

  it("排除章节后 toImportInput 不再包含该章", () => {
    let state = createEditablePreview(SAMPLE);
    const first = toggleChapter(state, "v0", "v0c0");
    expect(first.applied).toBe(true);
    state = first.state;
    const input = toImportInput(state);
    expect(input.volumes[0]!.chapters.map((chapter) => chapter.title)).toEqual(["第二章"]);
  });

  it("排除整卷后该卷不再输出", () => {
    let state = createEditablePreview(SAMPLE);
    const result = toggleChapter(state, "v1", "v1c0");
    expect(result.applied).toBe(true);
    const input = toImportInput(result.state);
    expect(input.volumes.map((volume) => volume.title)).toEqual(["第一卷"]);
  });

  it("至少保留一章：排除最后一章不生效", () => {
    let state = createEditablePreview(SAMPLE);
    state = toggleChapter(state, "v1", "v1c0").state;
    const result = toggleChapter(state, "v0", "v0c0");
    expect(result.applied).toBe(true);
    const last = toggleChapter(result.state, "v0", "v0c1");
    expect(last.applied).toBe(false);
    expect(toImportInput(last.state).volumes[0]!.chapters.length).toBe(1);
  });

  it("至少保留一章：全排除时最后一章拒绝，有效性判定保持", () => {
    let state = createEditablePreview(SAMPLE);
    state = toggleChapter(state, "v0", "v0c0").state;
    state = toggleChapter(state, "v0", "v0c1").state;
    const last = toggleChapter(state, "v1", "v1c0");
    expect(last.applied).toBe(false);
    expect(toImportInput(last.state).volumes.length).toBe(1);
    expect(isEditablePreviewValid(last.state)).toBe(true);
    expect(isEditablePreviewValid(createEditablePreview(SAMPLE))).toBe(true);
  });
});
