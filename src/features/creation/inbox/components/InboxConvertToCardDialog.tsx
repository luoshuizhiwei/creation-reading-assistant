import { useState, useEffect } from "react";
import { Button, Dialog, Select } from "@/components/ui";
import { Library } from "lucide-react";
import type { CreationProjectSummary, InboxItem } from "@/types/creation";
import type { InspirationType } from "@/types/inspiration";
import { TYPE_LABELS } from "./types";

export interface InboxTargetProjectSelectProps {
  projects: CreationProjectSummary[];
  targetProjectId: string;
  onChange: (projectId: string) => void;
}

/**
 * 顶部工具栏中的目标项目快速选择器
 */
export function InboxTargetProjectSelect({
  projects,
  targetProjectId,
  onChange
}: InboxTargetProjectSelectProps) {
  return (
    <label className="inbox-target-project">
      <span>转为资料卡的目标项目</span>
      <Select
        value={targetProjectId || projects[0]?.id || ""}
        onChange={(event) => onChange(event.target.value)}
      >
        {projects.map((project) => (
          <option key={project.id} value={project.id}>
            {project.title}
          </option>
        ))}
      </Select>
    </label>
  );
}

export interface InboxConvertToCardDialogProps {
  open: boolean;
  item: InboxItem | null;
  projects: CreationProjectSummary[];
  targetProjectId: string;
  onTargetProjectChange: (projectId: string) => void;
  onConfirm: (item: InboxItem, projectId: string, cardType?: string) => Promise<void> | void;
  onClose: () => void;
  busy?: boolean;
}

/**
 * 灵感转为创作资料卡（Convert to Card）弹窗
 * 支持选择目标项目与卡片类型映射
 */
export function InboxConvertToCardDialog({
  open,
  item,
  projects,
  targetProjectId,
  onTargetProjectChange,
  onConfirm,
  onClose,
  busy = false
}: InboxConvertToCardDialogProps) {
  const [selectedType, setSelectedType] = useState<string>("note");

  useEffect(() => {
    if (item) {
      setSelectedType(item.type || "note");
    }
  }, [item]);

  if (!item) return null;

  const handleConfirm = async () => {
    const effectiveProjectId = targetProjectId || projects[0]?.id || "";
    await onConfirm(item, effectiveProjectId, selectedType);
  };

  return (
    <Dialog
      open={open}
      onClose={busy ? undefined : onClose}
      title="转为创作资料卡"
      width="max-w-md"
      dataTestId="inbox-convert-card-dialog"
      footer={
        <div className="flex items-center justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={busy}>
            取消
          </Button>
          <Button onClick={() => void handleConfirm()} disabled={busy || projects.length === 0}>
            <Library size={14} />
            {busy ? "正在转卡…" : "确认转卡"}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="rounded-lg border border-paper-line bg-paper-soft/40 p-3">
          <div className="text-xs font-semibold text-paper-muted">待转卡灵感条目</div>
          <div className="mt-1 font-medium text-paper-ink">{item.title}</div>
          {item.body && (
            <div className="mt-1 line-clamp-2 text-xs text-paper-muted">{item.body}</div>
          )}
        </div>

        {projects.length === 0 ? (
          <p className="text-sm text-red-500">
            当前还没有创作项目，请先创建项目后再转为资料卡。
          </p>
        ) : (
          <div className="space-y-3">
            <Select
              label="目标项目"
              value={targetProjectId || projects[0]?.id || ""}
              onChange={(e) => onTargetProjectChange(e.target.value)}
              disabled={busy}
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </Select>

            <Select
              label="资料卡类型"
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
              disabled={busy}
            >
              {(Object.keys(TYPE_LABELS) as InspirationType[]).map((typeKey) => (
                <option key={typeKey} value={typeKey}>
                  {TYPE_LABELS[typeKey]}
                </option>
              ))}
              {!TYPE_LABELS[selectedType as InspirationType] && (
                <option value={selectedType}>{selectedType}</option>
              )}
            </Select>
          </div>
        )}

        <p className="text-xs leading-5 text-paper-muted">
          转为资料卡后，条目在收件箱中将标记为「已转卡片」，并原子写入指定创作项目的资料库中。
        </p>
      </div>
    </Dialog>
  );
}
