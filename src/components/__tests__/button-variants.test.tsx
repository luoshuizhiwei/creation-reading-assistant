// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { Button } from "../ui";

afterEach(cleanup);

describe("Button 强调层级（规格 §2）", () => {
  it("渲染全部 7 个 variant，且都不带阴影", () => {
    const variants = ["primary", "tonal", "outline", "ghost", "icon", "danger-outline", "danger-filled"] as const;
    for (const variant of variants) {
      render(
        <Button variant={variant} data-testid={`b-${variant}`}>
          保存修正
        </Button>
      );
    }
    for (const variant of variants) {
      const cls = screen.getByTestId(`b-${variant}`).className;
      expect(cls, `${variant} 不应有阴影`).not.toMatch(/(^|\s)shadow-/);
    }
  });

  it("反馈只靠颜色：无位移、无缩放", () => {
    render(<Button variant="primary">导出加密备份</Button>);
    const cls = screen.getByRole("button", { name: "导出加密备份" }).className;
    expect(cls).toMatch(/transition-colors/);
    expect(cls).not.toMatch(/translate|scale/);
    // 只允许 transition-colors，不允许整属性 transition 把位移也带上
    expect(cls.match(/transition[\w-]*/g)?.filter((t) => t !== "transition-colors")).toHaveLength(0);
  });

  it("键盘焦点环使用 --focus-ring 令牌", () => {
    render(<Button variant="primary">保存</Button>);
    const cls = screen.getByRole("button", { name: "保存" }).className;
    // 不能写成 shadow-[var(--focus-ring)]：Tailwind 会当成阴影颜色、不产出 box-shadow 声明
    expect(cls).toMatch(/focus-visible:\[box-shadow:var\(--focus-ring\)\]/);
    expect(cls).not.toMatch(/shadow-\[var\(--focus-ring\)\]/);
  });

  it("旧 variant 名继续可用并归一到新样式", () => {
    render(<Button variant="secondary">次要</Button>);
    const el = screen.getByRole("button", { name: "次要" });
    expect(el.className).toMatch(/border-paper-line/); // == outline
    // editorial-studio.css 用 [data-variant="quiet"] 做排除，原值必须保留
    expect(el.getAttribute("data-variant")).toBe("secondary");

    render(<Button variant="quiet" key="q">弱化</Button>);
    expect(screen.getByRole("button", { name: "弱化" }).className).toMatch(/text-paper-muted/); // == ghost
  });

  it("danger 两档都走 --proof-mark，不复用主操作色", () => {
    render(
      <>
        <Button variant="danger-outline" data-testid="d1">
          移入回收站
        </Button>
        <Button variant="danger-filled" data-testid="d2">
          删除这 3 章
        </Button>
      </>
    );
    for (const id of ["d1", "d2"]) {
      expect(screen.getByTestId(id).className).toMatch(/var\(--proof-mark\)/);
    }
    expect(screen.getByTestId("d2").className).toMatch(/bg-\[color:var\(--proof-mark\)\]/);
    expect(screen.getByTestId("d1").className).not.toMatch(/bg-\[color:var\(--proof-mark\)\]/);
  });

  it("三档尺寸高度可辨（sm28 / md36 / lg40）", () => {
    render(
      <>
        <Button size="sm" data-testid="s">
          小
        </Button>
        <Button data-testid="m">中</Button>
        <Button size="lg" data-testid="l">大</Button>
      </>
    );
    expect(screen.getByTestId("s").className).toMatch(/\bh-7\b/);
    // md 暂留 36px，与 .paper-input 同批收敛
    expect(screen.getByTestId("m").className).toMatch(/\bh-9\b/);
    expect(screen.getByTestId("l").className).toMatch(/\bh-10\b/);
  });

  it("icon 尺寸为正方形且带 aria-label", () => {
    render(
      <Button variant="icon" aria-label="关闭侧栏">
        ×
      </Button>
    );
    const el = screen.getByRole("button", { name: "关闭侧栏" });
    expect(el.className).toMatch(/\bh-9\b/);
    expect(el.className).toMatch(/\bw-9\b/);
    expect(el.className).toMatch(/\bp-0\b/);
  });

  it("loading 置灰、aria-busy，且保留原文字撑宽度", () => {
    render(
      <Button variant="primary" loading>
        正在保存
      </Button>
    );
    const el = screen.getByRole("button", { name: /正在保存/ });
    // 仓库未安装 @testing-library/jest-dom，用原生属性断言
    expect((el as HTMLButtonElement).disabled).toBe(true);
    expect(el.getAttribute("aria-busy")).toBe("true");
    // 不可见的占位文字保证按钮宽度不跳
    const holder = el.querySelector("span.invisible");
    expect(holder?.textContent).toBe("正在保存");
    expect(el.querySelector("svg")).toBeTruthy();
  });

  it("disabled 透明度为 .55（不再是 0.45/0.5/0.4 三种）", () => {
    render(<Button disabled>禁用</Button>);
    expect(screen.getByRole("button", { name: "禁用" }).className).toMatch(/disabled:opacity-55/);
  });

  it("实色色块上的前景走 --fg-on-solid，不写死白字", () => {
    // 夜校主题 --action-primary=#7fa5d9（浅蓝），白字只有 2.53:1，不达 AA 4.5:1
    render(
      <>
        <Button variant="primary" data-testid="p">
          保存修正
        </Button>
        <Button variant="danger-filled" data-testid="d">
          删除这 3 章
        </Button>
      </>
    );
    for (const id of ["p", "d"]) {
      const cls = screen.getByTestId(id).className;
      expect(cls).toMatch(/text-\[color:var\(--fg-on-solid\)\]/);
      expect(cls, `${id} 不应写死 text-white`).not.toMatch(/(^|\s)text-white(\s|$)/);
    }
  });

  it("未显式给 type 时默认 button，避免误提交表单", () => {
    render(<Button>默认类型</Button>);
    expect(screen.getByRole("button", { name: "默认类型" }).getAttribute("type")).toBe("button");
  });

  it("className 追加在后面，调用方仍可覆盖", () => {
    render(
      <Button variant="primary" className="my-own">
        覆盖
      </Button>
    );
    const el = screen.getByRole("button", { name: "覆盖" });
    expect(el.className).toMatch(/my-own/);
    expect(el.className).toMatch(/bg-copper\b/);
  });
});
