import { useEffect, useId, useRef, useState } from "react";
import { Check, ChevronDown, Upload } from "lucide-react";
import { importFontFile, useInstalledFonts } from "@/features/settings/reader/font-import";

export interface FontPickerProps {
  value?: string;
  onChange(fontFamily: string | undefined): void;
  label?: string;
  className?: string;
  /** 在列表底部显示「导入字体…」项，默认 true */
  showImportItem?: boolean;
  /** 导入成功后回调（用于刷新外部状态或提示） */
  onFontImported?(fontName: string): void;
}

const SAMPLE_TEXT = "永 Aa 创作阅读";

/**
 * FontPicker — 字体选择 listbox
 *
 * 每个选项用其自身字体渲染「字体名 + 示例文字」做实时预览；
 * 支持 Esc / 点击外部关闭与上下键导航。导入的字体在应用重启前
 * 由会话级 @font-face 支撑预览（与既有行为一致）。
 */
export function FontPicker({
  value,
  onChange,
  label,
  className = "",
  showImportItem = true,
  onFontImported
}: FontPickerProps) {
  const listboxId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const { fonts, refresh } = useInstalledFonts();

  const options: Array<{ value: string; family?: string; importItem?: boolean }> = [
    { value: "" },
    ...fonts.map((file) => {
      const name = file.replace(/\.[^.]+$/, "");
      return { value: name, family: `'${name}'` };
    }),
    ...(showImportItem ? [{ value: "__import__", importItem: true }] : [])
  ];

  useEffect(() => {
    if (!open) return;
    const onDocPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDocPointerDown);
    return () => document.removeEventListener("pointerdown", onDocPointerDown);
  }, [open]);

  useEffect(() => {
    if (open) {
      const current = options.findIndex((option) => option.value === (value ?? ""));
      setActiveIndex(current >= 0 ? current : 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, value]);

  const choose = async (optionValue: string) => {
    if (optionValue === "__import__") {
      const imported = await importFontFile();
      if (imported) {
        await refresh();
        onChange(imported);
        onFontImported?.(imported);
      }
      setOpen(false);
      triggerRef.current?.focus();
      return;
    }
    onChange(optionValue || undefined);
    setOpen(false);
    // BD-166：列表收起后焦点不能落到 body，否则下次 Tab 从页首重走。
    triggerRef.current?.focus();
  };

  const currentLabel = value ? value : "系统默认";

  // BD-166：方向键此前只挪高亮不挪焦点，读屏器念的还是旧项，回车却提交新项。
  // 让焦点跟着高亮走，两件事才是同一项。
  const moveActive = (delta: number) => {
    const next = (activeIndex + delta + options.length) % options.length;
    setActiveIndex(next);
    optionRefs.current[next]?.focus();
  };

  const button = (
    <button
      type="button"
      ref={triggerRef}
      className="paper-input flex h-8 w-full items-center justify-between gap-2 px-3 text-sm"
      aria-haspopup="listbox"
      aria-expanded={open}
      aria-controls={open ? listboxId : undefined}
      onClick={() => setOpen((prev) => !prev)}
      onKeyDown={(event) => {
        if (event.key !== "ArrowDown" && event.key !== "ArrowUp") {
          if (event.key === "Enter" && open) {
            event.preventDefault();
            void choose(options[activeIndex]?.value ?? "");
          }
          return;
        }
        event.preventDefault();
        if (!open) {
          setOpen(true);
          return;
        }
        moveActive(event.key === "ArrowDown" ? 1 : -1);
      }}
    >
      <span className="truncate" style={value ? { fontFamily: `'${value}'` } : undefined}>
        {currentLabel}
      </span>
      <ChevronDown size={14} className="shrink-0 text-paper-muted" />
    </button>
  );

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      {label && <span className="mb-1.5 block text-sm font-medium text-paper-ink">{label}</span>}
      {button}
      {open && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label={label ?? "选择正文字体"}
          className="absolute z-40 mt-1 max-h-72 w-full overflow-auto rounded-md border border-paper-line bg-paper-panel py-1 [box-shadow:var(--shadow-2)]"
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              setOpen(false);
              triggerRef.current?.focus();
            } else if (event.key === "ArrowDown") {
              event.preventDefault();
              moveActive(1);
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              moveActive(-1);
            } else if (event.key === "Enter") {
              event.preventDefault();
              void choose(options[activeIndex]?.value ?? "");
            }
          }}
        >
          {options.map((option, index) => {
            const selected = option.value === (value ?? "");
            const isImport = option.importItem === true;
            const text = isImport ? "导入字体…" : option.value === "" ? "系统默认" : option.value;
            // BD-166：option 曾经挂在 li 上、按钮长在 option 里面——嵌套交互，
            // axe 的 nested-interactive 与「option 里出现 button」都会红。
            // 把 li 降为 presentation，让按钮自己成为 listbox 的 option。
            return (
              <li key={option.value === "" ? "__default__" : option.value} role="presentation">
                <button
                  type="button"
                  ref={(node) => {
                    optionRefs.current[index] = node;
                  }}
                  id={`${listboxId}-option-${index}`}
                  role="option"
                  aria-selected={selected}
                  className={`flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left transition ${
                    index === activeIndex ? "bg-paper-soft" : "hover:bg-paper-soft/60"
                  }`}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => void choose(option.value)}
                >
                  {isImport ? (
                    <span className="flex items-center gap-2 text-sm text-paper-muted">
                      <Upload size={14} />
                      {text}
                    </span>
                  ) : (
                    <span className="min-w-0">
                      <span
                        className="font-picker-option-sample block truncate"
                        style={option.family ? { fontFamily: option.family } : undefined}
                      >
                        {text}
                      </span>
                      {option.family && (
                        <span className="font-picker-option-meta block truncate">{SAMPLE_TEXT}</span>
                      )}
                    </span>
                  )}
                  {selected && !isImport && <Check size={14} className="shrink-0 text-copper" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
