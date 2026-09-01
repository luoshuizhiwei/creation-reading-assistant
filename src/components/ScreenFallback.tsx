/**
 * 屏级 lazy 加载期间的骨架占位。
 *
 * 只在 React.lazy 尚未 resolve 对应屏 chunk 的窗口期出现。Electron 从本地
 * file:// 加载，这个窗口通常只有几十毫秒，但如果不给占位，切屏瞬间会白屏
 * 或发生布局跳动——对「点开就想写字」的工具来说，闪一下比慢一点更伤。
 *
 * 刻意沿用页面主体的留白节奏与 paper-* 设计令牌，让 fallback → 真实内容
 * 的过渡在视觉上连续，而不是"白屏 → 内容"的硬切。
 */
export function ScreenFallback() {
  return (
    <div className="flex h-full w-full flex-col gap-5 p-6" aria-busy="true">
      <span className="sr-only">正在加载页面…</span>
      <div className="flex flex-col gap-2">
        <div className="h-6 w-52 animate-pulse rounded-md bg-paper-soft" />
        <div className="h-4 w-72 animate-pulse rounded-md bg-paper-soft/60" />
      </div>
      <div className="flex flex-col gap-3">
        <div className="h-4 w-full animate-pulse rounded bg-paper-soft/40" />
        <div className="h-4 w-11/12 animate-pulse rounded bg-paper-soft/40" />
        <div className="h-4 w-full animate-pulse rounded bg-paper-soft/40" />
        <div className="h-4 w-10/12 animate-pulse rounded bg-paper-soft/40" />
        <div className="h-4 w-8/12 animate-pulse rounded bg-paper-soft/40" />
      </div>
    </div>
  );
}
