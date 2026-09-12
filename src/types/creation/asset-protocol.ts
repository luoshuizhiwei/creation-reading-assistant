/**
 * 创作工作区只读资源协议（主进程与渲染进程共用）。
 *
 * 封面与附件字节既不进 IPC 负载，也不向渲染进程暴露文件系统绝对路径：渲染进程只拿到一个
 * `creation-asset://card/<cardId>/<resourceId>` 形式的 URL，由主进程在
 * `registerCreationAssetProtocol` 中按资源记录反查真实文件，并强制该文件必须位于创作工作区
 * 根目录内（路径包含校验）。卡片 ID 与资源 ID 都必须在数据库中存在，否则协议直接返回错误码，
 * 不会回退到任意路径。
 *
 * 之所以用协议而不是 IPC 传 base64：一是封面可以有几 MB，二是 `<img>` 能直接消费 URL，
 * 无需在渲染进程里做 blob 生命周期管理。
 */

export const CREATION_ASSET_SCHEME = "creation-asset";

/** 资源 URL 的固定主机名，用于区分未来可能出现的其它资源类别。 */
export const CREATION_ASSET_HOST = "card";

export interface CreationAssetTarget {
  cardId: string;
  resourceId: string;
}

/** 构造卡片资源 URL；两段 ID 都做 URI 编码，避免路径分隔符注入。 */
export function buildCardResourceUrl(cardId: string, resourceId: string): string {
  return `${CREATION_ASSET_SCHEME}://${CREATION_ASSET_HOST}/${encodeURIComponent(cardId)}/${encodeURIComponent(resourceId)}`;
}

/** 解码单个路径段；非法百分号转义返回 null。 */
function decodeSegment(segment: string): string | null {
  try {
    return decodeURIComponent(segment);
  } catch {
    return null;
  }
}

/**
 * 解析卡片资源 URL。
 *
 * 只有严格符合 `creation-asset://card/<cardId>/<resourceId>` 的输入才返回目标；协议不符、
 * host 不符、段数不为 2、空段，以及解码后仍含路径分隔符或 `..` 的输入一律返回 null。
 */
export function parseCardResourceUrl(rawUrl: string): CreationAssetTarget | null {
  if (typeof rawUrl !== "string" || rawUrl.length === 0) return null;
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return null;
  }
  if (url.protocol !== `${CREATION_ASSET_SCHEME}:`) return null;
  if (url.hostname !== CREATION_ASSET_HOST) return null;
  const segments = url.pathname.split("/").filter((segment) => segment.length > 0);
  if (segments.length !== 2) return null;
  const cardId = decodeSegment(segments[0]!);
  const resourceId = decodeSegment(segments[1]!);
  if (!cardId || !resourceId) return null;
  for (const value of [cardId, resourceId]) {
    if (value.includes("/") || value.includes("\\") || value.includes("..")) return null;
  }
  return { cardId, resourceId };
}
