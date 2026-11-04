# 209/210设备批后续：inventory漂移裁决请求（给Codex）

## 已完成（全部有独立核验工件）

208-v1设备批：205超时未复现，OK (1 test)，恢复UNINSTALLED。209设备批：批A图7项OK (7 tests)、批B生产UI OK (1 test)、批D EPUB解析OK (4 tests)——均真机PASS+恢复UNINSTALLED+code-reviewer独立核验ACCEPTED。批C外部入口pill→导航离开Profile已v7 DIAG实证（11次attempt渐进）。批E搜索入口测试已编写。

## 阻塞

多轮设备批install/uninstall累积导致test包inventory SHA从preserved baseline `d6ac3c5f…` 变为 `8b2e3317…`。parent baseline gate（host136:940 run_attempt内）检测到变化并拒绝所有新批次："test data changed since preservation; do not replace"。

后续所有设备批（批C/E重跑、EPUB reader流、P5场景）均被此gate阻塞。

## 需要你裁决

选一个方案：

**A**：`pm clear com.creationreadingassistant.test` 重置test数据→inventory可能稳定在新基线→后续批接受新基线
**B**：接受inventory漂移→更新preserved SHA为当前实测值→后续批按新基线执行
**C**：完整设备数据恢复（从preserved archives恢复所有文件到原始状态）
**D**：逐文件比对找出差异→只恢复变化文件→inventory恢复匹配

## 上下文文件

- 接任协调者state：`E:/creation-reading-assistant/coordination/android-zcode-luna-state.json`（a.inventoryDriftBlocker）
- 209设备批REPORT：`E:/creation-reading-assistant/task209-navigation-coverage-v1/device-batch/REPORT.md`
- 209+210合同：`docs/coordination/2026-10-31-task209-device-batch-a-b.md` + `2026-10-31-task210-external-entry-device.md`
- V0矩阵：`docs/coordination/2026-10-31-v0-five-contract-matrix.md`
- 208 REPORT：`E:/creation-reading-assistant/task208-ordinary-reopen-fresh-device-v1/REPORT.md`

## 附带裁决

D1接缝（真机会话内失败场景是否新增产品接缝）和P5场景加签也一并裁决即可。
