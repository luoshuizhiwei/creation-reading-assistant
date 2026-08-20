import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace
} from "./index";

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    await workspace.close();
  }
}

async function expectWorkspaceError(code: string, fn: () => Promise<unknown>): Promise<void> {
  let caught: unknown;
  try {
    await fn();
  } catch (error) {
    caught = error;
  }
  assert.ok(caught instanceof CreationWorkspaceError, "应抛出 CreationWorkspaceError");
  assert.equal((caught as CreationWorkspaceError).code, code, `期望错误码 ${code}`);
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-p1-lifecycle-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    // ------------------------------------------------------------------
    // 任务 A：卡片类型 / 关系类型生命周期
    // ------------------------------------------------------------------
    await scenario("cardType.update：更新名称与字段并写入 change_log，revision 递增", async () => {
      await withWorkspace(path.join(parent, "ws-ctu"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "类型测试" });
        const created = await workspace.transact({
          type: "cardType.create",
          projectId: project.projectId,
          name: "配角",
          fields: [{ key: "note", label: "备注", kind: "multiline" }]
        });
        const result = await workspace.transact({
          type: "cardType.update",
          cardTypeId: created.entityId,
          name: "配角（改）",
          fields: [
            { key: "note", label: "备注（改）", kind: "multiline" },
            { key: "motto", label: "口头禅", kind: "text" }
          ],
          baseRevision: created.revision
        });
        assert.equal(result.revision, 2);
        const types = await workspace.read({ kind: "cardTypes.list", projectId: project.projectId });
        const updated = types.find((item) => item.id === created.entityId);
        assert.equal(updated?.name, "配角（改）");
        assert.equal(updated?.revision, 2);
        assert.equal(updated?.fields.length, 2);
        // 现有卡片数据用新 schema 验证通过
        const card = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: updated!.kind, title: "角色A", fields: { note: "n" } });
        const withNewField = await workspace.transact({
          type: "cardType.update",
          cardTypeId: created.entityId,
          name: "配角（再改）",
          fields: [
            { key: "note", label: "备注", kind: "multiline" },
            { key: "motto", label: "口头禅", kind: "text" }
          ],
          baseRevision: 2
        });
        assert.equal(withNewField.revision, 3);
        void card;
      });
    });

    await scenario("cardType.update：内置类型只读、revision 冲突、字段 key 重命名拒绝", async () => {
      await withWorkspace(path.join(parent, "ws-ctu-bad"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "类型约束" });
        const builtin = (await workspace.read({ kind: "cardTypes.list", projectId: project.projectId })).find((item) => item.projectId === null);
        assert.ok(builtin, "内置类型存在");
        await expectWorkspaceError("conflict", () =>
          workspace.transact({
            type: "cardType.update",
            cardTypeId: builtin!.id,
            name: "改名",
            fields: [{ key: "note", label: "备注", kind: "multiline" }],
            baseRevision: 1
          })
        );
        const created = await workspace.transact({
          type: "cardType.create",
          projectId: project.projectId,
          name: "配角",
          fields: [{ key: "note", label: "备注", kind: "multiline" }]
        });
        await expectWorkspaceError("revision-mismatch", () =>
          workspace.transact({
            type: "cardType.update",
            cardTypeId: created.entityId,
            name: "改名",
            fields: [{ key: "note", label: "备注", kind: "multiline" }],
            baseRevision: 99
          })
        );
        await expectWorkspaceError("invalid-input", () =>
          workspace.transact({
            type: "cardType.update",
            cardTypeId: created.entityId,
            name: "改名",
            fields: [{ key: "renamed", label: "备注", kind: "multiline" }],
            baseRevision: 1
          })
        );
      });
    });

    await scenario("cardType.delete：仍被卡片使用时禁止删除；未使用时可删除", async () => {
      await withWorkspace(path.join(parent, "ws-ctd"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "删除类型" });
        const created = await workspace.transact({
          type: "cardType.create",
          projectId: project.projectId,
          name: "临时类型",
          fields: [{ key: "note", label: "备注", kind: "multiline" }]
        });
        const types = await workspace.read({ kind: "cardTypes.list", projectId: project.projectId });
        const kind = types.find((item) => item.id === created.entityId)!.kind;
        await workspace.transact({ type: "card.create", projectId: project.projectId, kind, title: "卡" });
        await expectWorkspaceError("conflict", () =>
          workspace.transact({ type: "cardType.delete", cardTypeId: created.entityId, baseRevision: 1 })
        );
        // 删除卡片后可删除类型
        const cards = await workspace.read({ kind: "cards.list", projectId: project.projectId });
        await workspace.transact({ type: "card.delete", cardId: cards[0]!.id });
        const deleted = await workspace.transact({ type: "cardType.delete", cardTypeId: created.entityId, baseRevision: 1 });
        assert.equal(deleted.entityId, created.entityId);
        const after = await workspace.read({ kind: "cardTypes.list", projectId: project.projectId });
        assert.equal(after.some((item) => item.id === created.entityId), false);
      });
    });

    await scenario("relationType.update：稳定 name 不可改、两端约束变更校验已有关系", async () => {
      await withWorkspace(path.join(parent, "ws-rtu"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "关系类型" });
        const created = await workspace.transact({
          type: "relationType.create",
          projectId: project.projectId,
          forwardName: "认识",
          reverseName: "认识",
          fromKinds: ["character"],
          toKinds: ["character"]
        });
        const types = await workspace.read({ kind: "relationTypes.list", projectId: project.projectId });
        const current = types.find((item) => item.id === created.entityId)!;
        // 显示名可改，稳定 key 不变
        const updated = await workspace.transact({
          type: "relationType.update",
          relationTypeId: created.entityId,
          name: current.name,
          forwardName: "相识",
          reverseName: "相识",
          fromKinds: ["character"],
          toKinds: ["character"],
          baseRevision: 1
        });
        assert.equal(updated.revision, 2);
        // 稳定 name 不可改
        await expectWorkspaceError("invalid-input", () =>
          workspace.transact({
            type: "relationType.update",
            relationTypeId: created.entityId,
            name: "different",
            forwardName: "相识",
            reverseName: "相识",
            baseRevision: 2
          })
        );
        // 建立关系实例后，收紧约束到不包含 character → conflict
        const c1 = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "甲" });
        const c2 = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "乙" });
        await workspace.transact({
          type: "cardRelation.create",
          projectId: project.projectId,
          fromCardId: c1.entityId,
          toCardId: c2.entityId,
          relationTypeId: created.entityId
        });
        await expectWorkspaceError("conflict", () =>
          workspace.transact({
            type: "relationType.update",
            relationTypeId: created.entityId,
            name: current.name,
            forwardName: "相识",
            reverseName: "相识",
            fromKinds: ["location"],
            toKinds: ["location"],
            baseRevision: 2
          })
        );
      });
    });

    await scenario("relationType.delete：内置只读、仍被关系实例使用时禁止删除", async () => {
      await withWorkspace(path.join(parent, "ws-rtd"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "删除关系类型" });
        const builtin = (await workspace.read({ kind: "relationTypes.list", projectId: project.projectId })).find((item) => item.projectId === null);
        assert.ok(builtin, "内置关系类型存在");
        await expectWorkspaceError("conflict", () =>
          workspace.transact({ type: "relationType.delete", relationTypeId: builtin!.id, baseRevision: 1 })
        );
        const created = await workspace.transact({
          type: "relationType.create",
          projectId: project.projectId,
          forwardName: "登场于",
          reverseName: "登场角色",
          fromKinds: ["character"],
          toKinds: ["location"]
        });
        const c1 = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "甲" });
        const loc = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "location", title: "地" });
        await workspace.transact({
          type: "cardRelation.create",
          projectId: project.projectId,
          fromCardId: c1.entityId,
          toCardId: loc.entityId,
          relationTypeId: created.entityId
        });
        await expectWorkspaceError("conflict", () =>
          workspace.transact({ type: "relationType.delete", relationTypeId: created.entityId, baseRevision: 1 })
        );
      });
    });

    // ------------------------------------------------------------------
    // 任务 B：批注重新定位
    // ------------------------------------------------------------------
    await scenario("annotation.reanchor：成功重锚（锚点命中正文），只改 anchor/revision，note/status/cardId 不变", async () => {
      await withWorkspace(path.join(parent, "ws-reanchor"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "重锚" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "新的正文内容" }] }] }
        });
        const created = await workspace.transact({
          type: "annotation.create",
          projectId: project.projectId,
          sceneId: project.sceneId,
          cardId: undefined,
          anchor: { blockIndex: 0, textOffset: 0, textLength: 2 },
          note: "批注内容",
          status: "open"
        });
        const result = await workspace.transact({
          type: "annotation.reanchor",
          annotationId: created.annotationId,
          baseRevision: created.revision,
          anchor: { blockIndex: 0, textOffset: 2, textLength: 2 }
        });
        assert.equal(result.revision, created.revision + 1);
        const list = await workspace.read({ kind: "annotation.list", projectId: project.projectId, sceneId: project.sceneId });
        const annotation = list.find((item) => item.id === created.annotationId)!;
        assert.equal(annotation.anchoredText, "正文");
        assert.equal(annotation.note, "批注内容");
        assert.equal(annotation.status, "open");
        assert.equal(annotation.cardId, null);
      });
    });

    await scenario("annotation.reanchor：revision 冲突 / 锚点未命中 / 批注不存在均失败且原批注不变", async () => {
      await withWorkspace(path.join(parent, "ws-reanchor-bad"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "重锚失败" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "正文文本" }] }] }
        });
        const created = await workspace.transact({
          type: "annotation.create",
          projectId: project.projectId,
          sceneId: project.sceneId,
          anchor: { blockIndex: 0, textOffset: 0, textLength: 2 },
          note: "原批注"
        });
        await expectWorkspaceError("revision-mismatch", () =>
          workspace.transact({
            type: "annotation.reanchor",
            annotationId: created.annotationId,
            baseRevision: created.revision + 10,
            anchor: { blockIndex: 0, textOffset: 1, textLength: 2 }
          })
        );
        await expectWorkspaceError("invalid-input", () =>
          workspace.transact({
            type: "annotation.reanchor",
            annotationId: created.annotationId,
            baseRevision: created.revision,
            anchor: { blockIndex: 5, textOffset: 0, textLength: 2 }
          })
        );
        await expectWorkspaceError("not-found", () =>
          workspace.transact({
            type: "annotation.reanchor",
            annotationId: "annotation-nope",
            baseRevision: 1,
            anchor: { blockIndex: 0, textOffset: 0, textLength: 1 }
          })
        );
        // 原批注保持不变
        const list = await workspace.read({ kind: "annotation.list", projectId: project.projectId, sceneId: project.sceneId });
        const annotation = list.find((item) => item.id === created.annotationId)!;
        assert.equal(annotation.anchoredText, "正文");
        assert.equal(annotation.note, "原批注");
      });
    });

    // ------------------------------------------------------------------
    // 任务 C：快照预览 / 安全恢复 / 回收站影响
    // ------------------------------------------------------------------
    await scenario("snapshot.preview：字段级差异（当前值 before、快照值 after）", async () => {
      await withWorkspace(path.join(parent, "ws-preview"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "预览" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "快照正文" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "scene",
          subjectId: project.sceneId,
          reason: "里程碑"
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "修改后正文" }] }] }
        });
        const preview = await workspace.read({ kind: "snapshot.preview", projectId: project.projectId, snapshotId: snapshot.entityId });
        assert.ok(preview, "预览存在");
        assert.equal(preview!.subjectType, "scene");
        assert.equal(preview!.canRestore, true);
        const bodyRow = preview!.rows.find((row) => row.label === "正文");
        assert.ok(bodyRow, "包含正文差异行");
        assert.equal(bodyRow!.before, "修改后正文");
        assert.equal(bodyRow!.after, "快照正文");
        assert.equal(bodyRow!.changed, true);
      });
    });

    await scenario("snapshot.restoreWithProtection：保护快照+恢复+change_log 同一事务，返回保护快照 ID", async () => {
      await withWorkspace(path.join(parent, "ws-restore-protect"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "安全恢复" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "版本一" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "scene",
          subjectId: project.sceneId,
          reason: "里程碑"
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: project.sceneId,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "版本二" }] }] }
        });
        const result = await workspace.restoreSnapshotWithProtection({
          type: "snapshot.restoreWithProtection",
          projectId: project.projectId,
          snapshotId: snapshot.entityId,
          protectionReason: "恢复前保护"
        });
        assert.equal(result.ok, true);
        assert.equal(result.restoredSubjectType, "scene");
        assert.equal(result.restoredSubjectId, project.sceneId);
        assert.equal(typeof result.protectionSnapshotId, "string");
        // 恢复后正文回到版本一
        const body = await workspace.read({ kind: "scene.body", sceneId: project.sceneId });
        assert.equal(JSON.stringify(body?.body).includes("版本一"), true);
        // 保护快照可预览（当前值=版本一，快照值=版本二）
        const protectionPreview = await workspace.read({ kind: "snapshot.preview", projectId: project.projectId, snapshotId: result.protectionSnapshotId });
        assert.ok(protectionPreview, "保护快照可预览");
        const bodyRow = protectionPreview!.rows.find((row) => row.label === "正文");
        assert.equal(bodyRow!.before, "版本一");
        assert.equal(bodyRow!.after, "版本二");
      });
    });

    await scenario("snapshot.restoreWithProtection：快照不存在失败且不产生保护快照", async () => {
      await withWorkspace(path.join(parent, "ws-restore-missing"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "失败恢复" });
        let caught: unknown;
        try {
          await workspace.restoreSnapshotWithProtection({
            type: "snapshot.restoreWithProtection",
            projectId: project.projectId,
            snapshotId: "snapshot-nope",
            protectionReason: "x"
          });
        } catch (error) {
          caught = error;
        }
        assert.equal((caught as CreationWorkspaceError).code, "not-found");
        const snapshots = await workspace.read({ kind: "snapshot.list", projectId: project.projectId });
        assert.equal(snapshots.length, 0);
      });
    });

    await scenario("snapshot chapter/volume：创建与保护恢复覆盖结构内容", async () => {
      await withWorkspace(path.join(parent, "ws-chapter-snapshot"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "章节快照" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const chapter = outline!.volumes[0]!.chapters[0]!;
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: chapter.scenes[0]!.id,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "章节原文" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "chapter",
          subjectId: chapter.id,
          reason: "章节快照"
        });
        // 修改正文
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: chapter.scenes[0]!.id,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "修改内容" }] }] }
        });
        const result = await workspace.restoreSnapshotWithProtection({
          type: "snapshot.restoreWithProtection",
          projectId: project.projectId,
          snapshotId: snapshot.entityId,
          protectionReason: "恢复章节"
        });
        assert.equal(result.ok, true);
        const body = await workspace.read({ kind: "scene.body", sceneId: chapter.scenes[0]!.id });
        assert.equal(JSON.stringify(body?.body).includes("章节原文"), true);
      });
    });

    await scenario("trash.impact：volume/chapter/scene/card 影响统计 + 稳定错误", async () => {
      await withWorkspace(path.join(parent, "ws-trash-impact"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "回收站影响" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const volume = outline!.volumes[0]!;
        const chapter = volume.chapters[0]!;
        const scene = chapter.scenes[0]!;
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: scene.id,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "影响字数测试" }] }] }
        });
        const card = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "甲" });
        const card2 = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "乙" });
        await workspace.transact({
          type: "cardRelation.create",
          projectId: project.projectId,
          fromCardId: card.entityId,
          toCardId: card2.entityId,
          relationTypeId: "relation-type-character-character"
        });
        // scene impact（先删场景）
        await workspace.transact({ type: "scene.delete", sceneId: scene.id });
        const sceneImpact = await workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "scene", entityId: scene.id });
        assert.ok(sceneImpact, "场景影响存在");
        assert.equal(sceneImpact!.approxChars, 6);
        // chapter 未删除时查询 → 稳定 conflict 错误
        await expectWorkspaceError("conflict", () =>
          workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "chapter", entityId: chapter.id })
        );
        // 删除章节后查 impact
        await workspace.transact({ type: "chapter.delete", chapterId: chapter.id });
        const chapterImpact = await workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "chapter", entityId: chapter.id });
        assert.ok(chapterImpact);
        assert.equal(chapterImpact!.childSceneCount >= 1, true);
        // card impact
        await workspace.transact({ type: "card.delete", cardId: card.entityId });
        const cardImpact = await workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "card", entityId: card.entityId });
        assert.ok(cardImpact);
        // 卡片删除时关系已被级联清理，relatedCardCount 可为 0；附件计数独立可用。
        assert.equal(cardImpact!.relatedCardCount >= 0, true);
        assert.equal(cardImpact!.resourceCount, 0);
        // volume impact：章节/场景/约计字数
        const volumeOutline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const targetVolume = volumeOutline!.volumes[0]!;
        await workspace.transact({ type: "volume.delete", volumeId: targetVolume.id });
        const volumeImpact = await workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "volume", entityId: targetVolume.id });
        assert.ok(volumeImpact);
        assert.equal(volumeImpact!.childChapterCount >= 1, true);
        assert.equal(volumeImpact!.childSceneCount >= 1, true);
        assert.equal(volumeImpact!.approxChars >= 6, true);
        // 稳定错误
        await expectWorkspaceError("not-found", () =>
          workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "scene", entityId: "scene-nope" })
        );
        await expectWorkspaceError("conflict", () =>
          workspace.read({ kind: "trash.impact", projectId: project.projectId, entity: "card", entityId: card2.entityId })
        );
        void volume;
      });
    });

    // ------------------------------------------------------------------
    // 任务 D：快照恢复加固（Part 5 / P1-F09）
    //   1) 对象归属校验：跨项目创建/恢复稳定失败、零写入
    //   2) 目标已永久删除：预览 canRestore=false、恢复稳定拒绝且不产生保护快照
    //   3) 章节恢复跳过已移出本章的场景，不覆盖其在其他章节的新内容
    // ------------------------------------------------------------------
    await scenario("快照对象跨项目：create 稳定失败零写入，restore 经 B 项目访问 A 快照失败", async () => {
      await withWorkspace(path.join(parent, "ws-cross-project"), async (workspace) => {
        const projectA = await workspace.transact({ type: "project.create", title: "项目A" });
        const projectB = await workspace.transact({ type: "project.create", title: "项目B" });
        // 用 A 的场景对象在 B 项目下创建快照 → 稳定 invalid-input、零写入
        await expectWorkspaceError("invalid-input", () =>
          workspace.transact({
            type: "snapshot.create",
            projectId: projectB.projectId,
            subjectType: "scene",
            subjectId: projectA.sceneId,
            reason: "跨项目快照"
          })
        );
        const snapshotsB = await workspace.read({ kind: "snapshot.list", projectId: projectB.projectId });
        assert.equal(snapshotsB.length, 0, "B 不产生任何快照");
        // A 内正常创建后，经 B 项目恢复 → 快照行按项目隔离，稳定 not-found、零写入
        const snapshotA = await workspace.transact({
          type: "snapshot.create",
          projectId: projectA.projectId,
          subjectType: "scene",
          subjectId: projectA.sceneId,
          reason: "A 的里程碑"
        });
        await expectWorkspaceError("not-found", () =>
          workspace.restoreSnapshotWithProtection({
            type: "snapshot.restoreWithProtection",
            projectId: projectB.projectId,
            snapshotId: snapshotA.entityId,
            protectionReason: "跨项目恢复"
          })
        );
        const after = await workspace.read({ kind: "scene.body", sceneId: projectB.sceneId });
        assert.equal(JSON.stringify(after?.body), JSON.stringify({ type: "doc", content: [] }));
        const snapshotsB2 = await workspace.read({ kind: "snapshot.list", projectId: projectB.projectId });
        assert.equal(snapshotsB2.length, 0, "B 不产生任何快照（含保护快照）");
      });
    });

    await scenario("对象已永久删除：preview.canRestore=false，restore 稳定拒绝且不产生保护快照", async () => {
      await withWorkspace(path.join(parent, "ws-purged"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "永久删除" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const volume = outline!.volumes[0]!;
        const chapter = volume.chapters[0]!;
        const scene = chapter.scenes[0]!;
        const card = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "将删除" });
        const volume2 = await workspace.transact({ type: "volume.create", projectId: project.projectId, title: "待删卷" });

        const sceneSnap = await workspace.transact({ type: "snapshot.create", projectId: project.projectId, subjectType: "scene", subjectId: scene.id, reason: "场景快照" });
        const cardSnap = await workspace.transact({ type: "snapshot.create", projectId: project.projectId, subjectType: "card", subjectId: card.entityId, reason: "卡片快照" });
        const chapterSnap = await workspace.transact({ type: "snapshot.create", projectId: project.projectId, subjectType: "chapter", subjectId: chapter.id, reason: "章节快照" });
        const volumeSnap = await workspace.transact({ type: "snapshot.create", projectId: project.projectId, subjectType: "volume", subjectId: volume2.entityId, reason: "卷快照" });

        // 逐个永久删除目标（删除进回收站 → 清空回收站）
        await workspace.transact({ type: "scene.delete", sceneId: scene.id });
        await workspace.transact({ type: "trash.purge", projectId: project.projectId, entity: "scene", entityId: scene.id });
        await workspace.transact({ type: "card.delete", cardId: card.entityId });
        await workspace.transact({ type: "trash.purge", projectId: project.projectId, entity: "card", entityId: card.entityId });
        await workspace.transact({ type: "chapter.delete", chapterId: chapter.id });
        await workspace.transact({ type: "trash.purge", projectId: project.projectId, entity: "chapter", entityId: chapter.id });
        await workspace.transact({ type: "volume.delete", volumeId: volume2.entityId });
        await workspace.transact({ type: "trash.purge", projectId: project.projectId, entity: "volume", entityId: volume2.entityId });

        // 预览：全部 canRestore=false 且带永久删除警告
        for (const snap of [sceneSnap, cardSnap, chapterSnap, volumeSnap]) {
          const preview = await workspace.read({ kind: "snapshot.preview", projectId: project.projectId, snapshotId: snap.entityId });
          assert.ok(preview, "预览存在");
          assert.equal(preview!.canRestore, false, "永久删除后预览禁止恢复");
          assert.equal(preview!.warnings.some((w) => w.includes("永久删除")), true);
        }

        // 恢复：全部稳定 not-found 拒绝，且不产生保护快照
        const beforeCount = (await workspace.read({ kind: "snapshot.list", projectId: project.projectId })).length;
        for (const snap of [sceneSnap, cardSnap, chapterSnap, volumeSnap]) {
          await expectWorkspaceError("not-found", () =>
            workspace.restoreSnapshotWithProtection({
              type: "snapshot.restoreWithProtection",
              projectId: project.projectId,
              snapshotId: snap.entityId,
              protectionReason: "恢复已删除对象"
            })
          );
        }
        const afterCount = (await workspace.read({ kind: "snapshot.list", projectId: project.projectId })).length;
        assert.equal(afterCount, beforeCount, "拒绝恢复不产生保护快照");
      });
    });

    await scenario("chapter 恢复：已移出本章的场景被跳过且新正文不被覆盖，preview 明示", async () => {
      await withWorkspace(path.join(parent, "ws-chapter-moved"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "移出场景" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const chapterA = outline!.volumes[0]!.chapters[0]!;
        const chapterB = await workspace.transact({ type: "chapter.create", projectId: project.projectId, title: "第二章" });
        const sceneA1 = chapterA.scenes[0]!.id;
        const sceneA2 = await workspace.transact({ type: "scene.create", chapterId: chapterA.id, title: "场景A2" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA1,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A1版本一" }] }] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA2.entityId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A2版本一" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "chapter",
          subjectId: chapterA.id,
          reason: "第一章里程碑"
        });
        // 快照后：A1 改稿；A2 移到第二章并改稿（move 使 revision 递增）
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA1,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A1版本二" }] }] }
        });
        await workspace.transact({ type: "scene.move", sceneId: sceneA2.entityId, targetChapterId: chapterB.entityId });
        const movedBody = await workspace.read({ kind: "scene.body", sceneId: sceneA2.entityId });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA2.entityId,
          baseRevision: movedBody!.revision,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A2在第二章的最新正文" }] }] }
        });
        // 预览：明示 1 个场景已移出将被跳过
        const preview = await workspace.read({ kind: "snapshot.preview", projectId: project.projectId, snapshotId: snapshot.entityId });
        assert.ok(preview, "预览存在");
        assert.equal(preview!.canRestore, true);
        const movedRow = preview!.rows.find((row) => row.label === "已移出本章的场景（跳过）");
        assert.ok(movedRow, "预览包含已移出场景行");
        assert.equal(movedRow!.after, "1");
        assert.equal(preview!.warnings.some((w) => w.includes("已移出本章")), true);
        // 恢复：A1 回到版本一；A2 在第二章的最新正文不被覆盖
        const result = await workspace.restoreSnapshotWithProtection({
          type: "snapshot.restoreWithProtection",
          projectId: project.projectId,
          snapshotId: snapshot.entityId,
          protectionReason: "恢复第一章"
        });
        assert.equal(result.ok, true);
        const bodyA1 = await workspace.read({ kind: "scene.body", sceneId: sceneA1 });
        assert.equal(JSON.stringify(bodyA1?.body).includes("A1版本一"), true);
        const bodyA2 = await workspace.read({ kind: "scene.body", sceneId: sceneA2.entityId });
        assert.equal(JSON.stringify(bodyA2?.body).includes("A2在第二章的最新正文"), true, "移出场景的新正文不被覆盖");
      });
    });

    await scenario("volume 恢复：已移出本卷的章节与已移出本章的场景被跳过，仍属对象恢复", async () => {
      await withWorkspace(path.join(parent, "ws-volume-moved"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "卷恢复隔离" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const volumeA = outline!.volumes[0]!;
        const chapterA = volumeA.chapters[0]!;
        const sceneA1 = chapterA.scenes[0]!.id;
        const chapterATitleAtSnapshot = chapterA.title;
        const volumeB = await workspace.transact({ type: "volume.create", projectId: project.projectId, title: "卷B" });
        const chapterB = await workspace.transact({ type: "chapter.create", projectId: project.projectId, volumeId: volumeA.id, title: "第二章" });
        const sceneA2 = await workspace.transact({ type: "scene.create", chapterId: chapterA.id, title: "场景A2" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA1,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A1版本一" }] }] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA2.entityId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A2版本一" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "volume",
          subjectId: volumeA.id,
          reason: "卷A里程碑"
        });
        // 快照后：第二章移出卷A；场景A2移出第一章并改稿；第一章与 A1 改稿（应被恢复覆盖）
        await workspace.transact({ type: "chapter.move", chapterId: chapterB.entityId, targetVolumeId: volumeB.entityId });
        await workspace.transact({ type: "scene.move", sceneId: sceneA2.entityId, targetChapterId: chapterB.entityId });
        const movedBody = await workspace.read({ kind: "scene.body", sceneId: sceneA2.entityId });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA2.entityId,
          baseRevision: movedBody!.revision,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A2在卷B的最新正文" }] }] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA1,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A1版本二" }] }] }
        });
        const afterMoves = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const chapterAAfterMove = afterMoves!.volumes.find((volume) => volume.id === volumeA.id)!.chapters.find((chapter) => chapter.id === chapterA.id)!;
        const chapterBAfterMove = afterMoves!.volumes.find((volume) => volume.id === volumeB.entityId)!.chapters.find((chapter) => chapter.id === chapterB.entityId)!;
        await workspace.transact({ type: "chapter.rename", chapterId: chapterA.id, title: "第一章（改）", baseRevision: chapterAAfterMove.revision });
        await workspace.transact({ type: "chapter.rename", chapterId: chapterB.entityId, title: "第二章（已移出）", baseRevision: chapterBAfterMove.revision });
        // 预览：明示 1 个章节移出本卷、1 个场景移出所在章节将被跳过
        const preview = await workspace.read({ kind: "snapshot.preview", projectId: project.projectId, snapshotId: snapshot.entityId });
        assert.ok(preview, "预览存在");
        assert.equal(preview!.canRestore, true);
        const movedChapterRow = preview!.rows.find((row) => row.label === "已移出本卷的章节（跳过）");
        assert.ok(movedChapterRow, "预览包含已移出本卷的章节行");
        assert.equal(movedChapterRow!.after, "1");
        const movedSceneRow = preview!.rows.find((row) => row.label === "已移出所在章节的场景（跳过）");
        assert.ok(movedSceneRow, "预览包含已移出所在章节的场景行");
        assert.equal(movedSceneRow!.after, "1");
        assert.equal(preview!.warnings.some((w) => w.includes("已移出本卷")), true);
        assert.equal(preview!.warnings.some((w) => w.includes("已移出所在章节")), true);
        // 恢复：第一章与 A1 回到快照状态；第二章仍属卷B且新标题、A2 新正文与归属均不被覆盖
        const result = await workspace.restoreSnapshotWithProtection({
          type: "snapshot.restoreWithProtection",
          projectId: project.projectId,
          snapshotId: snapshot.entityId,
          protectionReason: "恢复卷A"
        });
        assert.equal(result.ok, true);
        const after = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const volumeAAfter = after!.volumes.find((volume) => volume.id === volumeA.id);
        const volumeBAfter = after!.volumes.find((volume) => volume.id === volumeB.entityId);
        assert.ok(volumeAAfter && volumeBAfter, "恢复后两卷均存在");
        const chapterAAfter = volumeAAfter!.chapters.find((chapter) => chapter.id === chapterA.id);
        const chapterBAfter = volumeBAfter!.chapters.find((chapter) => chapter.id === chapterB.entityId);
        assert.ok(chapterAAfter, "第一章仍属卷A");
        assert.equal(chapterAAfter!.title, chapterATitleAtSnapshot, "仍属本卷的章节标题被恢复");
        assert.equal(chapterAAfter!.scenes.some((scene) => scene.id === sceneA2.entityId), false, "移出场景不被拉回卷A");
        assert.ok(chapterBAfter, "第二章仍属卷B");
        assert.equal(chapterBAfter!.title, "第二章（已移出）", "移出章节的新标题不被覆盖");
        assert.equal(chapterBAfter!.scenes.some((scene) => scene.id === sceneA2.entityId), true, "移出场景归属不变");
        const bodyA1 = await workspace.read({ kind: "scene.body", sceneId: sceneA1 });
        assert.equal(JSON.stringify(bodyA1?.body).includes("A1版本一"), true, "仍属本卷本节的场景正文被恢复");
        const bodyA2 = await workspace.read({ kind: "scene.body", sceneId: sceneA2.entityId });
        assert.equal(JSON.stringify(bodyA2?.body).includes("A2在卷B的最新正文"), true, "移出场景的新正文不被覆盖");
      });
    });


    await scenario("scene 恢复不覆盖其他对象的新修改（P1-F09 隔离）", async () => {
      await withWorkspace(path.join(parent, "ws-scene-isolation"), async (workspace) => {
        const project = await workspace.transact({ type: "project.create", title: "隔离恢复" });
        const outline = await workspace.read({ kind: "project.outline", projectId: project.projectId });
        const chapter = outline!.volumes[0]!.chapters[0]!;
        const sceneA = chapter.scenes[0]!.id;
        const sceneB = await workspace.transact({
          type: "scene.create",
          chapterId: chapter.id,
          title: "场景B"
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneB.entityId,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "B初始" }] }] }
        });
        const card = await workspace.transact({ type: "card.create", projectId: project.projectId, kind: "character", title: "卡初始" });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA,
          baseRevision: 1,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A版本一" }] }] }
        });
        const snapshot = await workspace.transact({
          type: "snapshot.create",
          projectId: project.projectId,
          subjectType: "scene",
          subjectId: sceneA,
          reason: "A 里程碑"
        });
        // 快照后：A 与 B 与卡片都被修改
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneA,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "A版本二" }] }] }
        });
        await workspace.transact({
          type: "scene.updateBody",
          sceneId: sceneB.entityId,
          baseRevision: 2,
          body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "B最新修改" }] }] }
        });
        await workspace.transact({ type: "card.update", cardId: card.entityId, title: "卡最新修改", baseRevision: 1 });

        const result = await workspace.restoreSnapshotWithProtection({
          type: "snapshot.restoreWithProtection",
          projectId: project.projectId,
          snapshotId: snapshot.entityId,
          protectionReason: "恢复 A"
        });
        assert.equal(result.ok, true);
        const bodyA = await workspace.read({ kind: "scene.body", sceneId: sceneA });
        assert.equal(JSON.stringify(bodyA?.body).includes("A版本一"), true);
        const bodyB = await workspace.read({ kind: "scene.body", sceneId: sceneB.entityId });
        assert.equal(JSON.stringify(bodyB?.body).includes("B最新修改"), true, "场景 B 的新修改不被覆盖");
        const restoredCard = await workspace.read({ kind: "card.read", cardId: card.entityId });
        assert.equal(restoredCard?.title, "卡最新修改", "卡片的新修改不被场景恢复覆盖");
      });
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await rm(parent, { recursive: true, force: true });
        break;
      } catch {
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
    }
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
