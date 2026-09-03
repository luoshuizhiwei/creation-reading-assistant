/**
 * Markdown 目录与标题锚点。
 *
 * 此前实现按行扫描 ATX 标题生成目录，再在渲染后的 HTML 里按出现顺序给 <hN> 注入 id——
 * 两套启发式一旦不一致（Setext 下划线标题、围栏代码块里的 # 注释），id 就整体错位，
 * 目录跳转会落到错误的标题上。
 *
 * 现在改为从 markdown-it 的 token 流提取标题并在渲染前注入 id：目录与渲染结果
 * 共用同一次解析，天然对齐；Setext（= / - 下划线）标题由 block 解析器识别，
 * 围栏代码块内的伪标题天然被排除。slug 重复时追加 -2 / -3 序号保证唯一。
 */
import MarkdownIt from "markdown-it";

export interface MarkdownTocItem {
  id: string;
  title: string;
  level: number;
}

export const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true
});

function slugify(value: string): string {
  const slug = value
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
  return slug;
}

function uniqueSlug(value: string, ordinal: number, used: Set<string>): string {
  const base = slugify(value) || `heading-${ordinal}`;
  let id = base;
  let n = 2;
  while (used.has(id)) {
    id = `${base}-${n}`;
    n += 1;
  }
  used.add(id);
  return id;
}

/** markdown-it 渲染是同步的，用模块级变量在单次 render 内传递目录结果。 */
let currentRunToc: MarkdownTocItem[] = [];

markdown.core.ruler.push("cra_assign_heading_ids", (state) => {
  const used = new Set<string>();
  let ordinal = 0;
  for (let i = 0; i < state.tokens.length; i++) {
    const token = state.tokens[i];
    if (token.type !== "heading_open") continue;
    const inline = state.tokens[i + 1];
    ordinal += 1;
    const cleaned = (inline?.content ?? "").replace(/[*_`~[\]()]/g, "").trim();
    const title = cleaned || `标题 ${ordinal}`;
    const id = uniqueSlug(cleaned, ordinal, used);
    token.attrSet("id", id);
    currentRunToc.push({ id, title: title || `标题 ${ordinal}`, level: Number(token.tag.slice(1)) });
  }
});

/** 渲染 Markdown 并提取目录；返回的 HTML 中每个 <h1>-<h6> 都带有目录对应的唯一 id。 */
export function renderMarkdownWithToc(content: string): { html: string; toc: MarkdownTocItem[] } {
  currentRunToc = [];
  const html = markdown.render(content, {});
  return { html, toc: currentRunToc };
}
