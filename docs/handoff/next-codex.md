# 下一位 agent 接手说明（2026-11-04 收官版）

> 2026-11-17 夜间最新：[current](current.md) 页首「全文索引下线 + Mate 70 闪退收尾」。搜索已改按需扫描、
> 索引写入全部 no-op、库升 v17、新增"导入阅读记录(JSON)"；真机已重装并核对数据。
> 待办=删除 5 个 no-op 索引类及其注入点与 3 个过时用例（清单见 current 页首末条）。


> 2026-11-17 [Mate 70／候选问题修复](<E:/AgentWork/CRA/mate70-mobile-fixes-v1/REPORT.md>) 最新本地 APK 为 8a14246e…，书架异常、重试超时、恢复用例通过，夜读文字在 MuMu 实测。前代已验证旧库原地升级、Legado 预览与整包备份、新旧书全文搜索。HDC 暂无 Mate 70，接入后在隔离资料下复验原书架、搜索和兼容界面。原问题见 [对照报告](<E:/AgentWork/CRA/mate70-mobile-audit-v1/REPORT.md>)。

> 最新阅读内核批次 READER-KERNEL-DEVICE-v3，见 [报告](<E:/AgentWork/CRA/reader-kernel-device-v3/REPORT.md>)。Legado 记录导入见 [方针](<E:/AgentWork/CRA/reader-kernel-device-v3/LEGADO-IMPORT-PLAN.md>)，候固定版本样包确认后实施。

> 阅读内核最新批次为 READER-KERNEL-DEVICE-v2。[真机与离线报告](<E:/AgentWork/CRA/reader-kernel-device-v2/REPORT.md>) 记录冷开、缓存重开和翻页限制。后续先细分冷开阶段和抓手动翻页 trace。

> 阅读内核最新批次为 READER-KERNEL-DEVICE-v1。真机采样及恢复已完成，进一步延续见 [采集报告](<E:/AgentWork/CRA/reader-kernel-device-v1/REPORT.md>)。

> 阅读内核最新接续为 READER-KERNEL-HOT-PATHS-v1。按 [报告](<E:/AgentWork/CRA/reader-kernel-hot-paths-v1/REPORT.md>) 延续四项优化后的验证，当前用户仍暂缓运行测试与设备。

> 阅读内核最新接续：READER-KERNEL-SOL-CONTINUITY-v2 已交付源码、差量和指纹，运行验证暂缓。恢复验证后的顺序及剩余边界见 [本批报告](<E:/AgentWork/CRA/reader-kernel-sol-continuity-v2/REPORT.md>)。前一段审查仅用于追溯。

> 2026-11-14 最新接续：用户要求先只读审查，由 sol 实施。见 [审查与五项顺序方案](<E:/AgentWork/CRA/reader-kernel-review-sol-v1/REPORT.md>)、[完整实施词](<E:/AgentWork/CRA/reader-kernel-review-sol-v1/SOL-PROMPT.md>)。当前方案未执行；保持不测试、不构建、不动设备；此前工作树必须保留。

> 2026-11-14 接续更新。以 [current](current.md) 页首阅读内核连续性扩展为准。六个生产文件已更新，编译与运行验证按用户要求暂缓。APK `291bf1a0…` 为上一轮候选。范围与后续验证见 [本轮报告](<E:/AgentWork/CRA/reader-kernel-continuity-v1/REPORT.md>)。下文 2026-11-04 状态为历史交接。

> 项目处于**收官待决策**态：两产品线全部标准质量门绿，无未完成的自主工作。接手者先读本文件和 [current](current.md)，然后看"待用户决策"四项——不需要重启任何批次。

## 最小启动

1. 读仓库与用户级 AGENTS；遵守各 agent 私有记忆边界（本仓库历史经验见 ZCode 原生记忆索引）。
2. 读 [current](current.md)：现态、待用户四项、复用边界、候选 SHA。
3. 读 `E:/AgentWork/CRA/coordination/android-zcode-luna-state.json` 的 nextContinuationQueue（nbLine20261103 有 NB01-15 全台账）。旧路径查 [document-map.csv](../document-map.csv)，不加载历史全集。

## 当前事实（2026-11-04）

- **Android**：V0 八卡全闭合（V0_ACCEPTED_WITH_SCOPE_DEVICE，MuMu 口径）；JVM 3808/3808；设备批 AR03/04/05、NB03 图测试 7+1、NB04 SAF 文件渠道全 PASS；每批 166+R2 恢复全绿；产品 c396ebe1 + test 1555fc7f。
- **桌面**：设置重构+搜索 WIP（20 路径未提交）功能已完整，tsc×3 + vitest 981/981 + 契约 54/54 + build 全绿；打包验收 stage1 族真绿（stage3/4/4b 脚本钉历史目录待维护）。
- **历史红保留**：205 旧红、206 有限恢复、11-02 Antigravity 伪造设备报告（已 RETRACTION）——均不因新绿抹除。

## 新工作怎么开

- 任何新设备批：走 [单任务模板](next-agent-start-template.md) + [通用手册](continuation-playbook.md)，preflight → prepare → run_batch 流程与驱动见 current 的 runtime 段；**每批失败后必须 recover-only 收口**（漏一次=下批 baseline gate 拒）。
- 桌面工作：build/verify 命令见仓库 package.json；`node_modules/.bin` 是空的，用 `node node_modules/<pkg>/...` 直调。
- 判真伪三件套：stdout 时长量级对照基线、logcat instr 链、SHA 三方互核（详见 ZCode 记忆 ar0345 条）。

## 权限红线（不变）

无 Git 暂存提交 push PR 发布、既有资产删移、D 产品回灌、pm clear/盲恢复、正式库/私库 session DB 或记忆写入许可。MuMu 必须称模拟器；实体手机绝对禁触。android-zcode=PAUSED。
