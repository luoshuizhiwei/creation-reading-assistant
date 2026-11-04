# J1-v2 / J2-v2 核查与下一轮所有权

## 任务编号/版本

Codex-review-J1v2-J2v2-v1，2026-10-01；来源：用户“都完成了”。J1-v2 限定源码/构建交付已验收，设备运行未验。J2-v2 的证据缺口识别可用，错误路径、错误归因和过强结论由本报告补正，引用时以本报告为准。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。
- 新活动候选：[J1-v2 android](<E:/creation-reading-assistant/next-round-joint-j1-v2/android>)；[六文件清单](<E:/creation-reading-assistant/next-round-joint-j1-v2/freeze/source-manifest-j1v2.csv>) 全部与本轮实算一致。
- 两候选各875源码文件（排除build/.gradle/.kotlin/.idea及local.properties）；逐文件SHA比较仅两个授权文件变化，无产品源码差异。
- 宿主 `app-qahost.apk` SHA：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`，与旧freeze逐位一致。
- 新测试 `app-qahost-androidTest.apk` SHA：`83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8`。
- v2两个APK和旧冷备两个APK的 apksigner verify 均exit0，证书SHA一致：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。这是本地四个文件的证据，不替代下轮设备实际包/UID核查。

## 所有权与改动

Codex仅写本报告、交接入口、项目页/TODO和E盘审计工件；未改外部原报告、矩阵、产品、测试源码或freeze。本轮文档回退副本：[before](<E:/creation-reading-assistant/next-j1v2-j2v2-codex-review-20261001/before>)。

## 环境

Windows，本轮零ADB/设备操作。复用已有code-review与verification-before-completion方法，定向读取源码、补丁、原始构建日志并重新实算SHA/证书。不重跑2673项JVM或全量构建。

## 执行与结果

### Standards

- J1-v2变更限定为仪器测试及mockk-android依赖hunk；父行、失败明细及行存在断言已补。没有关闭FK或mock Room/SyncRepository/网络协议栈。
- 两个配置/身份提供者均改mock实例，移除真实config写入与clear。静态路径支持隔离设计；mockk ART运行、ContentProvider/WorkManager残余副作用仍需新批次核对，不能写“应用整体零副作用”。
- `gate-v2-qahost.log`实际为BUILD SUCCESSFUL，APK实算正确；这里只验收构建交付，四用例NOT RUN。

### Spec：J2-v2补正

1. **凭据损失未实证。** 原报告“所有密码/Token/AI密钥已被永久清空”是过度推断。代码证明SecurePrefs失败可触发删除重建；本轮没有前后密钥/配置运行证据，实际发生情况标NOT VERIFIED。下一轮不能以验证为由触发原配置自愈，亦不能称旧损失已恢复。
2. **输入状态归因须收窄。** J2-v2称“未触发Compose onValueChange”并不符合截图已显示新URL的事实。源码 `ProfileViewModel.testWebDav()` 调用 `webDavManager.test()`，后者默认读取已保存的 `webDavConfigStore.config`；编辑输入与保存配置是两层。可确认测试路径需要核已保存值；具体旧批次是否已保存不能靠猜测。原单步脚本也未保留其所称“显式聚焦后0请求”的完整证据。
3. **错误原文与路径。** 原J2已经将三项仪器列为FAIL，错误在于外推“产品无回归”，不能改写成原报告把四项全判PASS。R1 XML单个 `SyncRepositoryLocalFactsTest` 本身21项，不是“12项+另一WebDAV类9项”；summary也没有`spec4_requests_count`键。实际文件名是app-qahost.apk/app-qahost-androidTest.apk，冷备目录是cold-backup，主库是databases/creation_reading_assistant_native；J2-v2的debug后缀/cold_backup/cra_database均不能作为取证入口。
4. **保留有证据的结论。** APK完整值/21与24计数勘误、实装身份缺证、4项3失败、日志reset删除、安全用例summary=false、ZIP更新0条及恢复边界未闭合均成立。协议顺序来自summary请求明细，不来自只含UI节点的XML。JSON缺少成功DB对账，但不能仅凭确认弹窗断言“后续绝无截图”；需逐份读现存工件。

Standards限定实现无阻断发现；Spec报告有上述四组补正。J2-v2作为缺口清单引用时必须附本裁决，不能继承其确定性归因。

## 原始证据

- [本轮875文件/六SHA/双APK核查](<E:/creation-reading-assistant/next-j1v2-j2v2-codex-review-20261001/verification.json>) / [证书核查](<E:/creation-reading-assistant/next-j1v2-j2v2-codex-review-20261001/certificate-verification.json>)。
- [J1-v2原报告](../../dispatches/2026-10/2026-10-01-next-j1-v2-persistence-fixture-fix.md) / [原构建日志](<E:/creation-reading-assistant/next-round-joint-j1-v2/freeze/evidence/gate-v2-qahost.log>)。
- [J2-v2原报告（须结合本补正）](../../../qa/2026-10/2026-10-01-next-j2-v2-evidence-correction.md) / [原矩阵](<E:/creation-reading-assistant/next-round-j2-v2-evidence-review/evidence_correction_matrix.json>)。
- [上一轮Codex实证审阅](2026-10-01-next-j1-j2-codex-review.md)。

## 未覆盖项

四项仪器运行、实际配置安全拒绝、匹配ZIP更新及JSON恢复DB对账、设备实际APK/UID与持久数据恢复、旧批次KeyStore状态仍未闭合。MuMu不证明实体硬件/MIUI/R8/正式升级；N1整包原子性、桌面/HOLD范围不扩展。

## 恢复与副作用

下轮J2-v3仅运行指定四项仪器，不启动主UI、不改WebDAV配置、不做ZIP/JSON导入；测试DB独立，配置/身份mock。先冷备当前真实APK对和持久数据/系统设置，再同签名覆盖安装；严禁uninstall或pm clear。测试后同签名恢复原APK对与约定文件/设置并完整对账。旧KeyStore损失未证明，不为本轮验证读取旧加密凭据。若签名不兼容、隔离runner不生效、替身运行失败或恢复不成立，停止并如实报阻塞。

## 交付状态与下一步

- J1-v2：已验收（源码/构建），DEVICE_ACCEPTED=false。
- J2-v2：限定勘误采用并由本报告补正；设备关卡仍未通过。
- **J2-v3 / DSH-DeepSeek**：独占MuMu，运行这四项仪器并完成输入和恢复对账。报告写 `docs/qa/2026-10/2026-10-01-next-j2-v3-room-mumu-acceptance.md`。
- **W1-v1 / ZCode-GLM**：只在新E目录准备WebDAV受控服务、不可覆盖的分会话日志、合法中性ZIP/JSON夹具和离线自检；不ADB、不改产品，报告写 `docs/coordination/dispatches/2026-10/2026-10-01-next-w1-webdav-fixtures.md`。需说明未来UI验收的已保存配置路径及数据隔离依赖，未准备好设备沙箱时不能开展UI导入。
- 两任务独立并行；J2-v3即使4/4通过也不等于整个WebDAV/恢复关卡通过。联合构建+完整MuMu关卡仍1/2=50%；A–E5/5=100%、R1–R3实现3/3=100%，全产品分母未建立。
