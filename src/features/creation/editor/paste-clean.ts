import type { CreationDocument } from "@/types/creation";

export type ScenePasteVerdict = "direct" | "preview";

export interface ScenePasteInspection {
  verdict: ScenePasteVerdict;
  /** verdict 为 preview 时的原因说明；direct 时为 null。 */
  reason: string | null;
  /** 从粘贴内容推导出的纯文本回退，始终安全可插入。 */
  cleanedText: string;
  /** 是否包含表格/图片/代码块/嵌入等不支持的内容。 */
  hasForbiddenContent: boolean;
  /** 是否超过大小阈值。 */
  oversized: boolean;
}

export interface ScenePasteSource {
  html?: string;
  text?: string;
}

export const SCENE_PASTE_HTML_CHAR_LIMIT = 20_000;
export const SCENE_PASTE_TEXT_CHAR_LIMIT = 10_000;

const FORBIDDEN_TAGS = [
  "table",
  "img",
  "picture",
  "pre",
  "code",
  "iframe",
  "video",
  "audio",
  "object",
  "embed"
];

const FORBIDDEN_TAG_PATTERN = new RegExp(`<\\s*/?\\s*(?:${FORBIDDEN_TAGS.join("|")})\\b`, "i");

export function inspectScenePaste(source: ScenePasteSource): ScenePasteInspection {
  const html = source.html ?? "";
  const text = source.text ?? "";
  const hasForbiddenContent = FORBIDDEN_TAG_PATTERN.test(html);
  const oversized =
    html.length > SCENE_PASTE_HTML_CHAR_LIMIT || text.length > SCENE_PASTE_TEXT_CHAR_LIMIT;
  const cleanedText = html ? stripHtmlToPlainText(html) : text;
  const reasons: string[] = [];
  if (hasForbiddenContent) {
    reasons.push("包含表格、图片、代码块或嵌入内容等不支持的格式");
  }
  if (oversized) {
    reasons.push("内容超过粘贴阈值，建议先转为纯文本");
  }
  return {
    verdict: hasForbiddenContent || oversized ? "preview" : "direct",
    reason: reasons.length > 0 ? reasons.join("；") : null,
    cleanedText,
    hasForbiddenContent,
    oversized
  };
}

function stripHtmlToPlainText(html: string): string {
  let out = html
    .replace(/<\s*br\s*\/?\s*>/gi, "\n")
    .replace(/<\/(p|div|li|blockquote|h[1-6]|tr)>/gi, "\n")
    .replace(/<[^>]*>/g, "")
    .replace(/&nbsp;/gi, " ")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#0*39;/gi, "'")
    .replace(/&#x27;/gi, "'")
    .replace(/&#0*34;/gi, '"')
    .replace(/&amp;/gi, "&")
    .replace(/\n{3,}/g, "\n\n");
  return out.trim();
}

export function plainTextToCreationDocument(text: string): CreationDocument {
  const normalized = text.replace(/\r\n?/g, "\n");
  const paragraphs = normalized
    .split(/\n\s*\n/)
    .map((block) => block.replace(/\s*\n\s*/g, " ").trim())
    .filter((block) => block.length > 0);
  if (paragraphs.length === 0) {
    return { type: "doc", content: [{ type: "paragraph" }] };
  }
  return {
    type: "doc",
    content: paragraphs.map((paragraph) => ({
      type: "paragraph",
      content: [{ type: "text", text: paragraph }]
    }))
  };
}
