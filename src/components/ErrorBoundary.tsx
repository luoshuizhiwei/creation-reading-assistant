import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, RefreshCw, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onReset?: () => void;
}

interface State {
  hasError: boolean;
  error?: Error;
}

function redactPaths(value: string): string {
  return value.replace(/(?:[A-Za-z]:[\\/]|[/\\](?:Users|home|Volumes|root)[/\\])[^"\s'()`]+/g, "<redacted-path>");
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
  }

  public handleReset = () => {
    this.setState({ hasError: false, error: undefined });
    this.props.onReset?.();
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        // 批次 C：这块兜底页整体用 Tailwind 默认调色板的 red-100/200/500/600/700，
        // 那批颜色不随主题翻转，夜校里等于把晨校的浅岛原样画在深色面板上。
        // 实测最糟的是报错详情条：text-red-700 压在 bg-red-50/70 叠夜校面板上 3.14:1，
        // 图标底 bg-red-100 + text-red-600 两主题都只有 3.95:1（12px 正文，AA 要 4.5）。
        // 改走校样红令牌族：--proof-mark 压 --proof-tint 晨 4.85 / 夜 5.11。
        // 边框不能写 border-[color:var(--proof-mark)]/30（Tailwind 无法给 var() 套 alpha
        // 修饰符，整条声明会被静默丢弃），所以用 color-mix 混进 --separator。
        <div className="grid h-full place-items-center p-6 text-center paper-shell">
          {/* 批次 BD-19（阴影分级；守卫把这一笔的裁定权交给本批，SHADOW_BUDGET 注释里
           * 记着原话「要收它得先决定故障卡的红边由谁承担」）：红边由 border 自己承担——
           * 它走校样红令牌族，是「出错了」这层语义的唯一载体，不能撤；
           * 因此静止卡档 --shadow-1 在这里用不了（它自带 0 0 0 1px separator 环，
           * 会和这条边拼成批次 AE 说的 2px 双线）。投影归 --shadow-3：这张卡在结构上是
           * 接管整个视图区域的兜底面板（max-w-md、居中、压在正文之上），§5.3 里为
           * 「接管面板」准备的就是 3 档；原值 shadow-paper = `0 18px 50px rgba(34,38,48,.1)`
           * 的第一层偏移/模糊（18px/50px）本来就落在 3 档（24px/64px）附近，观感连续；
           * 2 档（0 4px 12px）是给菜单那种小控件的，48px 见方的卡用它只会看起来贴在地上。
           * ⚠ 必须用属性形式：方括号工具类 shadow-[var(...)] 只产 --tw-shadow-color、
           * 画不出阴影（批次 K 的幻影判据硬红它），写它等于假装还债。 */}
          <div className="max-w-md rounded-md border border-[color:color-mix(in_srgb,var(--proof-mark)_30%,var(--separator))] bg-paper-panel p-6 [box-shadow:var(--shadow-3)]">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[color:var(--proof-tint)] text-[color:var(--proof-mark)]">
              <AlertTriangle size={24} />
            </div>
            <h2 className="paper-title text-base font-semibold text-paper-ink">页面发生意外错误</h2>
            <p className="mt-2 text-xs leading-5 text-paper-muted">
              组件渲染时发生异常，已自动拦截以保护你的写作与阅读数据。
            </p>
            {this.state.error?.message && (
              <div className="mt-3 rounded-md border border-[color:color-mix(in_srgb,var(--proof-mark)_30%,var(--separator))] bg-[color:var(--proof-tint)] p-2.5 text-left font-mono text-xs text-[color:var(--proof-mark)] break-all">
                {redactPaths(this.state.error.message)}
              </div>
            )}
            <div className="mt-5 flex justify-center gap-2">
              <Button variant="secondary" onClick={this.handleReset}>
                <RefreshCw size={14} />
                重试当前页面
              </Button>
              <Button
                onClick={() => {
                  this.handleReset();
                  window.location.reload();
                }}
              >
                <RotateCcw size={14} />
                重新加载应用
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
