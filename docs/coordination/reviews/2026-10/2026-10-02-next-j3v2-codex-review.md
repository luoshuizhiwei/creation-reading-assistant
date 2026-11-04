# J3-v2 Codex 核查：核心 MuMu 合同关闭，UI/SAF另计

## 任务编号/版本

`J3v2-Codex-review-v1`，2026-10-02。用户“完成了”后，Root核实源码/原始日志/工件/设备恢复，独立android_reviewer只读核驱动与协议。原[执行报告](../../../qa/2026-10/2026-10-02-next-j3-n1-fix-accept-v2.md)保留，下列补正为当前验收依据。

## 输入指纹

- HEAD `e39a78da9c62f02614207cffb36969a32492eaea`、暂存区空；未操作原损坏Git。
- 候选 `E:/creation-reading-assistant/next-round-j3-n1-fix-accept-v2/android`；相对J3-v1仅单驱动和v7测试变化，没有新增/删除输入。
- 驱动实算/最终freeze SHA `b39f3cb1ea29c186ca4b2eb1f8af08466a762134c66b7c0efee683a2b7ee69ef`；报告§2的12ac7175是旧构建前值，不是最终输入。
- v7 Test SHA `000b7c811abc1ac013d301203d52f6a4ef9c8161a641f6d76ee782573f01e8f3`；生产Backup SHA `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e`不变。八项输入七保持、一替换，runner/Profile/配置保持。
- 冻结宿主SHA `d99fe746e5bb5f726f313c62159e16a46aba0df8eb0a76bbbb4523d211321082`；测试SHA `ab1210969860b809c208c13fc3f4ceee2c433e2bbb65925885a2253a7875c758`。均与build/outputs和记录的安装APK一致；Root签名/manifest工具验证成立。

## 所有权与改动

Root只写本报告、交接/项目页/TODO/skill记录与独立E审查根；未改产品、测试驱动、脚本或旧报告，没有构建/安装/启动应用/修改设备。只读设备回查与源树核对不重复原场景。修改入口前原始副本在 [docs-before](<E:/creation-reading-assistant/j3v2-codex-review-20261002/docs-before>)。

## 环境

Root本轮 adb devices 与 Windows TCP/process重新核实：`127.0.0.1:16416` 对应 PID27632 `MuMuVMMHeadless`；guest硬件标识回读存档。Samsung型号是模拟值，不作为实体设备证据。Python3.13/JDK17/SDK34工具只读核查；外部执行报告自报DeepSeek V4.1 Flash，Root没有读外部账户配置。

## 执行与结果

- 原始定向JVM XML：Backup62、JsonBridge20、StateStore16，合计 **98/98**，failures/errors/skipped均0。编译失败日志和最终成功日志均保留；输入匹配，不重复运行。
- 原始设备txt实算27次调用：**21个普通方法全部通过**，其中准备/seed重复两次，所以有23次普通成功调用；**3个arm阶段真实终止**，另有首次路径转换导致的1次无效arm失败，随后清场重跑。源码24个@Test不是报告“22/22”，arm不计JUnit PASS。
- 三次信号、日志与新进程读回一致：s1 `17480→17551`、s2 `17865→17940`、s4 `18407→18480`。终止前PID与信号一致，信号年龄4469/3843/4098ms，强停后pidof空，instrument结束，新PID不同。
- 读回状态均db_committed/未完成，原正文SHA `a8c1efea…`，准确A义务SHA `c2b0974e…`，journal有对应rel与未校验staging。s4导入B后真实Room revision=3；s1重选A收敛。
- 五类equal/空指纹/legacy/accepted/strictlyNewer成立；s1实际ZIP解包检查JSON id/revision对真实DB、正文等于A，s3/s4明确因未完成状态拒绝导出。25份JSON包括旧无效arm现场，不全部当当前成功结果。
- 未保留正向ZIP与测试库的独立副本；本轮正向导出采用已核驱动中的解包强断言+匹配的原始JUnit通过+JSON哈希，不能伪称Root另读了ZIP文件。下一UI批保留实际导出文件供独立读回。

## Standards

原两项资产边界修正符合本轮合同：基线两个新包不存在，其余原包保留；forward/reverse不增删。Root当前回读进一步核实原资产与清理，见下。

仍有两项 **P2脚本门禁**：

1. `scripts/run_device.sh:71–90`仅打印timestamp_fresh，实际abort只看scenario/PID，未比较本轮arm runId或拒绝过旧/未来时间。应比较真实runId，硬判`0 <= age < window`及phase/stateStage。
2. `run_device.sh:30–38,94–103`普通阶段以adb exit替代JUnit结果；强停后PID仍存活只WARNING并返回0。应识别实际OK、拒绝FAIL/crash；强停命令失败/PID存活/未结束返回非零。

本轮三次实际日志及新进程断言已由Root核实，因此不推翻已成立的验收；脚本在再次当自动门禁使用前必须修正。Standards当前2P2。

## Spec

上轮5项主要问题已关闭：无副作用路径getter、accepted分段、无效arm失败、每场景目录/库隔离与强读回、固定正反导出和解包断言。独立只读reviewer没有发现需重开生产算法或重复五类场景的理由。

计数补正：24个方法＝21普通通过＋3预期强停；不写22/22。`.qahostn1`透传真实Application启动有新UID内DB/工作任务副作用，不能写绝对零副作用。驱动“只编译/全NOT RUN”注释、freeze准备阶段not_done和设备XML说法是过期描述；实际本轮用txt/JSON，无设备XML归档。

实体手机未做是未覆盖边界，不是用户已授权MuMu合同的失败原因。核心MuMu合同通过与真实UI/SAF未测分开记录，不把两者混成全部N1已验。

## 原始证据

- [Root verify.py](<E:/creation-reading-assistant/j3v2-codex-review-20261002/verify.py>) / [源码、XML、27调用、三信号、25JSON、APK核查](<E:/creation-reading-assistant/j3v2-codex-review-20261002/verification.json>)。
- [Root只读设备回查脚本](<E:/creation-reading-assistant/j3v2-codex-review-20261002/live_readback.py>) / [当前完整回查](<E:/creation-reading-assistant/j3v2-codex-review-20261002/live-readback.json>) / [MuMu宿主进程证据](<E:/creation-reading-assistant/j3v2-codex-review-20261002/mumu-host-identity.json>)；每条ADB原输出独立存档。
- [外部原始运行](<E:/creation-reading-assistant/next-round-j3-n1-fix-accept-v2/device-runs>) / [JSON](<E:/creation-reading-assistant/next-round-j3-n1-fix-accept-v2/evidence/evidence>) / [freeze](<E:/creation-reading-assistant/next-round-j3-n1-fix-accept-v2/freeze>)；Root核索引与实际字节SHA。

## 未覆盖项

真实Profile页面常驻提示、确认/SAF选择/取消/重试/导出用户链未测；内存prefs wrapper不证明真实身份持久化。整包DB+文件仍非原子，不能说没有真实kill——本轮确有三个真实kill，覆盖的是明确复制接缝。实体硬件/MIUI、存储故障其他接缝、正式签名/升级、桌面/HOLD/F仍开放或暂缓。

## 恢复与副作用

外部前后53文件SHA列表逐字节一致。外部settings-after缺stay_on/proxy_host，且未保存完整最终原APK/mapping原输出；Root本轮**当前只读回查补齐**：

- qahost files/databases/shared_prefs **53/53**逐路径SHA及路径集合一致，无新增/删除；不读取凭证内容。
- 三个既有包及各自原APK SHA一致；新宿主/test包均不存在，两个包的私有/外部路径均不存在。
- size1080×1920、density280、旋转/自动旋转、超时、night、stay_on=0、proxy/proxy_host共9项匹配基线；forward/reverse均空，与基线相同。
- Root不修改任何设置，不重装/启动原包；当前回查不冒充外部当时未归档的原始日志。
- 53是本批明确持久范围，不沿用旧71/65分母；旧历史KeyStore边界不由此关闭。本批截图/窄手机布局尚未验，默认1080/density280约617dp偏宽。

## 交付状态与下一步

本批N1核心合同 **7/7=100%（五场景＋导出正反＋恢复）**关闭，98JVM通过、24阶段全部覆盖。与G1已验四旅程＋恢复构成 **两批限定MuMu合同2/2=100%**；明确替换旧含混的“N1/G1完整验收”标签，不将UI/SAF、全产品或发布纳入这两个分母。

里程碑复盘：保持阅读稳定/数据安全主线，生产修复与核心设备证据已充分，不再重派。剩余关键路径缩为真实UI/SAF和最终集成清单；实体硬件不再当当前设备阻塞。D-WIP、原仓库/桌面/HOLD保全，不沿用旧提交授权。

下一两项有实际独立范围：
1. `N1-ui-accept-v1`：新E/UI专用新UID，产品字节保持，只允许后缀单hunk与新测试类；核真实Profile持久提示、确认/取消、SAF重选原A和实际UI导出、手机宽度截图及完整恢复。准备与构建可和脚本工作并行；正式设备批前采用已补强且经离线正反例核验的执行门禁。
2. `N1-runner-hardening-v1`：独立E脚本副本、无设备/构建，修两P2与过期协议描述，用本批原日志/信号做离线正反检查。报告给Root并交UI执行者使用，不修改已验冻结或Android。

功能归属/交付覆盖仍11/23≈47.8%；本批验收不自动改变功能实现归属分母。全产品分母未核定。未永久回灌、暂存或提交。

入口与报告的相称验证见 [document-verification.json](<E:/creation-reading-assistant/j3v2-codex-review-20261002/document-verification.json>)：显式21文件及本地链接，无错误/告警。七条过期N1/G1返修行退出启动表，完整原文保留在已链接快照；TODO其他项目原始后缀、skill日志原始前缀逐字节保留。
