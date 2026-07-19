import { preparePlainTextSource, type PreparedPlainTextSource } from "./mobile-reader-txt";

const WORKER_PREPARE_THRESHOLD_CHARS = 256 * 1024;

interface PrepareResponse {
  source?: PreparedPlainTextSource;
  error?: string;
}

function abortError(): DOMException {
  return new DOMException("TXT 预处理已取消。", "AbortError");
}

function prepareOnMainThread(content: string, title: string, signal?: AbortSignal): Promise<PreparedPlainTextSource> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError());
      return;
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const cleanup = () => {
      if (timer !== undefined) globalThis.clearTimeout(timer);
      signal?.removeEventListener("abort", handleAbort);
    };
    const handleAbort = () => {
      cleanup();
      reject(abortError());
    };
    signal?.addEventListener("abort", handleAbort, { once: true });
    timer = globalThis.setTimeout(() => {
      cleanup();
      if (signal?.aborted) {
        reject(abortError());
        return;
      }
      try {
        resolve(preparePlainTextSource(content, title));
      } catch (error) {
        reject(error);
      }
    }, 0);
  });
}

/**
 * 大 TXT 的章节扫描放进独立 Worker，避免正则扫描和目录构建阻塞 WebView UI。
 * Worker 会在切书、返回或重新排版时立即 terminate；不让过期结果回写当前书。
 */
export function preparePlainTextSourceAsync(
  content: string,
  title: string,
  signal?: AbortSignal
): Promise<PreparedPlainTextSource> {
  if (signal?.aborted) return Promise.reject(abortError());
  if (content.length < WORKER_PREPARE_THRESHOLD_CHARS || typeof Worker === "undefined") {
    return prepareOnMainThread(content, title, signal);
  }

  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL("./mobile-reader-preparation.worker.ts", import.meta.url), { type: "module" });
    let settled = false;
    const cleanup = () => {
      signal?.removeEventListener("abort", handleAbort);
      worker.terminate();
    };
    const finish = (callback: () => void) => {
      if (settled) return;
      settled = true;
      cleanup();
      callback();
    };
    const handleAbort = () => finish(() => reject(abortError()));
    signal?.addEventListener("abort", handleAbort, { once: true });
    worker.onmessage = (event: MessageEvent<PrepareResponse>) => {
      if (event.data.source) {
        finish(() => resolve(event.data.source as PreparedPlainTextSource));
        return;
      }
      finish(() => reject(new Error(event.data.error || "TXT 预处理失败。")));
    };
    worker.onerror = (event) => {
      finish(() => reject(new Error(event.message || "TXT 预处理 Worker 运行失败。")));
    };
    worker.postMessage({ content, title });
  });
}
