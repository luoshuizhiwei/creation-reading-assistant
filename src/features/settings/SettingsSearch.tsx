import { useEffect, useMemo, useRef, useState } from "react";
import { Search } from "lucide-react";
import { SETTINGS_SECTION_LABELS, searchSettings, type SettingsSearchEntry } from "./search-registry";

export interface SettingsSearchProps {
  onSelect(entry: SettingsSearchEntry): void;
}

/**
 * SettingsSearch — 设置项搜索框
 *
 * 检索静态注册表（search-registry.ts），选中后由 SettingsPage
 * 切换到对应分区、滚动到控件并短暂高亮。
 */
export function SettingsSearch({ onSelect }: SettingsSearchProps) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);

  const results = useMemo(() => searchSettings(query), [query]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  useEffect(() => {
    if (!open) return;
    const onDocPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDocPointerDown);
    return () => document.removeEventListener("pointerdown", onDocPointerDown);
  }, [open]);

  const choose = (entry: SettingsSearchEntry) => {
    onSelect(entry);
    setOpen(false);
    setQuery("");
  };

  return (
    <div ref={rootRef} className="relative px-2 pb-2">
      <div className="relative">
        <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-paper-muted" />
        <input
          type="search"
          role="combobox"
          aria-expanded={open}
          aria-label="搜索设置项"
          placeholder="搜索设置项..."
          className="paper-input h-8 w-full pl-8 text-xs"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              setOpen(false);
            } else if (event.key === "ArrowDown") {
              event.preventDefault();
              setActiveIndex((index) => (index + 1) % Math.max(results.length, 1));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setActiveIndex((index) => (index - 1 + results.length) % Math.max(results.length, 1));
            } else if (event.key === "Enter") {
              const entry = results[activeIndex];
              if (entry) choose(entry);
            }
          }}
        />
      </div>
      {open && query.trim() !== "" && (
        <div className="absolute left-2 right-2 top-full z-40 mt-1 overflow-hidden rounded-[var(--radius-2)] border border-paper-line bg-paper-panel py-1 [box-shadow:var(--shadow-2)]">
          {results.length === 0 ? (
            <div className="px-3 py-2 text-xs text-paper-muted">没有找到相关设置项</div>
          ) : (
            results.map((entry, index) => (
              <button
                key={entry.id}
                type="button"
                className={`settings-search-result block w-full px-3 py-1.5 text-left text-xs transition ${
                  index === activeIndex ? "bg-paper-soft" : "hover:bg-paper-soft/60"
                }`}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => choose(entry)}
              >
                <span className="block font-medium text-paper-ink">{entry.label}</span>
                <span className="mt-0.5 block text-[10.5px] text-paper-muted">
                  {SETTINGS_SECTION_LABELS[entry.section]} › {entry.group}
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
