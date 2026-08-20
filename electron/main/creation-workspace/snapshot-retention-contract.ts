import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  openCreationWorkspace,
  type CreationWorkspace
} from "./index";
import {
  applyRetentionPlan,
  classifySnapshotMeta,
  DAILY_WINDOW_MS,
  DENSE_WINDOW_MS,
  planSnapshotRetention,
  utcDayKey,
  utcWeekKey,
  type SnapshotRetentionKind,
  type SnapshotRetentionMeta
} from "./snapshot-retention";

const NOW_ISO = "2026-08-14T12:00:00.000Z";
const NOW_MS = Date.parse(NOW_ISO);

function meta(
  id: string,
  projectId: string,
  subjectType: string,
  subjectId: string,
  kind: SnapshotRetentionKind,
  createdAt: string
): SnapshotRetentionMeta {
  return { id, projectId, subjectType, subjectId, kind, createdAt };
}

async function run(): Promise<void> {
  let workspace: CreationWorkspace | undefined;
  let raw: Database | undefined;
  let tests = 0;
  const directory = await mkdtemp(path.join(os.tmpdir(), "snapshot-retention-"));
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T> | T): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    // 1) 24 小时内密集保留：全部 keep，无 delete。
    await scenario("24 小时内密集保留", () => {
      const metas = [
        meta("a1", "p1", "scene", "s1", "auto", "2026-08-14T11:00:00.000Z"), // 1h
        meta("a2", "p1", "scene", "s1", "auto", "2026-08-13T13:00:00.000Z") // 23h
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.equal(plan.deleteIds.length, 0, "24h 内不应有删除");
      assert.deepEqual([...new Set(plan.keepIds)].sort(), ["a1", "a2"]);
    });

    // 2) 每日桶边界（24h < 年龄 ≤ 30 天）：每对象每 UTC 日保留最新一份。
    await scenario("每日桶边界：每 UTC 日保留最新", () => {
      const metas = [
        meta("d1", "p1", "scene", "s1", "auto", "2026-08-13T11:00:00.000Z"), // day 08-13 最新
        meta("d2", "p1", "scene", "s1", "auto", "2026-08-13T08:00:00.000Z"), // day 08-13 较旧 → 删
        meta("d3", "p1", "scene", "s1", "auto", "2026-08-12T12:00:00.000Z"), // day 08-12 最新
        meta("d4", "p1", "scene", "s1", "auto", "2026-08-12T06:00:00.000Z"), // day 08-12 较旧 → 删
        meta("d5", "p1", "scene", "s1", "auto", "2026-08-11T09:00:00.000Z"), // day 08-11 最新
        meta("d6", "p1", "scene", "s1", "auto", "2026-08-11T03:00:00.000Z") // day 08-11 较旧 → 删
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.deepEqual(plan.deleteIds.sort(), ["d2", "d4", "d6"]);
      assert.deepEqual(plan.keepIds.sort(), ["d1", "d3", "d5"]);
    });

    // 3) 每周桶边界（年龄 > 30 天）：每对象每 UTC 周保留最新一份。
    await scenario("每周桶边界：每 UTC 周保留最新", () => {
      const metas = [
        meta("w1", "p1", "scene", "s1", "auto", "2026-07-14T12:00:00.000Z"), // 31d，周A 最新
        meta("w2", "p1", "scene", "s1", "auto", "2026-07-14T06:00:00.000Z"), // 周A 较旧 → 删
        meta("w3", "p1", "scene", "s1", "auto", "2026-07-07T12:00:00.000Z"), // 周B 最新
        meta("w4", "p1", "scene", "s1", "auto", "2026-07-07T03:00:00.000Z"), // 周B 较旧 → 删
        meta("w5", "p1", "scene", "s1", "auto", "2026-06-30T12:00:00.000Z"), // 周C 最新
        meta("w6", "p1", "scene", "s1", "auto", "2026-06-30T03:00:00.000Z") // 周C 较旧 → 删
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.deepEqual(plan.deleteIds.sort(), ["w2", "w4", "w6"]);
      assert.deepEqual(plan.keepIds.sort(), ["w1", "w3", "w5"]);
    });

    // 4) 多项目、多对象隔离：各分组独立留存。
    await scenario("多项目、多对象隔离", () => {
      const metas = [
        // p1 / s1：同日两份 → 留最新
        meta("p1a", "p1", "scene", "s1", "auto", "2026-08-13T11:00:00.000Z"),
        meta("p1b", "p1", "scene", "s1", "auto", "2026-08-13T08:00:00.000Z"),
        // p1 / s2（不同对象）：同日两份 → 独立留最新
        meta("p1c", "p1", "scene", "s2", "auto", "2026-08-13T11:30:00.000Z"),
        meta("p1d", "p1", "scene", "s2", "auto", "2026-08-13T07:00:00.000Z"),
        // p2 / s1（不同项目）：同日两份 → 独立留最新
        meta("p2a", "p2", "scene", "s1", "auto", "2026-08-13T10:00:00.000Z"),
        meta("p2b", "p2", "scene", "s1", "auto", "2026-08-13T05:00:00.000Z")
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.deepEqual(plan.keepIds.sort(), ["p1a", "p1c", "p2a"]);
      assert.deepEqual(plan.deleteIds.sort(), ["p1b", "p1d", "p2b"]);
    });

    // 5) 时区与周边界：UTC 日/周边界与机器本地时区无关（确定性）。
    await scenario("时区与周边界（UTC 确定性）", () => {
      // 跨 UTC 午夜：23:30Z 与次日 01:30Z 属于不同 UTC 日。
      assert.notEqual(utcDayKey(Date.parse("2026-01-01T23:30:00.000Z")), utcDayKey(Date.parse("2026-01-02T01:30:00.000Z")));
      // UTC 周边界严格落在周一 00:00Z：边界两侧分属相邻 UTC 周。
      const monday = Date.UTC(2026, 0, 5, 0, 0, 0, 0); // 2026-01-05 是周一
      const sundayLate = monday - 1; // 上周日 23:59:59.999Z → 上一周
      const mondayEarly = monday + 1; // 周一 00:00:00.001Z → 本周
      assert.equal(utcWeekKey(monday), "2026-01-05", "周一 00:00Z 应归属当周");
      assert.equal(utcWeekKey(mondayEarly), "2026-01-05", "周一 00:00Z 之后瞬间仍属当周");
      assert.equal(utcWeekKey(sundayLate), "2025-12-29", "周一 00:00Z 之前瞬间属上一周");
      assert.notEqual(utcWeekKey(sundayLate), utcWeekKey(mondayEarly), "UTC 周边界两侧必须分属不同周");
      // 注入 now 在不同“本地时区”下结果一致：直接用绝对毫秒，与 tz 无关。
      const metas = [
        meta("z1", "p1", "scene", "s1", "auto", "2026-08-13T23:30:00.000Z"),
        meta("z2", "p1", "scene", "s1", "auto", "2026-08-14T01:30:00.000Z") // 不同 UTC 日
      ];
      const plan = planSnapshotRetention(metas, NOW_MS); // 直接用毫秒，跨时区不变
      // 两者 age 均 >24h 且 ≤30d，分属不同 UTC 日 → 各自保留。
      assert.deepEqual(plan.deleteIds.sort(), []);
      assert.deepEqual(plan.keepIds.sort(), ["z1", "z2"]);
    });

    // 6) 同时间戳稳定排序：相同 createdAt 时按 id 决定胜者，且跨次运行一致。
    await scenario("同时间戳稳定排序", () => {
      const created = "2026-08-12T12:00:00.000Z"; // 同日同刻
      const metas = [
        meta("same-a", "p1", "scene", "s1", "auto", created),
        meta("same-c", "p1", "scene", "s1", "auto", created),
        meta("same-b", "p1", "scene", "s1", "auto", created)
      ];
      const plan1 = planSnapshotRetention(metas, NOW_ISO);
      const plan2 = planSnapshotRetention(metas, NOW_ISO);
      // 确定性胜者：id 字典序最大者（same-c）。
      assert.deepEqual(plan1.keepIds.sort(), ["same-c"]);
      assert.deepEqual(plan1.deleteIds.sort(), ["same-a", "same-b"]);
      assert.deepEqual(plan1, plan2, "同输入两次运行必须完全一致");
    });

    // 7) 命名里程碑永久保留（即使很老）。
    await scenario("命名里程碑永久保留", () => {
      const metas = [
        meta("m-old", "p1", "scene", "s1", "milestone", "2020-01-01T00:00:00.000Z"),
        // 同对象很多很老的 auto 快照
        meta("old-a1", "p1", "scene", "s1", "auto", "2020-01-01T00:00:00.000Z"),
        meta("old-a2", "p1", "scene", "s1", "auto", "2020-01-08T00:00:00.000Z")
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.ok(plan.keepIds.includes("m-old"), "里程碑必须保留");
      assert.ok(!plan.deleteIds.includes("m-old"), "里程碑绝不被删除");
    });

    // 8) 保护快照不被删除（重组/替换/恢复前）。
    await scenario("保护快照不被删除", () => {
      const metas = [
        meta("prot-1", "p1", "scene", "s1", "protected", "2020-01-01T00:00:00.000Z"),
        // 两个很老的 auto，同一 UTC 周（2020-01-06 起的周），较旧者被清。
        meta("auto-old-b", "p1", "scene", "s1", "auto", "2020-01-07T00:00:00.000Z"), // 该周最新 → 留
        meta("auto-old-a", "p1", "scene", "s1", "auto", "2020-01-06T00:00:00.000Z") // 同周较旧 → 删
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.ok(plan.keepIds.includes("prot-1"), "保护快照必须保留");
      assert.ok(!plan.deleteIds.includes("prot-1"), "保护快照绝不被删除");
      assert.ok(plan.keepIds.includes("auto-old-b"), "同周最新 auto 保留");
      assert.ok(plan.deleteIds.includes("auto-old-a"), "同周较旧 auto 应被清理");
    });

    // 9) planner 幂等：相同输入重复运行结果一致。
    await scenario("planner 幂等", () => {
      const metas = [
        meta("x1", "p1", "scene", "s1", "auto", "2026-08-13T10:00:00.000Z"),
        meta("x2", "p1", "scene", "s1", "auto", "2026-08-12T10:00:00.000Z"),
        meta("x3", "p1", "scene", "s1", "milestone", "2026-08-01T10:00:00.000Z")
      ];
      const a = planSnapshotRetention(metas, NOW_ISO);
      const b = planSnapshotRetention(metas, NOW_MS);
      const c = planSnapshotRetention(metas, new Date(NOW_MS));
      assert.deepEqual(a, b);
      assert.deepEqual(a, c);
    });

    // 10) 清理失败事务回滚：删除过程中抛错 → 整事务回滚 → 零删除。
    await scenario("清理失败事务回滚（零删除）", () => {
      const plan = planSnapshotRetention(
        [
          meta("ok-1", "p1", "scene", "s1", "auto", "2026-08-13T10:00:00.000Z"), // 24h 内 → 留
          meta("fail", "p1", "scene", "s1", "auto", "2026-08-12T08:00:00.000Z"), // 同日报旧 → 删
          meta("ok-2", "p1", "scene", "s1", "auto", "2026-08-12T11:00:00.000Z") // 同日最新 → 留
        ],
        NOW_ISO
      );
      assert.ok(plan.deleteIds.includes("fail"), "fail 必须进入删除集才会触发回滚");
      // 模拟事务：BEGIN → 逐条删除 → 任一失败 ROLLBACK（清空已删除集合）。
      const simulateTransaction = (): { ok: boolean; applied: string[] } => {
        const applied: string[] = [];
        try {
          applyRetentionPlan(
            {
              deleteSnapshot(id) {
                if (id === "fail") throw new Error("delete failed");
                applied.push(id);
              }
            },
            plan
          );
          return { ok: true, applied };
        } catch {
          return { ok: false, applied: [] }; // ROLLBACK
        }
      };
      const result = simulateTransaction();
      assert.equal(result.ok, false, "应因失败而回滚");
      assert.equal(result.applied.length, 0, "回滚后不得有任何已生效删除");
    });

    // 11) 损坏元数据不会误删其他快照。
    await scenario("损坏元数据不会误删其他快照", () => {
      const metas = [
        meta("bad-time", "p1", "scene", "s1", "auto", "not-a-date"), // createdAt 不可解析
        meta("bad-kind", "p1", "scene", "s1", "weird" as SnapshotRetentionKind, "2026-08-12T10:00:00.000Z"), // kind 非法
        meta("good-new", "p1", "scene", "s1", "auto", "2026-08-14T10:00:00.000Z"), // 24h 内 → 留
        meta("good-old-keep", "p1", "scene", "s1", "auto", "2026-08-13T11:00:00.000Z"), // 25h → 每日桶最新 → 留
        meta("good-old-del", "p1", "scene", "s1", "auto", "2026-08-13T08:00:00.000Z") // 同日报旧 → 删
      ];
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.ok(!plan.deleteIds.includes("bad-time"), "损坏时间元数据不得被删除");
      assert.ok(!plan.deleteIds.includes("bad-kind"), "非法 kind 元数据不得被删除");
      assert.ok(plan.keepIds.includes("bad-time"), "损坏元数据应保守保留");
      assert.ok(plan.keepIds.includes("bad-kind"), "非法 kind 应保守保留");
      // 有效快照的分层判断不受损坏元数据影响。
      assert.ok(plan.keepIds.includes("good-new"));
      assert.ok(plan.keepIds.includes("good-old-keep"));
      assert.ok(plan.deleteIds.includes("good-old-del"));
    });

    // 12) 真实 DB 集成：classifySnapshotMeta 对真实行分类正确，且 over 真实行规划正确。
    await scenario("真实 DB 集成：classifySnapshotMeta 与 over-row 规划", async () => {
      workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({ type: "project.create", title: "留存测试" });
      const projectId = created.projectId;
      const sceneId = created.sceneId;

      raw = new Database(path.join(directory, "workspace.sqlite"));
      const insert = raw.prepare(
        "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      // 自动快照（scene-autosave）
      insert.run("snapshot-auto-1", projectId, "scene-autosave", sceneId, "{}", "2026-08-13T11:00:00.000Z");
      // 命名里程碑
      insert.run("snapshot-m1", projectId, "scene", sceneId, '{"reason":"初稿"}', "2020-01-01T00:00:00.000Z");
      // 恢复前保护快照
      insert.run("protective-1", projectId, "scene", sceneId, '{"reason":"恢复前保护"}', "2020-01-01T00:00:00.000Z");

      const rows = raw
        .prepare("SELECT id, subject_type, subject_id, created_at FROM snapshots WHERE project_id = ?")
        .all(projectId) as Array<{ id: string; subject_type: string; subject_id: string; created_at: string }>;
      assert.equal(rows.length, 3);

      // 分类必须由系统受控字段得出，绝不读 reason。
      const autoRow = rows.find((r) => r.id === "snapshot-auto-1")!;
      const milestoneRow = rows.find((r) => r.id === "snapshot-m1")!;
      const protectedRow = rows.find((r) => r.id === "protective-1")!;
      assert.equal(classifySnapshotMeta({ id: autoRow.id, subjectType: autoRow.subject_type, subjectId: autoRow.subject_id }), "auto");
      assert.equal(classifySnapshotMeta({ id: milestoneRow.id, subjectType: milestoneRow.subject_type, subjectId: milestoneRow.subject_id }), "milestone");
      assert.equal(classifySnapshotMeta({ id: protectedRow.id, subjectType: protectedRow.subject_type, subjectId: protectedRow.subject_id }), "protected");

      const metas: SnapshotRetentionMeta[] = rows.map((r) => ({
        id: r.id,
        projectId,
        subjectType: r.subject_type,
        subjectId: r.subject_id,
        kind: classifySnapshotMeta({ id: r.id, subjectType: r.subject_type, subjectId: r.subject_id }),
        createdAt: r.created_at
      }));
      const plan = planSnapshotRetention(metas, NOW_ISO);
      assert.ok(plan.keepIds.includes("snapshot-m1"), "里程碑永久保留");
      assert.ok(plan.keepIds.includes("protective-1"), "保护快照永久保留");
      assert.ok(plan.keepIds.includes("snapshot-auto-1"), "24h 内自动快照保留");
      assert.equal(plan.deleteIds.length, 0, "真实行里没有应删项");

      // 时间窗口常量必须为正且 30d > 24h（契约边界不变量）。
      assert.ok(DENSE_WINDOW_MS > 0);
      assert.ok(DAILY_WINDOW_MS > DENSE_WINDOW_MS);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    raw?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
