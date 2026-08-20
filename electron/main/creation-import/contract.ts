import { strict as assert } from "node:assert";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import JSZip from "jszip";
import { deflateRawSync } from "node:zlib";
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

function docxParagraph(text: string, style?: string): string {
  const styleXml = style ? `<w:pPr><w:pStyle w:val="${style}"/></w:pPr>` : "";
  return `<w:p>${styleXml}<w:r><w:t xml:space="preserve">${text}</w:t></w:r></w:p>`;
}

/** 用 jszip 构造最小合法 .docx（无压缩依赖，mammoth 可直接解析）。 */
async function writeDocx(filePath: string, bodyXml: string): Promise<void> {
  const zip = new JSZip();
  zip.file(
    "[Content_Types].xml",
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`
  );
  zip.file(
    "_rels/.rels",
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`
  );
  zip.file(
    "word/document.xml",
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${bodyXml}</w:body></w:document>`
  );
  const buffer = await zip.generateAsync({ type: "nodebuffer" });
  await writeFile(filePath, buffer);
}

/** 中央目录谎报 1 byte，但 deflate 实际可解压为 81MB；证明预检不只信 metadata。 */
async function writeForgedZipBombDocx(filePath: string): Promise<void> {
  const name = Buffer.from("word/document.xml", "utf8");
  const compressed = deflateRawSync(Buffer.alloc(81 * 1024 * 1024, 0x41), { level: 9 });
  const local = Buffer.alloc(30);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(20, 4);
  local.writeUInt16LE(8, 8);
  local.writeUInt32LE(compressed.length, 18);
  local.writeUInt32LE(1, 22);
  local.writeUInt16LE(name.length, 26);
  const centralOffset = local.length + name.length + compressed.length;
  const central = Buffer.alloc(46);
  central.writeUInt32LE(0x02014b50, 0);
  central.writeUInt16LE(20, 4);
  central.writeUInt16LE(20, 6);
  central.writeUInt16LE(8, 10);
  central.writeUInt32LE(compressed.length, 20);
  central.writeUInt32LE(1, 24);
  central.writeUInt16LE(name.length, 28);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(1, 8);
  eocd.writeUInt16LE(1, 10);
  eocd.writeUInt32LE(central.length + name.length, 12);
  eocd.writeUInt32LE(centralOffset, 16);
  await writeFile(filePath, Buffer.concat([local, name, compressed, central, name, eocd]));
}

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

    await scenario("DOCX H1+H2：一级为卷、二级为章", async () => {
      const filePath = path.join(parent, "旧稿.docx");
      await writeDocx(
        filePath,
        [
          docxParagraph("第一卷 风起", "Heading1"),
          docxParagraph("第一章 初见", "Heading2"),
          docxParagraph("雾都的雨夜里，有人敲响了门。"),
          docxParagraph("第二章 深巷", "Heading2"),
          docxParagraph("他跟着灯火走进深巷。"),
          docxParagraph("第二卷 云涌", "Heading1"),
          docxParagraph("第三章 重逢", "Heading2"),
          docxParagraph("多年以后，他们在旧城重逢。")
        ].join("")
      );
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.format, "docx");
      assert.equal(preview.projectTitle, "旧稿");
      assert.equal(preview.volumes.length, 2);
      assert.equal(preview.volumes[0]!.title, "第一卷 风起");
      assert.equal(preview.volumes[0]!.chapters.length, 2);
      assert.equal(preview.volumes[0]!.chapters[0]!.title, "第一章 初见");
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("雾都的雨夜里"), true);
      assert.equal(preview.volumes[1]!.chapters.length, 1);
      assert.equal(preview.volumes[1]!.chapters[0]!.title, "第三章 重逢");
      assert.equal(preview.totalChapters, 3);
      assert.equal(preview.warnings.some((warning) => warning.includes("一级标题作为卷")), true);
    });

    await scenario("DOCX 只有 H1 或只有 H2：全部作为章并归入「正文」卷", async () => {
      const h1Path = path.join(parent, "单级H1.docx");
      await writeDocx(
        h1Path,
        [
          docxParagraph("第一章 晨光", "Heading1"),
          docxParagraph("山间起了薄雾。"),
          docxParagraph("第二章 暮色", "Heading1"),
          docxParagraph("晚钟响过三遍。")
        ].join("")
      );
      const h1Preview = await previewLegacyDraft({ filePath: h1Path });
      assert.equal(h1Preview.volumes.length, 1);
      assert.equal(h1Preview.volumes[0]!.title, "正文");
      assert.equal(h1Preview.volumes[0]!.chapters.length, 2);
      assert.equal(h1Preview.volumes[0]!.chapters[0]!.body.includes("薄雾"), true);
      assert.equal(h1Preview.warnings.some((warning) => warning.includes("单级标题")), true);

      const h2Path = path.join(parent, "单级H2.docx");
      await writeDocx(
        h2Path,
        [docxParagraph("第一章 灯火", "Heading2"), docxParagraph("巷口亮起一盏灯。")].join("")
      );
      const h2Preview = await previewLegacyDraft({ filePath: h2Path });
      assert.equal(h2Preview.volumes.length, 1);
      assert.equal(h2Preview.volumes[0]!.title, "正文");
      assert.equal(h2Preview.volumes[0]!.chapters.length, 1);
      assert.equal(h2Preview.volumes[0]!.chapters[0]!.title, "第一章 灯火");
    });

    await scenario("DOCX 无标题样式：回退「第X章」识别", async () => {
      const filePath = path.join(parent, "无标题.docx");
      await writeDocx(
        filePath,
        [docxParagraph("第一章 风起"), docxParagraph("黄沙镇的风又吹过街角。"), docxParagraph("第二章 夜行"), docxParagraph("他沿着河岸走。")].join("")
      );
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes.length, 1);
      assert.equal(preview.volumes[0]!.chapters.length, 2);
      assert.equal(preview.volumes[0]!.chapters[0]!.title, "第一章 风起");
      assert.equal(preview.warnings.some((warning) => warning.includes("第X章")), true);
    });

    await scenario("DOCX 无结构：单章并警告", async () => {
      const filePath = path.join(parent, "随笔.docx");
      await writeDocx(filePath, docxParagraph("一段没有标题的随笔。"));
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters.length, 1);
      assert.equal(preview.volumes[0]!.chapters[0]!.title, "全文");
      assert.equal(preview.warnings.some((warning) => warning.includes("单章")), true);
    });

    await scenario("DOCX 表格内容被跳过并给出清洗警告", async () => {
      const filePath = path.join(parent, "含表格.docx");
      const table = `<w:tbl><w:tr><w:tc><w:p><w:r><w:t>表格机密数据</w:t></w:r></w:p></w:tc></w:tr></w:tbl>`;
      await writeDocx(filePath, `${table}${docxParagraph("正文段落保留。")}`);
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("表格机密数据"), false);
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("正文段落保留"), true);
      assert.equal(preview.warnings.some((warning) => warning.includes("表格")), true);
    });

    await scenario("DOCX 硬换行保留为段内换行", async () => {
      const filePath = path.join(parent, "硬换行.docx");
      const paragraph = `<w:p><w:r><w:t>第一行</w:t><w:br/><w:t>第二行</w:t></w:r></w:p>`;
      await writeDocx(filePath, paragraph);
      const preview = await previewLegacyDraft({ filePath });
      assert.equal(preview.volumes[0]!.chapters[0]!.body.includes("第一行\n第二行"), true);
    });

    await scenario("DOCX 中央目录谎报大小的压缩炸弹在 mammoth 前被拒绝", async () => {
      const filePath = path.join(parent, "压缩炸弹.docx");
      await writeForgedZipBombDocx(filePath);
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("解压后数据量过大"), true);
    });

    await scenario("DOCX 损坏文件给出中文可读错误", async () => {
      const filePath = path.join(parent, "损坏.docx");
      await writeFile(filePath, Buffer.from("这不是一个有效的 zip 文档", "utf8"));
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("文件损坏"), true);
    });

    await scenario("DOCX 输入过大被拒绝", async () => {
      const filePath = path.join(parent, "超大.docx");
      await writeFile(filePath, Buffer.alloc(21 * 1024 * 1024));
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("过大"), true);
    });

    await scenario("旧版 .doc 明确拒绝，不伪装支持", async () => {
      const filePath = path.join(parent, "旧稿.doc");
      await writeFile(filePath, Buffer.from("old binary", "utf8"));
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("仅支持 .docx"), true);
    });

    await scenario("未支持扩展名明确拒绝，不按 TXT 误读", async () => {
      const filePath = path.join(parent, "旧稿.pdf");
      await writeFile(filePath, Buffer.from("not a text draft", "utf8"));
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("不支持该文件格式"), true);
    });

    await scenario("DOCX 空文档：拒绝导入并给出可读错误", async () => {
      const filePath = path.join(parent, "空文档.docx");
      await writeDocx(filePath, "");
      let error: unknown;
      try {
        await previewLegacyDraft({ filePath });
      } catch (caught) {
        error = caught;
      }
      assert.equal(error instanceof Error, true);
      assert.equal((error as Error).message.includes("没有可导入的正文文本"), true);
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
