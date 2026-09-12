import { describe, expect, it } from "vitest";
import { buildAIPrompt, buildInspirationPrompt, buildScenePrompt, sceneInstruction } from "../ai-prompt";
import type { AIRunInput } from "../../../src/types/ai";

function sceneInput(action: AIRunInput["action"], overrides: Partial<AIRunInput> = {}): AIRunInput {
  return {
    action,
    title: "雨夜到访",
    content: "他推门进来，斗篷还在滴水。",
    sceneContext: {
      sceneTitle: "雨夜到访",
      planningText: "视角：林湛\n地点：旧书店",
      cardsText: "【角色】林湛（阿湛）\n性格: 克制",
      annotationsText: "待处理 · 关联「林湛」：这里的动机要更隐晦"
    },
    ...overrides
  };
}

describe("buildAIPrompt 分派（Stage 4-D）", () => {
  it("polish 不带 sceneContext 时仍走灵感提示词，不破坏收件箱既有行为", () => {
    const prompt = buildAIPrompt({ action: "polish", title: "灵感标题", content: "一句话灵感。" });
    expect(prompt).toContain("原始灵感：");
    expect(prompt).not.toContain("【场景正文】");
  });

  it("expand 不带 sceneContext 时走灵感提示词", () => {
    const prompt = buildAIPrompt({ action: "expand", title: "t", content: "c" });
    expect(prompt).toContain("原始灵感：");
    expect(prompt).not.toContain("【场景标题】");
  });

  it("场景专属动作带 sceneContext 时走场景提示词", () => {
    for (const action of ["consistency", "continuation", "condensing", "character-consistency"] as const) {
      const prompt = buildAIPrompt(sceneInput(action));
      expect(prompt).toContain("【场景标题】雨夜到访");
      expect(prompt).toContain("【场景正文】");
    }
  });

  it("场景专属动作缺 sceneContext 时抛错，避免提示词退化成灵感改写", () => {
    for (const action of ["consistency", "continuation", "condensing", "character-consistency"] as const) {
      expect(() => buildAIPrompt({ action, content: "正文" })).toThrow(/sceneContext/);
    }
  });

  it("空正文抛错，避免把空白请求发给 AI 服务", () => {
    expect(() => buildAIPrompt(sceneInput("polish", { content: "   " }))).toThrow(/场景正文/);
  });

  it("未知动作抛错", () => {
    expect(() => buildAIPrompt({ action: "not-a-real-action" as never, content: "x" })).toThrow(/不支持的 AI 动作/);
  });
});

describe("buildScenePrompt 上下文拼装", () => {
  it("上下文各组按 任务卡 / 关联卡片 / 批注 顺序出现且不混入正文", () => {
    const prompt = buildScenePrompt(sceneInput("continuation"));
    const ctxIndex = prompt.indexOf("【任务卡】");
    const cardsIndex = prompt.indexOf("【关联卡片】");
    const annoIndex = prompt.indexOf("【批注】");
    const bodyIndex = prompt.indexOf("【场景正文】");
    expect(ctxIndex).toBeGreaterThan(-1);
    expect(cardsIndex).toBeGreaterThan(ctxIndex);
    expect(annoIndex).toBeGreaterThan(cardsIndex);
    expect(bodyIndex).toBeGreaterThan(annoIndex);
  });

  it("上下文为空时不产生空的【上下文】块", () => {
    const prompt = buildScenePrompt({
      action: "condensing",
      title: "t",
      content: "正文。",
      sceneContext: { sceneTitle: "仅标题" }
    });
    expect(prompt).not.toContain("【上下文】");
    expect(prompt).toContain("【场景标题】仅标题");
  });

  it("续写只要求新增内容、不重复原文", () => {
    expect(sceneInstruction("continuation")).toContain("续写");
    expect(sceneInstruction("continuation")).toContain("不要重复原文");
  });

  it("精简要求保留关键情节并限制篇幅", () => {
    expect(sceneInstruction("condensing")).toContain("精简");
    expect(sceneInstruction("condensing")).toContain("70%");
  });

  it("角色一致性只输出报告、不改正文", () => {
    const instruction = sceneInstruction("character-consistency");
    expect(instruction).toContain("角色人格/动机一致性检查");
    expect(instruction).toContain("只输出报告，不改正文");
  });

  it("一致性检查限定问题类型与总体结论", () => {
    const instruction = sceneInstruction("consistency");
    expect(instruction).toContain("事实矛盾");
    expect(instruction).toContain("总体结论");
  });
});

describe("buildInspirationPrompt 灵感分支", () => {
  it("platform-style 带目标平台，humanize 与 conflict 各有指令", () => {
    expect(buildInspirationPrompt({ action: "platform-style", content: "c", platform: "起点" })).toContain("目标平台/风格：起点");
    expect(buildInspirationPrompt({ action: "humanize", content: "c" })).toContain("AI 腔");
    expect(buildInspirationPrompt({ action: "conflict", content: "c" })).toContain("冲突点");
  });

  it("灵感内容为空抛错", () => {
    expect(() => buildInspirationPrompt({ action: "polish", content: "" })).toThrow(/灵感内容/);
  });
});
