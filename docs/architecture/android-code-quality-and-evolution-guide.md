# 原生 Android 代码质量与演进规范指南

> 状态：**现行执行依据**  
> 适用范围：`android/`（原生 Kotlin + Jetpack Compose + Room）  
> 目标读者：所有参与原生 Android 阅读器与书架功能开发的 Agent 与工程师

---

## 一、核心设计原则（Core Principles）

1. **离线优先与单一事实源（Single Source of Truth）**：
   - 本地 Room 数据库是所有书籍、进度、标注与分类的唯一权威事实源。
   - UI 只能通过 ViewModel 观察 Repository 暴露的 `StateFlow` / `Flow`，禁止在 UI 组件内直接修改或持有持久化实体的易变副本。
2. **内存有界与流式保护（Bounded Memory & Streaming）**：
   - 绝不允许整本读取大文本。大 TXT 必须遵循 `TextStreamLoader`（5MB 阈值）+ `TxtFileScanner` 索引 + `ReadingUnit`（上限 50,000 字符）有界分页段。
   - 规则替换必须受控于 `ReplaceProjectionScopeProvider`：超过 256K 字符的超大逻辑章必须直接熔断降级为原文有界分页并提示，严禁为替换分配整章内存。
   - 文本投影缓存必须保持有界（当前规范：`BoundedLruCache` 容量恒定为 3 逻辑章）。
3. **坐标系统严密性（Coordinate Fidelity）**：
   - 数据库中的书签、笔记、高亮与已读进度一律使用**原始 Source 全局字符偏移**。
   - 替换净化文本（Display）与源文本（Source）必须通过双向映射（如 `TextOffsetMap`、`MarkdownOffsetMap`）在运行时转换，严禁将 Display 坐标持久化入库。

---

## 二、代码组织与文件拆分准则（Code Organization）

### 2.1 巨型 Composable 文件拆解规范
单个 UI 文件代码量超过 800 行时，必须按照**单一职责与垂直切片**原则进行模块拆分。拆分时遵循：
- **纯逻辑/策略下沉**：计算、排序、草稿验证、格式化等纯 Kotlin 逻辑必须移出 Composable 文件，提取为独立的类或顶层扩展函数，便于编写 JVM 单元测试。
- **子面板独立化**：大型 BottomSheet 或 Screen 必须按功能 Tab / Section 拆为独立的子组件文件。

**重点重构目标清单：**

| 文件 | 当前行数 | 拆解方案（Seam） |
|---|---:|---|
| `HomeArchiveScreens.kt` | ~1,720 | 拆为 `InspirationsScreen.kt`、`CompletedBooksScreen.kt`、`InspirationDetailScreen.kt`，公共卡片与格式化工具独立。 |
| `ReaderSettingsSheet.kt` | ~1,611 | 拆为 `TypographySection.kt`（字体/字号/字距/行距）、`PagingSection.kt`（翻页模式/滚动速度）、`ThemeSection.kt`（背景/纸纹/色温）、`AdvancedSection.kt`。 |
| `BookDetailSheet.kt` | ~1,286 | 拆为 `BookDetailHeader.kt`、`BookReadingStatsSection.kt`、`BookNotesSection.kt`、`BookFileActionsSection.kt`。 |
| `ReaderRulesSheet.kt` | ~1,144 | 将规则草稿管理（Draft）、正向/反向评估、排序计算抽离为纯 Kotlin 策略类，UI 仅保留展示与交互。 |
| `ReaderTocSheet.kt` | ~1,108 | 拆分为章节目录 Tab（`TocChapterList.kt`）、书签 Tab（`TocBookmarkList.kt`）与文本规则快速检测面板。 |
| `ReaderNotesSheet.kt` | ~1,043 | 拆分为批注列表、批注详情编辑面板、导出操作面板。 |

### 2.2 渐进式多模块化演进（Multi-module Roadmap）
当前所有 Android 代码均位于单模块 `:app` 中。为提高增量编译速度和测试隔离性，建议按以下阶段演进：
1. **阶段一（排版与排版数学抽离）**：将 `feature/reader/layout/`（分词、断句、禁则、两端对齐、文本尺寸测量抽象）抽离为独立的纯 Kotlin 库模块 `:core:reader-layout`。此模块不依赖 Android SDK，全部排版用例可纯通过 JVM 单测在秒级跑完。
2. **阶段二（数据层抽象）**：逐步形成 `:core:database` 与 `:core:model`，阻断 UI 对底层 Room DAO 的越界感知。

---

## 三、Jetpack Compose 性能与状态最佳实践（Compose Hygiene）

### 3.1 强制使用不可变集合（Immutable Collections）
- **规则**：所有 `UiState` 数据类中的列表字段，禁止使用标准 `List<T>`，必须统一使用 `kotlinx.collections.immutable` 提供的 `ImmutableList<T>` 或 `PersistentList<T>`。
- **原因**：Compose 编译器将 `java.util.List` 视为 Unstable 类型。哪怕列表项内容没有变化，只要父组件发生重组，所有接收 `List<T>` 的子组件都会强制重组，破坏 Smart Recomposition。
- **模型注解**：跨层传递的外部实体类或复杂嵌套类，显式标注 `@Immutable` 或 `@Stable`。

### 3.2 避免深度状态穿透（Prop Drilling）
- `ReaderScreen` 作为核心容器，包含翻页、选区、TTS、高亮、各类 Sheet 状态。
- 建议使用封装好的状态持有者（如 `ReaderStateHolder`）或者针对高内聚区域提供受控的局部 `CompositionLocal`，避免数十个 lambda 回调通过 5~6 层 Composable 逐级透传。

---

## 四、阅读核心体验与后续演化（Reader Experience）

1. **手势跟手与仿真揭页（Simulation/Peel Turn）**：
   - 现行仿真揭页在松手后才播放动画。
   - **优化目标**：在 `PageTurner.kt` 中实现真正的拖拽实时跟手（Interactive gesture-driven curl），使卷曲角与阴影随着手指在屏幕上的滑动实时变形，松手后再根据滑动速度与位移判定翻页或回弹。
2. **EPUB / Markdown 结构化正则替换与净化**：
   - TXT 的替换净化与双向坐标映射已完全闭环。
   - **优化目标**：后续针对 EPUB（基于 DOM / Spine 节点粒度）与 Markdown（基于抽象语法树 Block 粒度）推进结构保真替换，使所有格式享受相同的广告净化与单处错字纠错（`ReaderCorrectionEntity`）能力。
3. **离线全文索引构建状态透出**：
   - Room v11~v13 派生索引表（`search_terms`、`search_index_coverage`）在后台增量扫描构建。
   - **优化目标**：在书库或设置页展示构建进度指示器（例如：“3/10 本书已完成全文索引”），并提供手动触发或暂停的开关。

---

## 五、合规、API 弃用治理与工程规范（Hygiene & Compliance）

### 5.1 隐私与硬件标识合规（Hardware IDs）
- **现状**：`feature/sync/JsonBridge.kt` 曾直接使用 `Settings.Secure.ANDROID_ID`，导致 Lint 报出 `HardwareIds` 警告，且在应用商店审核中存在隐私合规风险。
- **规范**：
  - 严禁读取硬件设备标识符。
  - 同步凭证必须使用应用级随机 UUID（安装后首次生成并安全持久化于 `SecurePrefs` 或 `DataStore`）。

### 5.2 Compose / Kotlin 废弃 API 治理
- 针对 AndroidX Compose 升级引入的废弃项进行定向清理：
  - 迁移旧版剪贴板调用至现代安全剪贴板 API；
  - 替换已废弃的旧 `menuAnchor()`；
  - 检查并适配 `ModalBottomSheet` 属性变更；
  - 为纯图标补充 `contentDescription`，硬编码中文字符串逐步收口至 `strings.xml`。

### 5.3 数据库全链迁移测试验证（Room Migration）
- 每次新增 Room Schema 迁移（当前已演进至 v13），除了编写单步迁移测试（如 12→13）外，必须保持 `1 -> 13` 全链迁移测试（`MigrationTestHelper`）常绿，确保跨版本老用户升级不崩溃。

### 5.4 工作区与诊断文件隔离（Workspace Hygiene）
- 自动化测试与真机抓取的产物（如 `*.log`、`*.xml`、`*.png`、临时 `.db`）一律禁止散落在 `android/` 根目录下。
- 所有临时诊断文件统一输出至 `android/build/tmp/`、`.workbuddy/` 或由 `.gitignore` 显式排除的目录，严禁提交进版本控制系统。
