import { Download, Wifi, RefreshCw, AlertCircle, CheckCircle2, Clock, Upload, FileText } from "lucide-react";
import type { ProfileSubPage } from "../ProfilePage";
import type { SyncResultDetail } from "../../../hooks/useMobileSync";

function formatTimestamp(iso: string): string {
  try {
    return new Date(iso).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return iso;
  }
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}秒`;
  return `${Math.floor(ms / 60_000)}分${Math.round((ms % 60_000) / 1000)}秒`;
}

export function SyncPage({
  pairingText,
  paired,
  onPairingTextChange,
  onConnectLan,
  onScanQr,
  onSyncDesktop,
  pendingDownloadCount,
  syncLogs,
  lastSyncResult,
  syncing,
  onRetryFailedUploads,
  onSetActivePage
}: {
  pairingText: string;
  paired: boolean;
  onPairingTextChange: (value: string) => void;
  onConnectLan: () => void;
  onScanQr: () => void;
  onSyncDesktop: () => void;
  pendingDownloadCount: number;
  syncLogs: string[];
  lastSyncResult?: SyncResultDetail;
  syncing?: boolean;
  onRetryFailedUploads?: () => void;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  const hasFailedUploads = lastSyncResult?.failedItems.some((item) => item.type === "upload-book") ?? false;
  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">局域网同步</p>
          <h1>同步状态</h1>
        </div>
      </header>

      <section className="subpage-card">
        <div className="sync-status-summary">
          <article>
            <Wifi size={18} />
            <span>{paired ? "已连接电脑" : "未连接电脑"}</span>
          </article>
          <article>
            <Download size={18} />
            <span>{pendingDownloadCount} 本待下载正文</span>
          </article>
        </div>
        <p className="subtle">电脑端开启同步服务后，可以扫码或粘贴配对 URL。同步只更新书架、灵感和进度，书籍正文可在书架按需下载。每次同步前会自动备份本地数据。</p>
        <textarea value={pairingText} onChange={(event) => onPairingTextChange(event.target.value)} placeholder="粘贴电脑端配对 URL 或二维码载荷" />
        <div className="button-row settings-actions">
          <button onClick={onScanQr}>扫码</button>
          <button onClick={onConnectLan}>连接电脑</button>
          <button disabled={!paired || syncing} onClick={onSyncDesktop}>
            {syncing ? "同步中..." : "立即同步"}
          </button>
        </div>
      </section>

      {lastSyncResult && (
        <section className="subpage-card sync-result-detail">
          <header className="sync-result-header">
            {lastSyncResult.success ? <CheckCircle2 size={18} className="sync-success-icon" /> : <AlertCircle size={18} className="sync-error-icon" />}
            <div>
              <h3>{lastSyncResult.success ? "最近一次同步成功" : "最近一次同步失败"}</h3>
              <p className="sync-result-time">
                <Clock size={12} /> {formatTimestamp(lastSyncResult.timestamp)} · 耗时 {formatDuration(lastSyncResult.durationMs)}
              </p>
            </div>
          </header>

          {lastSyncResult.success && (
            <div className="sync-result-stats">
              <div className="sync-result-stat-group">
                <span className="sync-result-stat-title"><Upload size={14} /> 上传</span>
                <ul>
                  <li>{lastSyncResult.uploaded.inspirations} 条灵感</li>
                  <li>{lastSyncResult.uploaded.books} 本书</li>
                  <li>{lastSyncResult.uploaded.progress} 条进度</li>
                  <li>{lastSyncResult.uploaded.bookFiles} 个正文文件</li>
                </ul>
              </div>
              <div className="sync-result-stat-group">
                <span className="sync-result-stat-title"><Download size={14} /> 下载</span>
                <ul>
                  <li>{lastSyncResult.downloaded.inspirations} 条灵感</li>
                  <li>{lastSyncResult.downloaded.books} 本书</li>
                  <li>{lastSyncResult.downloaded.progress} 条进度</li>
                  <li>{lastSyncResult.downloaded.sessions} 条阅读会话</li>
                </ul>
              </div>
              <div className="sync-result-stat-group">
                <span className="sync-result-stat-title"><FileText size={14} /> 待处理</span>
                <ul>
                  <li>{lastSyncResult.pendingDownloadCount} 本待下载正文</li>
                </ul>
              </div>
            </div>
          )}

          {lastSyncResult.failedItems.length > 0 && (
            <div className="sync-failed-list">
              <h4 className="sync-failed-title">
                <AlertCircle size={14} /> {lastSyncResult.failedItems.length} 项失败
              </h4>
              <ul>
                {lastSyncResult.failedItems.map((item, index) => (
                  <li key={index} className="sync-failed-item">
                    <span className="sync-failed-type">
                      {item.type === "upload-book" ? "上传正文" :
                       item.type === "download-book" ? "下载正文" :
                       item.type === "push" ? "推送数据" :
                       item.type === "pull" ? "拉取数据" : "配对"}
                    </span>
                    {item.title && <span className="sync-failed-book">《{item.title}》</span>}
                    <span className="sync-failed-reason">{item.reason}</span>
                  </li>
                ))}
              </ul>
              {hasFailedUploads && onRetryFailedUploads && (
                <button className="secondary-button sync-retry-btn" disabled={syncing} onClick={onRetryFailedUploads}>
                  <RefreshCw size={14} /> 重试上传失败项
                </button>
              )}
            </div>
          )}

          {lastSyncResult.snapshotBackupKey && (
            <p className="sync-backup-hint subtle">同步前已自动备份本地数据，如同步后数据异常可联系开发者恢复。</p>
          )}
        </section>
      )}

      <section className="subpage-card">
        <details className="sync-log-panel">
          <summary>同步日志 / 最近一次错误</summary>
          {syncLogs.length ? syncLogs.map((item) => <p key={item}>{item}</p>) : <p>暂无同步日志。</p>}
        </details>
      </section>
    </div>
  );
}
