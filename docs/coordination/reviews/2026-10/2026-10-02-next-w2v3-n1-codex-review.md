# W2-v3 / N1 独立核查与里程碑复盘（2026-10-02）

## 任务编号/版本

Codex-review-W2v3-N1-v1。用户“都完成了”触发证据核查与排期。code-review/verification-before-completion；N1由独立android_reviewer只读复核，Root核原始交付及W2，不以51项测试通过替代需求审查。

## 输入指纹

HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。J1-v3六冻结SHA6/6重新实算；W2 APK SHA `0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594` 未变。

N1候选从J1-v3取源，四改两增仅六授权路径。Root排除build/.gradle/.kotlin/.git实数父876、候选878、其他872文件一致；与报告的874/876/870口径不同，采用Root清单，不因两文件算术差异重派格式任务。六候选完整SHA全部与原报告一致，补丁SHA `7f2c1f9915c9de1497c2ef1f6a48d7ffe0ebdb05771049009ebc13415cdbdbcf`成立。

## 所有权与改动

Root仅写本报告、现有current/项目页/TODO/规划有关段落/skill日志及自有E审查结果。独立审查者只读，无产品写入、构建或设备操作。Root只读ADB，不安装、不启宿主、不改设置。旧报告、工件及源码保留。五文档编辑前保全[回退副本](<E:/creation-reading-assistant/w2v3-n1-codex-review-20261002/before>)并核编辑前字节一致；不操作原仓库、Claude独立库或Git index。

## 环境

Windows/Python3.13；MuMu当前adb devices确认`127.0.0.1:16416`连接，product=e3q/model=SM_S9280；1080×1920、density280。非实体手机/MIUI。外部W2自报Antigravity/Gemini，未独立核应用界面；N1execution含23:4x/0x:xx占位时间，不能作精确时间依据，采用原始XML/日志与Root当前取证。

## 执行与结果

### Standards

限定源码所有权符合：六路径、父候选未改、未见独立规范违例，0项。外部证据的报文头/清理记录及时间精度在下文补正，不要求仅格式返修。

### Spec：W2-v3已限定验收

追加会话`20261001-235856-523-w2_v3_zip_app_get`有两PROPFIND、seq3 okhttp/4.12.0 ZIP GET200，精确一次编码路径`/dav/backup-20261001-%E6%B5%8B%E8%AF%95%20%E7%A9%BA%E6%A0%BC.zip`；seq4 Probe-404-Test错误文件名404。发送266字节及SHA `aec6d4ff8f6114b8b82d858be193cdc527817c80b34a1372f83c879d376aaecf`与实际夹具一致。服务端dump状态行+body也逐字节一致，**dump无响应头且由服务生成，不能称完整线路抓包**，不外推客户端保存文件SHA。

UI扫描列表、实际导入确认和请求对应；七PNG尺寸1080×1920。最终XML只有正常列表，没有完成Toast，不采用“业务落库再次通过”的推断；任务仅补精确GET，旧进度合同另行复用。原报告Case编号描述混淆，不据其重新解释既有六类。结合此前充分且输入未变的证据，W2七类 **7/7=100%**，既定构建/Room+WebDAV隔离MuMu关卡 **2/2=100%**。这关闭既定M1验收关卡，永久源码合流与后续N1新门禁另计。

### Spec：N1-v1返修，3项P1/2项P2

原始XML两套件51实际testcase、0failure/error/skip；最终log test任务实际执行，BUILD SUCCESSFUL。两负向对照分别确2失败且BUILD FAILED，生产文件回到报告SHA。Root本轮解析与静态审查，未重跑JVM；这些材料只证明已测契约。

1. **P1 同ZIP重试跳过未完成正文并清状态**：LocalZipBackup.kt 226–232/299–313；JsonBridge.kt 392–397/SyncMergePolicy.kt 23。首次DB提交后正文未复制，重试相同revision/updated_at被严格大于策略判为跳过，路径进入skippedBookContentPaths；正文仍旧/缺失，但写files_copied并clear。新增retry测试用relaxed JsonBridge，未覆盖真实第二次merge结果。修复须持久待修目标、验证字节并区分真正较新本地数据，不能关闭全局版本保护。
2. **P1 另一备份覆盖未完成归属**：LocalZipRestoreStateStore.kt 101–118 begin无条件覆盖，sourceLabel无实际验证。A中断后B不含A书籍或合法空备份，B成功清状态却保留A混合数据。须以内容指纹/操作身份守护A待修集合，不允许B自身成功解除A保护。
3. **P1 并发导入/导出绕过状态**：LocalZipBackup.kt 95–99/226–232，ProfileViewModel.kt 392–408，StateStore.kt 199–215。多个IO launch无服务互斥，B完成可clear仍运行A；导出检查后导入开始也可读混合内容。ZIP整操作共享互斥、推进/清除核operationId、独立临时文件；仅UI禁用不够。
4. **P2 半写staging归属仍缺**：LocalZipBackup.kt 337–360/124–132/671–680。先写staging，完整且rename失败才journalAdd；kill半写留下无归属文件。重试完成清新状态后旧半写文件仍可导出。须创建前可靠归属并区分未校验/已校验，复用journal，只处理已证归属，未知后缀资产保留。
5. **P2 清理false与持久摘要遗漏**：LocalZipBackup.kt 237–254。deleteRecursively=false被忽略；抛异常时先recordFailure后addSuppressed，磁盘摘要缺清理异常。检查返回值、保全主异常、汇总后持久摘要，补两种清理失败测试。

附带文案：started覆盖“DB已提交但状态尚未推进”窗口，不能确定写“数据库尚未提交”，改为未知/可能未完成。上述为静态可达执行路径，未冒充本轮真实kill复现；真实kill/UI/SAF后续验收仍待源码审查通过。

### 里程碑复盘

保留本地优先双产品目标、数据安全优先、互斥所有权及历史桌面功能不重造。W2完成退出队列，复用旧原件不反复重测。N1-v1不集成，先v2在新E副本补五项根因，合并实际merge与交错/半写/清理测试；保留既有机制而非架构重写。并行M0-scope-v3只读核已授权旅程及分子分母，复用D1调用链归属，解决全项目度量长期不清问题，不制造新增功能。新联合冻结/构建/设备由单一owner在N1语义通过后进行；Git按新清单当次许可，不继承142路径授权。

## 原始证据

- [Root核查JSON与实际XML/指纹](<E:/creation-reading-assistant/w2v3-n1-codex-review-20261002/verification.json>)、[核查脚本](<E:/creation-reading-assistant/w2v3-n1-codex-review-20261002/audit.py>)。
- [当前设备后账](<E:/creation-reading-assistant/w2v3-n1-codex-review-20261002/device-verification.json>)及[逐命令stdout/stderr](<E:/creation-reading-assistant/w2v3-n1-codex-review-20261002/device-readback>)。
- [W2原报告](../../../qa/2026-10/2026-10-01-next-w2-v3-webdav-zip-app-get.md)、[W2会话](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance-v3/logs/sessions>)、[W2 UI](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance-v3/evidence>)。
- [N1原报告](../../dispatches/2026-10/2026-10-01-next-n1-repair-v1.md)、[N1原XML](<E:/creation-reading-assistant/next-round-n1-repair-v1/results>)、[N1日志](<E:/creation-reading-assistant/next-round-n1-repair-v1/logs>)、[N1候选](<E:/creation-reading-assistant/next-round-n1-repair-v1/android>)。
- [前轮采纳边界](2026-10-01-next-w2v2-d1-codex-review.md)、[总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)、[当前交接](../../../handoff/current.md)。

## 未覆盖项

N1五项修复、真实kill/存储故障/UI/SAF重试、永久候选集成与新构建/设备验收未完成；全产品分母待核。旧密钥、实体手机/MIUI、实际LAN兼容、TalkBack/R8正式运行、正式签名升级/F不外推。原始51项通过不是新缺口的测试覆盖，不因此关闭N1。

## 恢复与副作用

外部pre_baseline有71SHA字典，post_commands记录卸载/七设置/转发原结果，但APK与71SHA后账原输出未保存；cleanup脚本也以未记录adb调用生成summary。因此Root另做当前只读回读，而非再派纯报告返修。

Root取证UTC 2026-10-01T16:59:37.922555+00:00（本地2026-10-02）：v3前账与旧71SHA清单相同，当前71/71完全匹配，modified/missing/extra空；原APK对SHA与UID10083/10084相同，七设置匹配，reverse/forward空，临时包不存在、原宿主未运行、PID40312及18082监听不存在。此证据证明当前约定资产恢复，不追溯外部清理过程、KeyStore或绝对零副作用。

## 交付状态与下一步

W2-v3限定已验收；N1-v1已交付但返修，Standards0、Spec5（3P1/2P2）。进度：既定W2七类7/7=100%、既定联合关卡2/2=100%、A–E5/5=100%、R1–R3实现3/3=100%；N1不继承以上状态，全产品分母未核定。

下一并行N1-repair-v2最小修复和M0-scope-v3只读范围核定。每项任意具备能力的空闲窗口，单一owner，以任务编号/指纹/产出路径接手；完成后由Codex核查，不自动通信或开启定时任务。

定向文档验证：21文件/142本地链接，0错误/0提醒；TODO其他项目后缀和skill既有历史原字节保留，HEAD未变、暂存区空，无产品/设备写入。
