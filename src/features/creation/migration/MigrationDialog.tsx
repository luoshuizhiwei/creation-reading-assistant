import { useEffect, useState } from "react";
import { AlertTriangle, ArchiveRestore, CheckCircle2, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import type { LegacyMigrationReport, LegacyMigrationStatus } from "@/types/creation";

interface MigrationDialogProps {
  onClose(): void;
  onMigrated(): void;
}

export function MigrationDialog({ onClose, onMigrated }: MigrationDialogProps) {
  const { loadMigrationStatus, runMigration } = useCreationActions();
  const [status, setStatus] = useState<LegacyMigrationStatus | null>(null);
  const [running, setRunning] = useState(false);
  const [report, setReport] = useState<LegacyMigrationReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadMigrationStatus().then((loaded) => setStatus(loaded));
  }, [loadMigrationStatus]);

  const execute = async () => {
    setRunning(true);
    setError(null);
    const result = await runMigration();
    setRunning(false);
    if (result) {
      setReport(result);
      onMigrated();
    } else {
      setError("迁移未完成，已回退；请检查旧数据后重试。");
    }
  };

  const discovered = status?.activation?.discovered ?? status?.report?.sources.discovered ?? 0;
  const migrated = status?.activation?.migrated ?? status?.report?.sources.migrated ?? 0;

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="旧数据迁移" aria-modal="true">
      <div className="creation-search-shell migration-dialog" role="search">
        <div className="creation-search-head">
          <ArchiveRestore size={16} className="creation-search-head-icon" />
          <span className="creation-proof-title">旧数据迁移</span>
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭迁移对话框">
            <X size={16} />
          </button>
        </div>

        <div className="migration-body">
          {report ? (
            <div className="migration-report">
              <p className="migration-report-head"><CheckCircle2 size={16} /> 迁移完成</p>
              <dl>
                <div><dt>发现旧灵感</dt><dd>{report.sources.discovered}</dd></div>
                <div><dt>已迁移到收件箱</dt><dd>{report.sources.migrated}</dd></div>
                <div><dt>跳过（重复）</dt><dd>{report.sources.skipped}</dd></div>
                <div><dt>失败</dt><dd>{report.sources.failed}</dd></div>
                <div><dt>备份目录</dt><dd>{report.backup.directory}</dd></div>
                <div><dt>完整性检查</dt><dd>{report.targetStore.integrityOk ? "通过" : "未通过"}</dd></div>
              </dl>
              {report.failures.length > 0 && (
                <ul className="migration-failures">
                  {report.failures.map((failure) => (
                    <li key={failure.legacyId}>旧灵感 {failure.legacyId}：{failure.reason}</li>
                  ))}
                </ul>
              )}
              <p className="migration-note">
                回退方式：{report.rollback.how}
              </p>
              <div className="migration-actions">
                <Button onClick={onClose}>完成</Button>
              </div>
            </div>
          ) : status?.activated ? (
            <div className="migration-report">
              <p className="migration-report-head"><CheckCircle2 size={16} /> 旧数据已迁移</p>
              <dl>
                <div><dt>迁移灵感</dt><dd>{migrated} 条（共发现 {discovered} 条）</dd></div>
                <div><dt>激活时间</dt><dd>{status.activation ? new Date(status.activation.activatedAt).toLocaleString("zh-CN") : "—"}</dd></div>
                <div><dt>备份</dt><dd>{status.activation?.backupDirectory ?? "—"}</dd></div>
              </dl>
              <p className="migration-note">旧数据保持只读，新灵感进入创作工作台的全局收件箱。</p>
              <div className="migration-actions">
                <Button onClick={onClose}>关闭</Button>
              </div>
            </div>
          ) : (
            <>
              {status && !status.canProceed && (
                <p className="migration-error"><AlertTriangle size={14} /> 迁移预检未通过：{status.blockingReasons.join("；")}</p>
              )}
              {status?.canProceed && (
                <p className="migration-note">
                  将扫描旧数据目录：先在独立备份目录创建带校验和的完整备份，再把全部旧灵感复制到创作工作台收件箱（保留标题、正文、标签、来源与全部 AI 候选），校验通过后写入激活标记。旧数据文件不会被修改。
                </p>
              )}
              {error && <p className="migration-error"><AlertTriangle size={14} /> {error}</p>}
              <div className="migration-actions">
                <Button variant="secondary" onClick={onClose}>取消</Button>
                <Button
                  disabled={running || !status?.canProceed}
                  onClick={() => void execute()}
                >
                  {running ? <><Loader2 size={14} className="spin" /> 迁移中…</> : "开始迁移"}
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
