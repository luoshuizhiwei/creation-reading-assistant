# N1-v2 / M0-v3 独立核查与合同重排（2026-10-02）

## 任务编号/版本

Codex-review-N1v2-M0v3-v1。用户“都完成了”触发核实际交付再推进；Root核指纹/原始结果/范围，独立Android reviewer核语义，不仅据报告关闭任务。

## 输入指纹

HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。N1父v1与v2实数各878文件，五改/一授权文件保持、其余873一致，无新增/删除/越界；六候选完整SHA与报告一致。增量补丁SHA `e2fc3ed332649091de1556cc6765643714bbde1f025bbd7fbe48571d4cb6487e`，累计补丁SHA `12db1465574983f9e106473d1a3d76acb7afea2672ff4f6771f56d4bbce7633d`，均实算成立。本轮未应用补丁或构建。

M0-v3矩阵34×13、证据31×8、raw计数26/2/6及哈希成立。Root未运行会覆盖其CSV的build_scope_v3.py，在自有审查目录独立解析和补正，保留原材料。

## 所有权与改动

Root仅写本报告、现有current/项目页/TODO/规划及skill日志与自有E审查/功能跟踪。独立审查只读，无产品/设备/Git变更。五文档先保存[字节回退副本](<E:/creation-reading-assistant/n1v2-m0v3-codex-review-20261002/before>)；TODO其他项目和skill既有历史原字节保留。旧轮任务行退出日常表，完整快照保留可追溯。原报告/源码/CSV/HOLD不改，Claude独立库不访问。

## 环境

Windows/Python3.13，零ADB、零MuMu、零构建/测试重跑。本轮新运行的是交付指纹/原始XML/计数与静态执行路径核查，不把前轮JVM说成Root本轮重跑。外部应用/模型无界面核查，不作永久路由；外部执行记录为自报，不是占用锁。

## 执行与结果

### Standards

N1五改均在授权六文件范围，当前生产@Singleton共享Mutex调用未见额外实例或多进程；无独立规范违例，0项。JVM原XML47+14=61实际testcase，0fail/error/skip、报告XML SHA正确；最终日志实际test任务与BUILD SUCCESSFUL in37s成立。

### Spec：N1-v2仍返修（3P1/1P2）

旧五项中 **2/5在当前审查范围关闭**：导入/导出并发的现有生产服务全生命周期互斥成立；递归清理false检查及先合并suppressed再落摘要成立；started文案修正成立。其余三项部分修复，不宣称设备验收关闭。

1. **P1 已有旧正文仍假完成**：LocalZipBackup.kt230–244与StateStore232–245只isFile。首次DB提交后换入前失败，真实同版本merge跳过已有OLD；OLD存在就不会解除skipped，actualTargets空、unresolved只检查文件存在，状态删除、导出放行。旧正文长度不同也能复现，报告宣称严格长度验证不成立；新用例只验missing正文。
2. **P1 DB提交后、计划落盘前窗口仍丢目标**：LocalZipBackup.kt204–253，DB208–210返回后才247–253存指纹/pending。该处kill，磁盘started含空指纹/空pending；同ZIP重试等版本被skip，没有目标可解除，缺正文却清状态。须提交前持久计划，不能仅把markDbCommitted放复制前。
3. **P1 不同备份/较新版本保护仍被绕过**：224–235的isSameBackupRetry以pending非空作OR，指纹仅backup.json；A高版本提交后正文缺失，B低版本同路径本应受保护，却被解除skipped、复制旧正文；StateStore160–165按同路径移除A义务。须身份覆盖正文、义务不可按路径消除，并保留真实较新行保护。
4. **P2 半写删失败仍剪枝**：LocalZipBackup.kt678–683及449–453忽略staging.delete=false仍移journal。重试清状态后残片无归属依据，再次导出。仅删除成功/已不存在才剪枝，失败保留日志且可观察。

没有证据报告“未校验分支删除旧完整backup”：旧三字段journal默认verified=true，完整staging/backup恢复保留，不将推测列为缺陷。上述四项是静态可达路径，未冒充本轮真实kill复现。61已测用例不覆盖这些反例。

### Spec：M0-v3草案采纳与Codex直接补正

采纳原始分类、来源导航、历史/当前分离和计数工件；**不采纳26为去重功能分母，亦不采纳3/26=11.5%全产品进度**。具体问题：

- 声明5重叠却脚本denom仍len(v3_req)=26；G3-06回归是验证活动，G1-03/G2-02/G4-02/G5-03是父项子面/缺陷，不重复计数。
- G4-01父旅程不能忽略N1恢复安全缺陷算完整通过；G5-01含真实跨端子项未闭合，只记有限覆盖。
- v3遗漏具体journey_name，Root从原D1还原名称；实现计数分类加总只有25，未分类的G5-03不能消失。
- [已授权Android执行入口](../../../plans/android-parallel-delivery-2026-09-09/README.md)明确R1-D1删除/撤销、R2-D2重关联、R5-L1动态筛选、R6无障碍资源化及手机/平板/横屏。原矩阵漏删除/重关联且把R6改待决定，不能因此取消。Root加G1-05数据生命周期旅程，L1列入G1-04检查点，G7-01保留功能。
- 签名/升级/R8/发布文档是交付门禁另计；实体硬件为当前MuMu限定证据边界，用户无需重复选设备；F公开发布独立授权。候选池未承诺，排期不强迫新取舍。

Root独立[功能跟踪表](<E:/creation-reading-assistant/n1v2-m0v3-codex-review-20261002/functional-tracker.csv>)保留全部原行、父子关系/归类、补缺旅程和名称，计数功能23项（Android17/桌面6），不删已授权行为或历史证据。它是当前已列范围，不证明全产品穷尽。

当前**交付/归属证据覆盖8/23≈34.8%**：六桌面源码归属、LAN限定实现、WebDAV七类。此数含不同证据层级，**不是完整开发完成率或联合验收百分比**。WebDAV既定验收闭合1项、LAN有限覆盖1项、恢复安全缺陷父项1项，其余20项证据仍需归属；未知不等于未开发，不能据此重开全部功能。

### 规划复盘与下一批

同重试根因经过两轮仍未闭合，改派发合同：v3先留下四类失败对照，再统一设计提交前待修计划、内容身份、目标完成判定与删失败归属保留，不再逐项写“已修复”而测试只覆盖missing。两已闭合项复用，保留现有journal/版本保护，无架构重写。过审后才合流构建/MuMu；W2不重开。

M0计数补正由Codex落盘，不派纯报表返修；并行一个窄只读Library-evidence-v1，针对G1已有恢复资产和E证据归属，补遗漏的数据生命周期来源；充分且输入未变证据复用，不构建或设备。不得把矩阵漏行/旧证据未知当新产品缺陷，不制造新功能或扩Git授权。

## 原始证据

- [Root审查JSON](<E:/creation-reading-assistant/n1v2-m0v3-codex-review-20261002/verification.json>)、[核查脚本](<E:/creation-reading-assistant/n1v2-m0v3-codex-review-20261002/audit.py>)。
- [Root功能跟踪及计数](<E:/creation-reading-assistant/n1v2-m0v3-codex-review-20261002/functional-tracker-counts.json>)。
- [N1-v2原报告](../../dispatches/2026-10/2026-10-02-next-n1-repair-v2.md)、[N1源码候选](<E:/creation-reading-assistant/next-round-n1-repair-v2/android>)、[原始XML](<E:/creation-reading-assistant/next-round-n1-repair-v2/results>)、[原始日志](<E:/creation-reading-assistant/next-round-n1-repair-v2/logs>)。
- [M0-v3原报告](../../decisions/2026-10/2026-10-02-next-m0-scope-v3.md)、[原矩阵](<E:/creation-reading-assistant/next-round-m0-scope-v3/m0-scope-v3-matrix.csv>)、[原计数](<E:/creation-reading-assistant/next-round-m0-scope-v3/counts-v3.json>)。
- [前轮四方边界](2026-10-02-next-w2v3-n1-codex-review.md)、[产品规划](../../../plans/2026-10-01-product-roadmap-realignment.md)、[当前交接](../../../handoff/current.md)。

## 未覆盖项

N1四反例的修复/故障注入、真实kill/UI/SAF、永久候选新构建验收仍未做；全产品穷尽分母未成立，历史书库/笔记等证据不能仅凭头部摘要全部作废或转绿。R6授权不取消，实体硬件/密钥/实际LAN/正式签名/R8/F证据边界保留。

## 恢复与副作用

本轮设备零操作，不重复前轮71/71后账。本轮无产品源码/工件写入、Git暂存提交、目录迁移或外部通信/自动任务；仅自有E审查与既有文档编辑。历史表移出日常入口后可由编辑前快照回读；TODO无关后缀和skill原历史保留。

## 交付状态与下一步

N1-v2返修：Standards0/Spec4，3P1/1P2；旧五项2/5=40%在本次审查范围关闭，三项部分，非完整N1验收。M0-v3限定采纳附Codex分类/需求补正；23功能跟踪、8/23证据覆盖不冒充全产品完成率。既定W2七类7/7及联合关卡2/2复用；A–E5/5、R1–R3实现3/3均局部。

下一并行N1-repair-v3（一个新E修复owner）与Library-evidence-v1（另一个只读owner）。每项任意具备能力的空闲窗口，按编号/指纹/报告接手；输入变化升版本。过审后再构建和独占MuMu，Git仍按新精确许可。

定向文档验证：21文件/146本地链接，0错误/0提醒；五文档指纹成立，TODO其他项目后缀与skill既有历史原字节保留，N1六候选SHA未变，HEAD未变、暂存区空；本轮零产品写入/设备操作。结果位于E审查目录document-verification.json。
