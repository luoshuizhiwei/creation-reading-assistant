// 阅读器主题选项：白纸 / 暖纸 / 护眼 / 夜间 / 暖黄纸感 / 绿豆沙 / 夜间 OLED
export const READER_BACKGROUND_OPTIONS = [
  ["white", "白纸"],
  ["warm", "暖纸"],
  ["green", "护眼"],
  ["night", "夜间"],
  ["warm-yellow", "暖黄纸感"],
  ["green-bean", "绿豆沙"],
  ["oled-black", "夜间 OLED"]
] as const;

// 阅读器加载超时阈值（毫秒）
export const READER_LOAD_TIMEOUT_MS = 12_000;
// 加载提示首次出现延迟（毫秒）
export const READER_LOADING_HINT_DELAY_MS = 350;
// 加载提示转长文本延迟（毫秒）
export const READER_LOADING_HINT_LONG_MS = 6_000;
// 阅读活跃时长心跳间隔（毫秒）
export const READER_SESSION_TICK_MS = 1000;
// 阅读活跃判定阈值：空闲超过此值则停止累加（毫秒）
export const READER_IDLE_THRESHOLD_MS = 45_000;
// 进度自动保存防抖延迟（毫秒）
export const READER_PROGRESS_SAVE_DEBOUNCE_MS = 900;
