import React from "react";
import ReactDOM from "react-dom/client";
import App from "@/app/App";
import { takeReturnScreen } from "@/app/lazy-recovery";
import { APP_SCREENS, useAppStore } from "@/stores/app-store";
import "@/styles.css";
import "@/styles/editorial-studio.css";

/**
 * BD-223：懒加载 chunk 在文档内失败过一次，Chromium 的模块图就把这条 promise 永久记成 rejected，
 * 就地重试不会再发请求（实测见 src/app/lazy-recovery.ts 顶部）。唯一的出路是重新载入文档 ——
 * 而重新载入会把 screen 打回默认的 projects，用户等于被踢离原屏。
 *
 * 所以重载前兜底卡会把屏名写进 URL hash，这里在挂载之前读回来（读一次即清，避免刷新后反复回跳）。
 * 刻意放在 createRoot 之前而不是组件里：这是启动序列的一次性动作，不该参与 StrictMode 的重复渲染。
 */
const returnScreen = takeReturnScreen(APP_SCREENS);
if (returnScreen) useAppStore.getState().setScreen(returnScreen);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
