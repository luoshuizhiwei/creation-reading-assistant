import { strict as assert } from "node:assert";
import type { ProjectExportView } from "../../../src/types/creation";
import {
  buildDraftExport,
  buildOutlineMarkdown,
  buildPlatformPlainText,
  buildStandardReviewMarkdown,
  DRAFT_EXPORT_PRESET_META,
  isDraftExportPreset
} from "./index";

const VIEW: ProjectExportView = {
  projectId: "project-1",
  title: "测试作品",
  wordCount: 18,
  volumes: [
    {
      id: "volume-1",
      title: "第一卷",
      wordCount: 12,
      chapters: [
        {
          id: "chapter-1",
          title: "风起",
          displayNumber: "第1章",
          status: "写作中",
          wordCount: 12,
          scenes: [
            {
              id: "scene-1",
              title: "默认场景",
              summary: "雨夜发现关键线索。",
              status: "drafting",
              wordCount: 12,
              targetWords: 2000,
              text: "正文行一。\n\n正文行二。",
              blocks: [
                { kind: "paragraph", text: "正文行一。" },
                { kind: "paragraph", text: "正文行二。" },
                { kind: "authorNote", text: "这段是作者按，不进入发布稿。" },
                { kind: "sceneBreak", text: "" },
                { kind: "quoteLetter", text: "此信为证。" },
                { kind: "centeredText", text: "居中铭文" }
              ]
            }
          ]
        }
      ]
    },
    {
      id: "volume-2",
      title: "第二卷",
      wordCount: 6,
      chapters: [
        {
          id: "chapter-2",
          title: "夜行",
          displayNumber: null,
          status: "待写",
          wordCount: 6,
          scenes: [
            {
              id: "scene-2",
              title: "山道",
              status: "planned",
              wordCount: 6,
              text: "山道正文。",
              blocks: [{ kind: "paragraph", text: "山道正文。" }]
            }
          ]
        }
      ]
    }
  ]
};

async function run(): Promise<void> {
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => void): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    await scenario("平台发布净文本：卷章标题+正文，无场景标题/作者按/内部标记", async () => {
      const result = buildPlatformPlainText(VIEW);
      assert.equal(result.preset, "platform-plain");
      assert.equal(result.extension, "txt");
      assert.equal(result.text.includes("第1章 风起"), true);
      assert.equal(result.text.includes("正文行一。"), true);
      assert.equal(result.text.includes("正文行二。"), true);
      assert.equal(result.text.includes("第二卷"), true);
      assert.equal(result.text.includes("山道正文。"), true);
      assert.equal(result.text.includes("作者按"), false);
      assert.equal(result.text.includes("这段是作者按"), false);
      assert.equal(result.text.includes("默认场景"), false);
      assert.equal(result.text.includes("> "), false);
      assert.equal(result.text.includes("<center>"), false);
      assert.equal(result.text.includes("card-"), false);
      assert.equal(result.text.includes("annotation-"), false);
    });

    await scenario("标准审阅稿：层级清晰，作者按标注，引文/居中保留语义", async () => {
      const result = buildStandardReviewMarkdown(VIEW);
      assert.equal(result.preset, "standard-review");
      assert.equal(result.extension, "md");
      assert.equal(result.text.includes("# 测试作品"), true);
      assert.equal(result.text.includes("## 第一卷"), true);
      assert.equal(result.text.includes("### 第1章 风起"), true);
      assert.equal(result.text.includes("#### 山道"), true);
      assert.equal(result.text.includes("> 作者按：这段是作者按，不进入发布稿。"), true);
      assert.equal(result.text.includes("> 此信为证。"), true);
      assert.equal(result.text.includes("<center>居中铭文</center>"), true);
      assert.equal(result.text.includes("* * *"), true);
      assert.equal(result.text.includes("#### 默认场景"), false);
      assert.equal(result.text.includes("card-"), false);
      assert.equal(result.text.includes("annotation-"), false);
      assert.equal(result.text.includes("planning"), false);
    });

    await scenario("预设构建：buildDraftExport 按预设分发", async () => {
      const plain = buildDraftExport(VIEW, "platform-plain");
      assert.equal(plain?.extension, "txt");
      const review = buildDraftExport(VIEW, "standard-review");
      assert.equal(review?.extension, "md");
      const outline = buildDraftExport(VIEW, "outline-markdown");
      assert.equal(outline?.extension, "md");
      assert.equal(buildDraftExport(VIEW, "unknown-preset"), null);
      assert.equal(buildDraftExport(VIEW, undefined), null);
    });

    await scenario("预设白名单与说明元数据", async () => {
      assert.equal(isDraftExportPreset("platform-plain"), true);
      assert.equal(isDraftExportPreset("standard-review"), true);
      assert.equal(isDraftExportPreset("outline-markdown"), true);
      assert.equal(isDraftExportPreset("docx"), false);
      assert.equal(DRAFT_EXPORT_PRESET_META["platform-plain"].extension, "txt");
      assert.equal(DRAFT_EXPORT_PRESET_META["standard-review"].extension, "md");
      assert.equal(DRAFT_EXPORT_PRESET_META["outline-markdown"].extension, "md");
      assert.equal(typeof DRAFT_EXPORT_PRESET_META["platform-plain"].description, "string");
    });

    await scenario("Markdown 大纲：包含汇总、双层状态、摘要与目标但不包含正文", async () => {
      const result = buildOutlineMarkdown(VIEW);
      assert.equal(result.text.includes("全书 18 字"), true);
      assert.equal(result.text.includes("第一卷（12 字）"), true);
      assert.equal(result.text.includes("章节状态：写作中"), true);
      assert.equal(result.text.includes("起草中"), true);
      assert.equal(result.text.includes("雨夜发现关键线索。"), true);
      assert.equal(result.text.includes("目标 2,000 字"), true);
      assert.equal(result.text.includes("正文行一。"), false);
    });

    await scenario("场景无 blocks 时回退 scene.text", async () => {
      const fallbackView: ProjectExportView = {
        projectId: "project-2",
        title: "回退作品",
        volumes: [
          {
            id: "volume-1",
            title: "正文",
            chapters: [
              {
                id: "chapter-1",
                title: "第一章",
                displayNumber: "第1章",
                scenes: [{ id: "scene-1", title: "正文", text: "纯文本场景。\n\n第二段。" }]
              }
            ]
          }
        ]
      };
      const plain = buildPlatformPlainText(fallbackView);
      assert.equal(plain.text.includes("纯文本场景。"), true);
      assert.equal(plain.text.includes("第二段。"), true);
      const review = buildStandardReviewMarkdown(fallbackView);
      assert.equal(review.text.includes("纯文本场景。"), true);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}

run();
