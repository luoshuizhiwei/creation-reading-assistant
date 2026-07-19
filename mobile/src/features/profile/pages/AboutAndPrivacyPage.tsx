import { useMemo, useRef, useState } from "react";
import { Book, Download, FileDown, FileUp, Info, RefreshCw, Shield, Sparkles, Wifi, X, Clock, ExternalLink, Trash2, Database } from "lucide-react";
import { formatBytes, downloadTextFile } from "../../../utils/mobile-helpers";
import {
  exportMobileSnapshot,
  importMobileSnapshot,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import {
  MOBILE_APP_VERSION,
  MOBILE_LICENSE_URL,
  MOBILE_RELEASES_URL,
  MOBILE_SOURCE_ARCHIVE_URL
} from "../../../services/mobile-updates";
import { buildStorageBreakdown, clearReaderContentCache } from "../profile-helpers";
import type { useUpdateCheck } from "../hooks/useUpdateCheck";
import type { ProfileSubPage } from "../ProfilePage";

function formatLastCheckLabel(timestamp: number): string {
  if (!timestamp) return "从未检查";
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return "刚刚检查过";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前检查过`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前检查过`;
  const date = new Date(timestamp);
  return `${date.getMonth() + 1}月${date.getDate()}日 检查过`;
}

export function AboutAndPrivacyPage({
  activePage,
  snapshot,
  onSnapshotChange,
  onMessage,
  update,
  onSetActivePage,
  onConfirm
}: {
  activePage: ProfileSubPage;
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  update: ReturnType<typeof useUpdateCheck>;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
}) {
  const importSnapshotInputRef = useRef<HTMLInputElement>(null);
  const { updateInfo, updateBusy, lastCheckAt, showUpdateBanner, checkUpdate, openUpdate, dismissUpdate } = update;
  const [storageRevision, setStorageRevision] = useState(0);
  const storage = useMemo(() => buildStorageBreakdown(snapshot), [snapshot, storageRevision]);

  const renderHeader = (eyebrow: string, title: string) => (
    <header className="mobile-header row-header subpage-header">
      <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
        ← 返回
      </button>
      <div>
        <p className="mini-label">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
    </header>
  );

  const exportSnapshot = async () => {
    try {
      const content = await exportMobileSnapshot(snapshot);
      await downloadTextFile(`creation-reading-assistant-mobile-${new Date().toISOString().slice(0, 10)}.json`, content);
      onMessage("已导出手机端数据快照。");
    } catch (error) {
      onMessage(`导出失败：${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const importSnapshotFile = async (file?: File) => {
    if (!file) return;
    try {
      const text = await file.text();
      const next = await importMobileSnapshot(snapshot, text);
      onSnapshotChange(next);
      onMessage("已导入并合并数据快照；同 ID 数据会自动去重。");
    } catch (error) {
      onMessage(`导入失败：${error instanceof Error ? error.message : "文件不是有效的数据快照"}`);
    } finally {
      if (importSnapshotInputRef.current) importSnapshotInputRef.current.value = "";
    }
  };

  const handleClearCache = () => {
    onConfirm({
      title: "清理缓存",
      message: `确认清理阅读器正文缓存？将删除 ${storage.cacheEntryCount} 条缓存条目（约 ${formatBytes(storage.cacheBytes)}），释放后可重新生成。不会删除正式书籍、进度、书签和笔记。`,
      onConfirm: () => {
        const result = clearReaderContentCache();
        setStorageRevision((value) => value + 1);
        onMessage(`已清理 ${result.removed} 条缓存，释放约 ${formatBytes(result.freedBytes)}。`);
      }
    });
  };

  if (activePage === "storage") {
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("数据与存储", "存储管理")}
        <section className="profile-metric-strip">
          <article><strong>{storage.totalBooks}</strong><span>书籍</span></article>
          <article><strong>{storage.downloadedBooks}</strong><span>已落地正文</span></article>
          <article><strong>{formatBytes(storage.indexBytes)}</strong><span>索引大小</span></article>
        </section>

        <section className="subpage-card storage-detail-card">
          <h4>格式分布</h4>
          <div className="storage-format-grid">
            <article><strong>{storage.txtCount}</strong><span>TXT</span></article>
            <article><strong>{storage.mdCount}</strong><span>Markdown</span></article>
            <article><strong>{storage.epubCount}</strong><span>EPUB</span></article>
          </div>
          <p className="subtle">格式统计来自书架元数据，不包含原始外部文件。</p>
        </section>

        <section className="subpage-card storage-cache-card">
          <h4>缓存</h4>
          <div className="storage-cache-row">
            <span>阅读器正文缓存</span>
            <strong>{storage.cacheEntryCount} 条 · {formatBytes(storage.cacheBytes)}</strong>
          </div>
          <p className="subtle">缓存可在重新打开书籍时重新生成；清理不影响正式数据。</p>
          <button className="secondary-button" onClick={handleClearCache} disabled={storage.cacheEntryCount === 0}>
            <Trash2 size={17} /> 清理缓存
          </button>
        </section>

        <section className="subpage-card">
          <p className="subtle">导出会保存灵感、书库元数据、进度、阅读记录、笔记、标签、分类和同步账号信息；不会导出 AI Key、WebDAV 密码 / token 或设备私有路径。</p>
          <input ref={importSnapshotInputRef} hidden type="file" accept="application/json,.json" onChange={(event) => void importSnapshotFile(event.currentTarget.files?.[0])} />
          <div className="button-row settings-actions">
            <button onClick={() => void exportSnapshot()}><FileDown size={17} />导出数据</button>
            <button onClick={() => importSnapshotInputRef.current?.click()}><FileUp size={17} />导入数据</button>
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "privacy") {
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("隐私安全", "隐私安全")}
        <section className="subpage-card privacy-list">
          <article><Shield size={19} /><div><strong>本地优先</strong><p>没有账号服务器。书籍、灵感、进度和笔记默认保存在手机本地。</p></div></article>
          <article><Wifi size={19} /><div><strong>同步可控</strong><p>局域网同步需要你手动连接电脑；WebDAV 需要你主动配置地址。</p></div></article>
          <article><Sparkles size={19} /><div><strong>密钥隔离</strong><p>AI Key 和 WebDAV 密码 / token 仅保存在应用本地沙箱，不参与电脑同步、WebDAV 同步或数据导出。</p></div></article>
          <article><Database size={19} /><div><strong>路径隔离</strong><p>本地文件路径、readerPreview、临时 URI 等设备私有字段不会进入同步 payload。</p></div></article>
        </section>
      </div>
    );
  }

  if (activePage === "about") {
    return (
      <div className="screen-stack profile-subpage">
        {renderHeader("关于", "关于")}
        <section className="subpage-card about-card">
          <div className="app-info-icon large">
            <Book size={32} />
          </div>
          <h2>创作阅读助手</h2>
          <p>Android 端 · 本地优先 · 灵感中心特色版</p>
          <div className="about-list">
            <span>版本：{MOBILE_APP_VERSION}</span>
            <span>支持：TXT / Markdown / EPUB</span>
            <span>同步：电脑局域网 / WebDAV</span>
          </div>
        </section>

        <section className="subpage-card">
          <div>
            <p className="mini-label">开源与许可证</p>
            <h2>Android 端按 GPL-3.0 发布</h2>
            <p className="subtle">
              移动端包含基于 Legado / 阅读Sigma 固定版本适配的本地阅读内核。每个正式 APK 版本都会同时提供对应源代码归档、许可证与修改说明。
            </p>
          </div>
          <div className="button-row settings-actions">
            <a className="secondary-button" href={MOBILE_SOURCE_ARCHIVE_URL} target="_blank" rel="noopener noreferrer">
              <FileDown size={17} />下载对应源码
            </a>
            <a className="secondary-button" href={MOBILE_LICENSE_URL} target="_blank" rel="noopener noreferrer">
              <ExternalLink size={17} />查看 GPL-3.0
            </a>
          </div>
        </section>

        {showUpdateBanner && updateInfo && (
          <section className="subpage-card update-banner-card" role="status">
            <button className="update-banner-close" onClick={dismissUpdate} aria-label="忽略此版本">
              <X size={16} />
            </button>
            <div className="update-banner-icon">
              <RefreshCw size={20} />
            </div>
            <div className="update-banner-body">
              <strong>发现新版本 v{updateInfo.latestVersion}</strong>
              <p>当前版本 {updateInfo.currentVersion}，可以去下载新版安装包。</p>
              {updateInfo.apkName && <span className="update-banner-apk">{updateInfo.apkName}</span>}
            </div>
            <button className="update-banner-action" onClick={openUpdate}>
              <Download size={16} />
              去下载
            </button>
          </section>
        )}

        <section className="subpage-card update-card">
          <div>
            <p className="mini-label">应用更新</p>
            <h2>{updateInfo?.hasUpdate ? `发现 v${updateInfo.latestVersion}` : "检查新版本"}</h2>
            <p className="subtle">
              {updateInfo
                ? updateInfo.hasUpdate
                  ? `当前版本 ${updateInfo.currentVersion}，新版安装包可从应用内打开下载。`
                  : updateInfo.checkFailed
                    ? "更新源暂时不可访问，可以打开发布页手动查看。"
                    : `当前版本 ${updateInfo.currentVersion}，已经是 GitHub Release 上的最新版本。`
                : "以后发布新版后，可以在这里检查并打开安装包下载；Android 仍会要求你确认安装。"}
            </p>
            <p className="update-last-check">
              <Clock size={13} />
              {formatLastCheckLabel(lastCheckAt)}
            </p>
          </div>
          {updateInfo?.hasUpdate && (
            <div className="update-release-note">
              <strong>{updateInfo.apkName || `v${updateInfo.latestVersion} 更新内容`}</strong>
              <div className="update-release-note-body">
                {updateInfo.notes.split("\n").map((line, index) => (
                  <p key={index}>{line || "\u00A0"}</p>
                ))}
              </div>
            </div>
          )}
          <div className="button-row settings-actions">
            <button disabled={updateBusy} onClick={() => void checkUpdate()}>
              <RefreshCw size={17} />{updateBusy ? "检查中…" : "检查更新"}
            </button>
            <button className="secondary-button" disabled={!updateInfo} onClick={openUpdate}>
              <Download size={17} />下载新版
            </button>
          </div>
          <a
            className="update-release-link"
            href={updateInfo?.releaseUrl || MOBILE_RELEASES_URL}
            target="_blank"
            rel="noopener noreferrer"
          >
            <ExternalLink size={13} />
            打开 GitHub Release 页面
          </a>
        </section>
      </div>
    );
  }

  return null;
}
