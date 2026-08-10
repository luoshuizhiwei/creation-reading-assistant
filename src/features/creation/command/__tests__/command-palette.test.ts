import { describe, expect, it } from "vitest";
import {
  KNOWN_KEYBINDS,
  commandPaletteKeybind,
  filterPaletteCommands,
  groupPaletteCommands,
  normalizeKey,
  registerKeybind,
  type PaletteCommand
} from "../command-palette";

describe("command-palette", () => {
  it("normalizeKey 规范化修饰键顺序与大小写", () => {
    expect(normalizeKey("Ctrl+Shift+F")).toBe("ctrl+f+shift");
    expect(normalizeKey("ctrl+f")).toBe("ctrl+f");
    expect(normalizeKey("Shift + Ctrl + F")).toBe("ctrl+f+shift");
  });

  it("registerKeybind 无冲突时注册成功", () => {
    const keybinds = new Map<string, { id: string; keys: string; description: string }>();
    const result = registerKeybind(keybinds, commandPaletteKeybind());
    expect(result.ok).toBe(true);
    expect(result.conflicts).toEqual([]);
    expect(keybinds.size).toBe(1);
  });

  it("registerKeybind 与已知占用冲突时拒绝注册", () => {
    const keybinds = new Map<string, { id: string; keys: string; description: string }>();
    const result = registerKeybind(keybinds, {
      id: "creation.search",
      keys: "Ctrl+K",
      description: "占用全局搜索"
    });
    expect(result.ok).toBe(false);
    expect(result.conflicts.length).toBe(1);
    expect(result.conflicts[0]!.id).toBe("global.search");
    expect(keybinds.size).toBe(0);
  });

  it("registerKeybind 与已注册项冲突时报告", () => {
    const keybinds = new Map<string, { id: string; keys: string; description: string }>();
    registerKeybind(keybinds, { id: "a", keys: "Ctrl+1", description: "A" });
    const second = registerKeybind(keybinds, { id: "b", keys: "ctrl+1", description: "B" });
    expect(second.ok).toBe(false);
    expect(second.conflicts.map((item) => item.id)).toEqual(["a"]);
  });

  it("filterPaletteCommands 按标签与关键词过滤", () => {
    const commands: PaletteCommand[] = [
      { id: "view.writing", label: "正文写作台", group: "视图", keywords: ["写作", "manuscript"] },
      { id: "project.create", label: "新建项目", group: "项目" },
      { id: "scene.goto", label: "跳转场景", group: "大纲" }
    ];
    expect(filterPaletteCommands(commands, "写作")).toHaveLength(1);
    expect(filterPaletteCommands(commands, "manuscript")).toHaveLength(1);
    expect(filterPaletteCommands(commands, "项目")).toHaveLength(1);
    expect(filterPaletteCommands(commands, "")).toHaveLength(3);
    expect(filterPaletteCommands(commands, "不存在")).toHaveLength(0);
  });

  it("groupPaletteCommands 保持分组与组内顺序", () => {
    const commands: PaletteCommand[] = [
      { id: "a", label: "A", group: "视图" },
      { id: "b", label: "B", group: "项目" },
      { id: "c", label: "C", group: "视图" }
    ];
    const groups = groupPaletteCommands(commands);
    expect(groups.map((group) => group.group)).toEqual(["视图", "项目"]);
    expect(groups[0]!.commands.map((command) => command.id)).toEqual(["a", "c"]);
  });

  it("命令面板自身快捷键不与已知占用冲突", () => {
    const result = registerKeybind(new Map(), commandPaletteKeybind());
    expect(result.ok).toBe(true);
    expect(KNOWN_KEYBINDS.some((item) => normalizeKey(item.keys) === normalizeKey(commandPaletteKeybind().keys))).toBe(false);
  });
});
