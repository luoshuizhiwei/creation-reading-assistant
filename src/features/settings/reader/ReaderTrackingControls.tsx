import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { NumberStepper, Switch } from "@/components/ui";
import type { ReaderTrackingSettings } from "@/types/library";

export interface ReaderTrackingControlsProps {
  tracking: ReaderTrackingSettings;
  onPatch(patch: Partial<ReaderTrackingSettings>): void;
}

/**
 * ReaderTrackingControls — 阅读记录开关 + 高级计时项
 * 设置页「阅读记录」组与阅读抽屉「更多设置」二级视图共用。
 * 计时项默认折叠（一般无需修改）。
 */
export function ReaderTrackingControls({ tracking, onPatch }: ReaderTrackingControlsProps) {
  const [showAdvanced, setShowAdvanced] = useState(false);

  return (
    <div className="grid gap-4">
      <div data-setting-id="reader.trackReadingSessions">
        <Switch
          checked={tracking.trackReadingSessions}
          onChange={(trackReadingSessions) => onPatch({ trackReadingSessions })}
          label="自动记录阅读会话"
        />
      </div>

      <div className="rounded-lg border border-paper-line bg-paper-soft/35">
        <button
          type="button"
          className="flex w-full items-center justify-between gap-2 px-3 py-2 text-sm text-paper-ink"
          aria-expanded={showAdvanced}
          onClick={() => setShowAdvanced((prev) => !prev)}
        >
          <span>
            高级计时
            <span className="ml-2 text-xs text-paper-muted">一般无需修改</span>
          </span>
          <ChevronDown size={15} className={`text-paper-muted transition-transform ${showAdvanced ? "rotate-180" : ""}`} />
        </button>
        {showAdvanced && (
          <div className="grid gap-4 border-t border-paper-line px-3 py-3">
            <p className="text-xs leading-5 text-paper-muted">
              下面这些秒数只控制阅读计时和位置保存：多久没有翻页会暂停计时、多久保存一次阅读位置、多久把会话时长写入本地文件。
            </p>
            <NumberStepper
              label="空闲超时秒数"
              hint="多久没有翻页、滚动或点击后，暂停计入有效阅读时间。"
              min={15}
              max={600}
              step={15}
              unit="秒"
              value={Math.round(tracking.idleTimeoutMs / 1000)}
              onChange={(value) => onPatch({ idleTimeoutMs: value * 1000 })}
            />
            <NumberStepper
              label="进度保存间隔秒数"
              hint="阅读时多久自动保存一次当前位置。"
              min={1}
              max={60}
              unit="秒"
              value={Math.round(tracking.progressSaveIntervalMs / 1000)}
              onChange={(value) => onPatch({ progressSaveIntervalMs: value * 1000 })}
            />
            <NumberStepper
              label="会话心跳秒数"
              hint="阅读页多久同步一次“还在读”的状态。"
              min={2}
              max={60}
              unit="秒"
              value={Math.round(tracking.sessionHeartbeatMs / 1000)}
              onChange={(value) => onPatch({ sessionHeartbeatMs: value * 1000 })}
            />
            <NumberStepper
              label="会话持久化间隔秒数"
              hint="阅读时长多久写入一次本地文件，异常退出时用于恢复统计。"
              min={5}
              max={300}
              step={5}
              unit="秒"
              value={Math.round(tracking.sessionPersistIntervalMs / 1000)}
              onChange={(value) => onPatch({ sessionPersistIntervalMs: value * 1000 })}
            />
          </div>
        )}
      </div>
    </div>
  );
}
