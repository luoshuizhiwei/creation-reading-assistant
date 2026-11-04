# J1-v3 / J2-v4 / W1-v2 独立核查与 WebDAV UI 下一步

## 任务编号/版本

Codex-review-J1v3-J2v4-W1v2-v1，2026-10-01。来源：用户“都完成了”。结论：J1-v3单hunk源码/构建交付通过；J2-v4限定Room四项及本轮已记录恢复范围通过；W1-v2说明修正通过。WebDAV真实UI/有效导入尚未验收，联合设备门禁仍开放。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；本轮未执行任何Git写操作。
- [J1-v3候选](<E:/creation-reading-assistant/next-round-joint-j1-v3/android>)与J1-v2各875源码；仅SyncRepositoryPersistenceTest.kt一处UTC字面量07:46:40Z→08:53:20Z不同，字节替换精确相等。六SHA清单实算6/6一致。
- 宿主SHA `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`；新测试SHA `03b2fa0d6b8ce5674ffab4e81e5d4b91ea0266bebb1e082ab92880fcebbd28a3`；与本轮设备实装原始哈希一致。
- W1-v2七工件SHA实算7/7一致；服务、ZIP/JSON字节未变，旧27项自检证据按相同输入复用。详见[审计JSON](<E:/creation-reading-assistant/next-j1v3-j2v4-w1v2-codex-review-20261001/verification.json>)。

## 所有权与改动

Codex只写本报告、恢复current.md、Codex项目页/TODO/skill使用记录，以及E盘定向审计副本。产品源码、外部报告、冻结工件与原日志保持原件。被修改入口已保存[回退副本](<E:/creation-reading-assistant/next-j1v3-j2v4-w1v2-codex-review-20261001/before>)。其他项目TODO后缀逐字节保留。

## 环境

Windows；Codex本轮零设备操作。设备证据来自外部窗口MuMu运行，不是实体硬件或MIUI。使用Python只读哈希/日志与副本23项核对；新测试包apksigner verify现场exit 0，证书SHA与原合同一致。未重跑2673 JVM、Room仪器或相同服务27项。

## 执行与结果

### Standards：输入和恢复

- J1-v3精确单hunk成立，2673 JVM/宿主既有证据不因测试预期字面量变化失效；定向assembleQahostAndroidTest日志BUILD SUCCESSFUL。
- 实装双APK匹配冻结；恢复双APK与本批冷备一致：宿主`e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`，测试包`fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`。
- 严格解析前后SHA清单：71/71相同，零新增/缺失/修改；含上轮未闭合的WorkManager主库/WAL/SHM。本轮冷停后的三文件恢复支持本批一致组对账，不证明运行中原子恢复。
- 七项已记录设置前后一致：size、density、font_scale、accelerometer_rotation、user_rotation、stay_on、night_mode。该批night=yes，与旧批night=no是不同时间的基线，不混用。

### Spec：实际断言链和说明

- 原仪器日志四项各start=1/end=0，结尾OK (4 tests)；位置/本地事实/完成状态/提交最新值的完整四项链通过，包括旧UTC失败点之后的断言。
- W1-v2更正时间1767000000000→2025-12-29T09:20:00Z、保存触发目录查询、同revision比较updatedAt、匹配由title/author决定。自有副本23/23核对exit 0。这是说明/夹具检查，不能替代产品执行；脚本内同构merge/normalize不作为产品测试。
- README保存阶段写“1条PROPFIND”按阶段/seq实际观测，不拿它约束整个UI会话总数；保存和测试请求分别记录。

## 原始证据

- [J1-v3交付](../../dispatches/2026-10/2026-10-01-next-j1-v3-utc-assertion-fix.md)、[READY](<E:/creation-reading-assistant/next-round-joint-j1-v3/READY.json>)、[构建](<E:/creation-reading-assistant/next-round-joint-j1-v3/freeze/evidence/gate-v3-qahost-test.log>)、[六SHA](<E:/creation-reading-assistant/next-round-joint-j1-v3/freeze/six-file-sha256.txt>)。
- [J2-v4报告](../../../qa/2026-10/2026-10-01-next-j2-v4-room-mumu-acceptance.md)、[仪器原日志](<E:/creation-reading-assistant/next-round-j2-v4-room-acceptance/phase3_test_run/instrumentation_raw.log>)；原日志SHA `7c127717ff469632bcf5c200c5a239880410a0f8f5f3727fcf79a2da6e220a8e`。
- [前SHA](<E:/creation-reading-assistant/next-round-j2-v4-room-acceptance/phase3_pre_baseline/device_data_sha256.log>)、[后SHA](<E:/creation-reading-assistant/next-round-j2-v4-room-acceptance/phase3_post_recovery/device_restored_data_sha256.log>)、[原APK对](<E:/creation-reading-assistant/next-round-j2-v4-room-acceptance/phase3_pre_baseline/device_installed_apk_sha256.log>)、[恢复APK对](<E:/creation-reading-assistant/next-round-j2-v4-room-acceptance/phase3_post_recovery/device_restored_apk_sha256.log>)。
- [W1-v2交付](../../dispatches/2026-10/2026-10-01-next-w1-v2-fixture-expectations.md)、[说明](<E:/creation-reading-assistant/next-round-w1-webdav-fixtures-v2/README-operations.md>)、[Codex副本核对日志](<E:/creation-reading-assistant/next-j1v3-j2v4-w1v2-codex-review-20261001/w1-expectations-rerun.log>)。

## 未覆盖项

- J2-v4不启动主UI、不导入真实书库、不访问生产SecurePrefs；它未证明旧凭据/KeyStore恢复能力。脚本虽在说明提reverse，实际未保存reverse清单；Room批次未用reverse，因此只关闭已记录范围，下一UI批必须新增此项基线。
- 原报告把测试DB写成cache路径，实际Context.getDatabasePath不支持该表述；采用四项原始运行及71文件对账，不能据此声称应用绝对零副作用或全部恢复能力成立。
- JSON全payload替换、本机无filePath、跨端墓碑/shelved、N1整包DB+文件原子性及残留导出污染、桌面/HOLD122、实体硬件/MIUI/TalkBack/R8/签名升级/F发布仍按当前范围开放。

## 恢复与副作用

Codex未连接MuMu，未启动服务/改原设备，未改原损坏仓库或Claude独立库；23项在E盘副本执行，不覆盖外部证据。旧J2-v1卸载回灌的密钥状态仍未证明；本批71文件/双APK/七设置一致不追溯修复旧KeyStore。

## 交付状态与下一步

- 当前限定Room 4/4=100%，文件恢复71/71=100%；R1–R3实现3/3=100%；A–E历史恢复5/5=100%。联合构建+完整MuMu仍1/2=50%（WebDAV UI未验）；全产品分母未定。
- W2-build-v1：ZCode/GLM在新的E盘UI候选，从J1-v3派生，仅将qahost applicationIdSuffix改为`.qahostwebdav`，构建真实App/Hilt宿主，freeze完整源码清单、manifest/权限/provider/证书/APK与READY；不跑ADB，不改共享树或其余变体。
- W2-accept-v1：DSH/DeepSeek独占MuMu。可先准备夹具和只读设备基线；等待上述精确包合同成立，确认新包预先不存在、新UID与新私有库后，做实际WebDAV UI安全、方法回退、编码、Legado/JSON有效导入七用例。旧qahost及正式包不启动/不写数据；手机尺寸密度临调后恢复，reverse等新增映射逐项恢复；仅清理本批新建测试包/中性资产，完整保全原环境。
- 失败写FAIL/BLOCKED，保留原始证据并停止越界；不通过降低断言或导入0条凑PASS。两窗口仅设备前置/构建真正独立部分并行，设备UI依赖精确READY。
- 上述为给用户的下一任务分配，不代表已向外部应用发送、已运行或已完成；无新暂存/提交授权。
- 入口定向验证：21文件、134本地链接，0错误/0提醒；HEAD/暂存区未变，其他项目TODO后缀与回退副本逐字节一致。静态检查不作为产品验收证据。
