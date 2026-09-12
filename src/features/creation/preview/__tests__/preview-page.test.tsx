// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PreviewPage } from "@/features/creation/preview/PreviewPage";
import { useUIStore } from "@/stores/ui-store";
import type { ProjectExportView } from "@/types/creation";

const service = vi.hoisted(() => ({
  projectPreview: vi.fn(),
  printProject: vi.fn()
}));

vi.mock("@/services/creation-service", () => service);

function makeView(): ProjectExportView {
  return {
    projectId: "p1",
    title: "测试作品",
    wordCount: 1200,
    volumes: [
      {
        id: "v1",
        title: "第一卷",
        wordCount: 1200,
        chapters: [
          {
            id: "c1",
            title: "风起",
            displayNumber: "第1章",
            wordCount: 800,
            scenes: [
              {
                id: "s1",
                title: "雨夜",
                wordCount: 800,
                blocks: [
                  { kind: "paragraph", text: "正文行一。" },
                  { kind: "quoteLetter", text: "此信为证。" },
                  { kind: "authorNote", text: "作者的提醒。" },
                  { kind: "sceneBreak", text: "" },
                  { kind: "centeredText", text: "居中铭文" }
                ]
              }
            ]
          },
          {
            id: "c2",
            title: "夜行",
            displayNumber: null,
            wordCount: 400,
            scenes: [{ id: "s2", title: "空场景", wordCount: 0, blocks: [] }]
          }
        ]
      }
    ]
  };
}

beforeEach(() => {
  useUIStore.setState({ toasts: [] });
  service.projectPreview.mockReset();
  service.printProject.mockReset();
});

afterEach(() => {
  cleanup();
});

describe("PreviewPage 读取状态", () => {
  it("读取中显示状态提示", () => {
    service.projectPreview.mockReturnValue(new Promise(() => {}));
    render(<PreviewPage projectId="p1" />);
    expect(screen.getByRole("status").textContent).toContain("正在读取全书预览");
  });

  it("读取失败给出可重试的失败态，而不是伪装成空项目", async () => {
    service.projectPreview.mockRejectedValueOnce(new Error("无法读取创作工作区数据。"));
    render(<PreviewPage projectId="p1" />);
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("无法读取全书预览");
    expect(alert.textContent).toContain("无法读取创作工作区数据。");

    service.projectPreview.mockResolvedValueOnce(makeView());
    fireEvent.click(screen.getByRole("button", { name: /重试/ }));
    expect(await screen.findByText("第1章 风起")).toBeDefined();
  });

  it("作品不存在时给出失败态", async () => {
    service.projectPreview.mockResolvedValueOnce(null);
    render(<PreviewPage projectId="p1" />);
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("作品不存在或已被删除。");
  });

  it("没有章节时给出空状态且不渲染工具条", async () => {
    service.projectPreview.mockResolvedValueOnce({ projectId: "p1", title: "空作品", volumes: [] });
    render(<PreviewPage projectId="p1" />);
    expect(await screen.findByText("空作品")).toBeDefined();
    expect(screen.queryByRole("button", { name: /打印/ })).toBeNull();
  });
});

describe("PreviewPage 通读渲染", () => {
  beforeEach(() => {
    service.projectPreview.mockResolvedValue(makeView());
  });

  it("按章节渲染标题、块角色与场景标记", async () => {
    render(<PreviewPage projectId="p1" />);
    expect(await screen.findByText("第1章 风起")).toBeDefined();
    expect(screen.getByText("夜行")).toBeDefined();
    expect(screen.getByText("第一卷")).toBeDefined();

    // 引文渲染为 blockquote，作者按保留可读标注，分场符为分隔线。
    expect(screen.getByText("此信为证。").tagName).toBe("BLOCKQUOTE");
    expect(screen.getByText("作者按：作者的提醒。")).toBeDefined();
    expect(screen.getByText("居中铭文").className).toContain("preview-block--centered");

    // 场景标题在屏幕上可见，供定位；打印版式会隐藏它。
    expect(screen.getByText("雨夜")).toBeDefined();
  });

  it("空场景逐处标注，并汇总数量", async () => {
    render(<PreviewPage projectId="p1" />);
    expect(await screen.findByText("本场景暂无正文。")).toBeDefined();
    expect(screen.getByText(/其中 1 个场景尚无正文/)).toBeDefined();
  });

  it("汇总信息使用导出视图的字数，不在前端重新统计", async () => {
    render(<PreviewPage projectId="p1" />);
    expect(await screen.findByText(/共 1 卷 · 2 章 · 2 个场景 · 1,200 字/)).toBeDefined();
    expect(screen.getByText("800 字")).toBeDefined();
  });

  it("只读：不提供任何编辑入口", async () => {
    const { container } = render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");
    expect(container.querySelectorAll("[contenteditable], textarea, input")).toHaveLength(0);
    expect(screen.getByText(/只读通读页/)).toBeDefined();
  });

  it("跳转下拉列出全部章节，选择后滚动到对应章节", async () => {
    const scrollIntoView = vi.fn();
    // jsdom 未实现 scrollIntoView，这里补上以便断言跳转行为。
    Element.prototype.scrollIntoView = scrollIntoView;
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    const select = screen.getByLabelText("跳转到章节");
    expect(select.querySelectorAll("option")).toHaveLength(3);

    fireEvent.change(select, { target: { value: "c2" } });
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(document.getElementById("preview-chapter-c2")).not.toBeNull();
  });
});

describe("PreviewPage 打印", () => {
  beforeEach(() => {
    service.projectPreview.mockResolvedValue(makeView());
  });

  it("系统打印走 print 方式，成功时不弹提示", async () => {
    service.printProject.mockResolvedValue({ canceled: false, filePath: null });
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    fireEvent.click(screen.getByRole("button", { name: /打印…/ }));
    await waitFor(() => expect(service.printProject).toHaveBeenCalledWith("p1", "print"));
    expect(useUIStore.getState().toasts).toHaveLength(0);
  });

  it("导出 PDF 成功时提示目标文件路径", async () => {
    service.printProject.mockResolvedValue({ canceled: false, filePath: "D:\\导出\\测试作品-打印版.pdf" });
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    fireEvent.click(screen.getByRole("button", { name: /导出打印版 PDF/ }));
    await waitFor(() => expect(service.printProject).toHaveBeenCalledWith("p1", "pdf"));
    const toasts = useUIStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0]!.tone).toBe("success");
    expect(toasts[0]!.body).toContain("测试作品-打印版.pdf");
  });

  it("用户取消保存时不提示成功", async () => {
    service.printProject.mockResolvedValue({ canceled: true, filePath: null });
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    fireEvent.click(screen.getByRole("button", { name: /导出打印版 PDF/ }));
    await waitFor(() => expect(service.printProject).toHaveBeenCalled());
    expect(useUIStore.getState().toasts).toHaveLength(0);
  });

  it("主进程报告失败原因时给出错误提示", async () => {
    service.printProject.mockResolvedValue({ canceled: false, filePath: null, failureReason: "系统没有可用打印机。" });
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    fireEvent.click(screen.getByRole("button", { name: /打印…/ }));
    await waitFor(() => expect(useUIStore.getState().toasts).toHaveLength(1));
    expect(useUIStore.getState().toasts[0]!.tone).toBe("error");
    expect(useUIStore.getState().toasts[0]!.body).toContain("系统没有可用打印机。");
  });

  it("打印请求抛错时给出错误提示", async () => {
    service.printProject.mockRejectedValue(new Error("打印方式无效。"));
    render(<PreviewPage projectId="p1" />);
    await screen.findByText("第1章 风起");

    fireEvent.click(screen.getByRole("button", { name: /打印…/ }));
    await waitFor(() => expect(useUIStore.getState().toasts).toHaveLength(1));
    expect(useUIStore.getState().toasts[0]!.title).toBe("打印失败");
  });
});
