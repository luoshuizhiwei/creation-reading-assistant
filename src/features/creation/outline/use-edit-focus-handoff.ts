import { useCallback, useEffect, useRef } from "react";
import type { RefObject } from "react";

/**
 * BD-251（WCAG 2.4.3 焦点顺序）：大纲编辑控件按下之后，焦点不许跌回 body。
 *
 * 与 BD-220 的删除交接正相反：那一支是**条目被删掉、整行卸载**；这里是条目还在、
 * 控件也还在 DOM 里，只是它在事务里被禁用（卡板 runEdit 的 busy）或到了排序边界
 * 而永久禁用（树的重排钮），浏览器当场把焦点吐回 body，事务结束后没人接回来。
 * 运行时实测（scripts/audit-edit-focus.mjs 的红基线 /tmp/bd251-red.log）：树·下移卷、
 * 卡板·章节状态下拉、卡板·上移/下移场景「焦点=body｜原控件还在 DOM=true」。
 *
 * 交接口径（与 BD-220 同族：原位置优先、就近退让、最后退容器）：
 *   原控件还能聚焦 → 交回它；否则同簇里第一颗可用控件 → 再否则本行主控件 → 再否则容器。
 * 只在「刚在本容器里动过手」且「焦点确实落在 body」时才接，绝不抢用户自己挪走的焦点，
 * 也不碰被弹层接走的焦点（那是 3.2.2／BD-246 的题）。
 *
 * 为什么要重试阶梯：卡板的 busy 与结构刷新是两条异步线，谁先落地不定。禁用还没解除时
 * 就把焦点交给邻居，等 busy 散了原控件明明能聚焦却回不去了。所以先试着交回原控件，
 * 交不回去就等下一档；最后一档才认「它是真的永久禁用了」，走就近退让。
 */
const HANDOFF_ATTEMPTS_MS = [0, 100, 300, 800, 1500];
// 交接窗口：动手之后 4 秒内的结构刷新都算这一次编辑的余波。卡板在 busy 解除之后还会
// 再刷一次结构，重排卡片时 DOM 节点被搬动，刚接回去的焦点会再掉一次（实测：只接一次
// 时「卡板·上移场景」仍红）。窗口过期就彻底放手，不去抢用户后来自己挪走的焦点。
const HANDOFF_WINDOW_MS = 4000;

export function useEditFocusHandoff(
  containerRef: RefObject<HTMLElement | null>,
  settled: readonly unknown[],
  /** 本行的宿主选择器，由调用方给（树给 "li"、卡板给卡片/分组）。类名留在调用方文件里：
   *  设计令牌守卫按「哪个模块提到了这个类名」登记宿主，并要求宿主沿 import 链够得上
   *  胜者样式表；把类名抄进本文件会凭空多出一个够不着 outline-reorg.css 的宿主，
   *  把「挂载覆盖」前提整条判失效（实测：注入电池 BD-40 那颗牙因此哑掉）。 */
  rowSelector: string
) {
  const pendingRef = useRef<{ el: HTMLElement; at: number } | null>(null);

  // 挂在容器的捕获阶段：键盘（Enter/空格）、指针、以及 select 的改值三条路都记下
  // 「动手时握着哪颗控件」——selectOption 这类只发 change 的输入也必须算进来。
  const capture = useCallback(() => {
    const container = containerRef.current;
    const active = document.activeElement;
    if (!container || !(active instanceof HTMLElement)) return;
    if (!container.contains(active)) return;
    if (!active.matches("button, select, input, textarea")) return;
    pendingRef.current = { el: active, at: Date.now() };
  }, [containerRef]);

  useEffect(() => {
    const pending = pendingRef.current;
    if (!pending) return;
    if (Date.now() - pending.at > HANDOFF_WINDOW_MS) {
      pendingRef.current = null;
      return;
    }
    const held = pending.el;
    const container = containerRef.current;
    if (!container) return;
    const focusable = (el: Element | null): el is HTMLElement =>
      el instanceof HTMLElement && el.isConnected && !(el as HTMLButtonElement).disabled;
    const atBody = () => {
      const active = document.activeElement;
      return !active || active === document.body || active === document.documentElement;
    };
    if (!atBody()) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const handOff = () => {
      const cluster = held.parentElement;
      const sibling = cluster
        ? Array.from(cluster.querySelectorAll<HTMLElement>("button, select, input")).find(focusable)
        : undefined;
      const row = held.closest(rowSelector);
      const rowMain = row?.querySelector<HTMLElement>("button, select, input") ?? null;
      const target = sibling ?? (focusable(rowMain) ? rowMain : null) ?? container;
      target.focus({ preventScroll: true });
    };
    HANDOFF_ATTEMPTS_MS.forEach((delay, index) => {
      timers.push(setTimeout(() => {
        if (!atBody()) {
          timers.forEach(clearTimeout);
          return;
        }
        if (focusable(held)) {
          timers.forEach(clearTimeout);
          held.focus({ preventScroll: true });
          return;
        }
        if (index === HANDOFF_ATTEMPTS_MS.length - 1) handOff();
      }, delay));
    });
    return () => timers.forEach(clearTimeout);
    // settled 由调用方给（树给 [outline]、卡板给 [outline, busy]）：结构或事务状态一变就
    // 重试一轮。卡板必须带上 outline——busy 解除之后还有一次结构刷新，重排卡片时
    // DOM 节点被搬动，刚接回去的焦点会再掉一次（实测：只盯 busy 时卡板·上移场景仍红）。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [containerRef, rowSelector, ...settled]);

  return { onPointerDownCapture: capture, onKeyDownCapture: capture, onChangeCapture: capture, onClickCapture: capture };
}