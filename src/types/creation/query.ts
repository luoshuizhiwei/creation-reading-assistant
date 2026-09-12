import type {
  TrashEntityKind,
  SnapshotSubjectType,
  CreationSearchScope,
  ReplaceScope,
  ProofRule
} from "./primitives";

export interface ProjectHomeQuery {
  kind: "project.home";
}

export interface ReadProjectOutlineQuery {
  kind: "project.outline";
  projectId: string;
}

export interface CardsListQuery {
  kind: "cards.list";
  /** 省略时读取全局卡片库；提供时只读取该项目已关联卡片。 */
  projectId?: string;
  /** 按卡片类型 kind 筛选（内置或自定义）。 */
  cardKind?: string;
  /** 标题/别名子串搜索。 */
  search?: string;
}

export interface CardReadQuery {
  kind: "card.read";
  cardId: string;
}

export interface CardTypesListQuery {
  kind: "cardTypes.list";
  /** @deprecated v10 忽略该值；保留到旧调用点迁移完毕。 */
  projectId?: string;
}

export interface RelationTypesListQuery {
  kind: "relationTypes.list";
  /** @deprecated v10 忽略该值；保留到旧调用点迁移完毕。 */
  projectId?: string;
}

export interface CardRelationsQuery {
  kind: "card.relations";
  cardId: string;
}

/** 关系图节点：一张卡片的摘要投影（卡片正文不进图，只进摘要）。 */
export interface RelationGraphNode {
  cardId: string;
  title: string;
  kind: string;
  /** 卡片类型显示名（无匹配类型时回落为 kind）。 */
  kindName: string;
  aliases: string[];
  /** 关键字段摘要：`key: value` 以「；」连接，最多 3 项。 */
  summary: string;
  /** 该节点在**当前结果集**内的关联条数（度数）。 */
  degree: number;
}

/** 关系图连线：一条全局卡片关系。方向语义由 UI 按选中节点决定。 */
export interface RelationGraphEdge {
  id: string;
  fromCardId: string;
  toCardId: string;
  relationTypeId: string;
  /** 关系类型名（如「师徒」）。 */
  relationName: string;
  /** 正向读法（如「师父」）。 */
  forwardName: string;
  /** 反向读法（如「徒弟」）。 */
  reverseName: string;
  note: string | null;
}

/** 关系图里的一种卡片类型（供过滤与图例）。 */
export interface RelationGraphKindFacet {
  kind: string;
  name: string;
  count: number;
}

/** 关系图里的一种关系类型（供过滤与图例）。 */
export interface RelationGraphTypeFacet {
  id: string;
  name: string;
  forwardName: string;
  reverseName: string;
  count: number;
}

/**
 * 关系图整图查询：一次性取回节点与连线，避免 UI 端按卡片逐个拉关系（N+1）。
 * 关系本体是全局卡片资产；提供 projectId 时返回**该项目已关联卡片的引用投影**。
 */
export interface RelationGraphQuery {
  kind: "relationGraph.list";
  /** 省略时读取全局卡片库全图；提供时只取该项目已关联卡片构成的子图。 */
  projectId?: string;
  /** 节点上限，默认 150，范围 20..400。 */
  limit?: number;
}

export interface RelationGraphView {
  /** 省略 projectId 时为「全局卡片库」，否则为「项目引用投影」。 */
  scope: "global" | "project";
  projectId: string | null;
  nodes: RelationGraphNode[];
  edges: RelationGraphEdge[];
  /** 参与图的卡片类型分面（按数量降序）。 */
  kindFacets: RelationGraphKindFacet[];
  /** 参与图的关系类型分面（按数量降序）。 */
  relationFacets: RelationGraphTypeFacet[];
  /** 因节点上限被截断而未返回的卡片数。 */
  truncatedNodeCount: number;
  /**
   * 项目视图下指向/来自「本项目未关联卡片」的关系条数。
   * 这些关系不绘制（不把项目外的全局卡片拉进项目视图），但数量必须显式告知。
   */
  hiddenRelationCount: number;
  /** 未参与任何关系的卡片数（默认仍然显示，用户可过滤）。 */
  isolatedNodeCount: number;
}

export interface TrashListQuery {
  kind: "trash.list";
  /** 省略时只列出全局卡片回收站。 */
  projectId?: string;
}

export interface TrashImpactQuery {
  kind: "trash.impact";
  /** 全局卡片影响预览不带 projectId。 */
  projectId?: string;
  entity: TrashEntityKind;
  entityId: string;
}

export interface TrashImpactView {
  title: string;
  childVolumeCount: number;
  childChapterCount: number;
  childSceneCount: number;
  relatedCardCount: number;
  resourceCount: number;
  linkedProjectCount?: number;
  sceneReferenceCount?: number;
  annotationCount?: number;
  approxChars: number;
  warnings: string[];
}

export interface SnapshotListQuery {
  kind: "snapshot.list";
  projectId: string;
  subjectType?: SnapshotSubjectType;
  subjectId?: string;
}

export interface SnapshotPreviewQuery {
  kind: "snapshot.preview";
  projectId: string;
  snapshotId: string;
}

export interface AnnotationListQuery {
  kind: "annotation.list";
  projectId: string;
  sceneId?: string;
  /** 返回上限，默认 200，最大 2000。 */
  limit?: number;
}

export interface ResourceListQuery {
  kind: "resource.list";
  /** 省略时 cardId 必填，只读取全局卡片资产。 */
  projectId?: string;
  cardId?: string;
}

export interface InboxListQuery {
  kind: "inbox.list";
  /** 返回上限，默认 100，最大 500。 */
  limit?: number;
  /** 分页偏移量，默认 0。迁移全量校验使用分页读取超过 500 条的收件箱。 */
  offset?: number;
}

/** 收件箱计数（不加载条目正文，供概览与项目首页共用）。 */
export interface InboxCountQuery {
  kind: "inbox.count";
}

export interface InboxCountView {
  /** 未删除条目总数。 */
  total: number;
  /** 未删除且状态不是 used（已转卡片）的待处理条目数。 */
  pending: number;
}

export interface InboxReadQuery {
  kind: "inbox.read";
  itemId: string;
}

export interface ProjectExportQuery {
  kind: "project.export";
  projectId: string;
  /** 为 true 时场景附带 blocks（kind + 文本）；默认只返回 text。 */
  includeBlocks?: boolean;
}

export interface ProjectBundleExportQuery {
  kind: "project.bundle.export";
  projectId: string;
}

export interface CreationSearchFilters {
  /** 仅搜索指定卡片类型（kind）。 */
  cardKinds?: string[];
  /** 仅搜索指定章节工作流状态。 */
  chapterStatuses?: string[];
  /** 仅搜索带指定标签的卡片（标签全命中）。 */
  tags?: string[];
}

export interface CreationSearchQuery {
  kind: "search.query";
  /** 搜索关键词（普通文本子串匹配，不做正则）。 */
  text: string;
  /** 限定单个项目；缺省时全局搜索所有项目。 */
  projectId?: string;
  /** 搜索范围；缺省覆盖全部四种。 */
  scopes?: CreationSearchScope[];
  filters?: CreationSearchFilters;
  /** 返回上限，默认 50，最大 200。 */
  limit?: number;
}

export interface CreationSearchHit {
  kind: CreationSearchScope;
  id: string;
  projectId: string;
  projectTitle: string;
  title: string;
  /** 命中上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 场景/章节命中时给出父章节信息。 */
  chapterId?: string;
  chapterTitle?: string;
  chapterStatus?: string;
  /** 卡片命中时的卡片类型与标签。 */
  cardKind?: string;
  tags?: string[];
  updatedAt: string;
}

export interface CreationSearchView {
  query: string;
  hits: CreationSearchHit[];
  total: number;
}

export interface ReplacePreviewHit {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  sceneTitle: string;
  /** 该场景命中次数。 */
  count: number;
  /** 命中上下文示例（最多 5 条，按出现顺序）。 */
  snippets: string[];
}

export interface ReplacePreviewQuery {
  kind: "replace.preview";
  projectId: string;
  /** 查找文本（普通文本或受限正则，见 regex）。 */
  find: string;
  /** 替换文本（普通字符串；regex 模式下支持 $1 引用捕获组）。 */
  replaceWith: string;
  /** 替换范围：项目 / 卷 / 章 / 场景。 */
  scope: ReplaceScope;
  /** scope 为 volume/chapter/scene 时对应的实体 ID。 */
  scopeId?: string;
  /** 高级模式：把 find 视为受限正则（禁用 lookaround/backreference，长度 ≤ 200）。 */
  regex?: boolean;
  /** 预览场景数上限，默认 200，最大 1000。 */
  limit?: number;
}

export interface ReplacePreviewView {
  projectId: string;
  find: string;
  replaceWith: string;
  scope: ReplaceScope;
  sceneHits: ReplacePreviewHit[];
  /** 全部命中总数。 */
  totalHits: number;
  /** 命中场景数。 */
  matchedScenes: number;
}

export interface StatsViewQuery {
  kind: "stats.view";
  projectId: string;
}

export interface SessionListQuery {
  kind: "session.list";
  projectId: string;
  /** 返回上限，默认 100，最大 500。 */
  limit?: number;
}

/**
 * 单条校对命中的位置。
 * 位置键是「项目 + 场景 + 规则 + locationKey」四元组的一部分，
 * 因此忽略某个位置不会波及其它位置（同场景其它段落、同文本的其它出现、其它项目）。
 */
export interface ProofLocation {
  /** 稳定位置键：`规则#段落序号#命中文本哈希`；段落序号为 -1 表示该规则以整场为粒度。 */
  locationKey: string;
  /** 段落序号（0 起）；-1 表示整场粒度。 */
  paragraphIndex: number;
  /** 命中文本（规范化前），用于展示与忽略记录校验。 */
  matchedText: string;
  /** 上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 位置级说明（可能为 null，此时回落到规则级说明）。 */
  detail: string | null;
  /** 该位置是否已被持久忽略。 */
  ignored: boolean;
}

export interface ProofIssue {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  sceneTitle: string;
  rule: ProofRule;
  /** 人类可读说明（按未忽略命中数聚合）。 */
  message: string;
  /** 上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 该场景该规则下**未忽略**的命中数。 */
  count: number;
  /** 该场景该规则下已忽略的命中数。 */
  ignoredCount: number;
  /** 命中的位置明细（含已忽略位置，便于逐个取消忽略）。 */
  locations: ProofLocation[];
}

/** 本次校对实际覆盖的范围，用于在 UI 中显式说明「扫描了什么」。 */
export interface ProofScanScope {
  /** project = 全书扫描；scene = 单场景扫描。 */
  kind: "project" | "scene";
  /** 人类可读范围描述。 */
  label: string;
  volumeCount: number;
  chapterCount: number;
  sceneCount: number;
  /** 本次启用的规则。 */
  rules: ProofRule[];
  /** 本次生效的禁用词。 */
  bannedWords: string[];
  /** 本次生效的超长段落阈值。 */
  maxParagraphChars: number;
}

/** 一条持久化的忽略记录。 */
export interface ProofIgnoreEntry {
  id: string;
  projectId: string;
  sceneId: string;
  sceneTitle: string;
  chapterId: string;
  chapterTitle: string;
  rule: ProofRule;
  locationKey: string;
  matchedText: string;
  note: string;
  createdAt: string;
}

export interface ProofQuery {
  kind: "proof.query";
  projectId: string;
  /** 限定单个场景；缺省检查项目全部场景。 */
  sceneId?: string;
  /** 启用的规则；缺省全部启用。 */
  rules?: ProofRule[];
  /** 用户自定义禁用词（子串匹配）。 */
  bannedWords?: string[];
  /** 超长段落阈值（字符数），默认 500，范围 100..5000。 */
  maxParagraphChars?: number;
  /** 问题分组数上限，默认 200，最大 2000。 */
  limit?: number;
  /** 为 true 时同时返回已忽略问题（`ignoredIssues`）。默认 false。 */
  includeIgnored?: boolean;
}

export interface ProofView {
  projectId: string;
  /** 本次扫描范围（显式展示给用户）。 */
  scanScope: ProofScanScope;
  /** 存在未忽略问题的分组，按章节/场景顺序。 */
  issues: ProofIssue[];
  /** 全部命中位置都被忽略的分组；仅在 includeIgnored 为 true 时返回。 */
  ignoredIssues: ProofIssue[];
  /** 实际检查的场景数。 */
  scannedScenes: number;
  /** 命中问题的场景数。 */
  affectedScenes: number;
  /** 未忽略的命中位置总数（截断前）。 */
  total: number;
  /** 已忽略的命中位置总数（截断前）。 */
  ignoredCount: number;
  /** 忽略前的命中位置总数（= total + ignoredCount）。 */
  rawTotal: number;
  /** 未忽略结果是否因 limit 被截断。 */
  truncated: boolean;
  /** 已忽略结果是否因 limit 被截断。 */
  ignoredTruncated: boolean;
  /** 本项目持久化忽略记录总数。 */
  ignoreRecordCount: number;
}

/** 列出项目的全部持久化忽略记录。 */
export interface ProofIgnoreListQuery {
  kind: "proof.ignores";
  projectId: string;
}
