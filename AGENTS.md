# AGENTS.md

本仓库包含两个活跃产品线（桌面端 Electron 与原生 Android `android/`），源码边界清晰，不共享运行时代码。旧 Capacitor `mobile/` 产品线已删除，历史存档见 `archives/frozen-mobile/`。

## 桌面端 (Electron)

| 维度 | 说明 |
|------|------|
| 源码目录 | `src/`（渲染进程 React）、`electron/`（主进程 + preload） |
| 配置入口 | 根 `package.json`、`electron.vite.config.ts`、`tsconfig.*.json` |

### 命令（均在仓库根执行）

```bash
npm install            # 安装依赖
npm run dev            # 启动开发（electron-vite dev）
npm test               # vitest（含设计令牌守卫与电池锚点审计两条集成测试）
npm run build          # 类型检查 + 构建（tsc + electron-vite build）
npm run preview        # 预览构建产物（electron-vite preview）
npm run dist:dir       # 构建 + 打包为目录
npm run dist           # 构建 + 打包安装程序
```

设计令牌与版式刻度有独立的守卫，规则写在 `scripts/verify-design-tokens.mjs` 顶部：

| 命令 | 作用 |
|------|------|
| `npm run verify:design-tokens` | 全量守卫：令牌定义、对比度、阴影/圆角/间距/字号/行高棘轮、死声明与死色账 |
| `npm run audit:test-anchors` | 核对注入测试里每个 needle 在当前源码中命中且唯一 |
| `npm run audit:script-selectors` | 核对 `scripts/*.mjs` 里每个字面量选择器的类名在 `src/`、`electron/` 里仍有出处 |
| `npm run audit:contrast` | 运行时对比度普查：12 个主视图 + 11 个弹层（含全局搜索 Ctrl+K 命中 <mark>、写作速查 Ctrl+Shift+K 面板、阅读器设置抽屉与其「更多设置」二级视图、阅读侧栏高亮与书签列表）+ 5 个设置分区 + 7 种 TXT 正文纸面（含五种颜色正文高亮 <mark>）+ 7 种 MD 正文纸面 + 1 个 EPUB 宿主壳 + 7 种 EPUB 正文纸面 + 1 个 EPUB 统一夜间正文（共 51 个范围；BD-109 起穿进 EPUB 同源 iframe 量正文墨色，BD-110 起量 markdown 渲染面的标题/引用/列表/链接，BD-111 起量正文高亮墨色跟着纸走，BD-112 起量全局搜索命中 mark，BD-113 起量写作速查面板三档 token 字色压在独有染色底上，BD-114 起普查判据沿祖先链累乘 CSS opacity（抓到收件箱「已转卡片」整行 dim 到 2.96:1 与 AI 发送确认排除组 dim 到 2.79:1 两处真缺陷并修复），BD-115 起量阅读器设置抽屉，BD-116 起量其「更多设置」二级视图，BD-118 起量阅读侧栏高亮列表（用户引文），BD-119 修目录（TOC）读态曾用 `text-paper-muted/55`（≈2.2:1；种子 TXT 书无标题、TOC 为空，普查扫不到这一未渲染态），改为不透明 `paper-muted` 并以行尾 Check 图标＋title 表达「已读」，另加源码级（`toc-read-contrast-source.test.ts`）与渲染级（`toc-list-read-state.test.tsx`）护栏——属修复而非新增范围，总数仍 50 个），BD-120 起普查额外实测 `::placeholder`（占位符不是文本节点、主循环永远量不到），并加 `@layer base` 的 `input/textarea::placeholder` 兜底色 `--text-secondary`；修 `.paper-input` 低 alpha（`text-paper-muted/55`，浅 2.16 / 深 3.22）与一批裸控件回落到 UA 默认灰 `#9ca3af`（浅 2.41）这一族真实缺陷，仍属修复非新增范围），BD-121 补进阅读器侧栏「书签」标签页（此前只扫过「高亮」；seed 未播书签，量其"暂无书签"空态与"在当前位置添加书签"按钮），新增一个范围，共 51），晨校与夜校各一遍，逐元素量实际计算色（需 Xvfb 与已构建的 `out/`，约 3 分钟） |
| `npm run inject:design-tokens` | 注入电池：逐颗改写源码验证守卫会咬（约 17 分钟，跑完自动还原） |

`npm test` 已经包含前三条；改版式或颜色后先让它通过再提交。守卫的账全是 0 或已逐条记档，
新增债务必须当场判定用途（收档 / 摘死 / 记档），不允许把预算调高来放行。

后两条目前只能本地跑（`audit:contrast` 要 Xvfb 约 3 分钟，电池要 17 分钟）。已备好
`.github/workflows/design-gates.yml` 把它们接进 CI（`audit:contrast` 随 `src/`、`scripts/`
变动的提交跑（约 3 分钟）；电池只在每周定时与手动触发跑，不给每个 PR 加十七分钟），
但该文件尚未入库——推送 workflow 需要 PAT 带 Workflows 读写权限，当前令牌没有，
所以它现在是本地待激活状态：补上权限后提交该文件即生效，在那之前后两条仍靠本地执行。

---

## 移动端 — 独立原生 Android（当前主线）

| 维度 | 说明 |
|------|------|
| 源码目录 | `android/`（Kotlin + Jetpack Compose + Room） |
| 构建体系 | Gradle（`android/gradlew`），不依赖 Capacitor 或 WebView |
| 命令目录 | `android/` |

### 命令（在 `android/` 目录执行）

```bash
./gradlew :app:testDebugUnitTest     # JVM 单元测试
./gradlew :app:lintDebug             # Lint 检查
./gradlew :app:assembleDebug         # 构建 Debug APK
./gradlew :app:assembleRelease       # 构建 Release APK（需配置签名）
```

---

## 移动端 — Capacitor（已删除，历史存档于 `archives/frozen-mobile/`）

`mobile/` 旧 Capacitor 产品线已于 2026-07-30 彻底删除（P0-A2 冻结后的收尾），当前移动端主线为 `android/`：

- **代码已删除**，仅保留许可证与上游归属存档于 `archives/frozen-mobile/`（`LICENSE`、`NOTICE.md`、`THIRD_PARTY_NOTICES.md`、`legado-reader-core/` 及本说明 `FROZEN.md`）。
- `android/` 不依赖 `mobile/` 的任何运行时代码，删除 `mobile/` 不影响 `android/` 构建与运行。
- 旧 `mobile/android` Release APK 的构建与上传已在 CI/Release 工作流中退役；原生 `android/` Release 迁移属于后续 **P0-A3**。
- 详见 [`archives/frozen-mobile/FROZEN.md`](archives/frozen-mobile/FROZEN.md)。

---

## 如何判断改动归属

| 改动涉及 | 归属 |
|-----------|------|
| `src/**`、`electron/**`、根 `package.json` 的 `dependencies`/`devDependencies`、`electron.vite.config.ts` | 桌面端 |
| `android/**` | 移动端 — 独立原生（当前主线） |
| `docs/**` | 共享文档，不属于任一端的运行时 |

## 共享工作区与并行 Agent

- 工作区中的既有修改均视为用户资产；不得为了“清空状态”执行 reset、checkout、全仓格式化或全仓 renormalize。
- 多个 coding agent 同时工作时必须预先分配互不重叠的文件所有权；发现需要修改他人所有文件时先交给集成者，不得直接越界。
- `package.json`、`package-lock.json`、`scripts/beta-check.mjs`、公共类型和根文档默认由单一集成者维护。
- 并行实现阶段只运行所属模块的定向测试；完整 build、desktop Beta、diff-check 和视觉验收由所有实现结束后的集成者串行执行。
- 未经用户明确要求，不 commit、不 push，也不清理 Android 或冻结归档中的未提交改动。

---

## 权威架构文档

深入了解阅读器引擎设计与技术选型，请参阅：

- [`docs/handoff/current.md`](docs/handoff/current.md) — 当前 Agent 交接入口、活动主线与开放项
- [`docs/architecture/native-android-reader.md`](docs/architecture/native-android-reader.md) — 当前独立原生 Android 阅读器架构
- [`docs/plans/legado-feature-backlog.md`](docs/plans/legado-feature-backlog.md) — 面向阅读体验的候选功能清单
- [`docs/README.md`](docs/README.md) — 当前文档、历史快照与归档边界

## Android 真机验证约束

- `android/` 的 UI 和设备行为只在已连接的真实 Android 手机上验证，不使用 MuMu 模拟器。
- 每次先运行 `adb devices` 核对当前设备，再对所有设备命令使用 `adb -s <serial>`。
- 长时间测试可临时启用 `stay_on_while_plugged_in`，但结束时必须恢复测试前的值。
- 测试报告、TODO、截图说明和交接记录只写"测试 EPUB""测试 TXT"等中性名称，不记录真实测试书名；这不授权修改设备上的书籍数据。
