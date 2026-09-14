# codex-r1-s1：安全滚动 TXT 替换的最小根因修复

日期：2026-09-10
基线：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`（HEAD），工作区既有 224 个脏文件未触碰、未隔离、未提交。
范围：仅 Android reader；不 commit / 不 push；未做真机验收（按任务边界，验收由 WorkBuddy 冻结后执行）。

## 一、根因（代码级闭环，非猜测）

真机缺陷形态：规则已保存且 enabled、替换菜单可用、预览命中，但滚动 TXT 正文在「保存后 / 重进后 / 重启后」三个时点均显示原文。

根因链（每个环节有代码依据）：

1. **小文件滚动 units 的 chapterIndex 恒为 0**（`ReaderScreenDerivedState.kt` 旧 L65-79，由 `chunkPlainText` 派生）。`ScrollingTxtChapterSource.buildScopes` 按 `chapterIndex` 分组 → **整本书坍缩为单一投影作用域**。
2. 测试书 `[此处填写标题]`（DB id `epub_wqn5y3`，1.8MB，约 60 万字符）> 投影上限 `DEFAULT_MAX_CHARS_FOR_PROJECTION = 256_000`（`PagedChapterSource.kt:68`）→ `scopeForSegment` 对**所有** unit 返回 `UnsupportedTooLarge` → `ReplacedSegmentedChapterSource.loadChapter` 降级原文、`projectionForChapter` 返回 null。
3. `loadScrollUnitContent`：exact=null → `ScrollUnitProjection.fromSource(unit, 原文)` identity 投影 → **正文原文、无异常、无失败项**（与真机观测一致）。
4. `preparePagedReplacement` 对「有规则的 segmented path」无条件返回 `APPLIED` → 替换菜单可用、规则可保存（能力裁决与实际投影能力脱节）。
5. 三时点稳定失败：数据形态静态缺陷，任何时刻一致。

旁证与佐证：

- 组装门 `txtChapters.isEmpty()`（原 `ReaderPagerEngineState.kt` L262）也是真实缺陷（CODEX.md 提示命中）：章节未识别时 source 直接不组装 → 菜单调为 `PAGER_ENGINE_DISABLED`。但它与「菜单可用」的实测矛盾，**不是本轮三时点失败的主根因**；本次一并修正（见下）。
- 投影引擎本身无罪：JVM 全链回归测试（fromText → preparePagedReplacement → ReplacedSegmentedChapterSource → loadScrollUnitContent）在连续 units 输入下全绿。
- **真机 trace 无法从既有安装包采集**：ScrollReplaceTrace 埋点提交 `05ebb56`（09-09 20:39）晚于设备 APK 构建（19:23）与安装（lastUpdateTime 17:27）——历史两轮复验「找不到 trace」的真正原因不是缓冲区轮转，而是**埋点从未进入任何已安装包**。
- 设备 DB 现状：`reader_text_rules` 0 行（Phase D 报告 4.4 节自证：临时规则已删除且 DB 复核 0 行，非异常消失）；`highlights` 4 行（Phase C 遗留）。

## 二、改动文件（本轮增量）

| 文件 | 改动 |
|---|---|
| `ui/screen/reader/ReaderTextIndex.kt` | 新增纯函数 `buildChapterAlignedPlainUnits(content, chapters)`：章节识别成功且从 0 完整衔接覆盖全书时按真实逻辑章切块（章内复用 `chunkPlainText` 有界切块、偏移平移、unitIndex 重编）；识别失败/边界不衔接时回退旧全文切块（诚实降级，不伪造逻辑章） |
| `ui/screen/reader/ReaderScreenDerivedState.kt` | 小文件滚动 units 改由 `buildChapterAlignedPlainUnits` 派生（渲染与 source 单一真相）；`txtChapters` 检测逻辑自 ReaderPagerEngineState **逐字上提**至此（keys 增加 `epubBook`），先于 units 就绪，打破「pagerEngine 需要 readingUnits、units 需要 txtChapters」的循环 |
| `ui/screen/reader/ReaderPagerEngineState.kt` | ① `rememberPagerEngineState` 增加 `txtChapters` 参数、删除内部检测 remember；② `prepareScrollTxtReplacement` 增加 `onUnsupportedTooLarge` 透传；③ scrollPreparedSource 组装门**去掉 `txtChapters.isEmpty()`**，无章小书以整书为完整作用域（fromText chapterRanges[0] 覆盖全文，真实完整非伪造），超限由 source 层逐 segment 判定并明确提示 |
| `ui/screen/reader/ReaderScreen.kt` | 调用点接线：`tocProfile` 提前、derived 传 `epubBook/tocProfile/textContent`、`txtChapters` 改从 derived 取、pagerEngine 传 `txtChapters` |

流式 TXT 路径（`txtStreamingDocument != null`）不受影响：units 本就按真实章节构建。

修复后行为矩阵：

| 场景 | availability | 正文 |
|---|---|---|
| 有章（TOC 识别成功）+ 有规则 | APPLIED | **逐章投影，替换生效** |
| 无章小书（<256K）+ 有规则 | APPLIED | 整书单 scope 投影，替换生效 |
| 无章大书（>256K）+ 有规则 | APPLIED + 「当前章节过大」提示 | 保留原文（明确降级） |
| 无规则 | NO_EFFECTIVE_RULES | 原文（可新增规则） |

## 三、回归测试

`android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ScrollReplaceChainRegressionTest.kt`（新增，4 个用例）：

1. `scroll chain applies replacement to every unit display text`：无 TOC 小书全链替换生效 + 坐标映射正确。
2. `scroll chain without rules keeps original text with source coordinates`：无规则 identity 降级对照。
3. `single chapter scope oversized book degrades to original text and fires callback`：**复现缺陷形态**（chapterIndex 恒 0 + >256K）→ 正文保留原文 + onUnsupportedTooLarge 每书恰好一次。
4. `chapter aligned units apply replacement on oversized book`：**修复验证**（章对齐 units + 384K 大书）→ 替换全部生效（24000 处）+ source 坐标映射正确。

## 四、命令与退出码（真实记录）

```
.\gradlew.bat :app:testDebugUnitTest --tests "...ScrollReplaceChainRegressionTest" :app:testDebugUnitTest --tests "...ReaderReplacementCapabilityTest" --tests "...ScrollingTxtChapterSourceTest" :app:compileDebugKotlin --no-daemon
退出码 0；BUILD SUCCESSFUL in 1m 31s
```

测试 XML 实测：ScrollReplaceChainRegressionTest **4/0**、ReaderReplacementCapabilityTest **16/0**、ScrollingTxtChapterSourceTest **3/0**（failures/errors 均 0）。

## 五、未覆盖项

> **2026-09-11 状态更新**（WorkBuddy 在 `workbuddy-r1r2-closure.md` 中逐条核对，原文保留不删）：

- ~~**真机端到端验收未执行**（任务边界禁止）~~ → **已执行并通过**：8 步复验 PASS，
  含「保存后当前屏替换 / 滚离返回 / 重进 / force-stop 重启 / 删除规则还原」；`ScrollReplaceTrace` 三行断言齐全。
  唯一未做子项：**替换后文字高亮跨章锚定**（正文替换已生效，但高亮锚定与坐标回归未实测）。
- ~~真机测试书 format=epub … 疑似导入 format 误判，**待确认**~~ → **已确认并已修复**：
  即 `R1R2-CLOSURE-AND-DEFECTS.md` B5。`feature/library/FormatClassifier.kt` 以首 4 字节 ZIP 魔数
  （`PK\x03\x04`）做真源分类，导入（`ShelfImporter`）与同步（`SyncRepository.kt:459`）共用。
  契约是**拒收**（宁可拒收可疑输入也不擅自改格式），不是降级为 txt；既有 DB 行不自动迁移。
- ~~高亮列表断链 … 为已知独立缺陷，未处理~~ → **已闭环**（源码层，即 B1）：
  `buildAnnotationEntries` 已合并 `highlights` + `notes`，删除 / 撤销 / 编辑 / 改色全部打通。
  ⚠️ 但「我的」tab 的**笔记页 UI 入口**已被产品规划移除（原笔记磁贴改为灵感中心直达），
  NOTES 路由 `profile/notes` 仍注册却无入口——用户可达性待 owner 决策，详见 `workbuddy-r1r2-closure.md` §A5。
- 桌面端、N1/D1 模块、标注路由、删除撤销接线：按边界未触碰。
- `SearchHighlightColorTest`、`AppPaletteTest` 等既有待补测试：与本题无关，未动。

## 六、给 WorkBuddy 的冻结后验收步骤

> **⚠️ 步骤 3 的测试书已需更换（2026-09-11 更正，执行前必读）**
>
> 原步骤 3 指向 `epub_wqn5y3`（1.8MB、声称 EPUB 的 TXT）。B5 `FormatClassifier` 落地后该书：
> ① 重新导入会被拒收；② 书架上既有行是 EPUB 格式，在「上下滚动」模式下取不到 `scrollPreparedSource`
> → `scrollProjectionOn = false` → `effectiveReplacementAvailability` 返回 `PAGER_ENGINE_DISABLED`，
> **替换菜单按设计置灰**，无法进入验收。这是正确行为，不是回归。
>
> **改为：使用一本真实 TXT 格式的样书**（顶栏显示「TXT · 第X章」），规则取该书中高频短语
> （本轮复验用的是 `\Q----\E → TTEAM`，命中 2 处；命中数不必追求数百处，**能稳定判定即可**）。
> 其余步骤（1 / 2 / 4-8）不变。
>
> **本步骤已于 2026-09-11 执行完毕，结论 PASS**，证据见 `workbuddy-r1r2-closure.md` §A1。

1. **构建**：`.\gradlew.bat :app:assembleDebug --no-daemon`（PowerShell，`$env:GRADLE_USER_HOME="D:\develop\env\gradle"`；沙箱环境需禁用沙箱跑 gradle）。
2. **安装**：`install_with_confirm.ps1` 优先，MIUI 超时则回退 `adb -s c49ac6cf install -r app/build/outputs/apk/debug/app-debug.apk`（如实记录安装方式）。
3. **重建规则**：进入测试书（滚动模式，关 TXT 分页兼容）→ 长按选中文本 → 更多 → 替换 → `\Q这个世界\E` → `TTEAM` → 保存启用。此书正文含大量「这个世界」（预览应命中数百处）。
4. **主验收**（PASS 门槛）：
   - 保存后当前屏实际显示 `TTEAM`（视觉截图 + uiautomator 文本双证）；
   - 滚离返回、重进书籍、进程重启后仍替换；
   - 替换后文字建高亮，锚定稳定；
   - App 内禁用规则 → 正文立即还原原文；重新启用 → 恢复替换。
5. **坐标回归**：替换态下选区、书内搜索、TTS 朗读位置、阅读进度恢复均按 source 坐标工作（重进后位置不漂移）。
6. **Trace 断言**（新包首次含埋点）：logcat 过滤 `ScrollReplaceTrace`，应见 `assemble ... source=ReplacedSegmentedChapterSource, availability=APPLIED` 与 `bind source=ReplacedSegmentedChapterSource, projected=true`，以及逐 unit 行 `exact=true`。
7. **无章大书降级抽查**（可选）：任选一本无目录大 TXT（>256K 字符）重复步骤 3-4，应见「当前章节过大，已保留原文」提示且正文原文——此为设计内诚实降级，不算回归。
8. logcat 无 FATAL/ANR；`stay_on_while_plugged_in` 保持 3 不动。
