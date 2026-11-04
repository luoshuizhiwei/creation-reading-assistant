# N1-v6审查/合流清单：Codex核查与下一批

## 任务编号/版本

`N1-v6-review-inventory-codex-v1`；日期2026-10-02。用户“都完成了”后核两份交付。工程规范与需求语义分开判断；限定采纳源码/JVM修复，完整设备验收仍开放。

## 输入指纹

恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；原损坏仓库未操作。N1-v6 Backup SHA `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e`，Test SHA `4d6825b1a97da6e97d4575b4742c169681b6b6eccac7a6dddbbf35771701a707`。

交付：[审查原报告](2026-10-02-next-n1-v6-independent-review-v1.md)、[合流原报告](../../dispatches/2026-10/2026-10-02-next-n1-v6-integration-inventory-v1.md)。原件保留，以下核查限定其结论。

## 所有权与改动

Root只读产品/候选/共享WIP，写本报告与交接、项目页、TODO/skill log；E自有审查根[本轮证据](<E:/creation-reading-assistant/n1v6-review-inventory-codex-20261002>)保存完整SHA/路径、XML核对、当前WIP差异及文档前副本。使用独立android_reviewer只读语义抽检，与原实施者不同；其未运行测试，不替代Root证据核对。

为补缺失探针绿XML，Root独占已结束的外部probe副本构建输出，先归档其旧LocalZipBackupTest XML，再只跑两个N1v6ReviewProbeTest。未改测试/产品源码或v5/v6正式候选。

## 环境

Windows/Python3.13/JDK17/Gradle8.9；构建与缓存均在E。零ADB、零APK安装、零正式库/实体手机操作，无Git状态写入。

## 执行与结果

### Standards

- 实算v5/v6除机器local.properties与生成/缓存目录：877/877，875同、2改、无增删；报告878口径含local.properties，不能混用分母。两处授权修改与未改StateStore/JsonBridge/Profile/build配置成立。
- 合流相对J1-v3：875→877，6改+2新增=8路径；继承依赖不能只拿v6本轮两文件。原清单的两个Profile共享WIP交集成立。
- 拟合流补丁两侧副本的8路径逐一对J1/N1 SHA成立；Root [verification.json](<E:/creation-reading-assistant/n1v6-review-inventory-codex-20261002/verification.json>)给完整路径与四套SHA，补正原报告省略号/缩写口径。未应用共享树。
- 原“独立审查”由实施者同一会话执行，采纳实算证据，独立性由另一个只读reviewer与Root补充；不称原报告为第三方。

规范结论：限定交付采纳，未发现产品越界；证据保全/分母/独立性限制已补正。

### Spec

- Root与第三方沿真实JsonBridge分类→计划→复制→markDbCommitted→clear核查：冲突约束整个OR，accepted冲突显式回加skipped；冲突目标不进pendingTargets，不剪原义务。strictlyNewer在前且保留skipped；清除核真实SHA，legacy无法凭长度解除。未发现v6新增阻塞级产品缺陷。
- 原红61/4与绿61/0的XML/日志实算成立，红失败逐条指向不同正文写入。Root没有重复全部61条。
- F1/P2测试缺口确认：候选C1–C4固定local/remote revision=2，全部equal。accepted冲突只存在外部probe，尚未进入候选回归集；合流前补一条正式用例，并验证真实accepted分类及正文/原义务/导出阻断。
- F2/P3断言范围确认：C4只断言“未完成状态门禁解除”，完整导出未证明。Root两个探针实跑2/2，XML系统输出仍为`NoSuchElementException: Expected at least one element`；应补齐导出DAO流并断言导出成功与ZIP内容，不能改生产逻辑掩盖夹具错误。
- legacy无法自动收敛/导出阻断是保守合同与待产品决定，不在本轮新增解除入口或清journal。

需求结论：源码/JVM冲突防护限定通过；2项候选测试补强和新联合/设备证据待完成。二报告已核2/2，不代表N1完整验收。

## 原始证据

- [完整实算清单](<E:/creation-reading-assistant/n1v6-review-inventory-codex-20261002/verification.json>)：红61/4、绿61/0、accepted探针v5红2/1，日志任务实际执行；v5/v6差异与8路径四方指纹。
- Root探针命令：自有外部probe/android目录，`:app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.N1v6ReviewProbeTest --rerun --offline --console=plain --max-workers=1`，44秒成功；[日志](<E:/creation-reading-assistant/n1v6-review-inventory-codex-20261002/root-probe-green.log>)、[XML](<E:/creation-reading-assistant/n1v6-review-inventory-codex-20261002/root-probe-green.xml>)。
- Root未把诊断导出错误的探针通过写成完整导出成功；accepted反例在v5失败、v6通过可支持该分支行为。

## 未覆盖项

真实Room/进程kill/存储故障/SAF与用户可见重试路径、正式签名与升级、永久候选合流、D-WIP两个Profile及其依赖整体集成均未运行。G1/W2旧通过范围保持，不扩大到N1。

## 恢复与副作用

候选产品、测试源码和共享树未修改；E probe仅生成构建输出，原XML先归档。新审查报告/入口前文档快照保存在本轮docs-before。未读Claude独立库、未使用旧本地提交或自动接力授权。

## 交付状态与下一步

本轮两报告核查2/2=100%；N1/G1完整验收仍1/2=50%。G1限定5/5保持关闭；全产品分母未核，不给伪造总完成率。

里程碑复盘：保留阅读稳定/数据安全主线及D2、legacy、跨端/F边界；调整执行顺序为两项独立并行：

1. N1-test-hardening-v7：从v6新E副本，仅LocalZipBackupTest补accepted回归和C4完整导出，生产SHA固定；红/绿按具体风险运行，报告交Root核。
2. J3-n1-sandbox-build-v1：从J1-v3新E副本带入N1完整8路径，D树WIP只登记不回灌；新`.qahostn1`独立UID，构建/冻结与单类真实Room/跨进程驱动准备，不操作设备。新后缀runner透传生产Application，须如实记录启动副作用边界。

第二项不依赖第一项的产品源码（v7只改JVM测试），可以先冻结宿主/仪器包。v7核过后更新联合候选唯一JVM测试并跑定向门禁，再交唯一MuMu所有者执行/恢复；不可把先冻结的宿主当完整门禁通过。避免先合并D-WIP或重复G1/W2，保护现有资产并缩短关键路径。当前两项提示词尚未自动发送外部窗口；本轮不永久集成/暂存/提交。
