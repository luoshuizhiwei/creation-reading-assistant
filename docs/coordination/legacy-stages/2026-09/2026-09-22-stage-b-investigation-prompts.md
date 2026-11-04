# 下一轮 B/C/D 只读调查任务书

共同目标：把历史问题与当前代码/证据对齐，为一次集中修复提供准确边界。执行者只领取自己窗口对应一节。

## 所有窗口共同要求

- 仓库仅使用 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支预期 `codex/recover-worktree-20260920`，HEAD 预期 `5aed5939600e491843e3c44453921bd6273a233c`；不进入原仓库修改。
- 先读同目录 `2026-09-22-current-task-ledger.md` 和 `../plans/2026-09-22-android-recovery-convergence-plan.md`。旧看板只是历史证据，不能据其 READY/HOLD/横屏要求扩任务。
- 这是多人共享工作区。产品、测试、Gradle、安装脚本、其他人的报告、任务账本都只读；唯一写权限是自己一份输出报告。
- 不运行 Gradle、ADB、App、数据库写入、远端同步；不切分支、不清理、不暂存、不提交、不推送。不读写任何独立 Agent 记忆库。
- 不把静态推断写成真机结论；历史测试须注明日期和原始输出位置，只有汇总数字而无原始证据时明确标注。
- 不按代码行数、注释或“测试很多”判断功能完成；从用户入口追踪实际数据流。
- 分类：仍存在（当前链路/反例支撑）、已修复（源码与对应证据）、无法确认、产品决定。每条分别写问题类型和影响；工程共享文件风险不等同于产品 P1。
- 已有问题沿用旧 ID；新问题给窗口前缀，说明与旧问题是否同源。缺陷未确认前不实施；不用“其他 App 都这样”当需求依据。
- 记录中只用“测试 TXT”“测试 EPUB”“测试 Markdown”等中性名称，避免引用原日志中的真实书名或正文。
- 每份报告建议 150 行以内；附件只引用原路径，避免复制数百 MB 证据。给出文件/符号、关键分支、最小修复和测试边界。
- 报告完成后停止，回传绝对路径、已确认风险/缺口、未验证项、是否改动范围外文件。不得自动领取下一任务。
- 后续真机只测竖屏，横屏按用户要求排除；本轮三个窗口均不验机。

## 窗口 B：S01/S02 父表安全和迁移证据

唯一输出：`docs/coordination/reviews/undated/agent-stage-b-database-audit.md`。

阅读入口：BookDao、InspirationDao、TaxonomyDao、ParentUpsertCascadeSafetyTest、AppDatabase、DatabaseModule、14.json/15.json、AppDatabaseMigrationTest、9/19 产品切片盘点 S01/S02。

任务：

1. 核对五类父表普通/批量写入、软删除与同步合并是否均走保留子表的路径，不能机械把所有叶表 REPLACE 判错。
2. 现有 ParentUpsertCascadeSafetyTest 有五个测试方法；按断言确认覆盖了什么，缺哪些路径。查现有报告/日志是否真正运行该类；找不到就写“未找到证据”，不写“从未执行”。
3. 核对 14→15 实际迁移、schema 差异、注册、手工索引回调及支持的升级起点。给出最低必要迁移用例：数据保留、索引变化、正式开库回调、至少一个支持的历史升级链。
4. 当前生产已引用 MIGRATION_14_15，但 app/src 测试中没有该符号；排查是否另有通用机制覆盖，避免只靠测试命名断言缺失。
5. 将“数据库逻辑测试不必启动 Compose Activity”与 D 已知 UI 启动阻塞分开；不要假设所有 instrumentation 都跑不了。本轮仍不执行设备命令。
6. 列出下一轮最小独占文件与共享文件 hunk；区分 S01 数据写入与 S02 schema，不夹带搜索/导入。

退出：可以据报告直接指定数据库测试补齐任务，且无“已过构建所以数据安全”的推导。

## 窗口 C：S03/S04/S14 搜索、来源替换和备份数据流

唯一输出：`docs/coordination/reviews/undated/agent-stage-b-dataflow-audit.md`。

阅读入口：9/20 S03/S04 审查报告、SearchIndexRepository/Scheduler、BookRepository、ShelfImporter、ShelfViewModel、LibrarySourceIndex、JsonBridge、WebDavBackup、SyncRepository/SyncOptions/SyncPrefsStore。

任务：

1. 核对旧 worker 在重建置脏后写回游标、coverage-only 孤儿清理和删除撤销等待索引三个旧问题；逐项找到当前调用路径/保护条件，不把旧行号直接当现状。
2. 从下拉刷新一路追到正文替换，记录是否有确认、哪些持久数据同步更新、搜索和旧标注坐标如何处理。已确认链路入口：ShelfAction.TriggerRefresh→ShelfViewModel.refresh→syncUpdatedSources→ShelfImporter.refreshFromSource。
3. 核对 content_hash、candidate_fingerprint、size/lastModified、source refs 的成功/失败更新；缺元数据时是否保留基线。区分“只读更新检测”与“实际替换正文”两个职责。
4. 给出精简备份矩阵：正文文件、书籍信息、来源引用、进度、会话、标注/灵感、规则、书单关系、设置，分别标记本地/WebDAV/LAN 的读取、写入与遗漏；无法在本轮核对的格子填未知。
5. C01 六接口复核报告要求后续保留待决 incompleteBookIds 及 rowsByStates；此时产品源码仍处于已删除状态。记录该状态，不自行恢复、不与别的修复混做。
6. 给出跨功能相同根因的去重结果，以及最小修复顺序、文件/hunk、行为测试。不得为了显示一致就擅改所有时长或重写同步层。

退出：高风险数据链的实际行为与证据缺口清楚，建议可分成小任务，不访问真实远端或设备。

## 窗口 D：S05/S06 书架和阅读核心旧问题对账

唯一输出：`docs/coordination/reviews/undated/agent-stage-b-core-audit.md`。

阅读入口：9/20 S05/S06 审查报告，ShelfModels/ShelfBookActions/ShelfRoute、BookViewModel、App/MainActivity/SettingsStore、LibraryBrowserViewModel、Reader progress、PageTurner、CraSlider 及相关测试。

任务：

1. S05：标记读完再取消后的最终分类、旧 READABLE 视图、叠加筛选下徽章数量、本批导入后“查看”的目标和可见性。按最终用户结果判断，不以一次 setter 被调用为通过。
2. S06：跟随系统/启动镜像升级、小 TXT 所有保存点的章号、搜索后全选/反选、动画期间快翻、RTL 滑杆与排序文案，逐条与当前实现/测试核对。
3. 对已修复项列出实际修复文件及有效证据，避免重复开发；对未覆盖项写最小定向测试，不新增大型全量测试计划。
4. F-1 原“首次轻点只收菜单”的改造已废止；当前按现有 ReaderChromeReducer 保留翻页时隐藏菜单，不当缺陷重报。
5. 整理这些问题与 S04 ShelfViewModel、S17 主题/公共组件的共享文件；给出实现可并行的分组，而非按整个目录分给多个窗口。

退出：形成可执行的 S05/S06 差异清单；本轮不安装、不验机、不修改任何代码。
