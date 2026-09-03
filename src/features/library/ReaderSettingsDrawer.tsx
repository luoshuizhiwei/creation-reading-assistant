import { X } from "lucide-react";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { resetReaderSettings } from "@/services/settings-service";
import { updateReaderSettings } from "@/services/reader-service";
import type { ReaderSettings } from "@/types/library";

/**
 * TXT/Markdown 阅读器的设置抽屉。与 EPUB 的 EpubSettingsDrawer 同构：
 * 设置不再与目录面板堆叠在同一右列，而是由顶栏按钮唤起的独立覆盖层。
 */
export interface ReaderSettingsDrawerProps {
  open: boolean;
  settings: ReaderSettings;
  onClose(): void;
  onSettingsChange(next: ReaderSettings): void;
  onAfterChange(): void;
}

export function ReaderSettingsDrawer({ open, settings, onClose, onSettingsChange, onAfterChange }: ReaderSettingsDrawerProps) {
  if (!open) return null;
  return (
    <div className="absolute inset-0 z-30 bg-paper-ink/10 backdrop-blur-[1px]" onMouseDown={onClose}>
      <aside
        className="motion-drawer absolute right-0 top-0 h-full w-[360px] overflow-auto border-l border-paper-line bg-paper-panel p-5 shadow-paper"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <div>
            <div className="paper-title text-lg font-semibold">阅读设置</div>
            <div className="mt-1 text-xs text-paper-muted">调整字号、行距与背景，设置实时生效。</div>
          </div>
          <button className="rounded-md p-2 text-paper-muted hover:bg-paper-soft hover:text-paper-ink" onClick={onClose}>
            <X size={17} />
          </button>
        </div>
        <ReaderSettingsPanel
          settings={settings}
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
      </aside>
    </div>
  );
}
