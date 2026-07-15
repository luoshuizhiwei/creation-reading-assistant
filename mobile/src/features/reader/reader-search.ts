import type { MobileReaderDocument } from "../../reader/mobile-reader";

export type ReaderSearchResult = {
  id: string;
  occurrenceIndex: number;
  snippet: string;
  progressPercent: number;
};

export function createReaderSearchResults(document: MobileReaderDocument, query: string): ReaderSearchResult[] {
  const keyword = query.trim();
  if (!keyword) return [];
  const text = document.plainText.replace(/\s+/g, " ");
  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const results: ReaderSearchResult[] = [];
  let fromIndex = 0;
  let occurrenceIndex = 0;
  while (results.length < 80) {
    const hitIndex = lowerText.indexOf(lowerKeyword, fromIndex);
    if (hitIndex < 0) break;
    const start = Math.max(0, hitIndex - 28);
    const end = Math.min(text.length, hitIndex + keyword.length + 42);
    results.push({
      id: `reader-search-${hitIndex}-${occurrenceIndex}`,
      occurrenceIndex,
      snippet: `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`,
      progressPercent: text.length ? (hitIndex / text.length) * 100 : 0
    });
    occurrenceIndex += 1;
    fromIndex = hitIndex + lowerKeyword.length;
  }
  return results;
}

export function jumpToReaderSearchResult(root: HTMLElement, query: string, occurrenceIndex: number): boolean {
  const keyword = query.trim();
  if (!keyword) return false;
  const lowerKeyword = keyword.toLowerCase();
  const walker = window.document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let currentNode = walker.nextNode();
  let seen = 0;
  while (currentNode) {
    const node = currentNode as Text;
    const text = node.nodeValue ?? "";
    const lowerText = text.toLowerCase();
    let fromIndex = 0;
    while (fromIndex < lowerText.length) {
      const hitIndex = lowerText.indexOf(lowerKeyword, fromIndex);
      if (hitIndex < 0) break;
      if (seen === occurrenceIndex) {
        const range = window.document.createRange();
        range.setStart(node, hitIndex);
        range.setEnd(node, Math.min(text.length, hitIndex + keyword.length));
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        node.parentElement?.scrollIntoView({ behavior: "smooth", block: "center" });
        return true;
      }
      seen += 1;
      fromIndex = hitIndex + lowerKeyword.length;
    }
    currentNode = walker.nextNode();
  }
  return false;
}
