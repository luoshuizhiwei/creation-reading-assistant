import type { CreationDocument, SceneSaveResponse } from "@/types/creation";

/** 自动保存防抖时长（毫秒），对应 P1-P03「停止输入后 1 秒内开始提交」。 */
export const SCENE_AUTOSAVE_DEBOUNCE_MS = 800;

export type SceneSessionStatus =
  | "idle"
  | "composing"
  | "dirty"
  | "saving"
  | "saved"
  | "error"
  | "conflict";

export interface SceneDocumentSessionState {
  status: SceneSessionStatus;
  sceneId: string | null;
  /** 最近一次确认落盘的 revision，也是下一次保存的 baseRevision。 */
  revision: number | null;
  /** 是否存在未落盘改动。 */
  dirty: boolean;
  error: string | null;
  /** 冲突时服务端当前的 revision。 */
  currentRevision: number | null;
  lastSavedAt: number | null;
}

export interface SceneSaveInput {
  sceneId: string;
  baseRevision: number;
  body: CreationDocument;
}

export interface SceneDocumentSessionDeps {
  save(input: SceneSaveInput): Promise<SceneSaveResponse>;
  setTimer(fn: () => void, ms: number): unknown;
  clearTimer(id: unknown): void;
  now(): number;
  onState(state: SceneDocumentSessionState): void;
}

export interface SceneDocumentSession {
  open(input: { sceneId: string; revision: number; body: CreationDocument }): void;
  edit(body: CreationDocument): void;
  compositionStart(): void;
  compositionEnd(): void;
  saveNow(): Promise<boolean>;
  dispose(): void;
  getState(): SceneDocumentSessionState;
}

const CONFLICT_MESSAGE = "正文已在别处更新，请刷新后重试。";

function errorMessageFor(code: string): string {
  switch (code) {
    case "closed":
      return "创作工作区正在维护，请稍后重试。";
    case "not-found":
      return "场景不存在，可能已被删除。";
    case "invalid-input":
      return "保存内容无效，请检查后重试。";
    case "integrity":
      return "数据校验失败，保存已中止。";
    default:
      return "保存失败，请重试。";
  }
}

function documentsEqual(a: CreationDocument, b: CreationDocument): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

class SceneDocumentSessionImpl implements SceneDocumentSession {
  private readonly deps: SceneDocumentSessionDeps;
  private state: SceneDocumentSessionState;
  private latest: CreationDocument;
  private lastSavedBody: CreationDocument | null = null;
  private revision: number | null = null;
  private sceneId: string | null = null;
  private dirty = false;
  private isComposing = false;
  private timerId: unknown = null;
  private saveInFlight: Promise<boolean> | null = null;
  private disposed = false;

  constructor(deps: SceneDocumentSessionDeps) {
    this.deps = deps;
    this.state = {
      status: "idle",
      sceneId: null,
      revision: null,
      dirty: false,
      error: null,
      currentRevision: null,
      lastSavedAt: null
    };
    this.latest = { type: "doc", content: [] };
  }

  open(input: { sceneId: string; revision: number; body: CreationDocument }): void {
    if (this.disposed) {
      return;
    }
    this.cancelTimer();
    this.sceneId = input.sceneId;
    this.revision = input.revision;
    this.latest = input.body;
    this.lastSavedBody = input.body;
    this.dirty = false;
    this.isComposing = false;
    this.setState({
      status: "saved",
      sceneId: input.sceneId,
      revision: input.revision,
      dirty: false,
      error: null,
      currentRevision: null,
      lastSavedAt: this.deps.now()
    });
  }

  edit(body: CreationDocument): void {
    if (this.disposed) {
      return;
    }
    if (documentsEqual(body, this.latest)) {
      return;
    }
    this.latest = body;
    if (this.lastSavedBody !== null && documentsEqual(body, this.lastSavedBody)) {
      this.dirty = false;
      this.cancelTimer();
      this.setState({ status: this.isComposing ? "composing" : "saved", dirty: false, error: null });
      return;
    }
    this.dirty = true;
    if (this.isComposing) {
      this.setState({ status: "composing", dirty: true });
      return;
    }
    if (this.saveInFlight) {
      this.setState({ status: "saving", dirty: true });
      return;
    }
    this.setState({ status: "dirty", dirty: true, error: null });
    this.scheduleDebounce();
  }

  compositionStart(): void {
    if (this.disposed) {
      return;
    }
    this.isComposing = true;
    this.cancelTimer();
    this.setState({ status: "composing", dirty: this.dirty });
  }

  compositionEnd(): void {
    if (this.disposed) {
      return;
    }
    this.isComposing = false;
    if (this.dirty) {
      if (this.saveInFlight) {
        this.setState({ status: "saving", dirty: true });
      } else {
        this.setState({ status: "dirty", dirty: true });
        this.scheduleDebounce();
      }
    } else {
      this.setState({ status: this.lastSavedBody ? "saved" : "idle", dirty: false });
    }
  }

  saveNow(): Promise<boolean> {
    if (this.disposed) {
      return Promise.resolve(false);
    }
    this.cancelTimer();
    if (this.saveInFlight) {
      return this.saveInFlight.then(() => {
        if (this.disposed) {
          return false;
        }
        return this.dirty ? this.saveNow() : true;
      });
    }
    if (
      !this.dirty ||
      (this.lastSavedBody !== null && documentsEqual(this.latest, this.lastSavedBody))
    ) {
      return Promise.resolve(true);
    }
    return this.performSave();
  }

  dispose(): void {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    this.cancelTimer();
  }

  getState(): SceneDocumentSessionState {
    return { ...this.state };
  }

  private scheduleDebounce(): void {
    this.cancelTimer();
    this.timerId = this.deps.setTimer(() => {
      this.timerId = null;
      void this.runAutosave();
    }, SCENE_AUTOSAVE_DEBOUNCE_MS);
  }

  private cancelTimer(): void {
    if (this.timerId !== null) {
      this.deps.clearTimer(this.timerId);
      this.timerId = null;
    }
  }

  private runAutosave(): void {
    if (this.disposed || !this.dirty || this.saveInFlight) {
      return;
    }
    void this.performSave();
  }

  private performSave(): Promise<boolean> {
    const sceneId = this.sceneId;
    const baseRevision = this.revision;
    const bodyToSave = this.latest;
    if (sceneId === null || baseRevision === null) {
      this.dirty = true;
      this.setState({ status: "error", dirty: true, error: "场景尚未加载完成。" });
      return Promise.resolve(false);
    }
    this.setState({ status: "saving", dirty: true });
    const promise = (async (): Promise<boolean> => {
      try {
        const result = await this.deps.save({ sceneId, baseRevision, body: bodyToSave });
        if (this.disposed) {
          return false;
        }
        if (result.ok) {
          this.revision = result.result.revision;
          this.lastSavedBody = bodyToSave;
          if (!documentsEqual(this.latest, bodyToSave)) {
            this.dirty = true;
            if (this.isComposing) {
              this.setState({
                status: "composing",
                dirty: true,
                revision: result.result.revision
              });
            } else {
              this.setState({
                status: "dirty",
                dirty: true,
                error: null,
                revision: result.result.revision
              });
              this.scheduleDebounce();
            }
          } else {
            this.dirty = false;
            this.setState({
              status: this.isComposing ? "composing" : "saved",
              revision: result.result.revision,
              dirty: false,
              error: null,
              currentRevision: null,
              lastSavedAt: this.deps.now()
            });
          }
          return true;
        }
        this.dirty = true;
        if (result.error.code === "revision-mismatch") {
          this.setState({
            status: "conflict",
            dirty: true,
            error: result.error.message || CONFLICT_MESSAGE,
            currentRevision: result.error.currentRevision ?? null
          });
        } else {
          this.setState({
            status: "error",
            dirty: true,
            error: result.error.message || errorMessageFor(result.error.code),
            currentRevision: null
          });
        }
        return false;
      } catch (error) {
        if (this.disposed) {
          return false;
        }
        this.dirty = true;
        this.setState({
          status: "error",
          dirty: true,
          error: error instanceof Error ? error.message : String(error)
        });
        return false;
      } finally {
        this.saveInFlight = null;
      }
    })();
    this.saveInFlight = promise;
    return promise;
  }

  private setState(patch: Partial<SceneDocumentSessionState>): void {
    this.state = { ...this.state, ...patch };
    this.deps.onState({ ...this.state });
  }
}

export function createSceneDocumentSession(deps: SceneDocumentSessionDeps): SceneDocumentSession {
  return new SceneDocumentSessionImpl(deps);
}
