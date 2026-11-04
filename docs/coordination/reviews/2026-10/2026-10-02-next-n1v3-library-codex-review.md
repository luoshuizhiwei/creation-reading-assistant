# N1-v3 / Library-v1 实际交付核查与下一批（2026-10-02）

## 任务编号/版本

Codex-review-N1v3-Libraryv1-v1。用户“都完成了”后核实际输入、源码差异、原XML及独立语义审查；用户最新原仓库AGENTS路由恢复入口，本轮不在原目录实施。

## 输入指纹

恢复HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。N1父v2/候选v3各878非生成文件，四改、874相同、无新增删除；六文件完整SHA与原报告一致。增量补丁SHA `0307043f438629ecacf650dbb9199b09b9748139b5ba7902ef84a2b1754e28a7`。书库13实际路径候选等HEAD，D工作树6处漂移，包含报告漏掉的LibrarySource.kt；SafLibrarySource为该文件内类型，LibraryBrowserPolicy实际在ui/viewmodel。全清单见E审查JSON。

## 所有权与改动

Root仅写本报告、五现有交接/项目/TODO/规划/skill文档和自有E审查/跟踪表；编辑前逐字节副本位于[E回退目录](<E:/creation-reading-assistant/n1v3-library-codex-review-20261002/before>)。独立reviewer只读，不写产品。原外部报告/候选/历史证据保留，无Git写入或原损坏仓库修改，Claude独立库未访问。

## 环境

Windows/Python3.13。本轮零产品测试重跑、零构建、零ADB/MuMu；运行的是哈希、原始XML解析、输入/调用链和文档检查。外部应用/模型身份来自报告自报，本轮未查界面，不推成永久路由。

## 执行与结果

### Standards

N1四改在六文件授权内；无独立源码规范违例，0项。报告缺完整九字段与Gradle文本日志，但不因此抹掉实际源码及XML交付；下一报告要求补齐。

### Spec：N1-v3不通过（1P1/3P2）

实核红XML6 testcase/6 failure、绿53+14=67 testcase/0 failure/error/skip；绿副本与候选build/test-results逐字节相同。未见随报告提供的Gradle原始文本日志，不声称Root重跑或新构建通过。

1. **P1 空身份解除严格较新保护且仍无preDB计划。** LocalZipBackup235–258：空fingerprint被视为任何备份的重试、全部源目标hash不同就解除skipped。最小无kill反例：本地新版本→缺backup.json坏ZIP留下started空身份→较低版本合法ZIP；真实JsonBridge保留较新DB行却旧正文被复制覆盖。210先提交DB、220/229才算目标/身份、269才持久计划；中断窗口仍在。JsonBridge373–491真实回调只在事务后，ImportMergeResult混合equal与strict-newer，现有六文件没有可靠分类和事务内计划机会。
2. **P2 A→B→A不能收敛。** LocalZipBackup247–250仅遍历当前pending，StateStore128/160–165将前序A目标移至unresolved；B成功后pending空，再回原A，equal被skip，后续同A也不补unresolved。clear内容哈希虽阻止误解禁，但原备份重试不能修复。
3. **P2 旧长度状态无迁移。** StateStore77–82将v2值"11"当SHA；旧json-only身份不等新复合身份，合法原备份无法解除equal跳过，继承unresolved永不等内容SHA。仅针对存在v2持久状态的场景，不宣称发布安装包已有该状态。需版本/明确legacy识别与内容证明重建，不能把长度当哈希或静默删除。
4. **P2 复制finally仍在删除失败后剪枝。** LocalZipBackup471–475直接staging.delete并journalRemove；新测试只覆盖恢复分支700–710。复制半写失败+delete=false仍使残片失去归属，成功重试后可混入导出。

已有旧正文的hash反例与非空备份内容身份局部修复成立；互斥/清理汇总复用，旧三字段完整backup journal默认verified=true保留。原五类仅两个旧项已充分关闭，不把局部修复提高为整体验收。上述四项来自独立静态可达路径，非Root本轮kill实测。

### Spec：Library-v1限定采纳，附Root补正

目录/智能识别/来源索引、书架状态与保存筛选、删除撤销确有实现；13实际源码路径候选等HEAD，D树6处漂移。实解析报告引用的21类既有JVM XML，232 testcase/0 failure/error/skip，无缺类，作为既有输入证据，不冒充本轮运行。

- **D2去重不等于重新关联。** ShelfImporter578–595的contentHash相同分支直接返回Duplicate，不换正文、不重建指针；不能采用“成体系重关联已实现”“一个设备旅程即可闭合完整G1-05”。删除/撤销部分归属成立，真实重关联入口/合同仍需定向核查；不从本次有限查询断言整个产品绝无实现。
- **13→15迁移不是从未验证。** 旧[G0+G2设备报告](../../../qa/2026-09/2026-09-29-stage-e-g0-mumu-isolated-migration.md)有原始instrument-case2-migrate-13-15.log，实际OK(1 test)，SHA `a012bb532e0e884fda49156be4c7ed9d986c8a439256b0cbe389d8f7015ed23c`；迁移测试方法与J1-v3正文一致（方法SHA `c4df5d075ea6f4a52fa6a865f7dd12e0c4b374bb1621273da9b41590fc2eddda`）。保留旧组合态边界，不宣称J1全APK同输入。新建v15库查表不能证明旧库迁移；不以报告“同批”要求制造一个新迁移任务。
- SAF失效/L1设备确认等仍分层记录；历史物理设备不升级为当前MuMu/MIUI结论。书库矩阵漏行不是缺实现，不重开发已存在功能。

## 原始证据

- [核查JSON](<E:/creation-reading-assistant/n1v3-library-codex-review-20261002/verification.json>)、[核查脚本](<E:/creation-reading-assistant/n1v3-library-codex-review-20261002/audit.py>)。
- [N1-v3原报告](../../dispatches/2026-10/2026-10-02-next-n1-repair-v3.md)、[Library-v1原报告](../../dispatches/2026-10/2026-10-02-next-library-evidence-v1.md)。
- [本轮功能跟踪](<E:/creation-reading-assistant/n1v3-library-codex-review-20261002/functional-tracker.csv>)、[计数](<E:/creation-reading-assistant/n1v3-library-codex-review-20261002/functional-tracker-counts.json>)。
- [既有书库UI沙箱构建方法](../../dispatches/2026-10/2026-10-01-next-w2-build-webdav-ui-sandbox.md)、[前轮审查](2026-10-02-next-n1v2-m0v3-codex-review.md)。

## 未覆盖项

N1真实事务接缝修复、四反例新验证、真实kill/存储故障/UI/SAF未执行；G1-05完整D2归属、L1与删除撤销当前UI子链设备验收仍开放。全产品穷尽分母、实际LAN、正式签名/升级/R8、实体硬件/MIUI/F保持原边界。

## 恢复与副作用

本轮零设备操作，不重复71/71后账；TODO其他项目后缀和skill既有历史原字节保留。仅自有E工件与上述文档修改，无提交、push、自动接力、模型切换或账号额度操作。

## 交付状态与下一步

N1-v3交付成立，Standards0/Spec4（1P1/3P2）返修；Library-v1限定采纳并直接补正，不再派纯报告返修。新增G1-01/02/04三项源码归属：当前23功能跟踪中11项证据归属覆盖=47.8%；G1-05仅部分不计新增完整归属。此数不是开发完成率/联合验收率，全产品穷尽分母仍未成立。既定W2七类7/7、联合关卡2/2与A–E5/5不重开。

规划调整：六文件无法同时实现真实提交前计划与版本分类，N1-repair-v4仅增加JsonBridge.kt/JsonBridgeTest.kt窄接缝，不改SyncMergePolicy/数据库模型。并行G1-build-v1从J1-v3建新UID书库UI包，允许qahost后缀与一个新增仪器类，准备删除撤销/L1子链夹具；不ADB、不改产品、不纳入N1，不宣称D2重关联闭合。过审后独占MuMu设备批次，Git仍须新精确范围。

定向文档检查：21文件、142本地链接，0错误/0提醒；TODO其他项目后缀与skill原历史字节保留，HEAD未变/暂存区空。静态检查不证明产品或设备验收。
