# 63单次模拟器验收与恢复结果

## 任务编号/版本

63 Root-r1入口的一次设备attempt，用户2026-10-18明确“启动”。run `CRA63_DE_JOINT_20261018_125028`，12:50:28开始、12:57:12入口结束。结果FAIL；没有重试或现场改码重绑。[本轮结果](<E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/device-run-20261018_125028/result.json>)。

## 输入指纹

冻结`31f0f859…90dc`，新host `14e573ab…82aa`、test `d5cdd05d…d21d7a`；Root入口`eae871b6…c969`与95绑定`e563105b…e722`，启动前实算匹配并由实际入口解析。完整SHA/命令在本轮`launch.json`及attempt的`attempt-state.json`。

## 所有权与改动

本会话Root唯一设备owner，初查无其他相关执行者，既存ADB server保留。未修改运行中源码、工具、冻结或APK，没有新构建。只运行一个新run/一次attempt，按既有恢复合同完成收尾。自动化未恢复。

## 环境

隔离MuMu实例1，fresh serial `127.0.0.1:16416`；实例0未启动。原两个实例均关闭，原保护包/原APK对/完整DB组、私有及外部文件、tmp、设置按本轮快照实核，未把历史71/820/115当作基线。本轮实际仍为71私有/820外部，其中tmp616。

## 执行与结果

启动、保护基线检查（diffs空）、安装新APK对均通过。ABC六个仪器方法全部原始`rc=0`且`OK (1 test)`；严格采集/消费者仅4项PASS、2项BLOCKED，联合入口按合同FAIL后进入恢复。

两项BLOCKED：

- `task34_epubNormalReadingPositionIsStableAndSavesOnActiveNavigation`
- `task34_unreadableSourceWithPreviewShowsRealErrorAndKeepsProgress`

直接拒绝点均为`uuid.pull_ok`：设备已产JSON，但`adb pull`无法创建Windows本地目标，报`Not a directory`。目标路径分别268、261字符，父目录实际存在；另一条同类259字符路径采集成功。此模式与Windows长路径兼容问题吻合，是有现场支持的首要假设，尚未做短路径对照，不扩称已定位底层API原因。

两份失败JSON在同run的两个retained harvest路径中保留，随后按恢复合同归档为`owned-tmp.tar`。Root在内存读取tar，不解包/回填消费目录，逐文件集合、size与SHA核同（2603/3850字节）；tar SHA `0a6a817e60191f12967e7a0cc87e0ea1c16cc5138db61b909fd1ae1b1584ec31`。这是同run失败留证，不把原BLOCKED改成PASS。

clamp、SAF、Room4、DE及总消费者均NOT_RUN；不是业务断言失败的证据，也不是完整功能链通过。

## 原始证据

attempt：`E:/creation-reading-assistant/task63-common-device-entry-v1/attempts/attempt_v14_root_20261018_125028`。

关键文件：`attempt-state.json`、`a/abc-entry-result.json`、`logs/abc-raw.log`、`h/CRA63_DE_JOINT_20261018_125028/invoke-*.json`及instrumentation原始输出、`phase_scratch/abc_execution.out.json`。恢复证据位于`device_recovery/`的前后保护快照、final-protection-result、final-tmp-recovery-v6、final-uninstall-forensics、host原始/最终状态。控制台与Root核对结果在`root-review/device-run-20261018_125028/`。

## 未覆盖项

本次模拟器联合FAIL，后续阶段未执行，无新Room持久化/SAF/整链验收。全产品分母未知，不报全产品百分比，不称实体手机/MIUI通过。数据恢复结论只覆盖本次记录的合同范围，不等同模拟器整个磁盘逐字节回滚。

## 恢复与副作用

五步恢复均报告通过：候选包卸载前归档、tmp精确清理、设置恢复、最终保护复核、实例关闭。Root额外核对：

- 前后71私有文件与820外部文件路径/size/SHA清单相同；最终`ok=true、diffs=[]`。
- 本轮tmp616→616；新增2个harvest文件在删除前经tar集合/size/SHA及tarSHA核验，原文件保留。
- 两个候选包的卸载前归档SHA匹配，卸载成功，最终包/私有/外部不存在证明齐全；原保护包/APK/设置通过最终检查。
- 两实例回到原关闭状态，实例0未动。host-final-state虽ok=true，但当时ADB仍列本run serial为offline；Root只定向`adb disconnect 127.0.0.1:16416`，不停止既存ADB server，随后fresh `adb devices`为空并保存原始输出。

无新设备恢复欠账；保留原FAIL、所有原始日志和归档。无Git/发布/D产品回灌/桌面/正式库/私有库/云部署或扩大清理，automation-2/3保持PAUSED。

## 交付状态与下一步

本次授权的一次设备执行与恢复已收口，联合FAIL。下一步只针对ABC采集的Windows本地落盘路径做离线短路径定位/最小修复，保留原设备路径及run/size/SHA映射，不放宽消费者，不改Android产品、不重建APK。未安排或执行第二次设备attempt；后续设备重验另由用户决定。
