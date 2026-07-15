import {
  escapeHtml,
  inlineMarkdown,
  type MobileReaderDocument
} from "./mobile-reader-types";

export function renderMarkdown(markdown: string): MobileReaderDocument {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const toc: MobileReaderDocument["toc"] = [];
  const html: string[] = [];
  let inCode = false;
  let inTable = false;
  let inList = false;

  const closeList = () => {
    if (inList) {
      html.push("</ul>");
      inList = false;
    }
  };

  lines.forEach((line, index) => {
    if (line.trim().startsWith("```")) {
      closeList();
      inCode = !inCode;
      html.push(inCode ? "<pre><code>" : "</code></pre>");
      return;
    }
    if (inCode) {
      html.push(`${escapeHtml(line)}\n`);
      return;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(line);
    if (heading) {
      closeList();
      const level = heading[1].length;
      const title = heading[2].trim();
      const id = `heading-${index}`;
      toc.push({ id, title, level });
      html.push(`<h${level} id="${id}">${inlineMarkdown(title)}</h${level}>`);
      return;
    }

    if (/^\s*\|.+\|\s*$/.test(line)) {
      closeList();
      const isSeparator = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
      if (!inTable) {
        html.push("<table>");
        inTable = true;
      }
      if (!isSeparator) {
        const cells = line
          .trim()
          .slice(1, -1)
          .split("|")
          .map((cell) => `<td>${inlineMarkdown(cell.trim())}</td>`)
          .join("");
        html.push(`<tr>${cells}</tr>`);
      }
      return;
    }
    if (inTable) {
      html.push("</table>");
      inTable = false;
    }

    const quote = /^>\s?(.+)$/.exec(line);
    if (quote) {
      closeList();
      html.push(`<blockquote>${inlineMarkdown(quote[1])}</blockquote>`);
      return;
    }

    const listItem = /^[-*+]\s+(.+)$/.exec(line);
    if (listItem) {
      if (!inList) {
        html.push("<ul>");
        inList = true;
      }
      html.push(`<li>${inlineMarkdown(listItem[1])}</li>`);
      return;
    }
    closeList();

    if (line.trim()) html.push(`<p>${inlineMarkdown(line.trim())}</p>`);
  });
  closeList();
  if (inTable) html.push("</table>");

  return {
    title: toc[0]?.title ?? "Markdown 书籍",
    format: "md",
    html: html.join("\n"),
    plainText: markdown,
    toc,
    wordCount: markdown.replace(/\s/g, "").length
  };
}
