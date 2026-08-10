import { describe, expect, it } from "vitest";
import {
  createSceneDocumentSession,
  SCENE_AUTOSAVE_DEBOUNCE_MS
} from "@/features/creation/editor/scene-document-session";
import type {
  SceneDocumentSession,
  SceneDocumentSessionDeps,
  SceneDocumentSessionState,
  SceneSaveInput
} from "@/features/creation/editor/scene-document-session";
import type { CreationDocument, SceneSaveResponse } from "@/types/creation";

function doc(text: string): CreationDocument {
  return {
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text }] }]
  };
}

interface FakeTimer {
  fn: () => void;
  ms: number;
}

interface Harness {
  session: SceneDocumentSession;
  saves: SceneSaveInput[];
  timers: Map<number, FakeTimer>;
  states: SceneDocumentSessionState[];
  fireTimer: (id: number) => Promise<void>;
  flush: () => Promise<void>;
  clock: () => number;
  failNextSave: (error: Error) => void;
}

function createHarness(options?: {
  save?: (input: SceneSaveInput) => Promise<SceneSaveResponse>;
}): Harness {
  const saves: SceneSaveInput[] = [];
  const timers = new Map<number, FakeTimer>();
  const states: SceneDocumentSessionState[] = [];
  let clockValue = 1_000;
  let nextId = 1;
  let forcedError: Error | null = null;

  const defaultSave = async (input: SceneSaveInput) => {
    saves.push(input);
    return {
      ok: true as const,
      result: {
        commandType: "scene.updateBody" as const,
        sequence: 1,
        projectId: "project-1",
        sceneId: input.sceneId,
        revision: input.baseRevision + 1
      }
    };
  };

  const deps: SceneDocumentSessionDeps = {
    save: async (input) => {
      if (forcedError) {
        const error = forcedError;
        forcedError = null;
        throw error;
      }
      return options?.save ? options.save(input) : defaultSave(input);
    },
    setTimer: (fn, ms) => {
      const id = nextId++;
      timers.set(id, { fn, ms });
      return id;
    },
    clearTimer: (id) => {
      timers.delete(id as number);
    },
    now: () => clockValue,
    onState: (state) => states.push(state)
  };

  const session = createSceneDocumentSession(deps);

  const flush = async (turns = 8) => {
    for (let i = 0; i < turns; i++) {
      await Promise.resolve();
    }
  };

  const fireTimer = async (id: number) => {
    const timer = timers.get(id);
    expect(timer).toBeDefined();
    timers.delete(id);
    timer!.fn();
    await flush();
  };

  return {
    session,
    saves,
    timers,
    states,
    fireTimer,
    flush,
    clock: () => clockValue,
    failNextSave: (error) => {
      forcedError = error;
    }
  };
}

function openSession(h: Harness, overrides?: { revision?: number; body?: CreationDocument }): void {
  h.session.open({
    sceneId: "scene-1",
    revision: overrides?.revision ?? 3,
    body: overrides?.body ?? doc("初始")
  });
}

describe("createSceneDocumentSession", () => {
  it("IME 组合输入期间编辑不触发任何保存，compositionEnd 后才防抖提交", async () => {
    const h = createHarness();
    openSession(h);
    expect(h.timers.size).toBe(0);
    expect(h.saves).toHaveLength(0);

    h.session.compositionStart();
    h.session.edit(doc("拼"));
    h.session.edit(doc("拼音"));
    h.session.edit(doc("拼音输"));

    expect(h.timers.size).toBe(0);
    expect(h.saves).toHaveLength(0);
    expect(h.states.at(-1)?.status).toBe("composing");

    h.session.compositionEnd();
    expect(h.timers.size).toBe(1);
    await h.flush();
    expect(h.saves).toHaveLength(0);

    await h.fireTimer([...h.timers.keys()][0]);
    expect(h.saves).toHaveLength(1);
    expect(h.saves[0].body).toEqual(doc("拼音输"));
    expect(h.saves[0].baseRevision).toBe(3);
  });

  it("自动保存使用 800ms 防抖，等待期内多次编辑只提交最新正文", async () => {
    const h = createHarness();
    openSession(h);

    h.session.edit(doc("一"));
    h.session.edit(doc("一二"));
    h.session.edit(doc("一二三"));

    expect(h.timers.size).toBe(1);
    expect([...h.timers.values()][0].ms).toBe(SCENE_AUTOSAVE_DEBOUNCE_MS);
    await h.flush();
    expect(h.saves).toHaveLength(0);

    await h.fireTimer([...h.timers.keys()][0]);
    expect(h.saves).toHaveLength(1);
    expect(h.saves[0].body).toEqual(doc("一二三"));
    expect(h.states.at(-1)?.status).toBe("saved");
  });

  it("Ctrl+S 强制落盘：取消防抖立即保存并返回成功", async () => {
    const h = createHarness();
    openSession(h);
    h.session.edit(doc("强制保存"));
    expect(h.timers.size).toBe(1);

    const result = await h.session.saveNow();

    expect(result).toBe(true);
    expect(h.saves).toHaveLength(1);
    expect(h.timers.size).toBe(0);
    expect(h.states.at(-1)?.status).toBe("saved");
    expect(h.states.at(-1)?.revision).toBe(4);
  });

  it("无改动时 saveNow 不调用保存接口", async () => {
    const h = createHarness();
    openSession(h);
    const result = await h.session.saveNow();
    expect(result).toBe(true);
    expect(h.saves).toHaveLength(0);
  });

  it("保存进行中编辑：首笔成功后按最新正文重新置脏并再次防抖", async () => {
    const h = createHarness();
    openSession(h);
    h.session.edit(doc("第一版"));
    const timerId = [...h.timers.keys()][0];
    h.fireTimer(timerId);
    // 保存已 in-flight：立即追加编辑
    h.session.edit(doc("第二版"));

    await h.flush();

    expect(h.saves).toHaveLength(1);
    expect(h.saves[0].body).toEqual(doc("第一版"));
    // 首个成功后重新调度了防抖
    expect(h.timers.size).toBe(1);
    expect(h.states.at(-1)?.status).toBe("dirty");

    await h.fireTimer([...h.timers.keys()][0]);
    expect(h.saves).toHaveLength(2);
    expect(h.saves[1].body).toEqual(doc("第二版"));
    expect(h.saves[1].baseRevision).toBe(4);
    expect(h.states.at(-1)?.status).toBe("saved");
  });

  it("成功保存后 revision 推进，下一次保存使用新 baseRevision", async () => {
    const h = createHarness();
    openSession(h, { revision: 10 });
    h.session.edit(doc("新内容"));
    await h.fireTimer([...h.timers.keys()][0]);

    expect(h.saves[0].baseRevision).toBe(10);
    expect(h.states.at(-1)?.revision).toBe(11);

    h.session.edit(doc("再改一次"));
    await h.fireTimer([...h.timers.keys()][0]);
    expect(h.saves[1].baseRevision).toBe(11);
  });

  it("revision-mismatch 进入 conflict，保留脏正文并暴露 currentRevision，不自动重试", async () => {
    const h = createHarness({
      save: async () => ({
        ok: false as const,
        error: { code: "revision-mismatch" as const, message: "正文已在别处更新", currentRevision: 42 }
      })
    });
    openSession(h);
    h.session.edit(doc("有冲突的正文"));
    await h.fireTimer([...h.timers.keys()][0]);

    expect(h.states.at(-1)?.status).toBe("conflict");
    expect(h.states.at(-1)?.dirty).toBe(true);
    expect(h.states.at(-1)?.currentRevision).toBe(42);
    expect(h.states.at(-1)?.error).toBeTruthy();
    expect(h.timers.size).toBe(0);
  });

  it("保存抛错进入 error，保留脏正文，saveNow 返回 false", async () => {
    const h = createHarness();
    openSession(h);
    h.session.edit(doc("会失败的正文"));
    h.failNextSave(new Error("磁盘写失败"));
    const result = await h.session.saveNow();

    expect(result).toBe(false);
    expect(h.states.at(-1)?.status).toBe("error");
    expect(h.states.at(-1)?.dirty).toBe(true);
    expect(h.states.at(-1)?.error).toContain("磁盘写失败");
  });

  it("dispose 取消挂起防抖且不再保存", async () => {
    const h = createHarness();
    openSession(h);
    h.session.edit(doc("未保存内容"));
    expect(h.timers.size).toBe(1);

    h.session.dispose();
    expect(h.timers.size).toBe(0);
    await h.flush();
    expect(h.saves).toHaveLength(0);

    const result = await h.session.saveNow();
    expect(result).toBe(false);
    h.session.edit(doc("dispose 后编辑"));
    await h.flush();
    expect(h.saves).toHaveLength(0);
  });

  it("正文与已保存内容等价时不调度保存，也不调用保存接口", async () => {
    const h = createHarness();
    const body = doc("原文");
    openSession(h, { body });
    h.session.edit(doc("原文"));
    expect(h.timers.size).toBe(0);
    await h.flush();
    expect(h.saves).toHaveLength(0);
    expect(h.states.at(-1)?.status).toBe("saved");
  });

  it("open 后状态为 saved 且不可变快照不被外部修改污染", async () => {
    const h = createHarness();
    openSession(h, { revision: 7, body: doc("快照") });
    const snapshot = h.session.getState();
    snapshot.dirty = true;
    expect(h.session.getState().dirty).toBe(false);
    expect(h.session.getState()).toEqual({
      status: "saved",
      sceneId: "scene-1",
      revision: 7,
      dirty: false,
      error: null,
      currentRevision: null,
      lastSavedAt: 1_000
    });
  });
});
