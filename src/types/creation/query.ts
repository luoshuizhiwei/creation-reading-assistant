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
  projectId: string;
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
  projectId: string;
}

export interface RelationTypesListQuery {
  kind: "relationTypes.list";
  projectId: string;
}

export interface CardRelationsQuery {
  kind: "card.relations";
  cardId: string;
}

export interface TrashListQuery {
  kind: "trash.list";
  projectId: string;
}

export interface TrashImpactQuery {
  kind: "trash.impact";
  projectId: string;
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
  projectId: string;
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

export interface ProofIssue {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  sceneTitle: string;
  rule: ProofRule;
  /** 人类可读说明。 */
  message: string;
  /** 上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 该场景该规则下的问题数。 */
  count: number;
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
  /** 问题数上限（场景×规则聚合后），默认 200，最大 2000。 */
  limit?: number;
}

export interface ProofView {
  projectId: string;
  issues: ProofIssue[];
  /** 检查的场景数。 */
  scannedScenes: number;
  /** 命中问题的场景数。 */
  affectedScenes: number;
  /** 问题总数（等于 issues 条数）。 */
  total: number;
}