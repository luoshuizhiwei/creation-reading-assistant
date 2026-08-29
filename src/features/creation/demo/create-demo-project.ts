import { annotationCreate, runStructure as runStructureRequest } from "@/services/creation-service";
import { DEFAULT_CHAPTER_WORKFLOW } from "@/features/creation/wizard-model";
import type { CreationDocument, CreateProjectInput, CreationProjectNavigation, CreationRunCommand, ScenePlanning } from "@/types/creation";

/**
 * 演示项目（对标 Novalist demo_novel）：一键载入一个预置了场景正文、任务卡、
 * 卡片关系与伏笔（一开一收）的迷你项目，让新用户立刻看见创作雷达、大纲、
 * 卡片与伏笔生命周期的实际形态。全部走既有 runStructure 命令，不加 IPC、不改存储。
 */

interface DemoActions {
  createProject(input: CreateProjectInput): Promise<CreationProjectNavigation | undefined>;
  /** 正文保存走独立通道（scene.updateBody 不在 runStructure 命令目录内）。 */
  saveSceneBody(sceneId: string, baseRevision: number, body: CreationDocument): Promise<unknown>;
}

interface StructureResult {
  entityId?: string;
}

const SCENE_BODIES: string[][] = [
  [
    "闭馆铃响过第三遍，林晚还站在借书台后。窗外雨势不减，日光灯把整个大厅照得像一口白瓷缸。",
    "整理还书时，一张借书卡从《城市绘图史》里滑了出来。卡面没有姓名，只有一行手写的小字：第七排，靠窗。",
    "她抬眼望向第七排。靠窗的位置空着，椅背上搭着一把还在滴水的黑伞。"
  ],
  [
    "陈默是踩着闭馆铃进来的。他收伞的动作很慢，目光在借书台上一扫而过，像只是路过。",
    "「找一本书。」他把纸条推过台面。林晚低头看了一眼——正是那本《城市绘图史》。",
    "「这本书今天刚被人还回来。」她说。陈默的手在纸条上停了两秒。"
  ],
  [
    "第二天早上，林晚在书车底层发现了那本日记。缺了三页，撕口很新。",
    "她把日记和借书卡并排放在一起，忽然明白过来：有人在替另一个人，一本书一本书地还债。"
  ]
];

const SCENE_PLANNINGS: Array<ScenePlanning & { targetWords?: number | null }> = [
  {
    time: "闭馆前的雨夜",
    goal: "查明借书卡的主人是谁",
    conflict: "那张卡不该出现在这本书里",
    emotion: "克制的好奇",
    targetWords: 1200
  },
  {
    time: "同一夜的九点后",
    goal: "弄清陈默找这本书的真正目的",
    conflict: "双方都在试探，谁也不先说破",
    emotion: "紧绷的对峙",
    targetWords: 1500
  },
  {
    time: "次日清晨",
    goal: "把借书卡与缺页日记对上",
    conflict: "线索指向图书馆之外",
    emotion: "恍然与不安",
    targetWords: 800
  }
];

/** 依次执行演示项目命令；任何一步失败即抛出，由调用方提示用户。 */
export async function createDemoProject(actions: DemoActions): Promise<string | undefined> {
  const navigation = await actions.createProject({
    title: "演示·雨夜图书馆",
    template: "long-form",
    totalWordGoal: 30000,
    weeklyUpdateDays: [0, 6],
    chapterWorkflow: [...DEFAULT_CHAPTER_WORKFLOW]
  });
  if (!navigation) return undefined;
  const projectId = navigation.project.id;
  const chapter = navigation.chapters[0];
  if (!chapter) return undefined;

  // 必须用返回 CreationStructureResult 的底层服务：useCreationActions.runStructure 返回 boolean，
  // 会把 entityId 一并吞掉，导致编排器拿到 undefined 的场景/卡片 ID。
  const transact = async (command: CreationRunCommand): Promise<StructureResult> =>
    (await runStructureRequest(command)) as StructureResult;

  // 三个场景：首场景已随项目创建，再补两个。
  const sceneIds: string[] = [chapter.scenes[0]!.id];
  for (let index = 1; index < SCENE_BODIES.length; index += 1) {
    const created = await transact({
      type: "scene.create",
      chapterId: chapter.id,
      title: `场景${index + 1}`
    });
    sceneIds.push(created.entityId!);
  }

  // 卡片先建（任务卡的视角/地点/出场要引用卡片 ID）。
  const cardIds: Record<string, string> = {};
  const cardDefs: Array<{ key: string; kind: string; title: string; aliases?: string[]; fields?: Record<string, unknown> }> = [
    { key: "wan", kind: "character", title: "林晚", aliases: ["阿晚"], fields: { note: "图书馆夜班管理员，习惯把疑问记在便签上。" } },
    { key: "mo", kind: "character", title: "陈默", aliases: [], fields: { note: "总是踩着闭馆铃到访的读者，话很少。" } },
    { key: "library", kind: "location", title: "旧图书馆", aliases: [], fields: { note: "第七排靠窗的位置常年空着。" } },
    {
      key: "card-mystery",
      kind: "foreshadow",
      title: "无名的借书卡",
      fields: { status: "未回收", plantedIn: "场景1", note: "卡上只有一行手写小字：第七排，靠窗。" }
    },
    {
      key: "diary",
      kind: "foreshadow",
      title: "缺页的日记",
      fields: { status: "已回收", plantedIn: "场景2", resolution: "场景3 揭示：有人在替另一个人还书。", note: "缺了三页，撕口很新。" }
    }
  ];
  for (const def of cardDefs) {
    const created = await transact({
      type: "card.create",
      projectId,
      kind: def.kind,
      title: def.title,
      aliases: def.aliases ?? [],
      fields: def.fields ?? {}
    });
    cardIds[def.key] = created.entityId!;
  }

  // 关系：林晚 登场于 旧图书馆（内置关系类型 character-location）。
  await transact({
    type: "cardRelation.create",
    projectId,
    fromCardId: cardIds.wan!,
    toCardId: cardIds.library!,
    relationTypeId: "relation-type-character-location"
  });

  // 正文 + 任务卡。
  for (let index = 0; index < sceneIds.length; index += 1) {
    const sceneId = sceneIds[index]!;
    if (!sceneId) throw new Error("演示项目命令失败：缺少场景 ID");
    const saved = (await actions.saveSceneBody(
      sceneId,
      1,
      {
        type: "doc",
        content: SCENE_BODIES[index]!.map((text) => ({
          type: "paragraph" as const,
          content: [{ type: "text" as const, text }]
        }))
      }
    )) as { ok?: boolean } | undefined;
    if (!saved?.ok) throw new Error(`演示项目正文保存失败：scene=${sceneId} result=${JSON.stringify(saved)}`);
    const planning = index === 0
      ? { ...SCENE_PLANNINGS[index], perspectiveCardId: cardIds.wan, locationCardId: cardIds.library, castCardIds: [cardIds.wan!, cardIds.mo!] }
      : { ...SCENE_PLANNINGS[index], castCardIds: index === 1 ? [cardIds.wan!, cardIds.mo!] : [cardIds.wan!] };
    await transact({
      type: "scene.updatePlanning",
      sceneId,
      planning
    });
  }

  // 批注（关联卡片）：把伏笔锚到正文真实段落，驱动雷达的引用 chip 与状态徽标。
  const annotations: Array<[string, string, { blockIndex: number; textOffset: number; textLength: number; text: string }, string, "open" | "resolved"]> = [
    [sceneIds[0]!, cardIds["card-mystery"]!, { blockIndex: 2, textOffset: 0, textLength: 8, text: "她抬眼望向第七排" }, "伏笔埋设：黑伞主人的身份线索。", "open"],
    [sceneIds[2]!, cardIds.diary!, { blockIndex: 1, textOffset: 0, textLength: 10, text: "她把日记和借书卡并排" }, "伏笔回收：日记与借书卡在此对上。", "resolved"]
  ];
  for (const [sceneId, cardId, anchor, note, status] of annotations) {
    await annotationCreate({ type: "annotation.create", projectId, sceneId, cardId, anchor, note, status });
  }

  return projectId;
}
