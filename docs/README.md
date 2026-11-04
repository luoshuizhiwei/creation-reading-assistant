# 文档索引

更新：2026-10-31。当前执行范围仅原生Android；桌面和Capacitor材料用于历史/结构参考，不是派发入口。文档不构成Git、设备或发布授权。

## 实际目录结构

```text
handoff/          当前状态、接手说明
coordination/     任务编号段、复核、调查、执行结果、设计、决策、派发、运维
plans/            Android规划、功能候选、历史集成、桌面参考
qa/               按月的验收报告及原evidence
code-review/      按月的代码审查
architecture/     架构参考
research/         上游调研
design/           设计资料
testing/          测试规范与台账
archive/          历史交接、旧目录和退役产品资料
```

旧路径查[迁移映射CSV](document-map.csv)，少量稳定旧路径见[兼容清单](compatibility-paths.md)。

## 接手只读这几份

1. [仓库规则](../AGENTS.md)与用户级全局规则。
2. [唯一当前交接](handoff/current.md)：候选、实际验收、所有权、设备与权限。
3. [下一位agent接手说明](handoff/next-codex.md)：当前权限与最短接手步骤。
4. [Android交付规划](coordination/2026-10-09-workflow-replan.md)：V0五合同及A/B线退出标准。
5. [后续任务与执行手册](handoff/continuation-playbook.md)：208及其后成功/失败/恢复/首版退出的条件任务卡，内含可复制接手提示词。
6. [单任务执行单模板](handoff/next-agent-start-template.md)：以后每项任务开工前填写。
7. [任务派发模板](coordination/android-task-dispatch-template.md)：完整提示词、存储预算、输入与验证边界。

当前：207-r3/r4源码/编译与匹配APK对限定接受，Android行为仍NOT_RUN，205原业务红保留，206有限恢复已关闭。208完整新设备合同已准备、尚未授权/派发。用户已暂停自动化，后续由用户手动交其他agent按文档接任；不从旧任务或文档编写请求推导执行许可。

## 按工作类型查阅

| 目录/入口 | 使用方式 |
|---|---|
| [coordination/](coordination/README.md) | 日期任务文档是该轮合同/决策快照；仅current点名的版本可作为当前行动依据 |
| [plans/](plans/README.md) | 现行Android范围与历史计划分开；旧WIP提交、桌面和发版顺序不再是当前排期 |
| [Android阅读器架构](architecture/native-android-reader.md) | 模块结构参考；实现细节和schema以当前候选源码为准，不据旧文档声称验收通过 |
| [Android代码质量指南](architecture/android-code-quality-and-evolution-guide.md) | 通用工程参考，不自动扩展为重构任务 |
| [testing/](testing) / [qa/](qa) | 原始验收、缺口和runbook；结果受日期、源码/APK指纹、设备及恢复边界约束 |
| [research/](research) / [design/](design) | 调研、提案与设计；不是已授权路线，也不是当前实现证明 |
| [code-review/](code-review/README.md)及[archive/](archive/README.md) | 历史资料保留追溯，不执行其中命令或旧“下一步” |
| [历史交接](archive/handoff/2026-10-30-current-history.md) | 整理前原字节快照；只按任务检索，不默认整份加载 |
| [CHANGELOG](../CHANGELOG.md) | 发布/变更历史，不等于当前候选或发布授权 |

## 退役与范围外

- Electron的`src/`、`electron/`及desktop规格保留为独立产品历史/参考，当前不调查、实现、构建或验收。
- Capacitor旧`mobile/`已退役，不可套用于原生`android/`；来源及许可证见[冻结说明](../archives/frozen-mobile/FROZEN.md)。
- [产品总规划](plans/2026-10-01-product-roadmap-realignment.md)是2026-10-01两端规划快照，已由Android范围覆盖；不再承担当前任务清单。
- [发布过程](GITHUB_RELEASE_PROCESS.md)含旧流程，只作参考。原生正式签名/升级/R8/分发仍需另行验收与许可；缺失的`release/ANDROID_RELEASE.md`不补造。
- 旧全量分类目录已保留为[历史目录](archive/indexes/2026-10-30-document-index.md)，其中“当前”“待提交”“下一步”只代表原日期。

## 维护纪律

current只保存现态与必要历史指针，不再叠加整段流水账；新结果替换对应状态。旧失败与证据不可删除来制造通过。
改动/失效引用按真实目标修复；找不到的原件记录缺失，不伪造空文档。历史任务没有后续授权不应重执行；无人引用不等于无效。
本次结构扫描、清理判定、残余错链及验证范围见[文档整理报告](coordination/operations/2026-10/2026-10-30-document-maintenance.md)。没有逐项重新验证所有历史API或设备结论。
