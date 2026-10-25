// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-191 源码账：InlineNotice 族与页面横幅的 polite 播报必须由常驻区承载。
 *
 * BD-173 给 InlineNotice 挂了 role="status"，宿主却常年 `{msg && <InlineNotice/>}`
 * 条件挂载——「插入即带内容」的 polite 区是哑的（BD-175/180/187 判据），消息
 * 清空再重发还要再哑一次。迁移横幅、大纲撤回条、恢复提示三类页面级横幅同理，
 * 且横幅里有按钮，本不该整体当 status 容器。修复形态：
 *  1) 每个案发宿主常驻一条 `sr-only role="status"` 播报区、只换文本；
 *  2) 当年自带 role="status" 的可见块不得回潮（InlineNotice 走 announce={false}，
 *     横幅直接摘掉 role）；
 *  3) InlineNotice 的 status 语义只在 announce 时存在——错误仍走 alert（插入即播合法）。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const PERSISTENT = /<span className="sr-only" role="status">/g;

const hosts: Array<{ file: string; regions: number; banned: RegExp[] }> = [
  {
    file: "../../settings/sections/AISection.tsx",
    regions: 2,
    banned: [/<InlineNotice tone="(info|warning)" className=/]
  },
  {
    file: "../../settings/sections/DebugSection.tsx",
    regions: 2,
    banned: [/<InlineNotice tone="(info|success)" className=/, /tone=\{updateInfo\.hasUpdate \? "success" : "info"\} className=/]
  },
  {
    file: "../../settings/sections/StorageSection.tsx",
    regions: 2,
    banned: [/<InlineNotice tone="(info|warning)" className=/]
  },
  {
    file: "../editor/WritingDesk.tsx",
    regions: 1,
    banned: [/tone="warning" className="writing-recovery-notice"/]
  },
  {
    file: "../outline/OutlinePage.tsx",
    regions: 1,
    banned: [/className="outline-revert-bar" role="status"/]
  },
  {
    file: "../CreationProjectsPage.tsx",
    regions: 1,
    banned: [/className="migration-banner" role="status"/]
  }
];

describe("BD-191 源码账：播报区常驻化", () => {
  for (const host of hosts) {
    it(`${host.file.split("../").pop()}：常驻播报区在位数达标，可见块不再自带 polite 播报`, () => {
      const src = read(host.file);
      const found = src.match(PERSISTENT)?.length ?? 0;
      expect(found, `应有 ${host.regions} 条常驻 sr-only 播报区，实为 ${found}`).toBeGreaterThanOrEqual(host.regions);
      for (const banned of host.banned) {
        expect(banned.test(src), `回退：${banned.source} 又把播报挂回了条件挂载的可见块`).toBe(false);
      }
      expect(/BD-191/.test(src), "缺少 BD-191 判据注释").toBe(true);
    });
  }

  it("WritingDesk 恢复提示的文案住在常驻区里（异步落定时换文本才播得出来）", () => {
    const src = read("../editor/WritingDesk.tsx");
    expect(
      /<span className="sr-only" role="status">\s*\{abnormalExit && !recoveryNoticeDismissed/.test(src),
      "回退：常驻区不再随 abnormalExit 换文本，恢复提示又回到插入即哑"
    ).toBe(true);
  });

  it("InlineNotice：status 语义受 announce 开关控制，错误仍走 alert", () => {
    const src = read("../../../components/interaction.tsx");
    expect(/announce = true/.test(src), "announce 缺省必须为 true（BD-173 的独立用法不受牵连）").toBe(true);
    expect(/announce \? "status" : undefined/.test(src), "非错误 tone 的 status 必须由 announce 门控").toBe(true);
    expect(/tone === "error" \? "alert" : "status"/.test(src), "回退：无条件 status 又回来了").toBe(false);
  });
});
