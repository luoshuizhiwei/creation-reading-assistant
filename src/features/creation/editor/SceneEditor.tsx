import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from "react";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import type { JSONContent } from "@tiptap/core";
import { redo, undo } from "@tiptap/pm/history";
import { Bold, CheckCircle2, Focus, Italic, Loader2, Redo2, ScanLine, Sparkles, Undo2, XCircle } from "lucide-react";
import { Button, Spinner } from "@/components/ui";
import { PastePreviewDialog } from "@/features/creation/editor/PastePreviewDialog";
import {
  createNovelEditorExtensions,
  emptyDocument,
  validateCreationDocument
} from "@/features/creation/editor/editor-schema";
import { inspectScenePaste, plainTextToCreationDocument } from "@/features/creation/editor/paste-clean";
import {
  createSceneDocumentSession,
  SCENE_SAVE_STATUS_LABEL,
  type SceneDocumentSession,
  type SceneDocumentSessionState
} from "@/features/creation/editor/scene-document-session";
import { resolveSceneSelection, shouldTriggerMention, type SceneSelection } from "@/features/creation/editor/annotation-selection";
import { useCreationStore } from "@/stores/creation-store";
import type { CreationDocument, SceneBodyView, SceneSaveResponse } from "@/types/creation";

type EditorView = Editor["view"];

export function resolveTypewriterScrollTop(input: {
  scrollTop: number;
  cursorTop: number;
  containerTop: number;
  containerHeight: number;
  scrollHeight: number;
}): number {
  const desired = input.scrollTop + input.cursorTop - input.containerTop - input.containerHeight / 2;
  const maximum = Math.max(0, input.scrollHeight - input.containerHeight);
  return Math.min(maximum, Math.max(0, desired));
}

const INITIAL_SESSION_STATE: SceneDocumentSessionState = {
  status: "idle",
  sceneId: null,
  revision: null,
  dirty: false,
  error: null,
  currentRevision: null,
  lastSavedAt: null
};

const statusLabel = SCENE_SAVE_STATUS_LABEL;

function editableDocument(body: CreationDocument): CreationDocument {
  return body.content.length > 0 && validateCreationDocument(body) ? body : emptyDocument();
}

function countCharacters(document: CreationDocument): number {
  let count = 0;
  const walk = (value: unknown): void => {
    if (!value || typeof value !== "object") return;
    const node = value as { type?: unknown; text?: unknown; content?: unknown };
    if (node.type === "text" && typeof node.text === "string") count += node.text.replace(/\s/g, "").length;
    if (Array.isArray(node.content)) node.content.forEach(walk);
  };
  walk(document);
  return count;
}

function updateCurrentBlock(editor: Editor, enabled: boolean): void {
  const host = editor.view.dom;
  host.querySelectorAll(".is-current-line").forEach((element: Element) => element.classList.remove("is-current-line"));
  if (!enabled) return;
  const position = editor.view.domAtPos(editor.state.selection.from);
  const element = position.node.nodeType === Node.TEXT_NODE ? position.node.parentElement : (position.node as HTMLElement);
  const block = element?.closest("p, blockquote, .centered-text, .author-note, hr.scene-break");
  if (block && host.contains(block)) block.classList.add("is-current-line");
}

export interface SceneEditorHandle {
  saveNow(): Promise<boolean>;
  isDirty(): boolean;
  /** 把键盘焦点还给正文，不改动当前选区或内容。 */
  focus(): void;
  /** 只读选区：从 ProseMirror 真实位置解析；无有效编辑器时返回 null。 */
  getSelection(): SceneSelection | null;
  /** 是否仍在 IME 组合输入；外部命令必须在此期间停用。 */
  isComposing(): boolean;
}

interface SceneEditorProps {
  view?: SceneBodyView;
  onSave(sceneId: string, baseRevision: number, body: CreationDocument): Promise<SceneSaveResponse | undefined>;
  /** 重新加载场景正文用于冲突恢复；连续模式下若父级未提供则隐藏「载入最新版本」按钮。 */
  onReloadScene?(): Promise<SceneBodyView | null | undefined>;
  onStatsChange?(characters: number): void;
  /** 选区变化（含折叠光标）：父级用它展示批注锚点摘要。 */
  onSelectionChange?(selection: SceneSelection | null): void;
  /** 非 IME 状态下输入 @ 触发卡片引用命令；编辑器不把 @ 写入正文。 */
  onMentionTrigger?(selection: SceneSelection): void;
  /** 场景目标字数；用于编辑区底部即时进度。 */
  targetWords?: number | null;
  focusMode: boolean;
  onToggleFocusMode(): void;
  typewriter: boolean;
  onToggleTypewriter(): void;
}

export const SceneEditor = forwardRef<SceneEditorHandle, SceneEditorProps>(function SceneEditor(
  { view, onSave, onReloadScene, onStatsChange, onSelectionChange, onMentionTrigger, targetWords, focusMode, onToggleFocusMode, typewriter, onToggleTypewriter },
  ref
) {
  const [sessionState, setSessionState] = useState(INITIAL_SESSION_STATE);
  const [lineFocus, setLineFocus] = useState(true);
  const [pastePreview, setPastePreview] = useState<{ text: string; reason: string | null }>();
  const [conflictDismissed, setConflictDismissed] = useState(false);
  const [currentWords, setCurrentWords] = useState(0);
  const editorRef = useRef<Editor | null>(null);
  const sessionRef = useRef<SceneDocumentSession>();
  const onSaveRef = useRef(onSave);
  /**
   * 会话状态能否落到本地 useState、能否发布进 creation store 的闸门（批次 BB）。
   * 初值必须是 true：首次挂载时 open() 就发生在下面那条同步 effect 里，比这条闸门所在
   * 的 effect 更早，晚一拍置 true 会让状态行停在 idle。
   * 原先它只会被置 false，于是 React 18 StrictMode（src/app/main.tsx 开着）那次
   * 「挂载→假卸载→再挂载」之后它永久停在 false，开发模式下状态行从此不再刷新；
   * 现在每次挂载重新置 true，开发/生产看到的是同一套行为。
   */
  const mountedRef = useRef(true);
  /** 当前已发布进 store 的场景 ID；换场景或卸载时据此撤销那一条，不留孤儿状态位。 */
  const reportedSceneIdRef = useRef<string | null>(null);
  const plainPasteRef = useRef(false);
  const typewriterRef = useRef(typewriter);
  const lineFocusRef = useRef(lineFocus);
  const composingRef = useRef(false);
  const onSelectionChangeRef = useRef(onSelectionChange);
  const onMentionTriggerRef = useRef(onMentionTrigger);
  const sceneIdRef = useRef<string | undefined>(undefined);
  onSaveRef.current = onSave;
  typewriterRef.current = typewriter;
  lineFocusRef.current = lineFocus;
  onSelectionChangeRef.current = onSelectionChange;
  onMentionTriggerRef.current = onMentionTrigger;
  sceneIdRef.current = view?.sceneId;

  /**
   * 把一份会话状态发布进 creation store（批次 BB，dock 的保存状态就读它）。
   * 归属由 reportedSceneIdRef 追踪：同一个实例换场景（写作台在两个已缓存场景之间切换
   * 就是这样，组件不卸载）时必须先撤掉上一场那条，否则 dock 会把两场都算进「正在编辑」，
   * 上一场最后一次读到的状态还会冒充当前场景。
   * open 之前 sceneId 还没落地，报给谁都不对 —— 那份归属属于「没有在编辑」。
   * 这里不另起事件总线，也不让 dock 反过来求值于编辑器句柄：store 是唯一的通道。
   */
  const publishSaveStatus = (state: SceneDocumentSessionState): void => {
    if (state.sceneId === null) return;
    const store = useCreationStore.getState();
    const last = reportedSceneIdRef.current;
    if (last !== null && last !== state.sceneId) store.clearSceneSaveStatus(last);
    reportedSceneIdRef.current = state.sceneId;
    store.reportSceneSaveStatus(state.sceneId, state.status);
  };

  if (!sessionRef.current) {
    sessionRef.current = createSceneDocumentSession({
      save: async (input) =>
        (await onSaveRef.current(input.sceneId, input.baseRevision, input.body)) ?? {
          ok: false,
          error: { code: "integrity", message: "保存请求未完成，请重试。" }
        },
      setTimer: (callback, delay) => window.setTimeout(callback, delay),
      clearTimer: (timer) => window.clearTimeout(timer as number),
      now: () => Date.now(),
      onState: (state) => {
        // 卸载之后不报：切场景与离开写作台都是「先撤条目，再异步补一次落盘」，
        // 若放行落盘带回的那两次回调（saving → saved），被撤掉的场景会带着一个
        // 「正在保存」在 dock 上复活，再也下不去。
        if (!mountedRef.current) return;
        publishSaveStatus(state);
        setSessionState(state);
      }
    });
  }

  const session = sessionRef.current;
  const sceneId = view?.sceneId;
  const editor = useEditor(
    {
      extensions: createNovelEditorExtensions(),
      content: editableDocument(view?.body ?? emptyDocument()) as unknown as JSONContent,
      editorProps: {
        attributes: {
          class: "scene-editor-content",
          "aria-label": "场景正文编辑区",
          spellcheck: "false"
        },
        handleKeyDown: (_view: EditorView, event: KeyboardEvent) => {
          const modifier = event.ctrlKey || event.metaKey;
          if (modifier && event.key.toLowerCase() === "s") {
            event.preventDefault();
            void session.saveNow();
            return true;
          }
          if (modifier && event.shiftKey && event.key.toLowerCase() === "v") plainPasteRef.current = true;
          return false;
        },
        handleTextInput: (_view: EditorView, _from: number, _to: number, text: string) => {
          // IME 组合输入期间按 @ 不触发引用面板，也不破坏组合输入。
          if (!shouldTriggerMention(text, composingRef.current)) return false;
          const activeEditor = editorRef.current;
          if (activeEditor && sceneIdRef.current) {
            const selection = resolveSceneSelection(activeEditor, sceneIdRef.current);
            if (selection) onMentionTriggerRef.current?.(selection);
          }
          // 阻止 @ 写入正文。
          return true;
        },
        handlePaste: (_view: EditorView, event: ClipboardEvent) => {
          const text = event.clipboardData?.getData("text/plain") ?? "";
          const html = event.clipboardData?.getData("text/html") ?? "";
          if (plainPasteRef.current) {
            plainPasteRef.current = false;
            event.preventDefault();
            const document = plainTextToCreationDocument(text);
            editorRef.current?.chain().focus().insertContent(document.content as JSONContent[]).run();
            return true;
          }
          const inspection = inspectScenePaste({ html, text });
          if (inspection.verdict === "preview") {
            event.preventDefault();
            setPastePreview({ text: inspection.cleanedText, reason: inspection.reason });
            return true;
          }
          return false;
        }
      },
      onUpdate: ({ editor: activeEditor }) => {
        const json = activeEditor.getJSON();
        if (!validateCreationDocument(json)) return;
        session.edit(json);
        const count = countCharacters(json);
        setCurrentWords(count);
        onStatsChange?.(count);
      },
      onSelectionUpdate: ({ editor: activeEditor }) => {
        updateCurrentBlock(activeEditor, lineFocusRef.current);
        if (sceneIdRef.current) {
          onSelectionChangeRef.current?.(resolveSceneSelection(activeEditor, sceneIdRef.current));
        }
        if (!typewriterRef.current) return;
        const container = activeEditor.view.dom.closest(".writing-scroll");
        if (!(container instanceof HTMLElement)) return;
        const coordinates = activeEditor.view.coordsAtPos(activeEditor.state.selection.from);
        const bounds = container.getBoundingClientRect();
        container.scrollTo({
          top: resolveTypewriterScrollTop({
            scrollTop: container.scrollTop,
            cursorTop: coordinates.top,
            containerTop: bounds.top,
            containerHeight: bounds.height,
            scrollHeight: container.scrollHeight
          })
        });
      }
    },
    [sceneId]
  );

  useEffect(() => {
    editorRef.current = editor;
    return () => {
      editorRef.current = null;
    };
  }, [editor]);

  useEffect(() => {
    // 切场景时 useEditor 会先销毁旧实例再由 useSyncExternalStore 换入新实例：
    // 本次 commit 里 editor 仍指向已销毁的旧实例（view 已随卸载置空），
    // 对新实例的同步要等下一次渲染，因此这里必须跳过，否则旧实例的 commands 为 null 会抛错。
    if (!editor || editor.isDestroyed || !view) return;
    const current = session.getState();
    if (current.sceneId === view.sceneId && current.revision === view.revision) return;
    if (current.sceneId === view.sceneId && current.dirty) return;
    const body = editableDocument(view.body);
    editor.commands.setContent(body as unknown as JSONContent, { emitUpdate: false });
    session.open({ sceneId: view.sceneId, revision: view.revision, body });
    const count = countCharacters(body);
    setCurrentWords(count);
    onStatsChange?.(count);
    updateCurrentBlock(editor, lineFocus);
    onSelectionChangeRef.current?.(resolveSceneSelection(editor, view.sceneId));
  }, [editor, lineFocus, onStatsChange, session, view]);

  useEffect(() => {
    if (!editor) return undefined;
    const dom = editor.view.dom;
    const start = () => {
      composingRef.current = true;
      session.compositionStart();
    };
    const end = () => {
      composingRef.current = false;
      session.compositionEnd();
    };
    dom.addEventListener("compositionstart", start);
    dom.addEventListener("compositionend", end);
    return () => {
      composingRef.current = false;
      dom.removeEventListener("compositionstart", start);
      dom.removeEventListener("compositionend", end);
    };
  }, [editor, session]);

  useEffect(() => {
    if (sessionState.status !== "conflict") setConflictDismissed(false);
  }, [sessionState.status]);

  useEffect(() => {
    // 批次 BB：卸载时先撤销自己那条，再补一次落盘。顺序不能颠倒——saveNow 成功后会话
    // 还会回调两次状态（saving → saved），若那时仍允许发布，已经离开的场景会在 dock 上
    // 留一个「正在保存」再也下不去。
    mountedRef.current = true;
    // 重新挂载时补一次发布：StrictMode 那次假卸载把条目撤掉了，会话本身的状态还在，
    // 不补回来 dock 就哑在「未在编辑」。
    publishSaveStatus(session.getState());
    return () => {
      mountedRef.current = false;
      const left = reportedSceneIdRef.current ?? session.getState().sceneId;
      if (left !== null) useCreationStore.getState().clearSceneSaveStatus(left);
      reportedSceneIdRef.current = null;
      const finish = session.getState().dirty ? session.saveNow() : Promise.resolve(true);
      // dispose 只在「真的没回来」时执行。StrictMode 的假卸载后紧接着会重挂载，
      // 重挂载的 effect 是同步跑的，等这条 finally 落到微任务时 mountedRef 已经是 true；
      // 当时无条件 dispose 会把会话永久打死（edit/saveNow 双双变哑，dev 下正文再也不自动保存）。
      void finish.finally(() => {
        if (mountedRef.current) return;
        session.dispose();
      });
    };
    // publishSaveStatus 只读 ref 与 store（都是稳定引用），依赖只需 session。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  useImperativeHandle(
    ref,
    () => ({
      saveNow: () => session.saveNow(),
      isDirty: () => session.getState().dirty,
      focus: () => editorRef.current?.commands.focus(),
      getSelection: () => {
        const activeEditor = editorRef.current;
        if (!activeEditor || !sceneIdRef.current) return null;
        return resolveSceneSelection(activeEditor, sceneIdRef.current);
      },
      isComposing: () => composingRef.current
    }),
    [session]
  );

  const reloadLatest = useCallback(async () => {
    if (!onReloadScene) return;
    const latest = await onReloadScene();
    if (!latest || !editor || editor.isDestroyed) return;
    const body = editableDocument(latest.body);
    editor.commands.setContent(body as unknown as JSONContent, { emitUpdate: false });
    session.open({ sceneId: latest.sceneId, revision: latest.revision, body });
    setConflictDismissed(true);
  }, [editor, onReloadScene, session]);

  if (!view || !editor) {
    return <div className="scene-editor-placeholder">选择一个场景开始写作。</div>;
  }

  const activeBlock = editor.isActive("quoteLetter")
    ? "quoteLetter"
    : editor.isActive("centeredText")
      ? "centeredText"
      : editor.isActive("authorNote")
        ? "authorNote"
        : "paragraph";

  const insertCleanedPaste = () => {
    if (!pastePreview) return;
    const document = plainTextToCreationDocument(pastePreview.text);
    editor.chain().focus().insertContent(document.content as JSONContent[]).run();
    setPastePreview(undefined);
  };

  return (
    <div className={`scene-editor ${lineFocus ? "scene-editor--line-focus" : ""}`}>
      <div className="scene-editor-toolbar" role="toolbar" aria-label="正文工具">
        <span className="scene-tool-group">
          <button className="scene-tool" type="button" onClick={() => undo(editor.state, editor.view.dispatch)} aria-label="撤销" title="撤销 (Ctrl+Z)"><Undo2 size={15} /></button>
          <button className="scene-tool" type="button" onClick={() => redo(editor.state, editor.view.dispatch)} aria-label="重做" title="重做 (Ctrl+Y)"><Redo2 size={15} /></button>
        </span>
        <span className="scene-tool-group">
          <select className="scene-block-select" value={activeBlock} onChange={(event) => editor.chain().focus().toggleNode(event.target.value, "paragraph").run()} aria-label="块类型">
            <option value="paragraph">段落</option>
            <option value="quoteLetter">引文信件</option>
            <option value="centeredText">居中文本</option>
            <option value="authorNote">作者注</option>
          </select>
          <button className="scene-tool" type="button" onClick={() => editor.chain().focus().insertContent({ type: "sceneBreak" }).run()} aria-label="插入场景分隔"><Sparkles size={15} /></button>
        </span>
        <span className="scene-tool-group">
          <button className={`scene-tool ${editor.isActive("bold") ? "active" : ""}`} type="button" onClick={() => editor.chain().focus().toggleMark("bold").run()} aria-label="加粗" aria-pressed={editor.isActive("bold")}><Bold size={15} /></button>
          <button className={`scene-tool ${editor.isActive("italic") ? "active" : ""}`} type="button" onClick={() => editor.chain().focus().toggleMark("italic").run()} aria-label="斜体" aria-pressed={editor.isActive("italic")}><Italic size={15} /></button>
        </span>
        <span className="scene-tool-group scene-tool-group--end">
          <button className={`scene-tool ${lineFocus ? "active" : ""}`} type="button" onClick={() => { setLineFocus((value) => !value); updateCurrentBlock(editor, !lineFocus); }} aria-label="当前行聚焦" aria-pressed={lineFocus}><ScanLine size={15} /></button>
          <button className={`scene-tool ${typewriter ? "active" : ""}`} type="button" onClick={onToggleTypewriter} aria-label="打字机滚动" aria-pressed={typewriter}><Loader2 size={15} /></button>
          <button className={`scene-tool ${focusMode ? "active" : ""}`} type="button" onClick={onToggleFocusMode} aria-label="专注模式" aria-pressed={focusMode}><Focus size={15} /></button>
        </span>
        <span className={`scene-save-status scene-save-status--${sessionState.status}`} aria-live="polite">
          {sessionState.status === "saving" && <Spinner size={13} label="正文保存中" />}
          {sessionState.status === "saved" && <CheckCircle2 size={13} />}
          {(sessionState.status === "error" || sessionState.status === "conflict") && <XCircle size={13} />}
          {statusLabel[sessionState.status]}
        </span>
      </div>

      {sessionState.status === "conflict" && !conflictDismissed && (
        <div className="scene-conflict-notice" role="alert">
          <div><strong>正文已在别处更新</strong><span>本地草稿仍在编辑器中，本切片不自动合并。</span></div>
          <div className="scene-conflict-actions">
            <Button variant="quiet" onClick={() => setConflictDismissed(true)}>保留本地草稿</Button>
            {onReloadScene && <Button onClick={() => void reloadLatest()}>载入最新版本</Button>}
          </div>
        </div>
      )}
      {sessionState.status === "error" && <div className="scene-save-error" role="alert">{sessionState.error}</div>}

      <EditorContent editor={editor} />

      <footer className="scene-target-progress" aria-label="场景目标进度">
        <span>{currentWords.toLocaleString("zh-CN")} 字</span>
        {targetWords ? (
          <>
            <div className="scene-target-track" aria-hidden="true"><span style={{ width: `${Math.min(100, (currentWords / targetWords) * 100)}%` }} /></div>
            <span>目标 {targetWords.toLocaleString("zh-CN")} · {Math.round((currentWords / targetWords) * 100)}%</span>
          </>
        ) : <span>未设置目标</span>}
      </footer>

      {pastePreview && (
        <PastePreviewDialog
          plainText={pastePreview.text}
          reason={pastePreview.reason}
          onCancel={() => setPastePreview(undefined)}
          onConfirm={insertCleanedPaste}
        />
      )}
    </div>
  );
});
