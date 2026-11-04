// @vitest-environment jsdom
/**
 * BD-245（WCAG 2.1.1 / 2.4.3）· 键盘选中搜索结果跳屏后，焦点必须有落点
 *
 * 普查实测（改前）：从搜索层用 ↓ + 回车选中一条结果，跳到工作台某视图后
 * 「焦点=·body·」，紧接着按 Tab 落的是「最小化窗口」——每次选结果都要从页首重走一整屏。
 * 根因：层一关，刚被聚焦的那条结果按钮整棵卸载；而屏内的「项目首页 → 某视图」这类跳转
 * 不换 PageTransition 的 screenKey，那条 2.4.3 着陆网（interaction.tsx:271-278）接不到。
 * 修法就是把落点交给同一个 .motion-page[tabindex="-1"]（useSearchActions.ts）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";

vi.mock("@/services/creation-service", () => ({
  search: vi.fn(),
  inboxList: vi.fn(),
  cardsList: vi.fn(),
  readSceneBody: vi.fn(),
  readProjectOutline: vi.fn(),
  listProjects: vi.fn(),
  readProjectNavigation: vi.fn(),
  runStructure: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  cardRead: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  trashList: vi.fn(),
  snapshotList: vi.fn(),
  exportDraft: vi.fn(),
  replacePreview: vi.fn(),
  replaceApply: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  sessionReport: vi.fn(),
  sessionDelete: vi.fn(),
  proofQuery: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  importDraftPreview: vi.fn(),
  exportProjectBundle: vi.fn(),
  importProjectBundle: vi.fn(),
  annotationList: vi.fn(),
  annotationCreate: vi.fn(),
  annotationUpdate: vi.fn(),
  annotationDelete: vi.fn(),
  resourceList: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  projectExport: vi.fn(),
  migrationStatus: vi.fn(),
  migrationRun: vi.fn(),
  createProject: vi.fn(),
  readProjectHome: vi.fn()
}));
vi.mock("@/services/search-service", () => ({ globalSearch: vi.fn() }));
vi.mock("@/services/inspiration-service", () => ({ listInspirations: vi.fn() }));
vi.mock("@/services/library-service", () => ({ listBooks: vi.fn() }));
vi.mock("@/services/reader-service", () => ({ openBook: vi.fn(), openEpub: vi.fn() }));

import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";
import { useAppStore } from "@/stores/app-store";

const nextFrame = () => new Promise((r) => requestAnimationFrame(() => r(null)));

beforeEach(() => {
  useSearchStore.setState({
    open: true,
    keyword: "词",
    filter: "all",
    projectContextId: undefined,
    allProjects: false,
    results: [],
    loading: false,
    error: null
  } as never);
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("BD-245 键盘选中结果跳屏后的焦点落点（2.1.1／2.4.3）", () => {
  it("navigated：关层后焦点交给 .motion-page 着陆容器，不是 body", async () => {
    // 屏内跳转的样子：PageTransition 的容器在场，但它不会因这次跳转换 key
    const landing = document.createElement("div");
    landing.className = "motion-page h-full";
    landing.tabIndex = -1;
    document.body.append(landing);

    const { result } = renderHook(() => useSearchActions());
    useSearchStore.setState({
      results: [
        {
          id: "screen:cards",
          source: "screen",
          title: "卡片库",
          snippet: "",
          originLabel: "屏幕",
          intent: { kind: "screen", screen: "cards" }
        }
      ]
    } as never);

    let outcome: string | undefined;
    await act(async () => {
      outcome = await result.current.openResult(useSearchStore.getState().results[0]!);
    });
    expect(outcome).toBe("navigated");
    expect(useSearchStore.getState().open).toBe(false);
    expect(useAppStore.getState().screen).toBe("cards");

    await act(async () => {
      await nextFrame();
    });
    expect(document.activeElement).not.toBe(document.body);
    expect(document.activeElement).toBe(landing);
  });

  it("没有着陆容器时也不许抛错（守卫要保守，不能把跳转本身带崩）", async () => {
    const { result } = renderHook(() => useSearchActions());
    useSearchStore.setState({
      open: true,
      results: [
        {
          id: "screen:settings",
          source: "screen",
          title: "设置",
          snippet: "",
          originLabel: "屏幕",
          intent: { kind: "screen", screen: "settings" }
        }
      ]
    } as never);
    let outcome: string | undefined;
    await act(async () => {
      outcome = await result.current.openResult(useSearchStore.getState().results[0]!);
    });
    await act(async () => {
      await nextFrame();
    });
    expect(outcome).toBe("navigated");
    expect(useAppStore.getState().screen).toBe("settings");
  });
});
