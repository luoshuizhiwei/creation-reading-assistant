/**
 * BD-202 账本：路由切换的焦点着陆点必须长在 PageTransition 的容器上，
 * 且 App 的两条分支（reader 直挂 / DesktopFrame 包裹）都继续以 screen 为 key
 * 整树重挂——着陆点靠的就是这个重挂节奏。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function source(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

const interactionSrc = source("../interaction.tsx");
const appSrc = source("../../app/App.tsx");

describe("BD-202 账本：路由着陆点接线", () => {
  it("PageTransition 容器带 tabIndex=-1 与 ref，换 key 即聚焦且 preventScroll", () => {
    const pt = interactionSrc.slice(
      interactionSrc.indexOf("export function PageTransition"),
      interactionSrc.indexOf("export function AnimatedPanel")
    );
    expect(/useEffect\(\(\) => \{\s*boxRef\.current\?\.focus\(\{ preventScroll: true \}\);\s*\}, \[screenKey\]\);/.test(pt), "回退：着陆 effect 被拆或不再跟 screenKey 走").toBe(true);
    expect(/<div key=\{screenKey\} ref=\{boxRef\} className="motion-page h-full" tabIndex=\{-1\}>/.test(pt), "回退：容器不再是可聚焦着陆点或 ref/key 脱钩").toBe(true);
  });

  it("App 两条分支都仍以 screen 为 key 挂 PageTransition", () => {
    const mounts = appSrc.match(/<PageTransition screenKey=\{screen\}>/g) ?? [];
    expect(mounts.length, "回退：路由整树重挂的形状变了，着陆点覆盖面要重新核").toBe(2);
  });
});
