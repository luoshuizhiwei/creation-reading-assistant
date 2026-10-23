/**
 * 批次 BD-93：把设计令牌守卫接进 `npm test`（CI 唯一会跑的入口）。
 *
 * 背景：CI 只跑 `npm test`、`npm run build` 和两个 verify 脚本，
 * 守卫从未进过流水线——前面几十批立下的红线，只在开发者本机敲
 * `npm run verify:design-tokens` 时才生效，跳过它就能把改版推上去。
 * 这里用一条集成测试把守卫拉进 vitest，不改 `.github/workflows/ci.yml`
 * （共享流水线配置的改动要单独批），也不改变守卫本身的行为。
 *
 * 代价是每次跑测试多起一个 node 进程（实测约 2.2s）。守卫失败时把它的
 * stderr 原样带进断言消息，红因仍指向具体那一节，不必再跑一遍才知道原因。
 */
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

describe("设计令牌守卫（集成）", () => {
  it("verify-design-tokens.mjs 在当前代码状态下必须通过", () => {
    const r = spawnSync(process.execPath, ["scripts/verify-design-tokens.mjs"], {
      cwd: root,
      encoding: "utf8",
    });
    expect(r.status, r.stderr || r.stdout).toBe(0);
    expect(r.stdout).toContain("全部通过");
  }, 60_000);
});
