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
        <div className="grid h-full place-items-center p-6 text-center paper-shell">
          <div className="max-w-md rounded-2xl border border-red-200 bg-paper-panel p-6 shadow-paper">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
              <AlertTriangle size={24} />
            </div>
            <h2 className="paper-title text-lg font-semibold text-paper-ink">页面发生意外错误</h2>
            <p className="mt-2 text-xs leading-5 text-paper-muted">
              组件渲染时发生异常，已自动拦截以保护你的写作与阅读数据。
            </p>
            {this.state.error?.message && (
              <div className="mt-3 rounded-lg border border-red-100 bg-red-50/70 p-2.5 text-left font-mono text-xs text-red-700 break-all">
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
