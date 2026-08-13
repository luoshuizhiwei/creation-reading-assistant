import type {
  CardCreateCommand,
  CreationRunCommand,
  CreationRunResultOf,
  CreationStructureResult,
  InboxUpdateCommand,
  SceneCreateCommand
} from "./creation";

function acceptRunCommand<Command extends CreationRunCommand>(command: Command): CreationRunResultOf<Command> {
  throw new Error(`type-check only: ${command.type}`);
}

/** 编译期契约：正例不用断言逃逸，反例必须被 TypeScript 拒绝。 */
function _typeAssertions(): void {
  type PlanCmd = Extract<CreationRunCommand, { type: "scene.updatePlanning" }>;
  const _plan: CreationRunResultOf<PlanCmd> = {
    commandType: "scene.updatePlanning",
    sequence: 1,
    projectId: "p",
    sceneId: "s",
    updatedAt: ""
  };

  type ImportCmd = Extract<CreationRunCommand, { type: "project.importDraft" }>;
  const _import: CreationRunResultOf<ImportCmd> = {
    commandType: "project.importDraft",
    sequence: 1,
    projectId: "p",
    volumeCount: 1,
    chapterCount: 1,
    sceneCount: 1
  };

  type ConvertCmd = Extract<CreationRunCommand, { type: "inbox.convertToCard" }>;
  const _convert: CreationRunResultOf<ConvertCmd> = {
    commandType: "inbox.convertToCard",
    sequence: 1,
    itemId: "i",
    cardId: "c",
    revision: 2,
    updatedAt: ""
  };

  type CardCmd = Extract<CreationRunCommand, { type: "card.create" }>;
  const _card: CreationRunResultOf<CardCmd> = {
    commandType: "card.create",
    sequence: 1,
    projectId: "p",
    entityId: "e",
    revision: 1,
    updatedAt: ""
  };

  type SceneCmd = Extract<CreationRunCommand, { type: "scene.create" }>;
  const _scene: CreationRunResultOf<SceneCmd> = {
    commandType: "scene.create",
    sequence: 1,
    projectId: "p",
    entityId: "e",
    revision: 1,
    updatedAt: ""
  };

  const _cardCreate: CardCreateCommand = {
    type: "card.create",
    projectId: "p",
    kind: "character",
    title: "test"
  };
  const _cardResult: CreationStructureResult = acceptRunCommand(_cardCreate);

  const _sceneCreate: SceneCreateCommand = {
    type: "scene.create",
    chapterId: "c",
    title: "test"
  };
  const _sceneResult: CreationStructureResult = acceptRunCommand(_sceneCreate);

  const _ordinaryInboxUpdate: InboxUpdateCommand = {
    type: "inbox.update",
    itemId: "i",
    baseRevision: 1,
    title: "test"
  };
  // @ts-expect-error inbox.update 不属于 runStructure 命令联合。
  acceptRunCommand(_ordinaryInboxUpdate);

  // @ts-expect-error scene.create 结果不能伪装成卡片结果字段。
  const _wrongSceneResult: CreationRunResultOf<SceneCmd> = { ..._scene, cardId: "c" };

  void [_plan, _import, _convert, _card, _scene, _cardResult, _sceneResult, _wrongSceneResult];
}

export { _typeAssertions };
