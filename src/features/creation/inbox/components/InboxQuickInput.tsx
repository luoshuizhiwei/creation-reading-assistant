import { useState, type FormEvent, type KeyboardEvent } from "react";
import { Plus, Send } from "lucide-react";
import { Button } from "@/components/ui";
import { InboxTargetProjectSelect } from "./InboxConvertToCardDialog";
import { TYPE_LABELS } from "./types";
import type { CreationProjectSummary } from "@/types/creation";
import type { InspirationType } from "@/types/inspiration";

export interface InboxQuickInputProps {
  projectId?: string;
  projects: CreationProjectSummary[];
  targetProjectId: string;
  onTargetProjectChange: (projectId: string) => void;
  onCreateNew: () => void;
  onQuickCreate?: (title: string, kind: InspirationType) => Promise<void> | void;
}

const QUICK_TYPES: InspirationType[] = ["note", "plot", "character", "scene", "line"];

/**
 * 顶部快速灵感输入框与快捷录入区
 * 包含：文本输入、分类快速切换、回车即存、新建想法按钮以及目标项目选择器
 */
export function InboxQuickInput({
  projectId,
  projects,
  targetProjectId,
  onTargetProjectChange,
  onCreateNew,
  onQuickCreate
}: InboxQuickInputProps) {
  const [quickTitle, setQuickTitle] = useState("");
  const [selectedType, setSelectedType] = useState<InspirationType>("note");
  const [submitting, setSubmitting] = useState(false);

  const handleQuickSubmit = async (e?: FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = quickTitle.trim();
    if (!trimmed || submitting) return;
    if (onQuickCreate) {
      setSubmitting(true);
      try {
        await onQuickCreate(trimmed, selectedType);
        setQuickTitle("");
      } finally {
        setSubmitting(false);
      }
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleQuickSubmit();
    }
  };

  return (
    <section className="inbox-toolbar">
      <div className="flex flex-1 items-center gap-2 max-w-xl">
        <div className="relative flex flex-1 items-center">
          <input
            type="text"
            className="paper-input h-8 w-full pr-14 text-xs placeholder:text-paper-muted"
            value={quickTitle}
            onChange={(e) => setQuickTitle(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="记下灵感碎片，回车速记…"
            disabled={submitting}
          />
          {quickTitle.trim() && (
            <button
              type="button"
              className="absolute right-1.5 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-copper hover:bg-paper-soft"
              onClick={() => void handleQuickSubmit()}
              disabled={submitting}
              title="存入收件箱"
            >
              <Send size={11} /> 存入
            </button>
          )}
        </div>

        <div className="hidden sm:flex items-center gap-1" role="group" aria-label="快捷分类">
          {QUICK_TYPES.map((t) => (
            <button
              key={t}
              type="button"
              className={`rounded-full px-2 py-0.5 text-[11px] transition ${
                selectedType === t
                  ? "bg-copper text-white font-medium"
                  : "bg-paper-soft/60 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
              }`}
              onClick={() => setSelectedType(t)}
            >
              {TYPE_LABELS[t]}
            </button>
          ))}
        </div>
      </div>

      <div className="inbox-hero-actions">
        <Button variant="outline" size="sm" onClick={onCreateNew}>
          <Plus size={13} /> 新建想法
        </Button>
        {!projectId && projects.length > 0 && (
          <InboxTargetProjectSelect
            projects={projects}
            targetProjectId={targetProjectId}
            onChange={onTargetProjectChange}
          />
        )}
      </div>
    </section>
  );
}
