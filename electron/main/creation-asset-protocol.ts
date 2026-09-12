/**
 * 创作工作区只读资源协议（主进程注册）。
 *
 * 渲染进程只能通过 `creation-asset://card/<cardId>/<resourceId>` 读取全局卡片封面/附件。
 * 这里做三件事，缺一不可：
 * 1. URL 形状校验（`parseCardResourceUrl`），拒绝任何多余段与穿越片段；
 * 2. 反查数据库，确认该资源确实登记在这张卡片上（`resource.list` 已含「卡片未删除」校验），
 *    且 `ownerScope === "card"`，因此项目私有资源不会被这条协议读到；
 * 3. 路径包含校验：解析出的绝对路径必须落在创作工作区根目录内，否则一律失败。
 *
 * 任何一步不满足都返回错误码，绝不回退到任意路径。
 */

import { protocol } from "electron";
import path from "node:path";
import { existsSync } from "node:fs";
import { CREATION_ASSET_SCHEME, parseCardResourceUrl } from "../../src/types/creation";
import type { ResourceInfo } from "../../src/types/creation";
import type { CreationCoordinator } from "./creation-coordinator";

/** 资源文件的根目录；与 `creation:attachResource` 的写入位置保持一致。 */
export function creationWorkspaceRoot(resolveDataRoot: () => string): string {
  return path.join(resolveDataRoot(), "CreationWorkspace");
}

/**
 * 路径包含校验：child 必须落在 parent 内（含相等）。
 * 与 index.ts 中的同名工具同语义，此处独立实现以避免主进程入口与 IPC 模块互相引用。
 */
export function isInsidePath(parentPath: string, childPath: string): boolean {
  const parent = path.resolve(parentPath);
  const child = path.resolve(childPath);
  return child === parent || child.startsWith(`${parent}${path.sep}`);
}

/**
 * 把「卡片 ID + 资源 ID」解析为工作区内可读文件的绝对路径。
 *
 * 返回 null 表示不可解析（卡片不存在、资源不属于该卡片、路径越界、文件已丢失），
 * 调用方据此回错误码。
 */
export async function resolveCardAssetPath(
  coordinator: CreationCoordinator,
  resolveDataRoot: () => string,
  cardId: string,
  resourceId: string
): Promise<string | null> {
  let resources: ResourceInfo[];
  try {
    resources = await coordinator.withWorkspace(
      (workspace) => workspace.read({ kind: "resource.list", cardId }) as Promise<ResourceInfo[]>
    );
  } catch {
    // 卡片不存在、工作区未打开等情况一律按不可解析处理。
    return null;
  }
  const resource = resources.find((item) => item.id === resourceId && item.ownerScope === "card");
  if (!resource) return null;
  const root = creationWorkspaceRoot(resolveDataRoot);
  const absolutePath = path.resolve(root, resource.relativePath);
  if (!isInsidePath(root, absolutePath)) return null;
  if (!existsSync(absolutePath)) return null;
  return absolutePath;
}

/**
 * 注册只读资源协议。必须在 app ready 之后调用（与 `registerEpubProtocol` 同阶段）；
 * 协议本身需在 ready 之前通过 `registerSchemesAsPrivileged` 声明为 standard + secure。
 *
 * 使用 `registerFileProtocol` 而不是 `protocol.handle`：由 Electron 按扩展名推导
 * Content-Type，图片扩展名（png/jpg/jpeg/webp/gif）会被识别为对应 image/*，`<img>` 可直接消费。
 */
export function registerCreationAssetProtocol(
  coordinator: CreationCoordinator,
  context: { resolveDataRoot: () => string }
): void {
  protocol.registerFileProtocol(CREATION_ASSET_SCHEME, (request, callback) => {
    void (async () => {
      const target = parseCardResourceUrl(request.url);
      if (!target) {
        callback({ error: -6 });
        return;
      }
      const absolutePath = await resolveCardAssetPath(
        coordinator,
        context.resolveDataRoot,
        target.cardId,
        target.resourceId
      );
      if (!absolutePath) {
        callback({ error: -6 });
        return;
      }
      callback({ path: absolutePath });
    })().catch(() => callback({ error: -2 }));
  });
}
