# N1 独立执行脚本补强

## 任务编号/版本
N1-runner-hardening-v1，2026-10-02，Root；用户授权自行实施A/B。
## 输入指纹
输入为J3-v2原始成功/失败/强停日志及信号；逐文件SHA见[offline-results.json](<E:/creation-reading-assistant/next-round-n1-runner-hardening-v1/offline-results.json>)。实际 runner SHA a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e。
## 所有权与改动
仅新E目录runner.py、offline_test.py、PROTOCOL.md和结果；不改Android runner/旧冻结。离线B未操作设备；Root A消费该脚本的真实设备运行另计。
## 环境
Windows Python3.13，显式UTF8，-B不产生__pycache__；无下载/账号/模型调查。
## 执行与结果
43/43离线门禁通过。fresh时钟严格0≤age<60000ms，同时runId/PID/scenario/phase/state匹配，否则硬拒绝force-stop。普通成功须exit0+OK(1test)且无fail/crash。真实强停须停止命令成功、PID消失、instrument结束与Process crashed，独立TERMINATED，不能作普通PASS。实际A批3普通PASS+1TERMINATED证明协议可用，首次错误runner名称被正确拒绝。
## 原始证据
[完整E目录与协议](<E:/creation-reading-assistant/next-round-n1-runner-hardening-v1>)；43例覆盖真实原样本、旧/未来信号、错误runId/PID/case/阶段、缺字段、零退出失败、仍存活、未结束等。输入SHA与代码SHA在结果。第一次离线读取Windows默认GBK失败，改显式UTF8后完成，未改原输入。
## 未覆盖项
该脚本限定单方法合同，不是通用instrument解析器；未验提供方/API/网络错误。subprocess异常可由Python直接报错，不能按PASS处理；目标数据恢复仍由设备owner负责。不得凭客户端terminate宣称宿主被停止。
## 恢复与副作用
B仅E文件与本报告，无设备权限/Android/冻结修改，未Git变更。
## 交付状态与下一步
两门禁补强2/2关闭，离线43/43=100%；后续执行复用PROTOCOL的显式manifest runner，必须保留基线/恢复及唯一owner。UI最终证据见[UI报告](../../../qa/2026-10/2026-10-02-next-n1-ui-accept-v1.md)。
