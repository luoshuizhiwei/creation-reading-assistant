/**
 * 批次 BD-95：锚点审计也进 `npm test`。
 *
 * 审计本身只有几十毫秒，但它是电池的前置条件：needle 一旦失配，那颗牙会在
 * 17 分钟的注入测试里记成 SKIP（= 无效），而不是当场报错。放进单元测试后，
 * 改 CSS 声明时就能立刻知道「有哨兵牙跟着哑了」。
 */
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

describe("电池锚点审计", () => {
  it("所有 needle 都要能在当前源码里命中且唯一", () => {
    const r = spawnSync(process.execPath, ["scripts/audit-test-anchors.mjs"], {
      cwd: root,
      encoding: "utf8",
    });
    expect(r.status, r.stderr || r.stdout).toBe(0);
    expect(r.stdout).toContain("ANCHOR_AUDIT_OK");
  }, 30_000);
});
