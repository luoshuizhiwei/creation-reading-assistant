import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AtSign, Eye, MessageSquarePlus, Radio, Trash2, X } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { ContinuousChapterEditor, type ContinuousChapterEditorHandle } from "@/features/creation/editor/ContinuousChapterEditor";
import { describeSelection, type SceneSelection } from "@/features/creation/editor/annotation-selection";
import { CardReferencePicker } from "@/features/creation/editor/card-reference-picker";
import "@/features/creation/editor/continuous-editor.css";
import "@/features/creation/editor/writing-reference.css";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { Annotation, CreationProjectNavigation, CreationProjectSummary, StructureApplyResult, StructureCommand } from "@/types/creation";

/** 写作会话：空闲超过该时长（毫秒）即结算并上报当前段。 */
const SESSION_IDLE_TIMEOUT_MS = 5 * 60 * 1000;

type EditMode = "scene" | "continuous";

interface WritingDeskProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  navigation: CreationProjectNavigation;
  onSelectProject(projectId: string): void;
}

export function WritingDesk({ projects, project, navigation, onSelectProject }: WritingDeskProps) {
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
    loadCards
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

  const refreshAnnotations = useCallback(async () => {
    if (!selectedSceneId) {
      setAnnotations([]);
      return;
    }
    setAnnotations(await loadAnnotations({ projectId: project.id, sceneId: selectedSceneId }));
  }, [loadAnnotations, project.id, selectedSceneId]);

  useEffect(() => {
    void refreshAnnotations();
  }, [refreshAnnotations]);

  const sessionRef = useRef<{
    sceneId: string | null;
    startedAt: number;
    startChars: number;
    lastActivity: number;
  } | null>(null);

  const settleSession = useCallback(() => {
    const session = sessionRef.current;
    if (!session) return;
    sessionRef.current = null;
    const activeMs = Math.max(0, session.lastActivity - session.startedAt);
    const activeSeconds = Math.round(activeMs / 1000);
    if (activeSeconds < 1) return;
    void reportSession({
      projectId: project.id,
      sceneId: session.sceneId ?? undefined,
      startedAt: new Date(session.startedAt).toISOString(),
      activeSeconds,
      netChars: characterCountRef.current - session.startChars
    });
  }, [project.id, reportSession]);

  const characterCountRef = useRef(0);
  characterCountRef.current = characterCount;

  const handleStatsChange = useCallback((count: number) => {
    setCharacterCount(count);
    const now = Date.now();
    const session = sessionRef.current;
    if (session && session.sceneId === selectedSceneIdRef.current) {
      session.lastActivity = now;
      return;
    }
    if (session) settleSession();
    sessionRef.current = {
      sceneId: selectedSceneIdRef.current ?? null,
      startedAt: now,
      startChars: count,
      lastActivity: now
    };
  }, [settleSession]);

  const continuousCharCounts = useRef(new Map<string, number>());
  const handleContinuousStatsChange = useCallback((_sceneId: string, chars: number) => {
    continuousCharCounts.current.set(_sceneId, chars);
    let total = 0;
    for (const value of continuousCharCounts.current.values()) total += value;
    setCharacterCount(total);
  }, []);

  const selectedSceneIdRef = useRef<string | undefined>(undefined);
  selectedSceneIdRef.current = selectedSceneId;

  useEffect(() => {
    const timer = setInterval(() => {
      const session = sessionRef.current;
      if (session && Date.now() - session.lastActivity > SESSION_IDLE_TIMEOUT_MS) settleSession();
    }, 30_000);
    const handleVisibility = () => {
      if (document.visibilityState === "hidden") settleSession();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
      settleSession();
    };
  }, [settleSession]);

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
  }, [project.id]);

  useEffect(() => {
    void loadCards({ projectId: project.id });
  }, [loadCards, project.id]);

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

  const chooseScene = async (sceneId: string) => {
    if (sceneId === selectedSceneId) return;
    if (!(await saveBeforeLeaving())) return;
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
  }, []);

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
    <section className={`writing-desk ${focusMode ? "writing-desk--focus" : ""}`} aria-label="正文写作台">
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
        <p className="desktop-card-label">Margin notes</p>
        <h3>场景信息</h3>
        <dl>
          <div><dt>所属章节</dt><dd>{selectedChapter?.title ?? "—"}</dd></div>
          <div><dt>场景修订</dt><dd>r{sceneView?.revision ?? selectedScene?.revision ?? 0}</dd></div>
          <div><dt>非空白字符</dt><dd>{characterCount.toLocaleString("zh-CN")}</dd></div>
          <div><dt>保存方式</dt><dd>停止输入 800ms 后自动保存</dd></div>
        </dl>
        <div className="writing-margin-rule" />
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
        </div>
        <div className="writing-margin-rule" />
        <p className="writing-boundary"><Eye size={14} /> 卷章结构可在左侧大纲树或卡片板中管理。批注锚定正文真实选区；正文改动后失效会进入待重新定位，不会静默丢失。在正文中输入 @ 可引用当前项目卡片。</p>
      </aside>
    </section>
  );
}
