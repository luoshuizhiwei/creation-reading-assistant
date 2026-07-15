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
    case "amber":
      return "#f5e6c8";
    case "parchment":
      return "#f0e4d0";
    case "beans":
      return "#c7edcc";
    case "warm":
    default:
      return "#f7f0e7";
  }
}

export function readerTextColor(background: ReaderBackground): string {
  switch (background) {
    case "night":
      return "#f4eadf";
    case "amber":
      return "#5c4a2a";
    case "parchment":
      return "#4a3728";
    case "beans":
      return "#2d4a2d";
    default:
      return "#241b16";
  }
}
