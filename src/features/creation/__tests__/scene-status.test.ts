import { describe, expect, it } from "vitest";
import {
  isSceneStatus,
  SCENE_STATUS_LABELS,
  SCENE_STATUS_OPTIONS,
  SCENE_STATUS_ORDER,
  sceneStatusLabel
} from "@/features/creation/scene-status";

describe("scene-status 场景状态展示映射", () => {
  it("选项顺序与标签覆盖全部四种场景状态", () => {
    expect(SCENE_STATUS_OPTIONS.map((item) => item.value)).toEqual(["planned", "drafting", "revising", "done"]);
    expect(SCENE_STATUS_OPTIONS.map((item) => item.label)).toEqual(["待规划", "起草中", "修订中", "已完成"]);
    expect(SCENE_STATUS_ORDER.map((value) => SCENE_STATUS_LABELS[value])).toEqual([
      "待规划",
      "起草中",
      "修订中",
      "已完成"
    ]);
  });

  it("已知状态映射为中文标签", () => {
    expect(sceneStatusLabel("planned")).toBe("待规划");
    expect(sceneStatusLabel("drafting")).toBe("起草中");
    expect(sceneStatusLabel("revising")).toBe("修订中");
    expect(sceneStatusLabel("done")).toBe("已完成");
  });

  it("空值显示为未设置，未知值原样返回以便暴露数据异常", () => {
    expect(sceneStatusLabel(null)).toBe("未设置");
    expect(sceneStatusLabel(undefined)).toBe("未设置");
    expect(sceneStatusLabel("")).toBe("未设置");
    // 未知值不被兜底成「待规划」，否则数据问题会被静默掩盖。
    expect(sceneStatusLabel("archived")).toBe("archived");
    expect(sceneStatusLabel("写作中")).toBe("写作中");
  });

  it("isSceneStatus 只接受四种受支持取值", () => {
    expect(isSceneStatus("planned")).toBe(true);
    expect(isSceneStatus("done")).toBe(true);
    expect(isSceneStatus("写作中")).toBe(false);
    expect(isSceneStatus(null)).toBe(false);
    expect(isSceneStatus(3)).toBe(false);
  });
});
