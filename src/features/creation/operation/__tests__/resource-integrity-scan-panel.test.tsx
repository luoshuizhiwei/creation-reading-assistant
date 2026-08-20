// @vitest-environment jsdom
import React from "react";
/**
 * ResourceIntegrityScanPanel 只读校验测试：
 *  - 空结果给出“未发现资源完整性问题”
 *  - 按分类汇总并展示问题（分类标签 + 计数 + 相对路径）
 *  - 仅展示相对路径，不泄露绝对路径
 *  - 不提供任何删除 / 自动修复 / 清理入口
 */
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ResourceIntegrityReport } from "../../../types/operation";
import { ResourceIntegrityScanPanel } from "../ResourceIntegrityScanPanel";

describe("ResourceIntegrityScanPanel", () => {
  it("空结果给出未发现问题的提示", () => {
    const report: ResourceIntegrityReport = { issues: [], scannedRecordCount: 0, scannedFileCount: 0 };
    render(<ResourceIntegrityScanPanel report={report} />);
    expect(screen.getByText(/未发现资源完整性问题/)).toBeTruthy();
  });

  it("按分类汇总数量并使用规范标签", () => {
    const report: ResourceIntegrityReport = {
      scannedRecordCount: 4,
      scannedFileCount: 4,
      issues: [
        { type: "file-missing", relativePath: "a.txt", message: "缺失" },
        { type: "file-missing", relativePath: "b.txt", message: "缺失" },
        { type: "size-mismatch", relativePath: "c.png", message: "尺寸" }
      ]
    };
    render(<ResourceIntegrityScanPanel report={report} />);
    expect(screen.getAllByText("文件缺失").length).toBeGreaterThan(0);
    expect(screen.getAllByText("大小不符").length).toBeGreaterThan(0);
    // 文件缺失计数 2，大小不符计数 1
    expect(screen.getByText("2")).toBeTruthy();
    expect(screen.getByText("1")).toBeTruthy();
  });

  it("仅展示相对路径，不泄露绝对路径", () => {
    const report: ResourceIntegrityReport = {
      scannedRecordCount: 1,
      scannedFileCount: 1,
      issues: [{ type: "file-unreferenced", relativePath: "assets/local/cover.png", message: "未被任何条目引用" }]
    };
    render(<ResourceIntegrityScanPanel report={report} />);
    expect(screen.getByText("assets/local/cover.png")).toBeTruthy();
    expect(screen.queryByText(/\/Users\//)).toBeNull();
    expect(screen.queryByText(/C:\\/)).toBeNull();
  });

  it("不提供删除 / 自动修复 / 清理入口", () => {
    const report: ResourceIntegrityReport = {
      scannedRecordCount: 1,
      scannedFileCount: 1,
      issues: [{ type: "hash-mismatch", relativePath: "assets/x.png", message: "哈希不符" }]
    };
    render(<ResourceIntegrityScanPanel report={report} />);
    expect(screen.queryByRole("button", { name: /删除|修复|清理/ })).toBeNull();
    expect(screen.getByText(/不提供删除或自动修复/)).toBeTruthy();
  });
});
