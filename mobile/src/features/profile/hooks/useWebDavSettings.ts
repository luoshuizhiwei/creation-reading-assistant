import { useEffect, useState } from "react";
import {
  clearWebDavPasswordSecret,
  loadWebDavPasswordSecret,
  saveWebDavPasswordSecret
} from "../../../services/mobile-webdav-secrets";
import {
  getMobileDeviceId,
  saveMobileSnapshot,
  saveSyncAccount,
  type MobileSnapshot
} from "../../../services/mobile-storage";
import { downloadWebDavSnapshot, testWebDavConnection, uploadWebDavSnapshot } from "../../../sync/webdav-sync";
import type { SyncAccount } from "../../../types/mobile";
import type { ProfileSubPage } from "../ProfilePage";

export function useWebDavSettings({
  snapshot,
  onSnapshotChange,
  onMessage,
  activePage
}: {
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  activePage: ProfileSubPage | undefined;
}) {
  const [webdav, setWebdav] = useState({ endpoint: "", username: "", password: "" });
  const [webdavError, setWebdavError] = useState("");
  const [webdavTouched, setWebdavTouched] = useState(false);
  const [hasWebDavPassword, setHasWebDavPassword] = useState(false);

  const validateWebDavEndpoint = (url: string): string => {
    if (!url) return "";
    if (!/^https?:\/\//i.test(url)) return "地址必须以 http:// 或 https:// 开头";
    try {
      new URL(url);
      return "";
    } catch {
      return "地址格式不正确";
    }
  };

  const handleEndpointChange = (value: string) => {
    setWebdavTouched(true);
    setWebdav((current) => ({ ...current, endpoint: value }));
    setWebdavError(validateWebDavEndpoint(value));
  };

  const savedWebdavAccount = snapshot.syncAccounts.find((item) => item.provider === "webdav");

  useEffect(() => {
    if (!savedWebdavAccount || webdavTouched) return;
    const endpoint = savedWebdavAccount.endpoint ?? "";
    const username = savedWebdavAccount.username ?? "";
    setWebdav({ endpoint, username, password: "" });
    setWebdavError(validateWebDavEndpoint(endpoint));
  }, [savedWebdavAccount?.endpoint, savedWebdavAccount?.username, webdavTouched]);

  useEffect(() => {
    let active = true;
    void loadWebDavPasswordSecret()
      .then((password) => {
        if (active) setHasWebDavPassword(Boolean(password));
      })
      .catch(() => {
        if (active) setHasWebDavPassword(false);
      });
    return () => {
      active = false;
    };
  }, [activePage]);

  const webdavAccount: SyncAccount = savedWebdavAccount ?? {
    id: "webdav-preview",
    provider: "webdav",
    name: "WebDAV",
    endpoint: webdav.endpoint,
    username: webdav.username,
    enabled: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    revision: 1,
    deviceId: getMobileDeviceId()
  };

  const getWebDavCredentials = async () => {
    const savedPassword = await loadWebDavPasswordSecret();
    return {
      endpoint: webdav.endpoint,
      username: webdav.username,
      password: webdav.password.trim() || savedPassword || ""
    };
  };

  const testWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      const result = await testWebDavConnection(credentials);
      const next = await saveSyncAccount(snapshot, {
        provider: "webdav",
        name: "WebDAV",
        endpoint: webdav.endpoint,
        username: webdav.username,
        enabled: result.ok
      });
      if (result.ok && webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      onSnapshotChange(next);
      onMessage(result.message);
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const uploadWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      await uploadWebDavSnapshot(credentials, webdavAccount, snapshot);
      if (webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      onMessage("WebDAV 上传完成。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const downloadWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      const next = await downloadWebDavSnapshot(credentials, snapshot);
      if (webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      await saveMobileSnapshot(next);
      onSnapshotChange(next);
      onMessage("WebDAV 下载完成，已合并到手机端。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const clearWebDavCredential = async () => {
    try {
      await clearWebDavPasswordSecret();
      setHasWebDavPassword(false);
      setWebdav((current) => ({ ...current, password: "" }));
      onMessage("已清除本机 WebDAV 密码 / token。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  return {
    webdav,
    webdavError,
    webdavTouched,
    hasWebDavPassword,
    webdavAccount,
    handleEndpointChange,
    setWebdav,
    setWebdavTouched,
    testWebDav,
    uploadWebDav,
    downloadWebDav,
    clearWebDavCredential
  };
}
