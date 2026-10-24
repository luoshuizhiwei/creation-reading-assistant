import { useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent, type RefObject } from "react";

/**
 * BD-130：弹层（role="dialog" / aria-modal）的键盘契约。
 *
 * 三件事在 BD-126~129 逐个用运行实测证明确实会坏，且每个弹层各写一遍太容易漏：
 * 1. Escape 关闭（焦点在弹层内任意控件上都成立，不只是输入框）；
 * 2. Tab 在弹层内首尾成环，不穿到后方页面（aria-modal 只是承诺，浏览器不自动兑现）；
 * 3. 关闭后把焦点还给打开前的元素（否则键盘用户落到 body，下次 Tab 从页首重走）。
 *
 * 返回的 onKeyDown 挂在弹层容器上；Esc 处理后会 stopPropagation，避免外层 document
 * 监听再关一次。
 */
export const FOCUSABLE_SELECTOR =
  "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";

export interface ModalKeyboardOptions {
  /** 弹层是否打开 */
  active: boolean;
  /** 弹层容器（Tab 环的作用域） */
  containerRef: RefObject<HTMLElement | null>;
  /** Escape 时执行 */
  onEscape: () => void;
  /** 打开后优先聚焦的元素；缺省为容器内首个可聚焦元素 */
  initialFocusRef?: RefObject<HTMLElement | null>;
}

export function useModalKeyboard({
  active,
  containerRef,
  onEscape,
  initialFocusRef
}: ModalKeyboardOptions): (event: ReactKeyboardEvent<HTMLElement>) => void {
  const escapeRef = useRef(onEscape);
  escapeRef.current = onEscape;

  useEffect(() => {
    if (!active) return undefined;
    const restoreTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = requestAnimationFrame(() => {
      const target = initialFocusRef?.current ?? containerRef.current?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      target?.focus();
    });
    return () => {
      cancelAnimationFrame(frame);
      if (restoreTo && restoreTo.isConnected) restoreTo.focus({ preventScroll: true });
    };
  }, [active, containerRef, initialFocusRef]);

  return (event: ReactKeyboardEvent<HTMLElement>) => {
    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      escapeRef.current();
      return;
    }
    if (event.key !== "Tab") return;
    const root = containerRef.current;
    if (!root) return;
    const items = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    if (items.length === 0) {
      event.preventDefault();
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    const activeEl = document.activeElement;
    if (event.shiftKey) {
      if (activeEl === first || !root.contains(activeEl)) {
        event.preventDefault();
        last.focus();
      }
    } else if (activeEl === last || !root.contains(activeEl)) {
      event.preventDefault();
      first.focus();
    }
  };
}
