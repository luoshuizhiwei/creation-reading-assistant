import { Button, Dialog } from "@/components/ui";

interface PastePreviewDialogProps {
  plainText: string;
  reason?: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}

export function PastePreviewDialog({ plainText, reason, onCancel, onConfirm }: PastePreviewDialogProps) {
  return (
    <Dialog
      open={true}
      title="粘贴内容预览"
      onClose={onCancel}
      width="max-w-lg"
      footer={
        <>
          <Button variant="secondary" onClick={onCancel}>
            取消
          </Button>
          <Button onClick={onConfirm}>
            插入纯文本
          </Button>
        </>
      }
    >
      <p className="paste-preview-note">
        {reason ? `${reason}。` : "检测到复杂或大段内容。"}当前版本会按纯文本插入，移除格式、图片与链接。
      </p>
      <pre className="paste-preview-body mt-3">{plainText}</pre>
    </Dialog>
  );
}
