// Node 环境源码账：BD-219 的行内删除焦点交接不许被拆回散装。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-219 源码账：弹层/管理面板内条目删除的焦点交接", () => {
  const flows = [
    {
      label: "CreateProjectWizard 阶段 chip",
      rel: "../CreateProjectWizard.tsx",
      reserve: "pendingStageFocusRef.current = index;",
      handoff: "(target ?? workflowInputRef.current)?.focus({ preventScroll: true });",
      register: "stageDeleteRefs.current.set(index, node)",
      dep: "}, [draft.chapterWorkflow]);"
    },
    {
      label: "CardTypeEditor 字段行",
      rel: "../cards/CardTypeEditor.tsx",
      reserve: "pendingFieldFocusRef.current = index;",
      handoff: "(target ?? addFieldBtnRef.current)?.focus({ preventScroll: true });",
      register: "fieldDeleteRefs.current.set(index, node)",
      dep: "}, [fields]);"
    },
    {
      label: "TxtMarkdownReader 章节起点行",
      rel: "../../library/reader/TxtMarkdownReader.tsx",
      reserve: "pendingChapterFocusRef.current = i;",
      handoff: "target?.focus({ preventScroll: true });",
      register: "chapterDeleteRefs.current.set(i, node)",
      dep: "}, [draftChapters]);"
    }
  ] as const;

  for (const flow of flows) {
    it(`${flow.label}：预约在删除前、交接带退路、ref 登记齐备`, () => {
      const code = read(flow.rel);
      expect(code, `${flow.label} 缺 BD-219 判据注释`).toContain("BD-219");
      const reserveAt = code.indexOf(flow.reserve);
      expect(reserveAt, `${flow.label}：删除流丢了交接预约`).toBeGreaterThan(-1);
      // 预约必须紧挨着过滤/删除动作：先记账再卸载条目。
      const removeAt = code.indexOf("filter((_", reserveAt);
      expect(removeAt, `${flow.label}：预约之后找不到条目卸载动作，账约失效需重订`).toBeGreaterThan(reserveAt);
      expect(code, `${flow.label}：交接退路被拆`).toContain(flow.handoff);
      expect(code, `${flow.label}：删除钮不再登记 ref，交接无从找回`).toContain(flow.register);
      expect(code, `${flow.label}：交接 effect 的依赖列表变了，账约失效需重订`).toContain(flow.dep);
    });
  }
});
