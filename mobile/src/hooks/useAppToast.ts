import { useEffect, useState } from "react";

/**
 * 应用级消息提示。message 变化后 4.2 秒自动清空。
 * 在 App.tsx 顶层使用一次，所有子页面/业务通过 setMessage 写入提示。
 */
export function useAppToast() {
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (!message) return;
    const timeout = window.setTimeout(() => setMessage(""), 4200);
    return () => window.clearTimeout(timeout);
  }, [message]);
  return { message, setMessage };
}
