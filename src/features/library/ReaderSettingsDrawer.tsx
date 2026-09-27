import { useEffect, useState } from "react";
import { ChevronLeft, Settings2, X } from "lucide-react";
import { ReaderSettingsMorePanel } from "@/features/library/ReaderSettingsMorePanel";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { resetReaderSettings } from "@/services/settings-service";
import { updateReaderSettings } from "@/services/reader-service";
import type { ReaderSettings } from "@/types/library";

/**
 * TXT/Markdown 阅读器的设置抽屉。与 EPUB 的 EpubSettingsDrawer 同构：
 * 设置不再与目录面板堆叠在同一右列，而是由顶栏按钮唤起的独立覆盖层。
 *
 * 主视图只放排版高频项；「更多设置」在抽屉内切换到二级视图
 * （不离开阅读页），承载 EPUB 样式、繁简转换、阅读记录等低频项。
 */
export interface ReaderSettingsDrawerProps {
  open: boolean;
  settings: ReaderSettings;
  onClose(): void;
  onSettingsChange(next: ReaderSettings): void;
  onAfterChange(): void;
  format?: "txt" | "md" | "epub";
  subtitle?: string;
}

export function ReaderSettingsDrawer({
  open,
  settings,
  onClose,
  onSettingsChange,
  onAfterChange,
  format = "txt",
  subtitle = format === "epub" ? "EPUB 默认保留原书样式，需要统一排版时再切换。" : "调整字号、行距与背景，设置实时生效。"
}: ReaderSettingsDrawerProps) {
  const [view, setView] = useState<"main" | "more">("main");

  useEffect(() => {
    if (open) setView("main");
  }, [open]);

  if (!open) return null;
  return (
    <div className="absolute inset-0 z-30 bg-paper-ink/10 backdrop-blur-[1px]" onMouseDown={onClose}>
      <aside
        className="motion-drawer absolute right-0 top-0 h-full w-[320px] overflow-auto border-l border-paper-line bg-paper-panel p-5 shadow-paper"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <div className="flex min-w-0 items-center gap-1">
            {view === "more" && (
              <button
                type="button"
                className="rounded-md p-1.5 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
                aria-label="返回阅读设置"
                onClick={() => setView("main")}
              >
                <ChevronLeft size={17} />
              </button>
            )}
            <div className="min-w-0">
              <div className="paper-title text-lg font-semibold">{view === "main" ? "阅读设置" : "更多设置"}</div>
              {view === "main" && <div className="mt-1 truncate text-xs text-paper-muted">{subtitle}</div>}
            </div>
          </div>
          <button
            type="button"
            className="rounded-md p-2 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
            aria-label="关闭"
            onClick={onClose}
          >
            <X size={17} />
          </button>
        </div>

        {view === "main" ? (
          <>
            <ReaderSettingsPanel
              settings={settings}
              onChange={async (patch) => {
                const next = await updateReaderSettings(patch);
                onSettingsChange(next);
                onAfterChange();
              }}
            />
            <button
              type="button"
              className="mt-5 flex w-full items-center justify-center gap-2 rounded-lg border border-paper-line bg-paper-soft/40 py-2 text-sm text-paper-muted transition hover:border-copper/50 hover:text-paper-ink"
              onClick={() => setView("more")}
            >
              <Settings2 size={15} />
              更多设置
            </button>
          </>
        ) : (
          <ReaderSettingsMorePanel
            settings={settings}
            format={format}
            onReset={async () => {
              const next = await resetReaderSettings();
              onSettingsChange(next.reader);
              onAfterChange();
            }}
            onChange={async (patch) => {
              const next = await updateReaderSettings(patch);
              onSettingsChange(next);
              onAfterChange();
            }}
          />
        )}
      </aside>
    </div>
  );
}
