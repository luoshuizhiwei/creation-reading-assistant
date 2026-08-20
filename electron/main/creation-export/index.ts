import type {
  DraftExportBuildResult,
  DraftExportPreset,
  ProjectExportBlock,
  ProjectExportView
} from "../../../src/types/creation";

export type { DraftExportBuildResult, DraftExportPreset } from "../../../src/types/creation";

/** 成稿导出预设白名单（主进程重校验用）。 */
export const DRAFT_EXPORT_PRESETS: readonly DraftExportPreset[] = ["platform-plain", "standard-review"];

export function isDraftExportPreset(value: unknown): value is DraftExportPreset {
  return typeof value === "string" && (DRAFT_EXPORT_PRESETS as readonly string[]).includes(value);
}

export interface DraftExportPresetMeta {
  preset: DraftExportPreset;
  extension: "txt" | "md";
  label: string;
  description: string;
}

/** 预设说明（renderer 选择框与主进程共用）。 */
export const DRAFT_EXPORT_PRESET_META: Record<DraftExportPreset, DraftExportPresetMeta> = {
  "platform-plain": {
    preset: "platform-plain",
    extension: "txt",
    label: "平台发布净文本",
    description: "仅卷/章标题与正文纯文本，不含场景标题、作者按、规划、引用与批注，适合直接发布。"
  },
  "standard-review": {
    preset: "standard-review",
    extension: "md",
    label: "标准审阅稿",
    description: "Markdown 层级清晰（项目/卷/章/场景），作者按标注为「作者按」，引文与居中文本保留可读语义，不含引用与批注元数据。"
  }
};

/** 场景块 → 文本行（marks 不进入导出；sceneBreak 以空行占位；authorNote 剔除）。 */
function blocksToLines(blocks: ProjectExportBlock[]): string[] {
  const lines: string[] = [];
  for (const block of blocks) {
    if (block.kind === "sceneBreak") {
      lines.push("");
      continue;
    }
    if (block.kind === "authorNote") continue;
    if (block.text) lines.push(block.text);
  }
  return lines;
}

/** 场景块 → 审阅稿 Markdown 行（作者按标注、引文/居中保留语义）。 */
function blocksToMarkdownLines(blocks: ProjectExportBlock[]): string[] {
  const lines: string[] = [];
  for (const block of blocks) {
    const text = block.text.trim();
    if (block.kind === "sceneBreak") {
      lines.push("", "* * *", "");
      continue;
    }
    if (!text) continue;
    if (block.kind === "authorNote") {
      lines.push(`> 作者按：${text}`, "");
      continue;
    }
    if (block.kind === "quoteLetter") {
      lines.push(`> ${text}`, "");
      continue;
    }
    if (block.kind === "centeredText") {
      lines.push(`**居中：** ${text}`, "");
      continue;
    }
    lines.push(text);
  }
  return lines;
}

/** 场景正文文本（blocks 缺失时回退到 scene.text，不区分块类型）。 */
function sceneLines(scene: { blocks?: ProjectExportBlock[]; text: string }, preset: DraftExportPreset): string[] {
  if (scene.blocks && scene.blocks.length > 0) {
    return preset === "standard-review" ? blocksToMarkdownLines(scene.blocks) : blocksToLines(scene.blocks);
  }
  const text = scene.text ?? "";
  return text ? [text] : [];
}

/** 章节标题行：显示编号 + 标题（如「第1章 风起」）。 */
function chapterHeading(chapter: { displayNumber: string | null; title: string }): string {
  return [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
}

/**
 * 平台发布净文本（.txt）：卷/章标题 + 正文纯文本。
 * 不含内部场景标题、authorNote、planning、引用与批注；场景分隔以空行保留。
 */
export function buildPlatformPlainText(view: ProjectExportView): DraftExportBuildResult {
  const lines: string[] = [];
  for (const volume of view.volumes) {
    if (view.volumes.length > 1) {
      lines.push(volume.title, "");
    }
    for (const chapter of volume.chapters) {
      lines.push(chapterHeading(chapter), "");
      for (const scene of chapter.scenes) {
        lines.push(...sceneLines(scene, "platform-plain"));
        lines.push("");
      }
    }
  }
  const text = `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trim()}\n`;
  return { preset: "platform-plain", extension: "txt", text };
}

/**
 * 标准审阅稿（.md）：项目/卷/章/非默认场景层级清晰；
 * authorNote 标注为「作者按」，quote/centered 保留可读 Markdown 语义；
 * 不含 annotations 与卡片引用元数据。
 */
export function buildStandardReviewMarkdown(view: ProjectExportView): DraftExportBuildResult {
  const lines: string[] = [`# ${view.title}`, ""];
  for (const volume of view.volumes) {
    lines.push(`## ${volume.title}`, "");
    for (const chapter of volume.chapters) {
      lines.push(`### ${chapterHeading(chapter)}`, "");
      for (const scene of chapter.scenes) {
        const isDefaultScene = scene.title === "默认场景" || scene.title === "正文";
        if (scene.title && !isDefaultScene) {
          lines.push(`#### ${scene.title}`, "");
        }
        lines.push(...sceneLines(scene, "standard-review"));
        lines.push("");
      }
    }
  }
  const text = `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trim()}\n`;
  return { preset: "standard-review", extension: "md", text };
}

/** 按预设构建成稿导出文本；preset 非法时返回 null（由调用方以 invalid-input 拒绝）。 */
export function buildDraftExport(view: ProjectExportView, preset: unknown): DraftExportBuildResult | null {
  if (!isDraftExportPreset(preset)) return null;
  if (preset === "standard-review") return buildStandardReviewMarkdown(view);
  return buildPlatformPlainText(view);
}
