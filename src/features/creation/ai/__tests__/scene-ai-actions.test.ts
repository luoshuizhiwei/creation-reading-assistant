import { describe, expect, it } from "vitest";
import {
  SCENE_AI_ACTION_LABELS,
  SCENE_AI_ACTION_ORDER,
  appendTextToSceneBody,
  mergeSceneBody,
  sceneAiActionLabel,
  sceneAiAdoptMode,
  sceneAiOutputKind
} from "@/features/creation/ai/scene-ai-actions";

describe("场景 AI 动作语义（Stage 4-D）", () => {
  it("六个动作都有中文标签且按钮顺序覆盖全部动作", () => {
    expect(SCENE_AI_ACTION_ORDER).toHaveLength(6);
    for (const action of SCENE_AI_ACTION_ORDER) {
      expect(SCENE_AI_ACTION_LABELS[action]).toBeTruthy();
      expect(sceneAiActionLabel(action)).toBe(SCENE_AI_ACTION_LABELS[action]);
    }
    expect(SCENE_AI_ACTION_ORDER).toContain("continuation");
    expect(SCENE_AI_ACTION_ORDER).toContain("condensing");
    expect(SCENE_AI_ACTION_ORDER).toContain("character-consistency");
  });

  it("续写 / 精简 / 润色 / 扩写 输出候选；一致性 / 角色一致性 输出只读报告", () => {
    expect(sceneAiOutputKind("polish")).toBe("candidate");
    expect(sceneAiOutputKind("expand")).toBe("candidate");
    expect(sceneAiOutputKind("continuation")).toBe("candidate");
    expect(sceneAiOutputKind("condensing")).toBe("candidate");
    expect(sceneAiOutputKind("consistency")).toBe("report");
    expect(sceneAiOutputKind("character-consistency")).toBe("report");
  });

  it("只有续写是追加语义，其余候选一律替换整篇", () => {
    expect(sceneAiAdoptMode("continuation")).toBe("append");
    expect(sceneAiAdoptMode("condensing")).toBe("replace");
    expect(sceneAiAdoptMode("polish")).toBe("replace");
    expect(sceneAiAdoptMode("expand")).toBe("replace");
  });

  it("未知动作回退为原样标签 + 候选替换，不让 UI 因缺标签而崩", () => {
    expect(sceneAiActionLabel("whatever")).toBe("whatever");
    expect(sceneAiOutputKind("whatever")).toBe("candidate");
    expect(sceneAiAdoptMode("whatever")).toBe("replace");
  });

  it("续写追加预览：空正文不加空行，非空正文用一个空行分隔", () => {
    expect(mergeSceneBody("", "新段落。")).toBe("新段落。");
    expect(mergeSceneBody("原正文。", "")).toBe("原正文。");
    expect(mergeSceneBody("原正文。", "新段落。")).toBe("原正文。\n\n新段落。");
    expect(mergeSceneBody("  ", "新段落。  ")).toBe("新段落。");
  });
});

describe("appendTextToSceneBody（文档层追加，避免正文往返丢段落）", () => {
  const doc = (...texts: string[]) => ({
    type: "doc",
    content: texts.map((text) => ({ type: "paragraph", content: [{ type: "text", text }] }))
  });

  it("在既有段落之后追加，既有段落原样保留（不会被并成一段）", () => {
    const result = appendTextToSceneBody(doc("第一段。", "第二段。"), "续写段落。");
    expect(result).toEqual(doc("第一段。", "第二段。", "续写段落。"));
  });

  it("候选含多段时全部追加", () => {
    const result = appendTextToSceneBody(doc("第一段。"), "新一。\n\n新二。");
    expect(result).toEqual(doc("第一段。", "新一。", "新二。"));
  });

  it("候选为空时正文不变", () => {
    const original = doc("第一段。");
    expect(appendTextToSceneBody(original, "   ")).toEqual(original);
  });

  it("正文为空（无 content）时退化为候选独立成段", () => {
    const result = appendTextToSceneBody(undefined, "新段落。");
    expect(result).toEqual(doc("新段落。"));
  });
});
