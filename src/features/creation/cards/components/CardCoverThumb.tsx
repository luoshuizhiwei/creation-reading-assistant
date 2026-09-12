import { useState } from "react";
import { ImageOff } from "lucide-react";
import { buildCardResourceUrl } from "@/types/creation";

export interface CardCoverThumbProps {
  cardId: string;
  /** `CardSummary.coverResourceId`；null/undefined 表示无封面。 */
  coverResourceId?: string | null;
  /** 卡片标题，用于 alt 与空态说明。 */
  title: string;
  /** 方形边长（px），默认 40。 */
  size?: number;
}

/**
 * 卡片列表/网格用的封面缩略图（紧凑版）。
 *
 * 与详情区的 `CardCoverImage` 区别：后者是大图 + 状态文字，放进列表项会过高；
 * 这里是固定方形小图，加载中/失败都退化为占位图标，不撑开布局、不显示文字，
 * 避免在密集列表里产生视觉噪音。
 *
 * 字节来源与详情区一致：主进程 `creation-asset` 只读协议，渲染进程只持有 URL。
 * 未设置封面时不渲染 img 元素，避免屏幕阅读器把「无封面」当成一张图片。
 */
export function CardCoverThumb({ cardId, coverResourceId, title, size = 40 }: CardCoverThumbProps) {
  const [failed, setFailed] = useState(false);
  const url = coverResourceId ? buildCardResourceUrl(cardId, coverResourceId) : null;

  if (!url || failed) {
    return (
      <span
        className="card-cover-thumb card-cover-thumb--empty"
        style={{ width: size, height: size }}
        aria-hidden="true"
        title={coverResourceId ? "封面读取失败" : `${title} 尚未设置封面`}
      >
        <ImageOff size={Math.max(12, Math.round(size * 0.4))} />
      </span>
    );
  }

  return (
    <img
      className="card-cover-thumb"
      src={url}
      alt={`${title} 封面`}
      width={size}
      height={size}
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}

export default CardCoverThumb;
