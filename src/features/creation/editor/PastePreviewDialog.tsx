import { useEffect, useRef } from "react";
import { Button } from "@/components/ui";

interface PastePreviewDialogProps {
  plainText: string;
  reason?: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}

export function PastePreviewDialog({ plainText, reason, onCancel, onConfirm }: PastePreviewDialogProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  return (
    <div className="paste-preview-overlay" onClick={onCancel}>
      <section
        className="paste-preview-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="paste-preview-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="paste-preview-head">
          <p className="desktop-card-label">Paste preview</p>
          <h2 id="paste-preview-title">粘贴内容预览</h2>
        </header>
        <p className="paste-preview-note">
          {reason ? `${reason}。` : "检测到复杂或大段内容。"}当前版本会按纯文本插入，移除格式、图片与链接。
        </p>
        <pre className="paste-preview-body">{plainText}</pre>
        <footer className="paste-preview-actions">
          <Button variant="secondary" onClick={onCancel}>
            取消
          </Button>
          <Button ref={confirmRef} onClick={onConfirm}>
            插入纯文本
          </Button>
        </footer>
      </section>
    </div>
  );
}
