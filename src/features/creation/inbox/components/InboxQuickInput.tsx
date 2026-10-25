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
          {/* 批次 BD-81（56px 桶·记档）：`pr-14` = 56px 是右侧「存入」浮钮的让位槽，
              不是节奏值，故不入刻度、原样保留（值账 `InboxQuickInput.tsx pr 56px ⑉ 1`
              与预算 1 都不动）。实拍（/tmp/bd81-census.json，1360 窗收件箱，输入一行
              长文让浮钮出现）：输入框 314.28×32、浮钮 51.02×20、右缩进 6 —— 占用面
              = 51.02 + 6 = 57.02px ⇒ 正文可延伸到的右界（x480.28）比钮盒左缘
              （x479.27）只多 1.02px，而钮自身 px-1.5 内衬 6px 把墨迹隔在 x485.27 之后，
              目视两档截图上文字尾与「◇ 存入」之间仍有空隙（shots pr56）。
              收 48 会把正文右界推到 x488.28——比钮文字起点还右 3.01px，长文直接压到
              图标上（shots pr48 实拍）；收 64 只是把空隙再拉 8px，不多换一分可读性。
              刻度上限 48 本身够不着 57.02 这个功能净空，没有既能上刻度又不压钮的取值，
              故记档。窄窗 1024 同一处生效（computed 仍 pr 56、盒 314.28×32）。 */}
          <input
            type="text"
            className="paper-input h-8 w-full pr-14 text-xs placeholder:text-paper-muted"
            aria-label="灵感速记"
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
              aria-pressed={selectedType === t}
              className={`rounded-full px-2 py-0.5 text-[11px] transition ${
                selectedType === t
                  ? "bg-copper text-[color:var(--fg-on-solid)] font-medium"
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
