import { useEffect, useState } from "react";
import { Bookmark, Copy, Inbox as InboxIcon, X } from "lucide-react";
import { Button } from "@/components/ui";
import type { ExcerptResult, ExcerptSourceSnapshot, ExcerptTarget } from "@/types/library";

interface ExcerptPickerProps {
  source: ExcerptSourceSnapshot;
  projects: Array<{ id: string; title: string }>;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (target: ExcerptTarget) => Promise<ExcerptResult>;
  onResult: (result: ExcerptResult, target: ExcerptTarget) => void;
}

/**
 * 资料摘录选择器。
 *
 * 展示选文预览、来源信息和目标选择（全局收件箱 / 项目资料卡）。
 * 提交成功/失败通过 onResult 回调通知调用方展示 toast。
 * 失败时不关闭弹窗，用户可重试或取消。
 */
export function ExcerptPicker({ source, projects, isSubmitting, onClose, onSubmit, onResult }: ExcerptPickerProps) {
  const [targetKind, setTargetKind] = useState<"inbox" | "projectCard">("inbox");
  const [projectId, setProjectId] = useState<string>(projects[0]?.id ?? "");

  // 项目列表异步加载后自动选择首个项目（首次打开时 projects 为空）
  useEffect(() => {
    if (projects.length > 0 && !projectId) {
      setProjectId(projects[0].id);
    }
  }, [projects, projectId]);

  const canSubmitProject = targetKind !== "projectCard" || Boolean(projectId);

  const handleSubmit = async () => {
    const target: ExcerptTarget =
      targetKind === "inbox" ? { kind: "inbox" } : { kind: "projectCard", projectId };
    if (target.kind === "projectCard" && !projectId) return;
    const result = await onSubmit(target);
    onResult(result, target);
    if (result.success) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-paper-ink/30 backdrop-blur-[1px]" onMouseDown={onClose}>
      <aside
        className="motion-panel relative flex max-h-[80vh] w-[min(560px,calc(100%-32px))] flex-col overflow-hidden rounded-xl border border-paper-line bg-paper-panel shadow-paper"
        onMouseDown={(event) => event.stopPropagation()}
        role="dialog"
        aria-label="资料摘录"
      >
        <header className="flex items-center justify-between border-b border-paper-line px-5 py-3">
          <div className="flex items-center gap-2">
            <Bookmark size={16} className="text-copper" />
            <h2 className="paper-title text-base font-semibold">摘录到资料</h2>
          </div>
          <button className="rounded-md p-1.5 text-paper-muted hover:bg-paper-soft hover:text-paper-ink" onClick={onClose} aria-label="关闭">
            <X size={16} />
          </button>
        </header>

        <div className="flex flex-col gap-4 overflow-auto px-5 py-4">
          {/* 来源信息 */}
          <div className="rounded-lg border border-paper-line bg-paper-soft/50 px-3 py-2 text-xs leading-5 text-paper-muted">
            <div className="font-medium text-paper-ink">{source.bookTitle}</div>
            <div className="mt-0.5">
              {source.format.toUpperCase()} · {source.locationLabel}
              {source.bookAuthor ? ` · ${source.bookAuthor}` : ""}
            </div>
          </div>

          {/* 选文预览 */}
          <div>
            <div className="mb-1 text-xs font-medium text-paper-muted">选文预览</div>
            <div className="max-h-40 overflow-auto rounded-lg border border-paper-line bg-white/60 px-3 py-2 text-sm leading-6 text-paper-ink">
              {source.excerpt}
            </div>
          </div>

          {/* 目标选择 */}
          <div>
            <div className="mb-2 text-xs font-medium text-paper-muted">摘录到</div>
            <div className="flex flex-col gap-2">
              <label className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition ${targetKind === "inbox" ? "border-copper bg-copper/5 text-paper-ink" : "border-paper-line text-paper-muted hover:bg-paper-soft/50"}`}>
                <input
                  type="radio"
                  name="excerpt-target"
                  checked={targetKind === "inbox"}
                  onChange={() => setTargetKind("inbox")}
                  className="accent-copper"
                />
                <InboxIcon size={15} />
                <span>全局收件箱（保存完整选文和来源）</span>
              </label>
              <label className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition ${targetKind === "projectCard" ? "border-copper bg-copper/5 text-paper-ink" : "border-paper-line text-paper-muted hover:bg-paper-soft/50"}`}>
                <input
                  type="radio"
                  name="excerpt-target"
                  checked={targetKind === "projectCard"}
                  onChange={() => setTargetKind("projectCard")}
                  className="accent-copper"
                />
                <Copy size={15} />
                <span>项目资料卡</span>
              </label>
              {targetKind === "projectCard" && (
                <select
                  className="paper-input h-9 ml-7"
                  value={projectId}
                  onChange={(event) => setProjectId(event.target.value)}
                  disabled={projects.length === 0}
                >
                  {projects.length === 0 ? (
                    <option value="">暂无可用项目</option>
                  ) : (
                    projects.map((project) => (
                      <option key={project.id} value={project.id}>{project.title}</option>
                    ))
                  )}
                </select>
              )}
            </div>
          </div>
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-paper-line px-5 py-3">
          <Button variant="quiet" onClick={onClose} disabled={isSubmitting}>取消</Button>
          <Button
            variant="secondary"
            onClick={() => void handleSubmit()}
            disabled={isSubmitting || !canSubmitProject}
          >
            {isSubmitting ? "保存中..." : "保存摘录"}
          </Button>
        </footer>
      </aside>
    </div>
  );
}
