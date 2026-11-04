# 第二视角10项发现：修复派发25–31

## 任务编号/版本

CRA-SECOND-LOOK-FIXES-DISPATCH-v1，2026-10-13。用户在补查后明确要求先修发现的问题，再进行下一批任务；随后要求外部模型不使用GPT/Claude，按WorkBuddy/TRAE当前资源提供首选和替换候选。本文件是当前实施授权与所有权，覆盖旧“仅补查后待命”。仅准备并由用户转发提示词，不向外部应用自动发消息。

## 输入指纹

活动D仓库HEAD实核`e39a78da9c62f02614207cffb36969a32492eaea`、暂存空。只读主输入为`E:/creation-reading-assistant/v0-joint-mumu-task24-v1/candidate/android`；1009源码清单`freeze-t24v2/source-sha256.final.json`实核SHA`08e09f3965a1540e0c85d6cfdbcfc46b4afaec3ec229f8b22cc79d0bd6d798b5`。Android生产与Root-v2同源，不重开旧任务1–24。验收输入为同目录`candidate/acceptance`及`tools`，旧证据只读。

## 执行与结果

本轮7个有效修复任务覆盖[补查报告](../../reviews/2026-10/2026-10-13-all-tasks-second-look-codex-review.md)全部10项NEW。25–30各修一项产品问题；31统一修4项工具问题，避免公共驱动多人修改。7项源码/离线工作可并行；所有Gradle使用既有操作系统排他锁，测试实际排队，不并发Gradle。本批不构建最终APK、不使用设备；全部核查通过后Root唯一owner合流并重新决定同版门禁和限定MuMu范围，再进入新功能开发。

| 编号 | 技术ID | 发现 | 文件所有权（仅各自E副本） | 唯一输出目录 |
|---|---|---|---|---|
| 25 | CRA-EPUB-TEMPORARY-PROGRESS-FIX-v1 | R1 | reader/ReaderProgressActions、ReaderActions、ReaderScreen及必要ReaderSessionEffects/ReaderScreenArgs/ReaderProgressEffects；相关章导航定向测试 | E:/creation-reading-assistant/task25-epub-temporary-progress-fix-v1 |
| 26 | CRA-BACKUP-RELATION-PRESERVATION-FIX-v1 | R3 | feature/sync/JsonBridge、JsonBridgeTest；必要TaxonomyDao/ChapterReadDao最小查询，无schema修改 | E:/creation-reading-assistant/task26-backup-relation-preservation-fix-v1 |
| 27 | CRA-SEARCH-REBUILD-CANCEL-EPOCH-FIX-v1 | R2 | data/repository/SearchIndexRepository、取消/重建并发测试 | E:/creation-reading-assistant/task27-search-rebuild-cancel-epoch-fix-v1 |
| 28 | CRA-TXT-OPEN-FAILURE-PREVIEW-FIX-v1 | R6 | ui/viewmodel/ReaderDocumentLoader及其定向测试 | E:/creation-reading-assistant/task28-txt-open-failure-preview-fix-v1 |
| 29 | CRA-MATERIAL-NESTED-MERGE-SOURCE-FIX-v1 | R4 | ui/viewmodel/InspirationMaterialOps、InspirationMaterialOpsTest | E:/creation-reading-assistant/task29-material-nested-merge-source-fix-v1 |
| 30 | CRA-INSPIRATION-SYNC-WIRE-FIX-v1 | R5 | data/repository/SyncRepository及自有新增InspirationSyncWire测试 | E:/creation-reading-assistant/task30-inspiration-sync-wire-fix-v1 |
| 31 | CRA-ACCEPTANCE-FREEZE-BACKUP-GUARDS-FIX-v1 | S1/S2/R7/R8 | 自有tools/final_ledger、db_backup_staging、make_config；自有acceptance/config/validate_joint_config、driver/joint_driver/joint_run/joint_run_task16与离线测试。强freeze/9Root驱动原件只读复用 | E:/creation-reading-assistant/task31-acceptance-freeze-backup-guards-fix-v1 |

Android文件路径均相对`app/src/main/java/com/creationreadingassistant`。状态全为“待派发”，不以提示词给出推断已开工。执行者开工自行查任务目录/STATUS与活动进程；同ID已有owner不另建并行执行，不要求用户补目录/跑命令。

## 模型与资源

本批外部模型禁止GPT/Claude，GLM5.2按用户既有“不可用”排除，Antigravity Gemini不作为首选。WorkBuddy官方模型页列出GLM5.3/Flash、DeepSeek V4 Flash/Pro及MiniMax/Kimi；TRAE官方列表额外明确DeepSeek V4.1 Flash、Seed-Code等。本轮推荐是任务分配判断；实际账户、反代映射/可用性、消耗以执行端为准，不能由协议前缀anthropic/openai推定底层模型身份。

- 25/27/30：优先DeepSeek V4.1 Flash（或实际菜单的V4 Flash），替换GLM5.3Flash；TRAE可再选Seed-Code。
- 26：优先DeepSeek V4 Pro用于数据关系合同，替换GLM5.3；若这些不可用或成本不合适，已有DeepSeek Flash继续，Root维持相同审查门。
- 28/29：优先GLM5.3Flash，替换DeepSeek Flash；TRAE可再选MiMo-V2.6-Flash。
- 31：优先DeepSeek Flash，替换GLM5.3；另选Seed-Code（TRAE）。

官方公开来源：[WorkBuddy](https://www.codebuddy.cn/docs/workbuddy/From-Beginner-to-Expert-Guide/Function-Description/Model)、[TRAE](https://docs.trae.cn/ide_models)。本轮未查用户账号私有模型配置/凭据，不宣称账号现有全列表已核。

只用已有会话/已配置模型，不新增付费订阅、密钥、节点或反代配置。模型不可自切时，在当前可用且允许的模型上执行并如实记录；服务完全不可用则保留可续接停点，不循环重试数小时。

## 证据与验收

每任务交付自有REPORT.md/STATUS.md、before-after文件SHA、源清单身份、最小patch及文件差量、原失败→修复后通过的生产路径定向用例、原始命令/退出码/XML（工具为纯Python原始结果）。只跑受影响测试，不每任务跑全量3543或lint/APK。测试资源排队单独记时；同原因两轮失败改验证方法或明确停点，不无限叠版本。Root核验覆盖10项发现映射后才合流；完成报告不等于修复已验收。

26关系无独立版本时采用保守合并，保留现有有效关系，补入有效备份关系并幂等，章节已读相同键保留较新read_at；不能仅凭书籍版本就删除更晚新增关系，也不引入schema/迁移或整库镜像恢复新模式。25保留正常主动跳章与分页被动同步原语义；28有显式原正文读取失败时抛出原因，不静默以预览伪装成功。

## 所有权与改动

执行者只写各自E候选/测试/证据，输入与其他任务不变；Root独占全局文档/整合。公共配置、依赖、源模型、Room schema与实体不由并行任务修改。6个Android候选只按1009清单复制源码，不复制build/cache大证据；7个目录不提前补造报告或候选。

## 环境

Windows/PowerShell；已有SDK/JDK/Gradle缓存优先复用，build与证据在E。Gradle包装器只读使用`E:/creation-reading-assistant/v0-joint-mumu-task24-v1/tools/with_gradle_lock.py`，锁为`E:/creation-reading-assistant/locks/android-gradle-exclusive.lock`，覆盖命令子进程全周期；锁忙自行有界排队不杀他人进程。31只做纯离线模拟/本地临时夹具。

## 恢复与副作用

本批不设备/安装/卸载、不触碰正式书库、原损坏仓库、桌面/HOLD或私有记忆；不改D产品/Git/发布，不复用旧24设备许可。31不得对实际设备演练备份/清理反例。无外部消息或自动恢复定时任务。

## 原始证据

[完整补查](<E:/creation-reading-assistant/all-tasks-second-look-codex-20261013/REPORT.md>)及其中reader/data/acceptance报告与反例；[24既有缺口](../../tasks/000-049/2026-10-13-task24-gap-codex-review.md)。开工只核本轮输入/目标文件，不重查已充分且未变化的历史日志。

## 未覆盖项

本文件不代表7项已运行/已修、10项已关闭。24旧D/F/短末项/系统UI/恢复链缺口仍有效；本批不借新增修复关闭这些未证风险，也不称实际LAN/实体手机/签名升级/R8/发布通过。

## 交付状态与下一步

派发规格已准备；用户转发7份完整提示词，任务可并行。用户报告完成后Root核实际成果与原始证据，处理所有权外接缝，再统一合流。修复核验前不安排下一批功能任务。全产品分母仍未知。
