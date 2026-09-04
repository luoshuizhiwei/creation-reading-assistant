import { useState } from "react";
import { FileDown } from "lucide-react";
import { Button, Dialog, Spinner } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { DraftExportPreset } from "@/types/creation";

interface ExportDraftDialogProps {
  projectId: string;
  projectTitle: string;
  onClose(): void;
}

const PRESET_OPTIONS: Array<{
  preset: DraftExportPreset;
  label: string;
  extension: string;
  description: string;
}> = [
  {
    preset: "platform-plain",
    label: "平台发布净文本",
    extension: ".txt",
    description: "卷/章标题与正文纯文本，不含场景标题、作者按、规划、引用与批注，适合直接发布。"
  },
  {
    preset: "standard-review",
    label: "标准审阅稿",
    extension: ".md",
    description: "Markdown 层级清晰（项目/卷/章/场景），作者按标注为「作者按」，引文与居中文本保留可读语义，不含引用与批注元数据。"
  }
];

export function ExportDraftDialog({ projectId, projectTitle, onClose }: ExportDraftDialogProps) {
  const { exportDraft } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [preset, setPreset] = useState<DraftExportPreset>("platform-plain");
  const [exporting, setExporting] = useState(false);

  const execute = async () => {
    setExporting(true);
    const result = await exportDraft(projectId, preset);
    setExporting(false);
    if (result.canceled || !result.filePath) return;
    showToast({ tone: "success", title: "已导出成稿", body: result.filePath });
    onClose();
  };

  return (
    <Dialog
      open={true}
      title={<span className="flex items-center gap-2"><FileDown size={16} /> 导出成稿</span>}
      ariaLabel="导出成稿"
      onClose={exporting ? undefined : onClose}
      width="max-w-md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => void execute()} disabled={exporting}>
            {exporting ? <><Spinner size={14} className="mr-1.5" /> 导出中…</> : "导出"}
          </Button>
        </>
      }
    >
      <p className="migration-note">
            选择成稿预设：「{projectTitle}」将导出为新文件，不会改动项目内容。
          </p>
          <div className="export-preset-list">
            {PRESET_OPTIONS.map((option) => (
              <label
                key={option.preset}
                className={`export-preset-option${preset === option.preset ? " export-preset-option--active" : ""}`}
              >
                <input
                  type="radio"
                  name="export-preset"
                  value={option.preset}
                  checked={preset === option.preset}
                  onChange={() => setPreset(option.preset)}
                />
                <span className="export-preset-option-head">
                  <strong>{option.label}</strong>
                  <em>{option.extension}</em>
                </span>
                <span className="export-preset-option-desc">{option.description}</span>
              </label>
            ))}
          </div>
    </Dialog>
  );
}
