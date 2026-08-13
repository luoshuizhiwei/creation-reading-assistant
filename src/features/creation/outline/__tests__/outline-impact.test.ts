import { describe, expect, it } from "vitest";
import {
  chapterDisplay,
  computeBatchStatusImpact,
  computeChapterMoveImpact,
  computeMergeImpact,
  computeNumberingImpact,
  computeSceneMoveImpact,
  computeSplitImpact,
  findChapterLocation,
  findSceneLocation
} from "../outline-impact";
import { makeOutline } from "./fixtures";

describe("outline-impact 影响预览模型", () => {
  it("拆分：首个场景作为拆分点无效（会产生空章）", () => {
    const o = makeOutline();
    const impact = computeSplitImpact(o, "c1", "s1", "新章");
    expect(impact?.valid).toBe(false);
    expect(impact?.invalidReason).toMatch(/第一个场景/);
  });

  it("拆分：非首场景产生正确的 moved / remaining 计数", () => {
    const o = makeOutline();
    const impact = computeSplitImpact(o, "c1", "s2", "新章");
    expect(impact?.valid).toBe(true);
    expect(impact?.movedScenes.map((s) => s.id)).toEqual(["s2"]);
    expect(impact?.remainingScenes.map((s) => s.id)).toEqual(["s1"]);
  });

  it("并入上一章显示源章 / 目标章 / 移动场景数", () => {
    const o = makeOutline();
    const impact = computeMergeImpact(o, "c2");
    expect(impact?.valid).toBe(true);
    expect(impact?.sourceChapter.id).toBe("c2");
    expect(impact?.targetChapter.id).toBe("c1");
    expect(impact?.movedScenes.length).toBe(1);
  });

  it("首章不能并入上一章", () => {
    const o = makeOutline();
    const impact = computeMergeImpact(o, "c1");
    expect(impact?.valid).toBe(false);
  });

  it("跨卷移动影响预览包含源章与目标卷", () => {
    const o = makeOutline();
    const impact = computeChapterMoveImpact(o, "c1", "v2");
    expect(impact?.sourceChapter.id).toBe("c1");
    expect(impact?.targetVolume.id).toBe("v2");
  });

  it("场景跨章移动影响预览包含源章与目标章", () => {
    const o = makeOutline();
    const impact = computeSceneMoveImpact(o, "s1", "c2");
    expect(impact?.sourceChapter.id).toBe("c1");
    expect(impact?.targetChapter.id).toBe("c2");
  });

  it("批量状态只统计传入章节", () => {
    const o = makeOutline();
    const impact = computeBatchStatusImpact(o, ["c1", "c3"], "已完成");
    expect(impact.count).toBe(2);
    expect(impact.chapters.map((c) => c.id)).toEqual(["c1", "c3"]);
  });

  it("编号模式影响预览", () => {
    const o = makeOutline();
    const impact = computeNumberingImpact(o, "c1", "custom", "外传一");
    expect(impact?.chapter.id).toBe("c1");
    expect(impact?.numbering).toBe("custom");
  });

  it("chapterDisplay 合并编号与标题", () => {
    const o = makeOutline();
    const loc = findChapterLocation(o, "c1");
    expect(chapterDisplay(loc!.chapter)).toContain("第一章");
    const scene = findSceneLocation(o, "s1");
    expect(scene?.scene.id).toBe("s1");
  });
});
