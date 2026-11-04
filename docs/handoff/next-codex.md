# 下一位 agent 接手说明（2026-11-04 收官版）

> 项目处于**收官待决策**态：两产品线全部标准质量门绿，无未完成的自主工作。接手者先读本文件和 [current](current.md)，然后看"待用户决策"四项——不需要重启任何批次。

## 最小启动

1. 读仓库与用户级 AGENTS；遵守各 agent 私有记忆边界（本仓库历史经验见 ZCode 原生记忆索引）。
2. 读 [current](current.md)：现态、待用户四项、复用边界、候选 SHA。
3. 读 `E:/creation-reading-assistant/coordination/android-zcode-luna-state.json` 的 nextContinuationQueue（nbLine20261103 有 NB01-15 全台账）。旧路径查 [document-map.csv](../document-map.csv)，不加载历史全集。

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
