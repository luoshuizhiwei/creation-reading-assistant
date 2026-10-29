// Node 环境源码账：BD-220 的页面级列表删除焦点交接不许被拆回散装。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-220 源码账：页面级列表条目删除的焦点交接", () => {
  it("OutlineTree：按钮带节点键、预约在删除前、树容器是退路", () => {
    const code = read("../outline/OutlineTree.tsx");
    expect(code, "OutlineTree 缺 BD-220 判据注释").toContain("BD-220");
    const reserveAt = code.indexOf("pendingDeleteFocusRef.current = { key, order };");
    expect(reserveAt, "OutlineTree：两步确认的第二击丢了交接预约").toBeGreaterThan(-1);
    const deleteAt = code.indexOf("runDelete(target)", reserveAt);
    expect(deleteAt, "OutlineTree：预约之后找不到删除动作，账约失效需重订").toBeGreaterThan(reserveAt);
    expect(code, "OutlineTree：条目没走时必须在动焦点前返回").toContain(
      "if (keys.includes(pending.key)) return;"
    );
    expect(code, "OutlineTree：退路被拆").toContain("(target ?? tree)?.focus({ preventScroll: true });");
    expect(code, "OutlineTree：删除钮不再挂节点键，交接无从定位").toContain("data-outline-node");
    expect(code, "OutlineTree：树容器不再是可聚焦退路").toContain('ref={treeRef} tabIndex={-1}');
    expect(code, "OutlineTree：交接 effect 的依赖列表变了").toContain("}, [outline]);");
  });

  it("StatsPage：行序快照在 await 前、删空退会话卡片", () => {
    const code = read("../stats/StatsPage.tsx");
    expect(code, "StatsPage 缺 BD-220 判据注释").toContain("BD-220");
    const reserveAt = code.indexOf(
      "pendingSessionFocusRef.current = { id: sessionId, order: sessions.map((item) => item.id) };"
    );
    expect(reserveAt, "StatsPage：删除流丢了交接预约").toBeGreaterThan(-1);
    const awaitAt = code.indexOf("await deleteSession({ projectId, sessionId });", reserveAt);
    expect(awaitAt, "StatsPage：预约必须记在 await 之前").toBeGreaterThan(reserveAt);
    expect(code, "StatsPage：失败时必须作废预约，避免下一次刷新抢焦点").toContain(
      "pendingSessionFocusRef.current = null;"
    );
    expect(code, "StatsPage：条目没走时必须在动焦点前返回").toContain(
      "if (current.includes(pending.id)) return;"
    );
    expect(code, "StatsPage：删空退路被拆").toContain("(target ?? sessionsCardRef.current)?.focus({ preventScroll: true });");
    expect(code, "StatsPage：删除钮不再登记 ref，交接无从找回").toContain(
      "sessionDeleteRefs.current.set(session.id, node)"
    );
    expect(code, "StatsPage：会话卡片不再是可聚焦退路").toContain('ref={sessionsCardRef} tabIndex={-1}');
    expect(code, "StatsPage：交接 effect 的依赖列表变了").toContain("}, [sessions]);");
  });
});
