export interface PaletteCommand {
  id: string;
  label: string;
  group: string;
  /** 过滤关键词（如拼音首字母/别名）。 */
  keywords?: string[];
  /** 快捷键显示（如 Ctrl+P）；不参与冲突判定。 */
  shortcut?: string;
  run(): void;
}

export interface KeybindEntry {
  id: string;
  keys: string;
  description: string;
}

/** 创作域已知占用的快捷键（外部/编辑器层），用于冲突检测与展示。 */
export const KNOWN_KEYBINDS: KeybindEntry[] = [
  { id: "editor.save", keys: "Ctrl+S", description: "写作台：立即保存正文" },
  { id: "editor.pasteClean", keys: "Ctrl+Shift+V", description: "写作台：粘贴清洗" },
  { id: "global.search", keys: "Ctrl+K", description: "全局搜索（创作域不占用）" },
  { id: "global.searchBody", keys: "Ctrl+Shift+F", description: "全局正文搜索（创作域不占用）" },
  { id: "dialog.close", keys: "Esc", description: "关闭当前对话框" }
];

const PALETTE_KEYBIND_ID = "creation.commandPalette";
const PALETTE_KEYS = "Ctrl+P";

/** 注册快捷键并检测冲突；返回冲突的已注册项（含已知占用）。 */
export function registerKeybind(
  keybinds: Map<string, KeybindEntry>,
  entry: KeybindEntry
): { ok: boolean; conflicts: KeybindEntry[] } {
  const normalized = normalizeKey(entry.keys);
  const conflicts = [...KNOWN_KEYBINDS, ...keybinds.values()]
    .filter((existing) => existing.id !== entry.id && normalizeKey(existing.keys) === normalized);
  if (conflicts.length === 0) keybinds.set(entry.id, entry);
  return { ok: conflicts.length === 0, conflicts };
}

export function normalizeKey(keys: string): string {
  return keys
    .toLowerCase()
    .split("+")
    .map((part) => part.trim())
    .filter(Boolean)
    .sort()
    .join("+");
}

export function commandPaletteKeybind(): KeybindEntry {
  return { id: PALETTE_KEYBIND_ID, keys: PALETTE_KEYS, description: "打开创作命令面板" };
}

/** 按输入过滤命令：label + keywords 子串匹配（大小写不敏感），按分组保持顺序。 */
export function filterPaletteCommands(commands: PaletteCommand[], query: string): PaletteCommand[] {
  const trimmed = query.trim().toLowerCase();
  if (!trimmed) return commands;
  return commands.filter((command) => {
    const haystack = [command.label, command.group, ...(command.keywords ?? [])].join(" ").toLowerCase();
    return haystack.includes(trimmed);
  });
}

/** 分组展示顺序（未列出的组排在最后，按首次出现顺序）。 */
export function groupPaletteCommands(commands: PaletteCommand[]): Array<{ group: string; commands: PaletteCommand[] }> {
  const order: string[] = [];
  const byGroup = new Map<string, PaletteCommand[]>();
  for (const command of commands) {
    if (!byGroup.has(command.group)) {
      byGroup.set(command.group, []);
      order.push(command.group);
    }
    byGroup.get(command.group)!.push(command);
  }
  return order.map((group) => ({ group, commands: byGroup.get(group)! }));
}
