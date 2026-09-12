/**
 * 全书只读预览与打印契约测试（阶段 4-B）。
 *
 * 重点验证「只读」与「不绕过边界」这两条硬约束：
 * - 预览读通道 = `project.export` + `includeBlocks: true`，与成稿导出同源同范围，
 *   只多出块级视图，不新增正文读源；
 * - 软删除的章节/场景在两条通道中同时消失，说明预览没有绕过删除过滤；
 * - 预览读取不产生任何写入副作用（快照数量与场景 revision 前后不变）；
 * - 打印方式白名单与 A4 版式参数由主进程校验。
 *
 * 这里只测读取与校验层；`printToPDF` / 系统打印对话框本身由真实打包验收覆盖。
 */

import { strict as assert } from "node:assert";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { openCreationWorkspace, type CreationWorkspace, type ProjectExportView } from "./index";
import { removeWithRetry } from "./test-utils";
import { isProjectPrintMode, PRINT_PDF_OPTIONS, PROJECT_PRINT_MODES } from "../creation-export/print";

interface SceneBodyViewLike {
  revision: number;
}

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-preview-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });
    const report = await workspace.check();
    assert.equal(report.ok, true);

    const readPreview = async (projectId: string): Promise<ProjectExportView | null> =>
      (await workspace!.read({ kind: "project.export", projectId, includeBlocks: true })) as ProjectExportView | null;
    const readExport = async (projectId: string): Promise<ProjectExportView | null> =>
      (await workspace!.read({ kind: "project.export", projectId })) as ProjectExportView | null;

    const created = await workspace.transact({ type: "project.create", title: "预览测试" });
    const projectId = created.projectId;
    const firstSceneId = created.sceneId;
    const firstChapterId = created.chapterId;

    await workspace.transact({
      type: "scene.updateBody",
      sceneId: firstSceneId,
      baseRevision: 1,
      body: {
        type: "doc",
        content: [
          { type: "paragraph", content: [{ type: "text", text: "雨落在旧城墙上。" }] },
          { type: "quoteLetter", content: [{ type: "text", text: "此信为证。" }] },
          { type: "centeredText", content: [{ type: "text", text: "居中铭文" }] },
          { type: "authorNote", content: [{ type: "text", text: "作者的提醒。" }] },
          { type: "sceneBreak" },
          { type: "paragraph", content: [{ type: "text", text: "风穿过长巷。" }] }
        ]
      }
    });

    await scenario("预览读通道返回块级视图，且块类型保真", async () => {
      const view = await readPreview(projectId);
      assert.ok(view, "预览读应返回视图");
      const scene = view.volumes[0]!.chapters[0]!.scenes[0]!;
      assert.ok(Array.isArray(scene.blocks), "预览读必须带 blocks");
      assert.deepEqual(
        scene.blocks!.map((block) => block.kind),
        ["paragraph", "quoteLetter", "centeredText", "authorNote", "sceneBreak", "paragraph"]
      );
      assert.equal(scene.blocks![1]!.text, "此信为证。");
      assert.equal(scene.blocks![4]!.text, "");
      assert.equal(scene.text.includes("雨落在旧城墙上。"), true);
    });

    await scenario("成稿导出读通道不带块级视图，两条通道互不改变对方行为", async () => {
      const view = await readExport(projectId);
      assert.ok(view, "成稿导出读应返回视图");
      assert.equal(view.volumes[0]!.chapters[0]!.scenes[0]!.blocks, undefined);
    });

    await scenario("预览读与成稿导出同源同范围，只多出 blocks", async () => {
      const preview = await readPreview(projectId);
      const exported = await readExport(projectId);
      assert.ok(preview && exported);
      const shape = (view: ProjectExportView): unknown =>
        view.volumes.map((volume) => ({
          id: volume.id,
          title: volume.title,
          wordCount: volume.wordCount,
          chapters: volume.chapters.map((chapter) => ({
            id: chapter.id,
            displayNumber: chapter.displayNumber,
            title: chapter.title,
            wordCount: chapter.wordCount,
            scenes: chapter.scenes.map((scene) => ({
              id: scene.id,
              title: scene.title,
              status: scene.status,
              wordCount: scene.wordCount,
              text: scene.text
            }))
          }))
        }));
      assert.deepEqual(shape(preview), shape(exported));
    });

    await scenario("预览读不产生写入副作用：快照数量与场景 revision 前后不变", async () => {
      const beforeSnapshots = (await workspace!.read({ kind: "snapshot.list", projectId })) as unknown[];
      const beforeRevision = ((await workspace!.read({ kind: "scene.body", sceneId: firstSceneId })) as SceneBodyViewLike | null)?.revision;
      await readPreview(projectId);
      await readPreview(projectId);
      const afterSnapshots = (await workspace!.read({ kind: "snapshot.list", projectId })) as unknown[];
      const afterRevision = ((await workspace!.read({ kind: "scene.body", sceneId: firstSceneId })) as SceneBodyViewLike | null)?.revision;
      assert.equal(afterSnapshots.length, beforeSnapshots.length);
      assert.equal(afterRevision, beforeRevision);
    });

    await scenario("软删除场景后，预览读与成稿导出同时不再包含它", async () => {
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId: firstChapterId,
        title: "待删除场景"
      });
      const extraSceneId = extra.entityId;
      assert.equal((await readPreview(projectId))!.volumes[0]!.chapters[0]!.scenes.length, 2);

      await workspace!.transact({ type: "scene.delete", sceneId: extraSceneId });
      const preview = await readPreview(projectId);
      const exported = await readExport(projectId);
      assert.equal(preview!.volumes[0]!.chapters[0]!.scenes.length, 1);
      assert.equal(exported!.volumes[0]!.chapters[0]!.scenes.length, 1);
      assert.equal(
        preview!.volumes[0]!.chapters[0]!.scenes.some((scene) => scene.id === extraSceneId),
        false
      );
    });

    await scenario("软删除章节后，预览读不再包含该章节", async () => {
      const extraChapter = await workspace!.transact({
        type: "chapter.create",
        projectId,
        title: "待删除章节"
      });
      const extraChapterId = extraChapter.entityId;
      const chapterIds = (view: ProjectExportView | null): string[] =>
        view!.volumes.flatMap((volume) => volume.chapters.map((chapter) => chapter.id));
      assert.equal(chapterIds(await readPreview(projectId)).includes(extraChapterId), true);

      await workspace!.transact({ type: "chapter.delete", chapterId: extraChapterId });
      assert.equal(chapterIds(await readPreview(projectId)).includes(extraChapterId), false);
    });

    await scenario("跨项目隔离：预览读只返回目标项目的章节", async () => {
      const other = await workspace!.transact({ type: "project.create", title: "另一个作品" });
      const preview = await readPreview(projectId);
      assert.ok(preview);
      assert.equal(preview.projectId, projectId);
      assert.equal(preview.title, "预览测试");
      assert.equal(
        preview.volumes.some((volume) => volume.chapters.some((chapter) => chapter.id === other.chapterId)),
        false
      );
    });

    await scenario("不存在的项目返回 null，打印通道据此拒绝", async () => {
      assert.equal(await readPreview("missing-project"), null);
      assert.equal(await readExport("missing-project"), null);
    });
    await scenario("打印方式白名单只接受 print 与 pdf", async () => {
      assert.deepEqual([...PROJECT_PRINT_MODES], ["print", "pdf"]);
      assert.equal(isProjectPrintMode("print"), true);
      assert.equal(isProjectPrintMode("pdf"), true);
      assert.equal(isProjectPrintMode("silent"), false);
      assert.equal(isProjectPrintMode("PDF"), false);
      assert.equal(isProjectPrintMode(""), false);
      assert.equal(isProjectPrintMode(undefined), false);
      assert.equal(isProjectPrintMode(null), false);
      assert.equal(isProjectPrintMode(0), false);
      assert.equal(isProjectPrintMode({ mode: "pdf" }), false);
    });

    await scenario("A4 打印版式参数自检：纵向、边距非负且留出正文区域", async () => {
      assert.equal(PRINT_PDF_OPTIONS.pageSize, "A4");
      assert.equal(PRINT_PDF_OPTIONS.landscape, false);
      assert.equal(PRINT_PDF_OPTIONS.printBackground, false);
      const { top, bottom, left, right } = PRINT_PDF_OPTIONS.margins;
      for (const value of [top, bottom, left, right]) {
        assert.equal(typeof value, "number");
        assert.ok(value >= 0, "边距不得为负");
      }
      // A4 为 8.27in × 11.69in；左右边距相加必须明显小于页宽，否则正文没有可排区域。
      assert.ok(left + right < 4, "左右边距不应挤占整页宽度");
      assert.ok(top + bottom < 6, "上下边距不应挤占整页高度");
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close().catch(() => undefined);
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
