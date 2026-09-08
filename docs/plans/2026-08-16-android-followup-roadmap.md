# 原生 Android 后续路线图（2026-09-08 状态同步）

> 状态：**现行全景路线图**。文末“当前优先顺序（2026-09-08）”与
> `docs/handoff/current.md` 是活动结论；下方 P0–P3 保留阶段背景，不得把其中早期“未提交”“待真机”
> 表述覆盖到更新日期更晚的结论。P0-A3 发布链路已收口但从未实际发版；替换净化的详细 seam 仍以
> `plans/replace-rules-render-integration-design.md` 为准。

---

## P0：收口与真机验证（立即，其他一切的前置）

### 0.1 工作区收口提交

当前工作区混有三类未提交改动，需要分层提交（建议顺序）：

1. **reader 引擎 WIP**（分页/文档/MainActivity 等约 20 文件）——由其所有者先收口；
   注意 `android-reeden-mobile` worktree（分支 `codex/android-reeden-mobile`）若为同一
   工作流的并行分支，先明确两边合并顺序，避免 pager/doc 文件二次冲突。
2. **示例书移除的测试侧**（ShelfImporterTest/ShelfViewModelTest/BookViewModelTest
   已清理死引用）——与 1 同属一个语义单元，建议同批提交。
3. **2026-08-16 两批功能改动**（色温/纹理/灵感跳转/删除文案/EPUB 封面/全局搜索正文/
   TTS 接续与拔耳机/方向锁定/进度交互/灵感状态筛选/语速滑条/书架筛选/内容哈希/
   AI 预检/TOC 书签/导出增强/StrictMode）——WIP 提交后再提，保持 diff 可审。

验收：工作区干净；每层提交后 `testDebugUnitTest + lintDebug` 全绿。

### 0.2 真机验证清单（真实设备，遵守 AGENTS.md 约束）

按功能逐项过（`adb devices` 先核对，长测临时开 `stay_on_while_plugged_in` 后恢复）：

| 功能 | 验证点 |
|------|--------|
| TTS 章末接续 | EPUB/MD 末句播完自动翻章续读；末章正常停止；手动停止不受影响 |
| TTS 拔耳机 | 播放中拔有线耳机/断蓝牙自动暂停，不外放 |
| TTS 语速滑条 | 0.5–2.0x 拖动即时生效；拖动不重读 |
| 色温 | 护眼开启时色温滑条有可见暖色变化；强度仍独立生效 |
| 纸张纹理 | 外观开关关闭后阅读器噪点消失、重开恢复 |
| EPUB 封面 | 真实 EPUB 导入后书架显示内嵌封面；用户手动封面不被覆盖 |
| 方向锁定 | 竖屏/横屏锁定生效，退出阅读器恢复，尊重系统旋转锁 |
| 进度交互 | 底栏/进度 Sheet 拖动显示百分比预览；百分比输入精确跳转 |
| 灵感 | 「定位来源」打开来源书；状态筛选正确参与过滤 |
| 书架 | 格式筛选、标签多选（交集语义）、TOC 书签跳转 |
| 全局搜索 | 正文关键词能命中书籍结果 |
| 内容哈希 | 同内容改名文件二次导入被判重；>32MB 文件正常导入（回退弱指纹） |

顺带：StrictMode 已在调试构建启用（penaltyLog），真机跑一轮
`adb logcat -s StrictMode` 记录违规清单（已知一处：`PagedEpubContent` 组合期
`File.lastModified()`），列入下批修复。

### 0.3 gap-audit 真机收尾项（与 0.2 同场完成）

- **A8**：Room 1→9 全链迁移真机复核（模拟器 CI 已覆盖，真机人工过一遍）。
- **A1**：50MB 级「测试 TXT」压力验证（打开/目录/搜索/翻页/TTS/退出重进，无 ANR/OOM）。

---

## P1：首次正式发版（P0 完成后）

1. **配置签名 Secrets**（用户操作，一次性）：按 `docs/release/ANDROID_RELEASE.md` §2.4
   添加 `ANDROID_KEYSTORE_BASE64 / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS /
   ANDROID_KEY_PASSWORD` 四项；keystore 按 §2.3 生成并妥善保管。
2. **首发 tag**：`git tag android-v0.5.0 && git push origin android-v0.5.0`
   （版本号与桌面端 `package.json` 解耦，Android 独立演进）。
3. **流水线实测**：`build-android-release` job 全绿；Release 附件齐全
   （APK + GPL 源码包 + SHA256SUMS-android.txt + 许可证）；apksigner 校验日志正常。
4. **安装与升级验证**：真机覆盖安装（若此前装过 debug 兜底包需先卸载——证书不同）、
   书架/进度/笔记数据不丢；应用内「检查更新」能发现新版本并直达 Release 页。
5. 更新 `CHANGELOG.md` 与发布说明。

> 红线：本地 debug 兜底签名产物严禁外发；Secrets 不落仓库。

---

## P2：质量门禁补齐（与 P1 并行或紧随）

| 项 | 内容 | 验收 |
|----|------|------|
| CI 设备测试 | 评估把 `android-migration-tests` 模拟器 job 扩展到 Compose 关键路径类（分批，控制时长） | 至少 Reader 关键路径类在 CI 执行 |
| A7 剩余 | 同步/WebDAV 两端真机↔桌面冒烟；UI 层重复点击/离线重试场景 | 无静默丢数据；冲突可解释 |
| A9 剩余 | EPUB 异常语料矩阵（无 TOC/坏 ZIP/路径穿越/超大图等）+ 首屏/翻页/内存基线记录 | 异常均进可退出错误态；基线入档 |
| A12 | 无障碍与资源化：约 337 处硬编码中文收口到 `strings.xml`、补 `contentDescription`；2026-09-08 Lint 实测 `0 errors, 4 warnings`（3 条依赖工具链 + `ANDROID_ID`） | 正式对外分发前必须完成 |
| 结构/API 债 | 09-08 已净减 1,767 行静态死结构；后续按 `qa/android-structure-cleanup-2026-09-08.md` 逐个拆巨型 UI 模块、迁移 Compose/Kotlin 弃用 API 和设计 app-scoped 同步设备 ID | 每片保持窄 seam、定向测试 + 全量门禁，不做全仓机械搬家 |
| StrictMode 违规 | 按 0.2 收集的清单清零主线程 I/O | 调试构建 logcat 无 StrictMode 违规 |
| ~~桌面端 A10~~ | **已完成（2026-08-22 复核）**：`splitTxtChapters` 59 用例全过（原 4 失败已在创作工作台开发中修复）；桌面 689 项测试全绿；`ci.yml` desktop job 自 2026-07-30 起含类型检查+`npm test`+`npm run build`+verify，2026-08-20 实跑全绿 | ✅ 已达成 |

---

## P3：功能深化（按性价比排序，P0 后可开始）

1. **替换净化规则接入正文** —— **首期（片 0 双轨 + Provider + 片 1–3 + 能力边界）已于
   2026-08-21 通过完整验收**：2026-08-20 首轮"流式章节 source"因三项回归
   （无目录大 TXT 整本退化、投影缓存无上限、UI 能力判断虚假）未通过；
   2026-08-21 会话采用双轨 + Provider 架构重写片 0：
   - 有界分页：TxtChapterSource.fromStreaming 以 ReadingUnit segment 为 chapterCount
     （≤PlainTextDocument.MAX_WINDOW_CHARS，50MB 无目录≥100 段，偏移连续）
   - 完整作用域：`ReplaceProjectionScopeProvider.scopeForSegment`，
     用 TxtFileIndex 元数据在读取整章前判定 Exact(≤256K) / UnsupportedTooLarge(>256K)
     / Incomplete；超限不读整章；同一逻辑章内 segments 共享同一投影；
   - ReplacedChapterSource：LRU 3-chapter 有界容量、锁外投影、规则 key 变化不复用；
   - UI：`PagedReplacementAvailability` 由集成层输出，不再靠 isTxt+pagerEngineOn 反推。
   JVM 定向 suites=5/tests=58 0 failures；全量 153 suites / 1432 tests 0 failures；
   lintDebug/assembleDebug/compileDebugAndroidTestKotlin 全绿；
   真机 serial=c49ac6cf：安装/冷启动/无 FATAL-ANR 通过；
   ReaderRulesSheetTest 6/6 PASS（numtests=6 OK (6 tests)）。
   剩余开放项：legacy/滚动实际正文投影、EPUB/Markdown 结构保真设计、
   并发线程下的重复投影去重收紧、授权导入中性测试 TXT 后的带书真机矩阵
   （正文替换/规则启停/重分页/搜索/高亮/选区/TTS/超限提示一次性/坐标持久化）。
   当前不支持路径已隐藏替换入口并解释保留原文；禁止按 ReadingUnit 或 overlap 近似替换。
2. **阅读目标 + streak 打卡 + 提醒通知** —— **四片全部收口（片 0–1 于 2026-08-20，片 2–3 于
   2026-08-22）**：本地会话状态机、口径统一、`GoalStore` + `GoalSubPage` 目标设置、Stats 进度环、
   Home 副标、WorkManager 每日提醒（`reading_goal` 渠道 + 权限 helper）全部落地；判定纯函数化，
   新增 JVM 23 项，全量 1455 项 0 失败。剩余：提醒的真机触发/重启恢复验收（通知点击 V1 仅拉起
   应用，直达阅读器 deep-link 留作增强）。
3. **TOC 已读标记持久化 + 分类/标签/书单手动排序** —— **已于 2026-08-20 收口**：
   schema v10、`chapter_reads`、迁移、到达章写入、删书/手动清理、TOC 弱化与计数，
   以及分类/标签/书单独立排序模式均已通过 JVM、Lint、构建和真实手机定向测试；
   TXT 已读和书单内书籍排序仍明确不在本期范围。
4. **backlog 候选池**（`plans/legado-feature-backlog.md`，候选非承诺）：
   热力图增强、全书页码/剩余页数（等 A9 排版性能基线后评估）、简繁转换、
   主题三件套、点击区域动作自定义、AI 批注层、共享元素转场、手柄/滚轮。

---

## 明确不做 / 悬置

- **TTS 选引擎入口**：无官方直达 Intent，脆弱 hack 不做（保留现有"去系统设置"提示文案）。
- **书源引擎 / RSS / 云端 TTS / MOBI-PDF**：产品边界外（backlog 第五节）。
- **替换规则按 ReadingUnit 拆开投影**：seam 契约禁止。

---

## 当前优先顺序（2026-09-08）

1. WorkBuddy 已完成 T8 的只读文件分类；以其清理前 273 项快照为参考，对当前工作树重新求差并加入
   09-08 死代码清理切片。提交、暂存、推送和诊断工件清理仍需用户授权。
2. P3.1 仅剩 legacy/滚动路径片 4：先写投影映射 + 持久化坐标测试，再逐个接入消费点；禁止 display
   坐标写数据库。分页 TXT 和 EPUB 的净化闭环以 `docs/handoff/current.md` 第 28–29 节为准。
3. Markdown 先完成源↔渲染文本映射、结构保真与测试契约，之后才评估接入替换净化；契约完成前保持入口隐藏。
4. 产品 UX 继续覆盖首页继续阅读、阅读历史、全局搜索及手机/平板/横屏信息架构。
5. P3.2 目标进度环与每日提醒已实施，尚需 WorkBuddy 采集真机通知触发和重启恢复证据。
6. 修复 MIUI 自动安装脚本超时；直接 adb 安装不可替代脚本成功。随后按互斥文件面推进 A7、A9、A12，
   首发仍依赖用户配置签名 Secrets、授权 tag/push 和升级验证。

最新接手入口见 `docs/handoff/current.md`；本路线图提供全景，不替代交接页的活动任务顺序。

维护规则：完成一项后回填本文件状态；被新计划取代时移入历史参考。

