/**
 * window-service.ts — 窗口控件 IPC 封装
 *
 * 将 window.api?.window.* 操作集中到 Service 层，
 * 避免 UI 组件直接调用 window.api 破坏层次边界。
 */

/** 最小化窗口 */
export function minimizeWindow(): void {
  window.api?.window?.minimize();
}

/** 切换最大化/恢复 */
export function toggleMaximize(): void {
  window.api?.window?.toggleMaximize();
}

/** 关闭窗口 */
export function closeWindow(): void {
  window.api?.window?.close();
}

/** 订阅最大化状态变化事件，返回取消订阅函数 */
export function onMaximizedChange(handler: (isMaximized: boolean) => void): () => void {
  return window.api?.window?.onMaximizedChange(handler) ?? (() => {});
}