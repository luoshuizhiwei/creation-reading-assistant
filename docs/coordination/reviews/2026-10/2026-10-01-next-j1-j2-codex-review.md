# J1/J2 独立核查与返修边界

## 任务编号/版本

Codex-review-J2-v1，2026-10-01。来源：用户本轮“上面分配的任务它们完成了”。结论：J1 构建关卡保持已验收；J2 设备关卡需返修。未修改产品或执行设备操作。

## 输入指纹

- 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`；暂存区空。
- 活动输入：[J1 六文件清单](<E:/creation-reading-assistant/next-round-joint-20261001/source-manifest-j1.csv>)；本轮实算六文件 SHA 全部一致。
- 五 APK 本轮重新实算；完整值见 [verification.json](<E:/creation-reading-assistant/next-j1-j2-codex-review-20261001/verification.json>)。
- qahost 宿主：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`。
- qahost 测试包：`0deb20074495d831a387a41a7c7d2f51ed8fb7897aeed68fb2206b89d0fa28cb`。
- J2 报告写的两个完整 SHA 与实算值不一致；只有短前缀一致。没有找到支持报告所称“实装逐位匹配”的原始完整回读日志。不能据此认定当时实际安装的输入正确或错误。

## 所有权与改动

Codex 仅写本报告、current.md、Codex 项目页/TODO/skill 使用记录和 E 盘只读审计脚本/输出。外部原报告、原日志、冷备、源码和 freeze 均保留。修改前副本在 [before](<E:/creation-reading-assistant/next-j1-j2-codex-review-20261001/before>)。

## 环境

Windows；原始资料声称 MuMu Android 12 / `127.0.0.1:16416`。本轮未连接设备，serial 不是新批次确认值。核查现存 XML、日志、截图、源码和本地 SHA，不重跑 Gradle，不将 MuMu 称实体手机。

## 执行与结果

### Standards

1. **测试恢复边界未满足。** `SyncRepositoryPersistenceTest.setUp()` 使用 qahost 原 Context 写真实 `sync_config`，`tearDown()` clear 不能恢复原配对。`DeviceInfoProvider` 还读取/创建 `cra_device_identity`；`SecurePrefs` 使用 AndroidKeyStore 默认主密钥。J2 声称卸载后回灌文件，但没有密钥/凭据可解密恢复证据。`SecurePrefs.open()` 解密创建失败会删 prefs 后重建；因此不能以文件回灌或书架正常证明凭据恢复。是否已丢失旧密钥或配置，本轮没有运行证据可确认。
2. **报告与原始结果冲突。** 完整 APK SHA、R1/R2 用例数和安全/导入结论不一致；恢复表只展示截断哈希。要求保留旧报告，出更正报告；不得凭报告字段把失败关单。

### Spec

1. **Room 持久化覆盖未完成。** 原 instrumentation 日志：4 tests / 3 failures。两项初始 `readingProgressDao.upsert()` 报外键失败；源码确未先插 `books` 父行，`ReadingProgressEntity` 明确要求 `book_id → books.id`。pull→推进→push 项在第249行取 null 报 NPE，缺少父行与之相符，但日志没有该 pull 的 failedItems 明细；应先补合法夹具并断言 pull 成功，再判断产品路径。书籍用例通过只能支持该用例，不能替代其余三项或判定产品全部正确。
2. **WebDAV 安全/导入结论不受证据支持。** summary 内 `pass_userinfo=false`、`pass_cleartext=false`，各计1请求；`spec4_userinfo_blocked.xml/png` 显示 HTTP 200 成功，不能称拦截已通过，也不能直接断言生产 URL 校验失效（可能仍在用先前已保存设置，需核输入路径）。summary 的 ZIP `zip_get_found=false`，后续 `spec5_chinese_space_import_done.xml` 为“已导入0条；跳过1条（未匹配1）”，只支持某次解析/跳过提示，未证明匹配记录实际更新。最终 request JSONL 仅剩一条 JSON GET；reset 脚本会清空日志，缺少原 ZIP 请求路径记录。JSON 确认弹窗也不等于恢复完成。

两轴分别有2项发现；Standards 最重为恢复边界，Spec 最重为未完成的设备覆盖。上述均为限定关卡发现，无全产品通过结论。

### 可复用证据

- 本轮重数 JVM：287 XML，2673 tests，failures/errors/skipped 均0；R1=21、R2=24，J2 的14/18需更正。这是核查已有原始结果，未重新运行测试。
- HEAD405→PROPFIND207/Depth0、PUT409→MKCOL201→PUT201、401/403仅 HEAD 在 summary 中有请求明细，并有对应 UI XML。保留为有限行为证据；被验 APK 原始身份未核清，暂不能提升为冻结候选设备通过。
- 冷备原 APK 两个完整 SHA 与报告一致；冷备 data 共67文件、1,182,907,163 bytes（包含 cache，不等同持久文件验收分母），主 DB SHA 与报告冷备值一致。未找到全文件前后对账、完整实装恢复 SHA 和设置前后命令日志。

## 原始证据

- [本轮审计输出](<E:/creation-reading-assistant/next-j1-j2-codex-review-20261001/verification.json>) / [只读审计脚本](<E:/creation-reading-assistant/next-j1-j2-codex-review-20261001/audit.py>)。
- [J1 原报告](../../dispatches/2026-10/2026-10-01-next-j1-joint-build-and-freeze.md) / [J2 原报告](../../../qa/2026-10/2026-10-01-next-j2-joint-mumu-acceptance.md)。
- [仪器原日志](<E:/creation-reading-assistant/next-round-j2-acceptance/logs/instrumentation_persistence_test.log>) / [WebDAV summary](<E:/creation-reading-assistant/next-round-j2-acceptance/logs/acceptance_suite_summary.json>) / [最终请求日志](<E:/creation-reading-assistant/next-round-j2-acceptance/logs/webdav_requests.jsonl>)。
- [安全用例 UI XML](<E:/creation-reading-assistant/next-round-j2-acceptance/evidence/spec4_userinfo_blocked.xml>) / [导入 UI XML](<E:/creation-reading-assistant/next-round-j2-acceptance/evidence/spec5_chinese_space_import_done.xml>)。

## 未覆盖项

原被验 APK 身份、后续 ZIP 单重编码请求证据、有效记录更新/JSON恢复完成、真实 URL 输入是否用于请求、持久文件逐项恢复、密钥可用性、系统设置前后差异均未闭合。实体硬件/MIUI、R8实际运行、正式签名/升级、桌面/HOLD、N1原子性未纳入本轮。

## 恢复与副作用

本轮零设备操作。不能为取证再卸载 qahost、清空 prefs、覆盖冷备或自动触发可能自愈删配置的读取。下一设备批次前须审查可恢复隔离方案，优先独立测试上下文/存储命名并保持原宿主主密钥；原包恢复优先同签名覆盖安装，不以卸载回灌作为完整恢复方案。签名不兼容或密钥状态无法证明时停止设备变更，保留缺口。

## 交付状态与下一步

- 本核查已交付；J1-v1 构建已验收，J2-v1 返修，联合关卡1/2=50%。A–E限定恢复5/5=100%；全产品分母未建立，不能给全产品百分比。
- 准备 **J1-v2**：ZCode/GLM 在新 E 候选修 `SyncRepositoryPersistenceTest.kt` 的父行、失败断言和真实 prefs/identity 隔离；必要时仅允许 `app/build.gradle.kts` 的最小 androidTest 依赖 hunk，以隔离配置/设备身份提供者，保留真实 Room、SyncRepository 和网络序列化。单换 Context/改 prefs 名不能自动证明同 UID 的默认 KeyStore 别名隔离。构建最小测试包并给完整输入 SHA；原 freeze 不改。
- 准备 **J2-v2**：DSH/DeepSeek 只读原始材料，产出更正报告/证据覆盖表及恢复风险方案，不操作设备；与J1-v2可并行。
- 用户已报告本轮执行完成；没有独立桌面运行状态接口，下一窗口应先确认同任务没有仍运行的作业。两份返修交付并由Codex核查后，才派J2-v3独占MuMu重验受影响项。产品缺陷需要 SEAM REQUEST，不扩大源码范围；当前六文件未获新提交许可。
