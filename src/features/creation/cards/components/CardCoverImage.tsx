import { useEffect, useState } from "react";
import { ImageOff } from "lucide-react";
import { buildCardResourceUrl, type ResourceInfo } from "@/types/creation";

type CoverStatus = "loading" | "ready" | "failed";

export interface CardCoverImageProps {
  cardId: string;
  /** 该卡片的全部资源；组件自行挑出 `role === "cover"` 的那一条。 */
  resources: ResourceInfo[];
  /** 卡片标题，用于 alt 与空态说明。 */
  title: string;
}

/**
 * 全局卡片封面。
 *
 * 封面字节由主进程的 `creation-asset` 只读协议提供，渲染进程只持有 URL，不接触文件路径。
 * 三种状态都要有明确呈现：未设置封面（空态）、正在读取（加载态）、读取失败（失败态，
 * 例如文件已被手工删除或协议拒绝）。
 *
 * 封面资源 ID 会随「移除封面后重新设置」而改变，因此 URL 天然带缓存区分，不需要额外的
 * 时间戳参数来强制刷新。
 */
export function CardCoverImage({ cardId, resources, title }: CardCoverImageProps) {
  const cover = resources.find((resource) => resource.role === "cover");
  const url = cover ? buildCardResourceUrl(cardId, cover.id) : null;
  const [status, setStatus] = useState<CoverStatus>("loading");

  useEffect(() => {
    setStatus("loading");
  }, [url]);

  if (!url) {
    // 空态不占用 img 角色，避免屏幕阅读器把「没有封面」当成一张图片。
    return (
      <div className="global-card-cover global-card-cover--empty">
        <ImageOff size={18} aria-hidden="true" />
        <span>{`${title} 尚未设置封面`}</span>
      </div>
    );
  }

  return (
    <div className="global-card-cover">
      <img
        className="global-card-cover-image"
        src={url}
        alt={`${title} 封面`}
        onLoad={() => setStatus("ready")}
        onError={() => setStatus("failed")}
      />
      {status !== "ready" ? (
        <span className="global-card-cover-status" role={status === "failed" ? "alert" : "status"}>
          {status === "loading" ? "正在读取封面…" : "封面读取失败，文件可能已被移除。"}
        </span>
      ) : null}
    </div>
  );
}

export default CardCoverImage;
