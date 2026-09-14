# 发给 WorkBuddy：R3 独立验收（只验不修）

> 本文件是 **R3** 轮任务包，替代 R1 的 `WORKBUDDY.md`（R1 原文保留作记录，不要覆盖它）。
> 工作目录 `D:/develop/Code/Codex/creation-reading-assistant`。
> **只在 Codex 明确交付冻结集成快照后才启动完整验收**；三方仍在开发或缺报告时说明依赖，
> 不能验旧 APK 后宣布新功能 PASS。

## 纪律

不修改产品源码 / 测试 / 脚本，不 stage / commit / push / reset / clean / stash。
设备命令一律 `adb -s <serial>`，只用真实手机（**禁用 MuMu**）；`stay_on_while_plugged_in` 记录原值、结束恢复。
测试书一律中性命名，只用自己创建的中性 fixture，删除只针对自己创建的资料。
报告写 `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r3.md`。

## 证据基线

记录 branch / HEAD / 所有权内 diff / 未跟踪产品文件；核对被验 APK **来自该冻结快照**，记录 SHA-256 与安装结果。
既有测试输出仅作参考；缺证据时补跑缺失门禁，有可信当前证据则不重复昂贵步骤。

门禁（android 目录）：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`，
记录命令、退出码、tests/failures/errors/skipped、Lint 条目。

## 安装授权

先 `android/scripts/install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>`；
**用户已明确授权**：脚本超时或 MIUI 拦截后，保留失败日志，允许 `adb -s <serial> install -r <absolute-apk>` 回退
（test APK 需要时 `-r -t`）。回退允许继续验收，**但不得写成脚本 PASS**。
不要求用户手动点 MIUI，不改安全设置，不卸载、不清应用数据。

## 验收矩阵

### A. P1 全局 / 本书两级设置
- 全局改一项（如字号）→ **未做过本书覆盖**的书跟随生效。
- 某书**显式覆盖**该键后 → 再改全局，该书**不变**；未覆盖的其他项仍跟随全局。
- 「清除本书覆盖」→ 回落全局；「恢复默认」有明确作用范围说明与二次确认。
- 恢复默认**不得**顺带改动阅读进度、替换规则、笔记/高亮、统计。
- 预设：套用后逐项可改回；套用预设不产生未声明的副作用。
- 繁体显示、TTS 定时停止：**未被重复实现、未被破坏**（既有行为回归）。
- 跨窗口 / 旋转 / 进程重启后设置一致，无静默回落全局。

### B. X1 选区动作 / 搜索提供方 / 首选词典
- 调整常用动作 → 工具条内容/顺序随之变化；**门控不被绕过**：
  无生效规则时「替换」不可出现、无 AI 配置时「AI 解读」不可出现、无外部 handler 时外部动作有明确反馈；
  不允许全部隐藏。
- 搜索提供方切换生效（书内搜索 vs 浏览器）。
- 首选词典三种模式：内置（StarDict 离线）/ 系统 PROCESS_TEXT / 网页词典；无词库时有明确降级提示。
- **StarDict 离线**：导入 `.ifo/.idx/.dict(.dz)` → **断网**查询可用、命中正确；大词库下不 OOM、不卡主线程。
- **往返不丢位置**（R3 退出条件）：外部查询 / 词典 / 浏览器返回后**正文位置不变**、进度不重置、阅读模式不变。

### C. 回归（不得回退）
- TXT 分页与滚动、EPUB、Markdown 的能力门控不越界；替换规则生效范围不变。
- 选区 / 高亮 / 笔记 / 书内搜索 / TTS / 阅读进度恢复仍按 source 坐标一致。
- 无 FATAL / ANR；结束时清理临时数据并归还设备设置。

## 输出

逐项 **PASS / FAIL / BLOCKED**，并列：未覆盖项、截图/录屏路径、设备副作用与恢复结果、与被验快照的对应关系。
**入口可点、预览命中、JVM 通过都不能代替"配置范围正确 + 离线查询可用 + 往返位置不变"的真实证据。**
发现失败时回交对应负责人（Trae/Qoder/Codex），不自行修改代码。
