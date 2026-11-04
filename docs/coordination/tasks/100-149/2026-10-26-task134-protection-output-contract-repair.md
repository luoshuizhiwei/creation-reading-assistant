# 任务134-v1：保护输出目录合同单点修复（纯离线）

2026-10-26香港；Claude桌面Code/global DeepSeek Flash，同133 owner，10分钟。133预检发现确定性host/adapter输出根冲突，Root已读REPORT/precheck与pin适配器run_check实际guard，接受阻断；无ADB/attempt/设备写入，用户批准窗口未消耗，不再问许可。先修这一真实接缝并Root核新SHA后再单次设备，不绕过适配器。

````text
任务134-v1，你为唯一host owner，Root协调验原件；不是唯一协作者，不覆盖他人改动。当前133以BLOCKED收口，禁止原133继续执行。
输入host E:/creation-reading-assistant/task131-f1-host-binding-v1/lane-b/scripts/host131.py50937B SHAb28362a2368f11cdf5e1e66de226e08b02176036d305a1517e22e27590194dc3；其39行把b.PROT_OUT_ROOT覆为新lane，而pin适配器只允许OUT_BASE内输出。
固定适配器 E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/candidate/protection_check.py SHA16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd，OUT_BASE为E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1；63-64行is_relative_to限制及已存在输出拒绝不可放松。
实际base E:/creation-reading-assistant/task94-organizing-export-integration-v1/v3/scripts/t94v3_device.py SHA c806ce361c9c3929c4c32e08f6435e5e1f5b8ce088c8e6f0aa487cdee1d5fdbe，cmd_protection149行真实拼PROT_OUT_ROOT/<runId>-<kind>。
只引用133 REPORT/logs/precheck.log，不重核全部APK/恢复件或重复31项离线套件。

Root裁定最小修法：
1. 原host131.py与原报告保持不变；新建同scripts目录 host131-r1.py（ROOT仍指原task131/lane-b，可用同实际inputs/run-id/attempt新运行空间），只修PROT_OUT_ROOT与必要注释，不改业务/恢复/停止/判定其他hunk。
2. 新PROT_OUT_ROOT明确为适配器OUT_BASE / 'task131-f1-host-binding-v1'。未来实际initial/final在该唯一新子根/<T131runId>-initial或final；这项保护原始输出允许落该根，其余host输出仍原lane，不再承诺保护也在lane。绝不改适配器OUT_BASE/guard/PINNED，不运行时wrapper或Monkey patch它。
3. 仅纯离线验证实际新路径在真实OUT_BASE内且initial/final不同、含准确runId、不覆盖旧根历史目录；静态核原适配器exists拒绝与relative guard仍在。导入新host前subprocess.run守卫，禁止host main/run_attempt/cmd_protection/run_check/任何ADB；不创建未来保护输出目录或run-id/budget/attempt。可用AST读取实际OUT_BASE/guard，纯路径判定足够，不需要真实设备才能检出此冲突。缺口正是131未覆盖的离线路径合同，不要声称“必须真设备才可核”。
4. 保存原host→r1实际最小diff与少量正反路径原始检查，不全套、不编译、不assemble。inputs.json3909B SHAe694d8b5f16b38af5491304836d0de268dd47e4d090515ecf285a2bcf61f78dd保持不变（清单没有host角色，不能为改host无故改9角色）；新hostSHA由Root外层冻结。

所有权：只新增原lane/scripts/host131-r1.py，其他新增报告/patch/原始检查在 E:/creation-reading-assistant/task134-protection-output-repair-v1/。不改旧host/inputs/适配器/base/消费者/F1测试/生产/APK/D current。差量<=1MiB/raw<=1MiB，无大目录/工程cache/APK复制，存量不删移压迁。
禁止ADB/设备或VM探测启停/host主入口/attempt/安装卸载/设置/构建；本任务只有离线host修复授权。用户设备窗口许可保持未消耗，完成后Root核新原件再派一次设备，不自动重跑133或接后批。
禁CLI代发登录/私库自动记忆/Git暂存提交pushPR发布/云SSH SFTP/正式书库HOLD/实体手机/购买换号/杀共享进程。输入SHA/owner冲突或需要改保护pin件即停报，不能扩修。
交付REPORT.md/ready.json task134-v1、新host实际路径sizeSHA/最小diff/离线原始路径检查结果、原host/inputs/pin未变证明与实际新增字节分类。新host仅路径修复不证明F1运行时；设备NOT_RUN。末行：这是任务134-v1。
````