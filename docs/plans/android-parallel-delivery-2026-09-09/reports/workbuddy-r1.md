# WorkBuddy R1 真机验收汇总（只验不修）

日期：2026-09-09（验收 1–4）／入仓整理：2026-09-11
验收方：WorkBuddy（独立验收，全程未修改源码、测试、脚本、文档；未 stage / commit / push / reset / checkout / clean）
设备：真机 `c49ac6cf`（MIUI），全部命令显式 `adb -s c49ac6cf`；**未使用 MuMu 模拟器**
命名纪律：本报告一律使用「测试 TXT」「测试 EPUB」等中性标签，不记录真实书名与正文片段

> 入仓说明：验收 3 / 验收 4 的原始报告此前仅存在于仓外 `C:\Users\23254\cra-scroll-replace-*.md`，
> 验收 1 / 验收 2 仅存在于 `.workbuddy/memory/2026-09-09.md`。本文件为**收拢汇总**，
> 与原始报告结论一致，不做二次加工掩盖结论。后续复验结论见 `workbuddy-r1r2-closure.md`。
>
> **⚠️ 复验须知（2026-09-11 补记）**：验收 3 / 4 使用的 1.8MB「声称 EPUB、正文实为 TXT」的样书，
> 在 B5（`FormatClassifier`）落地后**已不能再用于安全滚动 TXT 替换复验**——两重原因：
> ① 新增导入会被真源分类直接拒收（`Rejected("声明为 EPUB 但正文不是有效的 ZIP 文件")`）；
> ② 既有 DB 行虽未迁移，但 EPUB 格式书在「上下滚动」模式下取不到 `scrollPreparedSource`
> → `scrollProjectionOn = false` → `effectiveReplacementAvailability` 返回 `PAGER_ENGINE_DISABLED`，
> 替换菜单按设计置灰。**后续复验一律改用真实 TXT 格式的样书**（详见 `workbuddy-r1r2-closure.md` §A1）。

---

## 验收 1：书架 / 弹窗 / 目录 UX

- **基线**：`main@1ff74b8`（unify reader interaction surfaces），无暂存，243 条未提交。
  核心改动：`BookGrid` / `BookCover` / `GlassDialogs` / `ReaderTocSheet` / `ReaderTocState` / `Shape` / 两个测试文件。
- **静态审查（全 PASS）**：书籍级三点菜单两处全部删除无残留；`onClick` 保留、长按新增 haptic 与语义标签；
  `BookCover` 不再消费 `book.format`（留有过时注释，观察项）；居中对话框四角圆角收敛、底部面板只圆顶部；
  目录卷分组与折叠保留、TOC 行改为阅读清单布局；字数标签 TXT/MD 精确、EPUB 标注「约」；标签与标题同源同长。
- **门禁**：单命令 4 任务 EXIT=0（7m6s），**207 suites / 1695 tests / 0 失败**；`lintDebug` 0 error / 4 warning；
  APK 51,010,522 B，md5 `6937032e7939124a2fd694be5b8bcc8d`。
- **真机**：C1 网格 / C2 列表 / C3 多选 / C4 测试 TXT 目录（跳章 + 恢复）/ C5 测试 EPUB（约 1453 字 / 约 1.3 万字、1/1774 章）
  / C6 浅色纸张排版 / C7 三类对话框圆角 / C8 底部面板贴底 **均 PASS**。
  卷标题与折叠**仅静态验证**（所测 TXT 无卷结构）。
- **安装**：`install_with_confirm.ps1` 连续 4 次超时（3s / 92.6s / 94.9s / 92.5s）——根因为 MIUI「允许 Shell 安装」
  会话弹窗晚于脚本 90s 窗口。用户随后改令由助手自行安装 → `adb install -r` Success。
  **该成功是回退安装成功，不代表自动安装脚本 PASS。**
- **现场**：`stay_on_while_plugged_in` 全程 = 3 未动；书库 25 本未删任何数据；阅读统计与打卡为使用副产物，已如实记录。
- **判定：PASS（含观察项，不含阻塞项）**

## 验收 2：文本选区动作扩展

- **基线**：`main@7499dcf`，无暂存，243 条未提交。
- **静态（全 PASS）**：一级四项 = 高亮 / 浏览器 / 复制 / 更多；更多七项 = 字典 / 添加批注 / 替换 / 书内搜索 /
  AI 解读 / 记为灵感 / 取消选择（逐字同序）；空选区 → `NO_HANDLER` 不启动；字典先 `ACTION_PROCESS_TEXT`
  chooser 后网页回退；替换草稿 `\Q…\E` 字面转义 + 本书作用域、不自动保存。
- **真机 PASS**：工具栏四动作无裁切；浏览器跳转返回后位置不变；字典 chooser 正常；批注对话框正常。
- **披露偏差 F**：滚动模式（TXT 分页兼容 = 关闭）下替换入口仍 `enabled=true`，与当时的旧契约「滚动必须禁用」冲突。
  静态定位：`effectiveReplacementAvailability` 在 `scrollProjectionOn` 时有意放行。该偏差**引发后续契约修正**。
- **判定：PASS（含 1 项披露偏差 F + 1 项设备设置偏差：复核时发现 `stay_on_while_plugged_in` 被改为 null，已恢复 3）**

## 验收 3：安全滚动 TXT 替换窄复验（契约修正后）

- **契约修正**：安全滚动 TXT（`ScrollingTxtChapterSource` 能取得完整逻辑章 source）替换**可启用且必须实际生效**；
  legacy / 不完整滚动路径、EPUB、Markdown 须禁用或保留原文。旧验收 2 的 F 项按新契约重验。
- **流程**：TXT 分页兼容模式切「关闭」进入滚动视图（整章连续 TextView，非分页引擎）→ 选区更多菜单「替换」`enabled=true`
  （uiautomator dump 证实）→ 规则编辑器字面转义正确、作用域本书、编辑器预览命中（7 处 / 258 处）→ 保存并启用成功。
- **核心 FAIL**：正文实际替换从未生效。开篇前置页与真实章节正文两处独立取样均显示原文（视觉截图 + accessibility 文本一致），
  重进书籍整书重载后仍为原文。滚动路径呈现「菜单可启用、规则可入库，但渲染管线从未应用替换」的中间态，两种契约均不完全满足。
- **高亮**：创建 / 会话内滚动锚定 / 退出重入渲染锚定 **全 PASS**；进程重启后笔记 tab 显示「暂无划线与笔记」，
  但只读 DB 查询证实 `highlights` 表实际存在 4 行（3 条本次 + 1 条既往，`deleted_at` 均为 NULL）
  ⇒ **高亮已持久化但笔记列表从不收录**（列表查询缺陷），进而**无任何 UI 删除入口**，3 条临时高亮清理 BLOCKED。
- **清理**：2 条临时规则 App 内删除成功（空态 + `reader_text_rules` = 0 行双证实）；TXT / EPUB 分页兼容均已恢复「自动」。
- **稳定性**：logcat 无 `FATAL EXCEPTION`、无 ANR，crash buffer 空。观察项：选区状态在菜单开合 / 重启后偶发残留。
- **副作用**：模式切换导致该书阅读进度由 99% 重置为开篇（后推至 ~1.1%），如实记录。
- **判定：FAIL**（核心失败项 = 正文实际替换未生效）
- **高亮列表断链**：本项即 `R1R2-CLOSURE-AND-DEFECTS.md` B1。2026-09-11 源码核对**已闭环**
  （`buildAnnotationEntries` 已合并 `highlights` + `notes`，删除 / 撤销 / 编辑 / 改色全部打通），详见 `workbuddy-r1r2-closure.md` §B1。
- 原始报告：`C:\Users\23254\cra-scroll-replace-reverify-20260909.md`

## 验收 4：滚动 TXT 替换渲染修补复验

- **本轮相关 WIP**：`ReaderContentHostPlainTextBranch.kt`、`ReaderContentHost.kt`、`ScrollingTxtChapterSourceTest`、
  `ReaderReplacementCapabilityTest`、`docs/plans/replace-rules-render-integration-design.md`。
- **门禁**：定向测试 EXIT=0（27s），`ReaderReplacementCapabilityTest` **16/0** + `ScrollingTxtChapterSourceTest` **3/0** = 19/0；
  `assembleDebug` EXIT=0（42s），APK 51,968,353 B，md5 `339a676d6b905250a25be42c7ba8959f`（≠ 上轮 `015eb08f`，确认含修补）。
- **安装**：`install_with_confirm.ps1` 再次 EXIT=1（MIUI 弹窗超时，日志 `rrv-install.log`）→ 回退 `adb install -r` Success（17:27:12）。
- **真机（核心）**：规则 `\Q…\E → TTEAM`、作用域本书、编辑器预览命中 251 处、保存并启用 ✓。
  三个判定点**全部未替换**：
  1. 保存后 —— 同屏 3 处目标短语显示原文；
  2. 滚动离开再返回 —— 仍原文；
  3. 退出阅读器重新进入（整书重载）—— 无障碍 dump：6 处原文 / **0 处替换文本**；
  4. 进程重启后冷启 —— dump 同样 6 处原文 / 0 处替换文本。
- **高亮步骤 BLOCKED**：正文从未出现替换后文字，前置条件不成立（非跳过）。
- **清理与恢复**：临时规则 App 内删除成功（DB `reader_text_rules` = 0 行）；TXT / EPUB 分页兼容恢复「自动」；
  `stay_on_while_plugged_in` 3 → 3；既有高亮 4 行未触碰；进度副作用再次重置开篇。
- **稳定性**：logcat recent 8000 行 `FATAL EXCEPTION` 计数 0、无 ANR、crash buffer 空。
- **判定：FAIL**。JVM 定向测试 19/0、菜单可用、规则已保存、预览命中 251 处**均不构成 PASS 依据**。
- 原始报告：`C:\Users\23254\cra-scroll-replace-fix-reverify-20260909.md`

---

## 2026-09-11 复验补记（结论更新）

| 项 | 原判定 | 现状态 | 依据 |
|---|---|---|---|
| 验收 3 / 4 核心失败项（正文替换未生效） | FAIL | **已修复并复验 PASS** | 改用真实 TXT 样书执行 8 步复验：规则 `\Q…\E → TTEAM`、本书 scope、命中 2 处；重进 / 滚离返回 / force-stop 后均持久；删除规则后正文还原为原文。`ScrollReplaceTrace` 三行齐全（`availability=APPLIED` / `projected=true` / `unit=0 exact=true, scopeHits=1`）。详见 `workbuddy-r1r2-closure.md` §A1 |
| 验收 3 高亮列表断链（B1） | 阻塞 | **已闭环**（源码层） | `buildAnnotationEntries` 合并两类注解；删 / 撤销 / 编辑 / 改色全通 |
| 验收 3 / 4 测试书（1.8MB 声称 EPUB 的 TXT） | 可用 | **不再适用** | B5 落地后拒收；且 EPUB 在滚动模式下替换菜单按设计置灰。见文首「复验须知」 |
| 验收 3 / 4 的进度重置副作用 | 已记录 | 维持原记录 | 属模式切换的历史副作用，非本轮引入 |

**保留原判定的说明**：验收 3 / 4 的 FAIL 是**当时**的真实结论，不做回溯改写。
修复与复验结论以本补记与 `workbuddy-r1r2-closure.md` 承载，两处口径一致。

---

## R1 汇总结论

| 项 | 判定 | 阻塞点 |
|---|---|---|
| 验收 1 书架 / 弹窗 / 目录 UX | PASS | 无（1 项过时注释观察项） |
| 验收 2 文本选区动作扩展 | PASS | 无（偏差 F 已由契约修正承接） |
| 验收 3 安全滚动 TXT 替换 | **FAIL**（2026-09-11 复验 **PASS**） | 当时：正文实际替换未生效；高亮列表断链导致无删除入口 |
| 验收 4 滚动 TXT 替换渲染修补复验 | **FAIL**（2026-09-11 复验 **PASS**） | 当时：修补后行为与修补前一致，三时点均未替换 |

验收 3 / 4 的 FAIL 后续由 `codex-r1-s1-scroll-replace.md`（2026-09-10）定位根因并修复：
小文件滚动 `readingUnits` 的 `chapterIndex` 恒为 0 → 整书坍缩为单一投影作用域 → 超过
`DEFAULT_MAX_CHARS_FOR_PROJECTION = 256_000` 时全部判 `UnsupportedTooLarge` → 降级原文；
而 `preparePagedReplacement` 对有规则的 segmented path 无条件返回 `APPLIED`，造成「能力裁决与实际投影能力脱节」。
**该修复在本验收汇总时尚未经真机复验**，复验结论见 `workbuddy-r1r2-closure.md` §A1。
