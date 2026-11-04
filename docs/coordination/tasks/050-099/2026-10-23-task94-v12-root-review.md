# 94-v12 Root核验与94-v13方向

## 任务编号/版本、输入指纹

2026-10-23 14:36后，94-v12交付核验。REPORT/patch/当前仪器/产品/test/保全aebc/宿主7项SHA与大小（适用项）实核匹配，见E:/creation-reading-assistant/task94-organizing-export-integration-v1/v12/ready.json。REPORT ac71edad…13ff1、仪器cec7b588…b39d3、宿主6b4f76a9…991c4、产品11023464…ffc30c、test22c6c738…ae380。实际运行42bf原字节未在重建前保全，缺口采纳而不冒充完整复现。

## 所有权与改动、环境

Claude已Idle且无相关task94/Gradle进程后，Root只读核验并维护D协调入口。未改E源码、未构建、未进行新设备动作。后续94-v13由同一Claude owner闭环E仪器/宿主/必要构建/绑定/设备/交付；D协调记录/current归Root。唯一task59工作区与既有依赖/锁复用。

## 执行与结果、原始证据

[v12执行报告](2026-10-23-task94-v12-review.md)；原始证据位于上述v12/evidence/device与instrument。summary保留业务失败及export-cleanup UNPROVEN，ok=false；不能写成恢复无错误。原test恢复记录APK为0d18…、完整inventory UTF8/LF SHA d6ac3c5f…03e29、userState与有效合同逐项核同。保护final wrapper rc0，解析stdout原JSON ok=true/reasons=[]，71/820/diffs=[]/DB组一致。产品卸载原始Success及后验列表精确产品缺席、原test存在；keepalive实例1 pid66432、相同boot运行。均为该run历史证据。

保存前失败证据/日志与报告一致，导出保存、URI字节/内容/精确清理、清空、取消仍未执行。离线构建与修正不算设备通过。

## 方向自审、未覆盖项

产品目标保留剩余三项，复用已充分分类/搜索/AND/回源/69项。拒绝直接以22c6重跑：Root读取runRootShellScript发现写fds[0]、读fds[1]；[官方UiAutomation API](https://developer.android.com/reference/android/app/UiAutomation#executeShellCommandRw(java.lang.String))规定0为stdout、1为stdin，现实现反向，编译成功不证明通道正确。另宿主finally collect/cleanup失败仍无条件卸载产品，三态尚未控制可能损失唯一未拉证据的卸载分支，未满足94-v12恢复合同。以上是确证必要修正，不是泛审查或新增产品功能。

94-v13先修FD方向并验证实际生产通道、受控覆盖finally失败分支，未创建/已保全才恢复产品入场状态，未保全证据则保留依赖资产并阻塞、原test与独立安全恢复继续；之后一次剩余验收。恢复合同→必要构建→绑定→同设备有直接依赖，同owner闭环，不制造第二lane。实际派发完整源见[94-v13提示词](2026-10-23-task94-v13-dispatch.md)，保持当前Claude模型，确认额度耗尽才转ZCode。

## 恢复与副作用、交付状态与下一步

Root仅新D文本差量，不复制APK/工程/缓存、不清理存量；v12申报15,548,155B、其中必要保全APK10,525,181B，非APK约4.79MiB；构建缓存增量未独立量测。v13差量8MiB/build预计256MiB，唯一22c6覆盖前必要单份10526983B保全并要求实际运行APK也保全。

状态：94-v12部分交付已核，恢复主要对象核同但首次cleanup错误保留；94-v13自动交接。每批一次主attempt含入场失败，前置不闭合则不启动；失败不批内重跑。原test/71/820/DB/strict night=no及精确资产许可不放宽，实例不关闭重启/结束运行。automation-2/3保持PAUSED，新claude heartbeat接续；无Git发布/D产品回灌/私库/云/桌面，无全产品百分比或实体手机/MIUI通过结论。
