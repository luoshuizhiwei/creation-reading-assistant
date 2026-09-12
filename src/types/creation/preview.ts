/**
 * 阶段 4-B：全书只读预览与打印的共享契约。
 *
 * 预览读通道复用 `project.export`（固定 `includeBlocks: true`），因此：
 * - 不新增第二个正文读源，删除态章节/场景的过滤规则与成稿导出完全一致；
 * - 不新增字数真源，预览页展示的字数全部取自导出视图里由 `scenes.non_ws_count`
 *   派生的 `wordCount`，renderer 不重新统计文本。
 */

/** 打印方式：`print` 走系统打印对话框；`pdf` 走 A4 打印版式并保存为 PDF。 */
export type ProjectPrintMode = "print" | "pdf";

export interface ProjectPrintResult {
  /** 用户在保存/打印对话框中取消时为 true。 */
  canceled: boolean;
  /** `pdf` 模式成功时为目标文件路径；`print` 模式与取消时为 null。 */
  filePath: string | null;
  /** 主进程侧失败原因（例如系统没有可用打印机）；成功时不出现。 */
  failureReason?: string;
}
