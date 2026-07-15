import { useMemo, useState } from "react";
import { AlertCircle, CheckCircle2, Copy, Download, Trash2, XCircle } from "lucide-react";
import { MOBILE_APP_VERSION } from "../../../services/mobile-updates";
import { getMobileDeviceId } from "../../../services/mobile-storage";
import { addMobileLog, clearMobileLogs, exportMobileLogs, loadMobileLogs, type MobileLogEntry } from "../../../services/mobile-logger";
import { downloadTextFile } from "../../../utils/mobile-helpers";
import type { ProfileSubPage } from "../ProfilePage";

function formatLogTime(timestamp: string): string {
  try {
    return new Date(timestamp).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return timestamp;
  }
}

function getLevelIcon(level: MobileLogEntry["level"]) {
  if (level === "error") return <XCircle size={16} className="log-level-error" />;
  if (level === "warn") return <AlertCircle size={16} className="log-level-warn" />;
  return <CheckCircle2 size={16} className="log-level-info" />;
}

export function DiagnosticsPage({
  onSetActivePage,
  onMessage
}: {
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  onMessage: (value: string) => void;
}) {
  const [filter, setFilter] = useState<"all" | "error" | "warn" | "info">("all");
  const [expandedId, setExpandedId] = useState<string>();
  const [logs, setLogs] = useState(() => loadMobileLogs());
  const filteredLogs = useMemo(() => {
    if (filter === "all") return logs;
    return logs.filter((entry) => entry.level === filter);
  }, [logs, filter]);

  const diagnostics = useMemo(() => {
    const deviceId = getMobileDeviceId();
    return {
      appVersion: MOBILE_APP_VERSION,
      userAgent: typeof navigator !== "undefined" ? navigator.userAgent : "",
      deviceId: `${deviceId.slice(0, 8)}…`,
      platform: (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData?.platform ?? navigator.platform,
      screen: `${window.screen.width}×${window.screen.height}`,
      dpr: window.devicePixelRatio,
      language: navigator.language,
      webview: /wv|WebView/i.test(navigator.userAgent) ? "是" : "否"
    };
  }, []);

  const exportLogs = async () => {
    const content = exportMobileLogs();
    try {
      await downloadTextFile(`creation-reading-assistant-logs-${new Date().toISOString().slice(0, 10)}.json`, content);
      onMessage("日志已导出。导出文件不包含完整正文、API Key 或密码。");
    } catch (error) {
      onMessage(`日志导出失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const clearLogs = () => {
    clearMobileLogs();
    setLogs([]);
    setExpandedId(undefined);
    onMessage("已清空本地日志。");
  };

  const copyErrorCode = (code?: string) => {
    if (!code) return;
    void navigator.clipboard.writeText(code).then(() => onMessage("错误码已复制。"));
  };

  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">帮助</p>
          <h1>日志与诊断</h1>
        </div>
      </header>

      <section className="subpage-card diagnostics-summary-card">
        <h4>运行环境</h4>
        <dl className="diagnostics-list">
          <div><dt>应用版本</dt><dd>{diagnostics.appVersion}</dd></div>
          <div><dt>设备标识</dt><dd>{diagnostics.deviceId}</dd></div>
          <div><dt>平台</dt><dd>{diagnostics.platform || "未知"}</dd></div>
          <div><dt>屏幕</dt><dd>{diagnostics.screen} · DPR {diagnostics.dpr}</dd></div>
          <div><dt>语言</dt><dd>{diagnostics.language}</dd></div>
          <div><dt>WebView</dt><dd>{diagnostics.webview}</dd></div>
        </dl>
      </section>

      <section className="subpage-card">
        <div className="log-filter-row">
          <span className="subtle">共 {logs.length} 条日志，{logs.filter((entry) => entry.level === "error").length} 条错误</span>
          <div className="log-filter-tabs">
            {(["all", "error", "warn", "info"] as const).map((value) => (
              <button
                key={value}
                className={filter === value ? "active" : ""}
                onClick={() => setFilter(value)}
              >
                {value === "all" ? "全部" : value === "error" ? "错误" : value === "warn" ? "警告" : "信息"}
              </button>
            ))}
          </div>
        </div>

        {filteredLogs.length === 0 ? (
          <p className="empty-hint">暂无日志。应用运行中产生的导入、同步、阅读问题会自动记录到这里。</p>
        ) : (
          <ul className="log-entry-list">
            {filteredLogs.map((entry) => (
              <li key={entry.id} className={`log-entry ${entry.level}`}>
                <button
                  className="log-entry-header"
                  onClick={() => setExpandedId((current) => (current === entry.id ? undefined : entry.id))}
                >
                  {getLevelIcon(entry.level)}
                  <span className="log-entry-time">{formatLogTime(entry.timestamp)}</span>
                  <span className="log-entry-module">{entry.module}</span>
                  {entry.code && <span className="log-entry-code">{entry.code}</span>}
                </button>
                {expandedId === entry.id && (
                  <div className="log-entry-body">
                    <p>{entry.message}</p>
                    {entry.code && (
                      <button className="ghost-button" onClick={() => copyErrorCode(entry.code)}>
                        <Copy size={14} /> 复制错误码
                      </button>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}

        <div className="button-row settings-actions">
          <button onClick={() => void exportLogs()} disabled={logs.length === 0}>
            <Download size={17} /> 导出日志
          </button>
          <button className="secondary-button" onClick={clearLogs} disabled={logs.length === 0}>
            <Trash2 size={17} /> 清空日志
          </button>
        </div>
      </section>
    </div>
  );
}

export { addMobileLog };
