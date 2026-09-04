/**
 * 卡片筛选导出对话框。
 *
 * 导出当前筛选范围（类型 / 搜索 / 选中），由主进程弹出保存对话框并写出 CSV 或 Markdown；
 * 不导出内部 id、批注或不必要元数据，渲染端不持有文件路径能力。
 */
import { useState } from "react";
import { Download } from "lucide-react";
import { Button, Dialog, Spinner } from "@/components/ui";
import { useUIStore } from "@/stores/ui-store";
import type { CardExportFilter } from "./card-import-export-types";
import { exportCards } from "./card-import-export-client";

interface CardExportDialogProps {
  projectId: string;
  filter: CardExportFilter;
  onClose(): void;
}

type Format = "csv" | "markdown";

export function CardExportDialog({ projectId, filter, onClose }: CardExportDialogProps) {
  const showToast = useUIStore((state) => state.showToast);
  const [format, setFormat] = useState<Format>("csv");
  const [busy, setBusy] = useState(false);
  const [written, setWritten] = useState<number | null>(null);

  const runExport = async () => {
    setBusy(true);
    try {
      const result = await exportCards({ projectId, filter, format });
      if (result.canceled) {
        onClose();
        return;
      }
      setWritten(result.written);
      showToast({ tone: "success", title: "已导出", body: `共 ${result.written} 张卡片写入文件。` });
    } catch (error) {
      showToast({ tone: "error", title: "导出失败", body: error instanceof Error ? error.message : String(error) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={true}
      title={<span className="flex items-center gap-2"><Download size={16} /> 导出卡片（CSV / Markdown）</span>}
      ariaLabel="导出卡片"
      onClose={busy ? undefined : onClose}
      width="max-w-md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>关闭</Button>
          <Button onClick={() => void runExport()} disabled={busy}>
            {busy ? <><Spinner size={14} className="mr-1.5" /> 导出中…</> : "选择保存位置并导出"}
          </Button>
        </>
      }
    >
      <p className="migration-note">
            将导出当前筛选范围内的卡片（类型
            {filter.cardKind ? "已限定" : "全部"}、{filter.search ? "含搜索词" : "无搜索"}）。
            导出不含内部 id、批注与多余元数据；CSV 与 Markdown 使用对称字段。
          </p>

          <fieldset className="card-import-fieldset">
            <legend>导出格式</legend>
            <div className="cards-view-switch" role="group" aria-label="导出格式">
              <button
                type="button"
                className={format === "csv" ? "active" : ""}
                onClick={() => setFormat("csv")}
              >
                CSV
              </button>
              <button
                type="button"
                className={format === "markdown" ? "active" : ""}
                onClick={() => setFormat("markdown")}
              >
                Markdown
              </button>
            </div>
          </fieldset>

          {written !== null && (
            <p className="migration-note">已写入 {written} 张卡片。</p>
          )}

    </Dialog>
  );
}
