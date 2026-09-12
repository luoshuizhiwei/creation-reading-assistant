/**
 * v9→v10「全局卡片库」迁移基线夹具（仅测试使用，不进入生产构建路径）。
 *
 * 目的：在 v10 落地前，用**当前公开的 workspace 命令/API** 构造一份可落盘、可重开、
 * 可校验的 v9 数据基线，锁定「迁移前」期望，供 v9→v10 迁移契约复用
 * （见 docs/plans/2026-09-10-desktop-global-card-library-requirements.md 第 101 / 128 行）。
 *
 * 硬边界：
 * - 只记录并断言「当前 v9 行为」；不创建 v10 的 global_cards / project_card_links，
 *   不修改 SCHEMA_VERSION（重开后必须仍为 9）。
 * - 不写 SQLite：DB 变更只经由 transact / read / check；附件真实字节只写入
 *   `<workspaceDirectory>/resources/<projectId>/` 下。
 * - 不替产品决定删除策略、共享附件所有权或项目包冲突合并——那些是 v10 的待决 seam。
 *
 * 覆盖的 v9 实体：项目 ×2、卷/章/场景、项目私有卡片、项目私有自定义卡片类型、
 * 别名 / 标签 / 字段值 / 修订、卡片关系（正向 + 反向）、场景任务卡的卡片引用、
 * 批注对卡片的引用、附件（A/B 各自元数据；传 workspaceDirectory 时含确定性真实字节）。
 */

import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { lstat, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import {
  type CreationDocument,
  type CreationStructureResult,
  type CreationWorkspace,
  type ResourceResult
} from "./index";

/** 基线期望常量：契约与夹具共享，避免两处各写一份期望值导致漂移。 */
export const V9_BASELINE = {
  projectATitle: "基线·跨项目迁移甲",
  projectBTitle: "基线·跨项目迁移乙",
  cardTypeAName: "技能",
  cardTypeBName: "地图",
  cardASkillTitle: "影袭",
  cardASkillAliases: ["潜行一击", "暗影斩"],
  cardASkillTags: ["战斗"],
  cardASkillDesc: "高速突袭",
  cardASkillRank: "A",
  cardASkillMana: 30,
  /** 创建后再 update 一次（改标签），把 revision 从 1 抬到 2，锁定修订语义。 */
  cardASkillRevision: 2,
  cardASkillTagsAfterUpdate: ["战斗", "刺杀"],
  cardACharacterTitle: "林晚",
  cardALocationTitle: "旧城",
  cardBCharacterTitle: "顾淮",
  cardBLocationTitle: "北关",
  relationNote: "第一章",
  annotationANote: "此处引用了自定义卡片",
  annotationBNote: "乙项目独立批注",
  /** 场景 A 锚定文本「旧城」：第 0 段、段内偏移 4、长度 2。 */
  sceneAAnchoredText: "旧城",
  /** 场景 B 锚定文本「北关」：第 0 段、段内偏移 4、长度 2。 */
  sceneBAnchoredText: "北关",
  resourceOriginalName: "基线参考.pdf",
  resourceFileSuffix: "baseline-ref.pdf",
  resourceBOriginalName: "基线参考-乙.pdf",
  resourceBFileSuffix: "baseline-ref-b.pdf",
  planningTime: "入夜后",
  planningGoal: "取回借书卡",
  planningConflict: "油灯将熄",
  planningTargetWords: 2500
} as const;

/**
 * 附件真实文件内容（确定性，UTF-8）：size / sha256 一律由内容推导，不由调用方随意填。
 * A / B 内容必须不同，否则无法证明「B 的附件没有混进 A 的导出」。
 */
export const V9_ATTACHMENT_A_CONTENT = "v9-baseline-attachment-A\n林晚踏入旧城的雨幕。\n";
export const V9_ATTACHMENT_B_CONTENT = "v9-baseline-attachment-B\n顾淮守在北关的城楼上。\n";

function sha256Buffer(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

/** 附件写入规格：相对路径必须位于 `resources/<projectId>/` 下。 */
export interface V9AttachmentSpec {
  projectId: string;
  cardId: string;
  relativePath: string;
  content: Buffer;
  originalName: string;
}

export interface V9AttachmentRef {
  resourceId: string;
  relativePath: string;
  sha256: string;
  size: number;
  originalName: string;
}

/**
 * 复用辅助：写入真实附件字节（仅限 `<workspaceDirectory>/resources/<projectId>/` 下）
 * 并登记 `resource.attach`（sha256 / size 由内容推导）。不触碰 SQLite。
 */
export async function attachResourceWithFile(
  workspace: CreationWorkspace,
  workspaceDirectory: string,
  spec: V9AttachmentSpec
): Promise<V9AttachmentRef> {
  const requiredPrefix = `resources/${spec.projectId}/`;
  assert.equal(
    spec.relativePath.startsWith(requiredPrefix),
    true,
    `附件相对路径必须位于 resources/<projectId>/ 下：${spec.relativePath}`
  );
  assert.equal(spec.relativePath.includes(".."), false, "附件相对路径不得包含 ..");
  assert.equal(spec.relativePath.includes("\\"), false, "附件相对路径必须是 POSIX 风格");
  const absolute = path.join(workspaceDirectory, spec.relativePath);
  const relativeToRoot = path.relative(workspaceDirectory, absolute);
  assert.equal(
    relativeToRoot.startsWith("..") || path.isAbsolute(relativeToRoot),
    false,
    "附件路径不得越出工作区根目录"
  );

  await mkdir(path.dirname(absolute), { recursive: true });
  await writeFile(absolute, spec.content);
  const sha256 = sha256Buffer(spec.content);
  const result = (await workspace.transact({
    type: "resource.attach",
    projectId: spec.projectId,
    cardId: spec.cardId,
    relativePath: spec.relativePath,
    sha256,
    size: spec.content.length,
    originalName: spec.originalName
  })) as ResourceResult;
  return {
    resourceId: result.resourceId,
    relativePath: spec.relativePath,
    sha256,
    size: spec.content.length,
    originalName: spec.originalName
  };
}

/** 未提供 workspaceDirectory 时只登记元数据（不落盘），供纯 DB 语义的契约使用。 */
async function attachResourceMetadataOnly(
  workspace: CreationWorkspace,
  spec: V9AttachmentSpec
): Promise<V9AttachmentRef> {
  const sha256 = sha256Buffer(spec.content);
  const result = (await workspace.transact({
    type: "resource.attach",
    projectId: spec.projectId,
    cardId: spec.cardId,
    relativePath: spec.relativePath,
    sha256,
    size: spec.content.length,
    originalName: spec.originalName
  })) as ResourceResult;
  return {
    resourceId: result.resourceId,
    relativePath: spec.relativePath,
    sha256,
    size: spec.content.length,
    originalName: spec.originalName
  };
}

/** 已种入的 v9 基线实体 id（重开后仍应可读）。 */
export interface V9Baseline {
  projectA: string;
  volumeA: string;
  chapterA: string;
  sceneA: string;
  projectB: string;
  sceneB: string;
  cardTypeA: string;
  cardTypeB: string;
  /** 自定义卡片类型对应的 kind（v9 由系统生成 `custom-xxxxxxxx`）。 */
  cardKindA: string;
  cardACharacter: string;
  cardALocation: string;
  cardASkill: string;
  cardBCharacter: string;
  cardBLocation: string;
  annotationA: string;
  annotationB: string;
  /** 项目 A 的附件（M0.1 的 resourceRelativePath / resourceSha256 / resourceSize 即 A 的这一组）。 */
  resourceAId: string;
  resourceRelativePath: string;
  resourceSha256: string;
  resourceSize: number;
  /** 项目 B 的附件（内容与 A 不同，用于证明项目包与扫描的范围隔离）。 */
  resourceBId: string;
  resourceBRelativePath: string;
  resourceBSha256: string;
  resourceBSize: number;
}

/** 种入选项：提供 workspaceDirectory 时写真实附件文件，否则仅登记元数据。 */
export interface SeedV9BaselineOptions {
  /** 工作区根目录；附件文件写入 `<workspaceDirectory>/resources/<projectId>/`。 */
  workspaceDirectory?: string;
}

/** 场景 A 正文：第 0 段含「旧城」，供批注锚点命中。 */
function sceneABody(): CreationDocument {
  return {
    type: "doc",
    content: [
      { type: "paragraph", content: [{ type: "text", text: "林晚踏入旧城的雨幕。" }] },
      { type: "paragraph", content: [{ type: "text", text: "借书卡在灯下泛出微光。" }] }
    ]
  };
}

/** 场景 B 正文：第 0 段含「北关」，供批注锚点命中。 */
function sceneBBody(): CreationDocument {
  return {
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text: "顾淮守在北关的城楼上。" }] }]
  };
}

/**
 * 用公开命令把 v9 基线写入指定 workspace。
 * 调用方负责 workspace 的 open/close，以及“种完再重开”的校验节奏。
 */
export async function seedV9Baseline(
  workspace: CreationWorkspace,
  options: SeedV9BaselineOptions = {}
): Promise<V9Baseline> {
  const workspaceDirectory = options.workspaceDirectory;
  const attach = workspaceDirectory
    ? (spec: V9AttachmentSpec) => attachResourceWithFile(workspace, workspaceDirectory, spec)
    : (spec: V9AttachmentSpec) => attachResourceMetadataOnly(workspace, spec);

  // 1) 两个互相独立的项目（各自带默认卷 / 章 / 场景）。
  const createdA = await workspace.transact({ type: "project.create", title: V9_BASELINE.projectATitle });
  const createdB = await workspace.transact({ type: "project.create", title: V9_BASELINE.projectBTitle });
  const projectA = createdA.projectId;
  const projectB = createdB.projectId;

  // 2) 各项目一份「项目私有」自定义卡片类型（v9：card_types.project_id = 创建它的项目）。
  const cardTypeA = (await workspace.transact({
    type: "cardType.create",
    projectId: projectA,
    name: V9_BASELINE.cardTypeAName,
    fields: [
      { key: "desc", label: "描述", kind: "multiline" },
      { key: "rank", label: "等级", kind: "select", options: ["S", "A", "B"] },
      { key: "mana", label: "耗蓝", kind: "number", required: true }
    ]
  })) as CreationStructureResult;
  const cardTypeB = (await workspace.transact({
    type: "cardType.create",
    projectId: projectB,
    name: V9_BASELINE.cardTypeBName,
    fields: [
      { key: "scale", label: "比例", kind: "select", options: ["1:1", "1:100"] },
      { key: "note", label: "备注", kind: "text" }
    ]
  })) as CreationStructureResult;

  // v9 的自定义 kind 由系统生成（custom-xxxxxxxx），必须回读类型列表才能拿到。
  const typesA = await workspace.read({ kind: "cardTypes.list", projectId: projectA });
  const cardKindAMatch = typesA.find((type) => type.id === cardTypeA.entityId);
  assert.equal(cardKindAMatch !== undefined, true, "项目 A 的自定义卡片类型必须可回读");
  const cardKindA = cardKindAMatch!.kind;

  // 3) 项目私有卡片：A 含角色 / 地点 / 自定义类型卡（带别名、标签、字段值）；B 含角色 / 地点。
  const cardACharacter = (await workspace.transact({
    type: "card.create",
    projectId: projectA,
    kind: "character",
    title: V9_BASELINE.cardACharacterTitle
  })) as CreationStructureResult;
  const cardALocation = (await workspace.transact({
    type: "card.create",
    projectId: projectA,
    kind: "location",
    title: V9_BASELINE.cardALocationTitle
  })) as CreationStructureResult;
  const cardASkill = (await workspace.transact({
    type: "card.create",
    projectId: projectA,
    kind: cardKindA,
    title: V9_BASELINE.cardASkillTitle,
    aliases: [...V9_BASELINE.cardASkillAliases],
    tags: [...V9_BASELINE.cardASkillTags],
    fields: {
      desc: V9_BASELINE.cardASkillDesc,
      rank: V9_BASELINE.cardASkillRank,
      mana: V9_BASELINE.cardASkillMana
    }
  })) as CreationStructureResult;
  const cardBCharacter = (await workspace.transact({
    type: "card.create",
    projectId: projectB,
    kind: "character",
    title: V9_BASELINE.cardBCharacterTitle
  })) as CreationStructureResult;
  const cardBLocation = (await workspace.transact({
    type: "card.create",
    projectId: projectB,
    kind: "location",
    title: V9_BASELINE.cardBLocationTitle
  })) as CreationStructureResult;

  // 3b) 修订：创建后再更新一次（追加标签），把 revision 从 1 抬到 2。
  await workspace.transact({
    type: "card.update",
    cardId: cardASkill.entityId,
    tags: [...V9_BASELINE.cardASkillTagsAfterUpdate],
    baseRevision: 1
  });

  // 3c) 卡片关系：林晚 登场于 旧城（内置关系类型，正向 / 反向均可读）。
  const relationTypesA = await workspace.read({ kind: "relationTypes.list", projectId: projectA });
  const appearsAt = relationTypesA.find((type) => type.forwardName === "登场于");
  assert.equal(appearsAt !== undefined, true, "内置关系类型「登场于」必须存在");
  await workspace.transact({
    type: "cardRelation.create",
    projectId: projectA,
    fromCardId: cardACharacter.entityId,
    toCardId: cardALocation.entityId,
    relationTypeId: appearsAt!.id,
    note: V9_BASELINE.relationNote
  });

  // 4) 正文（批注锚点需要有可命中的文本）。
  await workspace.transact({
    type: "scene.updateBody",
    sceneId: createdA.sceneId,
    baseRevision: 1,
    body: sceneABody()
  });
  await workspace.transact({
    type: "scene.updateBody",
    sceneId: createdB.sceneId,
    baseRevision: 1,
    body: sceneBBody()
  });

  // 5) 场景任务卡：视角 / 地点 / 出场均引用本项目卡片（出场即卡片 ID 引用）。
  await workspace.transact({
    type: "scene.updatePlanning",
    sceneId: createdA.sceneId,
    planning: {
      perspectiveCardId: cardACharacter.entityId,
      time: V9_BASELINE.planningTime,
      locationCardId: cardALocation.entityId,
      castCardIds: [cardACharacter.entityId, cardASkill.entityId],
      goal: V9_BASELINE.planningGoal,
      conflict: V9_BASELINE.planningConflict,
      targetWords: V9_BASELINE.planningTargetWords
    }
  });

  // 6) 批注引用卡片：A / B 各一条，各自引用本项目卡片。
  const annotationA = await workspace.transact({
    type: "annotation.create",
    projectId: projectA,
    sceneId: createdA.sceneId,
    cardId: cardASkill.entityId,
    anchor: { blockIndex: 0, textOffset: 4, textLength: 2 },
    note: V9_BASELINE.annotationANote
  });
  const annotationB = await workspace.transact({
    type: "annotation.create",
    projectId: projectB,
    sceneId: createdB.sceneId,
    cardId: cardBCharacter.entityId,
    anchor: { blockIndex: 0, textOffset: 4, textLength: 2 },
    note: V9_BASELINE.annotationBNote
  });

  // 7) 附件：A / B 各自的元数据；传了 workspaceDirectory 时同时写入确定性真实字节。
  const attachmentA = await attach({
    projectId: projectA,
    cardId: cardASkill.entityId,
    relativePath: `resources/${projectA}/ref-${V9_BASELINE.resourceFileSuffix}`,
    content: Buffer.from(V9_ATTACHMENT_A_CONTENT, "utf8"),
    originalName: V9_BASELINE.resourceOriginalName
  });
  const attachmentB = await attach({
    projectId: projectB,
    cardId: cardBCharacter.entityId,
    relativePath: `resources/${projectB}/ref-${V9_BASELINE.resourceBFileSuffix}`,
    content: Buffer.from(V9_ATTACHMENT_B_CONTENT, "utf8"),
    originalName: V9_BASELINE.resourceBOriginalName
  });

  return {
    projectA,
    volumeA: createdA.volumeId,
    chapterA: createdA.chapterId,
    sceneA: createdA.sceneId,
    projectB,
    sceneB: createdB.sceneId,
    cardTypeA: cardTypeA.entityId,
    cardTypeB: cardTypeB.entityId,
    cardKindA,
    cardACharacter: cardACharacter.entityId,
    cardALocation: cardALocation.entityId,
    cardASkill: cardASkill.entityId,
    cardBCharacter: cardBCharacter.entityId,
    cardBLocation: cardBLocation.entityId,
    annotationA: annotationA.annotationId,
    annotationB: annotationB.annotationId,
    resourceAId: attachmentA.resourceId,
    resourceRelativePath: attachmentA.relativePath,
    resourceSha256: attachmentA.sha256,
    resourceSize: attachmentA.size,
    resourceBId: attachmentB.resourceId,
    resourceBRelativePath: attachmentB.relativePath,
    resourceBSha256: attachmentB.sha256,
    resourceBSize: attachmentB.size
  };
}

/**
 * 在（通常是重开后的）workspace 上断言基线完整可读、项目私有数据互相隔离、check 通过。
 * 只读，不写入；不校验磁盘文件（需要时另调 assertV9AttachmentFiles）。
 */
export async function assertV9BaselineReadable(
  workspace: CreationWorkspace,
  baseline: V9Baseline
): Promise<void> {
  // 6) 重开后仍为 v9。当前运行时的完整性目标已是 v10，因此 fixture-only 模式下
  // check() 会准确报告“版本/关联表未升级”，不能再要求全局 ok=true。
  const report = await workspace.check();
  assert.equal(report.schemaVersion, 9, "基线仍须为 v9（未修改 SCHEMA_VERSION）");
  assert.equal(report.schema.ok, false, "v9 基线在 v10 运行时下必须被识别为待迁移");
  assert.equal(report.relations.ok, true, "v9 基线原有关系与外键必须保持完整");
  assert.equal(report.resources.ok, true, "v9 基线原有附件归属必须保持完整");

  // 1) 两个独立项目均可读。
  const projects = await workspace.read({ kind: "projects.list" });
  assert.equal(
    projects.some((project) => project.id === baseline.projectA && project.title === V9_BASELINE.projectATitle),
    true,
    "项目 A 必须可读且标题一致"
  );
  assert.equal(
    projects.some((project) => project.id === baseline.projectB && project.title === V9_BASELINE.projectBTitle),
    true,
    "项目 B 必须可读且标题一致"
  );

  // 2) 项目私有卡片：A 只看得到 A 的，看不到 B 的。
  const cardsA = await workspace.read({ kind: "cards.list", projectId: baseline.projectA });
  assert.equal(cardsA.every((card) => card.projectId === baseline.projectA), true, "A 的卡片列表不得混入其它项目");
  const titlesA = cardsA.map((card) => card.title);
  assert.equal(titlesA.includes(V9_BASELINE.cardACharacterTitle), true);
  assert.equal(titlesA.includes(V9_BASELINE.cardALocationTitle), true);
  assert.equal(titlesA.includes(V9_BASELINE.cardASkillTitle), true);
  assert.equal(cardsA.some((card) => card.id === baseline.cardBCharacter), false, "A 不应看到 B 的角色卡");
  assert.equal(cardsA.some((card) => card.id === baseline.cardBLocation), false, "A 不应看到 B 的地点卡");

  // 2) 自定义卡片类型按项目隔离；内置类型全局共享（project_id = null）。
  const typesARead = await workspace.read({ kind: "cardTypes.list", projectId: baseline.projectA });
  const customA = typesARead.find((type) => type.id === baseline.cardTypeA);
  assert.equal(customA?.name, V9_BASELINE.cardTypeAName, "A 的自定义卡片类型必须可读");
  assert.equal(customA?.projectId, baseline.projectA, "自定义类型归属创建它的项目");
  assert.equal(customA?.kind, baseline.cardKindA);
  assert.equal(customA?.fields.find((field) => field.key === "rank")?.options?.join(","), "S,A,B");
  assert.equal(customA?.fields.find((field) => field.key === "mana")?.required, true);
  assert.equal(typesARead.some((type) => type.id === baseline.cardTypeB), false, "A 不应看到 B 的自定义卡片类型");
  assert.equal(typesARead.some((type) => type.projectId === null), true, "内置卡片类型为全局共享");
  const typesBRead = await workspace.read({ kind: "cardTypes.list", projectId: baseline.projectB });
  assert.equal(typesBRead.some((type) => type.id === baseline.cardTypeB), true, "B 的自定义卡片类型必须可读");
  assert.equal(typesBRead.some((type) => type.id === baseline.cardTypeA), false, "B 不应看到 A 的自定义卡片类型");

  // 3) 别名 / 标签 / 字段值 / 修订。
  const skill = await workspace.read({ kind: "card.read", cardId: baseline.cardASkill });
  assert.equal(skill?.id, baseline.cardASkill, "自定义类型卡片必须可读");
  assert.equal(skill?.kind, baseline.cardKindA);
  assert.deepEqual(skill?.aliases, [...V9_BASELINE.cardASkillAliases]);
  assert.deepEqual(skill?.tags, [...V9_BASELINE.cardASkillTagsAfterUpdate]);
  assert.equal(skill?.fields.desc, V9_BASELINE.cardASkillDesc);
  assert.equal(skill?.fields.rank, V9_BASELINE.cardASkillRank);
  assert.equal(skill?.fields.mana, V9_BASELINE.cardASkillMana);
  assert.equal(skill?.revision, V9_BASELINE.cardASkillRevision, "card.update 后 revision 必须 +1");

  // 3c) 卡片关系：正向（登场于）与反向均可读。
  const outgoingRelations = await workspace.read({ kind: "card.relations", cardId: baseline.cardACharacter });
  const relation = outgoingRelations.outgoing.find((item) => item.toCardId === baseline.cardALocation);
  assert.equal(relation?.forwardName, "登场于", "林晚 登场于 旧城 的正向关系必须存在");
  assert.equal(relation?.note, V9_BASELINE.relationNote);
  const incomingRelations = await workspace.read({ kind: "card.relations", cardId: baseline.cardALocation });
  assert.equal(
    incomingRelations.incoming.some((item) => item.fromCardId === baseline.cardACharacter),
    true,
    "反向关系必须可读"
  );

  // 5) 场景任务卡的卡片引用：视角 / 地点 / 出场。
  const outline = await workspace.read({ kind: "project.outline", projectId: baseline.projectA });
  const firstScene = outline?.volumes[0]?.chapters[0]?.scenes[0];
  assert.equal(firstScene?.id, baseline.sceneA, "项目 A 的首场景必须是基线场景");
  const planning = firstScene?.planning;
  assert.equal(planning?.perspectiveCardId, baseline.cardACharacter, "任务卡视角引用基线角色卡");
  assert.equal(planning?.locationCardId, baseline.cardALocation, "任务卡地点引用基线地点卡");
  assert.equal(planning?.time, V9_BASELINE.planningTime);
  assert.equal(planning?.goal, V9_BASELINE.planningGoal);
  assert.equal(planning?.targetWords, V9_BASELINE.planningTargetWords);
  assert.deepEqual(planning?.castCardIds, [baseline.cardACharacter, baseline.cardASkill], "任务卡出场引用基线卡片");

  // 6) 批注对卡片的引用（并确认批注按项目隔离）。
  const annotationsA = await workspace.read({ kind: "annotation.list", projectId: baseline.projectA });
  const annotationA = annotationsA.find((item) => item.id === baseline.annotationA);
  assert.equal(annotationA?.cardId, baseline.cardASkill, "批注必须引用项目 A 的卡片");
  assert.equal(annotationA?.sceneId, baseline.sceneA);
  assert.equal(annotationA?.anchorInvalid, false);
  assert.equal(annotationA?.anchoredText, V9_BASELINE.sceneAAnchoredText);
  const annotationsB = await workspace.read({ kind: "annotation.list", projectId: baseline.projectB });
  assert.equal(annotationsB.some((item) => item.id === baseline.annotationB), true, "项目 B 的批注必须可读");
  assert.equal(annotationsB.some((item) => item.id === baseline.annotationA), false, "B 不应看到 A 的批注");

  // 7) 附件元数据：A / B 各一条，挂在各自项目的卡片上，且互不可见。
  const resourcesA = await workspace.read({ kind: "resource.list", projectId: baseline.projectA });
  const resourceA = resourcesA.find((item) => item.relativePath === baseline.resourceRelativePath);
  assert.equal(resourceA?.id, baseline.resourceAId);
  assert.equal(resourceA?.cardId, baseline.cardASkill, "A 的附件元数据应挂在项目 A 的卡片上");
  assert.equal(resourceA?.sha256, baseline.resourceSha256);
  assert.equal(resourceA?.size, baseline.resourceSize);
  assert.equal(resourcesA.some((item) => item.id === baseline.resourceBId), false, "A 不应看到 B 的附件记录");
  assert.equal(
    resourcesA.some((item) => item.relativePath === baseline.resourceBRelativePath),
    false,
    "A 不应看到 B 的附件路径"
  );
  const resourcesB = await workspace.read({ kind: "resource.list", projectId: baseline.projectB });
  const resourceB = resourcesB.find((item) => item.relativePath === baseline.resourceBRelativePath);
  assert.equal(resourceB?.id, baseline.resourceBId);
  assert.equal(resourceB?.cardId, baseline.cardBCharacter, "B 的附件元数据应挂在项目 B 的卡片上");
  assert.equal(resourceB?.sha256, baseline.resourceBSha256);
  assert.equal(resourceB?.size, baseline.resourceBSize);
  assert.equal(resourcesB.some((item) => item.id === baseline.resourceAId), false, "B 不应看到 A 的附件记录");
}

/**
 * 校验 A / B 两份附件的真实磁盘字节与 DB 登记一致（大小 + SHA-256）。
 * 仅在 seedV9Baseline 传入了 workspaceDirectory 时有意义。只读，不写入。
 */
export async function assertV9AttachmentFiles(
  workspaceDirectory: string,
  baseline: V9Baseline
): Promise<void> {
  const entries = [
    {
      label: "项目 A",
      relativePath: baseline.resourceRelativePath,
      sha256: baseline.resourceSha256,
      size: baseline.resourceSize
    },
    {
      label: "项目 B",
      relativePath: baseline.resourceBRelativePath,
      sha256: baseline.resourceBSha256,
      size: baseline.resourceBSize
    }
  ];
  for (const entry of entries) {
    const absolute = path.join(workspaceDirectory, entry.relativePath);
    let info;
    try {
      info = await lstat(absolute);
    } catch {
      info = null;
    }
    assert.equal(info !== null && info.isFile(), true, `${entry.label} 的附件文件必须存在且为普通文件`);
    assert.equal(info!.size, entry.size, `${entry.label} 的附件文件大小必须与登记一致`);
    assert.equal(
      sha256Buffer(await readFile(absolute)),
      entry.sha256,
      `${entry.label} 的附件文件哈希必须与登记一致`
    );
  }
}
