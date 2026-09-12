import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AtSign, Eye, MessageSquarePlus, Sparkles, Trash2 } from "lucide-react";
import { SceneRadar } from "@/features/creation/editor/SceneRadar";
import { deriveSceneRadar } from "@/features/creation/editor/scene-radar";
import { describeSelection, type SceneSelection } from "@/features/creation/editor/annotation-selection";
import { CardReferencePicker } from "@/features/creation/editor/card-reference-picker";
import { buildAiContextPack, type AiContextPack } from "@/features/creation/ai/build-ai-context";
import {
  SCENE_AI_ACTION_ORDER,
  appendTextToSceneBody,
  sceneAiActionLabel,
  sceneAiAdoptMode,
  sceneAiOutputKind
} from "@/features/creation/ai/scene-ai-actions";
import { SceneCandidateReview, type SceneCandidate } from "@/features/creation/ai/SceneCandidateReview";
import { SceneAiReport } from "@/features/creation/ai/SceneAiReport";
import { creationDocumentToPlainText } from "@/features/creation/ai/diff-paragraphs";
import { plainTextToCreationDocument } from "@/features/creation/editor/paste-clean";
import { AiSendConfirmDialog, rememberAiSendOptOut } from "@/features/creation/inbox/ai-send-confirm";
import { getAISettings, runAIAction } from "@/services/ai-service";
import { annotationReanchor, runStructure as runStructureRequest } from "@/services/creation-service";
import {
  isAIAvailable,
  type AIRunAction,
  type AIRunSceneContext,
  type AISettings,
  type SceneAiAction
} from "@/types/ai";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type {
  Annotation,
  AnnotationReanchorCommand,
  CardSummary,
  CreationProjectOutline,
  CreationProjectSummary,
  SceneBodyView
} from "@/types/creation";

type MarginTab = "radar" | "notes";

interface ReanchorCandidate {
  annotation: Annotation;
  selection: SceneSelection;
}

/**
 * 把上下文包里「未被排除」的分组收敛成场景上下文：
 * 正文组不进 sceneContext（走 content 通道），任务卡/卡片/批注各自独立可选。
 * 这样用户排除掉的内容不会换个名字又出现在提示词里。
 */
function buildSceneContext(
  pack: AiContextPack,
  excluded: ReadonlySet<string> | undefined,
  sceneTitle: string
): AIRunSceneContext {
  const pick = (ids: readonly string[]): string | undefined => {
    const parts = pack.groups
      .filter((group) => ids.includes(group.id) && !excluded?.has(group.id))
      .map((group) => group.content.trim())
      .filter((part) => part !== "");
    return parts.length > 0 ? parts.join("\n\n") : undefined;
  };
  return {
    sceneTitle,
    planningText: pick(["planning"]),
    cardsText: pick(["cards", "quick-reference"]),
    annotationsText: pick(["annotations"])
  };
}

export interface WritingDeskMarginProps {
  project: CreationProjectSummary;
  outline: CreationProjectOutline | undefined;
  selectedSceneId: string | undefined;
  selectedScene?: { id: string; title: string; revision?: number };
  sceneView: SceneBodyView | undefined;
  characterCount: number;
  selection: SceneSelection | null;
  referencePickerOpen: boolean;
  onOpenReferencePicker: () => void;
  onCloseReferencePicker: () => void;
  collapsed: boolean;
  onToggleCollapse: (collapsed: boolean) => void;
  focusMode: boolean;
  getActiveEditor: () => { isComposing(): boolean; getSelection(): SceneSelection | null } | null;
  onOpenOutline?: () => void;
  /** 写作速查中显式打开、可在发送确认中排除的卡片。 */
  quickReferenceCards?: CardSummary[];
}

export function WritingDeskMargin({
  project,
  outline,
  selectedSceneId,
  selectedScene,
  sceneView,
  characterCount,
  selection,
  referencePickerOpen,
  onOpenReferencePicker,
  onCloseReferencePicker,
  collapsed,
  onToggleCollapse,
  focusMode,
  getActiveEditor,
  onOpenOutline,
  quickReferenceCards = []
}: WritingDeskMarginProps) {
  const cardList = useCreationStore((state) => state.cards);
  const cardTypes = useCreationStore((state) => state.cardTypes);
  /**
   * 项目导航对象的引用变化等价于「该项目有一次已提交的 workspace 变更落库」：
   * subscribeProject 的 handleEvent 对任何事件都会重取导航（setNavigation 写入新对象），
   * 而批注本身没有独立于导航的 store 信号。把它作为重读当前场景批注的触发源，
   * 避免「首屏读得太早 → 之后永不重读」：演示项目/导入/AI 等外部编排会在写作台
   * 挂载之后才逐条提交批注，只依赖 selectedSceneId 变化会永久停在第 0 条。
   */
  const projectNavigation = useCreationStore((state) => state.navigations[project.id]);
  const showToast = useUIStore((state) => state.showToast);

  const {
    loadAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
    loadCards,
    loadCardTypes,
    saveSceneBody
  } = useCreationActions();

  const [marginTab, setMarginTab] = useState<MarginTab>("radar");
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [annotationDraft, setAnnotationDraft] = useState("");
  const [annotationCardId, setAnnotationCardId] = useState("");
  const [confirmingAnnotation, setConfirmingAnnotation] = useState<string | null>(null);
  const [reanchorCandidate, setReanchorCandidate] = useState<ReanchorCandidate | null>(null);
  const [reanchorBusy, setReanchorBusy] = useState(false);
  const [reanchorError, setReanchorError] = useState<string | null>(null);
  const annotationTextareaRef = useRef<HTMLTextAreaElement>(null);

  // AI 场景助手状态
  const [aiSettings, setAiSettings] = useState<AISettings | undefined>();
  const [aiPackDialog, setAiPackDialog] = useState<{ action: AIRunAction; pack: AiContextPack } | null>(null);
  const [aiCandidate, setAiCandidate] = useState<SceneCandidate | null>(null);
  const [aiReport, setAiReport] = useState<{ content: string; model: string; actionLabel: string } | null>(null);
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
    [
      annotations,
      cardList,
      cardTypes,
      characterCount,
      outline,
      project.id,
      sceneView?.revision,
      selectedScene?.revision,
      selectedSceneId
    ]
  );

  const refreshAnnotations = useCallback(async () => {
    if (!selectedSceneId) {
      setAnnotations([]);
      return;
    }
    setAnnotations(await loadAnnotations({ projectId: project.id, sceneId: selectedSceneId }));
  }, [loadAnnotations, project.id, selectedSceneId]);

  useEffect(() => {
    void refreshAnnotations();
  }, [refreshAnnotations, projectNavigation]);

  useEffect(() => {
    void loadCards({ projectId: project.id });
    void loadCardTypes(project.id);
  }, [loadCards, loadCardTypes, project.id]);

  useEffect(() => {
    setReanchorCandidate(null);
    setReanchorError(null);
  }, [project.id, selectedSceneId]);

  useEffect(() => {
    if (referencePickerOpen) {
      setMarginTab("notes");
    }
  }, [referencePickerOpen]);

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

  const handleCardPicked = useCallback(
    (card: { id: string }) => {
      setAnnotationCardId(card.id);
      onCloseReferencePicker();
      window.setTimeout(() => annotationTextareaRef.current?.focus(), 0);
    },
    [onCloseReferencePicker]
  );

  const submitAnnotation = async () => {
    if (!selectedSceneId || !annotationDraft.trim()) return;
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
    const activeEditor = getActiveEditor();
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

  const requestSceneAI = (action: SceneAiAction) => {
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
      quickReferenceCards,
      cardTypes,
      annotations
    });
    setAiPackDialog({ action, pack });
  };

  const confirmSceneAI = async (
    finalContent: string | null,
    remember: boolean,
    excluded?: ReadonlySet<string>
  ): Promise<void> => {
    const dialog = aiPackDialog;
    if (!dialog || !selectedScene) return;
    if (remember) rememberAiSendOptOut();
    setAiPackDialog(null);
    const content = finalContent ?? dialog.pack.compose(new Set());
    if (content.trim() === "") return;
    setAiBusy(true);
    try {
      // 上下文边界：sceneContext 严格跟随用户在确认框中的排除结果，
      // 被排除的组不会以「上下文」名义二次进入提示词。
      const result = await runAIAction({
        action: dialog.action,
        title: selectedScene.title,
        content,
        sceneContext: buildSceneContext(dialog.pack, excluded, selectedScene.title)
      });
      const label = sceneAiActionLabel(dialog.action);
      if (sceneAiOutputKind(dialog.action) === "report") {
        setAiReport({ content: result.content, model: result.model, actionLabel: label });
      } else {
        setAiCandidate({
          action: dialog.action,
          content: result.content,
          model: result.model,
          mode: sceneAiAdoptMode(dialog.action)
        });
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
      const snapshotted = await runStructureRequest({
        type: "snapshot.create",
        projectId: project.id,
        subjectType: "scene",
        subjectId: selectedSceneId,
        reason: "AI 候选采纳前保护快照"
      });
      if (!snapshotted) throw new Error("保护快照创建失败，已取消采纳。");
      // 续写：在文档层追加，既有段落一个字节都不重排；其余动作：候选整篇替换。
      const nextBody =
        aiCandidate?.mode === "append"
          ? appendTextToSceneBody(sceneView.body, candidateText)
          : plainTextToCreationDocument(candidateText);
      const saved = await saveSceneBody(selectedSceneId, sceneView.revision, nextBody);
      if (!saved?.ok) {
        throw new Error("body-save-failed");
      }
      const label = sceneAiActionLabel(aiCandidate?.action ?? "");
      const append = aiCandidate?.mode === "append";
      setAiCandidate(null);
      showToast({
        tone: "success",
        title: append ? `已追加${label}结果` : "已采纳 AI 候选",
        body: append
          ? "续写内容已追加到正文末尾，原文保留；采纳前已创建保护快照。"
          : "采纳前已创建保护快照，可在版本历史找回原正文。"
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      showToast({
        tone: "error",
        title: "采纳失败",
        body:
          message === "body-save-failed"
            ? "正文保存未成功（可能存在修订冲突），候选已保留，请刷新场景后重试。"
            : message
      });
    } finally {
      setAiBusy(false);
    }
  };

  const projectCards = useMemo(
    () => cardList.filter((card) => card.projectId === project.id),
    [cardList, project.id]
  );

  return (
    <>
      {collapsed && !focusMode && (
        <button
          type="button"
          className="writing-margin-expand"
          aria-label="展开检查器"
          title="展开检查器"
          onClick={() => onToggleCollapse(false)}
        >
          «
        </button>
      )}
      <aside className="writing-margin" aria-label="场景信息">
        <div className="writing-margin-tabs" role="tablist" aria-label="场景信息页签">
          <button
            type="button"
            className="writing-margin-collapse"
            aria-label="收起检查器"
            title="收起检查器"
            onClick={() => onToggleCollapse(true)}
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
            aria-label="批注与引用"
            className={marginTab === "notes" ? "active" : ""}
            onClick={() => setMarginTab("notes")}
            title="批注与引用"
          >
            批注
          </button>
        </div>

        {marginTab === "radar" ? (
          <>
            <SceneRadar radar={radar} onOpenOutline={() => onOpenOutline?.()} />
            <div className="scene-radar-ai" data-testid="scene-ai-row">
              <p className="desktop-card-label">AI 助手</p>
              <div className="scene-radar-ai-buttons">
                {SCENE_AI_ACTION_ORDER.map((action) => (
                  <button
                    key={action}
                    type="button"
                    data-testid={`scene-ai-${action}`}
                    disabled={!aiReady || aiBusy}
                    onClick={() => requestSceneAI(action)}
                  >
                    <Sparkles size={13} /> {sceneAiActionLabel(action)}
                  </button>
                ))}
              </div>
              {!aiReady && (
                <p className="scene-radar-ai-hint">
                  {aiSettings?.enabled
                    ? "已启用 AI 但尚未配置 API Key。"
                    : "AI 未启用；输出只会进入候选评审，不会直接改正文。"}
                </p>
              )}
            </div>
          </>
        ) : (
          <div className="writing-annotations">
            <p className="desktop-card-label">批注与引用</p>
            {annotations.length === 0 && (
              <p className="writing-annotation-empty">
                暂无批注。可关联卡片标记引用，正文编辑后失效的锚点会显示「待重新定位」。
              </p>
            )}
            <ul>
              {annotations.map((annotation) => (
                <li
                  key={annotation.id}
                  className={`writing-annotation ${annotation.anchorInvalid ? "invalid" : ""} ${
                    annotation.status === "resolved" ? "resolved" : ""
                  }`}
                >
                  <span className="writing-annotation-main">
                    {annotation.anchorInvalid && <em className="writing-annotation-relocate">待重新定位</em>}
                    <span className="writing-annotation-text">{annotation.anchoredText || "（锚点失效）"}</span>
                    <span className="writing-annotation-note">{annotation.note}</span>
                    <span className="writing-annotation-meta">
                      {annotation.cardId ? "已关联卡片" : "未关联"} ·{" "}
                      {annotation.status === "resolved" ? "已解决" : "待处理"}
                    </span>
                  </span>
                  <span className="writing-annotation-actions">
                    {annotation.anchorInvalid && (
                      <button type="button" onClick={() => beginReanchor(annotation)}>
                        重新定位
                      </button>
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
                <span
                  className={`writing-annotation-selection-value ${
                    selection && !selection.collapsed ? "has-selection" : ""
                  }`}
                >
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
                  {projectCards.map((card) => (
                    <option key={card.id} value={card.id}>
                      {card.title}
                      {card.aliases.length > 0 ? `（${card.aliases.join("、")}）` : ""}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="writing-annotation-at"
                  onClick={() => {
                    if (selection) {
                      onOpenReferencePicker();
                    } else {
                      showToast({
                        tone: "warning",
                        title: "请先定位正文",
                        body: "在正文中定位光标后，再使用 @ 引用卡片。"
                      });
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
              <button
                type="button"
                className="writing-annotation-add"
                onClick={() => void submitAnnotation()}
                disabled={!annotationDraft.trim()}
              >
                <MessageSquarePlus size={13} /> 添加批注
              </button>
            </div>
            {referencePickerOpen && (
              <CardReferencePicker
                cards={projectCards}
                onSelect={handleCardPicked}
                onClose={onCloseReferencePicker}
              />
            )}
            {reanchorCandidate && (
              <div className="writing-reanchor-backdrop" role="presentation">
                <section
                  className="writing-reanchor-dialog"
                  role="dialog"
                  aria-modal="true"
                  aria-label="确认重新定位批注"
                >
                  <p className="desktop-card-label">新锚点</p>
                  <h4>确认重新定位批注</h4>
                  <p className="writing-reanchor-summary">{describeSelection(reanchorCandidate.selection)}</p>
                  <p className="writing-reanchor-old">
                    原锚点：{reanchorCandidate.annotation.anchoredText || "（已失效）"}（第{" "}
                    {reanchorCandidate.annotation.anchor.blockIndex + 1} 段）
                  </p>
                  <p className="writing-reanchor-note">批注内容、状态和关联卡片将保持不变。</p>
                  {reanchorError && (
                    <p className="writing-reanchor-error" role="alert">
                      {reanchorError}
                    </p>
                  )}
                  <div className="writing-reanchor-actions">
                    <button
                      type="button"
                      disabled={reanchorBusy}
                      onClick={() => {
                        setReanchorCandidate(null);
                        setReanchorError(null);
                      }}
                    >
                      取消
                    </button>
                    <button type="button" disabled={reanchorBusy} onClick={() => void confirmReanchor()}>
                      {reanchorBusy ? "提交中…" : "确认新锚点"}
                    </button>
                  </div>
                </section>
              </div>
            )}
          </div>
        )}
        {aiPackDialog && (
          <AiSendConfirmDialog
            actionLabel={sceneAiActionLabel(aiPackDialog.action)}
            title={selectedScene?.title ?? ""}
            content=""
            pack={aiPackDialog.pack}
            target={
              [aiSettings?.model, aiSettings?.baseUrl]
                .filter((part) => typeof part === "string" && part.trim() !== "")
                .join(" · ") || "你配置的 AI 服务"
            }
            busy={aiBusy}
            onConfirm={(finalContent, remember, excluded) => void confirmSceneAI(finalContent, remember, excluded)}
            onCancel={() => setAiPackDialog(null)}
          />
        )}
        {aiReport && (
          <SceneAiReport
            actionLabel={aiReport.actionLabel}
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
        <div className="writing-margin-rule" />
        <p className="writing-boundary">
          <Eye size={14} /> 卷章结构可在左侧大纲树或卡片板中管理。批注锚定正文真实选区；正文改动后失效会进入待重新定位，不会静默丢失。在正文中输入
          @ 可引用当前项目卡片。
        </p>
      </aside>
    </>
  );
}
