import type { InspirationStatus, InspirationType } from "@/types/inspiration";
import type { AIRunAction } from "@/types/ai";

export const TYPE_LABELS: Record<InspirationType, string> = {
  plot: "剧情点子",
  character: "人设",
  world: "世界观",
  scene: "桥段",
  line: "台词/金句",
  trope: "套路",
  conflict: "冲突点",
  note: "札记"
};

export const STATUS_LABELS: Record<InspirationStatus, string> = {
  inbox: "待处理",
  reviewing: "整理中",
  usable: "可用",
  polished: "已润色",
  used: "已转卡片",
  archived: "归档"
};

export const AI_LABELS: Record<Exclude<AIRunAction, "consistency">, string> = {
  polish: "润色",
  expand: "扩写",
  "platform-style": "平台风格化",
  conflict: "生成冲突点",
  humanize: "去 AI 味"
};

export function parseList(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function formatList(value: string[]): string {
  return value.join(", ");
}

export function newVariantId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return `variant-${crypto.randomUUID()}`;
  return `variant-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export interface InboxDraft {
  title: string;
  body: string;
  type: string;
  status: string;
  tags: string;
  platformTags: string;
}

export const EMPTY_DRAFT: InboxDraft = {
  title: "",
  body: "",
  type: "note",
  status: "inbox",
  tags: "",
  platformTags: ""
};

export type SaveStatus = "idle" | "unsaved" | "saving" | "saved";

export interface StatusFilterOption {
  value: InspirationStatus | "all";
  label: string;
}

export const STATUS_FILTER_OPTIONS: StatusFilterOption[] = [
  { value: "all", label: "全部" },
  { value: "inbox", label: "待处理" },
  { value: "reviewing", label: "整理中" },
  { value: "usable", label: "可用" },
  { value: "polished", label: "已润色" },
  { value: "used", label: "已转卡片" },
  { value: "archived", label: "归档" }
];
