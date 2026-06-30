import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import JSZip from "jszip";
import { DOMParser } from "@xmldom/xmldom";
import type { Document as XmlDocument, Element as XmlElement } from "@xmldom/xmldom";

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

interface ManifestItem {
  id: string;
  href: string;
  zipPath: string;
  mediaType: string;
  properties: string[];
}

export async function parseEpubFile(epubPath: string, bookId: string, coversRoot: string): Promise<ParsedEpubMetadata> {
  try {
    const zip = await JSZip.loadAsync(await readFile(epubPath));
    const containerXml = await readZipText(zip, "META-INF/container.xml");
    if (!containerXml) return emptyMetadata();

    const opfPath = findOpfPath(parseXml(containerXml));
    if (!opfPath) return emptyMetadata();

    const opfXml = await readZipText(zip, opfPath);
    if (!opfXml) return emptyMetadata();

    const opfDoc = parseXml(opfXml);
    const opfDir = zipDirname(opfPath);
    const manifest = parseManifest(opfDoc, opfDir);
    const spineTocId = getFirstElementByLocalName(opfDoc, "spine")?.getAttribute("toc")?.trim();
    const metadata: ParsedEpubMetadata = {
      ...parseMetadata(opfDoc),
      toc: await parseToc(zip, manifest, spineTocId),
      searchItems: await buildSearchItems(zip, manifest)
    };

    const coverPath = await extractCover(zip, opfDoc, manifest, bookId, coversRoot);
    if (coverPath) metadata.coverPath = coverPath;

    return metadata;
  } catch {
    return emptyMetadata();
  }
}

function emptyMetadata(): ParsedEpubMetadata {
  return { toc: [], searchItems: [] };
}

function parseXml(xml: string): XmlDocument {
  return new DOMParser().parseFromString(xml, "application/xml");
}

async function readZipText(zip: JSZip, zipPath: string): Promise<string | undefined> {
  const file = zip.file(normalizeZipPath(zipPath));
  return file ? file.async("text") : undefined;
}

async function readZipBytes(zip: JSZip, zipPath: string): Promise<Uint8Array | undefined> {
  const file = zip.file(normalizeZipPath(zipPath));
  return file ? file.async("uint8array") : undefined;
}

function findOpfPath(containerDoc: XmlDocument): string | undefined {
  const rootFiles = getElementsByLocalName(containerDoc, "rootfile");
  const opfRoot = rootFiles.find((element) => element.getAttribute("media-type") === "application/oebps-package+xml") ?? rootFiles[0];
  return normalizeOptionalZipPath(opfRoot?.getAttribute("full-path"));
}

function parseMetadata(opfDoc: XmlDocument): Omit<ParsedEpubMetadata, "coverPath" | "toc" | "searchItems"> {
  const metadataElement = getFirstElementByLocalName(opfDoc, "metadata") ?? opfDoc;
  return {
    title: findFirstText(metadataElement, "title"),
    author: findFirstText(metadataElement, "creator"),
    description: findFirstText(metadataElement, "description"),
    language: findFirstText(metadataElement, "language"),
    publisher: findFirstText(metadataElement, "publisher")
  };
}

function parseManifest(opfDoc: XmlDocument, opfDir: string): ManifestItem[] {
  return getElementsByLocalName(opfDoc, "item")
    .map((element) => {
      const id = element.getAttribute("id")?.trim() ?? "";
      const href = element.getAttribute("href")?.trim() ?? "";
      const mediaType = element.getAttribute("media-type")?.trim().toLowerCase() ?? "";
      const properties = splitProperties(element.getAttribute("properties"));

      return {
        id,
        href,
        zipPath: resolveZipPath(opfDir, href),
        mediaType,
        properties
      };
    })
    .filter((item) => item.id && item.href);
}

async function extractCover(
  zip: JSZip,
  opfDoc: XmlDocument,
  manifest: ManifestItem[],
  bookId: string,
  coversRoot: string
): Promise<string | undefined> {
  try {
    const coverItem = findCoverItem(opfDoc, manifest);
    if (!coverItem) return undefined;

    const coverBytes = await readZipBytes(zip, coverItem.zipPath);
    if (!coverBytes) return undefined;

    const extension = coverExtension(coverItem);
    const coverFileName = `${safeFileStem(bookId)}${extension}`;
    const coverPath = path.resolve(coversRoot, coverFileName);
    const rootPath = path.resolve(coversRoot);
    if (!isWithinDirectory(coverPath, rootPath)) return undefined;

    await mkdir(rootPath, { recursive: true });
    await writeFile(coverPath, coverBytes);
    return coverPath;
  } catch {
    return undefined;
  }
}

function findCoverItem(opfDoc: XmlDocument, manifest: ManifestItem[]): ManifestItem | undefined {
  const epub3Cover = manifest.find((item) => item.properties.includes("cover-image"));
  if (epub3Cover) return epub3Cover;

  const coverMeta = getElementsByLocalName(opfDoc, "meta").find((element) => element.getAttribute("name")?.trim() === "cover");
  const coverId = coverMeta?.getAttribute("content")?.trim();
  return coverId ? manifest.find((item) => item.id === coverId) : undefined;
}

async function parseToc(zip: JSZip, manifest: ManifestItem[], spineTocId?: string): Promise<EpubTocItem[]> {
  const navItem = manifest.find((item) => item.properties.includes("nav"));
  if (navItem) {
    const toc = await parseNavToc(zip, navItem);
    if (toc.length > 0) return toc;
  }

  const ncxItem = (spineTocId ? manifest.find((item) => item.id === spineTocId) : undefined)
    ?? manifest.find((item) => item.mediaType === "application/x-dtbncx+xml");
  return ncxItem ? parseNcxToc(zip, ncxItem) : [];
}

async function parseNavToc(zip: JSZip, navItem: ManifestItem): Promise<EpubTocItem[]> {
  const navXml = await readZipText(zip, navItem.zipPath);
  if (!navXml) return [];

  const navDoc = parseXml(navXml);
  const navElement = findTocNavElement(navDoc) ?? getFirstElementByLocalName(navDoc, "nav");
  if (!navElement) return [];

  const navHrefDir = packageHrefDirname(navItem.href);
  return getElementsByLocalName(navElement, "a")
    .map((anchor, index) => ({
      id: `toc-nav-${index}`,
      label: normalizeWhitespace(anchor.textContent ?? ""),
      href: resolvePackageHref(navHrefDir, anchor.getAttribute("href") ?? ""),
      level: 1
    }))
    .filter((item) => item.label && item.href);
}

async function parseNcxToc(zip: JSZip, ncxItem: ManifestItem): Promise<EpubTocItem[]> {
  const ncxXml = await readZipText(zip, ncxItem.zipPath);
  if (!ncxXml) return [];

  const ncxDoc = parseXml(ncxXml);
  const ncxHrefDir = packageHrefDirname(ncxItem.href);
  return getElementsByLocalName(ncxDoc, "navPoint")
    .map((navPoint, index) => {
      const label = findFirstText(navPoint, "text") ?? "";
      const src = getFirstElementByLocalName(navPoint, "content")?.getAttribute("src") ?? "";
      return {
        id: `toc-ncx-${index}`,
        label,
        href: resolvePackageHref(ncxHrefDir, src),
        level: 1
      };
    })
    .filter((item) => item.label && item.href);
}

async function buildSearchItems(zip: JSZip, manifest: ManifestItem[]): Promise<EpubSearchIndexItem[]> {
  const htmlItems = manifest.filter(isHtmlManifestItem);
  const searchItems: EpubSearchIndexItem[] = [];

  for (const [index, item] of htmlItems.entries()) {
    const html = await readZipText(zip, item.zipPath);
    if (!html) continue;

    const cappedHtml = html.slice(0, 200_000);
    const text = htmlToSearchText(cappedHtml).slice(0, 20_000);
    if (!text) continue;

    searchItems.push({
      id: `search-${index}`,
      title: extractHtmlTitle(cappedHtml) ?? item.id,
      href: item.href,
      text
    });
  }

  return searchItems;
}

function findTocNavElement(doc: XmlDocument): XmlElement | undefined {
  return getElementsByLocalName(doc, "nav").find((element) => {
    const epubType = element.getAttribute("epub:type") ?? element.getAttribute("type") ?? "";
    const role = element.getAttribute("role") ?? "";
    return splitProperties(epubType).includes("toc") || role === "doc-toc";
  });
}

function isHtmlManifestItem(item: ManifestItem): boolean {
  if (item.mediaType === "application/xhtml+xml" || item.mediaType === "text/html") return true;
  return /\.(xhtml|html|htm)$/i.test(item.href);
}

function extractHtmlTitle(html: string): string | undefined {
  const titleMatch = html.match(/<title\b[^>]*>([\s\S]*?)<\/title>/i);
  return titleMatch ? htmlToSearchText(titleMatch[1]).slice(0, 200) || undefined : undefined;
}

function htmlToSearchText(html: string): string {
  return decodeHtmlEntities(
    html
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, " ")
      .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, " ")
      .replace(/<!--[\s\S]*?-->/g, " ")
      .replace(/<[^>]+>/g, " ")
  );
}

function decodeHtmlEntities(value: string): string {
  return normalizeWhitespace(
    value
      .replace(/&nbsp;/gi, " ")
      .replace(/&amp;/gi, "&")
      .replace(/&lt;/gi, "<")
      .replace(/&gt;/gi, ">")
      .replace(/&quot;/gi, "\"")
      .replace(/&apos;/gi, "'")
      .replace(/&#(\d+);/g, (_match, codePoint: string) => safeCodePoint(Number(codePoint)))
      .replace(/&#x([0-9a-f]+);/gi, (_match, codePoint: string) => safeCodePoint(Number.parseInt(codePoint, 16)))
  );
}

function safeCodePoint(codePoint: number): string {
  if (!Number.isFinite(codePoint)) return "";
  try {
    return String.fromCodePoint(codePoint);
  } catch {
    return "";
  }
}

function findFirstText(root: XmlDocument | XmlElement, localName: string): string | undefined {
  const element = getFirstElementByLocalName(root, localName);
  const text = element?.textContent ? normalizeWhitespace(element.textContent) : "";
  return text || undefined;
}

function getFirstElementByLocalName(root: XmlDocument | XmlElement, localName: string): XmlElement | undefined {
  return getElementsByLocalName(root, localName)[0];
}

function getElementsByLocalName(root: XmlDocument | XmlElement, localName: string): XmlElement[] {
  const lowerName = localName.toLowerCase();
  return Array.from(root.getElementsByTagName("*")).filter((element) => elementLocalName(element) === lowerName);
}

function elementLocalName(element: XmlElement): string {
  return (element.localName || element.nodeName.split(":").pop() || "").toLowerCase();
}

function splitProperties(value: string | null | undefined): string[] {
  return (value ?? "")
    .split(/\s+/)
    .map((property) => property.trim().toLowerCase())
    .filter(Boolean);
}

function normalizeWhitespace(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function normalizeOptionalZipPath(value: string | null | undefined): string | undefined {
  const normalized = value ? normalizeZipPath(value) : "";
  return normalized || undefined;
}

function normalizeZipPath(value: string): string {
  return value.replace(/\\/g, "/").replace(/^\/+/, "").split("/").filter(Boolean).join("/");
}

function resolveZipPath(baseDir: string, href: string): string {
  const [hrefPath, fragment] = href.replace(/\\/g, "/").split("#", 2);
  if (!hrefPath) return fragment ? `#${fragment}` : "";

  const normalizedPath = normalizeZipPath(path.posix.normalize(path.posix.join(baseDir, hrefPath)));
  return fragment ? `${normalizedPath}#${fragment}` : normalizedPath;
}

function resolvePackageHref(baseDir: string, href: string): string {
  const [hrefPath, fragment] = href.replace(/\\/g, "/").split("#", 2);
  if (!hrefPath) return fragment ? `#${fragment}` : "";

  const normalizedPath = normalizeZipPath(path.posix.normalize(path.posix.join(baseDir, hrefPath)));
  return fragment ? `${normalizedPath}#${fragment}` : normalizedPath;
}

function zipDirname(zipPath: string): string {
  const directory = path.posix.dirname(normalizeZipPath(zipPath));
  return directory === "." ? "" : directory;
}

function packageHrefDirname(href: string): string {
  const directory = path.posix.dirname(normalizeZipPath(href));
  return directory === "." ? "" : directory;
}

function coverExtension(item: ManifestItem): string {
  const existingExtension = path.posix.extname(item.href).toLowerCase();
  if (existingExtension) return existingExtension;

  if (item.mediaType === "image/png") return ".png";
  if (item.mediaType === "image/gif") return ".gif";
  if (item.mediaType === "image/webp") return ".webp";
  if (item.mediaType === "image/svg+xml") return ".svg";
  return ".jpg";
}

function safeFileStem(value: string): string {
  return value.replace(/[^a-zA-Z0-9._-]/g, "_").replace(/^\.+/, "_") || "book";
}

function isWithinDirectory(filePath: string, directory: string): boolean {
  const relativePath = path.relative(directory, filePath);
  return relativePath === "" || (!relativePath.startsWith("..") && !path.isAbsolute(relativePath));
}
