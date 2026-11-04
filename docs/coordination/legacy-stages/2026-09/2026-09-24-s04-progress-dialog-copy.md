# S04 书架「重新同步正文」确认弹窗：进度说明文案修正

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c3c44453921bd6273a233c`（本轮全程未变）
- **日期**：2026-09-24
- **范围**：**仅文案**。未改同步逻辑、未改数据库/DAO/迁移、未动用户数据、未操作真机。
- **文件所有权**：`ShelfRoute.kt` 中该确认弹窗文案、直接相关测试、本报告。

---

## 0. 前置核对：是否已在别处完成

| 检查 | 命令 | 结果 |
|---|---|---|
| 本报告是否已存在 | `ls docs/coordination/ \| grep -i "s04-progress"` | **不存在**（退出码 1），无重复编辑 |
| 旧文案全仓出现处 | `grep -rn "重置章节与阅读进度" --include="*.kt" .` | **仅 1 处**：`ShelfRoute.kt:643` |
| 文案是否走 `strings.xml` | `grep -rn "同步源文件更新" --include="*.xml" android/` | **无命中** —— 该弹窗文案是 Kotlin 字面量，不在资源文件 |
| 是否已有断言该文案的测试 | `grep -rn "同步源文件更新\|源文件已被修改" --include="*.kt" src/test src/androidTest` | **无命中** —— 旧文案没有任何测试保护 |

`docs/code-review/2026-09/2026-09-24-s04-progress-reset-contract-audit.md`（本人上一轮只读审查，
**未被本轮修改**）已给出完整的调用链证据；本轮是它的**执行动作**，不重复论证。

---

## 1. 旧文案与新文案

### 旧文案（`ShelfRoute.kt:643`，修改前）

```
检测到 ${count} 本书${titleText}的源文件已被修改，是否重新同步正文？同步后将重置章节与阅读进度。
```

### 新文案

```
检测到 ${count} 本书${titleText}的源文件已被修改，是否重新同步正文？同步不会主动清除已有阅读记录；但正文更新后章节会重新划分，阅读位置也可能随之变化。
```

进度说明部分抽成常量 `SOURCE_REFRESH_PROGRESS_NOTE`（`ShelfRoute.kt:77`），
整句由纯函数 `sourceRefreshConfirmBody(count, titleText)`（`ShelfRoute.kt:84`）拼装。

### 逐句依据

| 新文案片段 | 依据 | 证据等级 |
|---|---|---|
| 「同步不会主动清除已有阅读记录」 | 替换链路（`refreshFromSource` → `repairFileTyped` → `reselectEpub` / `reselectPlainText`）**不引用** `ReadingProgressDao`；该 DAO 无 delete / clear 方法；全仓无重置调用方 | **源码事实** |
| 「章节会重新划分」 | TXT/MD 章节无持久化表（`AppDatabase` entities 无 chapters 表），每次加载由 `TxtChapterDetector.detect` 对**当前全文**重新检测（`ReaderDocumentLoader.kt:174`）；EPUB 章节随新副本解析 | **源码事实** |
| 「阅读位置也可能随之变化」 | 加载时按新内容夹取旧坐标：EPUB `coerceIn(0, chapters.lastIndex)`、TXT/MD `resolveImportedTextOffset` 的 `coerceIn(0, textLength)`；闸门打开后夹取值被写回 | **静态推断**（依赖用户是否重开并翻页/滚动，未实测） |

> **为什么不用「进度完全不变 / 保留进度」这种更强承诺**：旧坐标会被夹取到新正文上，
> 位置对应关系确实会变。写「不变」是**另一种**不实陈述。故用「可能变化」覆盖不确定部分，
> 只承诺代码中可确证的一半。

---

## 2. 实际改动的文件

| 文件 | 归属 | 改动 |
|---|---|---|
| `ui/screen/shelf/ShelfRoute.kt` | 授权 | 新增 KDoc + `SOURCE_REFRESH_PROGRESS_NOTE`（第 59–78 行）+ `sourceRefreshConfirmBody`（第 80–85 行）；弹窗调用点改为 `sourceRefreshConfirmBody(count, titleText)` |
| `src/test/.../ui/screen/shelf/ShelfSourceRefreshCopyTest.kt` | 授权（新建） | 5 条措辞边界用例 |
| `docs/coordination/legacy-stages/2026-09/2026-09-24-s04-progress-dialog-copy.md` | 授权（本报告） | 新建 |

**仅以上 3 个。** 无 SEAM REQUEST —— 本任务无需触碰授权列表外的产品文件。

改动后 `ShelfRoute.kt` SHA-256：`7de972f60181ee913628978a556171f9c8b2a3229b49ba0084faae1b442eb6a8`
新测试文件 SHA-256：`95655ac901c57e3476dc87aaf5361e589e5c28abfad8c9107fe4a7feefe6b038`

### 为什么抽成纯函数

比照仓库既有先例 `DELETE_BOOK_SELF_DESCRIPTION`（`ShelfSharedComponents.kt:30`）
+ `ShelfDeleteCopyConsistencyTest`：把文案提为常量/纯函数，才能在**纯 JVM** 下钉住措辞边界，
不需要 Compose 运行时。`sourceRefreshConfirmBody` 带 `count` / `titleText` 两个参数，
故做成函数而非常量，以便同时守住建句部分不被改动。

---

## 3. 新增测试的措辞边界（5 条）

`ShelfSourceRefreshCopyTest`：

| # | 用例 | 钉住的边界 |
|---|---|---|
| 1 | `does not claim progress is reset` | 不得含「重置」「清空」「清除阅读进度」「重置章节」 |
| 2 | `states existing reading records are not cleared` | 必须含「不会主动清除已有阅读记录」 |
| 3 | `warns that position may shift after content changes` | 必须含「章节会重新划分」「阅读位置也可能随之变化」；**不得**含把变化说成必然的「阅读位置将重新计算」 |
| 4 | `keeps the leading fact sentence intact` | 前缀事实句逐字不变（含 `count` 与书名括号的拼接） |
| 5 | `is a usable sentence rather than a placeholder` | 非空白、长度 ≥ 20、无未替换的「（）」 |

第 3 条特意同时断言「必须提示」与「不得说成必然」—— 位置漂移是**静态推断**，
措辞只能落在「可能」上，写死会重蹈旧文案「过度承诺」的覆辙。

---

## 4. 验证命令、退出码与结果

在 `android` 目录执行（执行前 `find app/build -mmin -1 \| wc -l` = `0`，无其他窗口构建争用）：

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.ShelfSourceRefreshCopyTest" --no-parallel
```

- **退出码：`0`**，`BUILD SUCCESSFUL in 1m 34s`（`30 actionable tasks: 9 executed, 21 up-to-date`）
- **测试数量：5**（`tests="5"`，`skipped="0"`、`failures="0"`、`errors="0"`，
  取自 `app/build/test-results/testDebugUnitTest/TEST-…ShelfSourceRefreshCopyTest.xml`）

```bash
git diff --check -- android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfRoute.kt
```

- **退出码：`0`**，无空白/换行错误

```bash
grep -rn "重置章节与阅读进度\|同步后将重置" --include="*.kt" --include="*.xml" android/app/src/main
```

- **命中 0 处**（唯一命中是新 KDoc 里引用旧文案做说明，位于 `ShelfRoute.kt:61`，
  不是用户可见文案，是有意保留的对比说明）

`git status --short` 条目数 **465 → 466**（+1 = 新增测试文件；两个改动文件本身此前即为 ` M`）。
HEAD 未变。**未** stage / commit / push / checkout / reset / clean。

---

## 5. 未覆盖范围（不得写成「已验证」）

1. **重映射是否真的发生，未实测。** 「阅读位置可能变化」是静态推断：需要用户**重新打开**
   该书并翻页/滚动，夹取后的坐标才会经 persist 闸门写回。本轮未跑真机、未跑 ADB、
   未安装 APK，无法证明它实际发生。详见
   `docs/code-review/2026-09/2026-09-24-s04-progress-reset-contract-audit.md` §7 的实测清单。
2. **`reading_progress` 行的字段级前后对比未做。** 「行还在」不等于「进度未变」——
   需对比 `current_location_json` / `progress_percent` / `updated_at`。本轮未做。
3. **新文案未在真机上肉眼确认。** 弹窗宽度/换行、长书名拼接后的可读性未验证。
4. **未做 `assembleDebug` / Lint 全量门禁。** 本轮只跑了与改动相称的定向 JVM 测试
   （纯字符串断言 + 一个纯函数，不涉及资源或布局）。
5. **入口 B「重新定位文件」文案未改（越权）。** `BookActionSheet.kt:308-310` 的副标题写的是
   「更换本地文件并**保留**阅读记录」，与本次修正后的入口 A 仍**未对齐**——
   该文件不在本轮授权范围。两个入口走同一个 `repairFileTyped`，进度机制完全相同，
   但一个说「可能变化」、一个说「保留」，构成新的不一致。**需总指挥决定是否立项**。
   注：旧的入口 A「重置」与入口 B「保留」是**直接矛盾**；修正后变为「可能变化」与「保留」
   的**强度差异**，矛盾程度下降但未消除。
6. **搜索索引重建的进度影响未评估。** `refreshFromSource` 末尾会
   `indexRefreshCoordinator.request(bookId)`，重建期间旧 term 行会被
   `deleteByBook` 清除后重算。这不影响 `reading_progress`，但本轮未评估它对
   「阅读位置/章节定位」的间接影响，也未在文案中提及。

---

## 6. 与其他窗口的边界

- 本轮**未回退、未覆盖**任何他人的工作区改动；只在我自己授权的两个文件上追加。
- 未进入也未修改原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未操作真机、未访问用户书库、未执行任何 git 写操作。
- 报告与 `docs/code-review/2026-09/2026-09-24-s04-progress-reset-contract-audit.md` 是**同一条线的两段**：
  后者是只读审查结论，本文件是执行落地。两者均未写真实书名或私有路径。
