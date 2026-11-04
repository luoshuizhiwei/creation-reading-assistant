# 117-v1 只读诊断补充1

时间：2026-10-24；模型：官方Start Plan GLM-5.3；作用：定位持续night=yes，不开新设备业务批；并行关系：设备只读仍同owner，冻结产品仪器APK不改。

````text
接续117-v1只读诊断补充1，沿同目录E:/creation-reading-assistant/task117-saved-view-reopen-entry-v1，保留现REPORT/ready/采样原件，不覆盖旧结果；新增diagnostic/及报告补充即可。Root不把“报告已完成”当来源已定位，不开118业务，不建attempt，不重跑仪器。

当前四次汇总night=yes/ui_night_mode2已表明需要停入场；但00-night-gate-sampling.json只有整理字段，缺原始命令/rc/stdout/stderr与采样对应的current user。报告“无其他owner”只凭无python并不能排除宿主UI/其他ADB客户端/系统自动来源，勿定论；116只能说前后采样不同，不是已确证瞬时故障。报告称host117-binding.patch存在，但Root实际只见REPORT/ready/evidence三个文件，核实真实存在性，不补造历史交付。

仅极窄只读：fresh serial/boot/owner/前台/current Android user，绑定-s，采cmd uimode night、settings get secure ui_night_mode（明确user）、dumpsys uimode的night/auto/custom/override/currentUser相关真实字段，Settings/system是否另有实际支持的自动深色/省电状态字段；读取失败明确原rc不当absence。核MuMu实例1的实际主题/跟随宿主/定时模式设置，只用现成配置实际精确路径或UI只读，不改宿主主题、实例主题、Android设置。不扫全盘私库，不读账号凭据。若用配置先从实际进程/已知MuMu安装实例路径定位相关少量文件，不能凭字段名猜作用。

只读现存窄时间窗日志是否有UIModeManager/SettingsProvider或明确调用者UID记录，可按现日志实际支持读取；不清logcat、不启长期trace、不重启服务、不授权新增ADB instrumentation或包安装。没有写入者日志就如实“现存证据不记录caller”，不要以无日志排除来源。宿主检查相关ADB命令进程只作为某时刻采样，不能证明此前无竞争。

所有新查询保存原命令/时间/serial/rc/stdout/stderr，小量必要输出，最多约2MiB，不导出全系统/全历史。结论区分当前模式来源字段/实际写入者证据/候选/未知。如果可只读定位源头，给最小恢复路径及影响；若缺历史caller无法确证，明确缺口并给有依据的一次可控恢复观察方案供Root裁决，不自行执行写no、不反复采同样东西等时间，不新建业务run。不要把来源未知当只能无穷查询。

保留strictnightno合同不放宽，设备现状保全，原产品缺席/test0d18不动，VM运行不关闭重启杀/共享ADB不动。仪器a64ca14f/test83d0e8bb/product6a冻结，零编译复制。Root D协调current，你不写D。禁止Claude/Cowork/Claude-Code/DSH或.codex/memories任何读写含自动记忆；不vault维护/Git暂存提交pushPR发布/云配置源码上传/桌面正式库HOLD实体手机/存量清理/关闭其他进程/建自动化或发外部消息。沿官方Start Plan GLM-5.3，最后单独一行：这是任务117-v1只读诊断补充1。
````

实际交接：新补充消息与工作中4秒/停止生成截图已核，未启动新设备业务批。
