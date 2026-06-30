import type { DesktopApi } from "@/types/api";

export function getDesktopApi(): DesktopApi {
  if (!window.api) {
    throw new Error("Desktop API is not available. Make sure the app is running inside Electron.");
  }
  return window.api;
}
