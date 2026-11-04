# Android 目录卷标判定修复（H 片）+ 仓库 git 阻塞通报

日期：2026-09-18
状态：**代码与测试已落地工作区，尚未提交**（原因见第 4 节，属仓库级阻塞，不是本片问题）
影响面：仅 `android/`，未触碰桌面端
另含：给「自定义页眉页脚」功能 owner 的整改清单（第 7 节，本人未改动那片 WIP）

---

## 1. 缺陷：`isVolumeHeader` 用子串判据，吞掉正常章节

`ui/screen/reader/sheets/ReaderTocModels.kt` 的旧实现：

```kotlin
return title.contains("卷") || title.contains("部") ||
    title.contains("Part", ignoreCase = true) || title.contains("Volume", ignoreCase = true) ||
    Regex("^第[一二三四五六七八九十\\d]+[卷部]").containsMatchIn(title)
```

`contains` 是**子串**判据，而 `readerTocEntries()`（同文件）对判定为卷名的条目返回 `null`：

```kotlin
if (isVolumeHeader(title)) { volume = title; null }
```

后果有两层，且都是静默的：

1. 任何**标题里恰好含「卷」或「部」字**的章节，从目录列表里整条消失（跳章、章数对不上、上下章跳转错位）；
2. 该标题随即被当作后续所有章节的**分组名**，目录里会出现一串像正文的句子当卷名。

第三行的 `containsMatchIn` 同样是子串匹配（只要求串里某处出现「第X卷」前缀），并不比前两行严格。

实测样本：用户提供的三份手工 TXT 之一（编号为三位补零 + 空格 + 标题的形态）里，422 个章节标题中有 **10 个**被误判吞掉，形如：

| 误判形态 | 触发字 |
|---|---|
| `NNN 举报试卷怎么样呢？算作弊吗？` | 「试卷」 |
| `NNN 她没去拳击部` | 「拳击部」（且正好在行尾） |
| `NNN 系统的部分规则` | 「部分」 |
| `NNN 长谷部同学：…` | 姓氏里的「部」 |

即：**「编号 + 空格 + 标题」这类 TXT 一旦标题里出现试卷/各部/部分/带「部」的姓氏就会掉章**。EPUB 与 Markdown 的目录条目走同一个函数，所以不止 TXT。

## 2. 修复

改为「整行就是一则卷标」的严格形态判定，并把正则提到文件级预编译（该函数对每个目录条目都要跑）：

```kotlin
private const val VOL_NUM = "[0-9０-９零〇一二三四五六七八九十百千两]"

private val VOLUME_HEADER = Regex(
    "^(?:第$VOL_NUM{1,6}[卷部]|卷之?$VOL_NUM{1,6}|[上中下][卷部])" +
        "(?:[ \\t　:：，,、—·-].{0,10})?\$" +
        "|^(?:vol|volume|part)[ .．、:：_-]*[0-9ivxlcdm]{1,8}\$",
    RegexOption.IGNORE_CASE,
)

private const val VOLUME_HEADER_MAX_LENGTH = 24

internal fun isVolumeHeader(title: String): Boolean {
    val t = title.trim()
    if (t.isEmpty() || t.length > VOLUME_HEADER_MAX_LENGTH) return false
    return VOLUME_HEADER.matches(t)
}
```

要点：

- 覆盖 `第一卷` / `第1卷` / `第二部` / `卷一` / `卷之三` / `上卷` / `中卷`，以及带短副标题的 `第一卷 风起云涌`、`第1卷：少年篇`；英文 `Vol.1` / `Volume 2` / `PART IV`。
- **`篇/册` 有意不纳入**：旧实现也没有，加上会把「番外篇」变成卷名，属扩大行为面，需要单独决策。
- 全角空格 `　`（U+3000）显式列出：TXT 正文与标题普遍以 `　　` 缩进，检测侧 `String.trim()` 会去掉它，但这里显式容忍更稳。
- 先做 24 字长度闸，再谈形态；卷标不会长到这个数。
- `groupChaptersByVolume()` 与 `readerTocEntries()` 共用该判据，签名与语义方向均未变，调用方无需改动。

新增回归测试：`app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/VolumeHeaderTest.kt`（JVM，无需设备），锁住上述 10 个误判形态、卷标正向形态、全角缩进、以及 `readerTocEntries` / `groupChaptersByVolume` 的端到端行为。夹具已做中性化，不记录真实书名。

## 3. 验证结果

在独立 detached worktree（`cra-slice-verify`，基线 `eebd59d`）中执行，未在共享工作区跑构建：

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest --tests *VolumeHeaderTest*` | 退出码 0，5 个用例全过（`tests=5 failures=0 errors=0 skipped=0`），任务真实执行未命中缓存 |
| `:app:testDebugUnitTest --rerun`（全量） | 退出码 0，**260 套件 / 2288 测试 / 0 失败 / 0 错误 / 0 跳过**，5m26s |
| 相对基线 | 基线为 259 套件 / 2283 测试，差值恰为新文件的 5 个用例，**无任何既有用例回归** |
| 空白卫生 | 新增 25 行，0 行尾空白、0 CR、EOF 正常；工作树为 LF（`.gitattributes` 的 `eol=lf` 生效），`git diff --check` 恢复可用后应通过 |

**尚未执行**：`lintDebug`、`assembleDebug`、`compileDebugAndroidTestKotlin`，以及真机/视觉验收。

改动仅限 2 个文件，均为干净文件（`ReaderTocModels.kt` 提交前 `git status` 为空；`VolumeHeaderTest.kt` 为新增）。脏文件 `ReaderTocChapterList.kt`、`ReaderTocSheetTest.kt` 属其他 Agent 的 WIP，本次**未触碰**，且因签名未变不影响其编译。

## 4. 仓库级阻塞（所有 Agent 请注意）

本仓库当前是 **`blob:none` 的 blobless 部分克隆**，且 github.com 不可达，导致大量 git 只读命令报错。这**不是磁盘对象损坏**，请勿按损坏处理。

诊断证据：

```
$ git config --get remote.origin.partialclonefilter
blob:none
$ ls .git/objects/pack/*.promisor | wc -l
39
$ git ls-remote --heads origin
fatal: unable to access 'https://github.com/...': Failed to connect to github.com port 443 after 21097 ms
$ git diff --name-only fd7f908 HEAD
fatal: unable to read tree (15c687cffadfe7a6fc055f53a7338b52abe7e374)
```

现象与影响：

- `git fsck` 会刷出大量 `broken link from tree … to tree …` 与 `invalid sha1 pointer in cache-tree of .git/index`——那是尚未懒取到本地的 blob/tree，**不是真损坏**；同一批 oid 在网络可用时曾成功读取。
- **任何跨提交的 diff/log 目前都跑不了**（`git diff <range>`、`git log` 穿过未本地化的 tree 即失败）。因此本片提交前强制要求的 `git diff --cached --name-status` / `--check` **无法执行**，这是本片暂缓提交的唯一原因。
- `git status` 仍可用；新建 worktree（需展开整棵树）在当前状态下会失败。

处置约定：

1. **不要**跑 `git gc`、`git prune`、`git gc --prune=now`，**不要**删 `.promisor`。部分克隆下这些对象只在远端，误删不可逆。
2. 恢复网络后按标准路径把部分克隆补全为完整克隆，再谈提交：
   ```bash
   git config --unset remote.origin.partialclonefilter
   git config --unset remote.origin.promisor
   git fetch --refetch origin        # 重新拉全量对象
   git fsck --no-dangling            # 应不再出现 broken link
   ```
3. 期间**不要提交**：在 cache-tree 指向不可读 tree 的状态下做 hunk 级精确暂存，风险是静默错暂存，而提交前校验恰好是跑不了的那一条。

## 5. 本片收尾清单（网络恢复后）

1. 只暂存这 2 个文件；`git diff --cached --name-status` 必须恰好 2 条，`git diff --cached --check` 必须 0。
2. 建议提交信息：`fix(android-toc): match volume headers by whole-line form`
3. 基于新 tip 创建**全新** detached worktree，串行补跑 4 道门禁（`testDebugUnitTest` / `lintDebug` / `assembleDebug` / `compileDebugAndroidTestKotlin`），各记退出码；APK 哈希这次**会**变化（改动进包），需与 `fd7f908` 的 `0e1a8002…a522` 区分记录。
4. `docs/handoff/current.md` 目前是他人的脏文件，本片未改。待其空闲时由集成者追加一行指向本文档。

## 6. 遗留项

- 该 TXT 另有 6 章写成 `第一卷：304 标题`（卷名与章号粘在同一行，行首非数字），任何「行首数字」形态的目录规则都识别不到；要覆盖需另设规则，不在本片范围。
- 同一本书存在源数据问题：编号 `148` 重号、`292..359` 整段缺失、卷二无卷标题行（导致卷二章节挂在错误的分组名下）。均非代码缺陷。
- `ReaderTocModels.kt` 的 `groupChaptersByVolume` 文档注释称「保留给单元测试和兼容调用」，但全仓 JVM 侧无引用；本片顺带补了一个用例覆盖它，未删除。

---

## 7. 给「自定义页眉页脚」功能 owner 的整改清单

> **⚠️ 状态更新：7.1 与 7.2 已由本人在用户授权后实施完毕，见第 8 节。本节保留作诊断记录，owner 请勿重复实现。**
> 下面的「归属声明」写于动手之前；用户随后明确要求「参考 md3 ……你来操作」，因此越界改动这片 WIP 是获授权的，不是自作主张。

**归属声明（原始）**：本节当时只是诊断，下列文件一行未改。`CustomTipDialog.kt`、`CustomTipPlaceholder.kt` 在 index 中是 untracked（`??`），`SettingsStore.kt`、`PagedReaderHeaderFooter.kt`、`PagedTxtReaderHost.kt`、`ReaderDisplaySettings.kt`、`ReaderSubPage.kt`、`ReaderContentHostPagedBranch.kt` 均为已跟踪未暂存（` M`）。注意第 4 节的阻塞使得「与 HEAD 比对以界定改动范围」当时做不了（`git ls-tree HEAD` 报 `not our ref`），上述判断仅由 index 状态支撑。

### 7.1 页眉页脚没有中间槽

现状是**结构上只有两槽**，不是配置问题：

- 渲染体参数只有 `headerLeft/headerRight`、`footerLeft/footerRight`（`PagedReaderHeaderFooter.kt:44-46`、`:104-106`）；
- 布局是 `Row(Arrangement.SpaceBetween)` 内放两个 `CustomTipValue`，各自 `weight(1f, fill = false)`（`:63-93`、`:121-151`）；
- 设置侧只有四行 picker（`ReaderDisplaySettings.kt:92-110`）；
- 持久化只有四个字段 `customTipHeaderLeft/Right`、`customTipFooterLeft/Right`（`SettingsStore.kt:244-247`），读写各一对（`:427-430`、`:569-572`）。

用户诉求「在中间放信息」无法满足，需要新增第三槽。改动面约 6–7 个文件：

| 文件 | 需要做什么 |
|---|---|
| `SettingsStore.kt` | +2 字段（header/footer 各一中间槽）、+2 DataStore KEY、读取/写入/effective getter 各 +2 |
| `PagedReaderHeaderFooter.kt` | `Row` 改三格：左右各 `weight(1f)` 并分别 start / **end** 对齐，中间格按自然宽度居中。当前两个 `weight(1f, fill = false)` 配 `SpaceBetween` 的写法撑不出真居中 |
| `PagedTxtReaderHost.kt:195,336`、`ReaderContentHostPagedBranch.kt` | 参数透传 |
| `ReaderDisplaySettings.kt` + `profile/ReaderSubPage.kt` | 两处设置页各加两行 picker（别只改一处，两处是同名并行的入口） |
| `ReaderOverride.kt` | 若要支持「本书覆盖」中间槽，需加 `ReaderOverrideKey` |

**验证约束**：页眉页脚只存在于**分页（翻页）模式**，滚动/legacy 模式没有页眉页脚行（`ReaderInteractionLayer.kt:153-154` 只是预留 TopAppBar 高度）。真机验收必须切到翻页模式，滚动模式不要给该功能加预期。

### 7.2 设置里显示英文 token

`CustomTipDialog.kt:252` 把**原始模板串**直接当值渲染：

```kotlin
text = template.ifBlank { "无" },
```

于是折叠行显示 `{ChapterTitle}`、`{PageIndex}/{PageSize}` 这类英文 token。中文其实全都已存在，只是这一处回显没走：`CustomTipPlaceholder` 每个枚举都带中文 `label`（书名 / 章节名 / 当前时间 / 电量 / 本章序 / 总章数 / 当前页 / 本章总页 / 本章余页 / 总进度%），弹窗「可用变量」chip 用的就是 `ph.label`（`:167`），四个设置项标题也已是中文。

建议的最小修法（2 文件，低风险）：

1. `CustomTipPlaceholder` 加 `describeTemplate(template: String): String`，复用现成的 `extractPlaceholders` 把每个 `{Key}` 反查为 `label`；遇到无法识别的片段**原样返回**，不要吞字符；
2. `CustomTipDialog.kt:252` 改用 `describeTemplate(template)`。

弹窗内的输入框**继续显示原始 token**——那是模板语言，用户必须看得见才能编辑；只有折叠态的回显需要人话。建议同时补一个 JVM 单测覆盖 `describeTemplate`（含未知占位符、空串、多占位符拼接三种情况）。

### 7.3 建议的处理顺序

1. 第 4 节的仓库阻塞解除（部分克隆补全为完整克隆）；
2. 该功能 WIP 先落地提交，避免与他人改动混在同一个未提交状态里；
3. 7.2 作为独立小片跟进（纯文案回显，可单独验证）；
4. 7.1 按功能排期——它牵动设置持久化与两处设置页，且需要真机在翻页模式验收。

---

## 8. 已实施：页眉页脚三槽 + 电量显示方式 + 中文回显（对应用户「参考 md3……你来操作」）

### 8.1 实现内容

1. **补齐到 MD3 的 6 个位置**（`CustomTipTarget` = HEADER/FOOTER × LEFT/MIDDLE/RIGHT）。新增 `customTipHeaderCenter`、`customTipFooterCenter` 两个持久化字段与对应 DataStore key；中间槽没有旧的 `HeaderFooterItem` 对应物，所以 `effectiveHeaderCenter()` / `effectiveFooterCenter()` 只回落到空串。
2. **三槽布局**改为 `Row` 内三个 `weight(1f)` 单元格，内容分别 `CenterStart` / `Center` / `CenterEnd` 对齐。中格占满中央三分之一，因此内容**恰好落在屏幕正中**，且三格互不重叠（超出各自三分之一时省略号截断）。
3. **电量显示方式**新增 `BatteryTipStyle` 枚举，三档：`ICON_ONLY`（电量内显，数字画进电池轮廓内）、`ICON_WITH_TEXT`（图标 + 数字，此前行为）、`TEXT_ONLY`（仅文字）。**默认 `ICON_ONLY`** —— 这套功能尚未提交过、没有存量用户，且用户诉求就是内显。
4. **顺带修掉一个真实缺陷**：旧实现把电池图标无条件提到整行最前面，`"第3页 {BatteryPercent}"` 会渲染成 `"🔋 第3页 85%"`，图标与数字各去各处。现在按 `{BatteryPercent}` 在模板中的位置把字符串切段逐段渲染，图标落在 token 原本的位置。
5. **中文回显**：`CustomTipPlaceholder.describeTemplate()` 把 `{Key}` 反查成 `label`，设置页折叠行由 `{ChapterTitle}` 改为「章节名」；未知占位符**原样保留**（回显不该吞内容，拒绝非法模板是 `isValid` 的职责）。点开后的输入框仍显示原始 token。
6. 两处设置页（阅读器内 sheet 与「我的→阅读设置」）都加了 6 个 picker 与电量方式选择；新行各带 `SettingsAnchor`（`reader_battery_style` / `reader_header_center` / `reader_footer_center`）并**登记进设置搜索索引**，否则新行搜不到。

### 8.2 改动文件（9 改 + 2 新）

```
M data/settings/SettingsStore.kt                    M feature/reader/pager/PagedTxtReaderHost.kt
M feature/reader/pager/PagedReaderHeaderFooter.kt   M ui/screen/reader/ReaderContentHostPagedBranch.kt
M ui/screen/reader/sheets/ReaderDisplaySettings.kt  M ui/screen/profile/ReaderSubPage.kt
M ui/screen/reader/sheets/ReaderTocModels.kt        M ui/components/CustomTipDialog.kt
M ui/screen/profile/SettingsSearchIndex.kt
+ feature/reader/pager/CustomTipTemplateDescribeTest.kt   + ui/screen/reader/sheets/VolumeHeaderTest.kt
```

### 8.3 验证方式与结果

这片 WIP 在 `eebd59d` / `fd7f908` 里根本不存在（`CustomTipDialog.kt` 等是 untracked），所以任何 worktree 都验不了它 —— 必须基于**当前工作树**构建。做法：把工作树的 `android/`（排除 build/.gradle）复制到一次性目录 `D:\develop\Code\Codex\cra-wip-build`，在那里跑 Gradle。共享工作区的 `android/app/build` 未被写入。

| 检查 | 命令（在 `cra-wip-build/android`） | 退出码 | 结果 |
|---|---|---|---|
| 主源码编译 | `:app:compileDebugKotlin` | **0** | BUILD SUCCESSFUL（首轮为 1，见下） |
| 全量 JVM | `:app:testDebugUnitTest` | **0** | **282 套件 / 2513 测试 / 0 失败 / 0 错误 / 0 跳过**，4m12s |
| 新增两套件 | 同上 | — | `CustomTipTemplateDescribeTest` 5/5、`VolumeHeaderTest` 5/5，均 0 失败 |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | **0** | 签名变更后 instrumentation 侧不破坏 |
| APK | `:app:assembleDebug` | **0** | 51,568,064 bytes，SHA-256 `d47377b46a81ea7905a49304bfa2492e321483d5060f97cf1ec43f19fa01b137` |
| Lint | `:app:lintDebug` | **1（失败）** | 3 errors / 9 warnings，**全部不属于本次改动**，见 8.4 |

首轮 `compileDebugKotlin` 失败（退出码 1），4 条错误都在我新写的 `PagedReaderHeaderFooter.kt`：`TipSlot` 的 `alignment` 参数误收成 `Alignment.Horizontal`，而 `Alignment.Center` 不是它的子类型。改为 `Alignment` 后通过。

### 8.4 Lint 失败归属（重要：不是本次改动引入）

3 个 error 全在 `MainActivity.kt:197,199` 的 `RestrictedApi`（`ComponentActivity.dispatchKeyEvent` 跨 library group 调用）。那是他人 WIP 里的鼠标滚轮桥接（`git status` 显示 ` M MainActivity.kt`，内容是 `event.action == MotionEvent.ACTION_SCROLL && ReaderHardwareKeys.dispatch(event)`），本次一行未碰。

9 个 warning 相对 `fd7f908` 基线（5 条）的变化：

- 新增 3 条 `GradleDependency`（`libs.versions.toml:32`）—— 他人依赖升级；
- 新增 2 条 `DefaultLocale`（`ReaderSubPage.kt:773,786`）—— 落在 **TTS 语速/音调**块（`String.format("%.1f", …)`），与我在 464–520 行的页眉页脚改动无关；
- 基线的 `HardwareIds` 消失 —— 他人已在 `c79cceb5 fix(android-sync): use app-scoped device identifiers` 修掉；
- 其余 3 条 `ObsoleteLintCustomCheck` + 1 条 `UnusedResources` 与基线相同。

**本次改动的 11 个文件贡献 0 error、0 warning。** 按门禁纪律「任一失败：保留原始输出、定位归属、停止」，我不在他人的 `MainActivity` / 依赖表上顺手修复。

### 8.5 尚未验证

- **真机/视觉完全未做**。「电量内显」是本次新写的 Canvas 自绘（27×13dp 电池轮廓 + 内嵌 7sp 数字 + 正极凸点 + 半透明电量填充），数字在 3 位数（100）与低对比纸张下是否清晰、与相邻文字的光学对齐，**必须真机翻页模式看过才算数**。我用的是 `Box` 叠 `Canvas` + `Text` 的方案，没有走 nativeCanvas 文字绘制，因此字形跟随 Compose 字体设置。
- 无障碍：`ICON_ONLY` 给了 `contentDescription = "电量 N%"`，但 TalkBack 实际读出效果未测。
- 三格各限 1/3 宽度：某侧文字过长会被截断，这是有意的取舍（保证不重叠 + 真居中），但需要真机确认长度是否符合预期。
- 提交仍未做，原因见第 4 节：`.git/index` 的 cache-tree 指向缺失 tree `15c687cf`，且远端报 `not our ref`（该对象不在远端，`fetch` 修不了）。需要动本地 index，未获授权前不碰。
- 一次性构建副本 `D:\develop\Code\Codex\cra-wip-build`（约 750MB + 构建产物）由本人创建，提交时可复用，收尾后可删。
