# Android 多 Agent 执行入口

日期：2026-09-09。状态：需求已授权，第一轮待开发；当前提交是开发检查点，不是发布版本。

## 开工方式

- 主仓库/Codex集成：`D:/develop/Code/Codex/creation-reading-assistant`。
- Trae独立工作树：`D:/develop/Code/Codex/cra-android-trae-r1`，分支 `codex/android-trae-r1`。
- Qoder独立工作树：`D:/develop/Code/Codex/cra-android-qoder-r1`，分支 `codex/android-qoder-r1`。
- 两工作树从本执行包提交创建，启动时记录 `git rev-parse HEAD` 和 `git status --short`；如果目录/分支不符先报告，不得在主仓库凑合施工。
- 用户把 TRAE.md、QODER.md 分别交给对应工具；CODEX.md 在主仓库执行。本文不是已发送消息或已启动外部 agent 的证明。
- 每人先完整阅读本文件与 COMMON.md，只执行本轮文件。完成后在自己的工作树提交报告（报告文件，不是 Git commit），由 Codex 审核和集成；不自行开启下一轮。
- WORKBUDDY.md 在集成构建准备好、明确设备时段后执行。验收独立、只验不修。

## 已授权范围与后续轮次

以下全部保留在实施计划中；后续轮次须由 Codex 在上一轮集成后重新冻结文件所有权。

| 轮次 | Trae | Qoder | Codex | 退出条件 |
|---|---|---|---|---|
| R1 | N1统一阅读笔记：展示/筛选/定位/编辑/导出/删除撤销 | D1书籍删除与撤销完整性 | R1滚动TXT替换、阅读恢复及既有行尾排版收口；共享接口集成 | WorkBuddy分项验收，失败修复，不以单测代替正文证据 |
| R2 | J1返回阅读处、跳转历史、临时查阅界面 | S1正文索引与全局搜索：覆盖状态、上下文、命中定位、错误反馈 | 统一source定位与导航入口；收口D2正文移除/重新关联 | 搜到内容可精确到达并返回；阅读进度语义一致 |
| R3 | P1全局/本书设置、预设、恢复默认、跨窗口表现 | X1选区动作配置、搜索提供方、首选词典、内置查询与StarDict离线词典 | 统一设置持久化/迁移及阅读器接线 | 配置范围正确，离线查询可用，外部往返位置不变 |
| R4 | E1普通纠错和高级规则界面、前后对照及应用反馈 | E2单处纠错/本书替换/全局规则的可撤销记录与执行 | 各格式安全映射、规则失效缓存、搜索/TTS/标注一致性 | 原文保留；显示与所有正文消费者一致 |
| R5 | I1素材回顾、待整理/已整理/已采用、采用去向 | I2摘录来源关联、多摘录素材卡；L1书架保存筛选条件 | 缺失书籍保留素材、导出/同步兼容集成 | 摘录→整理→采用→来源定位全链通过 |
| R6 | 无障碍、资源化、手机/平板/横屏 | 同步/WebDAV、索引与EPUB异常/大文件质量 | 安装脚本、通知证据协调、签名/升级与文档收口 | WorkBuddy整体验收；用户提供签名配置，最终才推送 |

R2以后表格是负责人方向，不能当作文件写入授权；每轮先确认实际代码和共享seam，再给独占路径。
首页/历史/灵感/统计等既有UI检查点在对应轮次一起回归，不重复造轮子。

## 统一需求契约

1. N1：高亮、批注、书签统一查询与操作；灵感独立但可关联。章节按真实顺序。没有locator的历史记录仍可管理，明确不能精确定位。
2. D1：区分搁置、移除正文、删除全部资料；撤销恢复本次影响，不复活此前删除项，不覆盖删除后合法新改动。D2重新关联必须校验内容，文件不同不得强套偏移。
3. J1：正常阅读位置与临时查阅位置分开；返回精确source位置；跨章、模式切换和重启语义明确；普通翻页不堆积导航历史。
4. S1：TXT/EPUB/Markdown分别记录索引覆盖，部分索引不是全文完成；可取消、续建、重试；命中有书籍/章节/上下文/source位置。搜索原文与替换显示文的口径必须明确。
5. P1：本书只保存覆盖项；全局变更不覆盖本书显式选项；恢复默认清楚说明范围。现有繁体显示、TTS定时停止先确认覆盖，不重复实现。
6. X1：默认保留高亮/浏览器/复制/更多，用户可调整常用项；显隐不绕过能力门控。外部查询明确由用户触发，保留选区/位置。首期离线词典支持StarDict，扩展格式不属于本轮承诺。
7. E1/E2：单处纠错以source位置及上下文锚定，批量替换以规则执行；原文件不改；普通用户不必输入正则转义。启停可还原；“保存成功”和“正文已应用”分开。
8. I1/I2：原文摘录、用户想法、AI内容、采用记录分开；素材可以聚合多个来源；原书缺失时摘录仍可查看。采用去向先支持文字/链接记录，不自动扩大为桌面项目改造。
9. L1：保存筛选条件形成动态视图，复用现有书单/标签机制；静态书单与动态视图语义清晰。

## 当前已知问题与证据边界

- WorkBuddy最新滚动TXT替换复验FAIL：保存、滚离返回、重进、进程重启仍显示原文。Debug `ScrollReplaceTrace`存在但无新鲜成功证据。
- 源码检查发现：全局搜索主要消费元数据与正文预览；索引仓储不能据类名推断全覆盖。
- “我的”笔记仅消费notes且普通点击只开书；书内面板有更多操作，待统一。
- 删除/撤销范围不对称，旧已删除记录可能被笼统恢复；需用回归测试确定并修复。
- 当前checkpoint包含历史已验收项与未验收WIP；整体状态仍为OPEN。
- 旧架构/路线图中的“安全滚动已完成”已被此记录否定；以更新的代码与WorkBuddy证据继续修正。

## 第一轮文件所有权

下表路径以 `android/app/src/main/java/com/creationreadingassistant/` 为基准。仅拥有列出的现有文件，以及自有新模块/测试/资源文件；同一目录的其他文件不自动归你。

| 负责人 | 独占现有文件/区域 |
|---|---|
| Trae | `ui/screen/profile/ReadingNotesSubPage.kt`、`ProfileRoute.kt`；`ui/viewmodel/ProfileViewModel.kt`；`ui/screen/reader/sheets/ReaderNotesSheet.kt`；`data/repository/NoteRepository.kt`；新增 `feature/annotations/**` |
| Qoder | `data/repository/BookRepository.kt`；新增 `feature/library/deletion/**`；`ui/viewmodel/ShelfBookActions.kt`、`ShelfViewModel.kt`；`ui/screen/shelf/ShelfRoute.kt`、`ShelfSearchRoute.kt`；`ui/screen/readinghistory/MyReadingRoute.kt` |
| Codex | reader其余宿主/导航/投影/layout/规则接线；全部现有DAO/entity/AppDatabase/migration/schema；`AppNavigation.kt`、`ReaderAction`；共享设置、构建文件、Manifest、现有公共strings.xml；主交接与路线文档 |

- Trae 新增字符串写 `res/values/strings_annotations.xml`，Qoder写 `strings_deletion.xml`，必要locale使用同名独占文件；不得同时修改公共strings.xml。
- 测试对应各自模块：Trae新增 `*Annotation*` 或自身笔记/Profile测试；Qoder新增 `*Deletion*` / `*Restore*`。既有跨域测试需SEAM REQUEST。
- `ReaderSheetHost.kt`归Codex；Trae先保持ReaderNotesSheet接口兼容，需要改参数则写集成请求。
- `InspirationDao.kt`含NoteDao/HighlightDao，仍归Codex，防止Trae/Qoder同时改；优先复用现有方法。

## 集成顺序

1. 三方交付各自报告：实际基线SHA、文件清单、命令退出码、测试数量、SEAM REQUEST、未完成项。
   Trae/Qoder开工后先提交必要SEAM REQUEST，由Codex优先明确DAO/导航接口；依赖接线未完成时只能标记Dev partial，不能宣布N1/D1完成或要求WorkBuddy给PASS。
2. Codex先处理DAO/schema/导航等共享请求，接口向后兼容；将明确提交同步到工作树，禁止整体覆盖文件。
3. 冻结三方写入，先集成Qoder数据语义，再集成Trae笔记，最后合入reader接线；每步检查他人改动保留。
4. 串行运行完整JVM、Lint、Debug APK、androidTest编译，保存真实结果和APK SHA-256。
   冻结集成前由Codex将Trae/Qoder工作树的自有报告带入主仓库本目录reports/，保留其原始基线及命令；WorkBuddy在主仓库读取三方报告。
5. WorkBuddy在冻结快照验收；发现问题回交对应负责人，修复后重新计算受影响范围。
6. 文档状态按 Implemented / Dev-verified / Accepted / Blocked 分开登记。整体未接受前不宣称完成，不push。
