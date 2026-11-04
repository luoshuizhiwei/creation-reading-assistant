# 阶段 B 风险核实退出复核（2026-09-28）

> 后续阶段 C 新证据：S14 窗口 2 改动在 Codex 新鲜定向 JVM 中编译通过但 9 项有 1 项失败，Windows JVM 的 `File.renameTo` 未能替换既有目标。原始 XML 与 SHA 见[任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)。这不改变阶段 B 对风险识别/处理归属的退出结论，但 S14 修复尚未通过阶段 C 门禁。

阶段 B 的退出条件是风险有当前证据和明确处理方式；这不代表风险已修复，也不代表设备验收通过。按 [收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) 检查，阶段 B **限定退出**，进入阶段 C 修复与定向验证。A–E 五个阶段已有 A、B 两项达到各自退出条件，整体进度按阶段计 **2/5 = 40%**；这不是代码量、功能量或发布时间的百分比。

| 范围 | 当前证据 | 阶段 C/D 处理方式 |
|---|---|---|
| S01/S02 | [父表与迁移复核](2026-09-28-stage-b-s01-s02-data-risk-closure-review.md)：隔离设备 5/5、迁移 23/23，在报告范围内关闭 | 相关 Schema/DAO 改动时才重验，不重复已足够的门禁 |
| S03 | [索引与撤销复核](2026-09-28-stage-b-s03-s04-data-risk-closure-review.md)：当前锁/CAS/请求链存在，历史定向 JVM 19/19、29/29、27/27；XML 已被覆盖，独立真实 Room 并发未验 | D 冻结后定向补跑并保存 XML 哈希；MuMu 隔离库可验证的链路单列，实体手机不写 PASS |
| S04 | [来源刷新与回滚复核](2026-09-28-stage-b-s03-s04-data-risk-closure-review.md)：MuMu qahost 有限证据；新内容字节、坐标重映射、>32 MiB/中断路径未证；回滚 21/21 是旧门禁 | C 处理现役缺口，D 对相关现字节重验、核对 DB/文件/哈希；正式书库与实体手机保持未验证 |
| S05/S06 | [核心调查](../undated/agent-stage-b-core-audit.md)、[阅读风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-reader-risk-delta.md)及 MuMu 定向 QA：已完成的窄场景与手机布局/快速连续操作缺口分开 | C 处理同书排队、阅读进度等现役风险；D 在隔离 MuMu 手机尺寸上验 UI，勿以平板截图代替 |
| S14 | [平台风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-platform-risk-delta.md)、[失败原子性调查](../../legacy-stages/2026-09/2026-09-28-s14-zip-restore-failure-atomicity.md)：本地 ZIP 的文件截断与较新本地行/旧文件偏斜已定位；[窗口 2 修复](../../legacy-stages/2026-09/2026-09-28-s14-zip-restore-safety-fix.md)仅落源码/测试，未编译 | C 串行编译、定向 JVM 和 MuMu 隔离持久库 6 项；仍须登记 DB 先提交、文件后复制的中间态与 staging 崩溃残留 |
| 其余 S07–S20 | [阅读风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-reader-risk-delta.md)、[平台风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-platform-risk-delta.md)逐项列出当前能力/证据/行动 | C 只修已证实故障；D 集中设备与构建证据；S19/S20 敏感诊断资产先清单后决定去向 |
| 六接口清理 | [逐接口复核](2026-09-22-cleanup-six-api-review.md)：两项恢复、四项删除保留的结论明确 | C01 已恢复必要接口；共享搜索文件由集成者独占，D 编译/测试覆盖 |

## Codex 对窗口 1/3 的补充裁决

- [流式 TXT 修复](../../legacy-stages/2026-09/2026-09-28-s12-streaming-txt-import-position-result.md)的 25/25 XML 与日志已回读。完整 `TxtFileIndex` 路径可接受为代码/JVM 范围；`unconvertedLegadoPosition` 目前只有生产字段定义/赋值，无 UI 消费，且 500ms 后保存仍可能把未换算 marker 覆盖为 offset 0。因此 S12 这项 **部分完成，不关单**。下一步由阅读进度 owner 接线可见提示和保存保护，再定向验收。
- S04 报告把 `EpubRepository.kt` 09-28 00:59 mtime 视为回滚 21/21 失效的提示，处理方向谨慎，但 **mtime 本身不足以证明回滚逻辑改变**。当前相对 HEAD 的 diff 可见 S12 在 `saveProgress` 增加 locator 量纲修正；此处与 `replaceStoredEpub` 是不同函数。旧 21/21 仅按当时门禁陈述，D 冻结时对现字节做一次相关定向门禁即可；不因 mtime 单独反复重跑。
- S03 历史 XML 覆盖不能写成失败，也不能写成现版可回读的 XML PASS。保留历史账本证据，D 一次性补证。

约束：原始仓库、真实手机、正式书库未触碰；MuMu 证据只代表模拟器；不执行 commit/push/reset/checkout。阶段 C 最多两路不重叠实现，共享文件由总指挥协调。
