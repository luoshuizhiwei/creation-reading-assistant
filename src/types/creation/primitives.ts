export type CreationProjectTemplate = "blank" | "long-form" | "serial";

export interface CreationProjectSetup {
  template: CreationProjectTemplate;
  description?: string;
  genre?: string;
  totalWordGoal?: number;
  dailyWordGoal?: number;
  weeklyWordGoal?: number;
  targetDate?: string;
  weeklyUpdateDays: number[];
  chapterWorkflow: string[];
}

export type CreateProjectInput = { title: string } & CreationProjectSetup;

export interface CreationDocument {
  type: "doc";
  content: unknown[];
}

export type CreationWorkspaceErrorCode =
  | "closed"
  | "invalid-input"
  | "not-found"
  | "conflict"
  | "revision-mismatch"
  | "integrity";

export type ChapterNumberingKind = "auto" | "prologue" | "extra" | "custom";

export type CardFieldKind =
  | "text"
  | "multiline"
  | "number"
  | "date"
  | "select"
  | "multiSelect"
  | "boolean"
  | "cardRef"
  | "url"
  | "attachment";

export interface CardFieldSchema {
  key: string;
  label: string;
  kind: CardFieldKind;
  required?: boolean;
  options?: string[];
  defaultValue?: unknown;
}

export type TrashEntityKind = "volume" | "chapter" | "scene" | "card";

export type SnapshotSubjectType = "scene" | "card" | "chapter" | "volume";

export type DraftImportFormat = "txt" | "markdown" | "docx";

export type DraftExportPreset = "platform-plain" | "standard-review" | "outline-markdown";

/** 场景自己的创作进度；与项目自定义的章节工作流状态严格分离。 */
export type SceneStatus = "planned" | "drafting" | "revising" | "done";

export type CreationSearchScope = "scene" | "card" | "chapter" | "project";

export type ReplaceScope = "project" | "volume" | "chapter" | "scene";

export type ReplacePlanScope = "all" | "chapter" | "scene";
export type ReplacePlanMode = "plain" | "regex";

export type ProofRule =
  | "repeatedChar"
  | "unbalancedPunctuation"
  | "abnormalSpacing"
  | "longParagraph"
  | "bannedWord"
  | "mixedPunctuation"
  | "crutchWord"
  | "paragraphStartRepeat"
  /** 别名一致性：同一张卡片在全书被多种称呼指代，少数派称呼所在位置逐个提示。 */
  | "aliasInconsistency"
  /** 疑似错拼：与项目词表（卡片主名/别名）仅差一个字的词，按位置提示。 */
  | "suspectedTypo";

/**
 * 场景任务卡字段（蓝图 §5.3）。
 * 语义：字段未提供（undefined）= 保留旧值；字段为 null = 明确清空。
 * JSON/IPC 会丢失 undefined，因此显式清空一律使用 null。
 */
export interface ScenePlanning {
  /** 视角角色卡片 ID。 */
  perspectiveCardId?: string | null;
  /** 时间或相对时间描述。 */
  time?: string | null;
  /** 地点卡片 ID。 */
  locationCardId?: string | null;
  /** 出场卡片 ID 列表（清空用 [] 或 null）。 */
  castCardIds?: string[] | null;
  goal?: string | null;
  conflict?: string | null;
  outcome?: string | null;
  emotion?: string | null;
  /** 目标字数（非空白字符）。 */
  targetWords?: number | null;
}
