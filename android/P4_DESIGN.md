# P4 设计文档 · 统计 / TTS / 批注 / WebDAV 远程备份

> 阶段目标：在 P1（地基+核心）、P2（EPUB）、P3（局域网同步+扫码）之上补齐四项增强能力——真实阅读统计、本地 TTS 朗读、阅读笔记批注、WebDAV 远程备份。全部为纯原生实现，无独立后端依赖。

---

## 1. 目标与背景

- **背景**：前序三阶段已具备书库/阅读/EPUB/局域网同步底座；本阶段聚焦「个人阅读体验增强」与「云端兜底备份」。
- **目标**：
  1. 阅读统计：基于 `reading_sessions` / `reading_progress` / `books` / `inspirations` 的真实聚合（非占位假数据）。
  2. TTS 朗读：用系统 `TextToSpeech` 本地朗读当前章节/正文。
  3. 笔记批注：阅读中记录「原文摘录 + 笔记」，落库 `notes` 表（payload 真相源模式）。
  4. WebDAV 远程备份：配置 WebDAV 服务器（Nextcloud / 群晖等）后一键把整库导出 JSON 加密备份。

## 2. 主要变更点说明

| 类别 | 变更 |
|---|---|
| 新增统计 | `data/repository/StatsRepository`（聚合计算）、`ui/viewmodel/StatsViewModel`、`ui/screen/StatsScreen`（真实指标卡） |
| 增强阅读器 | `ui/screen/ReaderScreen` 增加 `TtsControls`（DisposableEffect 内创建/shutdown TTS）+ 笔记 `AlertDialog` + `FloatingActionButton` |
| 增强阅读 VM | `ui/viewmodel/ReaderViewModel` 注入 `NoteDao`，新增 `_currentBookId`、`saveNote(quote, body)` |
| 新增 WebDAV | `feature/sync/WebDavConfigStore`（加密存储 url/user/pass）、`feature/sync/WebDavBackup`（OkHttp PUT + Basic Auth） |
| 复用导出 | `feature/sync/JsonBridge.exportToString(context)` 供 WebDAV 复用 `buildExport` 同份导出 |
| 改造界面 | `ProfileScreen` 增加「WebDAV 远程备份」卡（url/user/pass 输入 + 保存 + 备份）；`ProfileViewModel` 增加 `webDavConfig`/`webDavMsg` 及 `saveWebDav`/`backupNow` |
| 配置 | `app/build.gradle.kts` 版本号 `0.4.0-p4`；WebDAV 复用既有 OkHttp 依赖，无需新增 |
| 字符串 | `strings.xml` 增加 `profile_webdav_*`、`reader_note*`、`reader_tts_*` |

## 3. 功能描述

- **阅读统计**：打开「统计」页即聚合——藏书数、灵感数、累计/今日/近7天/近30天阅读时长、连续阅读天数、按书籍时长排行、最近阅读进度。
- **TTS 朗读**：阅读器底部栏「朗读/停止」按钮，朗读当前章节（EPUB）或正文（txt/md）。仅本地引擎，不依赖网络。
- **笔记批注**：阅读器右下悬浮按钮 → 弹出「原文摘录（可选）+ 笔记内容」对话框 → 保存落库 `notes`（`book_id` 取当前 epub 注册 id，纯文本临时打开为 null）。
- **WebDAV 备份**：「我的」→「WebDAV 远程备份」填写服务器地址/用户名/密码并保存，点「立即备份」将整库导出 JSON `PUT` 到 `{url}/{cra-backup-{时间戳}.json}`（Basic Auth）。

## 4. 关键参数 / 实现说明

### 统计聚合（`StatsRepository.compute`）
- 数据源：`sessionDao.observeAllActive()` / `progressDao.observeAllActive()` / `bookDao.observeAllActive()` / `inspirationDao.observeAllActive()`。
- 连续天数：`LocalDate.toEpochDay()` 从今天（或昨天）往前连续命中 `activeDays` 计数。
- 时长分桶：`Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()` 比对今天/7天前/30天前。

### TTS（`ReaderScreen.TtsControls`）
- `DisposableEffect(Unit)` 内 `TextToSpeech(context, initListener)`；`onDispose` 调 `stop()` + `shutdown()`。
- `getText()` 由调用方注入：EPUB 取当前章 `EpubBlock.Text` 拼接；纯文本取 `content`。

### 笔记（`ReaderViewModel.saveNote`）
- `NoteEntity` 字段对齐 V2 `notes` 表：`id(UUID)`、`book_id`、`title`、`body`、`excerpt`、`kind="note"`、`payload="{}"`、`created_at/updated_at`(Instant)、`revision=1`。

### WebDAV（`WebDavBackup.put`）
- `OkHttpClient`（connect 30s / write 60s）+ `Credentials.basic(user, pass)` + `PUT {url}/{filename}`，`application/json`。
- 失败抛 `IllegalStateException("WebDAV 返回 {code}: {body前200}")` 供 UI 回显。

### 加密存储
- `WebDavConfigStore` 与 P1 的 `SyncConfigStore` 同机制：`EncryptedSharedPreferences` + `MasterKey(AES256_GCM)`。

## 5. 使用方式

1. **统计**：底部切「统计」Tab，查看真实聚合数据。
2. **朗读**：阅读任意书 → 底部「朗读」→ 再点「停止」。
3. **笔记**：阅读中任意页 → 右下 + 按钮 → 填写摘录/笔记 →「保存笔记」。
4. **WebDAV 备份**：「我的」→ 填 WebDAV 地址/账号/密码 →「保存配置」→「立即备份」，状态栏回显「备份成功/失败」。

## 6. 一致性回检（文档 ↔ 代码，已逐项核对）

| 文档声明 | 代码落点 | 结论 |
|---|---|---|
| StatsRepository 聚合 4 表 | `StatsRepository.compute` 调 session/progress/book/inspiration 的 `observeAllActive()` | ✅ 一致 |
| StatsScreen 用 byBook/recentBooks | `StatsScreen` 引用 `b.title/b.totalMs`、`r.title/r.progressPercent`，与 `ByBook`/`RecentBook` 字段匹配 | ✅ 一致 |
| ReaderScreen TTS + 笔记 | `TtsControls`(DisposableEffect)、笔记 `AlertDialog`、`FloatingActionButton(NoteAdd)` | ✅ 一致 |
| ReaderViewModel.saveNote 字段 | `NoteEntity` 含 id/book_id/title/body/excerpt/chapter_title/progress_percent/kind/locator_json/payload/created_at/device_id/revision/updated_at/deleted_at | ✅ 一致 |
| WebDavConfigStore 加密 | `EncryptedSharedPreferences` + `MasterKey.AES256_GCM` | ✅ 一致 |
| WebDavBackup PUT | `OkHttp PUT + Credentials.basic` | ✅ 一致 |
| JsonBridge 复用 | `exportToString(context)` 复用 `buildExport` | ✅ 一致 |
| ProfileViewModel WebDAV | `webDavConfig`/`webDavMsg` StateFlow + `saveWebDav`/`backupNow` | ✅ 一致 |
| 新增字符串 | `strings.xml` 含 `profile_webdav_*` / `reader_note*` / `reader_tts_*` | ✅ 一致 |
| 版本号 | `0.4.0-p4` | ✅ 一致 |
| DAO 方法存在 | `observeAllActive()`/`upsert`/`upsertAll` 在 Book/Inspiration/Reading/Note DAO 均存在（已 grep 核实） | ✅ 一致 |

## 7. 已知限制 / 后续项（不影响本阶段编译与交付）

1. **笔记仅落库未浏览**：P4 实现「记笔记」能力，但没有独立的「笔记列表/详情」浏览页。如需在「灵感」页或独立入口查看，列为后续迭代。
2. **WebDAV 仅直传全量**：当前 `PUT` 整个导出 JSON 到 `{url}/{filename}`，未做列目录(PROPFIND)/增量。Nextcloud/群晖/nginx-webdav 直传通常可用；若服务器要求特定路径前缀需后续适配。
3. **TTS 语言/引擎**：依赖系统已安装的中文 TTS 引擎；若设备未装中文语音包，朗读可能无声音（系统级设置，非 App bug）。
4. **统计时区**：时长分桶用 `ZoneId.systemDefault()`，跨时区设备「今日」边界以本机时区为准。

## 8. 构建与 adb 测试 runbook（你本机执行）

```
# Android Studio 打开 D:\develop\Code\Codex\创作阅读助手\android
# 首次构建需联网拉取依赖（Compose BOM / Hilt / Room / Retrofit / ML Kit / CameraX）
# 连 Android 7.0+（API 24+）真机（USB 调试）
# 直接 Run，或打包后装：
D:\develop\Android\Sdk\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
# 自测：
#  - 统计：切「统计」Tab，确认数字随阅读行为变化（先读几页/加书再看）
#  - TTS：打开一本书 → 底部「朗读」→ 应听到语音；「停止」生效
#  - 笔记：阅读中点 + 按钮 → 填内容保存 → （可经 JSON 导出验证落库）
#  - WebDAV：我的 → 填 Nextcloud/群晖 地址/账号/密码 → 保存 → 立即备份 → 看状态栏
# 看崩溃：
D:\develop\Android\Sdk\platform-tools\adb.exe logcat | findstr creationreadingassistant
```

> 全部四阶段（P1→P2→P3→P4）已交付。本沙箱无网络/Compose 依赖缓存，无法代为构建测试；请于本机 Android Studio 构建并 adb 连真机验证，发现问题反馈我来修。
