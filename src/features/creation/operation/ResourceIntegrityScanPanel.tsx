import type { ResourceIntegrityCategory, ResourceIntegrityReport } from "../../../types/operation";
import {
  RESOURCE_INTEGRITY_CATEGORY_LABEL,
  RESOURCE_INTEGRITY_CATEGORY_ORDER
} from "../../../types/operation";
import "./operation.css";

export interface ResourceIntegrityScanPanelProps {
  report: ResourceIntegrityReport | null;
}

/**
 * 资源完整性扫描结果展示（只读）。
 * - 默认只读，不提供删除、自动修复或一键清理入口；
 * - 仅展示相对路径与安全说明，不暴露绝对路径；
 * - 空结果显示明确提示；
 * - 大量结果提供分类摘要与滚动容器。
 */
export function ResourceIntegrityScanPanel({ report }: ResourceIntegrityScanPanelProps): JSX.Element {
  if (!report) {
    return <p className="resource-scan-empty">未发现资源完整性问题。</p>;
  }

  const issues = report.issues ?? [];
  if (issues.length === 0) {
    return <p className="resource-scan-empty">未发现资源完整性问题。</p>;
  }

  const counts = new Map<ResourceIntegrityCategory, number>();
  for (const issue of issues) {
    counts.set(issue.type, (counts.get(issue.type) ?? 0) + 1);
  }

  // 按固定顺序展示有问题的分类，缺失分类不出现。
  const presentCategories = RESOURCE_INTEGRITY_CATEGORY_ORDER.filter((category) => counts.has(category));

  return (
    <div className="resource-scan">
      <p className="resource-scan-summary">
        扫描记录 {report.scannedRecordCount} 条、文件 {report.scannedFileCount} 个，发现 {issues.length} 处问题。
      </p>
      <ul className="resource-scan-categories">
        {presentCategories.map((category) => (
          <li key={category} className={`resource-scan-category resource-scan-category-${category}`}>
            <span className="resource-scan-category-label">
              {RESOURCE_INTEGRITY_CATEGORY_LABEL[category]}
            </span>
            <span className="resource-scan-category-count">{counts.get(category)}</span>
          </li>
        ))}
      </ul>
      <div className="resource-scan-list" role="list">
        {issues.map((issue, index) => (
          <div className="resource-scan-item" role="listitem" key={`${issue.relativePath}-${index}`}>
            <span className="resource-scan-item-category">
              {RESOURCE_INTEGRITY_CATEGORY_LABEL[issue.type]}
            </span>
            <span className="resource-scan-item-path">{issue.relativePath}</span>
            <span className="resource-scan-item-message">{issue.message}</span>
          </div>
        ))}
      </div>
      <p className="resource-scan-note">
        以上为只读诊断信息，仅展示工作区相对路径。如需处理，请手动在文件管理器中核对，本界面不提供删除或自动修复。
      </p>
    </div>
  );
}
