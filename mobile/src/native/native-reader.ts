import { Capacitor, registerPlugin } from "@capacitor/core";
import type { MobileBook, MobileReaderSettings } from "../types/mobile";

export interface NativeReaderLocatorInput {
  chapterIndex?: number;
  charOffset?: number;
  pageIndex?: number;
  progressPercent?: number;
  epubHref?: string;
}

export interface NativeReaderOpenOptions {
  bookId: string;
  title: string;
  author?: string;
  fileUri: string;
  format: "txt" | "md" | "epub";
  locator?: NativeReaderLocatorInput;
  settings: {
    fontSizeSp: number;
    lineSpacingMultiplier: number;
    paragraphSpacingDp: number;
    horizontalPaddingDp: number;
    verticalPaddingDp: number;
    textBold: boolean;
    pageMode: "NONE" | "SLIDE" | "COVER" | "SCROLL";
    theme: "WHITE" | "WARM" | "GREEN" | "NIGHT";
  };
}

export interface NativeReaderResult {
  cancelled: boolean;
  bookId?: string;
  charOffset?: number;
  pageIndex?: number;
  progressPercent?: number;
  activeDurationMs?: number;
  sessionId?: string;
  chapterTitle?: string;
  chapterIndex?: number;
  epubHref?: string;
  actions?: NativeReaderAction[];
  settings?: NativeReaderOpenOptions["settings"];
}

export interface NativeReaderAction {
  actionId: string;
  createdAt?: number;
  type: "bookmark" | "note" | "inspiration";
  bookId: string;
  bookTitle: string;
  bookAuthor?: string;
  format: "txt" | "epub";
  chapterTitle?: string;
  chapterIndex?: number;
  epubHref?: string;
  charOffset?: number;
  pageIndex?: number;
  progressPercent?: number;
  excerpt?: string;
}

export interface NativeReaderCheckpoint {
  sessionId: string;
  bookId: string;
  bookTitle?: string;
  format: "txt" | "epub";
  chapterTitle?: string;
  activeDurationMs?: number;
  updatedAt?: number;
  locator?: NativeReaderLocatorInput;
  settings?: NativeReaderOpenOptions["settings"];
}

interface NativeReaderPlugin {
  isAvailable(): Promise<{ available: boolean; engine: string }>;
  open(options: NativeReaderOpenOptions): Promise<NativeReaderResult>;
  getPendingActions(): Promise<{ actions: NativeReaderAction[] }>;
  acknowledgeActions(options: { actionIds: string[] }): Promise<{ acknowledged: number }>;
  getPendingCheckpoints(): Promise<{ checkpoints: NativeReaderCheckpoint[] }>;
  acknowledgeCheckpoints(options: { sessionIds: string[] }): Promise<{ acknowledged: number }>;
}

const NativeReader = registerPlugin<NativeReaderPlugin>("NativeReader");

export function canOpenWithNativeReader(
  book: Pick<MobileBook, "format">,
  settings?: Pick<MobileReaderSettings, "readerMode">
): boolean {
  return Capacitor.isNativePlatform() && (book.format === "txt" || book.format === "epub") && settings?.readerMode !== "scroll";
}

function mapTheme(background: MobileReaderSettings["readerBackground"]): NativeReaderOpenOptions["settings"]["theme"] {
  if (background === "white") return "WHITE";
  if (background === "green" || background === "beans" || background === "green-bean") return "GREEN";
  if (background === "night" || background === "oled-black") return "NIGHT";
  return "WARM";
}

export function nativeSettingsFromMobile(settings: MobileReaderSettings): NativeReaderOpenOptions["settings"] {
  return {
    fontSizeSp: settings.fontSize,
    lineSpacingMultiplier: settings.lineHeight,
    paragraphSpacingDp: Math.max(0, settings.paragraphSpacing * 8),
    horizontalPaddingDp: settings.pageMargin,
    verticalPaddingDp: Math.max(20, settings.pageMargin),
    textBold: settings.fontWeight === "bold",
    pageMode: settings.readerMode === "scroll" ? "SCROLL" : "SLIDE",
    theme: mapTheme(settings.readerBackground)
  };
}

function mobileBackgroundFromNative(
  theme: NativeReaderOpenOptions["settings"]["theme"]
): MobileReaderSettings["readerBackground"] {
  if (theme === "WHITE") return "white";
  if (theme === "GREEN") return "green";
  if (theme === "NIGHT") return "night";
  return "warm";
}

export function mergeNativeSettingsIntoMobile(
  current: MobileReaderSettings,
  native?: NativeReaderResult["settings"]
): MobileReaderSettings {
  if (!native) return current;
  return {
    ...current,
    fontSize: native.fontSizeSp,
    lineHeight: native.lineSpacingMultiplier,
    paragraphSpacing: native.paragraphSpacingDp / 8,
    pageMargin: native.horizontalPaddingDp,
    fontWeight: native.textBold ? "bold" : "regular",
    readerBackground: mobileBackgroundFromNative(native.theme),
    readerMode: native.pageMode === "SCROLL" ? "scroll" : "paged"
  };
}

export async function openNativeReader(options: NativeReaderOpenOptions): Promise<NativeReaderResult> {
  if (!Capacitor.isNativePlatform()) throw new Error("原生阅读内核只在 Android 应用中可用");
  const availability = await NativeReader.isAvailable();
  if (!availability.available) throw new Error("当前安装包没有包含原生阅读内核");
  return NativeReader.open(options);
}

export async function getPendingNativeReaderActions(): Promise<NativeReaderAction[]> {
  if (!Capacitor.isNativePlatform()) return [];
  const result = await NativeReader.getPendingActions();
  return Array.isArray(result.actions) ? result.actions : [];
}

export async function acknowledgeNativeReaderActions(actionIds: string[]): Promise<void> {
  const uniqueIds = [...new Set(actionIds.filter(Boolean))];
  if (!Capacitor.isNativePlatform() || uniqueIds.length === 0) return;
  await NativeReader.acknowledgeActions({ actionIds: uniqueIds });
}

export async function getPendingNativeReaderCheckpoints(): Promise<NativeReaderCheckpoint[]> {
  if (!Capacitor.isNativePlatform()) return [];
  const result = await NativeReader.getPendingCheckpoints();
  return Array.isArray(result.checkpoints) ? result.checkpoints : [];
}

export async function acknowledgeNativeReaderCheckpoints(sessionIds: string[]): Promise<void> {
  const uniqueIds = [...new Set(sessionIds.filter(Boolean))];
  if (!Capacitor.isNativePlatform() || uniqueIds.length === 0) return;
  await NativeReader.acknowledgeCheckpoints({ sessionIds: uniqueIds });
}
