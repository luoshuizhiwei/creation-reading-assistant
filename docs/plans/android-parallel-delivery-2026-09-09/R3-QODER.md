# 发给 Qoder：R3 选区动作配置、搜索提供方、首选词典与 StarDict 离线词典

> 本文件是 **R3** 轮任务包，替代 R1 的 `QODER.md`（R1 原文保留作记录，不要覆盖它）。
> 工作树与分支由 Codex 在 R3 冻结后创建并指定（建议 `codex/android-qoder-r3`）。**不要在主仓库凑合施工**；
> 若沿用 R1 工作树，先确认基线是否已被 Codex 集成，否则不得开工。

## 先读

1. 仓库 `AGENTS.md`；
2. `docs/plans/android-parallel-delivery-2026-09-09/{README.md, COMMON.md}`（纪律以 COMMON.md 为准）；
3. 本目录 `reports/` 里 Qoder 的 R1 报告（删除切片）、以及 `reports/workbuddy-r2-*`（搜索/导航口径）。

## 开工先做（不许跳）

输出 `pwd` / 分支 / `HEAD` / `git status --short`，以你首次报告的 HEAD 为基线。
然后**逐条核对**下列"现况"，不一致以代码为准并在报告写明：

- `ui/screen/reader/ReaderSelectionToolbar.kt` L62–L83：`SelectionToolbarActionSpec(id, label)` 与
  `selectionPrimaryActions`（`highlight/高亮`、`browser/浏览器`、`copy/复制`、`more/更多`）以及更多菜单
  （`dictionary/字典`、`note/添加批注`、`replace/替换`、`search/书内搜索`、`ai/AI 解读`、`inspiration/记为灵感`、`cancel/取消选择`）
  —— **当前是硬编码列表**，且 `label` 直接写中文、不走进字符串资源。
- `ui/screen/reader/ReaderSelectionExternalActions.kt`：浏览器写死 `bing.com`、词典写死 `dict.youdao.com`，
  优先 `Intent.ACTION_PROCESS_TEXT`，失败回退网页；调用点在 `ReaderLayerBuilders.kt` L262/L267。
- **目前没有任何词典实现**（无 StarDict、无离线词库、无词库导入）——本轮是全新模块。

## 目标（产品语）

用户能调整选区工具条的常用动作；**显隐不会绕过能力门控**（不该出现的动作不会因为配置而出现）。
外部查询由用户显式触发，跳走再回来**正文位置不变**。词典可按"首选词典"配置，**首期支持 StarDict 离线词典**，断网可用。

## 拥有的路径

以 `android/app/src/main/java/com/creationreadingassistant/` 为根：

- `ui/screen/reader/ReaderSelectionToolbar.kt`
- `ui/screen/reader/ReaderSelectionExternalActions.kt`
- 新增 `feature/dictionary/**`（StarDict 解析 / 索引 / 查询 / 词库导入，及其测试）
- 新增 `res/values/strings_dictionary.xml` 及同名 locale 资源

**不归你**：`ui/screen/reader/ReaderLayerBuilders.kt`、`ReaderInteractionLayer.kt`、`ReaderSheetHost.kt`、
`ReaderRoute.kt`/`ReaderScaffold.kt`（Codex 拥有 reader 宿主/投影/接线）、`data/settings/**`（Codex）、
`ui/navigation/AppNavigation.kt`、全部 DAO/entity/schema。需要接线或新设置键 → `SEAM REQUEST`，继续做不依赖部分。

## 必须完成

1. **动作模型可配置**：把硬编码列表抽成可配置模型（动作 id + 标签走字符串资源 + 排序 + 是否显示）。
   默认配置 = 现状（高亮/浏览器/复制/更多），保证老用户观感不变。
2. **门控不被绕过**：能力门控（无生效规则时「替换」、无选区时动作、无 AI 配置时「AI 解读」、
   外部无 handler 时「浏览器/字典」）**必须在配置层之上重新生效**；被隐藏的动作不因配置而出现；
   至少保留一个动作，不允许全隐藏。
3. **搜索提供方可配置**：至少「书内搜索」与「浏览器」两条路径可选；外部跳转仍由用户显式触发。
4. **首选词典可配置**：至少三种模式 ——「内置查询（StarDict 离线）」「系统 PROCESS_TEXT」「网页词典」；
   无可用词典时**明确降级提示**，不静默失败、不假装成功。
5. **StarDict 离线词典（首期范围）**：支持 `.ifo` + `.idx`（含 `idxoffsetbits=64`）+ `.dict` / `.dict.dz` 三件套；
   词库经 SAF 导入后放应用私有目录。**内存必须有界**：不许整本 `.dict` 常驻、不许整份 `.idx` 全量载入后无序扫描；
   `.dz` 需流式/分块解压或按需解压；查询走索引二分。首期不承诺 MDX/其他格式，但接口要留扩展位。
6. **往返不丢位置**（R3 退出条件）：外部查询返回后**正文位置不变**，不重定位、不重置进度、不改阅读模式；
   选区态与返回后的呈现要明确（保留或清理属于产品决策，但必须一致且不破坏 source 坐标）。
7. 不改用户原书、不新增网络依赖；不输出真实书名/正文/API Key/数据库快照。

## 验证与交付

- 定向 JVM：动作配置序列化与向后兼容、门控矩阵（每类动作 × 每个门控条件）、
  查询截断与空白归一、StarDict（小端 32 位 vs 64 位 `idxoffsetbits`、`.dz` 解压边界、二分命中/未命中、坏文件容错）。
- 不留编译红状态；设备验收交 WorkBuddy（离线查询需**断网**验证），安装脚本失败可 `adb install` 回退。
- 报告写 `docs/plans/android-parallel-delivery-2026-09-09/reports/qoder-r3.md`：
  基线 SHA、改动路径、完成项、定向命令/退出码/测试数、**实际未完成项**、SEAM REQUEST、WorkBuddy 验收路径。
- **不要自动进入 R4**；不 stage / commit / push。
