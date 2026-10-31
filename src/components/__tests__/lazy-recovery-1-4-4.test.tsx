// @vitest-environment jsdom
/**
 * BD-223 账本：WCAG 1.4.4「放大到 200% 不丢内容与功能」里懒加载路由的那条通道（连同 2.4.6 名实一致）。
 *
 * 主判据是运行时普查 scripts/audit-lazy-recovery.mjs：把三条屏的路由 chunk 在产物里改名，
 * 200% 下进那一屏，核对 ① 注入真咬到（requestfailed + 兜底卡）、② 卡里每颗出路按钮滚得进视口、
 * ③ 主按钮把用户带回**原来那一屏**。
 *
 * 本批真实缺陷（实测链，详见 src/app/lazy-recovery.ts 顶部）：路由 chunk 一次读取失败后，
 * Chromium 的模块图把这条 specifier 永久记成 rejected——文件放回原位、同一文档里再 import() 也只
 * 0ms 抛错、不再发请求。而旧兜底卡的「重试当前页面」做的是 setScreen("projects"/"library")，
 * 名字说重试、行为是甩去另一屏；「重新加载应用」又落回默认书架。于是 200% 下这一屏的内容与功能
 * 实实在在丢了。修复：chunk 类失败给「重新载入并回到这一屏」（先记屏再换模块图，启动序列读回），
 * 换屏退路单独成一颗有名字的按钮；渲染异常保留就地重试。
 *
 * 路径账（__tests__/ 起算）：../../../ = 仓库根；../../app = src/app/。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { isChunkLoadFailure, rememberReturnScreen, takeReturnScreen } from "@/app/lazy-recovery";
import * as lazyRecovery from "@/app/lazy-recovery";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-lazy-recovery.mjs");
const pkg = read("../../../package.json");
const app = read("../../app/App.tsx");
const boot = read("../../app/main.tsx");
const helper = read("../../app/lazy-recovery.ts");

const CHUNK_ERROR = "Failed to fetch dynamically imported module: file:///app/out/renderer/assets/SettingsPage-abc123.js";
const SCREENS = ["projects", "card-library", "inbox", "inspiration", "library", "reader", "stats", "settings"] as const;

/** 由外部闸门决定要不要抛的探针：就地重试那条腿要看得见「重试真的回到了子树」。
 *  开关不能放在组件内部（出错那棵子树会被边界摘掉，重试时是重新挂载，内部 state 会复位），
 *  也不能只靠一次性布尔——React 开发模式在边界接管时会把同一段 render 再走一遍，一次性的抛
 *  在第一次就被消耗掉，边界根本停不住。 */
function ThrowWhileArmed({ message, label, gate }: { message: string; label: string; gate: { armed: boolean } }) {
  if (gate.armed) throw new Error(message);
  return <p>{label}</p>;
}

function AlwaysThrows({ message }: { message: string }) {
  throw new Error(message);
}

let consoleError: ReturnType<typeof vi.spyOn>;
beforeEach(() => {
  consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
  window.history.replaceState(null, "", "/");
});
afterEach(() => {
  cleanup();
  consoleError.mockRestore();
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

describe("BD-223 谓词：哪一类失败必须走重载", () => {
  it("三种内核的 chunk 加载失败文案都认，普通渲染异常不认", () => {
    expect(isChunkLoadFailure(new Error(CHUNK_ERROR))).toBe(true);
    expect(isChunkLoadFailure(new Error("error loading dynamically imported module: x.js"))).toBe(true);
    expect(isChunkLoadFailure(new Error("Unable to preload CSS for /assets/SettingsPage-abc.css"))).toBe(true);
    expect(isChunkLoadFailure(new Error("Cannot read properties of undefined (reading 'map')"))).toBe(false);
    expect(isChunkLoadFailure(undefined)).toBe(false);
    expect(isChunkLoadFailure({})).toBe(false);
  });
});

describe("BD-223 重载目标片段：写下、读一次即清、名单校验", () => {
  it("记屏再读回得到那一屏，且片段被擦掉（第二次读为空）", () => {
    rememberReturnScreen("card-library");
    expect(decodeURIComponent(window.location.hash)).toBe("#return-to=card-library");
    expect(takeReturnScreen(SCREENS)).toBe("card-library");
    expect(window.location.hash).toBe("");
    expect(takeReturnScreen(SCREENS)).toBeNull();
  });

  it("名单外的值不认，但片段照样擦掉——不给下一次启动留后门", () => {
    window.history.replaceState(null, "", "/#return-to=editor");
    expect(takeReturnScreen(SCREENS)).toBeNull();
    expect(window.location.hash).toBe("");
  });

  it("别人的片段（不是 return-to）不动它", () => {
    window.history.replaceState(null, "", "/#chapter-3");
    expect(takeReturnScreen(SCREENS)).toBeNull();
    expect(window.location.hash).toBe("#chapter-3");
  });
});

describe("BD-223 兜底卡：chunk 失败与渲染异常给的是两条不同的出路", () => {
  it("chunk 失败：标题说「这一屏没能载入」，主出路是「重新载入并回到这一屏」，且不再有「重试当前页面」", () => {
    const reload = vi.spyOn(lazyRecovery, "reloadDocument").mockImplementation(() => {});
    render(
      <ErrorBoundary returnScreen="settings">
        <AlwaysThrows message={CHUNK_ERROR} />
      </ErrorBoundary>
    );
    expect(screen.getByRole("heading", { name: "这一屏没能载入" })).toBeTruthy();
    const primary = screen.getAllByRole("button")[0];
    expect(primary.textContent).toContain("重新载入并回到这一屏");
    expect(screen.queryByRole("button", { name: /重试当前页面/ })).toBeNull();
    fireEvent.click(primary);
    expect(decodeURIComponent(window.location.hash)).toBe("#return-to=settings");
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("没有 returnScreen 的屏（阅读器那条）不假装能回到原屏，只给整应用重载", () => {
    const reload = vi.spyOn(lazyRecovery, "reloadDocument").mockImplementation(() => {});
    render(
      <ErrorBoundary escapeLabel="退出阅读并返回书库" onEscape={() => {}}>
        <AlwaysThrows message={CHUNK_ERROR} />
      </ErrorBoundary>
    );
    const primary = screen.getAllByRole("button")[0];
    expect(primary.textContent).toContain("重新加载应用");
    fireEvent.click(primary);
    expect(window.location.hash).toBe("");
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("渲染异常：主出路是在地重试，点它不重载、不跳屏，重试后子树回来了", () => {
    const gate = { armed: true };
    const reload = vi.spyOn(lazyRecovery, "reloadDocument").mockImplementation(() => {});
    render(
      <ErrorBoundary returnScreen="inbox">
        <ThrowWhileArmed message="Cannot read properties of undefined (reading 'map')" label="收件箱回来了" gate={gate} />
      </ErrorBoundary>
    );
    expect(screen.getByRole("heading", { name: "页面发生意外错误" })).toBeTruthy();
    const primary = screen.getAllByRole("button")[0];
    expect(primary.textContent).toContain("重试当前页面");
    gate.armed = false;
    fireEvent.click(primary);
    expect(reload).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");
    expect(screen.getByText("收件箱回来了")).toBeTruthy();
  });

  it("退路单独成一颗，名字就是它要做的事（不再藏在「重试」里）", () => {
    render(
      <ErrorBoundary returnScreen="inbox" escapeLabel="先回项目书架" onEscape={() => {}}>
        <AlwaysThrows message={CHUNK_ERROR} />
      </ErrorBoundary>
    );
    expect(screen.getByRole("button", { name: "先回项目书架" })).toBeTruthy();
    const buttons = screen.getAllByRole("button").map((b) => (b.textContent || "").trim());
    expect(buttons.some((t) => t.startsWith("重试当前页面"))).toBe(false);
  });
});

describe("BD-223 接线与普查登记", () => {
  it("App 两条挂法：桌面壳交出当前屏，阅读器那条不交", () => {
    expect(app).toContain("returnScreen={screen}");
    expect(app).toContain('<ErrorBoundary escapeLabel="退出阅读并返回书库"');
    expect(app).not.toContain("onReset=");
  });

  it("启动序列在挂载前读回那一屏（读一次即清）", () => {
    expect(boot).toContain("takeReturnScreen(APP_SCREENS)");
    expect(boot).toContain("useAppStore.getState().setScreen(returnScreen)");
    expect(boot.indexOf("takeReturnScreen") < boot.indexOf("createRoot")).toBe(true);
  });

  it("普查登记在册，判据骨架齐（下限、注入核对、出路可达、名实一致）", () => {
    expect(pkg).toContain('"audit:lazy-recovery": "node scripts/audit-lazy-recovery.mjs"');
    expect(census).toContain("LAZY_MIN = 3");
    expect(census).toContain("注入未咬到");
    expect(census).toContain("在 200% 下够不着");
    expect(census).toContain("没能把用户带回这一屏");
    expect(census).toContain("getContentBounds");
    expect(census).toContain("process.exit(exitCode)");
    expect(census).not.toContain("process.exit(1)");
  });

  it("出路可达性按「容器可由用户滚动」判，不借 scrollIntoViewIfNeeded 判（牙齿第 3 轮的教训）", () => {
    // 实测：祖先链全 overflow:hidden 而 scrollHeight>clientHeight 时，CDP 的程序化滚动照样把按钮
    // 「滚进视口」，鼠标滚轮却什么也滚不出来——用 scrollIntoViewIfNeeded 判可达性，裁掉的出路会判成绿。
    expect(census).toContain("getComputedStyle(el).overflowY");
    expect(census).toContain("祖先里没有一只可滚容器");
    const reach = census.slice(census.indexOf("兜底卡自己的可达性"), census.indexOf("// 点之前先还原 chunk"));
    expect(reach).not.toContain(".scrollIntoViewIfNeeded(");
  });

  it("恢复通道只留一处重载写法（jsdom 里 location.reload 不可改写，这条是它的注入点）", () => {
    expect(helper).toContain("export function reloadDocument(): void");
    expect(census).not.toContain("reloadDocument");
  });

  it("兜底卡外壳自己滚得动：200% 下卡片比视口高时贴顶可滚，而不是居中裁底", () => {
    const boundary = read("../../components/ErrorBoundary.tsx");
    expect(boundary).toContain('className="flex h-full justify-center overflow-y-auto p-6 text-center paper-shell"');
    expect(boundary).toContain('className="m-auto max-w-md');
    // 旧写法 `grid h-full place-items-center` 的正中摆放会把卡片底边推到视口外，
    // 祖先链又全是 overflow:hidden——第三颗出路按钮就此消失（牙齿第 3 轮实测的红就是这个）。
    expect(boundary).not.toContain("place-items-center p-6");
  });
});
