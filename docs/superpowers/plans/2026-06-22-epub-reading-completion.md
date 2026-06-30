# EPUB Reading Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade EPUB support from basic import/open to metadata-rich library cards, TOC navigation, searchable EPUB body snippets, and a minimal reading-stats UI verification gate.

**Architecture:** Keep privileged file and EPUB parsing in the Electron main process. Add a focused EPUB helper module for parsing metadata, cover, TOC, and sidecar search index data; keep renderer changes limited to typed data display and navigation intent. Preserve current TXT/Markdown paths and existing CFI/session tracking.

**Tech Stack:** Electron main/preload IPC, React 18, Zustand, TypeScript strict mode, epubjs, jszip, @xmldom/xmldom, Node fs/path APIs.

---

## Repository Note

`D:\develop\Code\Codex\创作阅读助手` is not a Git repository. Where this plan says “checkpoint,” record changed files and run verification commands instead of `git commit`.

## File Structure

- Create `electron/main/epub-metadata.ts`: parse EPUB zip packages, OPF metadata, cover asset, TOC items, and plain-text search index items.
- Modify `electron/main/index.ts`: add cover/index directories, call EPUB parser during import, return TOC and pending target href when opening EPUB, search EPUB sidecar files.
- Modify `src/types/library.ts`: add EPUB metadata, TOC, search-index types and extend `ReaderEpubPayload`.
- Modify `src/types/search.ts`: add `epubHref` to `SearchTarget`.
- Modify `src/types/api.ts`: reflect updated payload types through existing API.
- Modify `src/stores/library-store.ts`: store pending EPUB href for renderer navigation.
- Modify `src/hooks/useSearchActions.ts`: pass search result href to active EPUB state.
- Modify `src/features/library/LibraryPage.tsx`: display cover and author.
- Modify `src/features/library/EpubReaderPage.tsx`: render TOC and jump to pending href.
- Create `scripts/verify-stats-ui.mjs`: static verification for reading stats UI fields.
- Modify `package.json`: add `verify:stats-ui`.
- Modify `BETA_CHECKLIST.md`: document new EPUB and stats verification.

## Task 1: Extend shared types for EPUB metadata and navigation

**Files:**
- Modify: `src/types/library.ts`
- Modify: `src/types/search.ts`
- Modify: `src/stores/library-store.ts`

- [ ] **Step 1: Add EPUB types to `src/types/library.ts`**

Add these exports after `BookFormat`:

```ts
export interface EpubTocItem {
  id: ID;
  label: string;
  href: string;
  level: number;
}

export interface EpubBookMetadata {
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverPath?: string;
  toc?: EpubTocItem[];
  searchIndexedAt?: ISODateString;
  searchIndexPath?: string;
}

export interface EpubSearchIndexItem {
  id: ID;
  title: string;
  href: string;
  text: string;
}

export interface EpubSearchIndex {
  version: 1;
  bookId: ID;
  updatedAt: ISODateString;
  items: EpubSearchIndexItem[];
}
```

- [ ] **Step 2: Extend `LibraryBook` in `src/types/library.ts`**

Change the interface to include optional metadata:

```ts
export interface LibraryBook {
  id: ID;
  title: string;
  filePath: string;
  originalPath?: string;
  format: BookFormat;
  importedAt: ISODateString;
  updatedAt: ISODateString;
  size: number;
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverPath?: string;
  epub?: EpubBookMetadata;
}
```

- [ ] **Step 3: Extend `ReaderEpubPayload` in `src/types/library.ts`**

Change it to:

```ts
export interface ReaderEpubPayload {
  book: LibraryBook;
  epubUrl: string;
  progress?: ReadingProgress;
  settings: ReaderSettings;
  toc: EpubTocItem[];
  targetHref?: string;
}
```

- [ ] **Step 4: Extend `SearchTarget` in `src/types/search.ts`**

Change it to:

```ts
export interface SearchTarget {
  projectRoot?: string;
  chapterId?: ID;
  cardKind?: CardKind;
  cardId?: ID;
  bookId?: ID;
  epubHref?: string;
}
```

- [ ] **Step 5: Extend library store active state**

In `src/stores/library-store.ts`, update `LibraryState`:

```ts
activeEpubTargetHref?: string;
setActiveBook: (book?: LibraryBook, content?: string, epubUrl?: string, epubTargetHref?: string) => void;
```

Update implementation:

```ts
setActiveBook: (activeBook, activeContent = "", activeEpubUrl, activeEpubTargetHref) =>
  set({ activeBook, activeContent, activeEpubUrl, activeEpubTargetHref, activeSession: undefined }),
```

- [ ] **Step 6: Verify type build fails or passes with expected follow-up errors**

Run:

```bash
npm run build
```

Expected: build may fail because producers of `ReaderEpubPayload` do not yet return `toc`; this is acceptable before Task 3.

- [ ] **Step 7: Checkpoint**

Record changed files:

```text
src/types/library.ts
src/types/search.ts
src/stores/library-store.ts
```

## Task 2: Add EPUB parser helper in main process

**Files:**
- Create: `electron/main/epub-metadata.ts`

- [ ] **Step 1: Create parser module with concrete interfaces**

Create `electron/main/epub-metadata.ts`:

```ts
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import JSZip from "jszip";
import { DOMParser } from "@xmldom/xmldom";
import type { EpubSearchIndexItem, EpubTocItem } from "../../src/types/library";

export interface ParsedEpubMetadata {
  title?: string;
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverPath?: string;
  toc: EpubTocItem[];
  searchItems: EpubSearchIndexItem[];
}

interface OpfInfo {
  opfPath: string;
  opfDir: string;
  opfXml: Document;
}

function textContent(doc: Document, tagName: string): string | undefined {
  const node = doc.getElementsByTagName(tagName)[0];
  const value = node?.textContent?.replace(/\s+/g, " ").trim();
  return value || undefined;
}

function attr(node: Element | null | undefined, name: string): string | undefined {
  const value = node?.getAttribute(name)?.trim();
  return value || undefined;
}

function normalizeZipPath(value: string): string {
  return value.replace(/\\/g, "/").replace(/^\/+/, "");
}

function resolveZipPath(baseDir: string, href: string): string {
  return normalizeZipPath(path.posix.normalize(path.posix.join(baseDir, href.split("#")[0] ?? href)));
}

function parseXml(text: string): Document {
  return new DOMParser().parseFromString(text, "application/xml");
}

async function readZipText(zip: JSZip, zipPath: string): Promise<string | undefined> {
  const file = zip.file(normalizeZipPath(zipPath));
  return file ? file.async("text") : undefined;
}

async function loadOpf(zip: JSZip): Promise<OpfInfo | undefined> {
  const containerText = await readZipText(zip, "META-INF/container.xml");
  if (!containerText) return undefined;
  const container = parseXml(containerText);
  const rootfile = Array.from(container.getElementsByTagName("rootfile"))[0];
  const opfPath = attr(rootfile, "full-path");
  if (!opfPath) return undefined;
  const opfText = await readZipText(zip, opfPath);
  if (!opfText) return undefined;
  return {
    opfPath,
    opfDir: path.posix.dirname(opfPath) === "." ? "" : path.posix.dirname(opfPath),
    opfXml: parseXml(opfText)
  };
}

function manifestItems(opfXml: Document): Element[] {
  return Array.from(opfXml.getElementsByTagName("item"));
}

function findCoverHref(opfXml: Document): string | undefined {
  const items = manifestItems(opfXml);
  const epub3Cover = items.find((item) => (attr(item, "properties") ?? "").split(/\s+/).includes("cover-image"));
  if (epub3Cover) return attr(epub3Cover, "href");
  const metaCover = Array.from(opfXml.getElementsByTagName("meta")).find((meta) => attr(meta, "name") === "cover");
  const coverId = attr(metaCover, "content");
  if (!coverId) return undefined;
  return attr(items.find((item) => attr(item, "id") === coverId), "href");
}

async function extractCover(zip: JSZip, opf: OpfInfo, bookId: string, coversRoot: string): Promise<string | undefined> {
  const coverHref = findCoverHref(opf.opfXml);
  if (!coverHref) return undefined;
  const coverZipPath = resolveZipPath(opf.opfDir, coverHref);
  const cover = zip.file(coverZipPath);
  if (!cover) return undefined;
  const ext = path.extname(coverZipPath).toLowerCase() || ".img";
  const targetPath = path.join(coversRoot, `${bookId}${ext}`);
  await mkdir(coversRoot, { recursive: true });
  await writeFile(targetPath, Buffer.from(await cover.async("uint8array")));
  return targetPath;
}

function stripHtml(value: string): string {
  return value
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/\s+/g, " ")
    .trim();
}

function navLabel(node: Element): string {
  const label = node.getElementsByTagName("text")[0]?.textContent ?? node.textContent ?? "";
  return label.replace(/\s+/g, " ").trim();
}

function parseNcxToc(ncxXml: Document): EpubTocItem[] {
  return Array.from(ncxXml.getElementsByTagName("navPoint"))
    .map((point, index) => {
      const content = point.getElementsByTagName("content")[0];
      const href = attr(content, "src") ?? "";
      const label = navLabel(point);
      return href && label ? { id: `toc-${index}`, label, href, level: 1 } : undefined;
    })
    .filter((item): item is EpubTocItem => Boolean(item));
}

async function parseToc(zip: JSZip, opf: OpfInfo): Promise<EpubTocItem[]> {
  const items = manifestItems(opf.opfXml);
  const navItem = items.find((item) => (attr(item, "properties") ?? "").split(/\s+/).includes("nav"));
  if (navItem) {
    const navPath = resolveZipPath(opf.opfDir, attr(navItem, "href") ?? "");
    const navText = await readZipText(zip, navPath);
    if (navText) {
      const links = Array.from(parseXml(navText).getElementsByTagName("a"));
      return links
        .map((link, index) => {
          const href = attr(link, "href") ?? "";
          const label = link.textContent?.replace(/\s+/g, " ").trim() ?? "";
          return href && label ? { id: `toc-${index}`, label, href, level: 1 } : undefined;
        })
        .filter((item): item is EpubTocItem => Boolean(item));
    }
  }
  const spine = opf.opfXml.getElementsByTagName("spine")[0];
  const tocId = attr(spine, "toc");
  const ncxHref = tocId ? attr(items.find((item) => attr(item, "id") === tocId), "href") : undefined;
  if (!ncxHref) return [];
  const ncxText = await readZipText(zip, resolveZipPath(opf.opfDir, ncxHref));
  return ncxText ? parseNcxToc(parseXml(ncxText)) : [];
}

async function buildSearchItems(zip: JSZip, opf: OpfInfo, toc: EpubTocItem[]): Promise<EpubSearchIndexItem[]> {
  const items = manifestItems(opf.opfXml).filter((item) => {
    const mediaType = attr(item, "media-type") ?? "";
    return mediaType === "application/xhtml+xml" || mediaType === "text/html";
  });
  const tocByHref = new Map(toc.map((item) => [item.href.split("#")[0], item.label]));
  const result: EpubSearchIndexItem[] = [];
  for (const item of items) {
    const href = attr(item, "href");
    if (!href) continue;
    const zipPath = resolveZipPath(opf.opfDir, href);
    const html = await readZipText(zip, zipPath);
    if (!html) continue;
    const text = stripHtml(html);
    if (!text) continue;
    result.push({
      id: `epub-section-${result.length}`,
      title: tocByHref.get(href) ?? path.posix.basename(href),
      href,
      text: text.slice(0, 20_000)
    });
  }
  return result;
}

export async function parseEpubFile(epubPath: string, bookId: string, coversRoot: string): Promise<ParsedEpubMetadata> {
  const buffer = await readFile(epubPath);
  const zip = await JSZip.loadAsync(buffer);
  const opf = await loadOpf(zip);
  if (!opf) return { toc: [], searchItems: [] };
  const toc = await parseToc(zip, opf);
  const coverPath = await extractCover(zip, opf, bookId, coversRoot).catch(() => undefined);
  return {
    title: textContent(opf.opfXml, "dc:title") ?? textContent(opf.opfXml, "title"),
    author: textContent(opf.opfXml, "dc:creator") ?? textContent(opf.opfXml, "creator"),
    description: textContent(opf.opfXml, "dc:description") ?? textContent(opf.opfXml, "description"),
    language: textContent(opf.opfXml, "dc:language") ?? textContent(opf.opfXml, "language"),
    publisher: textContent(opf.opfXml, "dc:publisher") ?? textContent(opf.opfXml, "publisher"),
    coverPath,
    toc,
    searchItems: await buildSearchItems(zip, opf, toc)
  };
}
```

- [ ] **Step 2: Run build to verify dependency imports**

Run:

```bash
npm run build
```

Expected: compile errors may remain from unintegrated Task 1/3, but there should be no “Cannot find module 'jszip'” or “Cannot find module '@xmldom/xmldom'” errors.

- [ ] **Step 3: Checkpoint**

Record changed file:

```text
electron/main/epub-metadata.ts
```

## Task 3: Integrate EPUB metadata, cover, TOC, and sidecar indexing in Electron main

**Files:**
- Modify: `electron/main/index.ts`

- [ ] **Step 1: Import parser and index types**

At the top of `electron/main/index.ts`, add:

```ts
import { parseEpubFile } from "./epub-metadata";
```

Extend the library type import list:

```ts
EpubSearchIndex,
EpubSearchIndexItem,
```

- [ ] **Step 2: Add storage roots**

After `appLibraryFilesRoot()` add:

```ts
function appLibraryCoversRoot(): string {
  return path.join(appLibraryRoot(), "covers");
}

function appLibrarySearchIndexRoot(): string {
  return path.join(appLibraryRoot(), "search-index");
}

function epubSearchIndexPath(bookId: string): string {
  return path.join(appLibrarySearchIndexRoot(), `${bookId}.json`);
}
```

- [ ] **Step 3: Ensure new directories exist**

In `ensureAppData()`, after ensuring `appLibraryFilesRoot()`, add:

```ts
await ensureDir(appLibraryCoversRoot());
await ensureDir(appLibrarySearchIndexRoot());
```

- [ ] **Step 4: Add search index helpers**

Near `readLibraryIndex()` add:

```ts
async function writeEpubSearchIndex(bookId: string, items: EpubSearchIndexItem[]): Promise<string> {
  const targetPath = epubSearchIndexPath(bookId);
  const index: EpubSearchIndex = {
    version: 1,
    bookId,
    updatedAt: now(),
    items
  };
  await writeJson(targetPath, index);
  return targetPath;
}

async function readEpubSearchIndex(bookId: string): Promise<EpubSearchIndex | undefined> {
  try {
    const index = await readJson<EpubSearchIndex | undefined>(epubSearchIndexPath(bookId), undefined);
    return index?.version === 1 && Array.isArray(index.items) ? index : undefined;
  } catch {
    return undefined;
  }
}
```

- [ ] **Step 5: Replace EPUB import record creation**

Inside `importEpub()`, replace the `books.unshift({ ... })` block with:

```ts
let parsed:
  | Awaited<ReturnType<typeof parseEpubFile>>
  | undefined;
try {
  parsed = await parseEpubFile(targetPath, id, appLibraryCoversRoot());
} catch (error) {
  await writeLog("warn", "EPUB metadata parsing failed.", {
    bookId: id,
    error: error instanceof Error ? error.message : String(error)
  });
}
let searchIndexPathValue: string | undefined;
if (parsed?.searchItems.length) {
  try {
    searchIndexPathValue = await writeEpubSearchIndex(id, parsed.searchItems);
  } catch (error) {
    await writeLog("warn", "EPUB search index creation failed.", {
      bookId: id,
      error: error instanceof Error ? error.message : String(error)
    });
  }
}
books.unshift({
  id,
  title: parsed?.title || path.basename(sourcePath, ext),
  filePath: targetPath,
  originalPath: sourcePath,
  format: "epub",
  importedAt: now(),
  updatedAt: now(),
  size: info.size,
  author: parsed?.author,
  description: parsed?.description,
  language: parsed?.language,
  publisher: parsed?.publisher,
  coverPath: parsed?.coverPath,
  epub: {
    coverPath: parsed?.coverPath,
    author: parsed?.author,
    description: parsed?.description,
    language: parsed?.language,
    publisher: parsed?.publisher,
    toc: parsed?.toc ?? [],
    searchIndexedAt: searchIndexPathValue ? now() : undefined,
    searchIndexPath: searchIndexPathValue
  }
});
```

If TypeScript reports duplicate fields inside `epub`, keep only fields defined by `EpubBookMetadata`.

- [ ] **Step 6: Return TOC from `openEpub()`**

Change return value in `openEpub()`:

```ts
return {
  book,
  epubUrl: epubUrlForBook(book.id),
  progress: await getProgress(bookId),
  settings: await getReaderSettings(),
  toc: book.epub?.toc ?? []
};
```

- [ ] **Step 7: Search EPUB sidecar body text**

Inside `searchGlobal()`, in the library loop, replace EPUB content handling:

```ts
if (book.format === "epub") {
  const index = await readEpubSearchIndex(book.id);
  if (index) {
    for (const item of index.items) {
      pushResult(
        "book",
        `${book.title} · ${item.title}`,
        `${book.format}\n${book.author ?? ""}\n${book.originalPath ?? ""}`,
        item.text,
        book.originalPath ?? book.filePath,
        { bookId: book.id, epubHref: item.href }
      );
    }
  }
} else {
  try {
    content = await readTextFile(book.filePath);
  } catch {
    content = "";
  }
  pushResult("book", book.title, `${book.format}\n${book.originalPath ?? ""}\n${book.filePath}`, content, book.originalPath ?? book.filePath, { bookId: book.id });
  continue;
}
pushResult("book", book.title, `${book.format}\n${book.author ?? ""}\n${book.originalPath ?? ""}\n${book.filePath}`, "", book.originalPath ?? book.filePath, { bookId: book.id });
```

- [ ] **Step 8: Run build**

Run:

```bash
npm run build
```

Expected: build passes or surfaces renderer follow-up errors for missing new store fields.

- [ ] **Step 9: Checkpoint**

Record changed file:

```text
electron/main/index.ts
```

## Task 4: Wire search result href through renderer state

**Files:**
- Modify: `src/hooks/useSearchActions.ts`
- Modify: `src/hooks/useLibraryActions.ts`

- [ ] **Step 1: Pass href from search open**

In `useSearchActions.ts`, change the EPUB/TXT/Markdown `setActiveBook` call to:

```ts
useLibraryStore
  .getState()
  .setActiveBook(payload.book, "content" in payload ? payload.content : "", "epubUrl" in payload ? payload.epubUrl : undefined, result.target.epubHref);
```

- [ ] **Step 2: Keep normal library opens without target href**

In `useLibraryActions.ts`, ensure the existing call remains:

```ts
setActiveBook(payload.book, "content" in payload ? payload.content : "", "epubUrl" in payload ? payload.epubUrl : undefined);
```

- [ ] **Step 3: Run build**

Run:

```bash
npm run build
```

Expected: build passes or only UI follow-up errors remain.

- [ ] **Step 4: Checkpoint**

Record changed files:

```text
src/hooks/useSearchActions.ts
src/hooks/useLibraryActions.ts
```

## Task 5: Update library cards for EPUB cover and author

**Files:**
- Modify: `src/features/library/LibraryPage.tsx`

- [ ] **Step 1: Add cover image rendering**

Inside each book `<article>`, before the text header, add:

```tsx
{book.coverPath && (
  <div className="mb-3 aspect-[3/4] overflow-hidden rounded-md bg-neutral-100">
    <img src={`file://${book.coverPath}`} alt={`${book.title} 封面`} className="h-full w-full object-cover" />
  </div>
)}
```

- [ ] **Step 2: Show author under title**

Under title/format block add:

```tsx
{book.author && <div className="mt-1 line-clamp-1 text-xs text-neutral-500">作者：{book.author}</div>}
```

- [ ] **Step 3: Show description if present**

Before progress bar add:

```tsx
{book.description && <p className="mt-3 line-clamp-3 text-xs leading-5 text-neutral-500">{book.description}</p>}
```

- [ ] **Step 4: Run build**

Run:

```bash
npm run build
```

Expected: build passes.

- [ ] **Step 5: Checkpoint**

Record changed file:

```text
src/features/library/LibraryPage.tsx
```

## Task 6: Add EPUB TOC panel and href jump behavior

**Files:**
- Modify: `src/features/library/EpubReaderPage.tsx`

- [ ] **Step 1: Read TOC and pending href from store**

Add selectors after `activeEpubUrl`:

```tsx
const targetHref = useLibraryStore((state) => state.activeEpubTargetHref);
const toc = activeBook?.epub?.toc ?? [];
```

- [ ] **Step 2: Use search target href on initial display**

Replace:

```ts
await rendition.display(restoreCfi || undefined);
```

with:

```ts
await rendition.display(targetHref || restoreCfi || undefined);
```

Add `targetHref` to that effect dependency list.

- [ ] **Step 3: Add TOC click helper**

Before render return add:

```tsx
const jumpToToc = async (href: string) => {
  try {
    await renditionRef.current?.display(href);
    handleActivity();
    scheduleProgressSave();
  } catch (error) {
    setError(error instanceof Error ? error.message : String(error));
  }
};
```

- [ ] **Step 4: Render TOC in side panel**

Inside the right `ShellPanel`, before “阅读设置”, add:

```tsx
<div className="mb-5">
  <div className="mb-2 text-sm font-semibold text-neutral-700">目录</div>
  {toc.length === 0 ? (
    <div className="rounded-md bg-neutral-50 p-3 text-xs text-neutral-500">未检测到目录</div>
  ) : (
    <div className="max-h-64 overflow-auto rounded-md border border-neutral-100">
      {toc.map((item) => (
        <button
          key={item.id}
          className="block w-full truncate px-3 py-2 text-left text-xs text-neutral-600 hover:bg-neutral-50 hover:text-neutral-950"
          style={{ paddingLeft: `${12 + Math.max(0, item.level - 1) * 12}px` }}
          onClick={() => void jumpToToc(item.href)}
          title={item.label}
        >
          {item.label}
        </button>
      ))}
    </div>
  )}
</div>
```

- [ ] **Step 5: Run build**

Run:

```bash
npm run build
```

Expected: build passes.

- [ ] **Step 6: Checkpoint**

Record changed file:

```text
src/features/library/EpubReaderPage.tsx
```

## Task 7: Add reading stats UI verification script

**Files:**
- Create: `scripts/verify-stats-ui.mjs`
- Modify: `package.json`

- [ ] **Step 1: Create stats UI verifier**

Create `scripts/verify-stats-ui.mjs`:

```js
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const statsPage = readFileSync(path.join(root, "src/features/library/ReadingStatsPage.tsx"), "utf8");

const requiredSnippets = [
  "todayDurationMs",
  "last7DaysDurationMs",
  "last30DaysDurationMs",
  "totalDurationMs",
  "byBook",
  "recentBooks",
  "recentSessions",
  "averageSessionDurationMs",
  "基于有效阅读会话聚合"
];

const missing = requiredSnippets.filter((snippet) => !statsPage.includes(snippet));
if (missing.length > 0) {
  console.error("[verify-stats-ui] Missing expected stats UI bindings:");
  for (const item of missing) console.error(`  - ${item}`);
  process.exit(1);
}

console.log("[verify-stats-ui] Reading stats UI bindings verified.");
```

- [ ] **Step 2: Add package script**

In `package.json` scripts, add:

```json
"verify:stats-ui": "node scripts/verify-stats-ui.mjs"
```

- [ ] **Step 3: Run verifier**

Run:

```bash
npm run verify:stats-ui
```

Expected:

```text
[verify-stats-ui] Reading stats UI bindings verified.
```

- [ ] **Step 4: Run build**

Run:

```bash
npm run build
```

Expected: build passes.

- [ ] **Step 5: Checkpoint**

Record changed files:

```text
scripts/verify-stats-ui.mjs
package.json
```

## Task 8: Update Beta checklist and run final verification

**Files:**
- Modify: `BETA_CHECKLIST.md`

- [ ] **Step 1: Update scope and known issues**

In `BETA_CHECKLIST.md`, remove EPUB body search / TOC / cover parsing from known P2 issues and add a maintenance update:

```md
EPUB completion update: EPUB import now parses metadata and cover when available, the EPUB reader shows a TOC panel, global search can use EPUB sidecar text indexes, and `npm run verify:stats-ui` checks reading stats UI bindings.
```

- [ ] **Step 2: Add automated check row**

Add a row:

```md
| Reading stats UI binding check | `npm run verify:stats-ui` | Passed |
```

- [ ] **Step 3: Add manual EPUB regression items**

Add to manual regression:

```md
8. Confirm the EPUB card shows parsed title/author/cover when the file provides them.
9. Open the EPUB TOC panel and click a chapter.
10. Search for a phrase from the EPUB body and click the result.
11. Expected: EPUB opens and jumps to the matched section or at minimum opens the correct book.
```

- [ ] **Step 4: Run complete verification**

Run:

```bash
npm run verify:stats-ui
npm run verify:beta
```

Expected: both commands pass.

- [ ] **Step 5: Optional package verification**

If preparing a fresh distributable, run:

```bash
npm run dist:beta
npm run verify:beta:release
```

Historical expected path at the time: `release-beta/win-unpacked/小说作者工作台.exe`. Current package name is `release-beta/win-unpacked/创作阅读助手.exe`.

- [ ] **Step 6: Obsidian Codex memory closeout**

Update:

```text
D:\Application\文档\Obsidian\Codex\projects\创作阅读助手.md
D:\Application\文档\Obsidian\Codex\agent\open-loops.md
D:\Application\文档\Obsidian\Codex\agent\skill-usage-log.md
```

Record completed EPUB metadata/TOC/search and stats UI verification, or record any remaining manual验收 items.

## Self-Review

- Spec coverage: Tasks cover shared types, parser, import integration, sidecar search, search-result navigation, library UI, EPUB TOC UI, stats verification, Beta checklist, and memory closeout.
- Placeholder scan: No task uses unresolved placeholder language as an instruction. Each code-changing task names the exact file and code to add or replace.
- Type consistency: `EpubTocItem`, `EpubBookMetadata`, `EpubSearchIndex`, `ReaderEpubPayload.toc`, `SearchTarget.epubHref`, and `activeEpubTargetHref` are defined before use.
- Constraint check: The plan avoids commits because the current directory is not a Git repository.
