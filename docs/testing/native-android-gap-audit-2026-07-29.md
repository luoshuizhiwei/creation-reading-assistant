# 项目缺口审计与 Agent 任务包

审计日期：2026-07-29  
主线：`android/` 独立原生 Android  
附带范围：桌面端、CI 与发布链路  
修订：2026-07-31（清理过时文档后，更新以下与实际代码不一致的条目）

## 0. 与 2026-07-29 审计相比已完成或已修正的条目

下列条目原先在审计报告里被列为缺口或 P1 级问题，现已完成，在阅读 A1-A10 时请不要重复派发：

- **B-1（已修，2026-07-31）CoroutineScope / CoroutineDispatcher 注入缺口**：已在 `CoroutineScopeModule.kt` 新增 `@IODispatcher / @DefaultDispatcher / @MainDispatcher` 三个 Qualifier，并把 `ShelfViewModel / PagerHealthStore` 等硬编码 `Dispatchers.*` 全部改为注入。测试里 `StatsDashboardViewModelTest` 的 `UnconfinedTestDispatcher` 也改为注入真实 `Dispatchers.Default`，保证缓存模型与生产环境调度一致。
- **B-4（已修，2026-07-31）Hilt @EntryPoint 滥用**：原来报告的「大量分散 EntryPoint」实际只存在 `StatsScreen.kt` 一处，且使用方式已确认符合 Hilt 约束。本轮已经把 `StatsDashboardViewModelTest` 的 `UnconfinedTestDispatcher` 问题修正，不再用 EntryPoint 作伪装借口；严重度由原 P1 下调为「仅 1 处、已确认无副作用」，不再作为缺口派发。
- **C-3（已修正版本号对照，2026-07-31）**：原「旧 mobile/android v0.1.26 / v0.2.0 → 对照 native `0.4.0-p4`」不再适用。当前 `android/app/build.gradle.kts`：`versionCode = 1`、`versionName = 0.4.0-p4`；Room schema 已导出到 `android/app/schemas`：`1.json`~`6.json`（当前 v6，P0-A8 补外键索引时会生成 v7 和 6→7 迁移）。
- **A-2（已修，原「明确 Capacitor 去留」，2026-07-30 P0-A2 收尾）**：`mobile/` 已整体删除，仅保留许可证/上游归属存档于 `archives/frozen-mobile/`。`AGENTS.md` 已更新「移动端两条独立产品线」说明，后续所有改动都落在 `android/`，不要再出现 Capacitor、mobile/android 路径、或对已删除的 `MobileReaderView/ShelfPage.tsx` 的改造计划。

## 结论

原生 Android 已经不是"只有页面的半成品"：书架、阅读、标注、TTS、AI、统计、局域网同步和 WebDAV 都已有实现，JVM 单元测试当前可通过。真正阻碍继续使用和交付的，是大文件边界、Markdown 语义、仓库/发布断层、设备回归盲区和超大页面耦合。

下列任务按独立 agent 可交付的粒度拆分。除特别说明外，各 agent 只处理自己的任务包，不顺手重写其他模块。

## P0：先完成

### A1. 大型 TXT 流式读取闭环

**已确认事实**

- `ReaderScreen.kt` 的流式锚点跳转仍对临时文件执行 `readText()`。
- `ReadingUnitBuilder` 的字节边界按章节平均密度推算，混合 ASCII/CJK/emoji/GB18030 时不能保证精确。
- 搜索、TTS/AI 上下文、目录跳转和分页需要逐一证明不会回退为整本读取。

**交付**

- 用精确的字符↔字节边界索引替代线性插值。
- 所有大文件消费者改为章节或窗口级有界读取；删除生产路径的整文件 `readText/readBytes`。
- 修复扫描输入流关闭、临时文件启动清理和主线程 I/O。
- 保持小文件路径、Locator、搜索命中和 TTS 字符空间兼容。

**验收**

- UTF-8/UTF-16/GB18030、BOM、混合 emoji、超长章、无章节和空文件测试全绿。
- 使用“测试 TXT”做 50 MB 真机压力验证：打开、目录、搜索、翻页、TTS、退出重进均可用，无 ANR/OOM。

### A2. 修复仓库构建断层并明确 Capacitor 去留

**已确认事实**

- `npm run build --prefix mobile` 当前失败；`mobile/src/App.tsx` 引用了 20 个已删除模块。
- CI 仍执行 Capacitor 类型检查和单元测试。
- 当前项目约定又明确 `android/` 才是移动端主线。

**建议方向**

优先正式退役 Capacitor 运行时：保留必要的协议/历史参考，删除其 CI、发布和根 `package.json` 中失效入口。若决定继续维护，则完整恢复模块与行为测试；禁止用空组件或 `any` 临时糊过编译。

**验收**

- 仓库文档、CI、package scripts 对 Capacitor 的定位一致。
- 根 CI 不再因为已退役代码失败；若保留 Capacitor，则 `npm run build --prefix mobile` 和测试必须通过。
- 不修改或丢失独立原生 Android 与桌面端数据协议。

### A3. 将正式发布迁移到独立原生 Android

**已确认事实**

- `.github/workflows/release.yml` 仍构建 `mobile/android`，源码包也未包含当前 `android/`。
- 原生 `android/app/build.gradle.kts` 仍是 `versionCode = 1`、`versionName = 0.4.0-p4`，没有主线发布闭环。

**交付**

- Release 工作流改为构建、签名、校验 `android/app` 的 release APK；源码包包含 `android/`。
- 建立稳定的版本号、签名、SHA-256、安装/升级验证和失败即停门禁。
- 更新应用内检查更新与发布说明，使其指向原生 APK。
- 保留桌面端发布，不得在未获用户明确授权时实际创建公开 Release。

**验收**

- `testDebugUnitTest`、`lintDebug`、`assembleRelease`、R8 后启动验证通过。
- 产物证书、包名、版本号和 SHA-256 可复核；发布文档与真实工作流一致。

### A4. 原生 Markdown 阅读语义

**已确认事实**

- 导入层接受 `md/markdown`，但阅读器进入与 TXT 相同的纯文本路径。
- 当前只有 `md-heading` 目录识别，没有列表、引用、代码、表格、链接等渲染模型。

**交付**

- 在 `ReaderDocument/DocBlock` 上增加 Markdown 语义块，不创建全书 WebView DOM。
- 支持标题、段落、列表、任务列表、引用、代码块、表格、链接和图片说明的稳定原生显示。
- 建立渲染文本与原始 Markdown 的偏移映射，确保搜索、TTS、书签、高亮、进度恢复不漂移。

**验收**

- “测试 Markdown”覆盖全部语义、目录跳转、分页/滚动、字号变化、选区、TTS、搜索和重进恢复。
- 大文件仍有界读取，不因解析 Markdown 回到整本常驻内存。

## P1：稳定性与可维护性

### A5. ReaderScreen 分层与主线程 I/O 清零

`ReaderScreen.kt` 当前约 4,462 行，并通过 Hilt EntryPoint 直接访问多个 DAO/Repository。先提取 `ReaderSessionController`、文档加载器、Locator/跳转服务、TTS 控制和各 Sheet；UI 只消费状态与事件。必须先补行为测试，再小步迁移，禁止一次性重写。

验收：单文件职责明显收敛；切书竞态、后台保存一次、退出清理、错误返回和进度恢复有测试；StrictMode/日志无主线程文件 I/O。

### A6. 真机自动化回归门禁

当前有 28 个 JVM 测试文件，但 `androidTest` 只有 Room 迁移测试，且 CI 仅编译、不执行设备测试。增加 Compose/设备关键路径测试，并提供只针对已连接真实手机的脚本：导入、打开、翻页、目录、搜索、主题、后台/强停恢复、缺失文件和同步占位错误态。运行前记录并在结束时恢复 `stay_on_while_plugged_in`。

验收：迁移 1→6、主要阅读路径和数据保留在真实手机上可重复执行；报告只使用中性测试书名。

### A7. 局域网同步与 WebDAV 数据安全回归

当前自动化只直接覆盖局域网地址判断，尚缺同步合并、冲突、墓碑删除、重试、WebDAV 上传/恢复和凭据隔离的系统测试。为 Repository/JSON Bridge/WebDAV 建可控假服务测试，再做两端真机/桌面冒烟；验证离线、超时、重复点击、部分失败和旧快照不会覆盖新数据。

验收：无静默丢数据、无重复记录、冲突可解释可重试；AI Key、WebDAV 密码和局域网 token 不进入导出、日志或备份。

### A8. Room 外键索引与 schema v7

本轮构建由 Room/KSP 明确报告 9 个未索引外键，涉及阅读记录、灵感来源、灵感/书籍关联和标签/分类关联。父表更新或删除时可能触发全表扫描。为对应列补联合/单列索引，生成 schema v7 和 6→7 迁移，并扩展迁移测试；不能用破坏性迁移或只消掉警告。

验收：KSP 的 9 条外键索引警告清零；1→7 全链迁移在真实手机通过，原有书库、进度、笔记、灵感和分类关系不丢。

### A9. EPUB 异常语料与性能基线

补无 TOC、无封面、损坏 ZIP、路径穿越、超长单章、超大图片/SVG、内嵌字体、中文路径和图片密集书的解析/分页测试；给全书搜索增加进度与取消，记录首屏、翻页、内存峰值和缓存回落。

验收：所有异常均进入可退出错误态，不白屏/ANR/OOM；“测试 EPUB”连续翻页和反复进出后内存保持有界。

### A10. 桌面端真实质量门禁

当前 CI 只对桌面端做 TypeScript 类型检查，没有执行根目录 `npm test` 或完整 `npm run build`。本轮实跑确认生产构建成功，但 75 个测试中 4 个 `splitTxtChapters` 用例失败（卷标题、前言空行和混合章节规则会多切章节）。先修算法或纠正有证据错误的断言，再把桌面单测和 Electron 构建加入 CI，并优先补 IPC 权限、数据导入导出、同步协议和更新检查。

验收：75 个现有测试全绿，桌面端测试与生产构建成为 PR 必过项，失败不会被字符串型 verify 脚本掩盖。

## P2：体验增强

### A11. AI 请求体验与可诊断性

当前 AI 客户端固定 `stream = false`。增加取消、流式显示、统一错误分类和敏感信息脱敏；关闭 Sheet/切书后不得回写旧结果。先覆盖超时、401/429/5xx、空响应和取消测试。

### A12. 无障碍、资源化与 Lint 收口

本轮 `lintDebug` 为 0 error、85 warning、8 information。把关键界面的硬编码中文和缺失 `contentDescription` 收口到资源，并逐类处理而非整体 suppress；补字体放大、TalkBack、触控目标、横屏/小屏和对比度检查。此项不应阻塞核心阅读修复，但应在正式分发前完成。

## 不应作为“缺功能”派发

- 登录、账号、自建云服务：产品明确不做。
- 在线书源、爬虫、平台发布：不在本地阅读工具范围。
- 云端 TTS：当前使用系统本地 TTS 是明确降级边界，不是 P0。
- 继续增加主题数量：视觉方向已冻结，先修功能和可交付性。
