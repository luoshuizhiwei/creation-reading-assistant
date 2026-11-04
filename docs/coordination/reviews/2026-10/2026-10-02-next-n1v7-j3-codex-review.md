# N1-v7 / J3-v1：Codex 独立核查与下一批

## 任务编号/版本

`N1v7-J3-Codex-review-v1`，日期 2026-10-02。用户本轮“都完成了”后核查两项交付。Root负责源码/日志/工件指纹；复用只读 `android_reviewer` 独立检查 J3 驱动与设备协议。不是设备执行报告。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`；暂存区为空。原损坏仓库只作导航，没有产品/Git写入。
- [v7交付](../../dispatches/2026-10/2026-10-02-next-n1-test-hardening-v7.md)：候选 `E:/creation-reading-assistant/next-round-n1-test-hardening-v7/android`。LocalZipBackupTest SHA `000b7c811abc1ac013d301203d52f6a4ef9c8161a641f6d76ee782573f01e8f3`。
- [J3交付](../../dispatches/2026-10/2026-10-02-next-j3-n1-sandbox-build-v1.md)：候选 `E:/creation-reading-assistant/next-round-j3-n1-sandbox-build-v1/android`。新增驱动 SHA `3bc297688bf4e98a81dfa67c4180eeb9afc9fe4e906a2e9d05edd5272baaaa4c`。
- 两候选生产 LocalZipBackup SHA `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e`。J3 八项继承指纹全部与 v6 一致。

## 所有权与改动

本轮只新增此报告、更新 current/项目页/TODO/skill使用记录，以及 E 盘独立核查文件。未改产品、测试类、旧候选或旧报告；未构建/安装/操作设备。被修改入口的原始副本在 [docs-before](<E:/creation-reading-assistant/v7-j3-codex-review-20261002/docs-before>)。

## 环境

Windows / Python 3.13；Android SDK build-tools 34.0.0 / JDK 17.0.14用于只读 aapt/apksigner。当前设备未连接核实，旧 serial 不能当新批输入。外部模型为动态资源，下一任务建议 GLM 5.3 Flash 或当前可用强模型负责驱动修复与设备批。

## 执行与结果

### v7：两项测试补强已验收

- 实算 877→877 文件，仅 LocalZipBackupTest 改变；生产字节未变。
- 原始绿色 XML 实算 **62 tests / 0 failures / 0 errors / 0 skipped**，保留旧61方法。日志有实际 `:app:testDebugUnitTest` 和 BUILD SUCCESSFUL。
- C5 使用真实 JsonBridge 和受控 DAO；实际 `withArg` 内有 `require(revision == 3)`，不是丢弃 Boolean 的伪断言。断言 A 义务保留、原正文不被 B 替换、状态未完成、导出精确被未完成状态门禁拒绝。
- v5 负向对照只有 C5：1 test / 1 failure，实际错误 ORIGINAL→B CONTENT。失败到达正文断言，分类验证已执行。
- C4 真正执行 exportZip，打开实际输出，解析 backup.json，断言仅有 b1，并比较 ZIP 正文 A CONTENT。DAO导出夹具补全后的原始绿色结果充分，不为确认重复全量跑。
- 以上是 JVM/测试范围，不等于真实 Room、跨进程或 UI 通过。

### J3：构建冻结成立，设备驱动返修

- J1-v3 875→J3 878 文件：7改、3新增，合计10路径；恰为八项N1输入、后缀单hunk、新驱动。后缀是唯一配置替换，未回灌 D-WIP。
- 冻结 APK 与本地 build/outputs 实算一致；签名工具两包退出0、证书一致。宿主包 `com.creationreadingassistant.qahostn1`；测试包 `com.creationreadingassistant.qahostn1.test`；runner 指向新宿主。
- 宿主 SHA `65003c06f4759fcc05f70bd2d52323f0f438ac1505b8162299a1385ac5dcea5b`；测试 SHA `5f2a1edc10ecbe93876ac31c53b3c45713daef0796da4567db4ce5e8275e3a39`。
- 最终原始日志 BUILD SUCCESSFUL in 47s。此前被覆盖日志缺失如实保留，不借最终成功追认旧失败过程。
- `.qahostn1` 不匹配 runner 的旧精确 `.qahost` 分支：生产 Application/Hilt/startup/WorkManager会在新UID内运行；隔离不等于没有启动副作用。
- 全21设备方法 NOT RUN；当前驱动存在以下可证实问题，不能直接派设备执行旧协议。

## Standards

1. **P1：既存沙箱包的处理可能删除原资产。** 原报告228–235允许冷备已有宿主后覆盖；260–262又无条件卸载，test包也未查既存。下一批新包合同改为：精确检查宿主/test任一个既存即停，不覆盖、不清理旧包。其他既有正式/历史包保护不动，不要求“正式包未安装”。
2. **P2：转发清理越界。** 原报告263允许 reverse --remove-all。下一批只移除本轮新增的精确mapping，恢复本轮改动并对账完整 forward/reverse 基线；禁止 blanket removal。

Standards：2项，最高P1。

## Spec

1. **P1：准备第一步必败。** 驱动97行 sandboxRoot 有 mkdirs；156–160删除后再次调用getter即重建，assertFalse必失败。取路径与创建目录分离，删除证明写到被删树之外。
2. **P1：accepted跨进程路径不可达。** 384–388的 n1_31 在 arm A 后才导入 B；真实 force-stop使B永远不可达，重跑又从arm开始。拆成独立arm、新进程纯读、B导入/B重试；Room读回A revision=2、B revision=3。
3. **P2：旧信号/continue误导。** 531–559、675–681未清信号/continue；仅文件存在即可触发错误强停。continue存在被记录成killObserved=true也错误。使用runId、scenario、新时间、实际PID；arm正常返回是无效中断，不可记PASS；真实终止凭外部PID消失及新进程读回证明。
4. **P2：纯读弱断言及场景残留。** 564–587只写JSON，openDb能新建空库；480–519 seed不清前场景状态。打开前断言库存在，读回验证原正文/准确A义务/未完成/真实journal和新PID；各场景隔离目录/库，或留证后显式清理，保留同场景跨进程状态。不能由前一场景残留触发导出门禁。
5. **P2：导出内容未验证。** 234–246、758–760仅查容器与名称。实际解包比正文A、解析JSON与真实DB行id/revision；n1_50要固定预期分支，不能根据任意当前状态选择可通过分支。

Spec：5项，最高P1。均为驱动/协议修正，不是新的生产算法缺陷。

## 原始证据

- [Root可复跑核查脚本](<E:/creation-reading-assistant/v7-j3-codex-review-20261002/verify.py>) / [全树指纹、XML与APK核查](<E:/creation-reading-assistant/v7-j3-codex-review-20261002/verification.json>) / [执行输出](<E:/creation-reading-assistant/v7-j3-codex-review-20261002/verify-output.txt>)。
- Root E目录保存 host/androidTest 的 badging、manifest、signature 原输出，各自SHA及exit写入verification.json。
- [v7原始测试证据](<E:/creation-reading-assistant/next-round-n1-test-hardening-v7/freeze/evidence>)：stage1-red、stage2-green、negative-v5-c5 XML/log；未覆盖旧证据。
- [J3冻结清单](<E:/creation-reading-assistant/next-round-j3-n1-sandbox-build-v1/freeze/freeze-manifest.json>)及 logs/build-qahostn1.log。Root未用外部脚本过期的9路径假设代替当前实际10路径核查。

## 未覆盖项

真实 Room/force-stop/跨进程/SAF UI均未执行。内存prefs wrapper不能证明真实用户设备身份持久化；当前驱动证据只对应其明确接缝。MuMu不能证明实体硬件/MIUI。整包DB+文件原子性、正式签名/升级链、桌面/HOLD、F发布保持原边界。

## 恢复与副作用

本轮零设备操作，未获得新设备事实；不声称恢复已运行。产品与Git均未改变；原HEAD/空暂存区核实。下一MuMu批须另存本轮既有包/原APK对、持久文件DB/WAL/SHM与settings/forward/reverse基线，严格只装此前不存在的新沙箱对，收尾卸载本轮新UID并对账，不删除正式/历史资产。

入口/报告相称验证见 [document-verification.json](<E:/creation-reading-assistant/v7-j3-codex-review-20261002/document-verification.json>)：显式检查21文件及相关本地链接。TODO其他项目原始后缀逐字节保留，skill日志原始前缀保留；没有把已完成v7当新待办。

## 交付状态与下一步

- v7已验收；J3构建/冻结已验收，驱动/协议返修。旧冻结仍留存，不在原目录修补后假称同一冻结有效。
- 下一项 `J3-n1-fix-accept-v2`：从J3-v1制作新E候选，合入已核v7唯一JVM测试，仅改新驱动，修上述七项；定向JVM、构建/静态前检过后由同一唯一执行者顺序完成MuMu五场景、导出正反门禁与恢复。这样减少“只构建→再派设备”往返，不引入重叠所有权。设备前检失败则仅交返修证据；生产失败则保全现场并按恢复边界收尾，不扩生产修改。
- G1/W2/旧迁移已充分验收，不重跑。D-WIP/Profile保全，当前不做永久回灌或Git集成。
- 复盘：保持既定数据安全闭环优先；两JVM缺口已关闭，剩余关键路径从生产修复转为测试驱动可信性与MuMu。没有证据支持新增生产任务或开放桌面/发布。
- 百分比：v7补强 **2/2=100%**；N1/G1完整验收仍 **1/2=50%**；A–E限定恢复历史 **5/5=100%**。功能表归属/交付覆盖 **11/23≈47.8%** 不等于全产品开发或验收完成率；全产品完整分母仍未核定。
