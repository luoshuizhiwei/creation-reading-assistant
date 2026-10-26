// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { useRouteStatus } from "@/hooks/useRouteStatus";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-198 行为账：useRouteStatus 的单槽所有权协议。
 *
 * 根级常驻区（App 里那只 span）先于一切屏幕存在，播报全靠换文本；
 * routeStatus 是唯一槽位、多生产者（开书入口/阅读器页/统计页），
 * 所以清账必须只清自己写的文本——否则慢生产者会把新到达者的播报抹掉。
 */

const slot = () => useUIStore.getState().routeStatus;

beforeEach(() => {
  useUIStore.setState({ routeStatus: "" });
});

describe("BD-198：useRouteStatus 忙态写入与所有权清账", () => {
  it("busy 为真：挂载 effect 即写入文案", () => {
    renderHook(() => useRouteStatus(true, "正在读取统计…"));
    expect(slot()).toBe("正在读取统计…");
  });

  it("同文案重写不改槽：入口与页面接手共用常量时不产生第二次播报", () => {
    const first = renderHook(() => useRouteStatus(true, "正在打开 EPUB…"));
    expect(slot()).toBe("正在打开 EPUB…");
    // 阅读器页带着同一字符串接手：busy 仍真、文本相同——槽内值不变，
    // DOM 文本不变即不会重声（BD-196 同文不换、不换不重声）。
    const second = renderHook(() => useRouteStatus(true, "正在打开 EPUB…"));
    expect(slot()).toBe("正在打开 EPUB…");
    second.unmount();
    first.unmount();
  });

  it("busy 转假且槽内仍是本文案：清回空", () => {
    const { rerender } = renderHook(({ busy }) => useRouteStatus(busy, "正在读取统计…"), {
      initialProps: { busy: true }
    });
    expect(slot()).toBe("正在读取统计…");
    rerender({ busy: false });
    expect(slot()).toBe("");
  });

  it("busy 转假但槽内已是别人的文本：不碰（后到者接管）", () => {
    const { rerender } = renderHook(({ busy }) => useRouteStatus(busy, "正在读取统计…"), {
      initialProps: { busy: true }
    });
    useUIStore.getState().setRouteStatus("正在打开 EPUB…");
    rerender({ busy: false });
    expect(slot()).toBe("正在打开 EPUB…");
  });

  it("卸载：只清自己写的文案", () => {
    const { unmount } = renderHook(() => useRouteStatus(true, "正在打开 EPUB…"));
    expect(slot()).toBe("正在打开 EPUB…");
    unmount();
    expect(slot()).toBe("");
  });

  it("卸载时槽内被他人改写：保留他人播报", () => {
    const { unmount } = renderHook(() => useRouteStatus(true, "正在打开 EPUB…"));
    useUIStore.getState().setRouteStatus("正在读取统计…");
    unmount();
    expect(slot()).toBe("正在读取统计…");
  });

  it("挂载时本就不忙：不写槽，也绝不清空别人的文本", () => {
    useUIStore.getState().setRouteStatus("正在打开 EPUB…");
    const { unmount } = renderHook(() => useRouteStatus(false, "正在读取统计…"));
    expect(slot()).toBe("正在打开 EPUB…");
    unmount();
    expect(slot()).toBe("正在打开 EPUB…");
  });
});
