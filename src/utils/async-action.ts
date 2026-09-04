/**
 * executeAction — 统一的异步操作包装器
 *
 * 消除全项目中 60+ 次重复的 try/catch/finally loading 样板代码。
 * 用法示例：
 *
 * ```ts
 * const result = await executeAction(
 *   () => someApiCall(args),
 *   { setLoading, setError, onSuccess: (res) => storeSet(res) }
 * );
 * ```
 */
import { messageFromError } from "@/utils/format";

export interface ExecuteActionOptions<T> {
  /** 调用前设置 loading 状态（进入时 true，退出时 false）*/
  setLoading?: (v: boolean) => void;
  /** 捕获到错误时调用，传入错误消息字符串 */
  setError?: (msg: string) => void;
  /** 成功拿到结果后调用 */
  onSuccess?: (result: T) => void;
}

/**
 * 将一个异步函数统一包裹在 loading/error/finally 生命周期内。
 * - 失败时调用 setError 并返回 undefined
 * - 成功时调用 onSuccess（若提供）并返回结果
 */
export async function executeAction<T>(
  fn: () => Promise<T>,
  options: ExecuteActionOptions<T> = {}
): Promise<T | undefined> {
  const { setLoading, setError, onSuccess } = options;
  setLoading?.(true);
  try {
    const result = await fn();
    onSuccess?.(result);
    return result;
  } catch (error) {
    setError?.(messageFromError(error));
    return undefined;
  } finally {
    setLoading?.(false);
  }
}

/**
 * 包装返回 void/未指定结果的命令式异步操作，成功返回 true，失败捕获错误并返回 false。
 */
export async function executeBoolAction(
  fn: () => Promise<unknown>,
  options: { setError?: (msg: string) => void } = {}
): Promise<boolean> {
  try {
    await fn();
    return true;
  } catch (error) {
    options.setError?.(messageFromError(error));
    return false;
  }
}

