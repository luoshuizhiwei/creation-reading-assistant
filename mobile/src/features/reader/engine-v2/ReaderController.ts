import { ReaderEngineFactory } from "./ReaderEngineFactory";
import { ReaderProgressRepository, type ReaderProgressLease } from "./ReaderProgressRepository";
import { idleReaderEngineState, readerStateCanPersist } from "./state";
import {
  ReaderEngineError,
  type ReaderEngine,
  type ReaderEngineVersion,
  type ReaderLocator,
  type ReaderOpenInput,
  type ReaderPreferences,
  type ReaderState
} from "./types";

export interface ReaderControllerLogger {
  info(event: string, detail: Record<string, unknown>): void;
  warn(event: string, detail: Record<string, unknown>): void;
}

interface ActiveReader {
  taskId: string;
  engine: ReaderEngine;
  lease: ReaderProgressLease;
  unsubscribeLocation: () => void;
}

const silentLogger: ReaderControllerLogger = {
  info: () => undefined,
  warn: () => undefined
};

function toReaderError(error: unknown, fallbackCode: ReaderEngineError["code"]): ReaderEngineError {
  if (error instanceof ReaderEngineError) return error;
  return new ReaderEngineError(
    fallbackCode,
    error instanceof Error ? error.message : "阅读器发生未知错误。",
    { cause: error }
  );
}

export class ReaderController {
  private state: ReaderState = idleReaderEngineState;
  private readonly listeners = new Set<(state: ReaderState) => void>();
  private active?: ActiveReader;
  private openingEngine?: ReaderEngine;
  private taskAbort?: AbortController;
  private currentTaskId?: string;
  private taskSequence = 0;
  private destroyed = false;

  constructor(
    private readonly factory: ReaderEngineFactory,
    private readonly progressRepository: ReaderProgressRepository,
    private readonly logger: ReaderControllerLogger = silentLogger
  ) {}

  getState(): ReaderState {
    return this.state;
  }

  subscribe(listener: (state: ReaderState) => void): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  private setState(state: ReaderState): void {
    if (this.destroyed) return;
    this.state = state;
    for (const listener of this.listeners) listener(state);
  }

  private nextTaskId(bookId: string): string {
    this.taskSequence += 1;
    return `${bookId}:${this.taskSequence}`;
  }

  private isCurrentTask(taskId: string, abort: AbortController): boolean {
    return !this.destroyed && this.taskAbort === abort && !abort.signal.aborted && this.currentTaskId === taskId;
  }

  private assertCurrentTask(taskId: string, abort: AbortController): void {
    if (!this.isCurrentTask(taskId, abort)) {
      throw new ReaderEngineError("stale-task", "过期的阅读任务已取消。", { retryable: false });
    }
  }

  private async destroyEngine(engine?: ReaderEngine): Promise<void> {
    if (!engine) return;
    try {
      await engine.destroy();
    } catch (error) {
      this.logger.warn("reader_engine_destroy_failed", {
        engineId: engine.id,
        code: "destroy-failed",
        message: error instanceof Error ? error.message : String(error)
      });
    }
  }

  private async releaseActive(options: { flush: boolean }): Promise<void> {
    const active = this.active;
    this.active = undefined;
    if (!active) return;
    active.unsubscribeLocation();
    if (options.flush && readerStateCanPersist(this.state)) {
      try {
        const locator = await active.engine.getCurrentLocator();
        if (locator) active.lease.stage(locator);
        await active.lease.flush();
      } catch (error) {
        this.logger.warn("reader_progress_final_flush_failed", {
          engineId: active.engine.id,
          bookId: active.lease.bookId,
          message: error instanceof Error ? error.message : String(error)
        });
      }
    }
    await active.lease.release({ flush: false }).catch(() => undefined);
    await this.destroyEngine(active.engine);
  }

  private async cancelCurrentTask(options: { flush: boolean }): Promise<void> {
    this.taskSequence += 1;
    this.taskAbort?.abort();
    this.taskAbort = undefined;
    this.currentTaskId = undefined;
    const opening = this.openingEngine;
    this.openingEngine = undefined;
    await this.releaseActive(options);
    if (opening && opening !== this.active?.engine) await this.destroyEngine(opening);
  }

  async open(
    input: Omit<ReaderOpenInput, "signal">,
    container: HTMLElement,
    version: ReaderEngineVersion = "legacy"
  ): Promise<ReaderState> {
    if (this.destroyed) throw new ReaderEngineError("aborted", "阅读控制器已经销毁。", { retryable: false });
    await this.cancelCurrentTask({ flush: true });
    const taskId = this.nextTaskId(input.book.id);
    const abort = new AbortController();
    this.taskAbort = abort;
    this.currentTaskId = taskId;
    this.setState({ status: "loading", bookId: input.book.id, taskId, phase: "validating", engineVersion: version });

    if (!input.content.trim()) {
      const error = new ReaderEngineError("empty-content", "书籍正文为空，不能进入阅读状态。", { retryable: false });
      this.setState({ status: "error", bookId: input.book.id, taskId, code: error.code, message: error.message, retryable: error.retryable });
      return this.state;
    }

    let loadedProgress: Awaited<ReturnType<ReaderProgressRepository["load"]>>;
    try {
      loadedProgress = await this.progressRepository.load(input.book.id);
    } catch (error) {
      if (!this.isCurrentTask(taskId, abort)) return this.state;
      const readerError = toReaderError(error, "open-failed");
      this.setState({
        status: "error",
        bookId: input.book.id,
        taskId,
        code: readerError.code,
        message: `读取阅读进度失败：${readerError.message}`,
        retryable: readerError.retryable
      });
      return this.state;
    }
    if (!this.isCurrentTask(taskId, abort)) return this.state;
    let lastError: ReaderEngineError | undefined;
    let candidates: ReturnType<ReaderEngineFactory["candidates"]>;
    try {
      candidates = this.factory.candidates(input.format, version);
    } catch (error) {
      const readerError = toReaderError(error, "unsupported-format");
      this.setState({
        status: "error",
        bookId: input.book.id,
        taskId,
        code: readerError.code,
        message: readerError.message,
        retryable: readerError.retryable
      });
      return this.state;
    }

    for (let index = 0; index < candidates.length; index += 1) {
      const candidate = candidates[index];
      const engine = candidate.engine;
      this.openingEngine = engine;
      try {
        this.setState({ status: "loading", bookId: input.book.id, taskId, phase: "opening", engineVersion: version });
        await Promise.resolve();
        this.assertCurrentTask(taskId, abort);
        this.setState({ status: "loading", bookId: input.book.id, taskId, phase: "parsing", engineVersion: version });
        const publication = await engine.open({ ...input, signal: abort.signal });
        this.assertCurrentTask(taskId, abort);
        if (!publication.readingOrder.length) {
          throw new ReaderEngineError("invalid-publication", "书籍没有可读取的内容单元。", { retryable: false });
        }
        this.setState({ status: "loading", bookId: input.book.id, taskId, phase: "mounting", engineVersion: version });
        await engine.mount(container);
        this.assertCurrentTask(taskId, abort);
        this.setState({ status: "loading", bookId: input.book.id, taskId, phase: "restoring-location", engineVersion: version });
        await engine.restore(loadedProgress.locator);
        this.assertCurrentTask(taskId, abort);

        const lease = this.progressRepository.acquire(input.book.id, engine.id, loadedProgress.legacy);
        const unsubscribeLocation = engine.on("location", (locator) => {
          if (!readerStateCanPersist(this.state) || this.state.taskId !== taskId || this.active?.engine !== engine) return;
          try {
            lease.stage(locator);
          } catch (error) {
            this.logger.warn("reader_progress_stage_failed", {
              engineId: engine.id,
              bookId: input.book.id,
              message: error instanceof Error ? error.message : String(error)
            });
          }
        });
        this.active = { taskId, engine, lease, unsubscribeLocation };
        this.openingEngine = undefined;
        this.setState({
          status: "ready",
          bookId: input.book.id,
          taskId,
          engineVersion: candidate.version,
          engine,
          publication
        });
        this.logger.info("reader_engine_ready", {
          bookId: input.book.id,
          engineId: engine.id,
          engineVersion: candidate.version
        });
        return this.state;
      } catch (error) {
        lastError = toReaderError(error, "open-failed");
        this.openingEngine = undefined;
        await this.destroyEngine(engine);
        if (lastError.code === "stale-task" || abort.signal.aborted) return this.state;
        const canFallback = version === "auto" && index < candidates.length - 1;
        if (canFallback) {
          this.logger.warn("reader_engine_fallback", {
            bookId: input.book.id,
            from: candidate.version,
            code: lastError.code,
            message: lastError.message
          });
          continue;
        }
        break;
      }
    }

    this.assertCurrentTask(taskId, abort);
    const error = lastError ?? new ReaderEngineError("open-failed", "阅读器无法打开这本书。");
    this.setState({
      status: "error",
      bookId: input.book.id,
      taskId,
      code: error.code,
      message: error.message,
      retryable: error.retryable
    });
    return this.state;
  }

  async applyPreferences(preferences: ReaderPreferences): Promise<void> {
    const active = this.active;
    if (!active || !readerStateCanPersist(this.state)) return;
    const locator = await active.engine.getCurrentLocator();
    await active.engine.applyPreferences(preferences);
    if (locator) await active.engine.restore(locator);
  }

  async flush(): Promise<void> {
    const active = this.active;
    if (!active || !readerStateCanPersist(this.state)) return;
    const locator = await active.engine.getCurrentLocator();
    if (locator) active.lease.stage(locator);
    await active.lease.flush();
  }

  async close(): Promise<void> {
    await this.cancelCurrentTask({ flush: true });
    this.setState(idleReaderEngineState);
  }

  async destroy(): Promise<void> {
    if (this.destroyed) return;
    await this.cancelCurrentTask({ flush: true });
    this.destroyed = true;
    this.listeners.clear();
    this.state = idleReaderEngineState;
  }
}
