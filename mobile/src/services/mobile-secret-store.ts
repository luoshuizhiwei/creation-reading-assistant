const MOBILE_SECRET_DB_NAME = "creation-reading-assistant-mobile-secrets";
const MOBILE_SECRET_STORE_NAME = "secrets";

function openMobileSecretDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!("indexedDB" in window)) {
      reject(new Error("当前 WebView 不支持本地密钥存储，请升级系统 WebView 后再保存敏感信息。"));
      return;
    }
    const request = indexedDB.open(MOBILE_SECRET_DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(MOBILE_SECRET_STORE_NAME)) db.createObjectStore(MOBILE_SECRET_STORE_NAME);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("打开本地密钥存储失败。"));
  });
}

export async function readMobileSecret(id: string): Promise<string | undefined> {
  const db = await openMobileSecretDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(MOBILE_SECRET_STORE_NAME, "readonly");
    const store = transaction.objectStore(MOBILE_SECRET_STORE_NAME);
    const request = store.get(id);
    request.onsuccess = () => resolve(typeof request.result === "string" ? request.result : undefined);
    request.onerror = () => reject(request.error ?? new Error("读取本地密钥失败。"));
    transaction.oncomplete = () => db.close();
  });
}

export async function writeMobileSecret(id: string, value: string): Promise<void> {
  const db = await openMobileSecretDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(MOBILE_SECRET_STORE_NAME, "readwrite");
    const store = transaction.objectStore(MOBILE_SECRET_STORE_NAME);
    const request = store.put(value, id);
    request.onerror = () => reject(request.error ?? new Error("保存本地密钥失败。"));
    transaction.oncomplete = () => {
      db.close();
      resolve();
    };
    transaction.onerror = () => reject(transaction.error ?? new Error("保存本地密钥失败。"));
  });
}

export async function deleteMobileSecret(id: string): Promise<void> {
  const db = await openMobileSecretDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(MOBILE_SECRET_STORE_NAME, "readwrite");
    const store = transaction.objectStore(MOBILE_SECRET_STORE_NAME);
    const request = store.delete(id);
    request.onerror = () => reject(request.error ?? new Error("删除本地密钥失败。"));
    transaction.oncomplete = () => {
      db.close();
      resolve();
    };
    transaction.onerror = () => reject(transaction.error ?? new Error("删除本地密钥失败。"));
  });
}

