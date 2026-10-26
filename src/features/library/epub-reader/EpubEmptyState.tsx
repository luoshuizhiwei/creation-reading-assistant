import { ArrowLeft } from "lucide-react";
import { Button, ShellPanel } from "@/components/ui";

export interface EpubEmptyStateProps {
  onBackToLibrary(): void;
  onBackToHome(): void;
}

export function EpubEmptyState({ onBackToLibrary, onBackToHome }: EpubEmptyStateProps) {
  // BD-194：同 EmptyState 的同款死装饰——挂载即带内容，polite 从不播；
  // 且这块里有「返回书库/返回首页」两颗按钮，带交互的容器本不该整体当
  // status（BD-191 判据：按钮会被卷进播报文本）。退回纯视觉。
  return (
    <ShellPanel className="h-full border-0">
      <div className="grid h-full place-items-center px-8 py-10 text-center">
        <div className="max-w-sm">
          <h2 className="paper-title text-base font-semibold">没有打开 EPUB</h2>
          <p className="mt-2 text-sm leading-[var(--leading-14)] text-paper-muted">从书库中选择一份 EPUB，即可浏览目录、复制选文或摘录到资料卡。</p>
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
