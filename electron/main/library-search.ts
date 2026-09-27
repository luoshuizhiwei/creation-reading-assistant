/**
 * 全局搜索（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：跨馆藏正文 / EPUB 全文索引 / 灵感库的关键词检索、打分与摘要裁剪。
 * 行为逐字保持，仅把顶层声明导出供主进程入口与 IPC 层使用。
 */
import type { SearchQuery, SearchResult, SearchResultType } from "../../src/types/search";
import { inspirationsPath, makeId } from "./storage";
import { readEpubSearchIndex, readLibraryIndex } from "./library-store";
import { readInspirations } from "./inspiration-store";
import { readTextFileIfWithinLimit } from "./reader-io";

export function scoreFor(keyword: string, title: string, metaText: string, content: string): number {
  const needle = keyword.toLowerCase();
  let score = 0;
  if (title.toLowerCase().includes(needle)) score += 20;
  if (metaText.toLowerCase().includes(needle)) score += 8;
  if (content.toLowerCase().includes(needle)) score += 4;
  return score;
}

export function snippetFor(keyword: string, text: string, fallback: string): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if (!clean) return fallback;
  const index = clean.toLowerCase().indexOf(keyword.toLowerCase());
  if (index < 0) return clean.slice(0, 140);
  const start = Math.max(0, index - 55);
  const end = Math.min(clean.length, index + keyword.length + 85);
  return `${start > 0 ? "..." : ""}${clean.slice(start, end)}${end < clean.length ? "..." : ""}`;
}

export async function searchGlobal(query: SearchQuery): Promise<SearchResult[]> {
  const keyword = typeof query.keyword === "string" ? query.keyword.trim() : "";
  if (!keyword) return [];
  const scopes = Array.isArray(query.scopes) && query.scopes.length > 0 ? query.scopes : ["library", "inspiration"];
  const limit = typeof query.limit === "number" ? Math.min(100, Math.max(1, query.limit)) : 50;
  const results: SearchResult[] = [];
  const pushResult = (type: SearchResultType, title: string, metaText: string, content: string, sourcePath: string | undefined, target: SearchResult["target"], charOffset?: number) => {
    const score = scoreFor(keyword, title, metaText, content);
    if (score <= 0) return;
    results.push({
      id: makeId("result"),
      type,
      title,
      snippet: snippetFor(keyword, `${metaText}\n${content}`, title),
      score,
      sourcePath,
      target: charOffset === undefined ? target : { ...target, charOffset }
    });
  };
  const pushScoredResult = (
    type: SearchResultType,
    displayTitle: string,
    scoreTitle: string,
    metaText: string,
    content: string,
    sourcePath: string | undefined,
    target: SearchResult["target"]
  ) => {
    const score = scoreFor(keyword, scoreTitle, metaText, content);
    if (score <= 0) return;
    results.push({
      id: makeId("result"),
      type,
      title: displayTitle,
      snippet: snippetFor(keyword, `${metaText}\n${content}`, displayTitle),
      score,
      sourcePath,
      target
    });
  };

  if (scopes.includes("library")) {
    for (const book of await readLibraryIndex()) {
      if (book.format === "epub") {
        const metaText = `${book.format}\n${book.author ?? ""}\n${book.description ?? ""}\n${book.originalPath ?? ""}\n${book.filePath}`;
        const index = await readEpubSearchIndex(book.id);
        const resultCountBeforeBook = results.length;
        if (index) {
          for (const item of index.items) {
            const itemMetaText = item.href;
            pushScoredResult("book", `${book.title} · ${item.title}`, item.title, itemMetaText, item.text, book.originalPath ?? book.filePath, {
              bookId: book.id,
              epubHref: item.href
            });
          }
        } else {
          // EPUB has no full-text search index — add a hint result
          pushResult("book", `${book.title} · 该 EPUB 尚未建立全文索引`, metaText, "", book.originalPath ?? book.filePath, { bookId: book.id });
        }
        if (results.length === resultCountBeforeBook) {
          pushResult("book", book.title, metaText, "", book.originalPath ?? book.filePath, { bookId: book.id });
        }
        continue;
      }
      const canReadBody = book.format === "txt" || book.format === "md";
      let content = "";
      if (canReadBody) {
        content = await readTextFileIfWithinLimit(book.filePath);
      }
      // 正文首个命中位置：供阅读器打开后直接跳到命中处（与 EPUB 的 epubHref 对等）
      let charOffset: number | undefined;
      if (content) {
        const idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx >= 0) charOffset = idx;
      }
      pushResult("book", book.title, `${book.format}\n${book.originalPath ?? ""}\n${book.filePath}`, content, book.originalPath ?? book.filePath, { bookId: book.id }, charOffset);
    }
  }

  if (scopes.includes("inspiration")) {
    for (const item of await readInspirations()) {
      pushResult(
        "inspiration",
        item.title,
        `${item.type}\n${item.status}\n${item.tags.join(" ")}\n${item.platformTags.join(" ")}\n${item.source?.bookTitle ?? ""}\n${item.source?.bookAuthor ?? ""}\n${item.source?.locationLabel ?? ""}`,
        `${item.body}\n${item.source?.excerpt ?? ""}\n${item.variants.map((variant) => variant.content).join("\n")}`,
        inspirationsPath(),
        { inspirationId: item.id }
      );
    }
  }

  return results.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, limit);
}
