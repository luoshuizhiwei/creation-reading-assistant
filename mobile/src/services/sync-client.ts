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
  return parsePairingCandidates(text)[0];
}

function parsePairingUrl(value: string): PairingInput {
  const url = new URL(value);
  const token = url.searchParams.get("token");
  if (!token) throw new Error("配对 URL 缺少 token。");
  const port = Number(url.port);
  if (!url.hostname || !Number.isFinite(port) || port <= 0) throw new Error("配对 URL 缺少有效的电脑地址或端口。");
  return { host: url.hostname, port, token, pairingUrl: value };
}

function uniquePairingCandidates(candidates: PairingInput[]): PairingInput[] {
  const seen = new Set<string>();
  return candidates.filter((candidate) => {
    const key = `${candidate.host}:${candidate.port}:${candidate.token}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function parsePairingCandidates(text: string): PairingInput[] {
  const value = text.trim();
  if (!value) throw new Error("请先粘贴电脑端配对信息。");
  try {
    if (value.startsWith("http://") || value.startsWith("https://")) {
      return [parsePairingUrl(value)];
    }
    const parsed = JSON.parse(value) as PairingTokenResult & Partial<PairingInput>;
    const candidates: PairingInput[] = [];
    if (Array.isArray(parsed.qrPayloads)) {
      for (const item of parsed.qrPayloads) {
        if (item.qrPayload) candidates.push(...parsePairingCandidates(item.qrPayload));
        else if (item.pairingUrl) candidates.push(parsePairingUrl(item.pairingUrl));
      }
    }
    if (Array.isArray(parsed.pairingUrls)) {
      for (const pairingUrl of parsed.pairingUrls) candidates.push(parsePairingUrl(pairingUrl));
    }
    if (parsed.qrPayload) candidates.push(...parsePairingCandidates(parsed.qrPayload));
    if (parsed.pairingUrl) candidates.push(parsePairingUrl(parsed.pairingUrl));
    if (parsed.host && parsed.port && parsed.token) {
      candidates.push({
        host: parsed.host,
        port: Number(parsed.port),
        token: parsed.token,
        pairingUrl: parsed.pairingUrl
      });
    }
    const unique = uniquePairingCandidates(candidates);
    if (!unique.length) throw new Error("二维码载荷缺少可用的 host/port/token 或 pairingUrl。");
    return unique;
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
    async uploadBookFile(bookId: string, fileName: string, content: string | Blob): Promise<{ ok: boolean; bookId: string }> {
      const response = await fetch(`${baseUrl}/sync/books/${encodeURIComponent(bookId)}/file`, {
        method: "PUT",
        headers: {
          "content-type": "application/octet-stream",
          "x-device-id": device.deviceId,
          "X-Original-File-Name": encodeURIComponent(fileName)
        },
        body: content
      });
      if (!response.ok) throw new Error(await response.text());
      return (await response.json()) as { ok: boolean; bookId: string };
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

export async function pairWithFirstReachable(
  candidates: PairingInput[],
  onAttempt?: (attempt: { index: number; total: number; input: PairingInput }) => void
): Promise<{ input: PairingInput; result: { ok: boolean; device: DeviceInfo; manifest: SyncManifest } }> {
  let lastError = "";
  for (const [index, input] of candidates.entries()) {
    onAttempt?.({ index: index + 1, total: candidates.length, input });
    try {
      const result = await createSyncClient(input).pair();
      return { input, result };
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
  }
  throw new Error(`所有配对地址都连接失败。最后错误：${lastError || "网络不可达"}。请确认手机和电脑在同一 Wi‑Fi，并尝试电脑端显示的备用地址。`);
}
