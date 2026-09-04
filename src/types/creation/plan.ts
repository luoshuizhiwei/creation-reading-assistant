import type { StructureCommand } from "./command";

/** 受保护重组覆盖的结构命令。 */
export type ProtectedStructureCommand = Extract<
  StructureCommand,
  | { type: "chapter.split" }
  | { type: "chapter.merge" }
  | { type: "chapter.move" }
  | { type: "scene.move" }
  | { type: "chapters.setStatus" }
  | { type: "chapter.setNumbering" }
>;

export type StructurePreviewCommand = {
  type: "structure.preview";
  projectId: string;
  command: ProtectedStructureCommand;
};

export type StructureApplyWithProtectionCommand = {
  type: "structure.applyWithProtection";
  projectId: string;
  /** 一次性权威预览计划；主进程只执行该计划中封存的命令。 */
  planId: string;
  protectionReason: string;
};

export type StructureRevertCommand = {
  type: "structure.revert";
  projectId: string;
  protectionSnapshotId: string;
  /** 应用完成时返回的精确版本集合；任一对象后来被改动即拒绝撤回。 */
  expectedAppliedRevisions: StructureAffectedObject[];
};

export type StructurePlanCommand = StructurePreviewCommand | StructureApplyWithProtectionCommand | StructureRevertCommand;

export type StructurePlanRow = { label: string; value: string };

export type StructurePreviewView = {
  ok: true;
  planId: string;
  command: ProtectedStructureCommand;
  rows: StructurePlanRow[];
  stale: boolean;
  affectedSceneCount: number;
  numberingChange?: string;
  softDeletedChapter?: string;
};

export type StructureAffectedObject = {
  type: "volume" | "chapter" | "scene";
  id: string;
  /** 应用后的精确对象版本；用于撤回前冲突检测，不是项目最大版本。 */
  revision: number;
};

export type StructureApplyResult = {
  ok: true;
  protectionSnapshotId: string;
  affected: StructureAffectedObject[];
  newRevision: number;
};

export type StructureRevertResult = {
  ok: true;
  restoredRevision: number;
};

export type StructurePlanResult = StructurePreviewView | StructureApplyResult | StructureRevertResult;