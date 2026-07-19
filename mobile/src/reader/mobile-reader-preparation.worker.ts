/// <reference lib="webworker" />

import { preparePlainTextSource } from "./mobile-reader-txt";

interface PrepareRequest {
  content: string;
  title: string;
}

interface PrepareResponse {
  source?: ReturnType<typeof preparePlainTextSource>;
  error?: string;
}

self.onmessage = (event: MessageEvent<PrepareRequest>) => {
  try {
    const source = preparePlainTextSource(event.data.content, event.data.title);
    self.postMessage({ source } satisfies PrepareResponse);
  } catch (error) {
    self.postMessage({
      error: error instanceof Error ? error.message : "TXT 预处理失败。"
    } satisfies PrepareResponse);
  }
};

export {};
