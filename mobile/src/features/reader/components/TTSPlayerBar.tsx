import { useEffect, useState } from "react";
import { Play, Pause, Square, SkipBack, SkipForward, Headphones, Settings2, X, Timer, BookOpen } from "lucide-react";
import { useTTS } from "../../../hooks/useTTS";
import type { TTSSettings, TTSVoiceInfo } from "../../../services/mobile-tts";
import { getLocalVoices, findDefaultChineseVoice } from "../../../services/mobile-tts";

interface TTSPlayerBarProps {
  /** 当前章节标题 */
  chapterLabel: string;
  /** 当前可朗读文本 */
  text: string;
  /** 当前书籍 ID */
  bookId: string;
  /** 书籍标题（用于 Media Session 显示） */
  bookTitle?: string;
  /** 阅读器底部偏移（避免遮挡） */
  bottomOffset?: number;
  /** 朗读起始字符偏移 */
  initialCharOffset?: number;
  /** 朗读进度变化回调 */
  onProgressChange?: (info: { charOffset: number; paragraphIndex: number }) => void;
  /** 点击“回正文”时回调，由外层负责停止 TTS 并滚动到对应位置 */
  onSwitchToText?: () => void;
}

export function TTSPlayerBar({
  chapterLabel,
  text,
  bookId,
  bookTitle,
  bottomOffset = 0,
  initialCharOffset = 0,
  onProgressChange,
  onSwitchToText
}: TTSPlayerBarProps) {
  const tts = useTTS();
  const [showSettings, setShowSettings] = useState(false);
  const [showAutoStopMenu, setShowAutoStopMenu] = useState(false);
  const [autoStopMinutes, setAutoStopMinutes] = useState(0);
  const [voices, setVoices] = useState<TTSVoiceInfo[]>([]);

  // 加载本地语音列表
  useEffect(() => {
    if (!tts.isAvailable) return;
    const loadVoices = () => {
      const list = getLocalVoices();
      setVoices(list);
    };
    loadVoices();
    // 语音列表可能异步加载
    if (window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }
    return () => {
      if (window.speechSynthesis) {
        window.speechSynthesis.onvoiceschanged = null;
      }
    };
  }, [tts.isAvailable]);

  // 更新 Media Session 元数据（系统通知栏/锁屏显示）
  useEffect(() => {
    tts.setMediaMetadata({
      title: chapterLabel || "朗读中",
      artist: bookTitle || "创作阅读助手"
    });
  }, [chapterLabel, bookTitle, tts]);

  // 组件卸载时停止朗读
  useEffect(() => {
    return () => {
      tts.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 向外同步朗读进度（字符偏移 + 段落序号）
  useEffect(() => {
    if (!tts.progress || !onProgressChange) return;
    onProgressChange({
      charOffset: tts.progress.sourceCharOffset,
      paragraphIndex: tts.progress.paragraphIndex
    });
  }, [tts.progress, onProgressChange]);

  // 面板首次出现时自动从指定偏移开始朗读
  useEffect(() => {
    if (text.trim() && bookId) {
      tts.play(text, bookId, initialCharOffset);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePlayPause = () => {
    if (tts.status === "playing") {
      tts.pause();
    } else if (tts.status === "paused") {
      tts.resume();
    } else {
      if (text.trim()) {
        tts.play(text, bookId, initialCharOffset);
      }
    }
  };

  const handleSwitchToText = () => {
    tts.stop();
    onSwitchToText?.();
  };

  const handleStop = () => {
    tts.stop();
  };

  const handleAutoStop = (minutes: number) => {
    setAutoStopMinutes(minutes);
    tts.setAutoStop(minutes);
    setShowAutoStopMenu(false);
  };

  if (!tts.isAvailable && tts.settings.engine === "local") {
    return null;
  }

  const isIdle = tts.status === "idle";
  const progressPercent = tts.progress && tts.progress.chunkCount > 0
    ? Math.round(((tts.progress.chunkIndex + 1) / tts.progress.chunkCount) * 100)
    : 0;

  return (
    <>
      {/* 朗读控制条 */}
      <div
        className={`tts-player-bar ${isIdle ? "tts-player-hidden" : ""}`}
        style={{ bottom: `calc(${bottomOffset}px + env(safe-area-inset-bottom))` }}
      >
        <div className="tts-player-progress" aria-hidden="true">
          <span style={{ width: `${progressPercent}%` }} />
        </div>
        <div className="tts-player-content">
          <div className="tts-player-info">
            <Headphones size={16} className={tts.status === "playing" ? "tts-pulse" : ""} />
            <div className="tts-player-meta">
              <strong>{chapterLabel}</strong>
              <small>
                {tts.progress
                  ? `段落 ${tts.progress.chunkIndex + 1}/${tts.progress.chunkCount} · ${progressPercent}%`
                  : "准备朗读…"}
              </small>
            </div>
          </div>
          <div className="tts-player-controls">
            <button
              onClick={() => tts.prev()}
              disabled={tts.status === "idle" || !tts.progress || tts.progress.chunkIndex === 0}
              aria-label="上一段"
            >
              <SkipBack size={18} />
            </button>
            <button
              className="tts-player-main-btn"
              onClick={handlePlayPause}
              aria-label={tts.status === "playing" ? "暂停" : "播放"}
            >
              {tts.status === "playing" ? <Pause size={20} /> : <Play size={20} />}
            </button>
            <button
              onClick={() => tts.next()}
              disabled={tts.status === "idle" || !tts.progress || tts.progress.chunkIndex >= tts.progress.chunkCount - 1}
              aria-label="下一段"
            >
              <SkipForward size={18} />
            </button>
            <button onClick={handleStop} disabled={isIdle} aria-label="停止">
              <Square size={16} />
            </button>
            <button
              className={`tts-player-icon-btn ${autoStopMinutes > 0 ? "active" : ""}`}
              onClick={() => setShowAutoStopMenu(!showAutoStopMenu)}
              aria-label="定时停止"
            >
              <Timer size={16} />
              {autoStopMinutes > 0 && <span className="tts-badge">{autoStopMinutes}</span>}
            </button>
            {onSwitchToText && (
              <button onClick={handleSwitchToText} aria-label="回到正文">
                <BookOpen size={16} />
              </button>
            )}
            <button onClick={() => setShowSettings(true)} aria-label="朗读设置">
              <Settings2 size={16} />
            </button>
          </div>
        </div>
        {tts.error && <div className="tts-player-error">{tts.error}</div>}
      </div>

      {/* 定时停止菜单 */}
      {showAutoStopMenu && (
        <div className="tts-autostop-menu" role="dialog">
          <div className="tts-autostop-header">
            <strong>定时停止</strong>
            <button onClick={() => setShowAutoStopMenu(false)}><X size={16} /></button>
          </div>
          <div className="tts-autostop-options">
            {[
              { label: "关闭", value: 0 },
              { label: "15 分钟", value: 15 },
              { label: "30 分钟", value: 30 },
              { label: "45 分钟", value: 45 },
              { label: "60 分钟", value: 60 }
            ].map((opt) => (
              <button
                key={opt.value}
                className={autoStopMinutes === opt.value ? "active" : ""}
                onClick={() => handleAutoStop(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 朗读设置面板 */}
      {showSettings && (
        <TTSSettingsPanel
          settings={tts.settings}
          voices={voices}
          onChange={tts.updateSettings}
          onClose={() => setShowSettings(false)}
        />
      )}
    </>
  );
}

function TTSSettingsPanel({
  settings,
  voices,
  onChange,
  onClose
}: {
  settings: TTSSettings;
  voices: TTSVoiceInfo[];
  onChange: (settings: TTSSettings) => void;
  onClose: () => void;
}) {
  const defaultVoice = findDefaultChineseVoice();

  return (
    <div className="tts-settings-overlay" role="dialog" aria-modal="true">
      <div className="tts-settings-backdrop" onClick={onClose} />
      <aside className="tts-settings-panel">
        <header className="tts-settings-header">
          <h2>朗读设置</h2>
          <button onClick={onClose} aria-label="关闭"><X size={20} /></button>
        </header>

        <section className="tts-settings-section">
          <label className="tts-settings-label">朗读引擎</label>
          <div className="tts-engine-tabs">
            <button
              className={settings.engine === "local" ? "active" : ""}
              onClick={() => onChange({ ...settings, engine: "local" })}
            >
              本地语音（离线）
            </button>
            <button
              className={settings.engine === "online" ? "active" : ""}
              onClick={() => onChange({ ...settings, engine: "online" })}
            >
              在线语音
            </button>
          </div>
        </section>

        {settings.engine === "local" ? (
          <section className="tts-settings-section">
            <label className="tts-settings-label">语音角色</label>
            <select
              className="tts-settings-select"
              value={settings.voiceId}
              onChange={(e) => onChange({ ...settings, voiceId: e.target.value })}
            >
              <option value="">默认（{defaultVoice?.name ?? "系统中文"}）</option>
              {voices.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} ({v.lang})
                </option>
              ))}
            </select>
            {voices.length === 0 && (
              <p className="tts-settings-hint">未检测到中文语音包，可在系统设置中安装中文 TTS 引擎。</p>
            )}
          </section>
        ) : (
          <>
            <section className="tts-settings-section">
              <label className="tts-settings-label">接口地址</label>
              <input
                type="url"
                className="tts-settings-input"
                placeholder="https://your-tts-api.com/synthesize"
                value={settings.onlineEndpoint ?? ""}
                onChange={(e) => onChange({ ...settings, onlineEndpoint: e.target.value })}
              />
              <p className="tts-settings-hint">POST JSON：text/voice/rate/volume，返回音频 blob。</p>
            </section>
            <section className="tts-settings-section">
              <label className="tts-settings-label">语音名称</label>
              <input
                type="text"
                className="tts-settings-input"
                placeholder="zh-CN-XiaoxiaoNeural"
                value={settings.onlineVoiceName ?? ""}
                onChange={(e) => onChange({ ...settings, onlineVoiceName: e.target.value })}
              />
            </section>
            <section className="tts-settings-section">
              <label className="tts-settings-label">API Key（可选）</label>
              <input
                type="password"
                className="tts-settings-input"
                placeholder="留空表示无需认证"
                value={settings.onlineApiKey ?? ""}
                onChange={(e) => onChange({ ...settings, onlineApiKey: e.target.value })}
              />
            </section>
          </>
        )}

        <section className="tts-settings-section">
          <label className="tts-settings-label">语速 ({settings.rate.toFixed(1)}x)</label>
          <input
            type="range"
            min="0.5"
            max="2.0"
            step="0.1"
            value={settings.rate}
            onChange={(e) => onChange({ ...settings, rate: Number(e.target.value) })}
          />
        </section>

        {settings.engine === "local" && (
          <section className="tts-settings-section">
            <label className="tts-settings-label">音调 ({settings.pitch.toFixed(1)})</label>
            <input
              type="range"
              min="0"
              max="2"
              step="0.1"
              value={settings.pitch}
              onChange={(e) => onChange({ ...settings, pitch: Number(e.target.value) })}
            />
          </section>
        )}

        <section className="tts-settings-section">
          <label className="tts-settings-label">音量 ({Math.round(settings.volume * 100)}%)</label>
          <input
            type="range"
            min="0"
            max="1"
            step="0.05"
            value={settings.volume}
            onChange={(e) => onChange({ ...settings, volume: Number(e.target.value) })}
          />
        </section>
      </aside>
    </div>
  );
}
