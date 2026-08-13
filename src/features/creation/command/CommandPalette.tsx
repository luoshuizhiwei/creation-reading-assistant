import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Command, CornerDownLeft, Keyboard, X } from "lucide-react";
import { useFocusRing } from "@/components/interaction";
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

/** IME 组合输入期间的键盘事件（中文拼音上屏、候选翻页等）一律不当作面板快捷键。 */
function isComposingEvent(event: ReactKeyboardEvent<HTMLInputElement>): boolean {
  return event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229;
}

export function CommandPalette({ commands, onClose }: CommandPaletteProps) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listboxId = useId();
  const inputRing = useFocusRing();
  const closeRing = useFocusRing();

  /** 打开前获得焦点的元素；面板关闭（卸载）时把焦点还给它。 */
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  if (!restoreFocusRef.current && document.activeElement instanceof HTMLElement) {
    restoreFocusRef.current = document.activeElement;
  }

  const filtered = useMemo(() => filterPaletteCommands(commands, query), [commands, query]);
  const groups = useMemo(() => groupPaletteCommands(filtered), [filtered]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    setSelected(0);
  }, [query]);

  const flat = useMemo(() => groups.flatMap((group) => group.commands), [groups]);
  const activeId = flat[selected] ? `${listboxId}-option-${flat[selected]!.id}` : undefined;

  useEffect(() => {
    const active = listRef.current?.querySelector<HTMLElement>("[data-active='true']");
    active?.scrollIntoView?.({ block: "nearest" });
  }, [selected]);

  useEffect(() => {
    const target = restoreFocusRef.current;
    return () => {
      if (target && document.contains(target) && typeof target.focus === "function") {
        target.focus({ preventScroll: true });
      }
    };
  }, []);

  const runSelected = () => {
    const command = flat[selected];
    if (!command) return;
    onClose();
    command.run();
  };

  const handleInputKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (isComposingEvent(event)) return;
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
      return;
    }
    if (event.key === "Tab") {
      event.preventDefault();
      closeRef.current?.focus();
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelected((current) => Math.max(0, Math.min(current + 1, flat.length - 1)));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelected((current) => Math.max(0, Math.min(current - 1, flat.length - 1)));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      runSelected();
    }
  };

  const handleCloseKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>) => {
    if (event.key === "Tab") {
      event.preventDefault();
      inputRef.current?.focus();
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
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
            aria-label="筛选命令"
            role="combobox"
            aria-expanded="true"
            aria-controls={listboxId}
            aria-activedescendant={activeId}
            aria-autocomplete="list"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            {...inputRing.handlers}
            onKeyDown={(event) => {
              inputRing.handlers.onKeyDown(event);
              handleInputKeyDown(event);
            }}
            style={inputRing.ringStyle}
          />
          <button
            ref={closeRef}
            type="button"
            className="creation-search-close"
            aria-label="关闭命令面板"
            onClick={onClose}
            {...closeRing.handlers}
            onKeyDown={(event) => {
              closeRing.handlers.onKeyDown(event);
              handleCloseKeyDown(event);
            }}
            style={closeRing.ringStyle}
          >
            <X size={16} />
          </button>
        </div>

        <div className="creation-search-results creation-palette-results">
          {flat.length === 0 && (
            <p className="creation-search-state" role="status">没有匹配的命令。</p>
          )}
          <ul ref={listRef} id={listboxId} role="listbox" aria-label="命令列表">
            {groups.flatMap((group) => [
              <li key={`group-${group.group}`} className="creation-palette-group" role="presentation">
                <h3>{group.group}</h3>
              </li>,
              ...group.commands.map((command) => {
                const index = flat.indexOf(command);
                const optionId = `${listboxId}-option-${command.id}`;
                return (
                  <li
                    key={command.id}
                    id={optionId}
                    role="option"
                    aria-selected={index === selected}
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
                  </li>
                );
              })
            ])}
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
