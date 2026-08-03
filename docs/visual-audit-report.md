# 纸墨视觉全局收敛 — 审计报告与交付清单

> 日期：2026-08-03 | 基线：`build_log22` BUILD SUCCESSFUL | 设备：小米 c49ac6cf (1220×2712)

---

## 一、审计结论：哪些令牌已存在、哪些调用点未接入

### ✅ 已存在的全局令牌（定义完整，无需新增）

| 令牌类别 | 定义位置 | 状态 |
|----------|----------|------|
| **ColorScheme**（PaperInkLight/Dark） | `ui/theme/Theme.kt` | ✅ 品牌绿 #365C4A / #8FAF9D 全站唯一 |
| **Typography**（宋体大标题+黑体其余） | `ui/theme/Typography.kt` | ✅ display*/headlineLarge = Serif |
| **ReaderPaperPalette**（5 态纸张） | `ui/theme/ReaderPaperPalette.kt` | ✅ 白/暖=#365C4A 护眼=#2E6B57 夜读=#8AA6D8 |
| **ComponentSpec**（圆角/间距规格） | `ui/theme/ComponentSpec.kt` | ✅ card=18dp sheet=28dp list=14dp pill=全圆 |
| **LayoutTokens**（页面边距体系） | `ui/layout/LayoutTokens.kt` | ✅ page/content/section/micro 间距 |
| **AppShapes** | `ui/theme/Shape.kt` | ✅ 统一形状令牌 |
| **共享组件** | `ui/components/SharedComponents.kt` | ✅ SectionCard / SettingRow / SelectablePill / FullEmptyState / SectionDivider / SheetHandle |

### 📊 接入覆盖率

| 共享组件 | 已接入屏幕数 | 覆盖率 |
|----------|-------------|--------|
| SectionCard | ~15 个页面 | 高 |
| SettingRow | ~12 个设置页 | 高 |
| SelectablePill | ~8 个筛选场景 | 高 |
| FullEmptyState | ~10 个空态场景 | 高 |
| SectionDivider | 全局 | 完整 |
| SheetHandle | 所有底部弹层 | 完整 |

### 🔍 未接入令牌的残留（本次修复前）

| 残留类型 | 文件 | 行为 | 处置 |
|----------|------|------|------|
| `RoundedCornerShape(14.dp)` | `ShelfSearchRoute.kt` | 输入框圆角 | ✅ **已改为** `spec.listItemShape` |
| `RoundedCornerShape(50)` | `ShelfSearchRoute.kt` | 无痕按钮圆角 | ✅ **已改为** `spec.pillShape` |
| `RoundedCornerShape(16.dp)` | `BookDetailSheet.kt` | 进度帽圆角 | 🟡 保留（阅读器语义） |
| `RoundedCornerShape(12.dp)` | `ReaderInteractionLayer.kt` | 选区手柄 | 🟡 保留（阅读器语义） |
| `RoundedCornerShape(8.dp)` | `ReaderThemeSheet.kt` / `ReaderTocSheet.kt` | 阅读器面板控件 | 🟡 保留（阅读器语义） |
| `Color(0xFF…)` | `MainActivity.kt` | 状态栏平台色 | 🟡 保留（系统 API 要求） |
| `Color(0xFF…)` | `PagedTxtReaderHost.kt` | 选区/朗读高亮 | 🟡 保留（阅读器语义） |
| `fontFamily = FontFamily.Serif` | `SealMark.kt` | 印章宋体 | 🟡 保留（业务语义·印章） |
| `fontFamily = FontFamily.Monospace` | `DiagnosticsSubPage.kt` | 诊断代码字体 | 🟡 保留（功能需要） |

**结论：非阅读器语义的硬编码残留仅 2 处，已全部收敛。**

---

## 二、改动列表

### 本次实际改动（1 个文件，3 处编辑）

#### `android/.../ui/screen/shelf/ShelfSearchRoute.kt`

| 编辑 | 改动 |
|------|------|
| import 删除 | 移除 `import androidx.compose.foundation.shape.RoundedCornerShape` |
| import 新增 | 新增 `import com.creationreadingassistant.ui.theme.LocalComponentSpec` |
| 第 ~138 行 | `shape = RoundedCornerShape(14.dp)` → `shape = LocalComponentSpec.current.listItemShape` |
| 第 ~158 行 | `shape = RoundedCornerShape(50)` → `shape = LocalComponentSpec.current.pillShape` |

**视觉影响：零变化**（listItemShape=14dp, pillShape=全圆，与原魔法数字一致）。目的是消除魔法数字，统一走令牌。

### 前序改动（已在之前会话完成，本次确认无回归）

| 文件 | 改动内容 | 状态 |
|------|----------|------|
| `Typography.kt` | 大标题 4 档加回 FontFamily.Serif（宋体） | ✅ 已提交 |
| `ReaderPaperPalette.kt` | 白/暖 accent→#365C4A, 护眼→#2E6B57, 夜读→#8AA6D8 | ✅ 已提交 |
| `ReaderScaffold.kt` | 修复 eyeCareOverlayAlpha 未定义编译错 | ✅ 已提交 |
| `docs/theme_decision_log.md` | 新建 6 项决策记录 | ✅ 已提交 |

---

## 三、构建与测试结果

```
构建：  ./gradlew.bat :app:assembleDebug --no-daemon  →  EXIT 0 ✅
单测：  :app:testDebugUnitTest                →  59 套件 / 581 用例 / 0 失败 / 0 错误 ✅
APK：   app/build/outputs/apk/debug/app-debug.apk  (47 MB, 2026-08-03 11:46)
安装：  adb -s c49ac6cf install -r             →  Success ✅
```

**无关失败：无。** 桌面端 `splitTxtChapters` 的 4 个既有测试失败（gap audit A10）不在 android/ 范围内。

---

## 四、真机双模走查发现

### 浅色模式 — ✅ 通过

| 页面 | 视觉检查项 | 结果 |
|------|-----------|------|
| 首页 | 暖纸底、品牌绿卡片、SectionCard 一致、继续阅读封面、统计概览、灵感空态 | ✅ 无蓝/紫、圆角一致、间距规范 |
| 书架 | 网格封面、EPUB/TXT 角标、进度条、筛选栏、视图切换 | ✅ 封面圆角一致、品牌绿选中态 |
| 灵感 | 筛选 pill（全部选中绿）、排序、书籍网格/空态 | ✅ pill 用 spec.pillShape |
| 统计 | 时间 pill（本周选中绿）、数据卡片、趋势图占位、书籍状态条 | ✅ 卡片用 SectionCard、进度条品牌绿 |
| 我的 | 信息卡、同步/笔记、SettingRow 列表、分组标题 | ✅ SettingRow 高度一致、箭头对齐 |

### 深色模式 — ✅ 通过

| 页面 | 视觉检查项 | 结果 |
|------|-----------|------|
| 首页 | 暗底、品牌绿 #8FAF9D、继续阅读卡片、统计概览 | ✅ 无蓝/紫、内容清晰 |
| 书架 | 暗底亮字、品牌绿选中态、网格封面、角标 | ✅ 视觉正确 |
| 灵感 | 空态（还没有灵感）+ 筛选 pill | ✅ 无重影 |
| 统计 | 时间 pill、数据卡片、趋势图、书籍状态 | ✅ 无重影 |
| 我的 | 信息卡、SettingRow 列表、分组 | ✅ 设置行对齐、无重影 |

#### 关于"深色模式鬼影/首页无法点击"的排查结论

**原报告现象：** 在先前的快速截图走查中，深色模式下切换 Tab 时似乎出现"旧页面内容半透明透出当前页"，且"首页 Tab 无法点击"。

**本次严谨复现结果：**
1. 使用 `uiautomator` 重新校准底部导航栏可点击区域后，5 个 Tab 在深色模式下**全部可以正常点击**。
2. 每次点击后**等待 1.5s** 再截图，所有页面均干净、无鬼影。
3. 点击后**立即截图**有时会抓到切换前一帧的内容，这是 `screencap` 与 touch 事件在 Android SurfaceFlinger 层不同步造成的**测试时序假象**，不是代码层 bug。

**根因确认：**
- `Theme.kt` 中 `PaperInkDarkColorScheme` 所有 surface/background token 均为不透明（`0xFF` 前缀），**无 alpha 通道**。
- `AppNavigation.kt` 使用标准 `NavHost`（非 Accompanist `AnimatedNavHost`），默认无跨页过渡动画，同一时刻只组合一个目的地。
- 各顶层页面均通过 `AppScreenScaffold` → `Scaffold` 获得不透明的 `background` 颜色。

**结论：深色模式功能正常，原"鬼影/首页无法点击"为测试方法（坐标偏差 + 未等待稳定）导致的假阳性。**

---

## 五、保留的硬编码及原因

| 硬编码 | 位置 | 保留原因 |
|--------|------|----------|
| `RoundedCornerShape(16/12/8).dp` | ReaderInteractionLayer / ThemeSheet / TocSheet / BookDetailSheet | **阅读器纸张语义内**——阅读器是独立视觉区域，由 ReaderPaperPalette 管理，允许与外壳不同的圆角规格 |
| `Color(0xFF...)` | MainActivity（状态栏）、PagedTxtReaderHost（选区高亮） | **平台 API 约束**（statusBarColor 需要 Int Color）和 **阅读器选区语义** |
| `FontFamily.Serif` | SealMark.kt | **印章业务语义**——印章必须用宋体，这是设计决策不是主题泄漏 |
| `FontFamily.Monospace` | DiagnosticsSubPage / ReaderHelpers | **代码/诊断显示功能需求**——等宽字体用于日志和技术信息展示 |

---

## 六、下一步行动

1. ~~**[P1] 二级页面深色模式验证**~~ ✅ 已完成（2026-08-03）—— 搜索/整理/书籍详情/导入页深色走查通过（uiautomator XML + 代码核验，截图不可视读）
2. ~~**[P1] 阅读器外壳菜单深色验证**~~ ✅ 已完成（2026-08-03）—— TTS 面板、overflow（AI/笔记/搜索）可正常打开；底部菜单为覆盖层，accent 来自 `paper.accent`，与纸墨一致；阅读器不显示应用 5 Tab 底栏（设计如此）
3. **[P2] 提交** —— 当前 ShelfSearchRoute.kt 收敛改动 + 前序品牌收敛改动已合入（P0 鬼影已证伪）。本次提交保留全部历史 `docs/*.md`（未删，遵循 guardrail「不删历史文件」），仅提交合法重构搬迁（`ui/screen/**` 拆分重定位）+ 图标清理（0 引用）+ 视觉收敛 + 新增 `docs/visual-audit-report.md` / `docs/theme_decision_log.md`。

### 提交前复验（2026-08-03）

- `assembleDebug` + `testDebugUnitTest`（Gradle 8.9，JDK 17）→ **BUILD SUCCESSFUL**，无 error / 失败用例。
- 测试基线：59 套件 / 581 用例 / 0 失败（与绿态基线一致）。
- 构建探针日志（`build_*.txt` 等）与 `android/.kotlin/` 缓存已加入 `.gitignore`，不进版本库。
