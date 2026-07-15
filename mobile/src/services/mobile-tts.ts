/**
 * TTS 朗读引擎服务
 * 支持两种引擎：
 * 1. local: Web Speech API (speechSynthesis)，使用设备自带离线语音
 * 2. online: 在线 TTS HTTP 接口（如 Microsoft Edge TTS、自定义接口）
 */

export type TTSEngineType = "local" | "online";

export interface TTSVoiceInfo {
  id: string;
  name: string;
  lang: string;
  isLocal: boolean;
  isDefault?: boolean;
}

export interface TTSSettings {
  engine: TTSEngineType;
  voiceId: string;
  rate: number; // 0.5 - 2.0
  pitch: number; // 0 - 2
  volume: number; // 0 - 1
  /** 在线 TTS 接口地址，POST 文本返回音频 */
  onlineEndpoint?: string;
  /** 在线 TTS 请求的 voice 名称 */
  onlineVoiceName?: string;
  /** 在线 TTS API Key（如有） */
  onlineApiKey?: string;
}

export type TTSStatus = "idle" | "playing" | "paused" | "loading";

export interface TTSProgressInfo {
  /** 当前朗读段落在队列中的索引 */
  chunkIndex: number;
  /** 总段落数 */
  chunkCount: number;
  /** 当前段落的字符偏移 */
  charOffset: number;
  /** 当前段落总字符数 */
  charLength: number;
  /** 当前朗读块在原始全文中的字符偏移 */
  sourceCharOffset: number;
  /** 当前朗读块所属原始段落序号 */
  paragraphIndex: number;
}

export interface TTSChunk {
  text: string;
  paragraphIndex: number;
  charOffset: number;
}

const TTS_SETTINGS_KEY = "creation-reading-assistant-mobile-tts-settings";
/** 朗读进度持久化前缀 + bookId */
const TTS_PROGRESS_KEY_PREFIX = "creation-reading-assistant-mobile-tts-progress-";

/** 单次朗读最大字符数（Web Speech API 长文本会中断） */
const MAX_CHUNK_LENGTH = 180;

export const defaultTTSSettings: TTSSettings = {
  engine: "local",
  voiceId: "",
  rate: 1.0,
  pitch: 1.0,
  volume: 1.0,
  onlineEndpoint: "",
  onlineVoiceName: "",
  onlineApiKey: ""
};

export function loadTTSSettings(): TTSSettings {
  try {
    const raw = localStorage.getItem(TTS_SETTINGS_KEY);
    if (!raw) return defaultTTSSettings;
    const parsed = JSON.parse(raw) as Partial<TTSSettings>;
    return {
      engine: parsed.engine === "online" ? "online" : "local",
      voiceId: parsed.voiceId ?? "",
      rate: typeof parsed.rate === "number" ? Math.min(2, Math.max(0.5, parsed.rate)) : 1.0,
      pitch: typeof parsed.pitch === "number" ? Math.min(2, Math.max(0, parsed.pitch)) : 1.0,
      volume: typeof parsed.volume === "number" ? Math.min(1, Math.max(0, parsed.volume)) : 1.0,
      onlineEndpoint: parsed.onlineEndpoint ?? "",
      onlineVoiceName: parsed.onlineVoiceName ?? "",
      onlineApiKey: parsed.onlineApiKey ?? ""
    };
  } catch {
    return defaultTTSSettings;
  }
}

export function saveTTSSettings(settings: TTSSettings): void {
  try {
    localStorage.setItem(TTS_SETTINGS_KEY, JSON.stringify(settings));
  } catch {
    // 忽略
  }
}

/** 朗读进度持久化 */
export interface TTSBookmark {
  bookId: string;
  chunkIndex: number;
  charOffset: number;
  savedAt: string;
}

export function loadTTSBookmark(bookId: string): TTSBookmark | undefined {
  try {
    const raw = localStorage.getItem(TTS_PROGRESS_KEY_PREFIX + bookId);
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as TTSBookmark;
    if (parsed.bookId !== bookId) return undefined;
    return parsed;
  } catch {
    return undefined;
  }
}

export function saveTTSBookmark(bookmark: TTSBookmark): void {
  try {
    localStorage.setItem(TTS_PROGRESS_KEY_PREFIX + bookmark.bookId, JSON.stringify(bookmark));
  } catch {
    // 忽略
  }
}

export function clearTTSBookmark(bookId: string): void {
  try {
    localStorage.removeItem(TTS_PROGRESS_KEY_PREFIX + bookId);
  } catch {
    // 忽略
  }
}

/** 将长文本按段落和长度限制切分为可朗读的块，并记录每个块在原文中的段落来源与字符偏移 */
export function splitTextIntoChunks(text: string): TTSChunk[] {
  const cleanText = text.replace(/\r\n/g, "\n").replace(/\u00a0/g, " ");
  const paragraphs = cleanText.split(/\n\s*\n/).map((p) => p.trim()).filter((p) => p.length > 0);
  const chunks: TTSChunk[] = [];
  let globalOffset = 0;
  for (let paragraphIndex = 0; paragraphIndex < paragraphs.length; paragraphIndex += 1) {
    const para = paragraphs[paragraphIndex];
    if (para.length <= MAX_CHUNK_LENGTH) {
      chunks.push({ text: para, paragraphIndex, charOffset: globalOffset });
    } else {
      const sentences = para.split(/(?<=[。！？!?\n；;])/);
      let current = "";
      let currentOffset = 0;
      for (const sentence of sentences) {
        if ((current + sentence).length > MAX_CHUNK_LENGTH) {
          if (current) {
            chunks.push({ text: current.trim(), paragraphIndex, charOffset: globalOffset + currentOffset });
            currentOffset += current.length;
          }
          if (sentence.length > MAX_CHUNK_LENGTH) {
            const parts = sentence.split(/(?<=[，,、：:])/);
            let sub = "";
            for (const part of parts) {
              if ((sub + part).length > MAX_CHUNK_LENGTH) {
                if (sub) {
                  chunks.push({ text: sub.trim(), paragraphIndex, charOffset: globalOffset + currentOffset });
                  currentOffset += sub.length;
                }
                sub = part;
              } else {
                sub += part;
              }
            }
            if (sub.trim()) {
              chunks.push({ text: sub.trim(), paragraphIndex, charOffset: globalOffset + currentOffset });
              currentOffset += sub.length;
            }
            current = "";
          } else {
            current = sentence;
          }
        } else {
          current += sentence;
        }
      }
      if (current.trim()) {
        chunks.push({ text: current.trim(), paragraphIndex, charOffset: globalOffset + currentOffset });
      }
    }
    globalOffset += para.length + 1; // 段落间保留一个换行占位，便于偏移对齐
  }
  return chunks.length ? chunks : [{ text: cleanText.slice(0, MAX_CHUNK_LENGTH), paragraphIndex: 0, charOffset: 0 }];
}

/** 获取本地可用语音列表 */
export function getLocalVoices(): TTSVoiceInfo[] {
  if (typeof window === "undefined" || !window.speechSynthesis) return [];
  const voices = window.speechSynthesis.getVoices();
  return voices
    .filter((v) => v.lang.startsWith("zh") || v.lang.startsWith("cmn"))
    .map((v, index) => ({
      id: `local-${index}`,
      name: v.name,
      lang: v.lang,
      isLocal: true,
      isDefault: v.default
    }));
}

/** 查找中文语音（优先 zh-CN） */
export function findDefaultChineseVoice(): TTSVoiceInfo | undefined {
  const voices = getLocalVoices();
  return voices.find((v) => v.lang === "zh-CN" && v.isDefault)
    ?? voices.find((v) => v.lang === "zh-CN")
    ?? voices.find((v) => v.lang.startsWith("zh"))
    ?? voices[0];
}

/** 根据 voiceId 获取原生 SpeechSynthesisVoice */
function getNativeVoice(voiceId: string): SpeechSynthesisVoice | undefined {
  if (!window.speechSynthesis) return undefined;
  const voices = window.speechSynthesis.getVoices();
  if (!voiceId) {
    return voices.find((v) => v.lang === "zh-CN" && v.default)
      ?? voices.find((v) => v.lang === "zh-CN")
      ?? voices.find((v) => v.lang.startsWith("zh"));
  }
  const index = Number.parseInt(voiceId.replace("local-", ""), 10);
  const filtered = voices.filter((v) => v.lang.startsWith("zh") || v.lang.startsWith("cmn"));
  return filtered[index] ?? voices[index];
}

/**
 * TTS 控制器：管理朗读状态、队列、播放控制
 * 采用回调式而非事件式，方便 React 组件订阅
 */
export class TTSController {
  private settings: TTSSettings;
  private chunks: TTSChunk[] = [];
  private currentIndex = 0;
  private status: TTSStatus = "idle";
  private bookId = "";
  private utterance: SpeechSynthesisUtterance | null = null;
  private audioElement: HTMLAudioElement | null = null;
  private listeners = new Set<() => void>();
  private progressListeners = new Set<(info: TTSProgressInfo) => void>();
  private statusListeners = new Set<(status: TTSStatus) => void>();
  private endListeners = new Set<() => void>();
  private errorListeners = new Set<(error: string) => void>();
  private autoStopTimer: ReturnType<typeof setTimeout> | null = null;
  private mediaMetadata: { title: string; artist: string; artwork?: string } | null = null;
  private mediaSessionSetup = false;

  constructor(settings?: TTSSettings) {
    this.settings = settings ?? loadTTSSettings();
  }

  /** 订阅状态变化（返回取消函数） */
  onStatusChange(cb: (status: TTSStatus) => void): () => void {
    this.statusListeners.add(cb);
    cb(this.status);
    return () => this.statusListeners.delete(cb);
  }

  onProgress(cb: (info: TTSProgressInfo) => void): () => void {
    this.progressListeners.add(cb);
    return () => this.progressListeners.delete(cb);
  }

  onEnd(cb: () => void): () => void {
    this.endListeners.add(cb);
    return () => this.endListeners.delete(cb);
  }

  onError(cb: (error: string) => void): () => void {
    this.errorListeners.add(cb);
    return () => this.errorListeners.delete(cb);
  }

  getStatus(): TTSStatus {
    return this.status;
  }

  getProgress(): TTSProgressInfo {
    const chunk = this.chunks[this.currentIndex] ?? { text: "", paragraphIndex: 0, charOffset: 0 };
    const charOffset = this.currentIndex < this.chunks.length
      ? this.chunks.slice(0, this.currentIndex).reduce((sum, item) => sum + item.text.length, 0)
      : 0;
    return {
      chunkIndex: this.currentIndex,
      chunkCount: this.chunks.length,
      charOffset,
      charLength: chunk.text.length,
      sourceCharOffset: chunk.charOffset,
      paragraphIndex: chunk.paragraphIndex
    };
  }

  updateSettings(settings: TTSSettings): void {
    this.settings = settings;
    // 如果正在用 local 引擎播放，需要更新语速/音调
    if (this.settings.engine === "local" && this.status === "playing") {
      // speechSynthesis 不支持动态修改，需要重新开始当前段
      this.restartCurrentChunk();
    }
  }

  /** 设置媒体元数据（用于系统通知栏/锁屏控件显示） */
  setMediaMetadata(metadata: { title: string; artist: string; artwork?: string }): void {
    this.mediaMetadata = metadata;
    this.updateMediaMetadata();
  }

  /** 加载文本并准备朗读 */
  loadText(text: string, bookId: string, startCharOffset = 0): void {
    this.stop();
    this.chunks = splitTextIntoChunks(text);
    this.currentIndex = 0;
    this.bookId = bookId;
    // 恢复进度：优先使用传入的偏移，其次使用持久化书签
    const bookmark = loadTTSBookmark(bookId);
    const offset = startCharOffset > 0 ? startCharOffset : (bookmark?.charOffset ?? 0);
    if (offset > 0) {
      this.currentIndex = this.findChunkIndexByCharOffset(offset);
    } else if (bookmark && bookmark.chunkIndex < this.chunks.length) {
      this.currentIndex = bookmark.chunkIndex;
    }
  }

  /** 从指定段落开始 */
  startFromChunk(chunkIndex: number): void {
    this.stop();
    this.currentIndex = Math.min(Math.max(0, chunkIndex), this.chunks.length - 1);
    this.play();
  }

  /** 从指定字符偏移开始朗读 */
  startFromCharOffset(charOffset: number): void {
    this.stop();
    this.currentIndex = this.findChunkIndexByCharOffset(charOffset);
    this.play();
  }

  private findChunkIndexByCharOffset(charOffset: number): number {
    if (this.chunks.length === 0) return 0;
    let index = this.chunks.findIndex((chunk) => chunk.charOffset >= charOffset);
    if (index < 0) index = this.chunks.length - 1;
    if (index > 0 && this.chunks[index].charOffset > charOffset) index -= 1;
    return index;
  }

  async play(): Promise<void> {
    if (this.chunks.length === 0) return;
    this.setupMediaSession();
    this.updateMediaMetadata();
    if (this.status === "paused" && this.settings.engine === "local") {
      // 恢复暂停
      window.speechSynthesis.resume();
      this.setStatus("playing");
      return;
    }
    if (this.currentIndex >= this.chunks.length) {
      this.currentIndex = 0;
    }
    await this.playChunk(this.currentIndex);
  }

  private async playChunk(index: number): Promise<void> {
    if (index >= this.chunks.length) {
      this.setStatus("idle");
      this.currentIndex = 0;
      clearTTSBookmark(this.bookId);
      this.endListeners.forEach((cb) => cb());
      return;
    }
    this.currentIndex = index;
    const chunk = this.chunks[index];
    this.notifyProgress();
    this.persistBookmark();

    if (this.settings.engine === "local") {
      this.playLocalChunk(chunk.text);
    } else {
      await this.playOnlineChunk(chunk.text);
    }
  }

  private playLocalChunk(text: string): void {
    if (!window.speechSynthesis) {
      this.errorListeners.forEach((cb) => cb("当前设备不支持语音合成。"));
      return;
    }
    // 清除队列中残留的 utterance
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = this.settings.rate;
    utterance.pitch = this.settings.pitch;
    utterance.volume = this.settings.volume;
    const voice = getNativeVoice(this.settings.voiceId);
    if (voice) {
      utterance.voice = voice;
      utterance.lang = voice.lang;
    } else {
      utterance.lang = "zh-CN";
    }
    utterance.onstart = () => this.setStatus("playing");
    utterance.onend = () => {
      if (this.status === "playing") {
        this.playChunk(this.currentIndex + 1);
      }
    };
    utterance.onerror = (event) => {
      if (event.error === "interrupted" || event.error === "canceled") return;
      this.errorListeners.forEach((cb) => cb(`朗读出错：${event.error}`));
      this.setStatus("idle");
    };
    this.utterance = utterance;
    this.setStatus("playing");
    window.speechSynthesis.speak(utterance);
  }

  private async playOnlineChunk(text: string): Promise<void> {
    if (!this.settings.onlineEndpoint) {
      this.errorListeners.forEach((cb) => cb("请先配置在线 TTS 接口地址。"));
      return;
    }
    this.setStatus("loading");
    try {
      const audioUrl = await this.fetchOnlineTTS(text);
      const audio = new Audio(audioUrl);
      audio.playbackRate = this.settings.rate;
      audio.volume = this.settings.volume;
      audio.onplay = () => this.setStatus("playing");
      audio.onended = () => {
        URL.revokeObjectURL(audioUrl);
        if (this.status === "playing") {
          this.playChunk(this.currentIndex + 1);
        }
      };
      audio.onerror = () => {
        URL.revokeObjectURL(audioUrl);
        this.errorListeners.forEach((cb) => cb("在线语音播放失败。"));
        this.setStatus("idle");
      };
      this.audioElement = audio;
      await audio.play();
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      this.errorListeners.forEach((cb) => cb(`在线 TTS 请求失败：${detail}`));
      this.setStatus("idle");
    }
  }

  /** 请求在线 TTS 接口，返回 blob URL */
  private async fetchOnlineTTS(text: string): Promise<string> {
    const endpoint = this.settings.onlineEndpoint!.trim();
    const headers: Record<string, string> = {
      "Content-Type": "application/json"
    };
    if (this.settings.onlineApiKey) {
      headers["Authorization"] = `Bearer ${this.settings.onlineApiKey}`;
    }
    const response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify({
        text,
        voice: this.settings.onlineVoiceName || "zh-CN-XiaoxiaoNeural",
        rate: this.settings.rate,
        volume: this.settings.volume
      })
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const blob = await response.blob();
    return URL.createObjectURL(blob);
  }

  pause(): void {
    if (this.settings.engine === "local" && window.speechSynthesis) {
      window.speechSynthesis.pause();
    } else if (this.audioElement) {
      this.audioElement.pause();
    }
    this.setStatus("paused");
  }

  stop(): void {
    if (window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
    if (this.audioElement) {
      this.audioElement.pause();
      this.audioElement = null;
    }
    this.clearAutoStopTimer();
    this.utterance = null;
    this.clearMediaSession();
    this.setStatus("idle");
  }

  next(): void {
    if (this.currentIndex < this.chunks.length - 1) {
      this.playChunk(this.currentIndex + 1);
    }
  }

  prev(): void {
    if (this.currentIndex > 0) {
      this.playChunk(this.currentIndex - 1);
    }
  }

  /** 定时停止（分钟） */
  setAutoStop(minutes: number): void {
    this.clearAutoStopTimer();
    if (minutes <= 0) return;
    this.autoStopTimer = setTimeout(() => {
      this.stop();
    }, minutes * 60_000);
  }

  clearAutoStopTimer(): void {
    if (this.autoStopTimer) {
      clearTimeout(this.autoStopTimer);
      this.autoStopTimer = null;
    }
  }

  private restartCurrentChunk(): void {
    if (this.status === "playing" || this.status === "paused") {
      const idx = this.currentIndex;
      this.stop();
      this.currentIndex = idx;
      void this.playChunk(idx);
    }
  }

  private setStatus(status: TTSStatus): void {
    this.status = status;
    this.statusListeners.forEach((cb) => cb(status));
    this.updateMediaSessionState();
  }

  private notifyProgress(): void {
    const info = this.getProgress();
    this.progressListeners.forEach((cb) => cb(info));
  }

  private persistBookmark(): void {
    if (!this.bookId) return;
    const chunk = this.chunks[this.currentIndex];
    saveTTSBookmark({
      bookId: this.bookId,
      chunkIndex: this.currentIndex,
      charOffset: chunk?.charOffset ?? 0,
      savedAt: new Date().toISOString()
    });
  }

  /** 初始化 Media Session action handlers（仅执行一次） */
  private setupMediaSession(): void {
    if (this.mediaSessionSetup) return;
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    this.mediaSessionSetup = true;
    const ms = navigator.mediaSession;
    try {
      ms.setActionHandler("play", () => { void this.play(); });
      ms.setActionHandler("pause", () => { this.pause(); });
      ms.setActionHandler("stop", () => { this.stop(); });
      ms.setActionHandler("previoustrack", () => { this.prev(); });
      ms.setActionHandler("nexttrack", () => { this.next(); });
      ms.setActionHandler("seekbackward", () => { this.prev(); });
      ms.setActionHandler("seekforward", () => { this.next(); });
    } catch {
      // 部分浏览器不支持某些 action，忽略
    }
  }

  private updateMediaMetadata(): void {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    if (!this.mediaMetadata) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: this.mediaMetadata.title,
        artist: this.mediaMetadata.artist,
        album: "创作阅读助手",
        artwork: this.mediaMetadata.artwork
          ? [{ src: this.mediaMetadata.artwork, sizes: "512x512", type: "image/png" }]
          : []
      });
    } catch {
      // 忽略
    }
  }

  private updateMediaSessionState(): void {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    if (!this.mediaSessionSetup) return;
    try {
      const state = this.status === "playing" ? "playing" : this.status === "paused" ? "paused" : "none";
      navigator.mediaSession.playbackState = state as MediaSessionPlaybackState;
    } catch {
      // 忽略
    }
  }

  private clearMediaSession(): void {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    if (!this.mediaSessionSetup) return;
    try {
      navigator.mediaSession.metadata = null;
      navigator.mediaSession.playbackState = "none";
    } catch {
      // 忽略
    }
  }

  /** 清理资源 */
  destroy(): void {
    this.stop();
    this.listeners.clear();
    this.progressListeners.clear();
    this.statusListeners.clear();
    this.endListeners.clear();
    this.errorListeners.clear();
  }
}

/** 全局单例（同一时间只允许一个 TTS 控制器） */
let globalTTSController: TTSController | null = null;

export function getTTSController(): TTSController {
  if (!globalTTSController) {
    globalTTSController = new TTSController();
  }
  return globalTTSController;
}

/** 检查 Web Speech API 是否可用 */
export function isLocalTTSAvailable(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}
