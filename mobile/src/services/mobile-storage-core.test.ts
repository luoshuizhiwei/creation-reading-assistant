import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MobileInspiration, MobileTag } from "../types/mobile";

const databaseMocks = vi.hoisted(() => ({
  run: vi.fn(async (_sql: string, ..._params: unknown[]) => undefined),
  query: vi.fn(async (_sql: string, ..._params: unknown[]) => [] as unknown[])
}));

vi.mock("../storage/mobile-database", () => ({
  openMobileDatabase: vi.fn(async () => ({
    db: {},
    run: databaseMocks.run,
    query: databaseMocks.query,
    close: vi.fn(async () => undefined)
  }))
}));

const values = new Map<string, string>();
Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear(),
    key: (index: number) => Array.from(values.keys())[index] ?? null,
    get length() { return values.size; }
  }
});

import { emptySnapshot, initializeMobileStorage, saveMobileSnapshot } from "./mobile-storage-core";

function inspiration(id: string): MobileInspiration {
  const now = new Date().toISOString();
  return {
    id,
    title: "测试灵感",
    body: "正文",
    type: "note",
    status: "inbox",
    tags: [],
    platformTags: [],
    variants: [],
    revision: 1,
    deviceId: "test-device",
    createdAt: now,
    updatedAt: now
  };
}

describe("mobile SQLite snapshot persistence", () => {
  beforeEach(() => {
    values.clear();
    databaseMocks.run.mockClear();
    databaseMocks.query.mockReset();
    databaseMocks.query.mockResolvedValue([]);
  });

  it("tombstones inspirations that were deleted from the snapshot", async () => {
    await saveMobileSnapshot({ ...emptySnapshot(), inspirations: [inspiration("idea-1")] });
    databaseMocks.run.mockClear();

    await saveMobileSnapshot(emptySnapshot());

    expect(databaseMocks.run.mock.calls.some(([sql]) => String(sql).includes("UPDATE inspirations SET deleted_at"))).toBe(true);
  });

  it("loads manager-only SQLite data instead of discarding it", async () => {
    const now = new Date().toISOString();
    const tag: MobileTag = {
      id: "tag-1",
      name: "灵感",
      type: "inspiration",
      createdAt: now,
      updatedAt: now,
      revision: 1,
      deviceId: "test-device"
    };
    values.set("creation-reading-assistant-mobile-sqlite-migrated", "done");
    databaseMocks.query.mockImplementation(async (sql: string) =>
      sql.includes("FROM tags") ? [{ payload: JSON.stringify(tag) }] : []
    );

    const snapshot = await initializeMobileStorage();

    expect(snapshot.tags).toEqual([tag]);
  });
});
