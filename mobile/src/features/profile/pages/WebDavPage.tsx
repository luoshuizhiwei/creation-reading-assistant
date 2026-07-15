import { CheckCircle2, Download, Upload, Wifi } from "lucide-react";
import type { useWebDavSettings } from "../hooks/useWebDavSettings";
import type { ProfileSubPage } from "../ProfilePage";

export function WebDavPage({
  webdav,
  onSetActivePage
}: {
  webdav: ReturnType<typeof useWebDavSettings>;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  const {
    webdav: webdavState,
    webdavError,
    hasWebDavPassword,
    handleEndpointChange,
    setWebdav,
    setWebdavTouched,
    testWebDav,
    uploadWebDav,
    downloadWebDav,
    clearWebDavCredential
  } = webdav;

  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">跨设备备份</p>
          <h1>WebDAV 设置</h1>
        </div>
      </header>

      <section className="subpage-card">
        <p className="subtle">同步目录固定为 .creation-reading-assistant/，会上传 manifest、records 和 books。WebDAV 密码 / token 仅保存在应用本地沙箱，AI Key 不参与同步。</p>
        <div className="input-field">
          <input
            value={webdavState.endpoint}
            onChange={(event) => handleEndpointChange(event.target.value)}
            placeholder="https://example.com/dav"
            className={webdavError ? "input-error" : ""}
          />
          {webdavError && <p className="input-error-text">{webdavError}</p>}
        </div>
        <input value={webdavState.username} onChange={(event) => {
          setWebdavTouched(true);
          setWebdav((current) => ({ ...current, username: event.target.value }));
        }} placeholder="用户名" />
        <input type="password" value={webdavState.password} onChange={(event) => {
          setWebdavTouched(true);
          setWebdav((current) => ({ ...current, password: event.target.value }));
        }} placeholder={hasWebDavPassword ? "已保存密码 / token；留空则继续使用" : "密码或 token"} />
        <div className="setting-check-row">
          <CheckCircle2 size={18} />
          <span>{hasWebDavPassword ? "已在应用本地沙箱保存 WebDAV 密码 / token；不会导出或参与同步。" : "还没有保存 WebDAV 密码 / token。"}</span>
        </div>
        <div className="button-row settings-actions">
          <button onClick={() => void testWebDav()} disabled={!!webdavError || !webdavState.endpoint}><Wifi size={17} />测试</button>
          <button onClick={() => void uploadWebDav()} disabled={!!webdavError || !webdavState.endpoint}><Upload size={17} />上传</button>
          <button onClick={() => void downloadWebDav()} disabled={!!webdavError || !webdavState.endpoint}><Download size={17} />下载</button>
          <button className="secondary-button" onClick={() => void clearWebDavCredential()} disabled={!hasWebDavPassword && !webdavState.password}>清除凭证</button>
        </div>
      </section>
    </div>
  );
}
