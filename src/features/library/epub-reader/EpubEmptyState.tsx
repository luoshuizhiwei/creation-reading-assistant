import { ArrowLeft } from "lucide-react";
import { Button, ShellPanel } from "@/components/ui";

export interface EpubEmptyStateProps {
  onBackToLibrary(): void;
  onBackToHome(): void;
}

export function EpubEmptyState({ onBackToLibrary, onBackToHome }: EpubEmptyStateProps) {
  return (
    <ShellPanel className="h-full border-0">
      <div className="grid h-full place-items-center px-8 py-10 text-center" role="status" aria-live="polite">
        <div className="max-w-sm">
          <h2 className="paper-title text-base font-semibold">没有打开 EPUB</h2>
          <p className="mt-2 text-sm leading-6 text-paper-muted">从书库中选择一份 EPUB，即可浏览目录、复制选文或摘录到资料卡。</p>
          <div className="mt-5 flex justify-center gap-2">
            <Button variant="primary" onClick={onBackToLibrary}>
              <ArrowLeft size={16} />
              返回书库
            </Button>
            <Button variant="quiet" onClick={onBackToHome}>
              返回首页
            </Button>
          </div>
        </div>
      </div>
    </ShellPanel>
  );
}
