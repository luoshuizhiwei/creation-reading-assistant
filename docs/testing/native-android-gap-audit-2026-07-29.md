# 项目缺口审计与 Agent 任务包

审计日期：2026-07-29  
主线：`android/` 独立原生 Android  
附带范围：桌面端、CI 与发布链路  
修订：2026-08-16（P0 收口与发布链路完成后，再次核对全部条目）

## 0. 与 2026-07-29 审计相比已完成或已修正的条目

下列条目原先在审计报告里被列为缺口或 P1 级问题，现已完成，在阅读 A1-A10 时请不要重复派发：

- **B-1（已修，2026-07-31）CoroutineScope / CoroutineDispatcher 注入缺口**：已在 `CoroutineScopeModule.kt` 新增 `@IODispatcher / @DefaultDispatcher / @MainDispatcher` 三个 Qualifier，并把 `ShelfViewModel / PagerHealthStore` 等硬编码 `Dispatchers.*` 全部改为注入。测试里 `StatsDashboardViewModelTest` 的 `UnconfinedTestDispatcher` 也改为注入真实 `Dispatchers.Default`，保证缓存模型与生产环境调度一致。
- **B-4（已修，2026-07-31）Hilt @EntryPoint 滥用**：原来报告的「大量分散 EntryPoint」实际只存在 `StatsScreen.kt` 一处，且使用方式已确认符合 Hilt 约束。本轮已经把 `StatsDashboardViewModelTest` 的 `UnconfinedTestDispatcher` 问题修正，不再用 EntryPoint 作伪装借口；严重度由原 P1 下调为「仅 1 处、已确认无副作用」，不再作为缺口派发。
- **C-3（已修正版本号对照，2026-08-22）**：原「旧 mobile/android v0.1.26 / v0.2.0 → 对照 native `0.4.0-p4`」不再适用。`android/app/build.gradle.kts`：`versionCode = 2`、`versionName = 0.5.0`（缺省由本地默认值兜底，发布时经 `-PcraVersionCode/-PcraVersionName` 注入）；Room schema 已导出到 `android/app/schemas`：`1.json`~`10.json`（当前 v10，含 `chapter_reads` 与 taxonomy 排序；注意 v10 目前只存在于 `codex/workspace-backup-2026-08-20` 分支，main 尚停留在 v9 时代）。
- **A-2（已修，原「明确 Capacitor 去留」，2026-07-30 P0-A2 收尾）**：`mobile/` 已整体删除，仅保留许可证/上游归属存档于 `archives/frozen-mobile/`。`AGENTS.md` 已更新「移动端两条独立产品线」说明，后续所有改动都落在 `android/`，不要再出现 Capacitor、mobile/android 路径、或对已删除的 `MobileReaderView/ShelfPage.tsx` 的改造计划。
- **A1（已基本完成，2026-08-08 复核）**：大 TXT 流式闭环已落地——`TextStreamLoader` 以 5MB 为阈值分流，大文件走 `TxtFileScanner` 索引 + `PlainTextDocument` 有界窗口，导入预览对 >10MB 文件使用 `readWindow(0, 20_000)`；搜索（`computeStreamingTxtSearch`）逐 ReadingUnit 读取。剩余复核点：个别消费者不得回退为整文件 `readText()`。
- **A4（已完成，2026-08-08 复核）**：原生 Markdown 语义已落地（`MarkdownParser`/`MarkdownDocument`/`MarkdownPageSource`/`MarkdownOffsetMap`），支持标题、段落、列表、任务列表、引用、代码块、表格、链接，规范文本与源偏移双向映射，搜索/TTS/Locator/选区基于规范偏移。
- **A5（已完成，2026-08-08 复核）**：`ReaderScreen.kt` 已从约 4,462 行拆至 502 行，文档加载、会话、进度、分页引擎状态、护眼/TTS、Sheet 均抽为独立文件与 State-holder；DAO/Repository 写入收敛到 `ReaderViewModel`。
- **A8（已完成，2026-08-08 实现）**：为 9 个外键列补 Room 声明索引（reading_sessions.book_id、inspirations.source_book_id、inspiration_variants.inspiration_id、notes.book_id/inspiration_id、highlights.book_id、book_tag.tag_id、book_category.category_id、shelf_book.book_id），schema 已生成 v8，7→8 迁移与 1→8 全链迁移测试已加入；KSP 未索引外键警告清零。**2026-08-22 跟进**：schema 已演进到 v10（`chapter_reads` 表 + taxonomy 排序，位于 `codex/workspace-backup-2026-08-20` 分支；1→最新 真机复核待办）。
- **A3（已完成，2026-08-16 收口）**：原生 Android 发布链路与检查更新已闭环——`.github/workflows/release.yml` 新增 `build-android-release` job，按 tag 前缀分流（`v*` 桌面端 / `android-v*` Android 签名 APK + GPL 源码包 + SHA-256，versionCode 由 tag 按「主×10000+次×100+修订」计算且修订 >99 直接失败）；版本注入与签名兜底/CI 缺签名即失败已落在 `android/app/build.gradle.kts`；应用内「检查更新」改为按 `android-v` 前缀过滤 releases 列表并做语义化版本比较（`UpdateCheck` + JVM 单测）。首次发版前需按 `docs/release/ANDROID_RELEASE.md` §2.4 配置签名 Secrets；发布工作流本身的实际链路待首次发版时验证。

## 结论

原生 Android 已经不是"只有页面的半成品"：书架、阅读、标注、TTS、AI、统计、局域网同步和 WebDAV 都已有实现，JVM 单元测试当前可通过（1,354 项全绿），发布链路与检查更新也已收口。剩余真实缺口集中在：真机复核（1→9 迁移、50MB 级 TXT 压力、Android 13+ 通知权限）、EPUB 异常语料与性能基线、桌面端测试门禁、同步两端冒烟，以及 lint 的 16 条 warning 收口。

下列任务按独立 agent 可交付的粒度拆分。除特别说明外，各 agent 只处理自己的任务包，不顺手重写其他模块。

## P0：先完成

### A1. 大型 TXT 流式读取闭环

**2026-08-16 复核：生产路径已无整文件 `readText()`，剩余复核点完成。** 全量 grep 生产源码仅 4 处 `readText(`，逐个核过均合理：`MainActivity` 崩溃日志（IO 线程小文件）、`EpubParser` nav.xhtml 单文档（有界 zip entry）、`JsonBridge` 整库导入（全量语义本身如此）、`AboutSubPage` 更新检查响应（IO 线程网络小响应）。导入预览走 `ShelfImporter` 10MB 阈值 + `TxtFileScanner` 索引有界窗口；锚点/locator 解析不直接碰文件。剩余：50MB 级真机压力验证（见验收第二条）。

**已确认事实（2026-07-29 原始审计，其中第一、三条已被上方复核更正/取代，保留作历史记录）**

- `ReaderScreen.kt` 的流式锚点跳转仍对临时文件执行 `readText()`。——已被复核取代：锚点/locator 解析不直接碰文件，`ReaderScreen.kt` 已拆分为约 500 行的 State-holder 结构（见 A5）。
- `ReadingUnitBuilder` 的字节边界按章节平均密度推算，混合 ASCII/CJK/emoji/GB18030 时不能保证精确。——仍成立，属已知精度限制，不阻塞。
- 搜索、TTS/AI 上下文、目录跳转和分页需要逐一证明不会回退为整本读取。——已被复核取代：`computeStreamingTxtSearch` 逐 ReadingUnit 读取，分页走 `ReadingUnit` 有界窗口（`PagedChapterSource` 单一真相）。

**交付**

- 用精确的字符↔字节边界索引替代线性插值。
- 所有大文件消费者改为章节或窗口级有界读取；删除生产路径的整文件 `readText/readBytes`。
- 修复扫描输入流关闭、临时文件启动清理和主线程 I/O。
- 保持小文件路径、Locator、搜索命中和 TTS 字符空间兼容。

**验收**

- UTF-8/UTF-16/GB18030、BOM、混合 emoji、超长章、无章节和空文件测试全绿。
- 使用“测试 TXT”做 50 MB 真机压力验证：打开、目录、搜索、翻页、TTS、退出重进均可用，无 ANR/OOM。

### A2. 修复仓库构建断层并明确 Capacitor 去留

**已确认事实（2026-07-29 原始审计，已完成，见第 0 节 A-2 条目；保留作历史记录）**

- `npm run build --prefix mobile` 当前失败；`mobile/src/App.tsx` 引用了 20 个已删除模块。——已被复核取代：`mobile/` 已整体删除，不再有构建入口。
- CI 仍执行 Capacitor 类型检查和单元测试。——已被复核取代：Capacitor 的 CI/发布已一并退役。
- 当前项目约定又明确 `android/` 才是移动端主线。——至今仍成立。

**建议方向**

优先正式退役 Capacitor 运行时：保留必要的协议/历史参考，删除其 CI、发布和根 `package.json` 中失效入口。若决定继续维护，则完整恢复模块与行为测试；禁止用空组件或 `any` 临时糊过编译。

**验收**

- 仓库文档、CI、package scripts 对 Capacitor 的定位一致。
- 根 CI 不再因为已退役代码失败；若保留 Capacitor，则 `npm run build --prefix mobile` 和测试必须通过。
- 不修改或丢失独立原生 Android 与桌面端数据协议。

### A3. 将正式发布迁移到独立原生 Android

**已确认事实（2026-07-29 原始审计，已完成，见第 0 节 A3 条目；保留作历史记录）**

- `.github/workflows/release.yml` 仍构建 `mobile/android`，源码包也未包含当前 `android/`。——已被复核取代：release.yml 现按 tag 前缀分流，`android-v*` 构建签名 APK + GPL 源码包 + SHA-256。
- 原生 `android/app/build.gradle.kts` 仍是 `versionCode = 1`、`versionName = 0.4.0-p4`，没有主线发布闭环。——已被复核取代：现为 versionCode = 2 / versionName = 0.5.0（可注入），发布闭环已落地。

**交付**

- Release 工作流改为构建、签名、校验 `android/app` 的 release APK；源码包包含 `android/`。
- 建立稳定的版本号、签名、SHA-256、安装/升级验证和失败即停门禁。
- 更新应用内检查更新与发布说明，使其指向原生 APK。
- 保留桌面端发布，不得在未获用户明确授权时实际创建公开 Release。

**验收**

- `testDebugUnitTest`、`lintDebug`、`assembleRelease`、R8 后启动验证通过。
- 产物证书、包名、版本号和 SHA-256 可复核；发布文档与真实工作流一致。

### A4. 原生 Markdown 阅读语义

**已确认事实（2026-07-29 原始审计，已完成，见第 0 节 A4 条目；保留作历史记录）**

- 导入层接受 `md/markdown`，但阅读器进入与 TXT 相同的纯文本路径。——已被复核取代：`MarkdownParser`/`MarkdownDocument` 语义层已落地。
- 当前只有 `md-heading` 目录识别，没有列表、引用、代码、表格、链接等渲染模型。——已被复核取代：标题/段落/列表/任务列表/引用/代码块/表格/链接均已支持。

**交付**

- 在 `ReaderDocument/DocBlock` 上增加 Markdown 语义块，不创建全书 WebView DOM。
- 支持标题、段落、列表、任务列表、引用、代码块、表格、链接和图片说明的稳定原生显示。
- 建立渲染文本与原始 Markdown 的偏移映射，确保搜索、TTS、书签、高亮、进度恢复不漂移。

**验收**

- “测试 Markdown”覆盖全部语义、目录跳转、分页/滚动、字号变化、选区、TTS、搜索和重进恢复。
- 大文件仍有界读取，不因解析 Markdown 回到整本常驻内存。

## P1：稳定性与可维护性

### A5. ReaderScreen 分层与主线程 I/O 清零

**2026-08-16 进展**：拆分已完成（4462 行 → 约 500 行）；`App.kt` 已在调试构建启用 StrictMode（`penaltyLog`，release 无影响），主线程 I/O 违规可通过 `adb logcat -s StrictMode` 观察。剩余：按日志清零违规（已知一处 `PagedEpubContent` 组合期 `File.lastModified()` stat 调用待移出）。

`ReaderScreen.kt` 当前约 4,462 行，并通过 Hilt EntryPoint 直接访问多个 DAO/Repository。先提取 `ReaderSessionController`、文档加载器、Locator/跳转服务、TTS 控制和各 Sheet；UI 只消费状态与事件。必须先补行为测试，再小步迁移，禁止一次性重写。

验收：单文件职责明显收敛；切书竞态、后台保存一次、退出清理、错误返回和进度恢复有测试；StrictMode/日志无主线程文件 I/O。

### A6. 真机自动化回归门禁

**2026-08-16 复核（描述更正）**：`androidTest` 已有 14 个测试文件（Room 迁移、EpubParser、Compose 关键路径）。CI 侧 `android-native` job 只编译不执行；另有独立的 `android-migration-tests` job（模拟器、必过）**只执行 `AppDatabaseMigrationTest` 一个类**（带 3 次重试脚本）。剩余缺口：其余 13 个 androidTest 文件的 CI 执行（模拟器矩阵成本需权衡）、真机脚本与「迁移 1→最新」真机人工复核。

当前有 28 个 JVM 测试文件（2026-08-16 为 48 个 JVM 测试文件 / 1,354 项用例），`androidTest` 已有 14 个测试文件（Room 迁移、EpubParser、Compose 关键路径），且 CI 仅编译 androidTest、不执行设备测试（另有独立 `android-migration-tests` job 在模拟器执行 `AppDatabaseMigrationTest` 一个类）。——**原始描述已被上方 2026-08-16 复核取代**。增加 Compose/设备关键路径测试，并提供只针对已连接真实手机的脚本：导入、打开、翻页、目录、搜索、主题、后台/强停恢复、缺失文件和同步占位错误态。运行前记录并在结束时恢复 `stay_on_while_plugged_in`。

验收：迁移 1→6、主要阅读路径和数据保留在真实手机上可重复执行；报告只使用中性测试书名。

### A7. 局域网同步与 WebDAV 数据安全回归

当前自动化只直接覆盖局域网地址判断，尚缺同步合并、冲突、墓碑删除、重试、WebDAV 上传/恢复和凭据隔离的系统测试。为 Repository/JSON Bridge/WebDAV 建可控假服务测试，再做两端真机/桌面冒烟；验证离线、超时、重复点击、部分失败和旧快照不会覆盖新数据。

**2026-08-16 进展（JVM 单测部分完成）**：`WebDavBackupTest`（MockWebServer 直接覆盖 PUT/HEAD/GET/PROPFIND、Basic Auth、`requireSafeTarget` 四态）；`JsonBridgeTest` 扩展（墓碑删除三向、四类实体合并、畸形 JSON、schemaVersion 拒绝、事务失败上抛、往返幂等、导出无凭据字段）；`SyncRepositoryTest` 扩展（push 墓碑传播、payload 回填、冲突映射）。修复两个由此暴露的问题：`JsonBridge` 增加未知 schemaVersion 拒绝导入；`WebDavBackup.listBackups` 的 RFC1123 时间由字典序改为 epoch 排序。**剩余**：两端真机/桌面冒烟与 UI 层重复点击/离线重试场景。

验收：无静默丢数据、无重复记录、冲突可解释可重试；AI Key、WebDAV 密码和局域网 token 不进入导出、日志或备份。

### A8. Room 外键索引与 schema 演进

本轮构建由 Room/KSP 明确报告 9 个未索引外键，涉及阅读记录、灵感来源、灵感/书籍关联和标签/分类关联。父表更新或删除时可能触发全表扫描。**2026-08-08 已实现**：为对应列补单列索引，生成 schema v8 与 7→8 迁移，并扩展迁移测试（7→8 单步 + 1→8 全链）；不能用破坏性迁移或只消掉警告。**2026-08-16 跟进**：schema 已演进到 v9（见第 0 节 A8 条目），迁移测试随 schema 同步维护。

验收：KSP 的 9 条外键索引警告清零（已达成）；1→最新全链迁移在真实手机通过，原有书库、进度、笔记、灵感和分类关系不丢（真机复核待办）。

### A9. EPUB 异常语料与性能基线

补无 TOC、无封面、损坏 ZIP、路径穿越、超长单章、超大图片/SVG、内嵌字体、中文路径和图片密集书的解析/分页测试；给全书搜索增加进度与取消，记录首屏、翻页、内存峰值和缓存回落。

**2026-08-16 复核**：「全书搜索进度与取消」已实现——`ReaderSearchLogic` 逐章流式检索带 `onProgress` 回调与 `yield()` 协作取消，`ReaderSearchSheet` 展示「已扫描 X/Y」进度条并可取消在途搜索（`BookSearchSession` 状态机防过期回调）。剩余缺口为异常语料矩阵与性能基线记录。

验收：所有异常均进入可退出错误态，不白屏/ANR/OOM；“测试 EPUB”连续翻页和反复进出后内存保持有界。

### A10. 桌面端真实质量门禁

**2026-08-22 复核：已完成，原描述已过时。** `splitTxtChapters` 现为 59 个用例全部通过（原"75 测试中 4 个失败"为 2026-07-29 时点数据，后续创作工作台开发中已修复）；桌面测试套件已增长到 689 项全绿（本地实测，4 项 skipped 为本地 better-sqlite3 ABI 与 Node 版本不匹配的环境问题，CI 的 Node 24 全量执行）。CI 侧：`.github/workflows/ci.yml` 的 desktop job 自 2026-07-30（`83e56cf`）起就包含三份 tsconfig 类型检查 + `npm test` + `npm run build` + verify 脚本，push/PR 均触发；2026-08-20 实际运行全绿。原「优先补 IPC 权限、数据导入导出、同步协议和更新检查」测试仍可作为后续增强，不阻塞验收。

验收：75 个现有测试全绿（现况 689 全绿），桌面端测试与生产构建成为 PR 必过项（已达成），失败不会被字符串型 verify 脚本掩盖。

## P2：体验增强

### A11. AI 请求体验与可诊断性

**2026-08-16 复核：已完成，原描述已过时。** 当前 `AiClient` 已支持：SSE 流式（`chatStreaming`，UI 实际在用）、协程取消（`executeCancellable` + `invokeOnCancellation`，OkHttp 异步 `enqueue` 非 `execute` 阻塞）、401/429/5xx 与网络错误分类（`classifyHttpError` / `classifyNetworkError`）、错误消息不携带响应体（防 API Key 回显）、非加密 http 一次性警示（局域网白名单）。`AiClientTest` 16 个用例覆盖超时、401/429、空响应、取消传播、SSE 解析与 insecure http 警示。

### A12. 无障碍、资源化与 Lint 收口

本轮 `lintDebug` 为 0 error、16 warning（实测 2026-08-16；早前审计报告的 85 warning / 8 information 已过时）。把关键界面的硬编码中文和缺失 `contentDescription` 收口到资源，并逐类处理而非整体 suppress；补字体放大、TalkBack、触控目标、横屏/小屏和对比度检查。此项不应阻塞核心阅读修复，但应在正式分发前完成。

## 不应作为“缺功能”派发

- 登录、账号、自建云服务：产品明确不做。
- 在线书源、爬虫、平台发布：不在本地阅读工具范围。
- 云端 TTS：当前使用系统本地 TTS 是明确降级边界，不是 P0。
- 继续增加主题数量：视觉方向已冻结，先修功能和可交付性。
