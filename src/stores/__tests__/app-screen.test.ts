import { describe, expect, it } from "vitest";
import { APP_SCREENS } from "@/stores/app-store";

describe("AppScreen 覆盖", () => {
  it("不再包含已移除的 start 屏幕", () => {
    expect(APP_SCREENS).not.toContain("start");
  });

  it("应用级屏幕恰好是可渲染的七项", () => {
    expect([...APP_SCREENS]).toEqual([
      "projects",
      "inbox",
      "inspiration",
      "library",
      "reader",
      "stats",
      "settings"
    ]);
  });
});
