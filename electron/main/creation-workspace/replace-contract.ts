import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { randomUUID } from "node:crypto";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ReplaceApplyResult,
  type ReplacePreviewView
} from "./index";
import {
  applyReplacePlan,
  createReplacePlan,
  ReplacePlanError,
  type ReplacePlan,
  type ReplacePlanController,
  type ReplacePlanStore
} from "./replace-plan";

// ---- 新替换计划（P1-F08 / P1-P07）独立合同：自包含内存存储，不依赖公共 seam ----
function makeBody(text: string): string {
  return JSON.stringify({
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text }] }]
  });
}

function readBodyText(bodyJson: string): string {
  try {
    const doc = JSON.parse(bodyJson) as { content?: Array<{ content?: Array<{ text?: string }> }> };
    return (doc.content ?? [])
      .map((block) => (block.content ?? []).map((node) => node.text ?? "").join(""))
      .join("");
  } catch {
    return "";
  }
}

class MemoryReplacePlanStore implements ReplacePlanStore {
  private db: Database;
  private plans = new Map<string, ReplacePlan>();
  private used = new Set<string>();
  failUpdateOnce = false;

  constructor() {
    this.db = new Database(":memory:");
    this.db.exec(`
      CREATE TABLE scenes (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        chapter_id TEXT NOT NULL,
        title TEXT NOT NULL,
        body_json TEXT NOT NULL,
        revision INTEGER NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE TABLE snapshots (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        subject_id TEXT NOT NULL,
        kind TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        created_at TEXT NOT NULL
      );
      CREATE TABLE change_log (
        sequence INTEGER PRIMARY KEY AUTOINCREMENT,
        project_id TEXT NOT NULL,
        command_type TEXT NOT NULL,
        changes_json TEXT NOT NULL,
        committed_at TEXT NOT NULL
      );
    `);
  }

  insertScene(projectId: string, chapterId: string, title: string, bodyJson: string, revision = 1): string {
    const id = randomUUID();
    this.db
      .prepare("INSERT INTO scenes (id, project_id, chapter_id, title, body_json, revision, updated_at) VALUES (?,?,?,?,?,?,?)")
      .run(id, projectId, chapterId, title, bodyJson, revision, new Date().toISOString());
    return id;
  }

  countSnapshots(): number {
    return (this.db.prepare("SELECT COUNT(*) AS c FROM snapshots").get() as { c: number }).c;
  }

  countChangeLog(): number {
    return (this.db.prepare("SELECT COUNT(*) AS c FROM change_log").get() as { c: number }).c;
  }

  snapshotRows(): Array<{ payload_json: string }> {
    return this.db.prepare("SELECT payload_json FROM snapshots").all() as Array<{ payload_json: string }>;
  }

  changeLogRows(): Array<{ changes_json: string }> {
    return this.db.prepare("SELECT changes_json FROM change_log").all() as Array<{ changes_json: string }>;
  }

  listScopeScenes(projectId: string, scope: "all" | "chapter" | "scene", scopeId?: string): Array<{
    id: string;
    projectId: string;
    chapterId: string;
    chapterTitle: string;
    title: string;
    bodyJson: string;
    revision: number;
    updatedAt: string;
  }> {
    const sql =
      scope === "scene"
        ? "SELECT * FROM scenes WHERE project_id=? AND id=?"
        : scope === "chapter"
          ? "SELECT * FROM scenes WHERE project_id=? AND chapter_id=?"
          : "SELECT * FROM scenes WHERE project_id=?";
    const args = scope === "all" ? [projectId] : [projectId, scopeId ?? ""];
    const rows = this.db.prepare(sql).all(...args) as Array<{
      id: string;
      project_id: string;
      chapter_id: string;
      title: string;
      body_json: string;
      revision: number;
      updated_at: string;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      chapterId: row.chapter_id,
      chapterTitle: "章节",
      title: row.title,
      bodyJson: row.body_json,
      revision: row.revision,
      updatedAt: row.updated_at
    }));
  }

  getScene(id: string): { bodyJson: string; revision: number; updatedAt: string } | undefined {
    const row = this.db.prepare("SELECT body_json, revision, updated_at FROM scenes WHERE id=?").get(id) as
      | { body_json: string; revision: number; updated_at: string }
      | undefined;
    return row ? { bodyJson: row.body_json, revision: row.revision, updatedAt: row.updated_at } : undefined;
  }

  savePlan(plan: ReplacePlan): void {
    this.plans.set(plan.planId, plan);
  }

  loadPlan(planId: string): ReplacePlan | undefined {
    return this.plans.get(planId);
  }

  deletePlan(planId: string): void {
    this.plans.delete(planId);
  }

  isPlanUsed(planId: string): boolean {
    return this.used.has(planId);
  }

  markPlanUsed(planId: string): void {
    this.used.add(planId);
  }

  beginTransaction(): void {
    this.db.exec("BEGIN");
  }

  commitTransaction(): void {
    this.db.exec("COMMIT");
  }

  rollbackTransaction(): void {
    this.db.exec("ROLLBACK");
  }

  updateSceneBody(id: string, bodyJson: string, newRevision: number, timestamp: string): void {
    if (this.failUpdateOnce) {
      this.failUpdateOnce = false;
      throw new Error("模拟事务写入失败");
    }
    this.db
      .prepare("UPDATE scenes SET body_json=?, revision=?, updated_at=? WHERE id=?")
      .run(bodyJson, newRevision, timestamp, id);
  }

  insertSnapshot(id: string, projectId: string, subjectId: string, payloadJson: string, createdAt: string): void {
    this.db
      .prepare("INSERT INTO snapshots (id, project_id, subject_id, kind, payload_json, created_at) VALUES (?,?,?,?,?,?)")
      .run(id, projectId, subjectId, "history", payloadJson, createdAt);
  }

  insertChangeLog(projectId: string, commandType: string, changesJson: string, committedAt: string): number {
    const info = this.db
      .prepare("INSERT INTO change_log (project_id, command_type, changes_json, committed_at) VALUES (?,?,?,?)")
      .run(projectId, commandType, changesJson, committedAt);
    return Number(info.lastInsertRowid);
  }
}

async function runReplacePlanTests(
  scenario: <T>(name: string, fn: () => Promise<T>) => Promise<T>
): Promise<void> {
  let store: MemoryReplacePlanStore;
  const projectId = "proj-plan";
  const chapterId = "chap-plan";

  await scenario("计划预览：同场景多命中拥有稳定 hitId 与 before/after", async () => {
    store = new MemoryReplacePlanStore();
    const sceneId = store.insertScene(projectId, chapterId, "A", makeBody("黄沙镇的风，黄沙镇的雨。"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "黄沙镇",
      replaceWith: "雾都城",
      mode: "plain"
    });
    assert.equal(plan.hits.length, 2);
    assert.equal(plan.scenes.length, 1);
    assert.equal(plan.scenes[0]!.hitCount, 2);
    assert.equal(plan.hits[0]!.before, "黄沙镇");
    assert.equal(plan.hits[0]!.after, "雾都城");
    assert.ok(plan.hits[0]!.hitId && plan.hits[0]!.hitId !== plan.hits[1]!.hitId);
    // 封存 revision / 哈希
    assert.equal(plan.seals[sceneId]?.revision, 1);
    assert.ok(plan.seals[sceneId]?.hash);
    return plan;
  });

  await scenario("逐命中排除：同场景多命中只排除其中一个", async () => {
    store = new MemoryReplacePlanStore();
    const sceneId = store.insertScene(projectId, chapterId, "B", makeBody("黄沙镇的风，黄沙镇的雨。"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "黄沙镇",
      replaceWith: "雾都城",
      mode: "plain"
    });
    const exclude = plan.hits.find((h) => h.sceneId === sceneId)!;
    const outcome = await applyReplacePlan(store, plan.planId, [exclude.hitId]);
    assert.equal(outcome.appliedHitCount, 1);
    assert.equal(outcome.modifiedSceneIds.length, 1);
    assert.equal(outcome.snapshotIds.length, 1);
    const after = readBodyText(store.getScene(sceneId)!.bodyJson);
    assert.equal(after.includes("雾都城"), true);
    assert.equal(after.includes("黄沙镇"), true); // 被排除的那一处仍在
    assert.equal(after.split("雾都城").length - 1, 1);
  });

  await scenario("跨场景逐命中排除", async () => {
    store = new MemoryReplacePlanStore();
    const a = store.insertScene(projectId, chapterId, "C", makeBody("苹果和苹果"));
    const b = store.insertScene(projectId, chapterId, "D", makeBody("苹果和香蕉"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "苹果",
      replaceWith: "橘子",
      mode: "plain"
    });
    const excludeA = plan.hits.filter((h) => h.sceneId === a)[1]!.hitId; // 排除 A 中的第二处
    const excludeB = plan.hits.filter((h) => h.sceneId === b)[0]!.hitId; // 排除 B 的全部
    const outcome = await applyReplacePlan(store, plan.planId, [excludeA, excludeB]);
    assert.equal(outcome.appliedHitCount, 1);
    const afterA = readBodyText(store.getScene(a)!.bodyJson);
    const afterB = readBodyText(store.getScene(b)!.bodyJson);
    assert.equal(afterA.includes("橘子"), true);
    assert.equal(afterA.includes("苹果"), true); // A 仅一处被替换
    assert.equal(afterB.includes("橘子"), false); // B 整体排除
    assert.equal(afterB, "苹果和香蕉");
  });

  await scenario("普通文本与受限正则均生效", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "E", makeBody("第1章 第2章"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "第(\\d)章",
      replaceWith: "卷$1章",
      mode: "regex"
    });
    const hit = plan.hits.find((h) => h.sceneId === s)!;
    assert.equal(hit.before, "第1章");
    assert.equal(hit.after, "卷1章"); // 捕获组替换
    const outcome = await applyReplacePlan(store, plan.planId, []);
    assert.equal(outcome.appliedHitCount, 2);
    const after = readBodyText(store.getScene(s)!.bodyJson);
    assert.equal(after.includes("卷1章"), true);
    assert.equal(after.includes("卷2章"), true);
  });

  await scenario("受限正则：环视/后向引用在预览即被拒", async () => {
    store = new MemoryReplacePlanStore();
    let lookahead: unknown;
    try {
      await createReplacePlan(store, { projectId, scope: "all", find: "(?=x)", replaceWith: "y", mode: "regex" });
    } catch (error) {
      lookahead = error;
    }
    assert.equal((lookahead as ReplacePlanError).code, "invalid-input");
    let backref: unknown;
    try {
      await createReplacePlan(store, { projectId, scope: "all", find: "(a)\\1", replaceWith: "y", mode: "regex" });
    } catch (error) {
      backref = error;
    }
    assert.equal((backref as ReplacePlanError).code, "invalid-input");
  });

  await scenario("stale：正文/revision 变化后拒绝应用且不静默重搜", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "F", makeBody("旧词旧词"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "旧词",
      replaceWith: "新词",
      mode: "plain"
    });
    const before = store.countSnapshots();
    // 模拟预览后外部修改了正文并提升 revision
    store.updateSceneBody(s, makeBody("已改动的内容"), 2, new Date().toISOString());
    let stale: unknown;
    try {
      await applyReplacePlan(store, plan.planId, []);
    } catch (error) {
      stale = error;
    }
    assert.equal((stale as ReplacePlanError).code, "stale");
    assert.equal(store.countSnapshots(), before); // 未写任何快照
  });

  await scenario("重叠命中：稳定拒绝，不重复替换", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "G", makeBody("啊啊啊"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "啊",
      replaceWith: "哇",
      mode: "plain"
    });
    // 手工注入一处与首命中重叠的命中
    const base = plan.hits[0]!;
    const overlapping: typeof base = {
      ...base,
      hitId: "overlap-hit",
      range: { start: base.range.start, end: base.range.start + 1 }
    };
    const badPlan: ReplacePlan = { ...plan, hits: [...plan.hits, overlapping] };
    store.savePlan(badPlan);
    let overlap: unknown;
    try {
      await applyReplacePlan(store, badPlan.planId, []);
    } catch (error) {
      overlap = error;
    }
    assert.equal((overlap as ReplacePlanError).code, "overlap");
  });

  await scenario("planId 伪造/重复/过期均被拒", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "H", makeBody("重复重复"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "重复",
      replaceWith: "唯一",
      mode: "plain"
    });
    // 伪造
    let fake: unknown;
    try {
      await applyReplacePlan(store, "not-a-real-plan", []);
    } catch (error) {
      fake = error;
    }
    assert.equal((fake as ReplacePlanError).code, "plan-forbidden");
    // 成功一次
    const ok = await applyReplacePlan(store, plan.planId, []);
    assert.equal(ok.appliedHitCount, 2);
    // 重复
    let dup: unknown;
    try {
      await applyReplacePlan(store, plan.planId, []);
    } catch (error) {
      dup = error;
    }
    assert.equal((dup as ReplacePlanError).code, "plan-used");
    // 过期
    const expiring = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "重复",
      replaceWith: "唯一",
      mode: "plain"
    });
    const loaded = store.loadPlan(expiring.planId)!;
    loaded.expiresAt = new Date(Date.now() - 1000).toISOString();
    store.savePlan(loaded);
    let expired: unknown;
    try {
      await applyReplacePlan(store, expiring.planId, []);
    } catch (error) {
      expired = error;
    }
    assert.equal((expired as ReplacePlanError).code, "plan-expired");
    void s;
  });

  await scenario("preview 中途取消：不留计划/快照/正文修改", async () => {
    store = new MemoryReplacePlanStore();
    store.insertScene(projectId, chapterId, "I1", makeBody("取消取消取消"));
    store.insertScene(projectId, chapterId, "I2", makeBody("取消取消取消"));
    let calls = 0;
    const controller: ReplacePlanController = {
      isCancellationRequested: () => calls >= 2,
      throwIfCancelled: () => {
        calls += 1;
        if (calls >= 2) throw new ReplacePlanError("cancelled", "已取消");
      },
      reportProgress: () => {}
    };
    let cancelled: unknown;
    try {
      await createReplacePlan(
        store,
        { projectId, scope: "all", find: "取消", replaceWith: "继续", mode: "plain" },
        controller
      );
    } catch (error) {
      cancelled = error;
    }
    assert.equal((cancelled as ReplacePlanError).code, "cancelled");
    assert.equal(store.countSnapshots(), 0);
  });

  await scenario("apply 前取消：从不调用 apply 即无写入", async () => {
    store = new MemoryReplacePlanStore();
    store.insertScene(projectId, chapterId, "J", makeBody("未应用未应用"));
    assert.equal(store.countSnapshots(), 0); // 未经 apply 则不写快照
  });

  await scenario("apply 事务失败：整体回滚，零写入", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "K", makeBody("失败失败"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "失败",
      replaceWith: "成功",
      mode: "plain"
    });
    store.failUpdateOnce = true;
    let failed: unknown;
    try {
      await applyReplacePlan(store, plan.planId, []);
    } catch (error) {
      failed = error;
    }
    assert.equal((failed as ReplacePlanError).code, "transaction-failed");
    assert.equal(store.countSnapshots(), 0); // 回滚后无快照
    assert.equal(readBodyText(store.getScene(s)!.bodyJson), "失败失败"); // 正文未变
  });

  await scenario("保护快照与 change_log 同事务一致", async () => {
    store = new MemoryReplacePlanStore();
    const s = store.insertScene(projectId, chapterId, "L", makeBody("保护保护保护"));
    const plan = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "保护",
      replaceWith: "守护",
      mode: "plain"
    });
    const outcome = await applyReplacePlan(store, plan.planId, []);
    assert.equal(store.countSnapshots(), outcome.modifiedSceneIds.length);
    assert.equal(store.countChangeLog(), outcome.modifiedSceneIds.length);
    const snap = JSON.parse(store.snapshotRows()[0]!.payload_json) as {
      kind: string;
      planId: string;
      sceneId: string;
      before: string;
    };
    assert.equal(snap.kind, "replace.plan");
    assert.equal(snap.planId, plan.planId);
    assert.equal(readBodyText(snap.before), "保护保护保护");
    const log = JSON.parse(store.changeLogRows()[0]!.changes_json) as { planId: string; sceneId: string };
    assert.equal(log.planId, plan.planId);
    assert.equal(log.sceneId, s);
  });

  await scenario("大项目预览：跨多场景上报进度且不阻塞（渲染端据此更新）", async () => {
    store = new MemoryReplacePlanStore();
    for (let i = 0; i < 300; i++) {
      store.insertScene(projectId, chapterId, `M${i}`, makeBody("风风风"));
    }
    let reports = 0;
    let maxCompleted = 0;
    const controller: ReplacePlanController = {
      isCancellationRequested: () => false,
      throwIfCancelled: () => {},
      reportProgress: (p) => {
        reports += 1;
        maxCompleted = Math.max(maxCompleted, p.completedScenes);
      }
    };
    const plan = await createReplacePlan(
      store,
      { projectId, scope: "all", find: "风", replaceWith: "雨", mode: "plain", limit: 1000 },
      controller
    );
    assert.equal(plan.truncated, false); // 高上限下不截断
    assert.equal(plan.totalHits, 900); // 300 场景 × 3 命中
    assert.equal(maxCompleted, 300); // 进度覆盖了全部场景
    assert.ok(reports > 300); // 每场景一次 + 初始 + 收尾
    // 截断行为：默认上限下超出即截断，且不再静默扫描剩余场景
    const limited = await createReplacePlan(store, {
      projectId,
      scope: "all",
      find: "风",
      replaceWith: "雨",
      mode: "plain"
    });
    assert.equal(limited.truncated, true);
    assert.equal(limited.totalHits, 200);
  });
}

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-replace-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
  let chapterId = "";
  let sceneA = "";
  let sceneB = "";
  let volumeId = "";
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });
    const report = await workspace.check();
    assert.equal(report.ok, true);

    await scenario("准备项目：两个场景的正文含重复词", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "替换测试项目" });
      projectId = created.projectId;
      chapterId = created.chapterId;
      volumeId = created.volumeId;
      sceneA = created.sceneId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneA,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: "黄沙镇的风吹过黄沙镇的街。", marks: [{ type: "bold" }] }]
            },
            { type: "paragraph", content: [{ type: "text", text: "油灯还亮着。" }] },
            { type: "paragraph", content: [{ type: "text", text: "第一章的结尾。" }] }
          ]
        }
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId,
        title: "第二场景"
      }) as { entityId: string };
      sceneB = extra.entityId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId: sceneB,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [{ type: "paragraph", content: [{ type: "text", text: "黄沙镇不在了。" }] }]
        }
      });
    });

    await scenario("预览：全项目命中统计与上下文片段", async () => {
      const view = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplacePreviewView;
      assert.equal(view.totalHits, 3);
      assert.equal(view.matchedScenes, 2);
      assert.equal(view.sceneHits.length, 2);
      const hitA = view.sceneHits.find((hit) => hit.sceneId === sceneA)!;
      assert.equal(hitA.count, 2);
      assert.equal(hitA.snippets.length, 2);
      assert.equal(hitA.snippets[0]!.includes("黄沙镇"), true);
      const hitB = view.sceneHits.find((hit) => hit.sceneId === sceneB)!;
      assert.equal(hitB.count, 1);
    });

    await scenario("预览：范围限定到单场景", async () => {
      const view = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "scene",
        scopeId: sceneA
      })) as ReplacePreviewView;
      assert.equal(view.totalHits, 2);
      assert.equal(view.matchedScenes, 1);
      assert.equal(view.sceneHits[0]!.sceneId, sceneA);
    });

    await scenario("预览：范围限定到章节/卷且校验归属", async () => {
      const chapterView = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "chapter",
        scopeId: chapterId
      })) as ReplacePreviewView;
      assert.equal(chapterView.totalHits, 3);
      const volumeView = (await workspace!.read({
        kind: "replace.preview",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "volume",
        scopeId: volumeId
      })) as ReplacePreviewView;
      assert.equal(volumeView.totalHits, 3);
      let foreignError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "黄沙镇",
          replaceWith: "雾都城",
          scope: "chapter",
          scopeId: "chapter-不存在"
        });
      } catch (error) {
        foreignError = error;
      }
      assert.equal((foreignError as CreationWorkspaceError).code, "not-found");
    });

    await scenario("执行替换：命中场景被修改并自动建保护快照", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "黄沙镇",
        replaceWith: "雾都城",
        scope: "project"
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 2);
      assert.equal(result.appliedHits, 3);
      assert.equal(result.skippedScenes, 0);
      assert.equal(result.snapshotIds.length, 2);
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(bodyA?.body).includes("雾都城的风吹过雾都城的街"), true);
      const bodyB = await workspace!.read({ kind: "scene.body", sceneId: sceneB });
      assert.equal(JSON.stringify(bodyB?.body).includes("雾都城不在了"), true);
      const snapshots = (await workspace!.read({
        kind: "snapshot.list",
        projectId,
        subjectType: "scene",
        subjectId: sceneA
      })) as Array<{ reason: string }>;
      assert.equal(snapshots.some((item) => item.reason === "查找替换自动备份"), true);
    });

    await scenario("执行替换：排除的场景不被修改", async () => {
      const before = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "雾都城",
        replaceWith: "旧月城",
        scope: "project",
        excludeSceneIds: [sceneA]
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 1);
      assert.equal(result.appliedHits, 1);
      assert.equal(result.skippedScenes, 1);
      const after = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(after?.body), JSON.stringify(before?.body));
      const bodyB = await workspace!.read({ kind: "scene.body", sceneId: sceneB });
      assert.equal(JSON.stringify(bodyB?.body).includes("旧月城不在了"), true);
    });

    await scenario("执行替换：无命中时不写快照且返回零", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "不存在的词",
        replaceWith: "x",
        scope: "project"
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 0);
      assert.equal(result.appliedHits, 0);
      assert.equal(result.snapshotIds.length, 0);
    });

    await scenario("受限正则：基本替换与 $1 捕获组引用", async () => {
      const result = (await workspace!.transact({
        type: "replace.apply",
        projectId,
        find: "第(.)章",
        replaceWith: "第七$1章",
        scope: "scene",
        scopeId: sceneA,
        regex: true
      })) as ReplaceApplyResult;
      assert.equal(result.appliedScenes, 1);
      assert.equal(result.appliedHits, 1);
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      assert.equal(JSON.stringify(bodyA?.body).includes("第七一章的结尾"), true);
    });

    await scenario("受限正则：拒绝环视与后向引用", async () => {
      let lookaheadError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(?=黄沙镇)",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        lookaheadError = error;
      }
      assert.equal((lookaheadError as CreationWorkspaceError).code, "invalid-input");
      let backrefError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(黄)\\1",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        backrefError = error;
      }
      assert.equal((backrefError as CreationWorkspaceError).code, "invalid-input");
      let invalidError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "(未闭合",
          replaceWith: "x",
          scope: "project",
          regex: true
        });
      } catch (error) {
        invalidError = error;
      }
      assert.equal((invalidError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("空查找文本与非法范围报 invalid-input", async () => {
      let emptyError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "  ",
          replaceWith: "x",
          scope: "project"
        });
      } catch (error) {
        emptyError = error;
      }
      assert.equal((emptyError as CreationWorkspaceError).code, "invalid-input");
      let scopeError: unknown;
      try {
        await workspace!.read({
          kind: "replace.preview",
          projectId,
          find: "黄沙镇",
          replaceWith: "x",
          scope: "shelf" as never
        });
      } catch (error) {
        scopeError = error;
      }
      assert.equal((scopeError as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("正文结构保持：粗体标记与场景分隔不受替换影响", async () => {
      const bodyA = await workspace!.read({ kind: "scene.body", sceneId: sceneA });
      const text = JSON.stringify(bodyA?.body);
      assert.equal(text.includes('"type":"bold"'), true);
    });

    await runReplacePlanTests(scenario);

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
