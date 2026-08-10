import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from "react";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import type { JSONContent } from "@tiptap/core";
import { redo, undo } from "@tiptap/pm/history";
import { Bold, CheckCircle2, Focus, Italic, Loader2, Redo2, ScanLine, Sparkles, Undo2, XCircle } from "lucide-react";
import { Button } from "@/components/ui";
import { PastePreviewDialog } from "@/features/creation/editor/PastePreviewDialog";
import {
  createNovelEditorExtensions,
  emptyDocument,
  validateCreationDocument
} from "@/features/creation/editor/editor-schema";
import { inspectScenePaste, plainTextToCreationDocument } from "@/features/creation/editor/paste-clean";
import {
  createSceneDocumentSession,
  type SceneDocumentSession,
  type SceneDocumentSessionState
} from "@/features/creation/editor/scene-document-session";
import type { CreationDocument, SceneBodyView, SceneSaveResponse } from "@/types/creation";

const INITIAL_SESSION_STATE: SceneDocumentSessionState = {
  status: "idle",
  sceneId: null,
  revision: null,
  dirty: false,
  error: null,
  currentRevision: null,
  lastSavedAt: null
};

const statusLabel: Record<SceneDocumentSessionState["status"], string> = {
  idle: "已保存",
  composing: "正在输入",
  dirty: "未保存",
  saving: "正在保存",
  saved: "已保存",
  error: "保存失败",
  conflict: "正文冲突"
};

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
  host.querySelectorAll(".is-current-line").forEach((element) => element.classList.remove("is-current-line"));
  if (!enabled) return;
  const position = editor.view.domAtPos(editor.state.selection.from);
  const element = position.node.nodeType === Node.TEXT_NODE ? position.node.parentElement : (position.node as HTMLElement);
  const block = element?.closest("p, blockquote, .centered-text, .author-note, hr.scene-break");
  if (block && host.contains(block)) block.classList.add("is-current-line");
}

export interface SceneEditorHandle {
  saveNow(): Promise<boolean>;
  isDirty(): boolean;
}

interface SceneEditorProps {
  view?: SceneBodyView;
  onSave(sceneId: string, baseRevision: number, body: CreationDocument): Promise<SceneSaveResponse | undefined>;
  onReloadScene(): Promise<SceneBodyView | null | undefined>;
  onStatsChange?(characters: number): void;
  focusMode: boolean;
  onToggleFocusMode(): void;
  typewriter: boolean;
  onToggleTypewriter(): void;
}

export const SceneEditor = forwardRef<SceneEditorHandle, SceneEditorProps>(function SceneEditor(
  { view, onSave, onReloadScene, onStatsChange, focusMode, onToggleFocusMode, typewriter, onToggleTypewriter },
  ref
) {
  const [sessionState, setSessionState] = useState(INITIAL_SESSION_STATE);
  const [lineFocus, setLineFocus] = useState(true);
  const [pastePreview, setPastePreview] = useState<{ text: string; reason: string | null }>();
  const [conflictDismissed, setConflictDismissed] = useState(false);
  const editorRef = useRef<Editor | null>(null);
  const sessionRef = useRef<SceneDocumentSession>();
  const onSaveRef = useRef(onSave);
  const mountedRef = useRef(true);
  const plainPasteRef = useRef(false);
  const typewriterRef = useRef(typewriter);
  const lineFocusRef = useRef(lineFocus);
  onSaveRef.current = onSave;
  typewriterRef.current = typewriter;
  lineFocusRef.current = lineFocus;

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
        if (mountedRef.current) setSessionState(state);
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
        handleKeyDown: (_view, event) => {
          const modifier = event.ctrlKey || event.metaKey;
          if (modifier && event.key.toLowerCase() === "s") {
            event.preventDefault();
            void session.saveNow();
            return true;
          }
          if (modifier && event.shiftKey && event.key.toLowerCase() === "v") plainPasteRef.current = true;
          return false;
        },
        handlePaste: (_view, event) => {
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
        onStatsChange?.(countCharacters(json));
      },
      onSelectionUpdate: ({ editor: activeEditor }) => {
        updateCurrentBlock(activeEditor, lineFocusRef.current);
        if (!typewriterRef.current) return;
        const container = activeEditor.view.dom.closest(".writing-scroll");
        if (!(container instanceof HTMLElement)) return;
        const coordinates = activeEditor.view.coordsAtPos(activeEditor.state.selection.from);
        const bounds = container.getBoundingClientRect();
        container.scrollTo({ top: container.scrollTop + coordinates.top - bounds.top - bounds.height / 2 });
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
    if (!editor || !view) return;
    const current = session.getState();
    if (current.sceneId === view.sceneId && current.revision === view.revision) return;
    if (current.sceneId === view.sceneId && current.dirty) return;
    const body = editableDocument(view.body);
    editor.commands.setContent(body as unknown as JSONContent, { emitUpdate: false });
    session.open({ sceneId: view.sceneId, revision: view.revision, body });
    onStatsChange?.(countCharacters(body));
    updateCurrentBlock(editor, lineFocus);
  }, [editor, lineFocus, onStatsChange, session, view]);

  useEffect(() => {
    if (!editor) return undefined;
    const dom = editor.view.dom;
    const start = () => session.compositionStart();
    const end = () => session.compositionEnd();
    dom.addEventListener("compositionstart", start);
    dom.addEventListener("compositionend", end);
    return () => {
      dom.removeEventListener("compositionstart", start);
      dom.removeEventListener("compositionend", end);
    };
  }, [editor, session]);

  useEffect(() => {
    if (sessionState.status !== "conflict") setConflictDismissed(false);
  }, [sessionState.status]);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      const finish = session.getState().dirty ? session.saveNow() : Promise.resolve(true);
      void finish.finally(() => session.dispose());
    };
  }, [session]);

  useImperativeHandle(
    ref,
    () => ({
      saveNow: () => session.saveNow(),
      isDirty: () => session.getState().dirty
    }),
    [session]
  );

  const reloadLatest = useCallback(async () => {
    const latest = await onReloadScene();
    if (!latest || !editor) return;
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
          {sessionState.status === "saving" && <Loader2 size={13} className="scene-save-spin" />}
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
            <Button onClick={() => void reloadLatest()}>载入最新版本</Button>
          </div>
        </div>
      )}
      {sessionState.status === "error" && <div className="scene-save-error" role="alert">{sessionState.error}</div>}

      <EditorContent editor={editor} />

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
