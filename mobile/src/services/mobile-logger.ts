const MOBILE_LOG_KEY = "creation-reading-assistant-mobile-logs";
const MAX_LOG_ENTRIES = 200;

export type MobileLogLevel = "info" | "warn" | "error";

export interface MobileLogEntry {
  id: string;
  timestamp: string;
  level: MobileLogLevel;
  module: string;
  message: string;
  code?: string;
}

function nowIso(): string {
  return new Date().toISOString();
}

function generateId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
}

function sanitizeLogMessage(message: string): string {
  return message
    .replace(/\b(?:sk-|pk-|ak-)[a-zA-Z0-9]{16,}\b/g, "<API_KEY_REDACTED>")
    .replace(/\b(?:password|passwd|pwd|token)\s*[:=]\s*[^\s&]+/gi, "<CREDENTIAL_REDACTED>")
    .slice(0, 800);
}

export function loadMobileLogs(): MobileLogEntry[] {
  try {
    const raw = localStorage.getItem(MOBILE_LOG_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown[];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is MobileLogEntry => {
      const entry = item as Partial<MobileLogEntry>;
      return (
        typeof entry.id === "string" &&
        typeof entry.timestamp === "string" &&
        typeof entry.message === "string" &&
        typeof entry.module === "string" &&
        (entry.level === "info" || entry.level === "warn" || entry.level === "error")
      );
    });
  } catch {
    return [];
  }
}

function saveMobileLogs(entries: MobileLogEntry[]): void {
  try {
    localStorage.setItem(MOBILE_LOG_KEY, JSON.stringify(entries.slice(0, MAX_LOG_ENTRIES)));
  } catch {
    // 日志写入失败时静默丢弃，避免阻塞业务
  }
}

export function addMobileLog(
  level: MobileLogLevel,
  module: string,
  message: string,
  options: { code?: string } = {}
): void {
  const entries = loadMobileLogs();
  entries.unshift({
    id: generateId(),
    timestamp: nowIso(),
    level,
    module,
    message: sanitizeLogMessage(message),
    code: options.code
  });
  saveMobileLogs(entries);
}

export function clearMobileLogs(): void {
  try {
    localStorage.removeItem(MOBILE_LOG_KEY);
  } catch {
    // ignore
  }
}

export function exportMobileLogs(): string {
  const entries = loadMobileLogs();
  return JSON.stringify(
    entries.map((entry) => ({
      time: entry.timestamp,
      level: entry.level,
      module: entry.module,
      message: entry.message,
      code: entry.code
    })),
    null,
    2
  );
}

export function getMobileLogStats(): { total: number; errors: number; oldestAt?: string; newestAt?: string } {
  const entries = loadMobileLogs();
  return {
    total: entries.length,
    errors: entries.filter((entry) => entry.level === "error").length,
    oldestAt: entries[entries.length - 1]?.timestamp,
    newestAt: entries[0]?.timestamp
  };
}
