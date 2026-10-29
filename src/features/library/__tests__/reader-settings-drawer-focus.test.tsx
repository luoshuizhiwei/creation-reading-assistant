// @vitest-environment jsdom
/**
 * BD-201：阅读设置抽屉的焦点跌落家族（WCAG 2.4.3，BD-183/BD-199/BD-200 同族）。
 * 抽屉不是 Dialog（无 BD-127 的归还机制），三条关闭路径都把焦点所在的节点整棵
 * 卸载——「关闭」钮、遮罩 mousedown、宿主 Escape 直接翻 open=false——焦点跌回
 * body，键盘用户要从文档开头重新 Tab。修复复用 BD-183 的开手回交契约，挂在
 * [open] 的 effect cleanup 上，一并覆盖宿主驱动的 Escape 路径；开手被卸载时静默。
 * 抽屉内「更多设置」↔「返回阅读设置」的视图切换同样卸载焦点所在按钮，但抽屉
 * 没关，焦点应回到两视图都常驻的标题。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSettingsDrawer } from "@/features/library/ReaderSettingsDrawer";
import type { ReaderSettings } from "@/types/library";

vi.mock("@/services/reader-service", () => ({
  updateReaderSettings: vi.fn(),
  savePreset: vi.fn().mockResolvedValue(undefined)
}));
vi.mock("@/services/settings-service", () => ({
  resetReaderSettings: vi.fn()
}));

const settings: ReaderSettings = {
  fontSize: 18,
  lineHeight: 1.8,
  paragraphSpacing: 1,
  letterSpacing: 0,
  pageMargin: 56,
  appTheme: "system",
  readerBackground: "warm",
  epubStyleMode: "publisher",
  textConversion: "none",
  restoreLastPosition: true,
  readingMode: "scroll",
  tracking: {
    trackReadingSessions: true,
    idleTimeoutMs: 60000,
    progressSaveIntervalMs: 5000,
    sessionHeartbeatMs: 15000,
    sessionPersistIntervalMs: 30000,
    maxPausedBeforeNewSessionMs: 300000,
    endSessionOnBookSwitch: true,
    recordRecentReads: true,
    showReadingStatsCards: true
  },
  presets: []
};

function Harness() {
  const [open, setOpen] = React.useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen((prev) => !prev)}>
        顶栏开手
      </button>
      <button type="button" onClick={() => setOpen(false)}>宿主收起</button>
      <ReaderSettingsDrawer
        open={open}
        settings={settings}
        onClose={() => setOpen(false)}
        onSettingsChange={() => {}}
        onAfterChange={() => {}}
      />
    </div>
  );
}

afterEach(() => cleanup());

function openDrawer() {
  const view = render(<Harness />);
  const opener = screen.getByRole("button", { name: "顶栏开手" });
  // jsdom 的 fireEvent.click 不带焦点；真实浏览器里指针/键盘激活都会先聚焦按钮，
  // 这里显式模拟（BD-199 的同款注意事项）。
  opener.focus();
  fireEvent.click(opener);
  return { ...view, opener };
}

function drawerTitle() {
  const node = document.querySelector(".paper-title");
  if (!node) throw new Error("标题节点不存在");
  return node;
}

describe("BD-201：阅读设置抽屉关闭与视图切换的焦点回交", () => {
  it("抽屉内「关闭」钮卸载后焦点回到开手按钮", () => {
    const { opener } = openDrawer();
    const closeBtn = screen.getByRole("button", { name: "关闭" });
    closeBtn.focus();
    fireEvent.click(closeBtn);
    expect(document.activeElement).toBe(opener);
  });

  it("遮罩按下并松开关闭后焦点同样回到开手", () => {
    const { opener } = openDrawer();
    // 焦点在抽屉内部（如「更多设置」钮）时被遮罩点掉——卸载的是整个覆盖层。
    const moreBtn = screen.getByRole("button", { name: "更多设置" });
    moreBtn.focus();
    const backdrop = document.querySelector(".absolute.inset-0") as HTMLElement;
    // jsdom 的 mousedown 不会像在真实浏览器里那样把焦点 blur 回 body；不手动 blur
    // 的话，关闭后的 activeElement 本来就还在抽屉里，回交断言会假绿。
    moreBtn.blur();
    expect(document.activeElement).toBe(document.body); // 前提：blur 后焦点确实跌到了 body
    // BD-216（2.5.2）：遮罩只在「按下且松开都在遮罩上」时关闭，按下本身不再生效。
    fireEvent.pointerDown(backdrop);
    expect(document.activeElement).toBe(document.body);
    fireEvent.pointerUp(backdrop);
    fireEvent.click(backdrop);
    expect(document.activeElement).toBe(opener);
  });

  it("宿主直接把 open 翻成 false（Escape 快捷键路径）也完成回交", () => {
    const { opener } = openDrawer();
    // 真实 Escape 场景里焦点在抽屉内；不先把焦点放进去，卸载就没有焦点可跌，
    // 断言会因「根本没离开过开手」而假绿。
    const moreBtn = screen.getByRole("button", { name: "更多设置" });
    moreBtn.focus();
    fireEvent.click(screen.getByRole("button", { name: "宿主收起" }));
    expect(document.activeElement).toBe(opener);
  });

  it("「更多设置」切到二级视图时焦点落在常驻标题上，抽屉保持打开", () => {
    openDrawer();
    const moreBtn = screen.getByRole("button", { name: "更多设置" });
    moreBtn.focus();
    fireEvent.click(moreBtn);
    const title = drawerTitle();
    expect(title.textContent).toBe("更多设置");
    expect(document.activeElement).toBe(title);
    expect(title.getAttribute("tabindex")).toBe("-1");
  });

  it("「返回阅读设置」切回主视图时焦点同样回到常驻标题", () => {
    openDrawer();
    fireEvent.click(screen.getByRole("button", { name: "更多设置" }));
    const backBtn = screen.getByRole("button", { name: "返回阅读设置" });
    backBtn.focus();
    fireEvent.click(backBtn);
    const title = drawerTitle();
    expect(title.textContent).toBe("阅读设置");
    expect(document.activeElement).toBe(title);
  });

  it("开手已被卸载（整页退场）时静默，不报错也不抢焦点", () => {
    const external = document.createElement("button");
    document.body.appendChild(external);
    const view = render(
      <div>
        <button type="button" onClick={() => external.focus()}>聚焦外置钮</button>
        <ReaderSettingsDrawer
          open={false}
          settings={settings}
          onClose={() => {}}
          onSettingsChange={() => {}}
          onAfterChange={() => {}}
        />
      </div>
    );
    // 打开时开手是外置按钮（非宿主树内节点），随后把它从文档里摘掉再关抽屉。
    external.focus();
    view.rerender(
      <div>
        <button type="button" onClick={() => external.focus()}>聚焦外置钮</button>
        <ReaderSettingsDrawer
          open
          settings={settings}
          onClose={() => {}}
          onSettingsChange={() => {}}
          onAfterChange={() => {}}
        />
      </div>
    );
    external.remove();
    view.rerender(
      <div>
        <button type="button" onClick={() => external.focus()}>聚焦外置钮</button>
        <ReaderSettingsDrawer
          open={false}
          settings={settings}
          onClose={() => {}}
          onSettingsChange={() => {}}
          onAfterChange={() => {}}
        />
      </div>
    );
    expect(document.activeElement).toBe(document.body);
  });
});
