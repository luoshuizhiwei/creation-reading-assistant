import { useEffect } from "react";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-198：路由级到达播报的常驻落点判据。
 *
 * 「正在读取统计…」「正在打开 EPUB…」这类到页忙碌态此前住在「正在挂载的页面」
 * 内部——插入即带内容的 role="status" 是哑的（BD-175 判据）：StatsPage 的加载根
 * 与 EPUB 覆盖层在 BD-193/195 摘掉死装饰后再没有任何播报（两处的「另案」注记即
 * 此）。修法是全局唯一一枚常驻区（挂在 App 根、先于一切屏幕存在），忙态经
 * ui-store.routeStatus 写入——区先存在、换文本才发声，与 BD-193/196 同族。
 *
 * 所有权约定（单槽多生产者，防互踩）：
 * - busy 为真：effect 写入文案（effect 晚于插入提交，换文本有效；同文案重写
 *   不改 DOM 文本，不会重声——入口动作与页面接手共用一个常量即靠此去重）；
 * - busy 转假：仅当槽内仍是本文案时清回空（到达即清，可见内容接手）；
 * - 卸载：同样只清自己写的文案，别人的播报不碰。
 */
export function useRouteStatus(busy: boolean, text: string) {
  const setRouteStatus = useUIStore((state) => state.setRouteStatus);
  useEffect(() => {
    if (busy) {
      setRouteStatus(text);
    } else if (useUIStore.getState().routeStatus === text) {
      setRouteStatus("");
    }
    return () => {
      if (useUIStore.getState().routeStatus === text) setRouteStatus("");
    };
  }, [busy, text, setRouteStatus]);
}

/** 开书（EPUB）忙碌播报文案：入口动作与阅读器页共用一个常量，保证去重与接手成立。 */
export const EPUB_OPENING_STATUS = "正在打开 EPUB…";
/** 创作统计读取中播报文案：StatsPage 独用。 */
export const STATS_LOADING_STATUS = "正在读取统计…";
