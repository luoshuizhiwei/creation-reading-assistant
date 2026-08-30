import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AtSign, Eye, MessageSquarePlus, Radio, Sparkles, Trash2, X } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { ContinuousChapterEditor, type ContinuousChapterEditorHandle } from "@/features/creation/editor/ContinuousChapterEditor";
import { describeSelection, type SceneSelection } from "@/features/creation/editor/annotation-selection";
import { CardReferencePicker } from "@/features/creation/editor/card-reference-picker";
import { SceneRadar } from "@/features/creation/editor/SceneRadar";
import { deriveSceneRadar } from "@/features/creation/editor/scene-radar";
import { buildAiContextPack, type AiContextPack } from "@/features/creation/ai/build-ai-context";
import { SceneCandidateReview, type SceneCandidate } from "@/features/creation/ai/SceneCandidateReview";
import { SceneAiReport } from "@/features/creation/ai/SceneAiReport";
import { creationDocumentToPlainText } from "@/features/creation/ai/diff-paragraphs";
import { plainTextToCreationDocument } from "@/features/creation/editor/paste-clean";
import { AiSendConfirmDialog, rememberAiSendOptOut, shouldConfirmAiSend } from "@/features/creation/inbox/ai-send-confirm";
import { getAISettings, runAIAction } from "@/services/ai-service";
import { runStructure as runStructureRequest } from "@/services/creation-service";
import { isAIAvailable, type AIRunAction, type AISettings } from "@/types/ai";
import "@/features/creation/editor/continuous-editor.css";
import "@/features/creation/editor/writing-reference.css";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { annotationReanchor } from "@/services/creation-service";
import { createWritingSessionTracker, type SessionSettleReport, type WritingSessionTracker } from "@/features/creation/editor/writing-session-tracker";
import type { Annotation, AnnotationReanchorCommand, CreationProjectNavigation, CreationProjectSummary, StructureApplyResult, StructureCommand } from "@/types/creation";

type EditMode = "scene" | "continuous";

interface ReanchorCandidate {
  annotation: Annotation;
  selection: SceneSelection;
}

interface WritingDeskProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  navigation: CreationProjectNavigation;
  onSelectProject(projectId: string): void;
  /** 场景雷达空态引导跳大纲页（由项目壳提供视图切换）。 */
  onOpenOutline?(): void;
}

type MarginTab = "radar" | "notes";

export function WritingDesk({ projects, project, navigation, onSelectProject, onOpenOutline }: WritingDeskProps) {
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const sceneViews = useCreationStore((state) => state.sceneViews);
  const outlines = useCreationStore((state) => state.outlines);
  const abnormalExit = useCreationStore((state) => state.abnormalExit);
  const recoveryNoticeDismissed = useCreationStore((state) => state.recoveryNoticeDismissed);
  const watchConnected = useCreationStore((state) => state.watchConnected);
  const selectScene = useCreationStore((state) => state.selectScene);
  const dismissRecoveryNotice = useCreationStore((state) => state.dismissRecoveryNotice);
  const setLeaveGuard = useCreationStore((state) => state.setLeaveGuard);
  const {
    loadOutline,
    loadScene,
    runStructure,
    previewStructure,
    applyStructureWithProtection,
    revertStructure,
    saveSceneBody,
    subscribeProject,
    reportSession,
    loadAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
    loadCards,
    loadCardTypes
  } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const editorRef = useRef<SceneEditorHandle>(null);
  const continuousRef = useRef<ContinuousChapterEditorHandle>(null);
  const [outlineView, setOutlineView] = useState<"tree" | "board">("tree");
  const [focusMode, setFocusMode] = useState(() => window.matchMedia("(max-width: 920px)").matches);
  const [typewriter, setTypewriter] = useState(false);
  const [characterCount, setCharacterCount] = useState(0);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [annotationDraft, setAnnotationDraft] = useState("");
  const [annotationCardId, setAnnotationCardId] = useState("");
  const [confirmingAnnotation, setConfirmingAnnotation] = useState<string | null>(null);
  const [reanchorCandidate, setReanchorCandidate] = useState<ReanchorCandidate | null>(null);
  const [reanchorBusy, setReanchorBusy] = useState(false);
  const [reanchorError, setReanchorError] = useState<string | null>(null);
  const [editMode, setEditMode] = useState<EditMode>("scene");
  /** 正文真实选区（含折叠光标）；批注锚点唯一来源。 */
  const [selection, setSelection] = useState<SceneSelection | null>(null);
  /** @ 触发的卡片引用选择器。 */
  const [referencePickerOpen, setReferencePickerOpen] = useState(false);
  const [lastProtectedApply, setLastProtectedApply] = useState<StructureApplyResult | null>(null);
  const [revertBusy, setRevertBusy] = useState(false);
  const [revertError, setRevertError] = useState<string | null>(null);
  const annotationTextareaRef = useRef<HTMLTextAreaElement>(null);

  const cardList = useCreationStore((state) => state.cards);
  const cardTypes = useCreationStore((state) => state.cardTypes);

  const refreshAnnotations = useCallback(async () => {
    if (!selectedSceneId) {
      setAnnotations([]);
      return;
    }
    setAnnotations(await loadAnnotations({ projectId: project.id, sceneId: selectedSceneId }));
  }, [loadAnnotations, project.id, selectedSceneId]);

  // reanchorAnnotation 的契约返回 Promise<boolean>；底层 annotationReanchor 正常返回 truthy 的 AnnotationResult，
  // revision 冲突时抛错。这里统一收敛为布尔（false = 失败/冲突），由调用方保留候选与弹层。
  // 注：useCreationActions 当前未导出该函数（属 Agent 1 的 hook 职责），此处就地包装既有服务，不越界修改公共 hook。
  const reanchorAnnotation = useCallback(
    async (command: AnnotationReanchorCommand): Promise<boolean> => {
      try {
        return Boolean(await annotationReanchor(command));
      } catch {
        return false;
      }
    },
    []
  );

  useEffect(() => {
    void refreshAnnotations();
  }, [refreshAnnotations]);

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

  const selectedSceneIdRef = useRef<string | undefined>(undefined);
  selectedSceneIdRef.current = selectedSceneId;

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

  // 场景雷达（调研 D-C1 v1）：纯只读聚合，数据全部来自已加载状态，自身无异步。
  const [marginTab, setMarginTab] = useState<MarginTab>("radar");
  // 检查器折叠（规格 §4.2）：窄窗默认折叠，可手动开合。
  const [marginCollapsed, setMarginCollapsed] = useState(
    () => typeof window !== "undefined" && window.matchMedia("(max-width: 1100px)").matches
  );

  // AI 场景助手（D-C2 切片 2）：上下文包预览 → 候选评审 → 保护快照 + revision 校验采纳。
  const [aiSettings, setAiSettings] = useState<AISettings | undefined>();
  const [aiPackDialog, setAiPackDialog] = useState<{ action: AIRunAction; pack: AiContextPack } | null>(null);
  const [aiCandidate, setAiCandidate] = useState<SceneCandidate | null>(null);
  const [aiReport, setAiReport] = useState<{ content: string; model: string } | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  useEffect(() => {
    void getAISettings().then(setAiSettings).catch(() => setAiSettings(undefined));
  }, []);
  const aiReady = isAIAvailable(aiSettings);
  const radar = useMemo(
    () =>
      deriveSceneRadar({
        outline,
        selectedSceneId,
        cards: cardList.filter((card) => card.projectId === project.id),
        cardTypes,
        annotations,
        characterCount,
        revision: sceneView?.revision ?? selectedScene?.revision ?? 0
      }),
    [annotations, cardList, cardTypes, characterCount, outline, project.id, sceneView?.revision, selectedScene?.revision, selectedSceneId]
  );

  // 连续模式：仅确保「当前章节」的场景正文已加载（不加载整项目正文）。
  // 正文始终来自 sceneViews（单一真相源），不产生第二份副本。
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

  useEffect(() => {
    // 保护快照只属于创建它的项目；项目切换后不得携带旧项目撤回入口。
    setLastProtectedApply(null);
    setRevertError(null);
    setReanchorCandidate(null);
    setReanchorError(null);
  }, [project.id]);

  useEffect(() => {
    setReanchorCandidate(null);
    setReanchorError(null);
  }, [selectedSceneId]);

  useEffect(() => {
    void loadCards({ projectId: project.id });
    // 卡片类型定义用于雷达的类型徽标与伏笔识别（D-C3）。
    void loadCardTypes(project.id);
  }, [loadCards, loadCardTypes, project.id]);

  useEffect(() => subscribeProject(project.id), [project.id, subscribeProject]);

  const saveBeforeLeaving = useCallback(async (): Promise<boolean> => {
    if (editMode === "continuous") {
      // ref 缺失时不得静默放行：连续编辑器尚未就绪意味着本地草稿可能未绑定保存通道。
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

  // ---- AI 场景助手（D-C2 切片 2）----
  const requestSceneAI = (action: AIRunAction) => {
    if (!selectedScene || !aiReady) {
      showToast({
        tone: "warning",
        title: "AI 未就绪",
        body: aiSettings?.enabled
          ? "已启用 AI 助手但尚未配置 API Key，请先到设置中心保存 Key。"
          : "AI 助手未启用。开启并配置 Key 后，可为当前场景生成候选版本。"
      });
      return;
    }
    if (aiBusy || aiCandidate || !sceneView) return;
    // 与场景相关的卡片：任务卡引用 + 批注关联（去重），避免全书卡片全量发送。
    const relevantIds = new Set<string>();
    const planning = radar.planning;
    if (planning?.perspectiveCardId) relevantIds.add(planning.perspectiveCardId);
    if (planning?.locationCardId) relevantIds.add(planning.locationCardId);
    for (const id of planning?.castCardIds ?? []) relevantIds.add(id);
    for (const annotation of annotations) {
      if (annotation.cardId) relevantIds.add(annotation.cardId);
    }
    const pack = buildAiContextPack({
      sceneTitle: selectedScene.title,
      sceneBodyText: creationDocumentToPlainText(sceneView.body),
      planning: planning ?? null,
      cards: cardList.filter((card) => card.projectId === project.id && relevantIds.has(card.id)),
      cardTypes,
      annotations
    });
    setAiPackDialog({ action, pack });
  };

  const confirmSceneAI = async (finalContent: string | null, remember: boolean): Promise<void> => {
    const dialog = aiPackDialog;
    if (!dialog || !selectedScene) return;
    if (remember) rememberAiSendOptOut();
    setAiPackDialog(null);
    const content = finalContent ?? dialog.pack.compose(new Set());
    if (content.trim() === "") return;
    setAiBusy(true);
    try {
      const result = await runAIAction({ action: dialog.action, title: selectedScene.title, content });
      if (dialog.action === "consistency") {
        setAiReport({ content: result.content, model: result.model });
      } else {
        setAiCandidate({ action: dialog.action, content: result.content, model: result.model });
      }
    } catch (error) {
      showToast({
        tone: "error",
        title: "AI 请求失败",
        body: error instanceof Error ? error.message : String(error)
      });
    } finally {
      setAiBusy(false);
    }
  };

  const acceptSceneCandidate = async (candidateText: string): Promise<void> => {
    if (!selectedSceneId || !sceneView) return;
    setAiBusy(true);
    try {
      // 保护快照（snapshot.create）→ 正文保存（revision 校验）。
      const snapshotted = await runStructureRequest({
        type: "snapshot.create",
        projectId: project.id,
        subjectType: "scene",
        subjectId: selectedSceneId,
        reason: "AI 候选采纳前保护快照"
      });
      if (!snapshotted) throw new Error("保护快照创建失败，已取消采纳。");
      const saved = await saveSceneBody(selectedSceneId, sceneView.revision, plainTextToCreationDocument(candidateText));
      if (!saved?.ok) {
        throw new Error("body-save-failed");
      }
      setAiCandidate(null);
      showToast({ tone: "success", title: "已采纳 AI 候选", body: "采纳前已创建保护快照，可在版本历史找回原正文。" });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      showToast({
        tone: "error",
        title: "采纳失败",
        body: message === "body-save-failed"
          ? "正文保存未成功（可能存在修订冲突），候选已保留，请刷新场景后重试。"
          : message
      });
    } finally {
      setAiBusy(false);
    }
  };

  const chooseScene = async (sceneId: string) => {
    if (sceneId === selectedSceneId) return;
    if (!(await saveBeforeLeaving())) return;
    // 切场景前结算当前会话段（归入原场景）。
    sessionTracker.settle();
    selectScene(sceneId);
    setSelection(null);
    await loadScene(sceneId);
  };

  const toggleEditMode = async () => {
    if (!(await saveBeforeLeaving())) return;
    setEditMode((mode) => (mode === "scene" ? "continuous" : "scene"));
    if (editMode === "continuous") continuousCharCounts.current.clear();
  };

  const handleSelectionChange = useCallback((next: SceneSelection | null) => {
    setSelection(next);
    // 有意义的选择：非空且非折叠选区才视为写作活动（不计按键内容，仅计时信号）。
    if (next && !next.collapsed) {
      sessionTracker.signalActivity("selection", selectedSceneIdRef.current ?? null, characterCountRef.current);
    }
  }, [sessionTracker]);

  const handleMentionTrigger = useCallback((next: SceneSelection) => {
    // 卡片引用基于当前选区建立非正文锚点；打开当前项目卡片搜索。
    setSelection(next);
    setReferencePickerOpen(true);
  }, []);

  const handleCardPicked = useCallback((card: { id: string }) => {
    setAnnotationCardId(card.id);
    setReferencePickerOpen(false);
    // 引导用户补全批注内容（引用不写正文，只建立批注关联）。
    window.setTimeout(() => annotationTextareaRef.current?.focus(), 0);
  }, []);

  const submitAnnotation = async () => {
    if (!selectedSceneId || !annotationDraft.trim()) return;
    // 无真实选区时禁止提交：不悄悄锚到第一段。
    if (!selection) {
      showToast({
        tone: "warning",
        title: "请先定位正文",
        body: "在正文中选中文字，或把光标放进目标段落后再添加批注。"
      });
      return;
    }
    const anchor = {
      blockIndex: selection.blockIndex,
      textOffset: selection.textOffset,
      textLength: selection.textLength,
      // 快照文本：编辑后由工作区校验锚点保持或进入待重新定位。
      text: selection.selectedText || undefined
    };
    const ok = await createAnnotation({
      projectId: project.id,
      sceneId: selection.sceneId,
      cardId: annotationCardId || undefined,
      anchor,
      note: annotationDraft.trim()
    });
    if (ok) {
      setAnnotationDraft("");
      await refreshAnnotations();
    } else {
      showToast({
        tone: "error",
        title: "批注创建失败",
        body: "锚点未命中正文文本或关联卡片不可用，请重新选择正文位置后再试。"
      });
    }
  };

  const beginReanchor = (annotation: Annotation) => {
    const activeEditor = editMode === "continuous" ? continuousRef.current : editorRef.current;
    if (activeEditor?.isComposing()) {
      showToast({ tone: "warning", title: "正在输入文字", body: "请先结束输入法组合输入，再重新定位批注。" });
      return;
    }
    const currentSelection = activeEditor?.getSelection() ?? null;
    if (!currentSelection || currentSelection.collapsed || !currentSelection.selectedText) {
      showToast({ tone: "warning", title: "请先选择新锚点", body: "请在正文中选中一段非空文字，再重新定位批注。" });
      return;
    }
    if (currentSelection.sceneId !== annotation.sceneId) {
      showToast({ tone: "warning", title: "选区不在当前场景", body: "批注只能重新定位到它所属场景的正文。" });
      return;
    }
    setReanchorCandidate({ annotation, selection: currentSelection });
    setReanchorError(null);
  };

  const confirmReanchor = async () => {
    if (!reanchorCandidate || reanchorBusy) return;
    const { annotation, selection: nextSelection } = reanchorCandidate;
    setReanchorBusy(true);
    setReanchorError(null);
    try {
      const ok = await reanchorAnnotation({
        type: "annotation.reanchor",
        annotationId: annotation.id,
        baseRevision: annotation.revision,
        anchor: {
          blockIndex: nextSelection.blockIndex,
          textOffset: nextSelection.textOffset,
          textLength: nextSelection.textLength,
          text: nextSelection.selectedText
        }
      });
      if (!ok) {
        setReanchorError("重新定位失败：批注可能已被其他操作修改。新锚点选择已保留，请检查后重试。");
        return;
      }
      setReanchorCandidate(null);
      await refreshAnnotations();
    } catch (error) {
      setReanchorError(error instanceof Error ? error.message : "重新定位失败。新锚点选择已保留，请重试。");
    } finally {
      setReanchorBusy(false);
    }
  };

  const chooseProject = async (projectId: string) => {
    if (projectId === project.id) return;
    if (!(await saveBeforeLeaving())) return;
    onSelectProject(projectId);
  };

  const runStructureForTree = useCallback(
    async (command: StructureCommand): Promise<boolean> => {
      const ok = await runStructure(command);
      if (ok) void loadOutline(project.id);
      return ok;
    },
    [loadOutline, project.id, runStructure]
  );

  const applyStructureForOutline = useCallback(
    async (command: Parameters<typeof applyStructureWithProtection>[0]) => {
      const result = await applyStructureWithProtection(command);
      if (result) await loadOutline(project.id);
      return result;
    },
    [applyStructureWithProtection, loadOutline, project.id]
  );

  const handleProtectedApplied = useCallback((result: StructureApplyResult) => {
    setLastProtectedApply(result);
    setRevertError(null);
  }, []);

  const revertLastProtectedApply = useCallback(async () => {
    if (!lastProtectedApply || revertBusy) return;
    setRevertBusy(true);
    setRevertError(null);
    try {
      const result = await revertStructure({
        type: "structure.revert",
        projectId: project.id,
        protectionSnapshotId: lastProtectedApply.protectionSnapshotId,
        expectedAppliedRevisions: lastProtectedApply.affected
      });
      if (!result) {
        setRevertError("无法撤回：大纲可能已被后续修改。请保留当前内容并重新检查。");
        return;
      }
      setLastProtectedApply(null);
      await loadOutline(project.id);
    } catch (error) {
      setRevertError(error instanceof Error ? error.message : "无法撤回本次重组。");
    } finally {
      setRevertBusy(false);
    }
  }, [lastProtectedApply, loadOutline, project.id, revertBusy, revertStructure]);

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
      <aside className="writing-outline" aria-label="项目大纲">
        <label className="writing-project-switcher">
          <span>当前项目</span>
          <select value={project.id} onChange={(event) => void chooseProject(event.target.value)}>
            {projects.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
          </select>
        </label>
        <div className="writing-outline-view-switch" role="group" aria-label="大纲视图">
          <button type="button" className={outlineView === "tree" ? "active" : ""} onClick={() => setOutlineView("tree")}>大纲树</button>
          <button type="button" className={outlineView === "board" ? "active" : ""} onClick={() => setOutlineView("board")}>卡片板</button>
        </div>
        {lastProtectedApply && (
          <div className="writing-outline-revert" role="status">
            <span>安全重组已应用，并已创建保护快照。</span>
            <div className="writing-outline-revert-actions">
              <button type="button" aria-label="撤回本次重组" disabled={revertBusy} onClick={() => void revertLastProtectedApply()}>
                {revertBusy ? "撤回中…" : "撤回"}
              </button>
              <button
                type="button"
                aria-label="关闭撤回提示"
                disabled={revertBusy}
                onClick={() => { setLastProtectedApply(null); setRevertError(null); }}
              >
                <X size={12} />
              </button>
            </div>
            {revertError && <span className="writing-outline-revert-error" role="alert">{revertError}</span>}
          </div>
        )}
        <div className="writing-outline-scroll">
          {outline ? (
            outlineView === "tree" ? (
              <OutlineTree
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={(sceneId) => void chooseScene(sceneId)}
                runStructure={runStructureForTree}
                previewStructure={previewStructure}
                applyStructureWithProtection={applyStructureForOutline}
                onProtectedApplied={handleProtectedApplied}
              />
            ) : (
              <CardBoard
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={(sceneId) => void chooseScene(sceneId)}
                previewStructure={previewStructure}
                applyStructureWithProtection={applyStructureForOutline}
                onProtectedApplied={handleProtectedApplied}
              />
            )
          ) : (
            <p className="outline-loading" role="status">正在读取大纲…</p>
          )}
        </div>
      </aside>

      {marginCollapsed && !focusMode && (
        <button
          type="button"
          className="writing-margin-expand"
          aria-label="展开检查器"
          title="展开检查器"
          onClick={() => setMarginCollapsed(false)}
        >
          «
        </button>
      )}
      <main className="writing-manuscript">
        <header className="writing-manuscript-head">
          <div>
            <p className="desktop-card-label">Manuscript</p>
            <h2>{editMode === "continuous"
              ? (selectedChapter ? selectedChapter.title : "整章连续编辑")
              : (selectedScene?.title ?? "选择场景")}</h2>
            <span>{selectedChapter?.title ?? project.title} · {editMode === "continuous" ? "整章连续编辑（可逐场景编辑）" : "逐场景编辑"}</span>
          </div>
          <div className="writing-head-actions">
            <div className="writing-mode-switch" role="group" aria-label="写作模式切换">
              <button
                type="button"
                className={editMode === "scene" ? "active" : ""}
                aria-pressed={editMode === "scene"}
                onClick={() => { if (editMode !== "scene") void toggleEditMode(); }}
                title="逐场景编辑：一次编辑一个场景"
              >
                逐场景
              </button>
              <button
                type="button"
                className={editMode === "continuous" ? "active" : ""}
                aria-pressed={editMode === "continuous"}
                onClick={() => { if (editMode !== "continuous") void toggleEditMode(); }}
                title="整章连续编辑：当前章节所有场景连续排列、各自可编辑"
              >
                整章连续
              </button>
            </div>
            <div className={`writing-watch ${watchConnected ? "connected" : ""}`} title={watchConnected ? "已订阅项目变更" : "正在连接项目变更"}>
              <Radio size={12} /> {watchConnected ? "变更已连接" : "连接中"}
            </div>
          </div>
        </header>

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

      <aside className="writing-margin" aria-label="场景信息">
        <div className="writing-margin-tabs" role="tablist" aria-label="场景信息页签">
          <button
            type="button"
            className="writing-margin-collapse"
            aria-label="收起检查器"
            title="收起检查器"
            onClick={() => setMarginCollapsed(true)}
          >
            »
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={marginTab === "radar"}
            className={marginTab === "radar" ? "active" : ""}
            onClick={() => setMarginTab("radar")}
          >
            场景雷达
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={marginTab === "notes"}
            className={marginTab === "notes" ? "active" : ""}
            onClick={() => setMarginTab("notes")}
          >
            批注与引用
          </button>
        </div>
        {marginTab === "radar" ? (
          <>
            <SceneRadar radar={radar} onOpenOutline={() => onOpenOutline?.()} />
            <div className="scene-radar-ai" data-testid="scene-ai-row">
              <p className="desktop-card-label">AI 助手</p>
              <div className="scene-radar-ai-buttons">
                <button type="button" disabled={!aiReady || aiBusy} onClick={() => requestSceneAI("polish")}>
                  <Sparkles size={13} /> 润色场景
                </button>
                <button type="button" disabled={!aiReady || aiBusy} onClick={() => requestSceneAI("expand")}>
                  <Sparkles size={13} /> 扩写场景
                </button>
                <button type="button" disabled={!aiReady || aiBusy} onClick={() => requestSceneAI("consistency")}>
                  <Sparkles size={13} /> 一致性检查
                </button>
              </div>
              {!aiReady && (
                <p className="scene-radar-ai-hint">
                  {aiSettings?.enabled ? "已启用 AI 但尚未配置 API Key。" : "AI 未启用；输出只会进入候选评审，不会直接改正文。"}
                </p>
              )}
            </div>
          </>
        ) : (
        <div className="writing-annotations">
          <p className="desktop-card-label">批注与引用</p>
          {annotations.length === 0 && <p className="writing-annotation-empty">暂无批注。可关联卡片标记引用，正文编辑后失效的锚点会显示「待重新定位」。</p>}
          <ul>
            {annotations.map((annotation) => (
              <li key={annotation.id} className={`writing-annotation ${annotation.anchorInvalid ? "invalid" : ""} ${annotation.status === "resolved" ? "resolved" : ""}`}>
                <span className="writing-annotation-main">
                  {annotation.anchorInvalid && <em className="writing-annotation-relocate">待重新定位</em>}
                  <span className="writing-annotation-text">{annotation.anchoredText || "（锚点失效）"}</span>
                  <span className="writing-annotation-note">{annotation.note}</span>
                  <span className="writing-annotation-meta">
                    {annotation.cardId ? "已关联卡片" : "未关联"} · {annotation.status === "resolved" ? "已解决" : "待处理"}
                  </span>
                </span>
                <span className="writing-annotation-actions">
                  {annotation.anchorInvalid && (
                    <button type="button" onClick={() => beginReanchor(annotation)}>重新定位</button>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      void updateAnnotation({
                        annotationId: annotation.id,
                        baseRevision: annotation.revision,
                        status: annotation.status === "resolved" ? "open" : "resolved"
                      }).then(() => refreshAnnotations());
                    }}
                  >
                    {annotation.status === "resolved" ? "重开" : "解决"}
                  </button>
                  <button
                    type="button"
                    className={confirmingAnnotation === annotation.id ? "confirming" : ""}
                    onClick={() => {
                      if (confirmingAnnotation === annotation.id) {
                        void deleteAnnotation({ annotationId: annotation.id }).then(() => refreshAnnotations());
                        setConfirmingAnnotation(null);
                      } else {
                        setConfirmingAnnotation(annotation.id);
                      }
                    }}
                  >
                    <Trash2 size={12} />
                    {confirmingAnnotation === annotation.id ? "确认" : "删除"}
                  </button>
                </span>
              </li>
            ))}
          </ul>
          <div className="writing-annotation-form">
            <div className="writing-annotation-selection" aria-live="polite">
              <span className="writing-annotation-selection-label">锚点</span>
              <span className={`writing-annotation-selection-value ${selection && !selection.collapsed ? "has-selection" : ""}`}>
                {describeSelection(selection)}
              </span>
            </div>
            <div className="writing-annotation-card-row">
              <select
                className="paper-input h-8 text-xs"
                value={annotationCardId}
                onChange={(event) => setAnnotationCardId(event.target.value)}
                aria-label="关联卡片"
              >
                <option value="">不关联卡片</option>
                {cardList.filter((card) => card.projectId === project.id).map((card) => (
                  <option key={card.id} value={card.id}>{card.title}{card.aliases.length > 0 ? `（${card.aliases.join("、")}）` : ""}</option>
                ))}
              </select>
              <button
                type="button"
                className="writing-annotation-at"
                onClick={() => {
                  if (selection) {
                    setReferencePickerOpen(true);
                  } else {
                    showToast({ tone: "warning", title: "请先定位正文", body: "在正文中定位光标后，再使用 @ 引用卡片。" });
                  }
                }}
                title="在正文输入 @ 可直接打开卡片引用"
              >
                <AtSign size={13} /> @ 引用卡片
              </button>
            </div>
            <textarea
              ref={annotationTextareaRef}
              className="paper-input min-h-[64px] resize-y text-xs"
              placeholder="批注内容（引用锚点记录真实选区文本）…"
              value={annotationDraft}
              onChange={(event) => setAnnotationDraft(event.target.value)}
            />
            <button type="button" className="writing-annotation-add" onClick={() => void submitAnnotation()} disabled={!annotationDraft.trim()}>
              <MessageSquarePlus size={13} /> 添加批注
            </button>
          </div>
          {referencePickerOpen && (
            <CardReferencePicker
              cards={cardList.filter((card) => card.projectId === project.id)}
              onSelect={handleCardPicked}
              onClose={() => setReferencePickerOpen(false)}
            />
          )}
          {aiPackDialog && (
            <AiSendConfirmDialog
              actionLabel={aiPackDialog.action === "polish" ? "润色场景" : aiPackDialog.action === "expand" ? "扩写场景" : "一致性检查"}
              title={selectedScene?.title ?? ""}
              content=""
              pack={aiPackDialog.pack}
              target={[aiSettings?.model, aiSettings?.baseUrl].filter((part) => typeof part === "string" && part.trim() !== "").join(" · ") || "你配置的 AI 服务"}
              busy={aiBusy}
              onConfirm={(finalContent, remember) => void confirmSceneAI(finalContent, remember)}
              onCancel={() => setAiPackDialog(null)}
            />
          )}
          {aiReport && (
            <SceneAiReport
              actionLabel="一致性检查"
              content={aiReport.content}
              model={aiReport.model}
              onClose={() => setAiReport(null)}
            />
          )}
          {aiCandidate && sceneView && (
            <SceneCandidateReview
              candidate={aiCandidate}
              currentBodyText={creationDocumentToPlainText(sceneView.body)}
              busy={aiBusy}
              onAccept={(text) => void acceptSceneCandidate(text)}
              onDiscard={() => setAiCandidate(null)}
            />
          )}
          {reanchorCandidate && (
            <div className="writing-reanchor-backdrop" role="presentation">
              <section className="writing-reanchor-dialog" role="dialog" aria-modal="true" aria-label="确认重新定位批注">
                <p className="desktop-card-label">新锚点</p>
                <h4>确认重新定位批注</h4>
                <p className="writing-reanchor-summary">{describeSelection(reanchorCandidate.selection)}</p>
                <p className="writing-reanchor-old">原锚点：{reanchorCandidate.annotation.anchoredText || "（已失效）"}（第 {reanchorCandidate.annotation.anchor.blockIndex + 1} 段）</p>
                <p className="writing-reanchor-note">批注内容、状态和关联卡片将保持不变。</p>
                {reanchorError && <p className="writing-reanchor-error" role="alert">{reanchorError}</p>}
                <div className="writing-reanchor-actions">
                  <button type="button" disabled={reanchorBusy} onClick={() => { setReanchorCandidate(null); setReanchorError(null); }}>取消</button>
                  <button type="button" disabled={reanchorBusy} onClick={() => void confirmReanchor()}>{reanchorBusy ? "提交中…" : "确认新锚点"}</button>
                </div>
              </section>
            </div>
          )}
        </div>
        )}
        <div className="writing-margin-rule" />
        <p className="writing-boundary"><Eye size={14} /> 卷章结构可在左侧大纲树或卡片板中管理。批注锚定正文真实选区；正文改动后失效会进入待重新定位，不会静默丢失。在正文中输入 @ 可引用当前项目卡片。</p>
      </aside>
    </section>
  );
}
