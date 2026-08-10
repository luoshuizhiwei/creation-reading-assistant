import { useEffect, useMemo, useRef, useState } from "react";
import { Command, CornerDownLeft, Keyboard, X } from "lucide-react";
import {
  KNOWN_KEYBINDS,
  filterPaletteCommands,
  groupPaletteCommands,
  type PaletteCommand
} from "@/features/creation/command/command-palette";

interface CommandPaletteProps {
  commands: PaletteCommand[];
  onClose(): void;
}

export function CommandPalette({ commands, onClose }: CommandPaletteProps) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const filtered = useMemo(() => filterPaletteCommands(commands, query), [commands, query]);
  const groups = useMemo(() => groupPaletteCommands(filtered), [filtered]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    setSelected(0);
  }, [query]);

  const flat = useMemo(() => groups.flatMap((group) => group.commands), [groups]);

  useEffect(() => {
    const active = listRef.current?.querySelector<HTMLElement>("[data-active='true']");
    active?.scrollIntoView({ block: "nearest" });
  }, [selected]);

  const runSelected = () => {
    const command = flat[selected];
    if (command) {
      onClose();
      command.run();
    }
  };

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="命令面板" aria-modal="true">
      <div className="creation-search-shell creation-palette-shell" role="search">
        <div className="creation-search-head">
          <Command size={16} className="creation-search-head-icon" />
          <input
            ref={inputRef}
            className="creation-search-input"
            placeholder="输入命令或搜索项目 / 场景…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") onClose();
              if (event.key === "ArrowDown") {
                event.preventDefault();
                setSelected((current) => Math.min(current + 1, flat.length - 1));
              }
              if (event.key === "ArrowUp") {
                event.preventDefault();
                setSelected((current) => Math.max(current - 1, 0));
              }
              if (event.key === "Enter") runSelected();
            }}
          />
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭命令面板">
            <X size={16} />
          </button>
        </div>

        <div className="creation-search-results creation-palette-results">
          {flat.length === 0 && <p className="creation-search-state">没有匹配的命令。</p>}
          <ul ref={listRef}>
            {groups.map((group) => (
              <li key={group.group} className="creation-palette-group">
                <h3>{group.group}</h3>
                <ul>
                  {group.commands.map((command) => {
                    const index = flat.indexOf(command);
                    return (
                      <li key={command.id}>
                        <button
                          type="button"
                          className="creation-palette-command"
                          data-active={index === selected}
                          onMouseEnter={() => setSelected(index)}
                          onClick={runSelected}
                        >
                          <span className="creation-palette-command-label">{command.label}</span>
                          <span className="creation-palette-command-keys">
                            {command.shortcut && <kbd>{command.shortcut}</kbd>}
                            {index === selected && <CornerDownLeft size={13} />}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        </div>

        <div className="creation-palette-footer">
          <span className="creation-palette-footer-hint">
            <Keyboard size={13} /> ↑↓ 选择 · Enter 执行 · Esc 关闭
          </span>
          <span className="creation-palette-footer-keys">
            {KNOWN_KEYBINDS.slice(0, 4).map((entry) => (
              <span key={entry.id} title={entry.description}><kbd>{entry.keys}</kbd> {entry.id.split(".")[1]}</span>
            ))}
          </span>
        </div>
      </div>
    </div>
  );
}
