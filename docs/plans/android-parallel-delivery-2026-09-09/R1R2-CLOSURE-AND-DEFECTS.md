# 发给接手 Agent：R1/R2 闭环与已知缺陷收口

> 可直接粘贴给该 agent 的提示词。与 **R3** 并行推进，两边不得互相改对方文件。

请直接实施本任务。工作目录 `D:/develop/Code/Codex/creation-reading-assistant`。
先完整阅读仓库 `AGENTS.md`、`docs/plans/android-parallel-delivery-2026-09-09/{README.md, COMMON.md}`（纪律以 COMMON.md 为准），
以及本目录 `reports/` 下的 `codex-r1-s1-scroll-replace.md`、`qoder-r1.md`、`qoder-r1-s1-capability.md`、
`workbuddy-r2-j1-i2-*.md`、`workbuddy-r2-s1-6-device-verification.md`、`workbuddy-r2-s1-7-index-sweep-fix.md`。

## 你的职责边界

**你只做「R1/R2 未闭环项 + 已记录缺陷」**。另有一个并行 agent 正在实施 **R3**（设置两级 / 选区动作 / 词典），
**以下路径归 R3，你不要改**：`data/settings/**`、`ui/screen/reader/sheets/ReaderSettingsSheet.kt`、
`ui/viewmodel/SettingsViewModel.kt`、`ui/components/SettingsComponents.kt`、`ui/screen/profile/ReaderSubPage.kt`、
`ui/screen/reader/ReaderSelectionToolbar.kt`、`ui/screen/reader/ReaderSelectionExternalActions.kt`、
新增 `ui/screen/settings/**`、新增 `feature/dictionary/**`、新增 `strings_settings.xml`/`strings_dictionary.xml`。
需要这些文件配合时走 SEAM REQUEST，不要直接改。

**共享脏工作区纪律**：主仓库有 80+ 修改与 200+ 未跟踪文件（多切片并行）。不 stage / commit / push，
不 `reset` / `checkout` / `clean` / `stash`，不覆盖他人改动。当前 HEAD 参考 `a9a3bce`。

## 开工先做

输出 `pwd` / 分支 / `HEAD` / `git status --short`。
然后**逐条核对下面每项在当前代码里是否仍然成立** —— 部分可能已被其他 agent 修掉。
**以代码为准**：已修的直接标注「已闭环（附证据）」并跳过，**不要为凑清单制造改动**。

## A. 真机验收缺口（只验不修，设备项）

1. **【最高优先】安全滚动 TXT 替换 8 步复验**——唯一「代码已修但证据缺失」的功能项。
   前两轮真机 FAIL 记录见 `.workbuddy/memory/2026-09-09.md`（验收 3 / 验收 4）；
   09-10 已做根因修复（章对齐 units + 去掉 `txtChapters.isEmpty()` 门控），**复验未做**。
   8 步流程见 `reports/codex-r1-s1-scroll-replace.md` §六。
2. **R1 分项验收报告入仓**：`reports/` 下目前**没有** `workbuddy-r1-*`；
   四轮验收报告散落在仓外 `C:\Users\23254\cra-scroll-replace-*.md`。补 `reports/workbuddy-r1.md`。
3. TTS **通知触发 + 重启恢复**真机证据（P3.2 片 2–3 已实现，缺证据）。
4. 临时查阅**跨书 / 多层 LIFO** 矩阵真机验证（`reports/workbuddy-r2-j1-i2-*.md` §7）。
5. N1 笔记页（筛选栏 / 选择态 / 底栏 / 导出对话框）真机视觉（`reports/trae-r1.md` 验收路径 1–7）。

## B. 缺陷修复（按所有权分工，先核对是否仍存在）

1. **高亮列表断链**：笔记 tab 从不收录高亮（DB `highlights` 有行），因而**无任何 UI 删除入口**。
2. **删除入口无撤销条**：首页继续阅读面板、归档页、阅读器内（`reports/qoder-r1.md` §6.3）。先核对是否已被 R1/R2 集成修掉。
3. **EPUB `APPLIED` 未收窄**：现有章长是 ZIP 估算值，用它判超限会误降级；需 EPUB 块级长度元数据接口（`reports/qoder-r1-s1-capability.md` §五.2）。
4. **替换降级提示只弹一次 Snackbar**，tab 内无常驻横幅（同报告 §五.3，已提 SR-2）。
5. **导入格式误判**：1.8MB 伪装 EPUB 的 TXT，顶栏显示「EPUB · 第1章」；落库前应按内容魔数判格式（SR-1）。
6. **R2-D2「正文移除 / 重新关联」**：内容哈希匹配、文件变更时定位风险、资料保留。本轮已落「`REMOVE_CONTENT` 真删文件」，重新关联侧无交付。
7. **索引遗留**：`#21 epub_pc57ry` 停在 783/784（旧锚点残留，非回归）；`incompleteBookIds()` / `rebuildAll()` 零 UI 调用方（产品决策）；
   display 反查边界 / EPUB 双通道 / `preview_only`·`failed` 覆盖率未覆盖。
8. **Markdown 结构保真 / 源↔渲染映射契约**、真实 legacy 滚动路径：若不实施，请在活动文档**明确写「不实施」**，不要长期悬空。
9. **JVM 无 Robolectric**（无真实 SQLite）：删除切片外键 / `IN ()` 语义靠人工建模（SR-3；需改共享构建文件）。

## 验证与交付

- 每条给出：**是否仍成立** → 根因 → 改动路径 → 定向命令 + 退出码 + 测试数 → 真机证据（截图/录屏/XML）→ **未覆盖项** → SEAM REQUEST。
- 设备：一律 `adb -s <serial>`，只用真实手机（**禁用 MuMu**）；安装先走 `android/scripts/install_with_confirm.ps1`，
  超时/MIUI 拦截后**允许** `adb -s <serial> install -r <apk>` 回退（保留失败日志，**不得写成脚本 PASS**）。
  `stay_on_while_plugged_in` 记录原值并恢复；用中性 fixture 书，删除只删自建数据。
- 报告写 `reports/<agent>-r1r2-closure.md`（同一 agent 多条可合并）。
- **不把开发检查点当 PASS，不 push。**
- 发现旧结论已作废时，**在源头文档改掉**（例：`workbuddy-r2-s1-8-handoff-to-next-agent.md` §6.4 的「P1 未处理」
  已由 `reports/workbuddy-r2-s1-9-*.md` 更正——多 agent 并行时滞后结论必须回改，不能只在新报告里加一句）。
