import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { X } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { ContinuousChapterEditor, type ContinuousChapterEditorHandle } from "@/features/creation/editor/ContinuousChapterEditor";
import type { SceneSelection } from "@/features/creation/editor/annotation-selection";
import { WritingDeskHeader } from "@/features/creation/editor/desk/WritingDeskHeader";
import { WritingDeskOutlineSidebar } from "@/features/creation/editor/desk/WritingDeskOutlineSidebar";
import { WritingDeskMargin } from "@/features/creation/editor/desk/WritingDeskMargin";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { createWritingSessionTracker, type SessionSettleReport, type WritingSessionTracker } from "@/features/creation/editor/writing-session-tracker";
import type { CreationProjectNavigation, CreationProjectSummary } from "@/types/creation";
import "@/features/creation/editor/continuous-editor.css";
import "@/features/creation/editor/writing-reference.css";

type EditMode = "scene" | "continuous";

export interface WritingDeskProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  navigation: CreationProjectNavigation;
  onSelectProject(projectId: string): void;
  /** 场景雷达空态引导跳大纲页（由项目壳提供视图切换）。 */
  onOpenOutline?(): void;
}

export function WritingDesk({ projects, project, navigation, onSelectProject, onOpenOutline }: WritingDeskProps) {
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const sceneViews = useCreationStore((state) => state.sceneViews);
  const outlines = useCreationStore((state) => state.outlines);
  const abnormalExit = useCreationStore((state) => state.abnormalExit);
  const recoveryNoticeDismissed = useCreationStore((state) => state.recoveryNoticeDismissed);
  const selectScene = useCreationStore((state) => state.selectScene);
  const dismissRecoveryNotice = useCreationStore((state) => state.dismissRecoveryNotice);
  const setLeaveGuard = useCreationStore((state) => state.setLeaveGuard);

  const {
    loadOutline,
    loadScene,
    saveSceneBody,
    subscribeProject,
    reportSession
  } = useCreationActions();

  const showToast = useUIStore((state) => state.showToast);
  const editorRef = useRef<SceneEditorHandle>(null);
  const continuousRef = useRef<ContinuousChapterEditorHandle>(null);

  const [focusMode, setFocusMode] = useState(() => window.matchMedia("(max-width: 920px)").matches);
  const [typewriter, setTypewriter] = useState(false);
  const [characterCount, setCharacterCount] = useState(0);
  const [editMode, setEditMode] = useState<EditMode>("scene");
  /** 正文真实选区（含折叠光标）；批注锚点唯一来源。 */
  const [selection, setSelection] = useState<SceneSelection | null>(null);
  /** @ 触发的卡片引用选择器。 */
  const [referencePickerOpen, setReferencePickerOpen] = useState(false);
  const [marginCollapsed, setMarginCollapsed] = useState(
    () => typeof window !== "undefined" && window.matchMedia("(max-width: 1100px)").matches
  );

  /**
   * 写作会话跟踪（独立 module）：仅在输入 / 有意义选择 / 结构操作时计时，
   * 空闲 5 分钟自动结算；切场景、窗口隐藏、卸载与异常关闭均正确结算。
   * 只接收字符数与场景 ID，不记录任何按键内容、选中文本或正文内容。
   */
  const sessionTrackerRef = useRef<WritingSessionTracker | null>(null);
  if (!sessionTrackerRef.current) {
    sessionTrackerRef.current = createWritingSessionTracker({
      onSettle: (report: SessionSettleReport) => {
        if (report.activeMs < 1000) return;
        void reportSession({
          projectId: project.id,
          sceneId: report.sceneId ?? undefined,
          startedAt: new Date(report.startedAt).toISOString(),
          activeSeconds: Math.round(report.activeMs / 1000),
          netChars: report.netChars
        });
      }
    });
  }
  const sessionTracker = sessionTrackerRef.current;

  const characterCountRef = useRef(0);
  characterCountRef.current = characterCount;

  const selectedSceneIdRef = useRef<string | undefined>(undefined);
  selectedSceneIdRef.current = selectedSceneId;

  const handleStatsChange = useCallback((count: number) => {
    setCharacterCount(count);
    sessionTracker.signalActivity("input", selectedSceneIdRef.current ?? null, count);
  }, [sessionTracker]);

  const continuousCharCounts = useRef(new Map<string, number>());
  const handleContinuousStatsChange = useCallback((sceneId: string, chars: number) => {
    continuousCharCounts.current.set(sceneId, chars);
    let total = 0;
    for (const value of continuousCharCounts.current.values()) total += value;
    setCharacterCount(total);
    sessionTracker.signalActivity("input", sceneId, total);
  }, [sessionTracker]);

  useEffect(() => {
    const timer = setInterval(() => {
      sessionTracker.checkIdle();
    }, 30_000);
    const handleVisibility = () => {
      if (document.visibilityState === "hidden") sessionTracker.settle();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    window.addEventListener("beforeunload", handleVisibility);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
      window.removeEventListener("beforeunload", handleVisibility);
      sessionTracker.settle();
    };
  }, [sessionTracker]);

  const outline = outlines[project.id];
  const workflow = project.setup.chapterWorkflow;

  const selectedScene = useMemo(
    () => navigation.chapters.flatMap((chapter) => chapter.scenes).find((scene) => scene.id === selectedSceneId),
    [navigation, selectedSceneId]
  );
  const selectedChapter = useMemo(
    () => navigation.chapters.find((chapter) => chapter.scenes.some((scene) => scene.id === selectedSceneId)),
    [navigation, selectedSceneId]
  );
  const sceneView = selectedSceneId ? sceneViews[selectedSceneId] : undefined;

  // 连续模式：仅确保「当前章节」的场景正文已加载（不加载整项目正文）。
  useEffect(() => {
    if (editMode !== "continuous" || !selectedChapter) return;
    for (const scene of selectedChapter.scenes) {
      if (!sceneViews[scene.id]) void loadScene(scene.id);
    }
  }, [editMode, selectedChapter, sceneViews, loadScene]);

  // 逐场景模式：确保当前选中场景正文已加载。
  useEffect(() => {
    if (editMode !== "scene") return;
    if (!selectedSceneId || sceneViews[selectedSceneId]) return;
    void loadScene(selectedSceneId);
  }, [editMode, loadScene, sceneViews, selectedSceneId]);

  useEffect(() => {
    void loadOutline(project.id);
  }, [loadOutline, project.id]);

  useEffect(() => subscribeProject(project.id), [project.id, subscribeProject]);

  const saveBeforeLeaving = useCallback(async (): Promise<boolean> => {
    if (editMode === "continuous") {
      if (!continuousRef.current) {
        showToast({
          tone: "warning",
          title: "编辑器尚未就绪",
          body: "连续编辑器正在初始化，请稍候再切换，以免丢失本地草稿。"
        });
        return false;
      }
      const ok = await continuousRef.current.saveAllDirty();
      if (!ok) {
        showToast({
          tone: "warning",
          title: "正文尚未全部保存",
          body: "部分场景存在保存失败或正文冲突，请解决后再切换。"
        });
        return false;
      }
      return true;
    }
    if (!editorRef.current?.isDirty()) return true;
    const saved = await editorRef.current.saveNow();
    if (!saved) {
      showToast({ tone: "warning", title: "正文尚未保存", body: "请解决保存失败或正文冲突后再切换场景。" });
    }
    return saved;
  }, [editMode, showToast]);

  useEffect(() => {
    setLeaveGuard(saveBeforeLeaving);
    return () => {
      if (useCreationStore.getState().leaveGuard === saveBeforeLeaving) setLeaveGuard(undefined);
    };
  }, [saveBeforeLeaving, setLeaveGuard]);

  const chooseScene = async (sceneId: string) => {
    if (sceneId === selectedSceneId) return;
    if (!(await saveBeforeLeaving())) return;
    sessionTracker.settle();
    selectScene(sceneId);
    setSelection(null);
    await loadScene(sceneId);
  };

  const chooseProject = async (projectId: string) => {
    if (projectId === project.id) return;
    if (!(await saveBeforeLeaving())) return;
    onSelectProject(projectId);
  };

  const toggleEditMode = async () => {
    if (!(await saveBeforeLeaving())) return;
    setEditMode((mode) => (mode === "scene" ? "continuous" : "scene"));
    if (editMode === "continuous") continuousCharCounts.current.clear();
  };

  const handleSelectionChange = useCallback((next: SceneSelection | null) => {
    setSelection(next);
    if (next && !next.collapsed) {
      sessionTracker.signalActivity("selection", selectedSceneIdRef.current ?? null, characterCountRef.current);
    }
  }, [sessionTracker]);

  const handleMentionTrigger = useCallback((next: SceneSelection) => {
    setSelection(next);
    setReferencePickerOpen(true);
  }, []);

  const getActiveEditor = useCallback(() => {
    return editMode === "continuous" ? continuousRef.current : editorRef.current;
  }, [editMode]);

  const continuousScenes = useMemo(
    () => (editMode === "continuous" && selectedChapter ? selectedChapter.scenes.map((scene) => ({
      id: scene.id,
      title: scene.title || "默认场景",
      view: sceneViews[scene.id]
    })) : []),
    [editMode, selectedChapter, sceneViews]
  );

  return (
    <section
      className={`writing-desk ${focusMode ? "writing-desk--focus" : ""} ${marginCollapsed ? "writing-desk--no-margin" : ""}`}
      aria-label="正文写作台"
    >
      <WritingDeskOutlineSidebar
        projects={projects}
        project={project}
        outline={outline}
        workflow={workflow}
        selectedSceneId={selectedSceneId}
        onSelectProject={(projectId) => void chooseProject(projectId)}
        onSelectScene={(sceneId) => void chooseScene(sceneId)}
      />

      <main className="writing-manuscript">
        <WritingDeskHeader
          editMode={editMode}
          selectedChapter={selectedChapter}
          selectedScene={selectedScene}
          project={project}
          onToggleEditMode={() => void toggleEditMode()}
        />

        {abnormalExit && !recoveryNoticeDismissed && (
          <InlineNotice tone="warning" className="writing-recovery-notice">
            <span>已恢复最后确认保存的正文；异常退出前不足一秒的未提交输入可能未保存。</span>
            <button type="button" onClick={dismissRecoveryNotice} aria-label="关闭恢复说明"><X size={14} /></button>
          </InlineNotice>
        )}

        <div className="writing-scroll">
          {editMode === "continuous" ? (
            selectedChapter && continuousScenes.length > 0 ? (
              <ContinuousChapterEditor
                ref={continuousRef}
                chapterTitle={selectedChapter.title}
                scenes={continuousScenes}
                onSave={saveSceneBody}
                onReloadScene={loadScene}
                onStatsChange={handleContinuousStatsChange}
                onSelectionChange={handleSelectionChange}
                onMentionTrigger={handleMentionTrigger}
                focusMode={focusMode}
                onToggleFocusMode={() => setFocusMode((value) => !value)}
                typewriter={typewriter}
                onToggleTypewriter={() => setTypewriter((value) => !value)}
              />
            ) : (
              <div className="scene-editor-placeholder">正在读取章节场景…</div>
            )
          ) : selectedSceneId && sceneView ? (
            <SceneEditor
              ref={editorRef}
              view={sceneView}
              onSave={saveSceneBody}
              onReloadScene={() => loadScene(selectedSceneId)}
              onStatsChange={handleStatsChange}
              onSelectionChange={handleSelectionChange}
              onMentionTrigger={handleMentionTrigger}
              focusMode={focusMode}
              onToggleFocusMode={() => setFocusMode((value) => !value)}
              typewriter={typewriter}
              onToggleTypewriter={() => setTypewriter((value) => !value)}
            />
          ) : (
            <div className="scene-editor-placeholder">{selectedSceneId ? "正在读取场景正文…" : "这个项目还没有可编辑场景。"}</div>
          )}
        </div>
      </main>

      <WritingDeskMargin
        project={project}
        outline={outline}
        selectedSceneId={selectedSceneId}
        selectedScene={selectedScene}
        sceneView={sceneView}
        characterCount={characterCount}
        selection={selection}
        referencePickerOpen={referencePickerOpen}
        onOpenReferencePicker={() => setReferencePickerOpen(true)}
        onCloseReferencePicker={() => setReferencePickerOpen(false)}
        collapsed={marginCollapsed}
        onToggleCollapse={setMarginCollapsed}
        focusMode={focusMode}
        getActiveEditor={getActiveEditor}
        onOpenOutline={onOpenOutline}
      />
    </section>
  );
}
