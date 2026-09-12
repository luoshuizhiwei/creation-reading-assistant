import type {
  StructureCommand,
  CardCommand,
  HistoryCommand,
  ProjectImportDraftCommand,
  SceneUpdatePlanningCommand,
  SceneUpdateMetaCommand,
  InboxConvertToCardCommand,
  SceneUpdatePlanningResult,
  SceneUpdateMetaResult,
  ProjectImportDraftResult,
  InboxConvertToCardResult,
  CardLinkResult,
  CreationStructureResult
} from "./command";

/**
 * runStructure 通道接受的命令联合。结果类型按命令 type 推导：
 * scene.updatePlanning → SceneUpdatePlanningResult；
 * project.importDraft → ProjectImportDraftResult；
 * 其余结构/卡片/回收站命令 → CreationStructureResult。
 */
export type CreationRunCommand =
  | StructureCommand
  | CardCommand
  | HistoryCommand
  | ProjectImportDraftCommand
  | SceneUpdatePlanningCommand
  | SceneUpdateMetaCommand
  | InboxConvertToCardCommand;

/** runStructure 的唯一运行时命令目录；Record 保证新增联合成员时必须同步白名单。 */
export const CREATION_RUN_COMMAND_TYPES: Readonly<Record<CreationRunCommand["type"], true>> = {
  "volume.create": true,
  "volume.rename": true,
  "volume.reorder": true,
  "volume.delete": true,
  "chapter.create": true,
  "chapter.rename": true,
  "chapter.reorder": true,
  "chapter.move": true,
  "chapter.delete": true,
  "chapter.setStatus": true,
  "chapter.setNumbering": true,
  "chapter.split": true,
  "chapter.merge": true,
  "chapters.setStatus": true,
  "scene.create": true,
  "scene.rename": true,
  "scene.reorder": true,
  "scene.move": true,
  "scene.delete": true,
  "cardType.create": true,
  "cardType.update": true,
  "cardType.delete": true,
  "relationType.create": true,
  "relationType.update": true,
  "relationType.delete": true,
  "card.create": true,
  "card.update": true,
  "card.delete": true,
  "card.link": true,
  "card.unlink": true,
  "cardRelation.create": true,
  "cardRelation.delete": true,
  "trash.restore": true,
  "trash.purge": true,
  "snapshot.create": true,
  "project.importDraft": true,
  "scene.updatePlanning": true,
  "scene.updateMeta": true,
  "inbox.convertToCard": true
};

export function isCreationRunCommandType(value: unknown): value is CreationRunCommand["type"] {
  return typeof value === "string" && Object.prototype.hasOwnProperty.call(CREATION_RUN_COMMAND_TYPES, value);
}

type _RunStructureCommandTypes = StructureCommand["type"] | CardCommand["type"] | HistoryCommand["type"];

export type CreationRunResultOf<Command extends CreationRunCommand> =
  Command extends { type: "scene.updatePlanning" } ? SceneUpdatePlanningResult :
  Command extends { type: "scene.updateMeta" } ? SceneUpdateMetaResult :
  Command extends { type: "project.importDraft" } ? ProjectImportDraftResult :
  Command extends { type: "inbox.convertToCard" } ? InboxConvertToCardResult :
  Command extends { type: "card.link" | "card.unlink" } ? CardLinkResult :
  Command extends { type: _RunStructureCommandTypes } ? CreationStructureResult :
  never;

export type CreationRunResult = CreationRunResultOf<CreationRunCommand>;
