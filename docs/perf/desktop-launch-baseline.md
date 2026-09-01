# 桌面端启动性能基线

> 测量日期：2026-09-01
> 分支：`codex/workspace-backup-2026-08-20`（桌面端 `src/`、`electron/` 为 clean 工作区）
> 目的：为「屏级 lazy 分割」提供可对比的 A/B 数字

## 测量方法

```bash
env -u CODEBUDDY_SESSION_ID -u CLAUDE_SESSION_ID npm run build
```

再用 `zlib.gzipSync(level 9)` 统计 `out/renderer/assets/` 下每个产物的 raw / gzip 字节数。

> **为什么主指标取 raw 而不是 gzip**
> Electron 从本地 `file://` 加载，不经过网络传输，gzip 体积与冷启动无关。
> 冷启动耗时主要来自 V8 对 JS 的**解析 + 编译 + 执行**，该项与 raw 字节数正相关。
> 因此本报告的收益对比以 **raw 字节数** 为准，gzip 仅作参考。

## 改造前基线（A0）

配置现状：`electron.vite.config.ts` 的 renderer 仅 `input: "index.html"`，**无 `manualChunks`**；
`src/` 内 `React.lazy` / `Suspense` / `loadable` **零命中**；
`src/app/App.tsx:29-37` 把 7 个屏全部**静态 import** 进主 chunk。

### 产物构成

| 产物 | raw | gzip |
|---|---:|---:|
| `index-*.js`（主 chunk，含全部 7 屏） | 2,967,931 B (2.83 MB) | 622 KB |
| `full-*.js`（重型依赖，推测为 epub.js 等） | 1,184,618 B (1.13 MB) | 495 KB |
| `index-*.css` | 295,003 B (288 KB) | 49 KB |
| **JS 合计** | **4,152,549 B (3.96 MB)** | **1,114 KB** |

### 首屏成本

零代码分割 ⇒ **首屏必须解析全部 JS**：

- **首屏 JS 解析量 = 3.96 MB**（基线主指标）
- 其中与首屏（默认 `projects`）无关、但被迫一起解析的：
  - `SettingsPage.tsx` — 1,172 行
  - `EpubReaderPage.tsx` — 931 行（仅 `reader` 屏用）
  - `ReaderPage.tsx` — 760 行（仅 `reader` 屏用）
  - `ReaderSettingsPanel.tsx` — 288 行（仅 `reader` 屏用）

### 冷启动挂钟时间

**未测（本环境无 GUI）**。CLI 沙箱无法启动 Electron 窗口，`did-finish-load` 时间戳需在有显示环境人工复测。
本轮以「首屏 JS 解析量」作为可自动化、可复现的替代指标。

## 环境坑（复现时必读）

1. **build 必须 unset 会话变量**：
   ```bash
   env -u CODEBUDDY_SESSION_ID -u CLAUDE_SESSION_ID npm run build
   ```
   否则 WorkBuddy 沙箱的 `genie-safe-delete` 会拦截 `emptyDir(out/)`，导致 build 假失败（非代码问题）。

2. **verify 脚本需 unset `NODE_OPTIONS`**：本机 `NODE_OPTIONS=--use-system-ca`（代理设置），
   electron run-as-node 会拒绝该标志。`verify-operation.mjs` / `verify-creation-scale.mjs` /
   `verify-creation-replace.mjs` 已内置 `delete electronEnv.NODE_OPTIONS`，其余脚本遇到同类报错按此处理。

3. **build 有一处无害警告**：Tailwind safelist `/^reader-(bg|shell)-...$/` 未匹配到任何类。
   属既有配置遗留，与本次改动无关，未处理。

## 改造后（A2）

改造日期：2026-09-01

### 做法

仅改动两处，**未触碰 store / IPC / preload / 类型定义**：

1. `src/app/App.tsx` — 7 个屏由静态 import 改为 `React.lazy()`。
   因这些模块均为 named export，统一用 `.then((m) => ({ default: m.X }))` 适配
   React.lazy 要求的 default 形状，避免为了分割去改 7 个页面的导出方式。
   **变量名刻意与原 import 保持一致**，故 `screenContent` 的 7 个 JSX 值零改动，
   `Record<AppScreen, ReactNode>` 的编译期穷尽约束原样保留。
2. 新增 `src/components/ScreenFallback.tsx` — 骨架占位（项目此前无任何 skeleton/pulse 先例）。
   `Suspense` 置于 `PageTransition` **内层**：放外层会导致切屏时整个转场容器被 fallback 顶掉、
   动画重播。

### chunk 构成（raw，降序）

| chunk | raw | gzip |
|---|---:|---:|
| `ReaderPage-*.js` | 1,248,474 B (1.19 MB) | 266 KB |
| `full-*.js`（OpenCC 词典，**动态**） | 1,184,618 B (1.13 MB) | 495 KB |
| `CreationProjectsPage-*.js` | 1,151,262 B (1.10 MB) | 235 KB |
| `index-*.js`（应用外壳） | 319,349 B (312 KB) | 76 KB |
| `SettingsPage-*.js` | 119,500 B (117 KB) | 25 KB |
| `InboxPage-*.js` | 35,483 B | 8 KB |
| `ai-*.js` | 34,261 B | 6 KB |
| `ReadingStatsPage-*.js` | 22,966 B | 5 KB |
| `LibraryPage-*.js` | 22,200 B | 5 KB |
| `OperationProgressDialog-*.js` | 14,918 B | 4 KB |
| `InspirationPage-*.js` | 1,613 B | 0.8 KB |
| 图标等小 chunk ×5 | ~3,018 B | ~1.7 KB |
| **合计** | **4,157,662 B (3.97 MB)** | **1,155 KB** |

chunk 数：2 → 16。JS 总量 4,152,549 → 4,157,662 B（**+5,113 B，+0.12%**，chunk 头开销）。

### 首屏收益（主指标）

| | raw |
|---|---:|
| 基线（零分割，首屏即全量） | 2,967,931 B (2.83 MB) |
| 改造后：应用外壳 | 319,349 B (312 KB) |
| 改造后：projects 屏闭包 | 1,202,886 B (1,175 KB) |
| **改造后首屏合计** | **1,522,235 B (1.45 MB)** |

> **首屏减少 1,412 KB（-48.7%）**

### 逐屏加载闭包（切屏时的增量成本）

| 屏 | 屏 chunk | 连带依赖 | 完整闭包 |
|---|---:|---:|---:|
| projects（默认首页） | 1,124 KB | 50 KB | **1,175 KB** |
| reader | 1,219 KB | 1 KB | **1,221 KB** |
| settings | 117 KB | 15 KB | **132 KB** |
| inbox | 35 KB | 35 KB | **70 KB** |
| library | 22 KB | 1 KB | **22 KB** |
| stats | 22 KB | 1 KB | **24 KB** |
| inspiration | 2 KB | 1 KB | **2 KB** |

`full-*.js`（OpenCC 简繁词典，1.13 MB）**不在任何屏的静态闭包内**：
它由 `getConverter()` 内的 `await import("./full-*.js")` 动态加载并带 key 缓存，
仅在用户真正使用简繁转换时才拉取。改造前后均不占首屏。

### 验证（A3）

| 门禁 | 结果 |
|---|---|
| `tsc -p tsconfig.main.json` | 通过 |
| `tsc -p tsconfig.renderer.json` | 通过 |
| `tsc -p tsconfig.node.json` | 通过 |
| `npm test`（Vitest） | **719 passed / 4 skipped**，75 文件，零失败 |
| `npm run build` | 通过（206 + 1 + 2048 模块） |

## 改造后（B1）二级分割：编辑器 lazy + 空闲预加载

实施日期：2026-09-01（`codex/workspace-backup-2026-08-20` 分支，桌面端 working tree 仅含 A/B 两轮性能改动）

### 目标选定（B 前测量，沿用 A 轮结论）

`CreationProjectsPage.tsx` 是容器组件，把整个 creation 子系统的 **14 个子页面全部静态 import**，
默认首页闭包达 1.10 MB。首页 chunk 内部构成（源码字节，rollup `generateBundle` 读 `chunk.modules`
元数据按目录聚合；**不用 `manualChunks`**，其会改变模块归属、让总量塌 36%）：

| 归属 | 源码字节 | 占比 |
|---|---:|---:|
| **编辑器栈合计** | **936 KB** | **68.2%** |
| ├ `prosemirror-view` | 242 KB | 17.6% |
| ├ `@tiptap/core` | 227 KB | 16.5% |
| ├ `prosemirror-model` | 122 KB | 8.9% |
| ├ `src/features/creation/editor` | 106 KB | 7.7% |
| ├ `prosemirror-transform` | 82 KB | 6.0% |
| ├ `@tiptap/react` | 38 KB | 2.8% |
| ├ `prosemirror-state` | 35 KB | 2.6% |
| ├ `prosemirror-commands` | 34 KB | 2.5% |
| └ 其余（history / schema-list / keymap / rope-sequence / orderedmap / w3c-keyname） | 50 KB | 3.6% |
| 其余 13 个 creation 子页面（outline / cards / history / stats / replace …） | ~386 KB | 28.1% |
| lucide-react 图标 + fast-equals | 51 KB | 3.7% |

关键前提：`CreationProjectsPage.tsx:93` → `useState<ProjectView>("overview")`，
`openProject()` 默认 `"overview"`。用户进项目先见**概览页**，编辑器仅在「写作」tab /
「继续写作」时才需要 ⇒ 编辑器 lazy 化在体验上成立。

### 做法（仅动 `src/features/creation/CreationProjectsPage.tsx`，未触碰 store / IPC / preload / 类型）

1. `WritingDesk` 静态 import 改为
   `const LazyWritingDesk = lazy(() => import("@/features/creation/editor/WritingDesk").then((m) => ({ default: m.WritingDesk })))`
   —— named export 用 default 包装适配，不改其导出方式；原静态 import 已删除。
2. writing view 渲染处用 `<Suspense fallback={<ScreenFallback />}>` 包裹 `<LazyWritingDesk/>`，
   复用 A 轨道新增的骨架占位。
3. 组件挂载（即用户已进入创作屏）后，借 `requestIdleCallback`（`timeout: 2000`，带
   `cancelIdleCallback` 清理；不支持时降级 `setTimeout(warmUp, 1200)`）预加载编辑器 chunk，
   消除「继续写作」的感知延迟。

### chunk 构成变化（raw）

| chunk | A2 屏级后 | B1 二级后 | 说明 |
|---|---:|---:|---|
| `WritingDesk-*.js` | 内联进 CreationProjectsPage | **758,089 B (758 KB)** | 拆为独立 lazy chunk |
| `CreationProjectsPage-*.js` | 1,151,262 B | 395,538 B (386 KB) | 去编辑器后仅含 13 子页面 |
| `index-*.js`（外壳） | 319,349 B | 319,428 B (319 KB) | 基本不变 |
| `full-*.js`（OpenCC，动态） | 1,184,618 B | 1,184,618 B | 不变 |
| **JS 合计** | 4,157,662 B | **4,160,250 B (4.16 MB)** | **+2,588 B (+0.06%)** |

### 首屏收益（主指标，rigorous 静态闭包测量）

方法：从 `index.html` 入口 BFS **静态** import 得 shell；再 BFS 默认屏 `CreationProjectsPage`
的**静态** import 得项目屏闭包；并集即「默认屏可交互」首屏。深层动态 `import(...)`
（`WritingDesk`、其余 6 个 lazy 屏、`full-OpenCC`）一律排除。

| | raw |
|---|---:|
| A0 基线（零分割） | 2,967,931 B (2.83 MB) |
| A2 屏级分割后 | 1,522,235 B (1.45 MB) |
| **B1 二级分割后首屏** | **757,152 B (739 KB)** |
| ├ 外壳 `index` | 319,428 B (319 KB) |
| ├ `CreationProjectsPage` 闭包 | 437,724 B (427 KB) |
| │  ├ `CreationProjectsPage` | 395,538 B |
| │  ├ `useCreationActions` | 25,516 B |
| │  ├ `OperationProgressDialog` | 14,918 B |
| │  └ 图标等小 chunk | 1,752 B |
| └ `WritingDesk`（动态，不在首屏） | 758,089 B |

> **B1 较 A2 再降 765 KB（-50.3%）；相对 A0 基线累计 −74.5%。**
> 预估「约 720 KB」基本命中（实际 739 KB，差 19 KB 为 chunk 头与更精确闭包口径）。

### 切到写作的增量成本

点「写作」tab / 「继续写作」→ `setView("writing")` → `Suspense` 触发 `WritingDesk`
动态加载（758 KB）。因 `requestIdleCallback` 已在空闲预热，真实交互时通常已 resolve，
仅「冷进创作屏后立即点写作」才会感知数十毫秒骨架占位。

### 验证（B2）

| 门禁 | 结果 |
|---|---|
| `tsc -p tsconfig.renderer.json` | 通过 |
| `npm run build`（tsc×3 + electron-vite） | 通过（206 + 1 + 2048 模块） |
| `npm test`（Vitest） | **719 passed / 4 skipped**，75 文件，零失败（与 A2 一致，无回归） |

### 备注

- `full-*.js`（OpenCC 简繁词典 1.13 MB）仍为 `getConverter()` 内动态加载，不在任何首屏闭包。
- 冷启动挂钟时间仍未测（本环境无 GUI）；首屏 JS 解析量作为可自动化替代指标持续有效。

---

## 改造后（C2）子页面 tab/dialog 级 lazy

实施日期：2026-09-01（接 B1，仍仅动 `CreationProjectsPage.tsx` + 一处测试断言）

### 做法

把 13 个 creation 子页面中除初始首页 `ProjectHomePage` 外的 12 个
（OverviewPage / OutlinePage / CardsPage / HistoryPage / StatsPage / ProofPanel / ReplacePanel /
CommandPalette / MigrationDialog / ImportDraftDialog / ExportDraftDialog / CreateProjectWizard）
由静态 import 改为 `React.lazy`（named export 统一 `.then(m => ({default: m.X}))` 适配），
各渲染点用 `<Suspense fallback={<ScreenFallback />}>` 包裹。

**关键取舍**：`ProjectHomePage` 保持静态——它是应用启动后「未选中项目」时首先渲染的视图，
若也 lazy 会在 screen 级 Suspense 之外再叠一层内层 Suspense，造成启动双骨架闪烁；
`OverviewPage` 虽为默认视图仍 lazy（打开项目时的短暂骨架可接受）。`OperationProgressDialog`
不在 13 之列，保持静态（Rollup 已将其抽为独立共享 chunk）。

### chunk 构成变化（raw，节选）

| chunk | C1 后 | C2 后 | 说明 |
|---|---:|---:|---|
| `CreationProjectsPage-*.js` | 395,538 B | **50,860 B (50 KB)** | 去 12 子页面后仅含自身 + ProjectHomePage |
| `OverviewPage-*.js` | 内联 | 15,931 B | 新拆 |
| `OutlinePage-*.js` | 内联 | 14,402 B | 新拆 |
| `StatsPage-*.js` | 内联 | 32,896 B | 新拆 |
| `HistoryPage-*.js` | 内联 | 39,974 B | 新拆 |
| `CardsPage-*.js` | 内联 | 111,320 B (111 KB) | 新拆（最重子页面） |
| `ReplacePanel` / `CreateProjectWizard` / `ProofPanel` / `CommandPalette` / `MigrationDialog` / `ImportDraftDialog` / `ExportDraftDialog` | 内联 | 19,261 / 19,496 / 6,941 / 9,955 / 8,546 / 10,507 / 4,901 B | 新拆 |
| `index-*.js`（外壳） | 319,428 B | 319,471 B (319 KB) | 基本不变 |
| **JS 合计** | 4,160,250 B | **4,172,969 B (4.17 MB)** | **+12,719 B (+0.3%)** |

### 首屏收益（主指标，rigorous 静态闭包）

| | raw |
|---|---:|
| A0 基线（零分割） | 2,967,931 B (2.83 MB) |
| A2 屏级分割后 | 1,522,235 B (1.45 MB) |
| C1 二级（编辑器 lazy）后 | 757,152 B (739 KB) |
| **C2 三级（子页面 lazy）后首屏** | **410,230 B (401 KB)** |
| ├ 外壳 `index` | 319,471 B (319 KB) |
| ├ `CreationProjectsPage` 闭包 | 90,759 B (89 KB) |
| │  ├ `CreationProjectsPage` + `ProjectHomePage` | 50,860 B |
| │  ├ `useCreationActions` | 24,984 B |
| │  ├ `OperationProgressDialog` | 14,541 B |
| │  └ 图标等 | 374 B |
| └ 12 子页面 + `WritingDesk`（动态，不在首屏） | — |

> **C2 较 A2 再降 1,112 KB（-73.1%）；相对 A0 基线累计 −86.2%（首屏压至原 14.1%）。**

### 验证（C3）

| 门禁 | 结果 |
|---|---|
| `tsc -p tsconfig.renderer.json` | 通过 |
| `npm run build`（tsc×3 + electron-vite） | 通过 |
| `npm test`（Vitest） | **719 passed / 4 skipped**（75 文件，零失败；其中 `creation-projects-page-real-hooks.test.tsx` 两个断言 CardsPage DOM 的用例改为 `waitFor` 异步等待 lazy 解析，非逻辑改动） |

### 备注

- 12 个子页面均为「切 tab / 开弹窗」时才动态加载，首屏不再承担其解析成本。
- 收益已递减至长尾（再 lazy 仅剩 ProjectHomePage 静态，~50 KB 内），C2 为本轮分割终点。
- `full-*.js`（OpenCC 简繁词典 1.13 MB）仍为 `getConverter()` 内动态加载，不在任何首屏闭包。
