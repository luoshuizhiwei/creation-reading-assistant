# J2-v3 / W1-v1 核查及最小闭合批次

## 任务编号/版本

Codex-review-J2v3-W1-v1，2026-10-01；来源：用户“都完成了”。J2-v3四项运行3通过1失败，恢复尚缺两文件；W1-v1服务/夹具字节与离线自检通过，操作说明需更正。零产品写入、零设备操作。

## 输入指纹

- HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。
- 当前候选仍J1-v2；宿主SHA `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`、测试SHA `83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8`。J2-v3完整实装回读日志与这些值一致。
- W1六工件SHA全部重新核对一致；ZIP/JSON可解析且符合数组/信封基本结构。具体值见[审计输出](<E:/creation-reading-assistant/next-j2v3-w1-codex-review-20261001/verification.json>)。

## 所有权与改动

仅新报告、current.md、Codex项目页/TODO及E盘审计/自检副本；原报告、证据、源码、freeze未改。文档[回退副本](<E:/creation-reading-assistant/next-j2v3-w1-codex-review-20261001/before>)。外部任务停止状态由用户完成声明及报告停止点支持，下轮仍核活动作业，避免设备冲突。

## 环境

Windows；资料来自MuMu。Codex不连接设备，读取原日志/清单并用Python UTC独立验算。W1自检在任务副本重跑，端口18082，与原18081分开，进程由自检finally关闭，原日志不覆盖。

## 执行与结果

### Standards：恢复缺口

- 原始 `instrumentation_raw.log` 与汇总一致：4 tests / 1 failure，runner日志显示隔离Application；mockk运行已到业务断言。
- 实装及恢复APK完整回读、签名核查与UID/已记录设置一致证据已提供，接受这些限定项。旧host_cert.txt保留早期失败，后续certificate_verification.json有四份exit0和完整摘要；引用后者，不能称没有失败过。
- 重算前后哈希清单：71→71，无新增/缺失，69一致，2变化。变化是`no_backup/androidx.work.workdb-wal`与`androidx.work.workdb-shm`。WAL是SQLite状态的一部分，不能凭“运行时临时文件”将恢复写100%。原脚本未将原WorkManager主库/WAL/SHM一致组回灌。
- 主业务库与全部已记录shared_prefs文件哈希一致可保留；不证明旧凭据可解密，也不证明应用整体零副作用。报告称测试库在cache没有充分证据，源码使用context.getDatabasePath；下轮按实际路径核，不依赖猜测。

### Spec：测试和夹具预期

1. **唯一仪器失败是预期字面量误算。** `1_760_000_000_000L`独立UTC转换为`2025-10-09T08:53:20Z`；源码断言写`07:46:40Z`，日志actual为正确值。只修测试字面量，不能改产品序列化或毫秒夹具凑PASS。后续filePath/未知字段断言仍在失败点之后，不能把该链全部判通过。
2. **W1时间预期也误算。** ZIP `durChapterTime=1767000000000`应为`2025-12-29T09:20:00Z`，不是报告/README的`05:20:00Z`。夹具毫秒字节不需改，预期说明必须纠正。
3. **保存会发请求。** `ProfileViewModel.saveWebDav()`调用`loadWebDavBackups()`，后者发目录查询。不能要求“保存后零请求/首条必为测试HEAD”；应分保存/刷新与连接测试的独立记录阶段，reset不删日志。编辑层与已保存配置层区别仍成立。
4. **等revision需比时间。** `SyncMergePolicy`：远端revision大接受、小拒绝、相等比较updatedAt。W1的“本地revision≥2一律拒绝”不准确，应明确同revision时间分支。
5. **字段名不解释未匹配。** totalChapterNum与totalChapters影响估算，不影响title/author匹配；旧UI是unmatched=1，不能把缺总章数认作直接原因。

W1受控服务在新副本重跑27/27，exit0；证明协议场景、追加日志、reset保留、脱敏及路径记录。仅离线服务证据，不是App/MuMu通过。没有为说明问题重跑全量产品测试。

## 原始证据

- [审计输出](<E:/creation-reading-assistant/next-j2v3-w1-codex-review-20261001/verification.json>) / [W1独立重跑日志](<E:/creation-reading-assistant/next-j2v3-w1-codex-review-20261001/w1-selfcheck-rerun.log>)。
- [J2-v3原报告](../../../qa/2026-10/2026-10-01-next-j2-v3-room-mumu-acceptance.md) / [原仪器日志](<E:/creation-reading-assistant/next-round-j2-v3-room-acceptance/test_run/instrumentation_raw.log>) / [原恢复清单](<E:/creation-reading-assistant/next-round-j2-v3-room-acceptance/post_recovery/recovery_reconciliation_summary.json>)。
- [W1-v1原报告](../../dispatches/2026-10/2026-10-01-next-w1-webdav-fixtures.md) / [原操作说明](<E:/creation-reading-assistant/next-round-w1-webdav-fixtures/README-operations.md>)。

## 未覆盖项

失败断言之后的链、四项全绿、WorkManager一致组完整恢复、未记录stay_on/reverse等设置边界、WebDAV实际UI安全/匹配导入/JSON恢复及其独立沙箱仍开放。logcat的SELinux告警不能外推实体硬件通过。全产品/发布、N1原子性、桌面/HOLD不扩范围。

## 恢复与副作用

下一设备所有者先保存当前差异现场，核当前状态仍为J2-v3停止点；冷停后仅恢复`no_backup/androidx.work.workdb`及存在的-wal/-shm一致组，来源本轮pre_baseline/data或tar。保留权限/UID/标签，不只拼接旧WAL到新主库，不读旧加密配置。若原冷备或状态不匹配则停止，不覆盖新用户活动。后续仪器仍同签名覆盖安装，不uninstall/pm clear，不启动主UI；结束恢复本轮原APK对、持久文件与设置。

## 交付状态与下一步

- Room功能实测3/4=75%，完整设备关卡未验收；恢复文件对账69/71一致，两文件待闭合。
- W1服务/夹具字节及离线自检已验收；操作说明返修，未来UI不能直接套旧预期。
- **ZCode/GLM：J1-v3 + W1-v2**，在新E候选只改一处测试UTC预期并构建冻结测试包；独立新W1-v2目录更正四组说明、增加UTC/合并条件核对，不改产品或夹具毫秒凑预期。先交付J1-v3 READY，后继续W1-v2文档；设备零操作。
- **DSH/DeepSeek：J2-v4**，独占MuMu，先补WorkManager恢复。待J1-v3精确单hunk/清单/构建/双APK身份符合合同后，运行四项并完整恢复；可以在ZCode准备阶段先做恢复，不抢源码。不进行WebDAV UI。
- 两窗口并行，设备复验依赖新测试包，满足精确合同后顺序执行。联合构建+完整MuMu关卡1/2=50%；A–E5/5=100%、R1–R3实现3/3=100%，全产品分母未建立。
