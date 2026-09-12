// @vitest-environment node
import { describe, expect, it } from "vitest";
import type { ProjectExportView } from "@/types/creation";
import {
  buildPreviewModel,
  PREVIEW_CHAPTER_ANCHOR_PREFIX,
  previewBlockRole,
  previewChapterAnchor,
  previewChapterHeading
} from "@/features/creation/preview/preview-model";

function makeView(): ProjectExportView {
  return {
    projectId: "p1",
    title: "测试作品",
    wordCount: 999,
    volumes: [
      {
        id: "v1",
        title: "第一卷",
        wordCount: 30,
        chapters: [
          {
            id: "c1",
            title: "风起",
            displayNumber: "第1章",
            status: "写作中",
            wordCount: 30,
            scenes: [
              {
                id: "s1",
                title: "雨夜",
                summary: "雨夜发现线索。",
                status: "drafting",
                wordCount: 12,
                targetWords: 2000,
                text: "正文行一。\n\n正文行二。",
                blocks: [
                  { kind: "paragraph", text: "正文行一。" },
                  { kind: "quoteLetter", text: "此信为证。" },
                  { kind: "centeredText", text: "居中铭文" },
                  { kind: "authorNote", text: "作者自己的提醒。" },
                  { kind: "sceneBreak", text: "" },
                  { kind: "paragraph", text: "正文行二。" },
                  { kind: "futureKind", text: "未知块内容。" }
                ]
              },
              {
                id: "s2",
                title: "空场景",
                wordCount: 0,
                text: "",
                blocks: [{ kind: "sceneBreak", text: "" }]
              }
            ]
          },
          {
            id: "c2",
            title: "夜行",
            displayNumber: null,
            status: "待写",
            wordCount: 18,
            scenes: [{ id: "s3", title: "夜路", wordCount: 18, text: "只有纯文本。" }]
          }
        ]
      },
      {
        id: "v2",
        title: "第二卷",
        wordCount: 0,
        chapters: [
          {
            id: "c3",
            title: "",
            displayNumber: null,
            wordCount: 0,
            scenes: [{ id: "s4", title: "无题场景", wordCount: 0, blocks: [] }]
          }
        ]
      }
    ]
  };
}

describe("previewBlockRole", () => {
  it("把导出块类型收敛为通读页语义角色", () => {
    expect(previewBlockRole("paragraph")).toBe("paragraph");
    expect(previewBlockRole("quoteLetter")).toBe("letter");
    expect(previewBlockRole("centeredText")).toBe("centered");
    expect(previewBlockRole("authorNote")).toBe("note");
    expect(previewBlockRole("sceneBreak")).toBe("break");
  });

  it("未知块类型按普通段落处理，不静默丢弃作者正文", () => {
    expect(previewBlockRole("futureKind")).toBe("paragraph");
    expect(previewBlockRole("")).toBe("paragraph");
  });
});

describe("previewChapterHeading / previewChapterAnchor", () => {
  it("沿用成稿导出的「显示编号 + 标题」约定，标题全空时给出可读占位", () => {
    expect(previewChapterHeading({ displayNumber: "第1章", title: "风起" })).toBe("第1章 风起");
    expect(previewChapterHeading({ displayNumber: null, title: "夜行" })).toBe("夜行");
    expect(previewChapterHeading({ displayNumber: null, title: "" })).toBe("未命名章节");
    expect(previewChapterHeading({ displayNumber: null, title: "   " })).toBe("未命名章节");
  });

  it("锚点 id 带固定前缀，避免与项目内其它 DOM id 撞名", () => {
    expect(previewChapterAnchor("c1")).toBe(`${PREVIEW_CHAPTER_ANCHOR_PREFIX}c1`);
    expect(PREVIEW_CHAPTER_ANCHOR_PREFIX).toBe("preview-chapter-");
  });
});

describe("buildPreviewModel", () => {
  it("按卷/章/场景顺序展开，卷标题只在每卷首次出现时标记", () => {
    const model = buildPreviewModel(makeView());
    expect(model.chapters.map((chapter) => chapter.id)).toEqual(["c1", "c2", "c3"]);
    expect(model.chapters.map((chapter) => chapter.startsVolume)).toEqual([true, false, true]);
    expect(model.chapters.map((chapter) => chapter.volumeTitle)).toEqual(["第一卷", "第一卷", "第二卷"]);
    expect(model.volumeCount).toBe(2);
    expect(model.chapterCount).toBe(3);
    expect(model.sceneCount).toBe(4);
  });

  it("块角色映射到通读页，未知类型仍保留文本", () => {
    const model = buildPreviewModel(makeView());
    const blocks = model.chapters[0]!.scenes[0]!.blocks;
    expect(blocks.map((block) => block.role)).toEqual([
      "paragraph",
      "letter",
      "centered",
      "note",
      "break",
      "paragraph",
      "paragraph"
    ]);
    expect(blocks[blocks.length - 1]!.text).toBe("未知块内容。");
    expect(blocks.every((block) => block.key.length > 0)).toBe(true);
  });

  it("blocks 缺失时回退到纯文本分段，不整章空白", () => {
    const model = buildPreviewModel(makeView());
    const fallbackScene = model.chapters[1]!.scenes[0]!;
    expect(fallbackScene.empty).toBe(false);
    expect(fallbackScene.blocks).toEqual([{ key: "text-0", role: "paragraph", text: "只有纯文本。" }]);
  });

  it("仅含分场符或空文本的场景标记为空并计入统计", () => {
    const model = buildPreviewModel(makeView());
    expect(model.chapters[0]!.scenes[1]!.empty).toBe(true);
    expect(model.chapters[2]!.scenes[0]!.empty).toBe(true);
    expect(model.emptySceneCount).toBe(2);
  });

  it("字数直接沿用导出视图，不在预览层重新统计文本", () => {
    const model = buildPreviewModel(makeView());
    // 项目字数为 999，与各章文本长度完全无关；预览必须原样透传。
    expect(model.wordCount).toBe(999);
    expect(model.chapters[0]!.wordCount).toBe(30);
    expect(model.chapters[0]!.scenes[0]!.wordCount).toBe(12);
    expect(model.chapters[2]!.scenes[0]!.wordCount).toBe(0);
  });

  it("字数缺失时才由子级同源数据聚合", () => {
    const view = makeView();
    view.wordCount = undefined;
    view.volumes[0]!.wordCount = undefined;
    view.volumes[0]!.chapters[0]!.wordCount = undefined;
    const model = buildPreviewModel(view);
    expect(model.chapters[0]!.wordCount).toBe(12);
    expect(model.wordCount).toBe(30);
  });

  it("保留场景摘要与状态，供通读页展示既有语义", () => {
    const model = buildPreviewModel(makeView());
    expect(model.chapters[0]!.scenes[0]!.summary).toBe("雨夜发现线索。");
    expect(model.chapters[0]!.scenes[0]!.status).toBe("drafting");
    expect(model.chapters[1]!.scenes[0]!.summary).toBeNull();
  });

  it("空项目返回零章节模型而不是抛错", () => {
    const model = buildPreviewModel({ projectId: "p", title: "空作品", volumes: [] });
    expect(model.chapters).toEqual([]);
    expect(model.chapterCount).toBe(0);
    expect(model.emptySceneCount).toBe(0);
    expect(model.wordCount).toBe(0);
  });
});
