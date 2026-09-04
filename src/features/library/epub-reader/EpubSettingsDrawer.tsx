import { ReaderSettingsDrawer, type ReaderSettingsDrawerProps } from "@/features/library/ReaderSettingsDrawer";

export type EpubSettingsDrawerProps = Omit<ReaderSettingsDrawerProps, "format">;

/**
 * EpubSettingsDrawer — EPUB 阅读器设置抽屉
 * 委托给通用 ReaderSettingsDrawer，锁定 format="epub"
 */
export function EpubSettingsDrawer(props: EpubSettingsDrawerProps) {
  return <ReaderSettingsDrawer {...props} format="epub" />;
}
