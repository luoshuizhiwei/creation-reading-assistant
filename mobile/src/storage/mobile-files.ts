import { Directory, Encoding, Filesystem } from "@capacitor/filesystem";
import type { BookFormat } from "../../../src/types/library";

export interface MobileBookFileInput {
  bookId: string;
  originalFileName: string;
  format: BookFormat;
  content: string;
}

export interface SavedMobileBookFile {
  localFilePath: string;
  localUri?: string;
}

function safeFileName(name: string): string {
  return name.replace(/[\\/:*?"<>|]/g, "_").slice(0, 80) || "book";
}

export async function saveMobileBookFile(input: MobileBookFileInput): Promise<SavedMobileBookFile | undefined> {
  const path = `books/${input.bookId}-${safeFileName(input.originalFileName)}`;
  try {
    await Filesystem.mkdir({ path: "books", directory: Directory.Data, recursive: true }).catch(() => undefined);
    await Filesystem.writeFile({
      path,
      data: input.content,
      directory: Directory.Data,
      encoding: Encoding.UTF8
    });
    const uri = await Filesystem.getUri({ path, directory: Directory.Data });
    return {
      localFilePath: path,
      localUri: uri.uri
    };
  } catch {
    return undefined;
  }
}

export async function readMobileBookFile(localPath?: string): Promise<string | undefined> {
  if (!localPath) return undefined;
  if (/^[a-z]+:\/\//i.test(localPath)) return undefined;
  try {
    const result = await Filesystem.readFile({
      path: localPath,
      directory: Directory.Data,
      encoding: Encoding.UTF8
    });
    return typeof result.data === "string" ? result.data : undefined;
  } catch {
    return undefined;
  }
}
