import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..", "..");

/**
 * 主进程实现模块清单。
 *
 * 这些守卫脚本要校验的是「主进程必须具备某项安全/数据实现」，而不是「某段代码必须写在
 * 某个文件里」。主进程入口已按职责拆分为存储底座与各数据域模块（纯移动式重构），
 * 因此断言必须覆盖整个模块集合，否则任何一次正常拆分都会让守卫误报。
 * 新增主进程模块时请同步登记到此处。
 */
export const MAIN_PROCESS_FILES = [
  "electron/main/index.ts",
  "electron/main/storage.ts",
  "electron/main/settings-store.ts",
  "electron/main/library-store.ts",
  "electron/main/inspiration-store.ts",
  "electron/main/progress-store.ts",
  "electron/main/sync-server.ts",
  "electron/main/reader-io.ts",
  "electron/main/library-search.ts"
];

export function readMainProcess() {
  return MAIN_PROCESS_FILES.map((file) => readFileSync(path.join(root, file), "utf8")).join("\n");
}

/**
 * 同步实现源码（负向断言用：同步路径不得接触 AI 密钥）。
 *
 * 独立成模块后，「同步代码范围内不得出现 X」应针对同步模块整体判断，
 * 比原先在单文件里取两个标记之间的文本窗口更严格。
 */
export function readSyncImplementation() {
  return readFileSync(path.join(root, "electron/main/sync-server.ts"), "utf8");
}
