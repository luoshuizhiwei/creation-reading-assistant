/**
 * 分层自动快照留存规划器（纯模块，不依赖 SQLite / electron / renderer）。
 *
 * 设计目标（来自桌面端 P1 规格 §5.9 与“分层快照留存”任务）：
 * - 自动快照 / 命名里程碑 / 保护快照必须按「系统受控字段」显式分类，
 *   绝不根据用户可编辑的 reason 文本推断类型（需求 1）。
 * - 命名里程碑永久保留（需求 2）；保护快照（重组 / 替换 / 恢复前创建）永久保留，
 *   绝不会被自动清理误删（需求 4、7）。
 * - 自动快照采用确定性分层（需求 3）：
 *     · 创建后 24h 内：全部保留（密集）；
 *     · 24h < 年龄 ≤ 30 天：每对象（project + subjectType + subjectId）每个 UTC 日保留最新一份；
 *     · 年龄 > 30 天：每对象每个 UTC 周（周一为界）保留最新一份。
 * - planner 输入为「快照元数据数组 + 注入的当前时间」，输出 keep/delete ID 集合；
 *   不直接依赖 SQLite（需求 6）。
 * - 时间通过依赖注入；同一输入重复运行结果一致（确定性、幂等，需求 7、8）。
 * - 日 / 周边界使用 UTC 日历边界，与运行机器本地时区无关（确定性、可测，需求“时区和周边界”）。
 * - 损坏元数据（缺 id、createdAt 不可解析、kind 非法）不会进入 deleteIds，
 *   也不会影响其他快照的留存判断（失败保守：保留，需求“损坏元数据不会误删其他快照”）。
 */

/** 系统受控分类。绝不来自用户可编辑的 reason。 */
export type SnapshotRetentionKind = "auto" | "milestone" | "protected";

/** planner 的输入元数据。kind 必须由上游显式给出（见 classifySnapshotMeta）。 */
export interface SnapshotRetentionMeta {
  id: string;
  projectId: string;
  subjectType: string;
  subjectId: string;
  kind: SnapshotRetentionKind;
  /** ISO 8601 时间戳字符串（UTC 或带偏移均可，内部按绝对时刻解析）。 */
  createdAt: string;
}

/** planner 输出：保留 / 删除 ID 集合。keepIds ∪ deleteIds 覆盖全部有效输入。 */
export interface RetentionPlan {
  keepIds: string[];
  deleteIds: string[];
}

/** 仅用于分类的原始行（来自 snapshots 表），不暴露 reason。 */
export interface SnapshotRow {
  id: string;
  subjectType: string;
  subjectId: string;
}

export interface RetentionOptions {
  /** 密集窗口（毫秒）。默认 24h。 */
  denseWindowMs?: number;
  /** 每日窗口上界（毫秒）。默认 30d。超过此值进入每周桶。 */
  dailyWindowMs?: number;
}

/** 时间窗口常量（毫秒）。 */
export const DENSE_WINDOW_MS = 24 * 60 * 60 * 1000; // 24 小时
export const DAILY_WINDOW_MS = 30 * 24 * 60 * 60 * 1000; // 30 天

/**
 * 系统受控分类：仅依据 subjectType 与 id 前缀，绝不读取 reason。
 * - protective 前缀或 structure-operation → protected（重组/替换/恢复前保护快照）
 * - scene-autosave → auto（每场景自动保存）
 * - 其余（scene/card/chapter/volume 且 id 以 snapshot- 开头）→ milestone（用户命名里程碑）
 *
 * 该规则在主进程（本规划器上游）与 renderer（UI 分类器）必须保持一致。
 */
export function classifySnapshotMeta(row: SnapshotRow): SnapshotRetentionKind {
  if (row.id.startsWith("protective") || row.subjectType === "structure-operation") {
    return "protected";
  }
  if (row.subjectType === "scene-autosave") {
    return "auto";
  }
  return "milestone";
}

function parseTime(value: string | number | Date): number | null {
  const t = value instanceof Date ? value.getTime() : new Date(value).getTime();
  return Number.isFinite(t) ? t : null;
}

/** UTC 日键：YYYY-MM-DD（按 UTC 字段），与机器本地时区无关。 */
export function utcDayKey(timeMs: number): string {
  const d = new Date(timeMs);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** UTC 周一 00:00Z 的时间戳，作为“周桶”的稳定边界（ISO 周，周一为起点）。 */
function utcWeekStartMs(timeMs: number): number {
  const d = new Date(timeMs);
  const utcDay = d.getUTCDay(); // 0=周日..6=周六
  const isoWeekday = utcDay === 0 ? 7 : utcDay; // 周一=1..周日=7
  const daysSinceMonday = isoWeekday - 1;
  return Date.UTC(
    d.getUTCFullYear(),
    d.getUTCMonth(),
    d.getUTCDate() - daysSinceMonday,
    0,
    0,
    0,
    0
  );
}

/** UTC 周键：该周周一的 YYYY-MM-DD，与机器本地时区无关。 */
export function utcWeekKey(timeMs: number): string {
  return utcDayKey(utcWeekStartMs(timeMs));
}

function pushBucket(map: Map<string, ValidMeta[]>, key: string, m: ValidMeta): void {
  const arr = map.get(key);
  if (arr) arr.push(m);
  else map.set(key, [m]);
}

/**
 * 同桶内挑选“最新”快照：按 (createdAt 升序, id 升序) 排序后取最后。
 * 同时间戳时以 id 字典序决定，保证确定性（需求“同时间戳稳定排序”）。
 */
function pickLatest(arr: ValidMeta[]): ValidMeta {
  return [...arr].sort((a, b) => {
    if (a._createdMs !== b._createdMs) return a._createdMs - b._createdMs;
    if (a.id < b.id) return -1;
    if (a.id > b.id) return 1;
    return 0;
  }).at(-1)!;
}

interface ValidMeta extends SnapshotRetentionMeta {
  _createdMs: number;
}

/**
 * 计算分层留存计划。
 * @param metas 快照元数据（kind 已由上游显式给出）
 * @param now 注入的当前时间（Date / ISO 字符串 / 毫秒时间戳）；不允许依赖系统时钟
 * @param options 可选窗口覆盖
 */
export function planSnapshotRetention(
  metas: readonly SnapshotRetentionMeta[],
  now: Date | string | number,
  options?: RetentionOptions
): RetentionPlan {
  const nowMs = parseTime(now);
  if (nowMs === null) {
    throw new Error("planSnapshotRetention: 无效的当前时间（now）。");
  }
  const denseWindow = options?.denseWindowMs ?? DENSE_WINDOW_MS;
  const dailyWindow = options?.dailyWindowMs ?? DAILY_WINDOW_MS;

  const keepIds = new Set<string>();
  const deleteIds = new Set<string>();
  const autoValid: ValidMeta[] = [];

  // 1) 非 auto（里程碑 / 保护）恒保留；损坏元数据保守保留且不参与删除决策。
  for (const m of metas) {
    if (!m || typeof m.id !== "string" || m.id.length === 0) {
      // 缺 id：无法安全定位，保守保留（不进入任何集合）。
      continue;
    }
    if (m.kind === "milestone" || m.kind === "protected") {
      keepIds.add(m.id);
      continue;
    }
    // 仅处理 kind === "auto"；kind 缺失/非法 → 失败保守：保留。
    if (m.kind !== "auto") {
      keepIds.add(m.id);
      continue;
    }
    const createdMs = parseTime(m.createdAt);
    if (
      createdMs === null ||
      typeof m.projectId !== "string" ||
      m.projectId.length === 0 ||
      typeof m.subjectType !== "string" ||
      typeof m.subjectId !== "string"
    ) {
      // createdAt 不可解析或归属字段缺失：保守保留，绝不影响其他快照。
      keepIds.add(m.id);
      continue;
    }
    autoValid.push({ ...m, _createdMs: createdMs });
  }

  // 2) 按 (projectId, subjectType, subjectId) 分组，逐个对象独立留存（多项目 / 多对象隔离）。
  const groups = new Map<string, ValidMeta[]>();
  for (const m of autoValid) {
    const key = `${m.projectId}\u0000${m.subjectType}\u0000${m.subjectId}`;
    const arr = groups.get(key);
    if (arr) arr.push(m);
    else groups.set(key, [m]);
  }

  for (const arr of groups.values()) {
    const dailyBuckets = new Map<string, ValidMeta[]>();
    const weeklyBuckets = new Map<string, ValidMeta[]>();

    for (const m of arr) {
      const age = nowMs - m._createdMs;
      if (age <= denseWindow) {
        // 密集窗口：全部保留。
        keepIds.add(m.id);
      } else if (age <= dailyWindow) {
        pushBucket(dailyBuckets, utcDayKey(m._createdMs), m);
      } else {
        pushBucket(weeklyBuckets, utcWeekKey(m._createdMs), m);
      }
    }

    // 每日桶：每 UTC 日保留最新一份，其余删除。
    for (const bucket of dailyBuckets.values()) {
      const winner = pickLatest(bucket);
      for (const m of bucket) {
        if (m.id === winner.id) keepIds.add(m.id);
        else deleteIds.add(m.id);
      }
    }
    // 每周桶：每 UTC 周保留最新一份，其余删除。
    for (const bucket of weeklyBuckets.values()) {
      const winner = pickLatest(bucket);
      for (const m of bucket) {
        if (m.id === winner.id) keepIds.add(m.id);
        else deleteIds.add(m.id);
      }
    }
  }

  // keep 优先：理论不相交，双保险。
  for (const id of keepIds) deleteIds.delete(id);

  return { keepIds: [...keepIds], deleteIds: [...deleteIds] };
}

/** 在调用方提供的事务内执行删除。throw 时由调用方回滚 → 零删除（需求 9）。 */
export interface RetentionDeleter {
  deleteSnapshot(id: string): void;
}

/**
 * 应用留存计划：对 deleteIds 逐个调用 deleter.deleteSnapshot。
 * 调用方必须将本调用包在单个 SQLite 事务（BEGIN IMMEDIATE）内；
 * 任何 deleteSnapshot 抛错都会向上传播，事务回滚，保证零删除。
 */
export function applyRetentionPlan(deleter: RetentionDeleter, plan: RetentionPlan): void {
  for (const id of plan.deleteIds) {
    deleter.deleteSnapshot(id);
  }
}
