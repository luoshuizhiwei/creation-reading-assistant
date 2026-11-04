# 117-v1 必要恢复补充2

时间：2026-10-24；模型：官方Start Plan GLM-5.3；作用：保留来源未知，单次恢复既定night=no并有限观察；不是设备业务批，不新增attempt。设备仅同owner。

Root核11-read-only-samples原命令rc/currentuser0、nightyes、secure2及dumpsys当前mNightMode2，接受当前模式证据。现日志没有caller不足以排除来源。14-summary误读mNightModeLocked为“locked to yes”，该标志本身来自config_lockDayNightMode配置与权限约束，不证明写者或强制yes；MODE_NIGHT_NO也不是0，不能用错误数值验收。参考[官方AOSP UiModeManagerService](https://android.googlesource.com/platform/frameworks/base/+/android16-release/services/core/java/com/android/server/UiModeManagerService.java)与[官方UiModeManager常量](https://developer.android.com/reference/android/app/UiModeManager)，不是MuMu定制实现已核。报告host补丁不存在的更正接受，冻结旧报告不改造历史。

````text
接续117-v1必要恢复补充2，同E:/creation-reading-assistant/task117-saved-view-reopen-entry-v1，新增recovery-observation/，旧REPORT/ready/diagnostic不覆盖。沿官方GLM-5.3/同设备唯一owner。现阶段只读定位已达现存证据边界：caller未知。Root在既有strictnight=no恢复授权内明确指定一次必要恢复及有限观察，不再开相同业务批、不建inputs/attempt、不安装包、不执行仪器。

先更正诊断解释：mNightModeLocked=true本身不是“锁为yes/明确外部写入”的证据；AOSP该标志取config_lockDayNightMode并用于权限约束，MuMu定制是否相同未核，不能推导writer。恢复验收不要用mNightMode=0（AUTO）；以cmd uimode night=no对应实际平台常量及同user持久字段、dumpsys一致为准。原样保留14-summary，补充更正。5分钟保持no也不能证明历史yes只是一次外部写入，只能证明该观察窗内未再现。

fresh serial/boot/currentuser/相关owner/前台，核仍项目MuMu1无业务owner。如果有人正在改主题或使用实例，停止写入保全现态，不抢。先保存原始night/同user secure值/dumpsys和时间。仅执行一次标准合同命令 adb -s 本轮serial shell cmd uimode night no，保留原命令rc/stdout/stderr；不直接写secure数据库、不改锁/主题/省电/定时/宿主设置，不删任何资产，不重启杀VM/共享ADB、不装包不清log。若命令失败或初次复核仍非no，停后续写入，保全失败和只读状态，交Root；不静默换命令/重试写no。

写后立即、约60秒、约300秒各做原始只读cmdnight/同user secure值/dumpsys采样，逐次时间/serial/rc/stdout/stderr。期间不发其他设置写入，无长期追踪。若任一变yes，立即保存窄现存日志和当前状态停止观察，不再写no，不开业务批；caller仍无记录就明确未知。若稳定，只报告这5分钟no稳定及合同恢复，不宣称writer根因排除或允许自己开业务。任何读失败分开，不当absence/成功。

这是一项单次必要恢复，非业务attempt、不用设备instrumentation、无APK复制/构建。新增原始证据预算1MiB/脚本不超必要范围；报告补充记录精确执行、结果、来源仍未知、最终VM/serial/包状态和空间。不重跑71/820全保护：本次只改既定night设置且无产品/数据动作，复用116充分数据保护；不宣称本轮又全量核过。原test0d18/产品缺席冻结，现有设备资产全保护。禁止任何私库/.codex/memories含自动记忆、D协调current写入、Git暂存提交pushPR发布、云配置源码上传/桌面正式库HOLD实体手机/存量清理/关闭其他进程/创建自动化外部发消息。完成交Root，不自行下一批。最后单独一行：这是任务117-v1必要恢复补充2。
````

实际交接：新补充2消息及工作中8秒/停止生成已核；同GLM-5.3。UI同时提示该模型活动额度剩余8%（1591万tokens），未核总账号余额，未换账号购买。
