import type { AIRunAction } from "../../../../src/types/ai";
import type { InspirationStatus, InspirationType } from "../../../../src/types/inspiration";

export const inspirationStatusOptions: Array<{ value: InspirationStatus; label: string; hint: string }> = [
  { value: "inbox", label: "收集箱", hint: "刚记下，之后再整理" },
  { value: "reviewing", label: "待整理", hint: "标记需要重点整理" },
  { value: "usable", label: "可使用", hint: "已经能转成素材" },
  { value: "polished", label: "已打磨", hint: "经过润色或扩写" },
  { value: "used", label: "已采用", hint: "已写入正文或方案" },
  { value: "archived", label: "归档", hint: "暂时不再处理" }
];

export function getInspirationStatusLabel(status: string): string {
  if (status === "draft") return "可使用";
  return inspirationStatusOptions.find((item) => item.value === status)?.label ?? "收集箱";
}

export function getInspirationTypeLabel(type: InspirationType | undefined): string {
  const labels: Record<InspirationType, string> = {
    note: "灵感",
    plot: "剧情",
    character: "人物",
    world: "世界观",
    scene: "场景",
    line: "对话",
    trope: "设定",
    conflict: "冲突"
  };
  return type ? labels[type] ?? "灵感" : "灵感";
}

export function parseTagInput(value: string): string[] {
  return value
    .split(/[，,\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export const aiActions: Array<[AIRunAction, string]> = [
  ["polish", "润色"],
  ["expand", "扩写"],
  ["platform-style", "平台风格化"],
  ["conflict", "生成冲突"],
  ["humanize", "去 AI 味"]
];

/** 灵感模板：快速按创作素材类型创建结构化灵感 */
export interface InspirationTemplate {
  type: InspirationType;
  label: string;
  hint: string;
  titlePrefix: string;
  bodyTemplate: string;
  defaultTags: string[];
}

export const inspirationTemplates: InspirationTemplate[] = [
  {
    type: "character",
    label: "人物",
    hint: "角色设定、性格、动机",
    titlePrefix: "人物",
    bodyTemplate: "【姓名】\n【身份】\n【性格特征】\n【核心动机】\n【关键背景】",
    defaultTags: ["人物", "设定"]
  },
  {
    type: "world",
    label: "世界观",
    hint: "世界设定、规则、历史",
    titlePrefix: "世界观",
    bodyTemplate: "【设定名称】\n【核心规则】\n【地理/势力】\n【历史背景】\n【与现实差异】",
    defaultTags: ["世界观", "设定"]
  },
  {
    type: "conflict",
    label: "冲突",
    hint: "矛盾点、戏剧冲突",
    titlePrefix: "冲突",
    bodyTemplate: "【冲突双方】\n【冲突起因】\n【激化点】\n【可能结局】\n【可用场景】",
    defaultTags: ["冲突", "剧情"]
  },
  {
    type: "line",
    label: "金句",
    hint: "可复用的台词、句子",
    titlePrefix: "金句",
    bodyTemplate: "【原文】\n【出处/场景】\n【适用情境】\n【改写方向】",
    defaultTags: ["金句", "台词"]
  },
  {
    type: "plot",
    label: "剧情点",
    hint: "剧情节点、转折",
    titlePrefix: "剧情点",
    bodyTemplate: "【剧情位置】\n【触发事件】\n【关键转折】\n【影响人物】\n【后续走向】",
    defaultTags: ["剧情", "转折"]
  },
  {
    type: "scene",
    label: "场景",
    hint: "可用场景描写",
    titlePrefix: "场景",
    bodyTemplate: "【场景名称】\n【时间地点】\n【氛围基调】\n【关键元素】\n【可用片段】",
    defaultTags: ["场景", "描写"]
  }
];

/** 从阅读摘录快速生成素材卡：返回标题、正文、默认标签 */
export function buildMaterialCardFromExcerpt(excerpt: string, bookTitle?: string): {
  title: string;
  body: string;
  tags: string[];
} {
  const trimmedExcerpt = excerpt.trim().slice(0, 200);
  const title = bookTitle ? `${bookTitle}摘录素材` : "阅读摘录素材";
  const body = `【摘录原文】\n${trimmedExcerpt}\n\n【可提炼方向】\n- \n【适用场景】\n- \n【备注】\n`;
  const tags = ["摘录", "素材卡"];
  if (bookTitle) tags.push(bookTitle);
  return { title, body, tags };
}
