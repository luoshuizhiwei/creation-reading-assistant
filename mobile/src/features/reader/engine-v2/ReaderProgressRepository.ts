import type { ReadingLocation, ReadingProgress } from "../../../../../src/types/library";
import { legacyLocationFromLocator, locatorFromLegacyLocation, normalizeReaderLocator, serializeReaderLocator } from "./locator";
import { ReaderEngineError, type ReaderLocator } from "./types";

export interface ReaderProgressPersistence {
  load(bookId: string): Promise<ReadingProgress | undefined>;
  save(bookId: string, locator: ReaderLocator, legacyLocation: ReadingLocation): Promise<void>;
}

export interface ReaderProgressLease {
  readonly bookId: string;
  readonly ownerId: string;
  stage(locator: ReaderLocator): void;
  flush(): Promise<void>;
  release(options?: { flush?: boolean }): Promise<void>;
}

interface LeaseState {
  bookId: string;
  ownerId: string;
  previousLocation?: ReadingLocation;
  pending?: ReaderLocator;
  lastSaved?: string;
  timer?: ReturnType<typeof setTimeout>;
  writeQueue: Promise<void>;
  released: boolean;
}

export class ReaderProgressRepository {
  private readonly leases = new Map<string, LeaseState>();

  constructor(
    private readonly persistence: ReaderProgressPersistence,
    private readonly throttleMs = 1_500
  ) {}

  async load(bookId: string): Promise<{ locator?: ReaderLocator; legacy?: ReadingLocation }> {
    const progress = await this.persistence.load(bookId);
    return {
      locator: locatorFromLegacyLocation(bookId, progress?.currentLocation),
      legacy: progress?.currentLocation
    };
  }

  acquire(bookId: string, ownerId: string, previousLocation?: ReadingLocation): ReaderProgressLease {
    const existing = this.leases.get(bookId);
    if (existing && !existing.released && existing.ownerId !== ownerId) {
      throw new ReaderEngineError(
        "open-failed",
        `书籍 ${bookId} 已由另一个阅读引擎持有进度写入权。`,
        { retryable: false }
      );
    }
    const state: LeaseState = existing?.ownerId === ownerId
      ? existing
      : { bookId, ownerId, previousLocation, writeQueue: Promise.resolve(), released: false };
    state.released = false;
    state.previousLocation = previousLocation ?? state.previousLocation;
    this.leases.set(bookId, state);

    const assertActive = () => {
      if (state.released || this.leases.get(bookId) !== state) {
        throw new ReaderEngineError("stale-task", "阅读进度写入权已失效。", { retryable: false });
      }
    };

    const flush = async () => {
      assertActive();
      if (state.timer) {
        clearTimeout(state.timer);
        state.timer = undefined;
      }
      const pending = state.pending;
      if (!pending) return;
      const serialized = serializeReaderLocator(pending);
      if (serialized === state.lastSaved) {
        state.pending = undefined;
        return;
      }
      state.pending = undefined;
      state.writeQueue = state.writeQueue.then(async () => {
        assertActive();
        const legacy = legacyLocationFromLocator(pending, state.previousLocation);
        await this.persistence.save(bookId, pending, legacy);
        state.previousLocation = legacy;
        state.lastSaved = serialized;
      });
      await state.writeQueue;
    };

    return {
      bookId,
      ownerId,
      stage: (locator) => {
        assertActive();
        if (locator.bookId !== bookId) {
          throw new ReaderEngineError("navigation-failed", "不能把其他书籍的位置写入当前阅读进度。", { retryable: false });
        }
        state.pending = normalizeReaderLocator(locator);
        if (!state.timer) {
          state.timer = setTimeout(() => {
            state.timer = undefined;
            void flush().catch(() => undefined);
          }, this.throttleMs);
        }
      },
      flush,
      release: async (options = {}) => {
        if (state.released) return;
        if (options.flush !== false && state.pending) await flush();
        if (state.timer) clearTimeout(state.timer);
        state.timer = undefined;
        state.released = true;
        if (this.leases.get(bookId) === state) this.leases.delete(bookId);
      }
    };
  }

  hasActiveWriter(bookId: string): boolean {
    const lease = this.leases.get(bookId);
    return Boolean(lease && !lease.released);
  }
}
