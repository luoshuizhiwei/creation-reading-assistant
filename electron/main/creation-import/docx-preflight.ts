import { createInflateRaw } from "node:zlib";

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;
const MAX_EOCD_SEARCH = 65_557;

export interface DocxPreflightLimits {
  maxDocumentXmlBytes: number;
  maxTotalUncompressedBytes: number;
}

const DEFAULT_LIMITS: DocxPreflightLimits = {
  maxDocumentXmlBytes: 80 * 1024 * 1024,
  maxTotalUncompressedBytes: 200 * 1024 * 1024
};

interface ZipEntry {
  name: string;
  flags: number;
  method: number;
  compressedSize: number;
  uncompressedSize: number;
  localHeaderOffset: number;
}

export class DocxPreflightError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DocxPreflightError";
  }
}

function invalidArchive(): never {
  throw new DocxPreflightError("文件损坏或不是有效的 .docx 文档。");
}

function findEndOfCentralDirectory(buffer: Buffer): number {
  const start = Math.max(0, buffer.length - MAX_EOCD_SEARCH);
  for (let offset = buffer.length - 22; offset >= start; offset -= 1) {
    if (buffer.readUInt32LE(offset) === EOCD_SIGNATURE) return offset;
  }
  return invalidArchive();
}

function parseCentralDirectory(buffer: Buffer): ZipEntry[] {
  if (buffer.length < 22) return invalidArchive();
  const eocd = findEndOfCentralDirectory(buffer);
  const diskNumber = buffer.readUInt16LE(eocd + 4);
  const centralDisk = buffer.readUInt16LE(eocd + 6);
  const entriesOnDisk = buffer.readUInt16LE(eocd + 8);
  const entryCount = buffer.readUInt16LE(eocd + 10);
  const centralSize = buffer.readUInt32LE(eocd + 12);
  const centralOffset = buffer.readUInt32LE(eocd + 16);
  if (
    diskNumber !== 0 ||
    centralDisk !== 0 ||
    entriesOnDisk !== entryCount ||
    entryCount === 0xffff ||
    centralSize === 0xffffffff ||
    centralOffset === 0xffffffff ||
    centralOffset + centralSize > eocd
  ) {
    return invalidArchive();
  }

  const entries: ZipEntry[] = [];
  let offset = centralOffset;
  for (let index = 0; index < entryCount; index += 1) {
    if (offset + 46 > buffer.length || buffer.readUInt32LE(offset) !== CENTRAL_SIGNATURE) return invalidArchive();
    const flags = buffer.readUInt16LE(offset + 8);
    const method = buffer.readUInt16LE(offset + 10);
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const uncompressedSize = buffer.readUInt32LE(offset + 24);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const diskStart = buffer.readUInt16LE(offset + 34);
    const localHeaderOffset = buffer.readUInt32LE(offset + 42);
    const next = offset + 46 + nameLength + extraLength + commentLength;
    if (
      diskStart !== 0 ||
      compressedSize === 0xffffffff ||
      uncompressedSize === 0xffffffff ||
      localHeaderOffset === 0xffffffff ||
      next > buffer.length
    ) {
      return invalidArchive();
    }
    const name = buffer.subarray(offset + 46, offset + 46 + nameLength).toString("utf8").replace(/\\/g, "/");
    if (!name || name.includes("\0") || name.startsWith("/") || name.split("/").includes("..")) return invalidArchive();
    if ((flags & 0x1) !== 0) throw new DocxPreflightError("文档已加密，无法导入。请先解除密码保护。");
    if (method !== 0 && method !== 8) throw new DocxPreflightError("DOCX 使用了不支持的压缩方式。");
    entries.push({ name, flags, method, compressedSize, uncompressedSize, localHeaderOffset });
    offset = next;
  }
  if (offset !== centralOffset + centralSize) return invalidArchive();
  return entries;
}

function entryCompressedData(buffer: Buffer, entry: ZipEntry): Buffer {
  const offset = entry.localHeaderOffset;
  if (offset + 30 > buffer.length || buffer.readUInt32LE(offset) !== LOCAL_SIGNATURE) return invalidArchive();
  const localFlags = buffer.readUInt16LE(offset + 6);
  const localMethod = buffer.readUInt16LE(offset + 8);
  const nameLength = buffer.readUInt16LE(offset + 26);
  const extraLength = buffer.readUInt16LE(offset + 28);
  if (localFlags !== entry.flags || localMethod !== entry.method) return invalidArchive();
  const dataStart = offset + 30 + nameLength + extraLength;
  const dataEnd = dataStart + entry.compressedSize;
  if (dataStart > buffer.length || dataEnd > buffer.length) return invalidArchive();
  return buffer.subarray(dataStart, dataEnd);
}

async function countInflatedBytes(compressed: Buffer, limit: number): Promise<number> {
  return new Promise<number>((resolve, reject) => {
    const inflate = createInflateRaw();
    let count = 0;
    let settled = false;
    const finishReject = (error: unknown): void => {
      if (settled) return;
      settled = true;
      reject(error instanceof DocxPreflightError ? error : new DocxPreflightError("DOCX 压缩数据损坏。"));
    };
    inflate.on("data", (chunk: Buffer) => {
      count += chunk.length;
      if (count > limit) {
        inflate.destroy(new DocxPreflightError("DOCX 解压后数据量过大，已中止导入。"));
      }
    });
    inflate.once("error", finishReject);
    inflate.once("end", () => {
      if (settled) return;
      settled = true;
      resolve(count);
    });
    inflate.end(compressed);
  });
}

/**
 * mammoth 前置 ZIP 预算校验：逐条目真实解压并只计数，不保留输出，阻止压缩炸弹在主进程物化。
 */
export async function preflightDocxArchive(buffer: Buffer, limits: DocxPreflightLimits = DEFAULT_LIMITS): Promise<void> {
  const entries = parseCentralDirectory(buffer);
  let total = 0;
  let documentXmlSeen = false;
  for (const entry of entries) {
    if (entry.name.endsWith("/")) continue;
    const isDocumentXml = entry.name === "word/document.xml";
    const remainingTotal = limits.maxTotalUncompressedBytes - total;
    const entryLimit = isDocumentXml ? Math.min(limits.maxDocumentXmlBytes, remainingTotal) : remainingTotal;
    if (entryLimit < 0 || entry.uncompressedSize > entryLimit) {
      throw new DocxPreflightError("DOCX 解压后数据量过大，已中止导入。");
    }
    const compressed = entryCompressedData(buffer, entry);
    const actualSize = entry.method === 0 ? compressed.length : await countInflatedBytes(compressed, entryLimit);
    if (actualSize !== entry.uncompressedSize) return invalidArchive();
    total += actualSize;
    if (total > limits.maxTotalUncompressedBytes) {
      throw new DocxPreflightError("DOCX 解压后数据量过大，已中止导入。");
    }
    if (isDocumentXml) documentXmlSeen = true;
  }
  if (!documentXmlSeen) return invalidArchive();
}
