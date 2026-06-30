import { Button, Field, TextInput } from "@/components/ui";
import type { ReaderSettings } from "@/types/library";

export function ReaderSettingsPanel({
  settings,
  onChange,
  onReset
}: {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
  onReset?: () => void;
}) {
  const tracking = settings.tracking;

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-3">
        <div className="text-sm font-semibold text-paper-ink">阅读设置</div>
        {onReset && (
          <Button variant="secondary" className="h-8 px-2" onClick={onReset}>
            恢复默认
          </Button>
        )}
      </div>
      <Field label="字号">
        <TextInput type="number" min={12} max={32} value={settings.fontSize} onChange={(event) => onChange({ fontSize: Number(event.target.value) })} />
      </Field>
      <Field label="行距">
        <TextInput
          type="number"
          min={1.2}
          max={2.6}
          step={0.1}
          value={settings.lineHeight}
          onChange={(event) => onChange({ lineHeight: Number(event.target.value) })}
        />
      </Field>
      <Field label="页边距">
        <TextInput type="number" min={24} max={120} value={settings.pageMargin} onChange={(event) => onChange({ pageMargin: Number(event.target.value) })} />
      </Field>
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">书籍背景</span>
        <select
          className="paper-input h-9"
          value={settings.readerBackground}
          onChange={(event) => onChange({ readerBackground: event.target.value as ReaderSettings["readerBackground"] })}
        >
          <option value="white">白纸</option>
          <option value="warm">暖纸</option>
          <option value="green">护眼</option>
          <option value="night">夜间</option>
        </select>
      </label>
      <label className="grid gap-1.5 text-sm text-paper-muted">
        <span className="font-medium text-paper-ink">EPUB 样式</span>
        <select
          className="paper-input h-9"
          value={settings.epubStyleMode}
          onChange={(event) => onChange({ epubStyleMode: event.target.value as ReaderSettings["epubStyleMode"] })}
        >
          <option value="publisher">保留原书样式</option>
          <option value="unified">统一阅读样式</option>
        </select>
      </label>

      <div className="mt-2 border-t border-paper-line pt-4">
        <div className="mb-1 text-sm font-semibold text-paper-ink">高级阅读记录</div>
        <p className="mb-3 text-xs leading-5 text-paper-muted">这些选项只影响计时和进度保存，不会改变正文内容。</p>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input type="checkbox" checked={settings.restoreLastPosition} onChange={(event) => onChange({ restoreLastPosition: event.target.checked })} />
          自动恢复上次位置
        </label>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input
            type="checkbox"
            checked={tracking.trackReadingSessions}
            onChange={(event) => onChange({ tracking: { ...tracking, trackReadingSessions: event.target.checked } })}
          />
          自动记录阅读会话
        </label>
        <label className="mb-3 flex items-center gap-2 text-sm text-paper-muted">
          <input
            type="checkbox"
            checked={tracking.showReadingStatsCards}
            onChange={(event) => onChange({ tracking: { ...tracking, showReadingStatsCards: event.target.checked } })}
          />
          显示当前会话卡片
        </label>
        <Field label="空闲暂停秒数">
          <p className="text-xs leading-5 text-paper-muted">多久没有翻页、滚动或点击后，暂停计入有效阅读时间。</p>
          <TextInput
            type="number"
            min={15}
            max={600}
            value={Math.round(tracking.idleTimeoutMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, idleTimeoutMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
        <Field label="进度保存间隔秒数">
          <p className="text-xs leading-5 text-paper-muted">阅读时多久自动保存一次当前位置，数值越小越不容易丢进度。</p>
          <TextInput
            type="number"
            min={1}
            max={60}
            value={Math.round(tracking.progressSaveIntervalMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, progressSaveIntervalMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
        <Field label="会话持久化秒数">
          <p className="text-xs leading-5 text-paper-muted">阅读计时多久写入一次本地文件，用来防止异常退出后时长丢失。</p>
          <TextInput
            type="number"
            min={5}
            max={300}
            value={Math.round(tracking.sessionPersistIntervalMs / 1000)}
            onChange={(event) => onChange({ tracking: { ...tracking, sessionPersistIntervalMs: Number(event.target.value) * 1000 } })}
          />
        </Field>
      </div>
    </div>
  );
}
