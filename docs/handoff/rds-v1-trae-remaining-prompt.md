# 给 Trae 的 RDS-v1 剩余旁站与复核提示词

> 此提示词已被后续排期替代，勿重发。先读 current，再按 `docs/coordination/2026-11-05-rds-next-replan-v2.md` 对变更做增量核验；无新结果不重复扫描或追加无变化日志。

````text
你继续担任创作阅读助手原生 Android 恢复项目 RDS-v1 修复阶段的独立旁站与复核 owner。用户手动转交本提示词；自动化保持暂停。ZCode 是唯一源码、Gradle、APK 和手机写入 owner；你只做只读复核、证据交叉核对和问题回交，不改源码、不构建、不写设备、不改 ZCode 的报告或 ISSUES，不写 current/state，不写记忆库。

先读：
1. D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/AGENTS.md 与 C:/Users/23254/.codex/AGENTS.md。
2. docs/handoff/current.md。
3. docs/coordination/2026-11-05-real-device-smoke-rds-v1.md。
4. docs/coordination/2026-11-05-rds-v1-remaining-queue.md（T1–T5 完整合同；以该文件为执行依据）。
5. E:/AgentWork/CRA/rds-v1-real-device-smoke/REPORT.md（终稿 v2）、REPORT-v1-archived.md、ISSUES.md。
6. E:/AgentWork/CRA/coordination/RDS-v1-ZCODE-ISSUES.md 与你已写的 rds-v1-trae-observation.md。

你的旁站部分已经完成，但后续复核与交接队列没有结束。严格按下列五项推进；每项把结论追加到 E:/AgentWork/CRA/coordination/rds-v1-trae-observation.md 的独立章节：
T1 复核 Z1：只读核 v1/v2 原字节、ISSUES、Trae 原清单、交付 SHA、裁决冲突、S1/S2/S3 计数、NOT_RUN 口径、旧红与隐私标注。T1 可与 Z1 同时开始，但只对已落盘文件下结论。
T2 复核 Z2：只读核 RDS-102 的控件边界、实际点击与生产导航证据，RDS-103 的至少 60 秒观察、播放状态、段落边界、进度回写和设计范围；每个候选写“证据支持 / 证据不足 / 属设计范围”，标出从现象跳到根因的推断。
T3 复核 Z3：只读核实际 diff、定向测试源码、原始 rc/stdout、APK 指纹和红→绿证据；确认测试走生产路径，没有放宽断言、阈值或隐藏异常；逐缺陷给结论。
T4 旁站 Z4：每 5 分钟只读观察新证据和 ADB 状态，记录 fresh identity、安装 APK 指纹、修复项复现、logcat FATAL/ANR、设置与数据恢复、最终 identity。用户全程不碰手机，保持供电与唤醒；你不得与 ZCode 同时操作设备。若安全锁屏再次出现且常规唤醒无法恢复，记录 BLOCKED 和受影响项，不索取 PIN/图案。
T5 复核 Z5/Z6：逐项核对 NOT_RUN 补测、业务与恢复判定、隐私裁剪、新旧报告差异、严重级计数、残余风险和限定接受建议，写最终三方差异表（你 / ZCode 报告 / 原始证据）。把复核结论整理成一条消息，确认是 ZCode 的正确会话后点“立即”发送；若无法确认目标会话，落盘并报告发送失败，绝不发到其他任务窗口。Trae 不裁决最终通过，不自行发起修复。

观察纪律：每条记录带时间、检查内容、实际结果、证据路径、是否影响验收，区分亲自实测、执行者自报和推断。ready/报告输入指纹未变不重复核验；无新 ready 时只检查已落盘的授权交付，不进入桌面查进度、不 UI 轮询、不发“继续”、不重贴、不盲重试。S1=0 与 FATAL=0 是有限观察，不等于全产品通过；0 项 instrumentation 不构成业务通过。

资产与停止条件：保留原红、REPORT-v1-archived、Trae 原清单和 backup-pre-uninstall/；不得读取个人文件内容或外传含设备真实文件名的 SAF dump；禁止 pm clear、恢复出厂、正式书库/个人文件写入、Git 暂存提交 push PR 发布、资产删移、D 回灌和记忆写入。若发现 owner 争用、认证/权限请求、工作区冲突或用户 stop/Esc，停止新增输入并报告。Root 在 Z1–Z6 与 T1–T5 完成后负责 current/state 和最终有限裁决。最终总结末尾单独一行写“这是任务 RDS-v1 剩余队列 T1–T5。”
````
