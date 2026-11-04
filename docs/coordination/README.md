# 协调文档分类入口

更新：2026-10-31。原来同层的任务、调查、报告已实际移入子目录。历史文件不因仍保留就自动成为当前任务。

## 当前工作与通用规则

- [当前交接](../handoff/current.md)、[换窗接手](../handoff/next-codex.md)。
- [Android交付规划](2026-10-09-workflow-replan.md)、[派发模板](android-task-dispatch-template.md)。
- [后续任务与接任执行手册](../handoff/continuation-playbook.md)：接续队列与原Root不在线时的职责移交。
- [208-v1新设备合同](2026-10-31-task208-ordinary-reopen-fresh-device.md)：当前首个待办，待本批设备授权、未派发。207-r3/r4只接受源码/编译及产物，Android行为NOT_RUN；207更早返修仅历史参考。
- [202设备恢复机制合同](2026-10-30-task202-lifo-body-device.md)：原字节保留供冻结合同核对；不是新设备运行授权。

## 按用途查找

| 分类 | 内容 |
|---|---|
| [tasks](tasks/README.md) | 带task编号的合同/报告，按000–049、050–099等编号段放置，各版本相邻 |
| [reviews](reviews/README.md) | Root复核、独立审查、审计与反例，按月 |
| [investigations](investigations/README.md) | 原agent-investigation定点调查 |
| [agent-results](agent-results/README.md) | 原agent-result执行结果 |
| [designs](designs/README.md) | 原agent-design草案 |
| [decisions](decisions/README.md) | 范围、阶段决策、台账和交接安排，按月 |
| [dispatches](dispatches/README.md) | 未带task编号的日期化派发合同，按月 |
| [legacy-stages](legacy-stages/README.md) | 早期S/阶段类执行记录，按月/未注明日期 |
| [operations](operations/README.md) | 文档、存储与环境运维记录，按月 |

少量文档因产品源码中的固定引用留在旧路径，清单见[兼容入口](../compatibility-paths.md)。原JSON/log/ps1证据及脚本未移动，以免破坏机器引用；其用途与位置也在兼容清单说明。不要执行旧清理脚本。

旧路径查新位置见[迁移映射CSV](../document-map.csv)。迁移前原件保存在E盘分类整理证据目录，原始失败和授权没有被抹掉。分类只影响查找，不改变验收结果或许可。
