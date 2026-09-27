import { describe, expect, it } from "vitest";
import type { SettingsSection } from "@/types/settings";
import { SETTINGS_SEARCH_REGISTRY, SETTINGS_SECTION_LABELS, searchSettings } from "../search-registry";

const VALID_SECTIONS: SettingsSection[] = ["appearance", "reader", "ai", "storage", "debug"];

describe("settings search registry", () => {
  it("每个条目的 id 唯一", () => {
    const ids = SETTINGS_SEARCH_REGISTRY.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("每个条目的 section 合法、label 与 group 非空", () => {
    for (const entry of SETTINGS_SEARCH_REGISTRY) {
      expect(VALID_SECTIONS).toContain(entry.section);
      expect(entry.label.trim().length).toBeGreaterThan(0);
      expect(entry.group.trim().length).toBeGreaterThan(0);
    }
  });

  it("分区标签覆盖全部五个分区", () => {
    for (const section of VALID_SECTIONS) {
      expect(SETTINGS_SECTION_LABELS[section]).toBeTruthy();
    }
  });
});

describe("searchSettings", () => {
  it("空查询不返回结果", () => {
    expect(searchSettings("")).toEqual([]);
    expect(searchSettings("   ")).toEqual([]);
  });

  it("按设置项名匹配（字号 → reader.fontSize）", () => {
    const results = searchSettings("字号");
    expect(results[0]?.id).toBe("reader.fontSize");
  });

  it("按关键词匹配（背景 → 书籍背景）", () => {
    const results = searchSettings("背景");
    expect(results.some((entry) => entry.id === "reader.readerBackground")).toBe(true);
  });

  it("按英文关键词匹配（epub）", () => {
    const results = searchSettings("epub");
    expect(results.some((entry) => entry.id === "reader.epubStyleMode")).toBe(true);
  });

  it("无匹配时返回空", () => {
    expect(searchSettings("不存在的设置项xyz")).toEqual([]);
  });

  it("结果不超过 limit", () => {
    expect(searchSettings("目录", 3).length).toBeLessThanOrEqual(3);
  });
});
