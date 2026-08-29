/**
 * 场景候选评审（调研 D-C2 切片 2）：段落级 LCS diff。
 * 输入当前正文与候选正文（均为纯文本，空行分段），输出逐段差异序列。
 * 纯函数，无依赖——评审 UI 只渲染序列。
 */

export type DiffEntryType = "same" | "add" | "remove";

export interface DiffEntry {
  type: DiffEntryType;
  text: string;
}

/** 空行分段；与主进程 plainTextToSceneDocument 的口径一致（无空行按行）。 */
export function splitParagraphs(text: string): string[] {
  const cleaned = text.replace(/\r\n/g, "\n").trim();
  if (!cleaned) return [];
  const blocks = cleaned.split(/\n{2,}/);
  const paragraphs: string[] = [];
  for (const block of blocks) {
    for (const line of block.split("\n")) {
      const trimmed = line.trim();
      if (trimmed) paragraphs.push(trimmed);
    }
  }
  return paragraphs;
}

/** 经典 LCS（段落级）；规模为段落数（远小于字符数），O(n·m) 可接受。 */
export function diffParagraphs(current: string, candidate: string): DiffEntry[] {
  const a = splitParagraphs(current);
  const b = splitParagraphs(candidate);
  const n = a.length;
  const m = b.length;
  // lcs[i][j] = a[i..] 与 b[j..] 的最长公共子序列长度
  const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i -= 1) {
    for (let j = m - 1; j >= 0; j -= 1) {
      lcs[i]![j] = a[i] === b[j] ? lcs[i + 1]![j + 1]! + 1 : Math.max(lcs[i + 1]![j]!, lcs[i]![j + 1]!);
    }
  }
  const entries: DiffEntry[] = [];
  const push = (type: DiffEntryType, text: string): void => {
    const last = entries[entries.length - 1];
    if (last && last.type === type) last.text += "\n" + text;
    else entries.push({ type, text });
  };
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      push("same", a[i]!);
      i += 1;
      j += 1;
    } else if (lcs[i + 1]![j]! >= lcs[i]![j + 1]!) {
      push("remove", a[i]!);
      i += 1;
    } else {
      push("add", b[j]!);
      j += 1;
    }
  }
  while (i < n) {
    push("remove", a[i]!);
    i += 1;
  }
  while (j < m) {
    push("add", b[j]!);
    j += 1;
  }
  return entries;
}

/** diff 统计：新增/删除段落数，供按钮文案与确认提示。 */
export function diffStats(entries: DiffEntry[]): { added: number; removed: number; same: number } {
  const stats = { added: 0, removed: 0, same: 0 };
  for (const entry of entries) {
    const count = entry.text.split("\n").length;
    if (entry.type === "add") stats.added += count;
    else if (entry.type === "remove") stats.removed += count;
    else stats.same += count;
  }
  return stats;
}

/** 场景 doc → 纯文本（段落按换行连接）；候选 diff 与上下文包共用。 */
export function creationDocumentToPlainText(doc: unknown): string {
  if (!doc || typeof doc !== "object") return "";
  const content = (doc as { content?: unknown }).content;
  if (!Array.isArray(content)) return "";
  const lines: string[] = [];
  for (const block of content) {
    if (!block || typeof block !== "object") continue;
    const record = block as { type?: unknown; content?: unknown };
    if (record.type !== "paragraph") continue;
    const runs = Array.isArray(record.content) ? record.content : [];
    const text = runs
      .map((run) => (run && typeof run === "object" && (run as { type?: unknown }).type === "text" ? String((run as { text?: unknown }).text ?? "") : ""))
      .join("");
    if (text.trim()) lines.push(text.trim());
  }
  return lines.join("\n");
}
