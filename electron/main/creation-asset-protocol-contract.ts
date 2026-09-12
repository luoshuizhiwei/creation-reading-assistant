/**
 * 只读资源协议契约测试（阶段 4-A）。
 *
 * 覆盖 `creation-asset://card/<cardId>/<resourceId>` 的解析与解析失败路径，重点验证：
 * - 只有登记在该卡片上的资源才可读（项目私有资源、他人卡片资源一律拒绝）；
 * - 解析结果必须落在创作工作区根目录内（含被篡改的穿越路径记录）；
 * - 文件缺失、卡片已删除时返回 null，而不是回退到任意路径。
 *
 * 这里只测解析层（`resolveCardAssetPath`）；协议注册本身由真实打包验收覆盖。
 */

import { strict as assert } from "node:assert";
import { mkdtemp, mkdir, writeFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { createCreationCoordinator } from "./creation-coordinator";
import { creationWorkspaceRoot, isInsidePath, resolveCardAssetPath } from "./creation-asset-protocol";
import { removeWithRetry } from "./creation-workspace/test-utils";
import { SCHEMA_VERSION } from "./creation-workspace/index";
import { buildCardResourceUrl, parseCardResourceUrl } from "../../src/types/creation";
import type { ResourceAttachCommand } from "../../src/types/creation";

async function run(): Promise<void> {
  const dataRoot = await mkdtemp(path.join(os.tmpdir(), "creation-asset-"));
  const workspaceDirectory = path.join(dataRoot, "CreationWorkspace");
  const coordinator = createCreationCoordinator({ resolveDirectory: () => workspaceDirectory });
  const resolveDataRoot = (): string => dataRoot;
  const root = creationWorkspaceRoot(resolveDataRoot);
  let tests = 0;
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

    // 逐条命令显式走 overload，避免用宽泛的联合类型掩盖命令形状错误。
    const createProject = async (title: string): Promise<{ projectId: string }> =>
      (await coordinator.withWorkspace((workspace) =>
        workspace.transact({ type: "project.create", title })
      )) as unknown as { projectId: string };

    const createCard = async (title: string): Promise<string> => {
      const created = (await coordinator.withWorkspace((workspace) =>
        workspace.transact({ type: "card.create", kind: "character", title })
      )) as unknown as { entityId: string };
      return created.entityId;
    };

    const attachResource = async (command: ResourceAttachCommand): Promise<string> => {
      const result = (await coordinator.withWorkspace((workspace) =>
        workspace.transact(command)
      )) as unknown as { resourceId: string };
      return result.resourceId;
    };

    const linkCard = (projectId: string, cardId: string): Promise<unknown> =>
      coordinator.withWorkspace((workspace) => workspace.transact({ type: "card.link", projectId, cardId }));

    const deleteCard = (cardId: string): Promise<unknown> =>
      coordinator.withWorkspace((workspace) => workspace.transact({ type: "card.delete", cardId }));

    const resolve = (cardId: string, resourceId: string): Promise<string | null> =>
      resolveCardAssetPath(coordinator, resolveDataRoot, cardId, resourceId);

    const report = await coordinator.withWorkspace((workspace) => workspace.check());
    assert.equal(report.ok, true);
    assert.equal(report.schemaVersion, SCHEMA_VERSION);
    assert.equal(root, workspaceDirectory);

    let cardId = "";
    let otherCardId = "";
    let coverResourceId = "";
    let attachmentResourceId = "";
    let projectResourceId = "";
    let projectScopedCardResourceId = "";

    await scenario("准备：全局卡片 + 封面 + 附件 + 项目私有资源 + 真实落盘文件", async () => {
      const project = await createProject("资产项目");
      cardId = await createCard("主角");
      otherCardId = await createCard("地点");
      const sha256 = "a".repeat(64);
      coverResourceId = await attachResource({
        type: "resource.attach",
        cardId,
        role: "cover",
        relativePath: `resources/cards/${cardId}/cover.png`,
        sha256,
        size: 8,
        originalName: "封面.png"
      });
      attachmentResourceId = await attachResource({
        type: "resource.attach",
        cardId,
        relativePath: `resources/cards/${cardId}/note.txt`,
        sha256,
        size: 4,
        originalName: "备注.txt"
      });
      // 项目私有资源（不归属任何卡片）。
      projectResourceId = await attachResource({
        type: "resource.attach",
        projectId: project.projectId,
        relativePath: `resources/${project.projectId}/private.txt`,
        sha256,
        size: 4,
        originalName: "私有.txt"
      });
      // 项目作用域、但引用了同一张卡片的资源：必须仍然不可通过卡片协议读到。
      // （项目附件要求卡片已关联到该项目，因此先建立关联。）
      await linkCard(project.projectId, cardId);
      projectScopedCardResourceId = await attachResource({
        type: "resource.attach",
        projectId: project.projectId,
        cardId,
        relativePath: `resources/${project.projectId}/card-scoped.txt`,
        sha256,
        size: 4,
        originalName: "项目内卡片材料.txt"
      });
      // 协议只读文件，不负责创建文件：这里按真实落盘位置补齐。
      await mkdir(path.join(root, `resources/cards/${cardId}`), { recursive: true });
      await writeFile(path.join(root, `resources/cards/${cardId}/cover.png`), Buffer.from([0x89, 0x50, 0x4e, 0x47]));
      await writeFile(path.join(root, `resources/cards/${cardId}/note.txt`), Buffer.from("note"));
    });

    await scenario("封面解析为工作区内绝对路径", async () => {
      const resolved = await resolve(cardId, coverResourceId);
      assert.equal(resolved, path.join(root, `resources/cards/${cardId}/cover.png`));
      assert.equal(isInsidePath(root, resolved!), true);
    });

    await scenario("非封面附件同样可解析：协议只认卡片归属，不按角色区分", async () => {
      const resolved = await resolve(cardId, attachmentResourceId);
      assert.equal(resolved, path.join(root, `resources/cards/${cardId}/note.txt`));
    });

    await scenario("资源不属于该卡片时不可解析", async () => {
      // 同一份封面记录，换一张卡片 ID 必须失败。
      assert.equal(await resolve(otherCardId, coverResourceId), null);
      // 不存在的资源 ID。
      assert.equal(await resolve(cardId, "resource-missing"), null);
    });

    await scenario("项目私有资源不会被卡片协议读到", async () => {
      assert.equal(await resolve(cardId, projectResourceId), null);
      // 即便该资源引用了这张卡片，它登记在项目作用域，同样读不到。
      assert.equal(await resolve(cardId, projectScopedCardResourceId), null);
    });

    await scenario("文件缺失时不可解析，而不是回退到任意路径", async () => {
      const absolute = path.join(root, `resources/cards/${cardId}/note.txt`);
      await rm(absolute, { force: true });
      assert.equal(await resolve(cardId, attachmentResourceId), null);
    });

    await scenario("卡片删除后其资源不可解析", async () => {
      const doomedCardId = await createCard("待删卡片");
      const doomedCoverId = await attachResource({
        type: "resource.attach",
        cardId: doomedCardId,
        role: "cover",
        relativePath: `resources/cards/${doomedCardId}/cover.png`,
        sha256: "c".repeat(64),
        size: 4,
        originalName: "封面.png"
      });
      await mkdir(path.join(root, `resources/cards/${doomedCardId}`), { recursive: true });
      await writeFile(path.join(root, `resources/cards/${doomedCardId}/cover.png`), Buffer.from([0x89, 0x50]));
      // 删除前可解析，证明失败原因来自卡片被删除而不是文件缺失。
      assert.equal(typeof (await resolve(doomedCardId, doomedCoverId)), "string");
      await deleteCard(doomedCardId);
      assert.equal(await resolve(doomedCardId, doomedCoverId), null);
    });

    await scenario("被篡改的穿越路径记录被路径包含校验拒绝", async () => {
      // 通过正常命令无法写入越界路径（resource.attach 会校验前缀），这里直接改库模拟损坏数据。
      const raw = new Database(path.join(workspaceDirectory, "workspace.sqlite"));
      raw
        .prepare(
          "INSERT INTO global_card_resources(id, card_id, relative_path, sha256, size, original_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?, 'attachment', ?)"
        )
        .run("resource-traversal", cardId, "../escaped.png", "d".repeat(64), 4, "escaped.png", new Date().toISOString());
      raw.close();
      // 在工作区根之外放一个真实文件，证明拒绝原因是越界而非文件不存在。
      await writeFile(path.join(dataRoot, "escaped.png"), Buffer.from("x"));
      assert.equal(await resolve(cardId, "resource-traversal"), null);
    });

    await scenario("URL 构造与解析往返一致，非法输入一律拒绝", async () => {
      const url = buildCardResourceUrl(cardId, coverResourceId);
      assert.equal(url.startsWith("creation-asset://card/"), true);
      const parsed = parseCardResourceUrl(url);
      assert.equal(parsed?.cardId, cardId);
      assert.equal(parsed?.resourceId, coverResourceId);
      // 需要编码的 ID 也要能往返。
      const encoded = buildCardResourceUrl("card 1", "resource/2");
      assert.equal(parseCardResourceUrl(encoded), null, "编码后的斜杠在解码后仍应被拒绝");
      assert.equal(parseCardResourceUrl(buildCardResourceUrl("card-1", "res-2"))?.cardId, "card-1");
      assert.equal(parseCardResourceUrl("creation-asset://card/only-one"), null);
      assert.equal(parseCardResourceUrl("creation-asset://card/a/b/c"), null);
      assert.equal(parseCardResourceUrl("creation-asset://other/a/b"), null);
      assert.equal(parseCardResourceUrl("novel-workbench-epub://card/a/b"), null);
      assert.equal(parseCardResourceUrl("creation-asset://card/a%2Fb/c"), null);
      assert.equal(parseCardResourceUrl("not a url"), null);
      assert.equal(parseCardResourceUrl(""), null);
    });

    await scenario("isInsidePath 语义：相等算包含，前缀相近不算包含", async () => {
      assert.equal(isInsidePath("/a/b", "/a/b"), true);
      assert.equal(isInsidePath("/a/b", "/a/b/c"), true);
      assert.equal(isInsidePath("/a/b", "/a/bc"), false);
      assert.equal(isInsidePath("/a/b", "/a"), false);
      assert.equal(isInsidePath("/a/b", "/a/b/../c"), false);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await coordinator.close().catch(() => undefined);
    await removeWithRetry(dataRoot);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
