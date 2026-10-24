import { useEffect, useRef, useState } from "react";
import { Bookmark, Copy, Inbox as InboxIcon, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useModalKeyboard } from "@/components/ui/use-modal-keyboard";
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
  const dialogRef = useRef<HTMLElement>(null);

  // BD-131：全屏弹层此前没有任何键盘契约——Esc 不关、焦点留在正文上、Tab 可直接
  // 穿到弹层后的阅读器控件。组件只在打开时挂载，active 恒为 true。
  const handleModalKeyDown = useModalKeyboard({
    active: true,
    containerRef: dialogRef,
    onEscape: onClose
  });

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
        ref={dialogRef}
        onKeyDown={handleModalKeyDown}
        className="motion-panel relative flex max-h-[80vh] w-[min(560px,calc(100%-32px))] flex-col overflow-hidden rounded-[var(--radius-3)] border border-paper-line bg-paper-panel [box-shadow:var(--shadow-3)]"
        onMouseDown={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
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
          <div className="rounded-md border border-paper-line bg-paper-soft/50 px-3 py-2 text-xs leading-[var(--leading-12)] text-paper-muted">
            <div className="font-medium text-paper-ink">{source.bookTitle}</div>
            <div className="mt-0.5">
              {source.format.toUpperCase()} · {source.locationLabel}
              {source.bookAuthor ? ` · ${source.bookAuthor}` : ""}
            </div>
          </div>

          {/* 选文预览 */}
          <div>
            <div className="mb-1 text-xs font-medium text-paper-muted">选文预览</div>
            <div className="max-h-40 overflow-auto rounded-md border border-paper-line bg-white/60 px-3 py-2 text-sm leading-[var(--leading-14)] text-paper-ink">
              {source.excerpt}
            </div>
          </div>

          {/* 目标选择 */}
          <div>
            <div className="mb-2 text-xs font-medium text-paper-muted">摘录到</div>
            <div className="flex flex-col gap-2">
              <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm transition ${targetKind === "inbox" ? "border-copper bg-copper/5 text-paper-ink" : "border-paper-line text-paper-muted hover:bg-paper-soft/50"}`}>
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
              <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm transition ${targetKind === "projectCard" ? "border-copper bg-copper/5 text-paper-ink" : "border-paper-line text-paper-muted hover:bg-paper-soft/50"}`}>
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
                /* 批次 BD-71（28px 桶·摘录目标 select 缩进）：ml-7→ml-8（28→32）。
                   28 是 24/32 正中平票，就近不裁决 ⇒ 判据是它该缩进到哪一条列线上。
                   父 label 是 `px-3 py-2` + `gap-2`，内含 radio(13px) / 图标(15px) / 文字：
                   列 x421 → 内衬 12 起 → radio 434–447 → 间隙 8 → 图标 455–470 → 间隙 8 →
                   文字 478。即选项的「内容列」（图标起点）在列首 +34，刻度的最近档是 32。
                   真宿主实拍（/tmp/bd71-excerpt2.json，阅读器选中→`title="摘录到资料"`→
                   切「项目资料卡」，1360 窗）：ml 28 → select x449 = 列首 +28，与图标列
                   差 6px、与 radio 右缘只差 2px，哪条线都没对齐；ml 24 → x445，**左缘切进
                   radio 自己的盒**(434–447) 里 2px；ml 32 → x453，与图标列差 2px，是全三档
                   里唯一落在选项内容带内的。
                   三档下 select 右缘恒为 939（= 父 label 右缘），宽 490/494/486 随左缩进
                   让出，高恒 32、radio 与图标位置全不动（/tmp/bd71-ml-24|28|32.png 三帧
                   目视一致），即本改动只把嵌套控件推回选项内容带，不牵动任何兄弟。 */
                <select
                  className="paper-input h-8 ml-8"
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
