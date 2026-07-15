/**
 * 笔记/灵感导出服务
 * 支持 Markdown / HTML 导出和系统分享
 */

import type { MobileInspiration, MobileNote } from "../types/mobile";

export type ExportFormat = "markdown" | "html";

/** 将灵感列表导出为 Markdown 文本 */
export function inspirationsToMarkdown(inspirations: MobileInspiration[]): string {
  if (inspirations.length === 0) return "# 灵感收集箱\n\n暂无灵感记录。\n";
  const lines: string[] = ["# 灵感收集箱", ""];
  for (const item of inspirations) {
    lines.push(`## ${item.title || "未命名灵感"}`);
    lines.push("");
    if (item.tags.length > 0) {
      lines.push(`> 标签：${item.tags.map((t) => `\`${t}\``).join(" ")}`);
      lines.push("");
    }
    if (item.source?.bookTitle) {
      lines.push(`> 来源：《${item.source.bookTitle}》`);
      if (item.source.excerpt) {
        lines.push(`> 摘录：${item.source.excerpt}`);
      }
      lines.push("");
    }
    if (item.body) {
      lines.push(item.body);
      lines.push("");
    }
    const created = new Date(item.createdAt);
    lines.push(`---`);
    lines.push(`*创建于 ${created.toLocaleDateString("zh-CN")} ${created.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}*`);
    lines.push("");
  }
  return lines.join("\n");
}

/** 将灵感列表导出为 HTML 文本 */
export function inspirationsToHTML(inspirations: MobileInspiration[]): string {
  const escape = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const cards = inspirations.length === 0
    ? "<p>暂无灵感记录。</p>"
    : inspirations.map((item) => {
        const tags = item.tags.length > 0
          ? `<div class="tags">${item.tags.map((t) => `<span class="tag">${escape(t)}</span>`).join("")}</div>`
          : "";
        const source = item.source?.bookTitle
          ? `<div class="source">来源：《${escape(item.source.bookTitle)}》${item.source.excerpt ? `<br>摘录：${escape(item.source.excerpt)}` : ""}</div>`
          : "";
        const created = new Date(item.createdAt);
        return `<article class="inspiration">
  <h2>${escape(item.title || "未命名灵感")}</h2>
  ${tags}
  ${source}
  <div class="body">${escape(item.body || "").replace(/\n/g, "<br>")}</div>
  <footer>创建于 ${created.toLocaleDateString("zh-CN")} ${created.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}</footer>
</article>`;
      }).join("\n");
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>灵感收集箱</title>
<style>
  body { font-family: -apple-system, sans-serif; max-width: 720px; margin: 0 auto; padding: 20px; color: #333; line-height: 1.6; }
  h1 { border-bottom: 2px solid #6750a4; padding-bottom: 8px; color: #6750a4; }
  article { margin: 20px 0; padding: 16px; border: 1px solid #e0e0e0; border-radius: 10px; }
  h2 { margin: 0 0 8px; font-size: 18px; }
  .tags { margin: 4px 0; }
  .tag { display: inline-block; background: #f0e6f6; color: #6750a4; padding: 2px 8px; border-radius: 999px; font-size: 12px; margin-right: 4px; }
  .source { background: #f5f5f5; padding: 8px 12px; border-radius: 6px; font-size: 13px; color: #666; margin: 8px 0; }
  .body { margin: 10px 0; white-space: pre-wrap; }
  footer { font-size: 12px; color: #999; margin-top: 8px; }
</style>
</head>
<body>
<h1>灵感收集箱</h1>
${cards}
</body>
</html>`;
}

/** 将笔记列表导出为 Markdown */
export function notesToMarkdown(notes: MobileNote[], bookTitles: Map<string, string>): string {
  if (notes.length === 0) return "# 阅读笔记\n\n暂无笔记记录。\n";
  const lines: string[] = ["# 阅读笔记", ""];
  for (const note of notes) {
    lines.push(`## ${note.title || "阅读笔记"}`);
    lines.push("");
    if (note.bookId && bookTitles.has(note.bookId)) {
      lines.push(`> 来源：《${bookTitles.get(note.bookId)}》`);
      if (note.chapterTitle) lines.push(`> 章节：${note.chapterTitle}`);
      lines.push("");
    }
    if (note.excerpt) {
      lines.push(`> ${note.excerpt}`);
      lines.push("");
    }
    if (note.body) {
      lines.push(note.body);
      lines.push("");
    }
    const created = new Date(note.createdAt);
    lines.push(`---`);
    lines.push(`*创建于 ${created.toLocaleDateString("zh-CN")}*`);
    lines.push("");
  }
  return lines.join("\n");
}

/** 检查系统分享是否可用 */
export function isShareAvailable(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.share === "function";
}

/** 使用系统分享 */
export async function shareText(title: string, text: string): Promise<boolean> {
  if (!isShareAvailable()) return false;
  try {
    await navigator.share({ title, text });
    return true;
  } catch {
    return false;
  }
}

/** 生成导出文件名 */
export function buildExportFilename(prefix: string, format: ExportFormat): string {
  const date = new Date();
  const dateStr = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
  const ext = format === "markdown" ? "md" : "html";
  return `${prefix}_${dateStr}.${ext}`;
}
