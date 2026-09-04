import { lazy, Suspense } from "react";
import { ShellPanel } from "@/components/ui";
import { useLibraryStore } from "@/stores/library-store";
import { TxtMarkdownReader } from "./reader/TxtMarkdownReader";

// 保留上层与测试引用的重导出，避免破坏已有的单元测试契约
export { splitTxtChapters } from "@/features/library/toc/txt-chapters";
export type { TxtChapter } from "@/features/library/toc/txt-chapters";

// EPUB 阅读器连带 epubjs 体量很大，按 format 懒加载，TXT/MD 不再为其付出下载与解析成本。
const EpubReaderPage = lazy(() =>
  import("@/features/library/EpubReaderPage").then((m) => ({ default: m.EpubReaderPage }))
);

export function ReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);

  if (activeBook?.format === "epub") {
    return (
      <Suspense
        fallback={
          <ShellPanel className="grid h-full place-items-center border-0 text-sm text-paper-muted">
            正在打开 EPUB...
          </ShellPanel>
        }
      >
        <EpubReaderPage />
      </Suspense>
    );
  }

  return <TxtMarkdownReader />;
}
