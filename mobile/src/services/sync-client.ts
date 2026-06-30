import type { DeviceInfo, PairingTokenResult, SyncManifest, SyncPullResponse, SyncPushPayload, SyncPushResult } from "../../../src/types/sync";

export interface PairingInput {
  host: string;
  port: number;
  token: string;
  pairingUrl?: string;
}

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

export function parsePairingPayload(text: string): PairingInput {
  const value = text.trim();
  if (!value) throw new Error("请先粘贴电脑端配对信息。");
  try {
    if (value.startsWith("http://") || value.startsWith("https://")) {
      const url = new URL(value);
      const token = url.searchParams.get("token");
      if (!token) throw new Error("配对 URL 缺少 token。");
      return { host: url.hostname, port: Number(url.port), token, pairingUrl: value };
    }
    const parsed = JSON.parse(value) as PairingTokenResult & Partial<PairingInput>;
    if (parsed.pairingUrl) return parsePairingPayload(parsed.pairingUrl);
    if (!parsed.host || !parsed.port || !parsed.token) throw new Error("二维码载荷缺少 host/port/token。");
    return { host: parsed.host, port: Number(parsed.port), token: parsed.token };
  } catch (error) {
    if (error instanceof Error) throw error;
    throw new Error("配对信息无法解析。");
  }
}

export function createMobileDevice(): DeviceInfo {
  const saved = localStorage.getItem("creation-reading-assistant-mobile-device-id");
  const deviceId = saved ?? `android-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
  localStorage.setItem("creation-reading-assistant-mobile-device-id", deviceId);
  return {
    deviceId,
    name: "Android 手机端",
    platform: "android",
    pairedAt: new Date().toISOString(),
    lastSeenAt: new Date().toISOString()
  };
}

export function createSyncClient(input: PairingInput) {
  const baseUrl = trimTrailingSlash(`http://${input.host}:${input.port}`);
  const device = createMobileDevice();

  async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${baseUrl}/${path.replace(/^\/+/, "")}`, {
      ...init,
      headers: {
        "content-type": "application/json",
        "x-device-id": device.deviceId,
        ...(init?.headers ?? {})
      }
    });
    if (!response.ok) throw new Error(await response.text());
    return (await response.json()) as T;
  }

  return {
    async pair(): Promise<{ ok: boolean; device: DeviceInfo; manifest: SyncManifest }> {
      return requestJson("sync/pair", {
        method: "POST",
        body: JSON.stringify({ token: input.token, device })
      });
    },
    async manifest(): Promise<SyncManifest> {
      return requestJson("sync/manifest");
    },
    async pull(): Promise<SyncPullResponse> {
      return requestJson("sync/pull", { method: "POST", body: JSON.stringify({ device }) });
    },
    async push(payload: Omit<SyncPushPayload, "device">): Promise<SyncPushResult> {
      return requestJson("sync/push", {
        method: "POST",
        body: JSON.stringify({ device, ...payload })
      });
    },
    async downloadBookFile(bookId: string): Promise<Blob> {
      const response = await fetch(`${baseUrl}/sync/books/${encodeURIComponent(bookId)}/file`, {
        headers: { "x-device-id": device.deviceId }
      });
      if (!response.ok) throw new Error(await response.text());
      return response.blob();
    },
    async downloadBookChunk(bookId: string, index: number): Promise<Blob> {
      const response = await fetch(`${baseUrl}/sync/books/${encodeURIComponent(bookId)}/chunks/${index}`, {
        headers: { "x-device-id": device.deviceId }
      });
      if (!response.ok) throw new Error(await response.text());
      return response.blob();
    }
  };
}
