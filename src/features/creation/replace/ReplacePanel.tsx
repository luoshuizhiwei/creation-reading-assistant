import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Button, Tabs } from "@/components/ui";
import { ReplacePlanView } from "./ReplacePlanView";
import { ReplaceProgressDialog } from "./ReplaceProgressDialog";
import { createReplacePlanService } from "./replace-service";
import type {
  ReplaceApplyResultView,
  ReplaceErrorView,
  ReplacePlanProgress,
  ReplacePlanService,
  ReplacePlanView as PlanView
} from "./types";
import "./replace.css";

interface ReplacePanelProps {
  projectId: string;
  chapterId?: string;
  sceneId?: string;
  onClose: () => void;
  service?: ReplacePlanService;
}

type PanelPhase = "idle" | "previewing" | "applying" | "done" | "error";

interface PreviewQuery {
  find: string;
  replaceWith: string;
  mode: "plain" | "regex";
}

function toErrorView(error: unknown): ReplaceErrorView {
  if (
    error &&
    typeof error === "object" &&
    "code" in error &&
    typeof (error as ReplaceErrorView).code === "string"
  ) {
    const candidate = error as ReplaceErrorView;
    return { code: candidate.code, message: candidate.message ?? String(error) };
  }
  return { code: "transaction-failed", message: error instanceof Error ? error.message : String(error) };
}

export function ReplacePanel({ projectId, chapterId, sceneId, onClose, service }: ReplacePanelProps) {
  const svc = useMemo(() => service ?? createReplacePlanService(), [service]);
  const scope = sceneId ? "scene" : chapterId ? "chapter" : "all";
  const scopeId = sceneId ?? chapterId;

  const [query, setQuery] = useState<PreviewQuery>({ find: "", replaceWith: "", mode: "plain" });
  const [plan, setPlan] = useState<PlanView | null>(null);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  const [progress, setProgress] = useState<ReplacePlanProgress | null>(null);
  const [phase, setPhase] = useState<PanelPhase>("idle");
  const [error, setError] = useState<ReplaceErrorView | null>(null);
  const [result, setResult] = useState<ReplaceApplyResultView | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  // 浮动面板 top 跟随页面 hero 底部（写作台操作行会换行、宽度变化会变高），
  // 保证面板永不遮住 hero 里的查找/校对/导出等操作按钮。
  const [floatingTop, setFloatingTop] = useState(96);
  const findInputRef = useRef<HTMLInputElement>(null);

  // BD-176：面板打开后焦点此前留在触发钮上，键盘用户得把整页 Tab 一遍才够得到"查找"；
  // Esc 也不关。桌面浮层的惯例是焦点直接落进主输入框、Esc 收起、关闭后焦点回到原处。
  useEffect(() => {
    const restoreTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = requestAnimationFrame(() => findInputRef.current?.focus());
    return () => {
      cancelAnimationFrame(frame);
      if (restoreTo && restoreTo.isConnected) restoreTo.focus({ preventScroll: true });
    };
  }, []);

  const handlePanelKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    // 输入法组合中的 Esc 是取消候选词，不能当成关闭面板。
    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
    if (event.key !== "Escape") return;
    event.preventDefault();
    event.stopPropagation();
    onClose();
  };

  useLayoutEffect(() => {
    const measure = (): void => {
      const hero = document.querySelector(".desktop-page-hero");
      const next = hero ? Math.max(96, Math.ceil(hero.getBoundingClientRect().bottom) + 16) : 96;
      setFloatingTop((prev) => (prev === next ? prev : next));
    };
    measure();
    window.addEventListener("resize", measure);
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== "undefined") {
      const hero = document.querySelector(".desktop-page-hero");
      if (hero) {
        observer = new ResizeObserver(measure);
        observer.observe(hero);
      }
    }
    return () => {
      window.removeEventListener("resize", measure);
      observer?.disconnect();
    };
  }, []);

  const remaining = plan ? plan.totalHits - excluded.size : 0;
  const busy = phase === "previewing" || phase === "applying";

  const handlePreview = async (): Promise<void> => {
    if (!query.find) {
      setError({ code: "invalid-input", message: "查找内容不能为空。" });
      setPhase("error");
      return;
    }
    const controller = new AbortController();
    abortRef.current = controller;
    setPlan(null);
    setExcluded(new Set());
    setResult(null);
    setError(null);
    setProgress(null);
    setPhase("previewing");
    try {
      const created = await svc.createPlan(
        { projectId, scope, scopeId, find: query.find, replaceWith: query.replaceWith, mode: query.mode },
        { onProgress: setProgress, signal: controller.signal }
      );
      setPlan(created);
      setPhase("idle");
    } catch (e) {
      const err = toErrorView(e);
      if (err.code === "cancelled") {
        setPhase("idle");
      } else {
        setError(err);
        setPhase("error");
      }
    } finally {
      abortRef.current = null;
    }
  };

  const handleApply = async (): Promise<void> => {
    if (!plan) return;
    setError(null);
    setResult(null);
    setPhase("applying");
    try {
      const applied = await svc.applyPlan(plan.planId, [...excluded]);
      setResult(applied);
      setPlan(null);
      setExcluded(new Set());
      setPhase("done");
    } catch (e) {
      const err = toErrorView(e);
      if (err.code === "cancelled") {
        setPhase("idle");
      } else {
        setError(err);
        setPhase("error");
      }
    }
  };

  const handleCancel = (): void => {
    abortRef.current?.abort();
    svc.cancel();
  };

  const handleToggleHit = (hitId: string): void => {
    setExcluded((prev) => {
      const next = new Set(prev);
      if (next.has(hitId)) next.delete(hitId);
      else next.add(hitId);
      return next;
    });
  };

  const handleToggleScene = (sceneIdArg: string, hitIds: string[], excludeAll: boolean): void => {
    setExcluded((prev) => {
      const next = new Set(prev);
      for (const id of hitIds) {
        if (excludeAll) next.add(id);
        else next.delete(id);
      }
      void sceneIdArg;
      return next;
    });
  };

  const handleReset = (): void => {
    setPlan(null);
    setExcluded(new Set());
    setError(null);
    setResult(null);
    setProgress(null);
    setPhase("idle");
  };

  const scopeLabel = scope === "scene" ? "当前场景" : scope === "chapter" ? "当前章节" : "整个项目";

  return (
    <div
      className="replace-panel"
      data-testid="replace-panel"
      onKeyDown={handlePanelKeyDown}
      style={{ top: floatingTop, maxHeight: `calc(100vh - ${floatingTop + 20}px)` }}
    >
      <header className="replace-panel-header">
        <h2>查找替换</h2>
        <span className="replace-scope-label" data-testid="replace-scope">
          范围：{scopeLabel}
        </span>
        <Button variant="outline" onClick={onClose} aria-label="关闭" data-testid="replace-panel-close">
          关闭
        </Button>
      </header>

      <div className="replace-query-form">
        <label>
          查找
          <input
            ref={findInputRef}
            type="text"
            value={query.find}
            aria-label="查找内容"
            data-testid="replace-find"
            aria-invalid={error?.code === "invalid-input"}
            aria-describedby={error?.code === "invalid-input" ? "replace-error-banner" : undefined}
            onChange={(e) => setQuery((q) => ({ ...q, find: e.target.value }))}
          />
        </label>
        <label>
          替换为
          <input
            type="text"
            value={query.replaceWith}
            aria-label="替换内容"
            data-testid="replace-replace"
            onChange={(e) => setQuery((q) => ({ ...q, replaceWith: e.target.value }))}
          />
        </label>
        <Tabs<"plain" | "regex">
          variant="pill"
          value={query.mode}
          onChange={(mode) => setQuery((q) => ({ ...q, mode }))}
          items={[
            { id: "plain", label: <span data-testid="replace-mode-plain">普通文本</span> },
            { id: "regex", label: <span data-testid="replace-mode-regex">正则表达式</span> }
          ]}
          ariaLabel="替换模式"
        />
        <Button onClick={() => void handlePreview()} disabled={busy} data-testid="replace-preview">
          预览替换
        </Button>
      </div>

      {error && phase === "error" && (
        <div className="replace-error-banner" role="alert" id="replace-error-banner" data-testid="replace-error-banner">
          <p>{error.message}</p>
          <Button variant="outline" size="sm" onClick={handleReset} data-testid="replace-error-reset">
            重新预览
          </Button>
        </div>
      )}

      {plan && phase === "idle" && (
        <div className="replace-plan-container">
          <ReplacePlanView
            plan={plan}
            excluded={excluded}
            onToggleHit={handleToggleHit}
            onToggleScene={handleToggleScene}
          />
          <div className="replace-actions">
            <Button onClick={() => void handleApply()} disabled={remaining === 0} data-testid="replace-apply">
              应用替换（{remaining} 处）
            </Button>
            <Button variant="outline" onClick={handleReset} data-testid="replace-replan">
              重新预览
            </Button>
          </div>
        </div>
      )}

      {phase === "done" && result && (
        <div className="replace-done-banner" data-testid="replace-done-banner">
          <p>
            替换完成：应用 {result.appliedHitCount} 处命中，影响 {result.modifiedSceneIds.length} 个场景（已创建保护快照与变更记录）。
          </p>
          <Button variant="outline" size="sm" onClick={handleReset} data-testid="replace-done-reset">
            继续
          </Button>
        </div>
      )}

      <ReplaceProgressDialog
        open={busy}
        progress={progress}
        isApplying={phase === "applying"}
        error={phase === "error" ? error : null}
        result={phase === "done" ? result : null}
        onCancel={handleCancel}
        onClose={handleReset}
      />
    </div>
  );
}
