import { describe, expect, it, vi } from "vitest";
import type { ReadingLocation, ReadingProgress } from "../../../../../src/types/library";
import { ReaderProgressRepository } from "./ReaderProgressRepository";
import type { ReaderLocator } from "./types";

const locator = (progression: number): ReaderLocator => ({
  version: 2,
  bookId: "book-1",
  format: "txt",
  progression,
  textOffset: Math.round(progression * 1_000),
  updatedAt: 1_000 + progression * 100
});

describe("ReaderProgressRepository", () => {
  it("migrates old progress and writes both V2 and compatible legacy locations", async () => {
    const oldProgress = {
      bookId: "book-1",
      currentLocation: {
        format: "txt",
        mode: "text-anchor",
        progressPercent: 25,
        precision: "exact",
        text: { charOffset: 250 },
        updatedAt: "2026-07-16T00:00:00.000Z"
      }
    } as ReadingProgress;
    const save = vi.fn(async (_bookId: string, _locator: ReaderLocator, _legacy: ReadingLocation) => undefined);
    const repository = new ReaderProgressRepository({ load: async () => oldProgress, save }, 10_000);
    const loaded = await repository.load("book-1");
    expect(loaded.locator).toMatchObject({ progression: 0.25, textOffset: 250 });

    const lease = repository.acquire("book-1", "engine-a", loaded.legacy);
    lease.stage(locator(0.5));
    await lease.flush();
    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0][1]).toMatchObject({ progression: 0.5 });
    expect(save.mock.calls[0][2]).toMatchObject({ progressPercent: 50, text: { charOffset: 500 } });
    await lease.release();
  });

  it("allows only one engine to persist a book at a time", async () => {
    const repository = new ReaderProgressRepository({ load: async () => undefined, save: async () => undefined });
    const first = repository.acquire("book-1", "legacy");
    expect(() => repository.acquire("book-1", "v2")).toThrow(/另一个阅读引擎/);
    await first.release({ flush: false });
    expect(() => repository.acquire("book-1", "v2")).not.toThrow();
  });

  it("coalesces staged locations and flushes the newest one", async () => {
    const save = vi.fn(async (_bookId: string, _locator: ReaderLocator, _legacy: ReadingLocation) => undefined);
    const repository = new ReaderProgressRepository({ load: async () => undefined, save }, 10_000);
    const lease = repository.acquire("book-1", "v2");
    lease.stage(locator(0.1));
    lease.stage(locator(0.2));
    lease.stage(locator(0.3));
    await lease.flush();
    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0][1].progression).toBe(0.3);
    await lease.release();
  });
});
