# Root 当前阅读闭环阶段收尾

## 任务编号/版本

CRA-ROOT-READER-STAGE-20261015-v9，本批实施、核验与外层恢复已收尾；联合验收未通过。按用户要求已停等安排，不派外部提示词、不自动进入下一阶段。

## 输入指纹

工作根为 `E:/creation-reading-assistant/root-runtime-device-20261015`。v7强冻结SHA为 c0cab011e3b20689da3e7927f617c039350c68b3bf05229b9a27054548e5f64a。v9保持1023源码、65验收工件，只改ProfileHomeScreen的现有笔记入口与D诊断的PID日志作用域；验收工件复用v7。

v9实际构建宿主与测试双APK，snapshot/wrap/独立verify均PASS：`freeze-v9/attempt1/freeze.json` SHA b7583c782fbf4c3b86bf0f5fadf066ab51d008e14892f26030486482c90ba8b3；host SHA 118d39467543ea442291aa8486126632789bb1f3d82f405b030de4156ae23cf3；test SHA 4d779518bd351f979b0a3b302cd00f017baec3c98a711ae52073d1b53bd8f6f8；source manifest SHA 505c35235e2ecafc8cc248870753e2ca3c0b06e9adffc02e74debb658ab18dc5；acceptance manifest SHA 9f768fad00b76b20a3c08167a756b8882497fd572dafcb23c32e2c4f3d925a01。547个main/config路径仅ProfileHomeScreen改变，旧宿主未复用。

ProfileHomeScreen源SHA：2b8b3d324eb1a27f3e98514d19df0baff646e0a927315b5f0359d9f6543e3ece；候选SHA：0db90fb71693e4a9db5bd0e7607409fac172118cd644228578f623becfbe1f60。D探针候选SHA：317be29bebd85627fa1cf918f21d18d8ee5e8a3a9018a0ccd09bae51ca0fa044。

## 所有权与改动

Root唯一负责集成、设备和现态；独立候选修复与唯一持锁构建分开。实现/构建/缓存/原始证据均在E盘。当前首次必要产品改动仅增加“阅读笔记”菜单，沿已有NOTES路由；不增加业务功能，不以测试直跳隐藏路由代替用户可达性。D仓库产品代码未回灌，无暂存、提交、push或发布。

## 环境

登记隔离MuMu实例1，serial 127.0.0.1:16416，SDK32，Root唯一owner；实例0保持关闭。原plain qahost/.test不卸载。新增j1persistv2/.test初始不存在，所有数据均为本轮隔离测试资产。MuMu不等于实体手机或MIUI验收。

## 执行与结果

- 主机运行窗口PID、超时、失败恢复、完整工具清单及受保护文件收回的五个测试文件69/69通过；v7静态身份/清单2/2通过。原始日志见E工作根 `evidence/pytest-harvest-integration-v6-final/pytest-five.log`。
- v7真实NavHost迟到dispose测试1/1通过。原始trace明确旧保存均早于新保存，旧route最后dispose未追加保存；这只证明该生命周期路径，与原88项IO乱序JVM证据分开。
- v6短末项8步骤仪器通过，原始12列行复核确认S/R偏移3840、S2/Exit/Cold偏移1772，marker0049变0023，时间和数值进程冷序成立。额外核查发现启动时间文字字段是ps usage、冷PID空输出未归档，已修为/proc+CLK_TCK来源和原始force-stop/pidof回执；v7新增身份反例测试1/1通过，最终完整链待v9复验。
- v7 ABC实际5/6通过；A2未通过。根因为“我的”缺少现有阅读笔记入口，仪器尚未进入临时查看来源，不将其误报EPUB定位失败。
- v7内层恢复未通过：旧备份暂存被纳入下一基线，深层路径导致两个恢复推送失败。保留原失败，未改写成通过。
- D v7有真实anchor但诊断混用了旧PID的max seq而过滤本轮日志。修复仅加当前PID作用域，未放松pending/marker保护、未伪造observe；v9实测结论见下文。

v9实际追加结果：

- 新APK对现场安装，v9静态身份和工具清单2/2通过；69项功能回归按相同主机实现复用，区分其v6静态绑定输入。
- ABC六项原始仪器6/6通过，严格消费5/6。A2用户入口、临时来源查阅、跨章marker、返回普通阅读及后续位移均已执行；但 `returned_to_ordinary_reader` 相对冻结点的 `last_read_at`、`updated_at` 改变，完整12列冻结门禁不通过。未放松该门禁或改成通过。
- ABC-v9初次因新安装外部证据根未创建而在调用前拒绝，内层恢复通过；随后D初始化该任务证据根并经真实库存预检后重试。retry1内层恢复仍有两项新增文件未清理：目标测试包自己的 `shared_prefs/sync_config.xml`、`webdav_config.xml`。因此整链不通过，即使六项仪器通过也不签联合PASS。
- v9 D仅限受控MuMu窗口实验：实际root从1080×1920到2160×3840，真实anchor index4/1828，之后observe index2/1214；该observe日志的moved是在处理本次坐标之前采样。初始/重测量后完整行offset8019，退出后3532；marker2不可见，测试为inconclusive，不能外推一般设备根因。wm/rotation恢复，readerMode即时StateFlow回读为false（可能异步回读，尚无后续证明），Native总恢复门禁失败。外层恢复结果见恢复段，不覆盖这个原始失败。
- v9短末项8/8通过，Root独立复核5个时点的完整12列行、20/30/40原始dump SHA/非空marker/实时PID，以及ISO启动时间、/proc stat、CLK_TCK与毫秒值一致；冷前force-stop rc0、pidof rc1且原始stdout/stderr为空已归档，旧证据时刻＜旧PID消失采样＜新进程启动。见 `device/resume-v9/clamp/root-review.json`。
- v9真实SAF创建导出、结束进程后的冷读、删除与权限恢复3/3通过。清理结果确认目标URI不存在、对象已删除、权限释放，grantsBefore/After均为空。原12项清理单测及旧分享取消证据按未变实现复用，不冒充本次分享实测或发送行为。

## 原始证据

- v7 ABC：E工作根 `logs/abc-v7-final.json`、`device/resume-v7/abc/local/harvest/ROOTABCV7_20261015/`。
- v6短末项完整收回：`device/resume-v6/clamp/final-harvest/`，tar逐文件size/SHA核同，Native完整结果和UI XML另已提取。
- F真实trace：`device/resume-v6/probes/f-lifecycle/system-out-trace.txt`。
- D失败及原始同PID日志：`device/resume-v7/probes/d-expanded-endpoint/`。
- v9严格ABC与内层恢复失败：`logs/abc-v9-retry1-final.json`；逐方法instrument/receipt/12列行在 `device/resume-v9/abc-retry1/local/harvest/ROOTABCV9R1_20261015/`。
- v9 D实际实验和失败恢复：`device/resume-v9/probes/d-expanded-endpoint/`。
- v9短末项逐文件核同tar、Native结果、原始回执及Root独立复核：`device/resume-v9/clamp/final-harvest/`、`device/resume-v9/clamp/root-review.json`。
- v9 SAF：`device/resume-v9/saf/results.json`、`after-cleanup/task35-restoration-ROOTSAF20261015V9.json`；保留原始instrument和命令回执。

## 未覆盖项

全产品分母未核定。X整机中断、实体手机/MIUI、正式签名升级、R8最终运行、发布和桌面均不得宣称通过。当前阶段以同一v9候选的受影响阅读/冷进程证据与安全恢复为退出点，不自动启动下一轮产品任务。

## 恢复与副作用

为消除嵌套旧暂存，Root已逐包取证后卸载新j1测试包对：宿主156文件、数据库相关文件和APK已完整归档逐SHA验证，两个包均恢复初始absent。原plain两包保持安装。

另将前一轮667个tmp新增文件归档到E并逐文件size/SHA核对后，仅删除确属本轮的7个精确目录；整个 `/data/local/tmp` 库存与原616文件一致。证据为 `device/resume-v7/reset-forensics/` 和 `device/resume-v7/tmp-recovery/result.json`。

末次v9外层恢复已完成，与ABC/D的内层失败严格分开：

- v9两个新增测试包逐包卸载前取证；宿主99文件（含DB/WAL/SHM等实际存在成员）与外部证据全部归档逐size/SHA核对，新双APK另存且匹配本轮冻结。两包卸载Success，pm精确无项、私有目录及外部目录均缺失，恢复原absent状态。
- 末次143个本轮tmp文件另行归档核同，只删除4个精确自有目录；tmp完整库存再次回到原616文件。
- 原plain两包71私有文件的size/SHA/ns-mtime/mode/owner/context、数据库组、原APK对和820外部原文件全部核同；系统设置、shell身份、映射与Home核对。唯一初始差异是夜间模式yes，已按基线恢复no并fresh readback；原失败对账另存，71/820与其余设置已充分核同的证据复用，不为只改night重复全库读回。
- MuMu Manager独立确认实例1已关回原关闭状态，实例0始终关闭，登记创建标识一致；不把ADB离线条目当仍运行，也未kill-server或操作其他设备。

末次证据：`device/resume-v9/final-uninstall-forensics/result.json`、`final-tmp-recovery/result.json`、`final-protection-result.json`、`night-restore-receipt.json`、`host-final-state.json`。D的Native `FAILED_RESTORATION` 与ABC内层两文件未清理继续保留，不被外层安全恢复改写为通过。

## 交付状态与下一步

本批收尾结束，已停等用户安排；当前无运行中的设备/构建/外部任务。本批成功交付：现有阅读笔记入口已接通、新宿主/测试APK已冻结、真实短末项及SAF证据闭环、原始资产恢复核对。未获V0联合放行，不能称产品完成。

仍未过的三处边界：A2返回普通阅读时两时间列与完整行冻结合同不一致；D受控窗口实验不满足marker及Native恢复门禁；ABC内层恢复未清两份新配置。后续先处理这些边界，现不派任务、不放松门禁、不重复充分且未变的证据。

阶段方向复核：保留原生Android稳定阅读、笔记/取材回源与轻量导出方向；此次只补已有隐藏笔记页的可见入口。当前应先收口未过合同，而非扩展新功能、桌面或发布。D HEAD仍为e39a78da9c62f02614207cffb36969a32492eaea，index为空；产品改动保留于E候选，未回灌/暂存/提交/push/发布。
