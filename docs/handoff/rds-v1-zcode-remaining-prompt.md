# 给 ZCode 的 RDS-v1 剩余任务提示词

> 此提示词已被后续排期替代，勿重发。先读 current，再按 `docs/coordination/2026-11-05-rds-next-replan-v2.md` 的 R1–R8 接续已有交付。

````text
你继续担任创作阅读助手原生 Android 恢复项目 RDS-v1 真机测试后的唯一修复、构建与设备 owner。用户手动转交本提示词；自动化保持暂停。不要操作无关项目，不自行 commit/push/PR/发布，不删除或移动既有资产，不写 current/state，不写记忆库。

先读：
1. D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/AGENTS.md 与 C:/Users/23254/.codex/AGENTS.md。
2. docs/handoff/current.md。
3. docs/coordination/2026-11-05-real-device-smoke-rds-v1.md。
4. docs/coordination/2026-11-05-rds-v1-remaining-queue.md（Z1–Z6 完整合同；以该文件为执行依据）。
5. E:/AgentWork/CRA/rds-v1-real-device-smoke/REPORT.md（终稿 v2）、REPORT-v1-archived.md、ISSUES.md。
6. E:/AgentWork/CRA/coordination/RDS-v1-ZCODE-ISSUES.md 与 rds-v1-trae-observation.md。

当前有限事实：P0–P13 已走查完毕；S1=0、S2=3、S3=10；RDS-101 两次复现确认，RDS-102/RDS-103 仍是候选，RDS-103 跨书串扰已撤回；P6.2/P6.4/P6.5、P9.3/P9.4、P11.3/P11.4、P12 主体、全局搜索等仍是 NOT_RUN。REPORT v2 的 EXECUTED_COMPLETE 只表示执行流程结束，不表示产品通过。P13 设置已恢复，但 /sdcard/Download 的 5 个 9 月旧测试文件仍在；本批不清理它们。

严格按队列完成六项，逐项在 E:/AgentWork/CRA/rds-v1-real-device-smoke/ 下写报告与 ready/Z<n>.json：
Z1 冻结交付与待修基线，核 v2/v1、ISSUES、Trae 清单、证据 SHA、计数、NOT_RUN 口径、隐私标注及原红保留。
Z2 先补判别证据：RDS-102 区分点击热区/操作误差与生产导航缺陷；RDS-103 做至少 60 秒 TTS 观察并核播放、段落边界、进度回写、通知控制与设计范围。每项给 CONFIRMED_DEFECT / OBSERVATION_LIMIT / DESIGN_SCOPE，只有确认的真实缺陷进入 Z3。
Z3 对已确认缺陷做最小根因修复，补真实调用生产路径的定向回归；RDS-101 覆盖 home 与 back 两种退出并对照 TXT/EPUB 保存路径。保留原始红→绿记录，禁止放宽断言、阈值或用字符串检查冒充行为验证。
Z4 使用 Z3 实际新 APK 在同一实体手机做 fresh identity 的修复项复现批。先 adb devices 并重新探测 serial/boot/user/焦点/包/设置基线，所有命令显式带本轮 serial；所有点击用脚本或 ADB 模拟，用户不碰手机。保持供电和唤醒，留原 rc、stdout、UI XML、截图、分段 logcat；业务判定与恢复判定分开写，结束时恢复本轮数据、APK、设置、旋转、媒体音量并做最终 identity 比对。0 项 instrumentation 不算通过；恢复不绿不得写业务通过；同项三败保留原红并停该方法。
Z5 依次补齐可执行的原 NOT_RUN 覆盖：P6.2 摘录与精确回源、P6.4 删除测试书后的摘录保全、P6.5 笔记导出、P9.3 亮度/护眼、P9.4 设置搜索、P11.3 手势/三键导航、P11.4 连续操作流畅度、P12 本地 ZIP 备份恢复演练、全局搜索命中跳转、P8 耳机拔出（可脚本断连才做）。每项给 PASS/FAIL/BLOCKED/NOT_RUN/NOT_APPLICABLE 和原始证据；截图存在不等于行为判定；只用测试书与测试数据，不配真实 WebDAV 账号，不碰正式书库。
Z6 汇总 Z1–Z5 所有问题状态、实际 diff、测试、设备、恢复、隐私裁剪、S1/S2/S3 计数、未覆盖项和残余风险，写 FINAL-ROOT-PACKAGE.md 与 ready/Z6.json 后停下等 Root 裁决。明确区分真机证据、历史 MuMu 证据和仍保留的旧红，不写“全产品完成”或无分母百分比。

Trae 是独立旁站 owner，负责 T1–T5 只读复核；你负责全部源码、Gradle、APK 和手机写入，双方不得争用 owner。Trae 不改源码或设备，你也不写 Trae 日志。每一步只依据已落盘 ready/报告/原 rc/XML/实际 diff/APK 推进，processed 指纹未变不重复核验，无新 ready 不盲等或发继续。

红线：禁止 pm clear、恢复出厂、盲恢复、正式书库/个人文件读写、Git 暂存提交 push PR 发布、D 产品回灌、资产删移、私有记忆写入；保留 RDS-v1 原红、v1 报告、Trae 原清单和 backup-pre-uninstall/。若出现 ZCode 认证/权限请求、模型不可用、工作区冲突、owner 争用、设备断连两次、电量低于 15% 或需新卸载授权，停对应操作并报告。用户 stop/Esc 立即停止新增输入。最终总结末尾单独一行写“这是任务 RDS-v1 剩余队列 Z1–Z6。”
````
