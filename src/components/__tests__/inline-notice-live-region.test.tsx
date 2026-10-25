// @vitest-environment jsdom
/**
 * BD-173：动作之后才出现的行内提示必须是可感知的状态消息。
 *
 * InlineNotice 此前只是一个普通 div——更新检查、同步状态、AI 连通性、迁移结果
 * 这些「点完才出现」的话在屏幕上有了，读屏器却完全不知道（WCAG 4.1.3）。
 * 约定：错误走 alert（插入即播），其余走 status。
 */
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { InlineNotice } from "@/components/interaction";

afterEach(() => cleanup());

describe("InlineNotice 的 live region 语义（BD-173）", () => {
  it("普通/成功提示落在 status 区", () => {
    render(<InlineNotice tone="success">已导出调试信息</InlineNotice>);
    expect(screen.getByRole("status").textContent).toBe("已导出调试信息");
  });

  it("错误提示走 alert 断言播报", () => {
    render(<InlineNotice tone="error">目录不可写</InlineNotice>);
    expect(screen.getByRole("alert").textContent).toBe("目录不可写");
  });

  it("缺省 tone 也当普通提示播报", () => {
    render(<InlineNotice>默认提示</InlineNotice>);
    expect(screen.getByRole("status").textContent).toBe("默认提示");
  });

  // BD-191：宿主自带常驻播报区时，可见块退回纯视觉（announce={false}）。
  it("announce={false} 的可见块不再自带 polite 播报（播报归宿主常驻区）", () => {
    render(<InlineNotice tone="info" announce={false}>正在保存...</InlineNotice>);
    expect(screen.queryByRole("status")).toBeNull();
    expect(screen.getByText("正在保存...")).toBeTruthy();
  });

  it("announce={false} 也拦不住错误——alert 插入即播是合法形态（BD-189）", () => {
    render(<InlineNotice tone="error" announce={false}>目录不可写</InlineNotice>);
    expect(screen.getByRole("alert").textContent).toBe("目录不可写");
  });
});
