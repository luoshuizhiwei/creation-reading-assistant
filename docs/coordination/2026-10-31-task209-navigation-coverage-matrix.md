# 209-v1（C5）：导航剩余运行覆盖矩阵与图/生产测试独立runner适配（待授权）

2026-10-31。208-v1已按C2第1行接受（业务PASS单方法+恢复可核，见`E:/creation-reading-assistant/task208-ordinary-reopen-fresh-device-v1/REPORT.md`）。本卡是手册§9(C5)的落地任务文档；**准备阶段只读，设备执行需用户对本卡明确授权后另记批次**。

## 目标（可观察结果）

1. **覆盖矩阵（离线可完成）**：以208实际覆盖为基线，逐项列出导航/保存路径的运行覆盖账——已覆盖（208成功链：S0→T1→T2→T1→S0→一次退出书架→重开S0、恢复）、图壳seam可证、生产组合未运行、不可自然触发四类。输入：208 REPORT、207-r3/r4报告、冻结203方法体、`ReaderTemporaryNavigationStackTest.kt`（`d2a08684…`）、`ReaderTemporaryNavigationProductionTest.kt`（`811cae6d…`）。
2. **runner适配（离线）**：现有`collector192`冻结为203单方法/expectedCount=1，不能直接跑图测试7项或生产UI 1项。产出一个**新的独立离线runner/collector适配**（新任务根，不改冻结192/203）：输入写具体class#method/expectedCount，先定向离线验证argv解析/绑定/拒绝错误输入（复用task176-r4严格离线回放方法），再交独立设备合同执行。
3. **失败/取消场景缺口（只列账）**：reader内生产临时入口目标恒为自身book、取消窗口racy无合法钩子（208 REPORT §7缺口引用）——按手册§9.4判定是否现有产品可达风险；确需新注入接缝时列最小文件与证据目标给用户裁决，不擅自实现。

## 输入与SHA

- 208报告/ready：`E:/creation-reading-assistant/task208-ordinary-reopen-fresh-device-v1/`（业务PASS原证路径见其REPORT §4/§5）。
- 207-r4：`E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r4/`（图测试`d2a08684…`、生产测试`811cae6d…`、APK对`c396ebe1…`/`e4b7b70b…`）。
- 冻结机制：`collector192.py`（`9e406c88…`）、`host192.py`（`1e47afdc…`）只读；179r3/184r2/166机制只读。

## 所有权与权限

- 唯一candidate/Gradle owner独立（若有并行工作互斥）；本卡离线阶段不占设备。
- 可写：新任务根`E:/creation-reading-assistant/task209-navigation-coverage-v1/`（矩阵+runner适配源+raw）；冻结host/collector/consumer不可写。
- 无设备/ADB/VM/Git/删移/记忆许可直到用户对本卡设备批明确授权；图测试7项+生产UI 1项的设备执行需独立合同与fresh全套机制（照208模式：fresh179r3+26role+166恢复）。

## 验证与交付

- 矩阵：每行给证据绝对路径+覆盖级别，不写"全部覆盖"。
- runner适配：严格离线回放全绿（真实argv构造+拒绝非法输入+actual receipt绑定），保留原rc/log；**不称设备通过**。
- 交付`task209-navigation-coverage-v1/REPORT.md`+`ready.json`+runner源+原证；预算：source/runtime/report/ready≤256KiB、raw≤256KiB。

## 停止条件

输入漂移（207/208原证SHA变化）、冻结机制需改才能达成（如实列缺口停）、设备授权缺失（矩阵+适配交付即PARTIAL完成）。

这是任务209-v1（C5）准备合同。
