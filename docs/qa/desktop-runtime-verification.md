# 桌面端运行时验证（C 类交付物）

> 本文件为 C1 / C2 / C3 的验证方案与评估结论。C1、C3 需在**真机 GUI** 执行，沙箱无 GUI/设备，故仅交付步骤与清单；C2 经评估后延后。

## C1 — 冷启动挂钟度量

**目的**：量化桌面端冷启动时间（进程启动 → 渲染进程可交互），验证 A/B/C2 首屏代码分割（首屏 JS 已由 2.83 MB 压至 ~401 KB，累计 −86%）对真实挂钟的改善。

**前置**：已完成 `npm run build`（tsc×3 + electron-vite），产出 installer 或 portable 包。

**步骤**：
1. 真机干净环境安装（控制变量：关闭无关启动项/杀软实时扫描、使用 SSD、记录首次冷启与二次冷启）。
2. 度量口径 = 主进程启动时间戳 → 渲染进程 `first paint` / 可交互。
   - 推荐在 `src/app/main.tsx` 顶部注入 `performance.mark("renderer-ready")`，主进程 `app.on("ready")` 记录 `performance.now()` 取差值；或借 `app.getAppPath` ready 事件 + `BrowserWindow` 的 `ready-to-show` 事件。
3. 记录数值并与基线（2.83 MB 首屏时代）对比，报告首屏字节下降对应的挂钟收益。

**判定**：无 GUI 无法在沙箱执行；待真机回归后回填数字。

## C2 — 主进程 bundle 分割（评估结论：延后）

**发现**：主进程 `electron/main/index.ts` 在文件顶部静态 import 若干较重模块，其中 `jschardet`、`iconv-lite`（仅 TXT/编码导入时使用）是懒加载候选。

**决策**：**延后**。主进程为单次启动加载，分割对冷启动收益有限，且需先有 C1 冷启动基线才能量化收益。遵循"不优化无实测"纪律——待 C1 基线出来后，若冷启确为瓶颈，再对 `jschardet`/`iconv-lite` 做 `import()` 懒加载（仅在其使用函数内动态引入）。

**非目标**：不拆分 IPC/preload（preload 零 node 依赖，保持静态）。

## C3 — `sandbox: true` 真机回归清单

**背景**：`webPreferences.sandbox: true` 已在 commit `79aa001` 落地（preload 经 contextBridge + ipcRenderer，零 node 依赖，静态确认安全），但沙箱无 GUI 未做运行时验证。

**真机回归清单**：
- [ ] 自定义字体 `@font-face` 经 `file:` 正常加载（CSP `font-src` 已含 `file:`）。
- [ ] EPUB 自定义协议 `novel-workbench-epub:` scheme 资源可读（封面/内文图片）。
- [ ] 全部 IPC（`contextBridge` 暴露接口）功能正常，preload 无 node 依赖泄漏。
- [ ] `better-sqlite3` 在沙箱渲染上下文可加载（`electron.vite.config.ts` 已 `asarUnpack`）。
- [ ] 生产环境 DevTools 默认关闭（`devTools: !app.isPackaged`）。

**判定**：无设备无法执行；待真机回归后勾选。
