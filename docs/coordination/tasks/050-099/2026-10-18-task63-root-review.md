# 63共同冻结与入口核验

## 任务编号/版本

63-v1，Root入口最小修正root-r1，2026-10-18香港时间。[接收清单](<E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/ready.json>)。

## 输入指纹

共同冻结`31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc`；新host `14e573ab964a122c3d4fbb83770423d70bebd57028eca26c455a442da1a282aa`、新test `d5cdd05d8709b6bef22d3e3b0fae9e703a50105a4adb0a2f4f3539f16cb21d7a`实算匹配。63产品清单逐项声明与已采纳62清单相同，1034文件，无产品源码改动。无需向用户再次询问新旧APK选择：Root采纳严格冻结绑定的新对，旧59/62归档对保留。

## 所有权与改动

外部63完成冻结/构建/新增Room阶段。Root初查无相关活动执行者，只核本轮差量、冻结与原始日志；未新建子代理、编译框架或复制工程。原63 REPORT/ready/入口/94绑定不改。

原入口SHA `6031368b0cbf622d0dc5f973eb6aa860b1ca3a58cd615b83cbbe2821d9e76ee0` → Root-r1 `eae871b623f20c04cf81a8c8ed52aad06a7a8d9af9d725774a5d45f526dfc969`。实际后续入口在`task63-common-device-entry-v1/root-review/root-r1/task63_joint_runner_v14.py`；对应95项绑定SHA `e563105b01307200f672f3e688885c047caa1f04cf710ae6751ac125f509e722`，原94声明保留，追加真实Root入口并实际校验通过。

## 环境

沿用Python3.13及现有工具。63真实Gradle调用1次、exit0；Root构建0、设备/ADB0。Root只用scoped subprocess mock直接调用新增Room生产执行函数，不启动任何仪器或模拟器。

## 执行与结果

- 冻结原日志记录cra-freeze-gate/2 wrap/verify PASS（45项、0 FAIL/SKIP），包括真实构建记录及APK身份；Root核对冻结、构建日志及新APK指纹，复用已有门禁输出，不重建。
- 既有门禁确实拒绝旧产物直接复用，必要真实构建有依据。但63采用全图`--rerun-tasks`，范围大于任务要求的最小必要打包，记录偏差；不因此再次重建或拒绝有效产物，下次优先定向正规打包。
- 新增Room真实执行体有确定阻断：缺少`import re`，成功仪器输出也会触发NameError。原`--offline`仅模拟跳过执行体，不能覆盖它。Root直接调用原函数已复现并保留trace。
- 原TimeoutExpired会在部分stdout/stderr落盘前退出，受控注入部分输出时落盘文件为0。Root增加原始字节、部分目录与失败结果留证，保留原超时；留证写盘也失败则ExceptionGroup同时保留两原异常。
- 原Room私有路径加入tmp允许清理根，与方案“仅作记录”不符。Root移出tmp授权列表，改写独立`room-created-assets.json`观察账，`deletionAuthorized=false`，仍通过已验证包级归档与恢复合同处理，不按stdout路径删除。
- 修正版实际Room执行体定向4/4：正常4用例标记、仪器失败留证、超时部分字节/目录留证、超时叠加写盘失败保留双原异常。真实默认合同入口解析同一新冻结、APK对与95绑定通过。AST确认仅Room执行函数及联合入口相应登记段有函数级变化，既有clamp/SAF/进程树等机制未变。

## 原始证据

原63的`logs/wrap.log`、`freeze/attempt1`与`evidence/offline-entry-checks.json`、APK身份日志；Root目录的`original-normal-error.log`、`original-timeout-check.json`、`root-normal|failed|timeout|timeout-write-failure`、`root-checks.json`、`root-r1/source-sha.json`、`runner.patch`及95绑定。所有反例只是本地scoped subprocess mock，没有设备命令；原离线PASS保留且范围收窄。

## 未覆盖项

4个Room仪器用例和ABC/clamp/SAF/DE/总消费者未真实执行；无模拟器/UI/进程冷启动或整链通过。新APK字节与旧对不同已实证，同源码/包名/签名等身份由冻结记录支持；“具体差异完全来自增量与全量重建”不是Root已定位的二进制原因，不扩称逐字节等价。全产品分母未知，不称实体手机/MIUI通过。

## 恢复与副作用

Root无新设备恢复义务，旧63冻结/APK/交付与旧56工具不改；只追加小型Root入口差量/绑定/必要日志和交接。无D产品回灌/Git/发布/外部派单/桌面/正式库/私有库/云部署/既有资产删除迁移；自动化保持PAUSED。

## 交付状态与下一步

63共同冻结/新APK采纳；入口使用Root-r1替代原v14，沿同任务完成小修，不重派、不重建。具体单次方案以原DEVICE-PLAN加[Root更正](<E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/DEVICE-PLAN-DELTA.md>)为准，冻结/APK不变，改用Root入口/95绑定及独立Room观察账。唯一设备owner/一次新run/一次attempt，fresh基线与完整阶段+1200秒恢复，失败不现场改码重绑再试；设备执行仍等待用户决定，两个自动化不恢复。
