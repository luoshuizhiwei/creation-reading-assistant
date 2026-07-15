import { useEffect, useRef } from "react";

export function ConfirmDialog({
  title,
  message,
  onConfirm,
  onCancel
}: {
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onCancel();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  useEffect(() => {
    confirmRef.current?.focus();
  }, []);

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog-content" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-message" onClick={(e) => e.stopPropagation()}>
        <h3 id="confirm-dialog-title" className="dialog-title">{title}</h3>
        <p id="confirm-dialog-message" className="dialog-message">{message}</p>
        <div className="dialog-actions">
          <button className="dialog-button cancel" onClick={onCancel}>
            取消
          </button>
          <button
            ref={confirmRef}
            className="dialog-button confirm"
            onClick={() => {
              onConfirm();
              onCancel();
            }}
          >
            确认
          </button>
        </div>
      </div>
    </div>
  );
}
