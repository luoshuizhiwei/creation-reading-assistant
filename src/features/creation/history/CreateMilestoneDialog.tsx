import { useEffect, useMemo, useState } from "react";
import { Button, Dialog, Select } from "@/components/ui";
import type {
  CardSummary,
  CreationProjectNavigation,
  CreationProjectOutline,
  SnapshotSubjectType
} from "@/types/creation";

export interface SelectableObject {
  type: SnapshotSubjectType;
  id: string;
  title: string;
}

interface CreateMilestoneDialogProps {
  projectId: string;
  navigation: CreationProjectNavigation | null | undefined;
  cards: CardSummary[];
  outline?: CreationProjectOutline | null | undefined;
  onCancel: () => void;
  onSubmit: (subjectType: SnapshotSubjectType, subjectId: string, reason: string) => Promise<boolean>;
  busy?: boolean;
}

const ENTITY_LABEL: Record<SnapshotSubjectType, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片"
};

export function CreateMilestoneDialog({
  navigation,
  cards,
  outline,
  onCancel,
  onSubmit,
  busy = false
}: CreateMilestoneDialogProps) {
  const [subjectType, setSubjectType] = useState<SnapshotSubjectType>("scene");
  const [subjectId, setSubjectId] = useState<string>("");
  const [reason, setReason] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const objects = useMemo<SelectableObject[]>(() => {
    if (subjectType === "scene") {
      const list: SelectableObject[] = [];
      if (navigation) {
        for (const chapter of navigation.chapters) {
          for (const scene of chapter.scenes) {
            list.push({ type: "scene", id: scene.id, title: scene.title });
          }
        }
      }
      return list;
    }
    if (subjectType === "volume") {
      return (outline?.volumes ?? []).map((volume) => ({
        type: "volume" as const,
        id: volume.id,
        title: volume.title
      }));
    }
    if (subjectType === "chapter") {
      return [
        ...(outline?.volumes ?? []).flatMap((volume) => volume.chapters),
        ...(outline?.looseChapters ?? [])
      ].map((chapter) => ({
        type: "chapter" as const,
        id: chapter.id,
        title: chapter.title
      }));
    }
    return cards.map((c) => ({ type: "card", id: c.id, title: c.title }));
  }, [subjectType, navigation, cards, outline]);

  useEffect(() => {
    setSubjectId("");
  }, [subjectType]);

  const reasonValid = reason.trim().length > 0;
  const canSubmit = !busy && !submitting && subjectId.length > 0 && reasonValid;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const ok = await onSubmit(subjectType, subjectId, reason.trim());
      if (ok) {
        onCancel();
      } else {
        setSubmitError("创建失败，请重试。");
      }
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "创建失败，请重试。");
    } finally {
      setSubmitting(false);
    }
  };

  const isBusy = busy || submitting;

  return (
    <Dialog
      open={true}
      title="创建命名里程碑"
      onClose={isBusy ? undefined : onCancel}
      width="max-w-md"
      className="history-modal"
      footer={
        <>
          <Button variant="outline" onClick={onCancel} disabled={isBusy}>
            取消
          </Button>
          <Button variant="primary" onClick={() => void handleSubmit()} disabled={!canSubmit}>
            {isBusy ? (
              <>
                <span className="history-busy" /> &nbsp;创建中…
              </>
            ) : (
              "创建里程碑"
            )}
          </Button>
        </>
      }
    >
          <Select
            id="history-subject-type"
            label="对象类型"
            value={subjectType}
            onChange={(e) => setSubjectType(e.target.value as SnapshotSubjectType)}
            disabled={isBusy}
          >
            <option value="volume">卷</option>
            <option value="chapter">章</option>
            <option value="scene">场景</option>
            <option value="card">卡片</option>
          </Select>

          {objects.length === 0 ? (
            <Select id="history-subject-id" label="选择对象" disabled>
              <option value="">暂无可用的{ENTITY_LABEL[subjectType]}</option>
            </Select>
          ) : (
            <Select
              id="history-subject-id"
              label="选择对象"
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
              disabled={isBusy}
            >
              <option value="">请选择一个{ENTITY_LABEL[subjectType]}</option>
              {objects.map((obj) => (
                <option key={obj.id} value={obj.id}>
                  {obj.title}
                </option>
              ))}
            </Select>
          )}

          <div className="history-field">
            <label htmlFor="history-reason">里程碑名称</label>
            <input
              id="history-reason"
              type="text"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="如：初稿完成、人物设定定稿"
              disabled={isBusy}
              maxLength={200}
            />
            {!reasonValid && reason.length > 0 && (
              <span className="history-field-error">名称不能为空</span>
            )}
          </div>

          {submitError && <p className="history-impact-error">{submitError}</p>}
    </Dialog>
  );
}
