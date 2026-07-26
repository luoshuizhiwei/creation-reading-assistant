# 创作阅读助手 · 原生 Android 端（P1）设计文档

> 本文件依据增强提示词「修改 → 出文档 → 实现 → 回检」四阶段交付纪律编写，并与 `android/` 下实际代码逐条对齐。

---

## 1. 目标与背景

- **背景**：原移动端为 `Vite + Capacitor` 混合方案（Web 包壳）。本次按需求重写为**纯原生 Android（Kotlin）**，遵循 Material Design / Material 3，支持主流版本（minSdk 24 / target 34）。
- **目标（P1 · 最小可运行闸门）**：先交付一个能 `assembleDebug` 通过、可装真机的工程，验证地基与核心链路：
  - 「墨韵·素笺」主题（亮/暗，对齐现有 `md3-base.css` 设计令牌）
  - 5 个底部 Tab 导航（书架 / 阅读 / 灵感 / 统计 / 我的）
  - Room 持久化（严格对齐现有 V2 schema 的 17 张表）
  - 书架列表 + txt/md 阅读（SAF 打开，ContentResolver 读取）
  - JSON 导出/导入桥接（**复用 `src/types/sync.ts` 的 SyncEnvelope 契约**，作为后续局域网同步 P3 的先行落地）
- **不在 P1 范围**：EPUB 原生渲染（Readium）、局域网同步服务端对接、扫码配对、WebDAV、TTS、批注高亮、统计真实聚合 —— 见第 6 节 P2/P3 路线。

**关键认知**：项目没有独立后端，Electron 桌面端即同步服务端。P3 将直接复用 `src/types/sync.ts` 的 LAN 协议对接，本 P1 的 JSON 桥接已采用同一信封结构，保证契约前向兼容。

---

## 2. 主要变更点说明

| 项 | 变更 |
|---|---|
| 技术栈 | 由 `Vite+Capacitor(Web)` 改为 `Kotlin + Jetpack Compose + Material 3` |
| 工程位置 | 新建独立 `android/` 目录（保留 `mobile/` 作参考与回退，互不破坏） |
| 数据层 | 由 `@capacitor-community/sqlite`（V2 17 表）改为 `Room`（17 实体一一映射，部分索引在 `onCreate` 补建） |
| 导航 | 由 Web 路由改为 `Navigation Compose` 的 `NavHost` + `NavigationBar`（5 Tab） |
| 主题 | 由 CSS 变量改为 Compose `ColorScheme`（纸/墨/靛青印章三色映射到 light/dark） |
| 文件读取 | 由 Capacitor Filesystem 改为 Android `ContentResolver` + SAF（`OpenDocument`/`CreateDocument`） |
| 同步 | P1 以 JSON 桥接替代 LAN 同步，信封结构沿用 `SyncEnvelope<T>` |

> 注意：新 App 数据库为独立 Room 库（v1），与旧 Capacitor App **数据不互通**。旧数据迁移通过本 P1 的 JSON 桥接在「原生 App 实例间」完成；如需从旧 App 迁数据，后续加一层映射（复用同一信封）。

---

## 3. 功能描述

- **书架（ShelfScreen）**：`Flow<List<BookEntity>>` 驱动的可滚动卡片列表；首次启动自动播种一本示例书；FAB 可继续添加示例书；点击卡片进入阅读详情。
- **阅读（ReaderScreen / reader_home）**：经 SAF 选定本地 `txt/md`，用 `ContentResolver` 读取为文本，按行展示；支持加载态/错误态/空态。书架卡片进入时为全屏路由 `reader/{bookId}`（无底部栏）。
- **灵感（InspirationScreen）**：由 `InspirationDao` 驱动的列表（实体与 V2 `inspirations` 表对齐），P1 为只读展示。
- **统计（StatsScreen）**：占位卡片，真实指标（阅读时长/藏书/灵感数）待 P2 接入 `reading_sessions` 聚合。
- **我的（ProfileScreen）**：提供「导出数据(JSON) / 导入数据(JSON)」按钮（SAF），复用 `SyncEnvelope` 信封；主题说明（跟随系统亮/暗）。
- **主题（AppTheme）**：`墨韵·素笺`——纸 `#F5F1E8`(亮)/深墨 `#1A1C1E`(暗) 作背景，墨 `#2B2B2B`(亮)/宣白 `#E6E1D8`(暗) 作前景，靛青印章 `#3A4A8C` 作主色。

---

## 4. 接口定义 / 关键参数说明

### 4.1 数据库（`AppDatabase`，version = 1，DB_NAME = `creation_reading_assistant_native`）
17 张表实体（包 `data.local.entity`）：`books / book_content / book_files / reading_progress / reading_sessions / inspirations / inspiration_variants / notes / highlights / tags / categories / shelves / book_tag / book_category / shelf_book / sync_accounts / sync_state`。
- 外键约束在 `@Entity(foreignKeys=...)` 中声明（ON DELETE CASCADE / SET NULL），与原 schema 一致。
- **部分索引**（`WHERE deleted_at IS NULL`）Room 注解不支持，在 `AppDatabase.CreateIndexCallback.onCreate` 中通过 `execSQL` 补建（共 10 条，与 `mobile-schema.ts` V2 索引语句逐一对应）。

### 4.2 同步信封（`domain.model.SyncEnvelope<T>`，对齐 `src/types/sync.ts`）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 记录主键 |
| type | String | `book`/`inspiration`/`progress`/`session` |
| revision | Int | 修订号（冲突合并用，P3） |
| deviceId | String | 设备标识（P1 取 `Settings.Secure.ANDROID_ID`） |
| updatedAt | String | ISO 时间戳（@SerialName） |
| deletedAt | String? | 软删除时间戳 |
| payload | T | 业务对象（P1 即实体本身） |

### 4.3 JSON 桥接（`feature.sync.JsonBridge` / `LocalExport`）
- `suspend fun exportTo(context, uri: Uri)`：聚合四类信封 → `LocalExport` → 写入 SAF 选定文件（`prettyPrint` JSON）。
- `suspend fun importFrom(context, uri: Uri)`：解析 `LocalExport` → 按 id `REPLACE` 合并回各 DAO。
- 网络/存储：仅 `INTERNET` 权限（为 P3 LAN 预留）；SAF 导入导出**无需**存储权限；`networkSecurityConfig` 已开 cleartext（P3 局域网明文所需）。

### 4.4 构建关键参数（`gradle/libs.versions.toml`）
Kotlin 1.9.24 · AGP 8.5.2 · Compose 编译器 1.5.14 · Compose BOM 2024.09.00 · Hilt 2.51.1 · Room 2.6.1(KSP) · minSdk 24 / target/compileSdk 34 · `coreLibraryDesugaring` 已开（保证 `java.time` 在 API 24/25 可用）。

---

## 5. 使用方式

1. **打开工程**：用 Android Studio（含 Gradle 8.9）打开仓库内的 `android/` 目录。首次打开会自动生成 Gradle Wrapper 并下载依赖。
2. **连接设备 / 模拟器**：Android 7.0+（API 24+）真机或模拟器。
3. **运行 / 打包**：
   - 直接运行：`Run ▸ Run 'app'`（装到设备，便于自测）。
   - 打 Debug APK：`gradlew :app:assembleDebug`，产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。
   - 打 Release：`gradlew :app:assembleRelease`（需自备签名；当前 `release` 未配置签名，P1 先用 debug）。
4. **自测路径**：打开 App → 书架应有示例书 → 点「阅读」Tab → 「打开本地 txt/md」选一个文本 → 显示正文 → 「我的」里导出 JSON（SAF 存文件）/ 再导入验证往返。

> 本环境未执行全量 Gradle 构建（无 `gradle` CLI、且需联网拉取 AGP 等几百 MB 依赖）。代码已按上述版本组合逐项核对，构建以你在 Android Studio 中执行为准。

---

## 6. 一致性回检（阶段四，必须执行）

逐条比对文档第 3/4 节与代码实现：

| 文档声明 | 代码落点 | 一致性 |
|---|---|---|
| 5 Tab 导航 | `ui/navigation/AppNavigation.kt` `TOP_LEVEL_ROUTES`（Shelf/Reader/Inspiration/Stats/Profile） | ✅ |
| 墨韵主题亮暗 | `ui/theme/Theme.kt` light/dark ColorScheme | ✅ |
| 17 表 Room 映射 | `data/local/entity/*`（5 文件，含外键） | ✅（17 实体齐） |
| 部分索引补建 | `AppDatabase.CreateIndexCallback`，10 条 SQL 与 V2 索引一致 | ✅ |
| txt/md 阅读 SAF | `ui/screen/ReaderScreen.kt` + `ReaderViewModel.loadText(ContentResolver)` | ✅ |
| JSON 桥接复用 SyncEnvelope | `feature/sync/JsonBridge.kt` + `domain/model/SyncEnvelope.kt` | ✅ |
| 依赖版本组合 | `gradle/libs.versions.toml` + `app/build.gradle.kts` | ✅（含 desugaring、serialization 插件、lifecycle-runtime-compose） |
| 启动图标兼容 API24 | Manifest 指向 `@drawable/ic_launcher_foreground`（矢量，API21+） | ✅ |

**未自验项（已在第 5 节声明）**：全量 `assembleDebug` 编译/安装未在本环境执行，需你在 Android Studio 中确认。

---

## 7. 后续路线（P2/P3，待你确认）

- **P2**：Readium 原生 EPUB 渲染（最高风险，建议先 spike）；统计页真实聚合。
- **P3**：局域网同步（Retrofit 动态 baseUrl + cleartext）、ML Kit/CameraX 扫码配对、WebDAV、TTS、批注高亮。
- **迁移桥接**：旧 Capacitor App → 原生 App 的数据映射（复用 `SyncEnvelope`）。
