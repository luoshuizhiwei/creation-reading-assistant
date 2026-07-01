import { Directory, Encoding, Filesystem } from "@capacitor/filesystem";
import type { BookFormat } from "../../../src/types/library";

export interface MobileBookFileInput {
  bookId: string;
  originalFileName: string;
  format: BookFormat;
  content: string;
}

export interface MobileBookBlobInput {
  bookId: string;
  originalFileName: string;
  format: BookFormat;
  blob: Blob;
}

export interface SavedMobileBookFile {
  localFilePath: string;
  localUri?: string;
}

function safeFileName(name: string): string {
  return name.replace(/[\\/:*?"<>|]/g, "_").slice(0, 80) || "book";
}

function bookPath(bookId: string, originalFileName: string): string {
  return `books/${bookId}-${safeFileName(originalFileName)}`;
}

async function ensureBooksDirectory(): Promise<void> {
  await Filesystem.mkdir({ path: "books", directory: Directory.Data, recursive: true }).catch(() => undefined);
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}

export async function saveMobileBookFile(input: MobileBookFileInput): Promise<SavedMobileBookFile | undefined> {
  const path = bookPath(input.bookId, input.originalFileName);
  try {
    await ensureBooksDirectory();
    if (input.format === "epub") {
      await Filesystem.writeFile({
        path,
        data: input.content,
        directory: Directory.Data
      });
    } else {
      await Filesystem.writeFile({
        path,
        data: input.content,
        directory: Directory.Data,
        encoding: Encoding.UTF8
      });
    }
    const uri = await Filesystem.getUri({ path, directory: Directory.Data });
    return {
      localFilePath: path,
      localUri: uri.uri
    };
  } catch {
    return undefined;
  }
}

export async function saveMobileBookBlob(input: MobileBookBlobInput): Promise<SavedMobileBookFile | undefined> {
  const path = bookPath(input.bookId, input.originalFileName);
  try {
    await ensureBooksDirectory();
    if (input.format === "epub") {
      await Filesystem.writeFile({
        path,
        data: arrayBufferToBase64(await input.blob.arrayBuffer()),
        directory: Directory.Data
      });
    } else {
      await Filesystem.writeFile({
        path,
        data: await input.blob.text(),
        directory: Directory.Data,
        encoding: Encoding.UTF8
      });
    }
    const uri = await Filesystem.getUri({ path, directory: Directory.Data });
    return {
      localFilePath: path,
      localUri: uri.uri
    };
  } catch {
    return undefined;
  }
}

export async function readMobileBookFile(localPath?: string, format?: BookFormat): Promise<string | undefined> {
  if (!localPath) return undefined;
  if (/^[a-z]+:\/\//i.test(localPath)) return undefined;
  try {
    const result = await Filesystem.readFile({
      path: localPath,
      directory: Directory.Data,
      ...(format === "epub" ? {} : { encoding: Encoding.UTF8 })
    });
    return typeof result.data === "string" ? result.data : undefined;
  } catch {
    return undefined;
  }
}
