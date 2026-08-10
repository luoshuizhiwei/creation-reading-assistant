import { rm } from "node:fs/promises";

/** Windows 上 SQLite WAL/shm 文件句柄延迟释放，删除目录需重试避免偶发 EBUSY。 */
export async function removeWithRetry(target: string, attempts = 6, delayMs = 300): Promise<void> {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      await rm(target, { recursive: true, force: true });
      return;
    } catch {
      if (attempt === attempts - 1) throw new Error(`无法清理临时目录：${target}`);
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
}
