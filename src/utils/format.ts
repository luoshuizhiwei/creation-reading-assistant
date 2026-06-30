import type { ReaderBackground } from "@/types/library";

export function formatDuration(ms = 0): string {
  const minutes = Math.floor(ms / 60_000);
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (hours <= 0) return `${restMinutes} 分钟`;
  return `${hours} 小时 ${restMinutes} 分钟`;
}

export function formatDate(value?: string): string {
  if (!value) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

export function messageFromError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function readerShellClass(background: ReaderBackground): string {
  return `reader-shell-${background}`;
}

export function readerPaperClass(background: ReaderBackground): string {
  return `reader-bg-${background}`;
}

export function readerBackgroundColor(background: ReaderBackground): string {
  switch (background) {
    case "white":
      return "#f6f4ef";
    case "green":
      return "#e9f2e2";
    case "night":
      return "#15100e";
    case "warm":
    default:
      return "#f7f0e7";
  }
}

export function readerTextColor(background: ReaderBackground): string {
  return background === "night" ? "#f4eadf" : "#241b16";
}
