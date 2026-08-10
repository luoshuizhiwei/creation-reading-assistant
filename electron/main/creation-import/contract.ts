import { strict as assert } from "node:assert";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { previewLegacyDraft } from "./index";
import {
  openCreationWorkspace,
  type CreationWorkspace,
  type ProjectImportDraftResult,
  type ProjectImportDraftCommand
} from "../creation-workspace";

const TXT_SAMPLE = `第一章 风起

黄沙镇的风又吹过街角，油灯在案头忽明忽暗。

第二章 夜行

他沿着河岸走了又走，听见水声在暗处起伏。

第三章 归途

天亮之前，他终于看见了镇口的旧牌坊。`;

const MD_SAMPLE = `# 第一卷 风起

## 第一章 初见

雾都的雨夜里，有人敲响了门。

## 第二章 深巷

他跟着灯火走进深巷，听见身后传来脚步声。

# 第二卷 云涌

## 第三章 重逢

多年以后，他们在旧城重逢。`;

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-import-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    await scenario("TXT 章节识别：第X章标题拆分为多章", async () => {
      const filePath = path.join(parent, "样例.txt");
      await writeFile(filePath, TXT_SAMPLE, "utf8");
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.format, "txt");
      assert.equal(preview.projectTitle, "样例");
      assert.equal(preview.volumes.length, 1);
      assert.equal(preview.volumes[0]!.chapters.length, 3);
      assert.equal(preview.volumes[0]!.chapters[0]!.title, "第一章 风起");
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("黄沙镇的风"), true);
      assert.equal(preview.totalChapters, 3);
      assert.equal(preview.totalWords > 0, true);
    });

    await scenario("TXT 无章节标题：整篇单章并给出警告", async () => {
      const filePath = path.join(parent, "随笔.txt");
      await writeFile(filePath, "一段没有标题的随笔。\n\n第二段。", "utf8");
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters.length, 1);
      assert.equal(preview.warnings.length, 1);
    });

    await scenario("Markdown 多卷多章：一级标题为卷、二级为章", async () => {
      const filePath = path.join(parent, "长篇.md");
      await writeFile(filePath, MD_SAMPLE, "utf8");
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.format, "markdown");
      assert.equal(preview.volumes.length, 2);
      assert.equal(preview.volumes[0]!.title, "第一卷 风起");
      assert.equal(preview.volumes[0]!.chapters.length, 2);
      assert.equal(preview.volumes[1]!.chapters.length, 1);
      assert.equal(preview.volumes[1]!.chapters[0]!.title, "第三章 重逢");
      assert.equal(preview.totalChapters, 3);
    });

    await scenario("GBK 编码 TXT 可解码", async () => {
      const iconv = await import("iconv-lite");
      const filePath = path.join(parent, "旧稿.txt");
      const content = "第一章 古早\n\n这是一段 GBK 编码的旧稿正文。";
      await writeFile(filePath, iconv.encode(content, "gbk"));
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("GBK"), true);
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("旧稿正文"), true);
    });

    await scenario("project.importDraft：建项目并写入卷章场景正文", async () => {
      const directory = path.join(parent, "workspace");
      const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
      const filePath = path.join(parent, "长篇.md");
      const preview = await previewLegacyDraft({ filePath });
      const command: ProjectImportDraftCommand = {
        type: "project.importDraft",
        title: preview.projectTitle,
        volumes: preview.volumes
      };
      const result = (await workspace.transact(command)) as ProjectImportDraftResult;
      assert.equal(result.volumeCount, 2);
      assert.equal(result.chapterCount, 3);
      assert.equal(result.sceneCount, 3);
      const outline = await workspace.read({ kind: "project.outline", projectId: result.projectId });
      assert.equal(outline?.volumes.length, 2);
      assert.equal(outline?.volumes[0]?.chapters[0]?.scenes[0]?.title, "正文");
      const sceneId = outline?.volumes[0]?.chapters[0]?.scenes[0]?.id!;
      const body = await workspace.read({ kind: "scene.body", sceneId });
      assert.equal(JSON.stringify(body?.body).includes("雾都的雨夜里"), true);
      const list = (await workspace.read({ kind: "projects.list" })) as Array<{ title: string }>;
      assert.equal(list.some((project) => project.title === "长篇"), true);
      await workspace.close();
    });

    await scenario("project.importDraft：空卷拒绝", async () => {
      const directory = path.join(parent, "workspace2");
      const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
      let error: unknown;
      try {
        await workspace.transact({
          type: "project.importDraft",
          title: "空导入",
          volumes: []
        });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "invalid-input");
      await workspace.close();
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
