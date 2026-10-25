// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { SceneRadar } from "@/features/creation/editor/SceneRadar";
import type { SceneRadarData } from "@/features/creation/editor/scene-radar";

// BD-167：出场角色、字数、批注三组 dt/dd 曾挂在普通 <div> 上，脱离 <dl> 后
// 术语/定义语义整个失效（axe 的 definition-list 也会红）。
function makeRadar(overrides: Partial<SceneRadarData> = {}): SceneRadarData {
  return {
    sceneTitle: "雨夜来客",
    planning: { perspectiveCardId: null },
    hasPlanning: true,
    perspective: "林晚",
    location: "旧码头",
    cast: ["林晚", "顾淮"],
    unresolvedAnnotations: 2,
    resolvedAnnotations: 1,
    invalidAnchors: 0,
    referencedCards: [],
    foreshadowOpenInScene: 0,
    foreshadowOpenInProject: 0,
    targetWords: 2000,
    characterCount: 1200,
    estimatedMinutes: 3,
    revision: 7,
    ...overrides
  };
}

afterEach(() => cleanup());

describe("SceneRadar 定义列表结构", () => {
  it("每个 dt/dd 都在 dl 里，术语与定义成对可读", () => {
    const { container } = render(<SceneRadar radar={makeRadar()} onOpenOutline={vi.fn()} />);

    const terms = Array.from(container.querySelectorAll("dt"));
    expect(terms.length).toBeGreaterThan(0);
    for (const term of terms) expect(term.closest("dl")).not.toBeNull();
    for (const def of Array.from(container.querySelectorAll("dd"))) expect(def.closest("dl")).not.toBeNull();

    // 字数那一行、出场角色与批注概览各自成一册，而不是散在 div 里
    expect(container.querySelectorAll("dl").length).toBeGreaterThanOrEqual(4);
    expect(container.querySelector("dl.scene-radar-words-row > dt")).not.toBeNull();
  });
});
