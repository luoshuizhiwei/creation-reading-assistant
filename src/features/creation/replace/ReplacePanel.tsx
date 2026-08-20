import React, { useMemo, useRef, useState } from "react";
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
    <div className="replace-panel" data-testid="replace-panel">
      <header className="replace-panel-header">
        <h2>查找替换</h2>
        <span className="replace-scope-label" data-testid="replace-scope">
          范围：{scopeLabel}
        </span>
        <button type="button" onClick={onClose} aria-label="关闭" data-testid="replace-panel-close">
          关闭
        </button>
      </header>

      <div className="replace-query-form">
        <label>
          查找
          <input
            type="text"
            value={query.find}
            aria-label="查找内容"
            data-testid="replace-find"
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
        <div className="replace-mode-toggle" role="group" aria-label="替换模式">
          <button
            type="button"
            aria-pressed={query.mode === "plain"}
            data-testid="replace-mode-plain"
            onClick={() => setQuery((q) => ({ ...q, mode: "plain" }))}
          >
            普通文本
          </button>
          <button
            type="button"
            aria-pressed={query.mode === "regex"}
            data-testid="replace-mode-regex"
            onClick={() => setQuery((q) => ({ ...q, mode: "regex" }))}
          >
            正则
          </button>
        </div>
        <button
          type="button"
          onClick={() => void handlePreview()}
          disabled={busy}
          data-testid="replace-preview"
        >
          预览替换
        </button>
      </div>

      {error && phase === "error" && (
        <div className="replace-error-banner" data-testid="replace-error-banner">
          <p>{error.message}</p>
          <button type="button" onClick={handleReset} data-testid="replace-error-reset">
            重新预览
          </button>
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
            <button
              type="button"
              onClick={() => void handleApply()}
              disabled={remaining === 0}
              data-testid="replace-apply"
            >
              应用替换（{remaining} 处）
            </button>
            <button type="button" onClick={handleReset} data-testid="replace-replan">
              重新预览
            </button>
          </div>
        </div>
      )}

      {phase === "done" && result && (
        <div className="replace-done-banner" data-testid="replace-done-banner">
          <p>
            替换完成：应用 {result.appliedHitCount} 处命中，影响 {result.modifiedSceneIds.length} 个场景（已创建保护快照与变更记录）。
          </p>
          <button type="button" onClick={handleReset} data-testid="replace-done-reset">
            继续
          </button>
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
