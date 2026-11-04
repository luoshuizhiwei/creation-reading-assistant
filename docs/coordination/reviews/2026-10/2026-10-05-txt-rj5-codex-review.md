# TXT修复与真实J5：Codex独立核查

## 任务编号/版本
CRA-TXT-RJ5-CODEX-REVIEW-v1；2026-10-05。

## 输入指纹
HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存区空。重算TXT候选979文件、恢复候选982文件全部匹配各自冻结。恢复APK宿主dbe197fb8160d21350435341289d65e350e233399f1d11c0e49ae1f1216777fe、测试e822972e5c110f9520edbd1f6f6af19becd69b6c45604bec36e23805a6b002be与文件实算一致。

## 所有权与改动
Root仅写本报告、current/TODO、skill使用记录及E核查证据；外部源码/报告原件只读。无构建、设备写入或Git动作。两外部任务已交付，未确认新任务运行。

## 环境
Windows/Python3.13；本轮复用原始XML/ADB记录，不重新执行已充分取证的J5。

## 执行与结果
### Standards
已核两项交付在E隔离候选/定向范围内，未发现新增源码所有权或工程规范违规。XML计数6套件29/29绿，恢复候选相对基座生产Kotlin零变。
### Spec
[P1] TXT修复未建立布局就绪后的恢复重试。ReaderProgressEffects的TXT LaunchedEffect只以readingUnits/savedPlainOffset/savedPlainPercent/pagerEngineOn/temporaryInspection/pending为键；新增layoutReady读取在协程体内，返回null即return@LaunchedEffect。pending不变，布局状态也不是key，且本effect没有snapshotFlow等待。首次未就绪返回后，没有保证布局就绪会重新执行，可能永久pending并阻断保存。两处注释“snapshotFlow/下一帧重试”与实际实现不符。新测试仅分别调用纯函数layoutReady=false/true，没有验证同一生产恢复协程随布局改变继续运行。29绿不能关闭这个缺口；拒绝合流v1，下一v2需等待/重试实际接入及动态回归。
原“未测量时scrollToItem静默忽略”的设备根因模型未有真实effect轨迹证明，仍是待验证假设；不能仅用Python重写旧算法或新函数在旧基座不可编译作为实测根因证明。

真实J5限定行为成立：源码仅经真实ProfileViewModel入口/已有LocalZipBackup.fileCopyOperation测试接缝驻留；生产stage=db_committed且pendingTargets非空，信号pid10444/对应runId、stop_exit0、pid_after空、TERMINATED非普通JUnitPASS。冷读与重试原始输出OK，新进程11803/11966/12180；截图显示数据库已更新/正文待复制/重试入口，重试后横幅消失且正文RECOVERED SHA一致。正确口径是一个J5旅程通过（含一个受控TERMINATED步骤），不是四个普通JUnitPASS。

前后七设置/forward/reverse一致，保护原APK对SHA相同、qahost71条完整文件SHA及测试包0条前后相同；接受本批限定恢复合同，不追溯关闭旧unified-v1缺前账。开工新包缺席记录只留pm-path rc1，缺成功完整包列表的原始回显；不能将此包装成严格双重确认，下一批补足。J1负向谓词要求偏移逐字相等，而TXT修复按单元起点恢复可能自然有差值；后续绑定需显式采用单元映射/范围和UI可见标记，不能任意放宽容差或只require非零。

## 原始证据
- [TXT外部报告](../../dispatches/2026-10/2026-10-05-txt-reentry-fix-v1.md)
- [真实J5外部报告](../../../qa/2026-10/2026-10-05-restore-real-interruption-accept-v1.md)
- [Root核查输出](<E:/creation-reading-assistant/txt-rj5-codex-review-20261005/verification.json>)
- [TXT生产effect](<E:/creation-reading-assistant/txt-reentry-fix-v1/android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt>)
- [前账](<E:/creation-reading-assistant/restore-real-interruption-accept-v1/evidence/baseline_pre.json>)与[后账](<E:/creation-reading-assistant/restore-real-interruption-accept-v1/evidence/baseline_post.json>)
- J5信号/强停/重试在同任务evidence/raw与evidence/ui，原件保留。

## 恢复与副作用
Root本轮无设备操作。文档改前副本保存E本轮before，旧报告不改。J5本批恢复证据不替代旧失败批前账。

## 未覆盖项
TXT v1生产effect重试未验证、原J1根因/真实设备回归未关闭；最终修复统一APK未冻结，J2–J5证据来自各限定候选，不能称同一APK联合通过。

## 交付状态与下一步
本轮2项交付中1项J5限定验收通过、1项TXT返修。跨候选五旅程限定行为证据4/5=80%，J1仍失败；最终TXT修复APK联合门禁开放，全产品分母未核。下一只派TXT-v2返修，保留已核J5与旧29绿证据；待v2可接入后再单owner冻结APK做J1与受影响邻接验收，不为并行重做J5。
审查汇总：Standards 0项；Spec 1项P1（布局未就绪后缺重试）。
