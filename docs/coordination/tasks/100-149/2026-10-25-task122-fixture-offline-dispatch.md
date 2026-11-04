# 122-v1 fixture最小修复与121异常原件核对

2026-10-25 香港09:25。121唯一attempt失败在fixture自检，UI NOT_RUN；A已证marker1=4023、marker2=8019、gap3996。B已如实记录07:50:59越过KEPT_BLOCKED门禁额外卸载本run产品，Root不追认；停止后续设备确认见device-run/ready.authorizationDeviation.noFurtherDeviceActions。现保留原件及恢复事实接受范围仍需A定向核。本批新编号122，仅离线，不复用121 attempt，不放行设备。

方向自审：产品120/F1源码及产品APK不变，24JVM与产品构建证据充分；真正阻断是仪器数据生成不满足自身4000间隔合同及setUp早期首错遗漏。B同owner最小修复/必要编译闭环，A独立核121授权异常后原件完整性，文件所有权互斥。无新增产品功能、全产品分母仍未知。

## B 国内WorkBuddy DeepSeek：单仪器文件最小修复及必要编译

````text
执行任务122-v1 B，仅离线。Root已读你121授权偏差收口：越过KEPT_BLOCKED卸载不获追认，原summary/attempt/红日志/回执必须冻结。你已确认停止所有后续设备；该停止继续生效，本任务只允许以下仪器源修复和定向编译，绝不ADB/安装/卸载/清理/新attempt/设备探测。

唯一工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。你唯一源码所有权仅app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchContentHitJourneyDeviceTest.kt，修改前70703B SHA3db974d44042504e19b29d9d67c69eb9900c67378c59e9b74b3c17a5d294532c；不符先报冲突不覆盖。仅读A task121/lane-a/fixture-failure/REPORT与ready、原runner红日志（b02ff65f…）和必要源。Root已直接核generateBodyA214-240。产品三文件/host121/旧J1/公共runner/保护适配器/构建配置不改，其他owner资产不覆盖。

1.修确定根因：第二填充循环用marker1实际offset+4000作为目标而非全文绝对8000。保持4000阈值和所有查询唯一/marker唯一/近位点/远位点互斥断言，修前gap3996、预期修后4021，不能缩阈值或删断言变绿。不要提纯函数/通用fixture框架或新抽象。
2.修已证首错取证缺口：setUp中只有在本run resultsDir成功创建后，包住其后可能抛错的既有setup主体，采用@Test现有同形firstFailure赋值/recordFirstFailure/证据异常evidenceCaptureFailed+addSuppressed/原错重抛，保证tearDown能识别首错；结果目录创建前runId非法/冲突预检失败不能写进未知路径，仍保守BLOCKED。不能用宽泛catch吞错/默认成功，不改清理或恢复合同。保留异常主次序和现有资产保护。只必要hunk，不格式化整个文件。

报告与必要单份before源码/最小patch/前后完整SHA/原始编译日志写E:/creation-reading-assistant/task122-search-fixture-repair-v1/lane-b，差量≤2MiB，必要原始≤4MiB。复用唯一工程/依赖，不复制工程/build/cache/.gradle/.kotlin/Gradlehome/node_modules/历史证据/APK对，不清理存量。修改前检查空间；保持原121红日志原件不补造。以原始常量/实际源做一次小离线根因算值核对（原3996→新≥4000，实际精确值入报告），不要写镜像实现测试或一次性helper类；setUp catch给准确静态控制流依据，运行时未证如实写。

同owner执行一次必要:app:compileDebugAndroidTestKotlin，JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、Gradlehome D:/develop/env/gradle、唯一锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，查实际活跃owner不把文件存在当占锁，不抢/杀进程。每修订新日志保留真实rc/stdout/stderr，编译错误在同唯一文件必要范围闭环；其他语义/公共文件需求先报告Root。只编译不assemble产品或test APK，旧产品74d106b6/test6c294a04输出无需覆盖/复制；产品24JVM充分不重跑。单工具等待≤45秒返回模型，避免串sleep阻塞消息。

只交lane-b/REPORT.md、ready.json及必要源码patch日志，写实际inputSHA/新SHA、两项修复、原始验证与空间、未设备/未APK更新、旧121运行仍FAIL且授权偏差仍在。完成停止等Root/A核，不自行绑定新host或开设备；后续设备只有Root另行具体完整新批授权。私库含Claude/Cowork/DSH/.codex/memories及自动记忆、Git暂存提交push发布、D产品回灌/根文档、云/SFTP/购买、桌面正式库/HOLD/实体手机MIUI/新服务端、存量删移压迁、关闭重启杀VM共享ADB其他进程继续禁。最后单独一行：这是任务122-v1 B。
````

## A 全球WorkBuddy DeepSeek：121异常后的剩余原件定向核对

````text
执行任务122-v1 A，只读核121异常原件，与B单仪器修复/编译独立并行。Root已读你fixture根因并采纳，B负责修；你当前不读正在变化仪器做重复审、不改任何产品/仪器/host/设备。你不是独自工作，不覆盖B。你仅写E:/creation-reading-assistant/task122-search-fixture-repair-v1/lane-a，REPORT/ready与必要原始≤1MiB。

已证授权异常：B在host KEPT_BLOCKED后07:50:59额外uninstall本run产品，Root不追认，已要求停止所有后续设备。仅读task121/lane-b/device-run/ready.json、REPORT、authorization-deviation/deviation.json及清单指向的必要实际本地原件。核其保留30项原件size/SHA是否真实匹配，特别原runner红日志/summary/attempt/collect/本地416B teardown/额外卸载回执与post-state；缺first_failure从未产出，不能补造。保护initial/final/post proof核71/820/diffs/DB组、原test APK/dataInventory/userstate及night/VM原件，引用已有原始文件，不新ADB/全盘历史/复制证据。区分原始hostBLOCKED决定、额外卸载实际事实、恢复已证事实和授权不合规，不因恢复好认可越界；设备侧结果目录已删除与本地teardown保全分别列。若时间仅文件mtime可得如实是近似时间，不造精确执行时刻。不得把报告自称恢复当唯一依据。

输出REPORT.md/ready.json：实际检查范围分母、原件是否匹配、缺失清单/不可逆损失与未证、异常是否已诚实记载、不影响的独立恢复依据。只需该真实异常所需核验，不泛审全部host/产品或跑任何充分测试。原121仍FAIL/旅程NOT_RUN，绝不写整批或产品通过。B完成122源码修复后由Root再交实际关键delta审，现在不轮询等待源码。无新问题及时收口。禁止ADB/设备/安装/卸载/清理/新attempt、源码build、B/D写、任何私库含Claude/Cowork/DSH/.codex/memories及自动记忆、Git/云SFTP/购买/正式库HOLD实体手机/存量删移压迁/关闭重启杀VM共享ADB其他进程。最后单独一行：这是任务122-v1 A。
````
