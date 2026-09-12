/**
 * 资源孤儿只读扫描契约。
 *
 * 通过 esbuild 打包为 cjs，再用 electron ELECTRON_RUN_AS_NODE=1 运行。
 * 覆盖合同要求的全部问题类型，并断言扫描严格只读（前后 DB / 文件树哈希不变、
 * 不暴露绝对路径、临时 operation 目录不误报）。成功时末行打印 JSON 证据。
 */

import { strict as assert } from "node:assert";
import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import {  mkdir, mkdtemp, readFile, readdir, rm, symlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { openCreationWorkspace, scanResourceConsistencyCore } from "./index";
import {
  assertV9AttachmentFiles,
  assertV9BaselineReadable,
  seedV9Baseline,
  type V9Baseline
} from "./v9-baseline-fixture";

async function seedWorkspace(parent: string): Promise<{ workspaceDir: string; resourceRel: string; projectId: string; cardId: string }> {
  const workspaceDir = path.join(parent, `ws-${randomUUID()}`);
  let resourceRel = "";
  let projectId = "";
  let cardId = "";
  const workspace = await openCreationWorkspace({ directory: workspaceDir });
  try {
    const created = (await workspace.transact({
      type: "project.create",
      title: "扫描项目",
      setup: { template: "long-form", weeklyUpdateDays: [5], chapterWorkflow: ["规划", "待写"] }
    })) as { projectId: string };
    projectId = created.projectId;
    const card = (await workspace.transact({
      type: "card.create",
      projectId: created.projectId,
      kind: "character",
      title: "角色"
    })) as { entityId: string };
    cardId = card.entityId;
    const content = Buffer.from("资源-内容-20260814", "utf8");
    resourceRel = `resources/${created.projectId}/${randomUUID()}-材料.pdf`;
    await mkdir(path.dirname(path.join(workspaceDir, resourceRel)), { recursive: true });
    await writeFile(path.join(workspaceDir, resourceRel), content);
    await workspace.transact({
      type: "resource.attach",
      projectId: created.projectId,
      cardId: card.entityId,
      relativePath: resourceRel,
      sha256: createHash("sha256").update(content).digest("hex"),
      size: content.length,
      originalName: "材料.pdf"
    });
  } finally {
    await workspace.close();
  }
  return { workspaceDir, resourceRel, projectId, cardId };
}

function rawInsert(workspaceDir: string, row: { id: string; projectId: string; relativePath: string; sha256: string; size: number }): void {
  const db = new Database(path.join(workspaceDir, "workspace.sqlite"));
  try {
    db.prepare(
      "INSERT INTO resources (id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, NULL, ?, ?, ?, 'x', '2026-01-01T00:00:00.000Z')"
    ).run(row.id, row.projectId, row.relativePath, row.sha256, row.size);
  } finally {
    db.close();
  }
}

async function hashWorkspace(workspaceDir: string): Promise<string> {
  const hash = createHash("sha256");
  const dbPath = path.join(workspaceDir, "workspace.sqlite");
  if (existsSync(dbPath)) hash.update(await readFile(dbPath));
  const resourcesDir = path.join(workspaceDir, "resources");
  const walk = async (relative: string): Promise<void> => {
    let entries;
    try {
      entries = await readdir(path.join(resourcesDir, relative), { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const child = relative ? `${relative}/${entry.name}` : entry.name;
      if (entry.isDirectory()) await walk(child);
      else if (entry.isFile()) hash.update(child).update(await readFile(path.join(resourcesDir, child)));
    }
  };
  await walk("");
  return hash.digest("hex");
}

async function run(): Promise<number> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "resource-scan-contract-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.stack ?? error.message : String(error)}`);
      }
    };

    await scenario("健康工作区：无问题", async () => {
      const { workspaceDir } = await seedWorkspace(parent);
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(result.issues.length, 0);
      assert.equal(result.scannedRecordCount >= 1, true);
      assert.equal(result.scannedFileCount >= 1, true);
    });

    await scenario("全局卡片资产参与物理文件一致性扫描", async () => {
      const { workspaceDir, cardId } = await seedWorkspace(parent);
      const content = Buffer.from("全局卡片封面-20260911", "utf8");
      const globalRel = `resources/cards/${cardId}/${randomUUID()}-cover.png`;
      await mkdir(path.dirname(path.join(workspaceDir, globalRel)), { recursive: true });
      await writeFile(path.join(workspaceDir, globalRel), content);
      const workspace = await openCreationWorkspace({ directory: workspaceDir });
      try {
        await workspace.transact({
          type: "resource.attach",
          cardId,
          role: "cover",
          relativePath: globalRel,
          sha256: createHash("sha256").update(content).digest("hex"),
          size: content.length,
          originalName: "cover.png"
        });
      } finally {
        await workspace.close();
      }

      const healthy = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(healthy.issues.length, 0);
      assert.equal(healthy.scannedRecordCount >= 2, true);
      await rm(path.join(workspaceDir, globalRel), { force: true });
      const missing = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(missing.issues.some((issue) => issue.type === "file-missing" && issue.relativePath === globalRel), true);
    });

    await scenario("缺失文件：记录存在但文件缺失 → file-missing", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      await rm(path.join(workspaceDir, resourceRel), { force: true });
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issue = result.issues.find((i) => i.type === "file-missing");
      assert.equal(issue !== undefined, true);
      assert.equal(issue!.relativePath, resourceRel);
      assert.equal(issue!.resourceId !== undefined, true);
    });

    await scenario("未引用文件：文件存在但无记录 → file-unreferenced", async () => {
      const { workspaceDir } = await seedWorkspace(parent);
      const orphan = "resources/orphan-extra.txt";
      await writeFile(path.join(workspaceDir, orphan), "无人认领");
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issue = result.issues.find((i) => i.type === "file-unreferenced");
      assert.equal(issue !== undefined, true);
      assert.equal(issue!.relativePath, orphan);
    });

    await scenario("大小不符：文件大小与记录不一致 → size-mismatch", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      await writeFile(path.join(workspaceDir, resourceRel), "更长的篡改内容-20260814-extra");
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issue = result.issues.find((i) => i.type === "size-mismatch");
      assert.equal(issue !== undefined, true);
      assert.equal(issue!.relativePath, resourceRel);
    });

    await scenario("哈希不符：内容变化但大小相同 → hash-mismatch", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      const original = await readFile(path.join(workspaceDir, resourceRel));
      // 等长但逐字节翻转，确保大小一致、哈希不同。
      const flipped = Buffer.from(original.map((b) => (b === 0 ? 1 : b - 1)));
      await writeFile(path.join(workspaceDir, resourceRel), flipped);
      assert.equal(flipped.length, original.length);
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issue = result.issues.find((i) => i.type === "hash-mismatch");
      assert.equal(issue !== undefined, true);
      assert.equal(issue!.relativePath, resourceRel);
    });

    await scenario("路径穿越 / 绝对路径记录 → unsafe-relative-path", async () => {
      const { workspaceDir, projectId } = await seedWorkspace(parent);
      rawInsert(workspaceDir, {
        id: `bad-${randomUUID()}`,
        projectId,
        relativePath: "resources/../../escape.txt",
        sha256: "0".repeat(64),
        size: 0
      });
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issue = result.issues.find((i) => i.type === "unsafe-relative-path");
      assert.equal(issue !== undefined, true);
      assert.equal(issue!.relativePath.includes("/"), true);
    });

    await scenario("符号链接文件 → symlink-escape（尽力，特权不足时跳过断言）", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      const linkRel = `${resourceRel}.link`;
      let created = false;
      try {
        await symlink(path.join(workspaceDir, resourceRel), path.join(workspaceDir, linkRel));
        created = true;
      } catch {
        created = false;
      }
      if (created) {
        const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
        const issue = result.issues.find((i) => i.type === "symlink-escape");
        assert.equal(issue !== undefined, true);
      }
    });

    await scenario("临时 operation 目录不误报为孤儿", async () => {
      const { workspaceDir } = await seedWorkspace(parent);
      const tempDir = path.join(workspaceDir, "resources", `.bundle-import-${randomUUID()}`);
      await mkdir(tempDir, { recursive: true });
      await writeFile(path.join(tempDir, "staging-extra.txt"), "临时文件");
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(result.issues.some((i) => i.type === "file-unreferenced"), false);
    });

    await scenario("重复相对路径 → duplicate-path", async () => {
      const { workspaceDir, resourceRel, projectId } = await seedWorkspace(parent);
      rawInsert(workspaceDir, {
        id: `dup-a-${randomUUID()}`,
        projectId,
        relativePath: resourceRel,
        sha256: "0".repeat(64),
        size: 1
      });
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      const issues = result.issues.filter((i) => i.type === "duplicate-path");
      assert.equal(issues.length >= 1, true);
    });

    await scenario("多种问题可同时报告（缺失文件 + 未引用文件）", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      await rm(path.join(workspaceDir, resourceRel), { force: true });
      await writeFile(path.join(workspaceDir, "resources", " stray-unreferenced.txt"), "无人认领");
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(result.issues.some((i) => i.type === "file-missing"), true);
      assert.equal(result.issues.some((i) => i.type === "file-unreferenced"), true);
    });

    await scenario("扫描严格只读：前后 DB / 资源文件树哈希不变", async () => {
      const { workspaceDir } = await seedWorkspace(parent);
      const before = await hashWorkspace(workspaceDir);
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(result.issues.length, 0);
      const after = await hashWorkspace(workspaceDir);
      assert.equal(after, before);
    });

    await scenario("扫描报告不含绝对路径", async () => {
      const { workspaceDir, resourceRel } = await seedWorkspace(parent);
      await rm(path.join(workspaceDir, resourceRel), { force: true });
      const result = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      for (const issue of result.issues) {
        assert.equal(path.isAbsolute(issue.relativePath), false);
        assert.equal(issue.message.includes(workspaceDir), false);
      }
    });

    await scenario("v9 双项目基线：真实附件与 DB 记录完全一致 → 零问题；关闭重开后仍成立", async () => {
      const workspaceDir = path.join(parent, `ws-v9-${randomUUID()}`);
      let baseline: V9Baseline | undefined;
      const seeding = await openCreationWorkspace({ directory: workspaceDir, testOnlyTargetSchemaVersion: 9 });
      try {
        // 夹具写入 A / B 各自的真实附件文件（仅限 resources/<projectId>/ 下）。
        baseline = await seedV9Baseline(seeding, { workspaceDirectory: workspaceDir });
      } finally {
        await seeding.close();
      }

      const first = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(first.issues.length, 0, `双项目基线应零问题，实际：${JSON.stringify(first.issues)}`);
      assert.equal(first.scannedRecordCount >= 2, true, "应至少扫描到 A / B 两条附件记录");
      assert.equal(first.scannedFileCount >= 2, true, "应至少扫描到 A / B 两个附件文件");

      // 关闭 → 重开：数据与文件都必须是落盘态，而非内存态。
      const reopened = await openCreationWorkspace({ directory: workspaceDir, testOnlyTargetSchemaVersion: 9 });
      try {
        await assertV9BaselineReadable(reopened, baseline!);
      } finally {
        await reopened.close();
      }
      await assertV9AttachmentFiles(workspaceDir, baseline!);

      const second = await scanResourceConsistencyCore({ workspaceDirectory: workspaceDir });
      assert.equal(second.issues.length, 0, `重开后仍应零问题，实际：${JSON.stringify(second.issues)}`);
      assert.equal(second.scannedRecordCount, first.scannedRecordCount);
      assert.equal(second.scannedFileCount, first.scannedFileCount);
    });

    return tests;
  } finally {
    await rm(parent, { recursive: true, force: true }).catch(() => undefined);
  }
}

run()
  .then((tests) => {
    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  })
  .catch((error) => {
    process.stdout.write(`${JSON.stringify({ allPass: false, error: error instanceof Error ? error.message : String(error) })}\n`);
    process.exitCode = 1;
  });
