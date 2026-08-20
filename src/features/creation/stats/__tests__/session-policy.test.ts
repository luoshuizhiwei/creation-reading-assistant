import { describe, expect, it } from "vitest";
import {
  validateSessionCorrection,
  SESSION_DURATION_MAX_SECONDS,
  SESSION_NET_CHARS_LIMIT
} from "../session-policy";

const NOW = Date.parse("2026-08-14T10:00:00.000Z");

function draft(overrides: Partial<Parameters<typeof validateSessionCorrection>[0]> = {}) {
  return {
    projectId: "project-1",
    sessionId: "session-1",
    startedAt: "2026-08-14T08:30:00.000Z",
    activeSeconds: 1200,
    netChars: 400,
    ...overrides
  };
}

describe("session-policy 会话修正校验", () => {
  it("合法修正通过并规范化（ISO 时间、整型化秒数与净增）", () => {
    const result = validateSessionCorrection(draft(), NOW);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.startedAt).toBe("2026-08-14T08:30:00.000Z");
      expect(result.value.activeSeconds).toBe(1200);
      expect(result.value.netChars).toBe(400);
    }
  });

  it("startedAt 不可解析 → invalid-start", () => {
    const result = validateSessionCorrection(draft({ startedAt: "not-a-date" }), NOW);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.issues).toContain("invalid-start");
  });

  it("startedAt 明显晚于当前时间 → future-start", () => {
    const result = validateSessionCorrection(draft({ startedAt: new Date(NOW + 60 * 60 * 1000).toISOString() }), NOW);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.issues).toContain("future-start");
  });

  it("activeSeconds 负数 / 超上限 / 非整数 → duration-range 或 duration-type", () => {
    expect(validateSessionCorrection(draft({ activeSeconds: -1 }), NOW).ok).toBe(false);
    expect(validateSessionCorrection(draft({ activeSeconds: SESSION_DURATION_MAX_SECONDS + 1 }), NOW).ok).toBe(false);
    expect(validateSessionCorrection(draft({ activeSeconds: 10.5 }), NOW).ok).toBe(false);
    expect(validateSessionCorrection(draft({ activeSeconds: Number.NaN }), NOW).ok).toBe(false);
  });

  it("netChars 超限 / 非有限数 → net-chars-range 或 net-chars-type", () => {
    expect(validateSessionCorrection(draft({ netChars: SESSION_NET_CHARS_LIMIT + 1 }), NOW).ok).toBe(false);
    expect(validateSessionCorrection(draft({ netChars: -SESSION_NET_CHARS_LIMIT - 1 }), NOW).ok).toBe(false);
    expect(validateSessionCorrection(draft({ netChars: Number.NaN }), NOW).ok).toBe(false);
  });

  it("projectId / sessionId 缺失 → missing-project / missing-session", () => {
    const result = validateSessionCorrection(draft({ projectId: "  ", sessionId: "" }), NOW);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.issues).toContain("missing-project");
      expect(result.issues).toContain("missing-session");
    }
  });

  it("多个问题同时报告（不短路）", () => {
    const result = validateSessionCorrection(
      draft({ startedAt: "bad", activeSeconds: -5, netChars: 999_999_999 }),
      NOW
    );
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.issues).toEqual(expect.arrayContaining(["invalid-start", "duration-range", "net-chars-range"]));
    }
  });

  it("边界值合法：0 秒、0 净增、86400 秒、±1000000 净增", () => {
    expect(validateSessionCorrection(draft({ activeSeconds: 0, netChars: 0 }), NOW).ok).toBe(true);
    expect(validateSessionCorrection(draft({ activeSeconds: SESSION_DURATION_MAX_SECONDS }), NOW).ok).toBe(true);
    expect(validateSessionCorrection(draft({ netChars: SESSION_NET_CHARS_LIMIT }), NOW).ok).toBe(true);
    expect(validateSessionCorrection(draft({ netChars: -SESSION_NET_CHARS_LIMIT }), NOW).ok).toBe(true);
  });
});
