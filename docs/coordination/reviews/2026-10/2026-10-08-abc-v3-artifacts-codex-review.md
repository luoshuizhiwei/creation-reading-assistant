# A/B/C v3 定点核查与退出条件调整

## 任务编号/版本

A CRA-D2-DEVICE-ARTIFACTS-REPAIR-v3；B CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v3；C CRA-JOINT-RECOVERY-ARTIFACTS-REPAIR-v3。报告路径3/3可见，但C报告是原v2原件；不称三个新报告完成。完整工件验收0/3，已关闭部分保留，不重做产品。

## 输入指纹

Root核查根 `E:/creation-reading-assistant/abc-v3-codex-review-20261008`，fingerprints.json保存当前指纹。
A四代码4/4匹配，manifest d60807322c678e7f4858d01dfc78df5926495d8393fc4493fb884966c69fc2d7。
B九工件9/9匹配，manifest 0df6c4ef9522733775485a039bea1218e52ea47a688ac386585ca9008c66d3f6，无清单自引用。
C driver实际SHA 4bfc39fb2534beb456e49a812e121816c7905e145a42a552783ee51760cab95a，有真实改动。REPORT及原20项日志与v2逐字节相同，不能作v3验证。
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

## 所有权与改动

原v3/v2只读保留；下一独立v4仅A测试合同、B真实UI测试、C恢复/配置接缝互斥写。公共合流/冻结/设备仍唯一C owner，待Root核。产品源码、原充分197JVM及已核APK身份不重做。

## Standards

A建议GLM5.3Flash处理三项明确测试合同；B/C建议当前可用DeepSeek处理真实旅程和数据保护。实际模型由执行者记录，本轮A报告自报Gemini，不自动改变今后路由；GLM5.2不可用。D产品/Git/发布暂停，设备继续NOT_RUN。

## Spec

### A：编译已成立，三项P2续修

DAO、ZIP固定时间、证据实际写出与保留、七方法重开均有进展。四源码在实际编译android树；class产物及日志时间晚于源码，原compileQahostAndroidTestKotlin执行并BUILD SUCCESSFUL in1m26s。独立机器exit记录缺失，不把报告0冒充另核机器记录。

DataStore归属(83–106)仅按任意pb存在二选一，双方有文件时漏host，writer不序列actualDataStoreFiles。输出两个具体Store双方前后指纹/所有可能路径；未知标MAY_TOUCH_BOTH并绑定恢复范围，不反射改单例。

SAME BookFile行空会跳过，成功允许字段列表未作为实际全实体差分门；refresh漏实际size/modified、完整content/file等。相关行必须存在，按实际产品允许变化规范化后其余全行比较。

证据固定根/方法名且无run，新失败可能留旧成功。DataStore根稳定可保留，证据目录按run新建，失败输出本次状态/已得快照，summary和C绑定本run当前方法集合。

### B：编译日志存在，真实旅程仍P1

实际API、Activity延迟启动、正确引导键/恢复、夹具清后建、12列比较及去通配删除保留。报告编译candidate_build/android已不存在，交付src没有编译输入SHA对应，不认定日志造假，也不能签交付准确输入已编译。

Journey198–225用DAO upsert伪造返回后位置/时间，必须删除该验证写入，改真实滚动/翻页及生产保存。Fixtures479的>=不足以证明新保存。

正文全局selector会点分类或匹配多个节点，按目标身份+真实命中片段及点击父节点选择。临时B系统Back按生产合同返回A，不能当回搜索保留栈；不可达多层/重复UI条件登记NOT_COVERED，复用已有JVM辅助，不为测试加产品入口。

普通A仍阅读时采基线，随后离开合法保存未完成就冻结比较；退出后确认合法最终保存，再采临时期间冻结基线。

固定四ID清理无run归属；生成run ID或门禁拒绝未知旧记录，账本只清本次对象，finally保证prefs恢复。

同书搜索追加而未替换查询，SECOND不在实际正文且手工索引偏移与正文不一致；真实夹具唯一词/准确offset/生产索引。普通Back没有pending却宣称pending通过，删除不实声明，无法触发仍NOT_COVERED。

### C：29离线通过，仍三接缝缺口

保留force-stop失败/进程存活/DB枚举失败拒绝、reverse真实格式恢复、原APK对、正式包metadata-only、字符串账本与同run根改进。

清理仍因j1-accept[-staging]关键字豁免精确run，Android/data未锁当前包，task仅黑名单。实际纯内存反例外国task/run与com.other.app均被接受。改正向精确所有权，唯一例外只能显式登记固定文件，未知目录禁止递归删除。

DB远端SHA空或stat失败跳过比较仍db_backup_ok=True；pidof故障与正常不存在未分。所有完整性探针不可读拒绝，manifest完整集合与拉回SHA/尺寸双对账。

item task_id未被callback/harvest真实消费，仍使用顶层联合task；联合索引应调用独立TXT子配置，before/after/checker同身份。冻结不得默认旧v1兜底，最终读取显式冻结。

## 环境

Root未设备、未APK构建；读取原编译日志与产物、指纹，三独立reviewer只读。C实际专项经确认全MockAdb后由Root运行一次，TEMP/TMP在本轮E核查根，未调用真实adb。

## 执行与结果

Root执行 python -B joint-recovery-artifacts-v3/acceptance/offline/test_joint_recovery_hardening_offline.py -v：29/29，机器exit0，C-tests.log。该真实新结果补齐原旧日志证据缺口，但未覆盖上述外国清理/探针故障/真实子合同接缝，不能签整体通过。A/B日志BUILD SUCCESSFUL，B当前准确编译输入仍缺绑定。原产品充分证据复用。

## 原始证据

Root fingerprints.json、C-tests.log；原A/B REPORT、compile-androidtest.log、Kotlin代码/class及manifest；C真实driver/tests与原报告差异。详细独立审查消息由本报告收敛记录，不冒充Root重跑编译。

## 恢复与副作用

仅E离线临时文件/日志和D交接文档变更。没有设备数据/APK/settings改动，不存在本轮设备恢复PASS。历史原件不删除不迁移。

## 未覆盖项

准确联合仪器编译/新测试APK冻结、真实限定MuMu与完整恢复仍待。B不可达时序/多层条件不强行标绿；X整机中断、实体手机、正式书库、桌面与发布不扩展。全产品分母未核。

## 交付状态与下一步

本轮保留已修项；独立v4仅余下A三合同、B真实旅程/归属、C三接缝。B设备合同改为确实可达的真实阅读/搜索/返回/保存与同书落点，辅助JVM及未覆盖项分开。A/B改动后定向编译，C新负向离线验证；核通过后唯一owner合流/冻结，再核才给MuMu。不可达UI不擅自扩成新产品功能。
