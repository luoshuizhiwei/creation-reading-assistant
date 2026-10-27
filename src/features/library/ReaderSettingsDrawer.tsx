import { useEffect, useRef, useState } from "react";
import { ChevronLeft, Settings2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useModalKeyboard } from "@/components/ui/use-modal-keyboard";
import { ReaderSettingsMorePanel } from "@/features/library/ReaderSettingsMorePanel";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { resetReaderSettings } from "@/services/settings-service";
import { updateReaderSettings } from "@/services/reader-service";
import type { ReaderSettings } from "@/types/library";

/**
 * TXT/Markdown 阅读器的设置抽屉。与 EPUB 的 EpubSettingsDrawer 同构：
 * 设置不再与目录面板堆叠在同一右列，而是由顶栏按钮唤起的独立覆盖层。
 *
 * 主视图只放排版高频项；「更多设置」在抽屉内切换到二级视图
 * （不离开阅读页），承载 EPUB 样式、繁简转换、阅读记录等低频项。
 */
export interface ReaderSettingsDrawerProps {
  open: boolean;
  settings: ReaderSettings;
  onClose(): void;
  onSettingsChange(next: ReaderSettings): void;
  onAfterChange(): void;
  format?: "txt" | "md" | "epub";
  subtitle?: string;
}

export function ReaderSettingsDrawer({
  open,
  settings,
  onClose,
  onSettingsChange,
  onAfterChange,
  format = "txt",
  subtitle = format === "epub" ? "EPUB 默认保留原书样式，需要统一排版时再切换。" : "调整字号、行距与背景，设置实时生效。"
}: ReaderSettingsDrawerProps) {
  const [view, setView] = useState<"main" | "more">("main");
  const titleRef = useRef<HTMLDivElement>(null);
  const drawerRef = useRef<HTMLElement>(null);

  // BD-209：遮罩是全屏层（inset-0，z-30），抽屉一开，阅读器正文、顶栏页签、底栏进度
  // 滑杆的 Tab 停靠点全被压在它下面——焦点矩形整块看不见（2.4.11 红，摸底 14 停靠点，
  // 取证 /tmp/bd209-probe.json）。修法不是给遮罩下元素打补丁，而是把抽屉按 BD-130
  // 弹层键盘契约补齐：Tab 在抽屉内成环、Escape 关自己并 stopPropagation、落焦进标题。
  // 环把键盘圈在可见层里，背景停靠点根本到不了，遮挡随契约一起消失；aria-modal
  // 的承诺由 hook 兑现（浏览器不会自动兑现）。hook 的关后回交与下方 BD-201 的
  // 开手回交捕捉的是同一节点（都在 open 翻真时记 activeElement），双还同一目标幂等；
  // BD-201 账本形状（宿主翻 open 的覆盖面）原样保留。
  const handleDrawerKeyDown = useModalKeyboard({
    active: open,
    containerRef: drawerRef,
    onEscape: onClose,
    initialFocusRef: titleRef
  });

  // BD-201：抽屉的三条关闭路径（「关闭」钮、遮罩 mousedown、TXT/MD 宿主顶栏下的
  // Escape 快捷键）都直接把整棵抽屉子树卸载，焦点从被点的节点跌回 body——键盘
  // 用户要从文档开头重新 Tab（同 BD-183 ReaderSearchOverlay 的出口契约）。抽屉
  // 由宿主常驻挂载、靠 open 翻转出内容，所以回交挂在 [open] 的 effect cleanup 上：
  // 开时记住是谁开的，收时还给它；开手已不在文档里（切路由整页卸载）就不抢焦点。
  // 抽屉内的视图切换（「更多设置」↔「返回阅读设置」）卸载的同样是焦点所在按钮，
  // 但抽屉还没关，焦点不该跑出抽屉——回交给两视图都常驻的标题。
  const restoreFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const opener = document.activeElement;
    restoreFocusRef.current =
      opener instanceof HTMLElement && opener !== document.body ? opener : null;
    return () => {
      const target = restoreFocusRef.current;
      restoreFocusRef.current = null;
      if (target?.isConnected) target.focus({ preventScroll: true });
    };
  }, [open]);

  useEffect(() => {
    if (open) setView("main");
  }, [open]);

  if (!open) return null;
  return (
    <div className="absolute inset-0 z-30 bg-paper-ink/10 backdrop-blur-[1px]" onMouseDown={onClose}>
      <aside
        ref={drawerRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reader-settings-drawer-title"
        onKeyDown={handleDrawerKeyDown}
        className="motion-drawer absolute right-0 top-0 h-full w-[320px] overflow-auto rounded-[var(--radius-3)] border-l border-paper-line bg-paper-panel p-5 [box-shadow:var(--shadow-3)]"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <div className="flex min-w-0 items-center gap-1">
            {view === "more" && (
              <button
                type="button"
                className="rounded-md p-1.5 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
                aria-label="返回阅读设置"
                onClick={() => {
                  setView("main");
                  // BD-201：本按钮随视图切换卸载，焦点先交给常驻标题，别跌落。
                  titleRef.current?.focus({ preventScroll: true });
                }}
              >
                <ChevronLeft size={17} />
              </button>
            )}
            <div className="min-w-0">
              <div id="reader-settings-drawer-title" className="paper-title text-base font-semibold" ref={titleRef} tabIndex={-1}>{view === "main" ? "阅读设置" : "更多设置"}</div>
              {view === "main" && <div className="mt-1 truncate text-xs text-paper-muted">{subtitle}</div>}
            </div>
          </div>
          <button
            type="button"
            className="rounded-md p-2 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
            aria-label="关闭"
            onClick={onClose}
          >
            <X size={17} />
          </button>
        </div>

        {view === "main" ? (
          <>
            <ReaderSettingsPanel
              settings={settings}
              onChange={async (patch) => {
                const next = await updateReaderSettings(patch);
                onSettingsChange(next);
                onAfterChange();
              }}
            />
            <Button
              variant="outline"
              className="mt-5 w-full"
              onClick={() => {
                setView("more");
                // BD-201：本按钮随视图切换卸载，焦点先交给常驻标题，别跌落。
                titleRef.current?.focus({ preventScroll: true });
              }}
            >
              <Settings2 size={15} />
              更多设置
            </Button>
          </>
        ) : (
          <ReaderSettingsMorePanel
            settings={settings}
            format={format}
            onReset={async () => {
              const next = await resetReaderSettings();
              onSettingsChange(next.reader);
              onAfterChange();
            }}
            onChange={async (patch) => {
              const next = await updateReaderSettings(patch);
              onSettingsChange(next);
              onAfterChange();
            }}
          />
        )}
      </aside>
    </div>
  );
}
