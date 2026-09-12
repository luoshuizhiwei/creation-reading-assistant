import type { ProjectPrintMode } from "../../../src/types/creation";

export type { ProjectPrintMode } from "../../../src/types/creation";

/**
 * 全书预览的打印方式白名单（主进程重校验用）。
 * `print` 打开系统打印对话框；`pdf` 直接按打印版式导出 PDF。
 */
export const PROJECT_PRINT_MODES: readonly ProjectPrintMode[] = ["print", "pdf"];

export function isProjectPrintMode(value: unknown): value is ProjectPrintMode {
  return typeof value === "string" && (PROJECT_PRINT_MODES as readonly string[]).includes(value);
}

/**
 * A4 打印版式参数。
 *
 * 页面尺寸与边距都在这里给定（而不是在 CSS 里再写一份 `@page margin`），
 * 避免「选项边距 + CSS 边距」叠加成双倍留白；CSS 只负责声明 `@page { size: A4 }`
 * 以及把外壳还原成可自然分页的静态块。
 */
export const PRINT_PDF_OPTIONS = {
  pageSize: "A4",
  landscape: false,
  printBackground: false,
  margins: {
    top: 0.71,
    bottom: 0.71,
    left: 0.63,
    right: 0.63
  }
} as const;
