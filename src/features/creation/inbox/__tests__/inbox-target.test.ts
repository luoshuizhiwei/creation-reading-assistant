import { describe, expect, it } from "vitest";
import { resolveTargetProject } from "@/features/creation/inbox/inbox-target";

const projects = [{ id: "p1" }, { id: "p2" }, { id: "p3" }];

describe("收件箱目标项目解析", () => {
  it("用户当前选择仍存在时保留选择", () => {
    expect(resolveTargetProject(projects, undefined, "p2")).toBe("p2");
  });

  it("目标项目被移除后回退到第一个可用项目", () => {
    expect(resolveTargetProject(projects, undefined, "deleted")).toBe("p1");
    expect(resolveTargetProject(projects, undefined, "")).toBe("p1");
  });

  it("projectId prop 改变时优先同步到该 prop", () => {
    expect(resolveTargetProject(projects, "p3", "p1")).toBe("p3");
  });

  it("projectId prop 已不存在时安全回退", () => {
    expect(resolveTargetProject(projects, "deleted", "p2")).toBe("p2");
    expect(resolveTargetProject(projects, "deleted", "")).toBe("p1");
  });

  it("没有项目时返回 undefined，转换前可拦截", () => {
    expect(resolveTargetProject([], undefined, "")).toBeUndefined();
    expect(resolveTargetProject([], "deleted", "")).toBeUndefined();
  });
});
