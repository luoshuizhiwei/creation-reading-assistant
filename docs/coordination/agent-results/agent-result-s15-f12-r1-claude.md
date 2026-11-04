# S15-F12-R1 实施报告：年度账单同一时长显示不一致

> 执行：Claude（窗口 B）　|　任务：S15-F12-R1
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收尾 `git rev-parse --short HEAD` 均 = `5aed593`
> 未运行全量 JVM / Lint / APK / ADB / 真机；未 commit / push / stage

---

## 1. 根因

`YearBillHeroCard.kt` 在 S15-F12 之后仍保留了**两套时长量化**：

```kotlin
// 主值路径（旧）
val hoursFloat = stats.totalReadingMs.toFloat() / 3_600_000f
val hours = rememberCountUp(hoursFloat.roundToInt(), "stats.yearbill.hours", reducedMotion)  // ← 抬成整数小时
value = formatCompactDuration((hours * 60L) * 60_000L, LocalDurationUnit.current)            // ← 回乘成 ms

// 金句路径（旧）
buildYearHeadline(totalMs = stats.totalReadingMs, ...)   // ← 用原始 ms
```

主值先把原始时长 **四舍五入到整小时**再交给公共 formatter，金句却直接用原始时长。两条路径的输入不同，同一张卡出现两个读数。

**50 小时 30 分**（`50 * 3_600_000 + 30 * 60_000`）的实际后果：

| 位置 | 旧输出 | 说明 |
|---|---|---|
| 主卡「累计阅读」 | **`51 小时`** | `50.5f.roundToInt()` = 51 → `formatCompactDuration(51h)` |
| subtitle | **`51 小时 2026`** | 与主值同源，同样错 |
| 金句（`hours >= 50f` 分支） | **`累计 50 小时、…`** | 用原始 ms → 向下取整得 50 |

⇒ **同屏出现「51 小时」与「累计 50 小时」**，违反本片"页面不自行决定舍入、同一数值使用公共事实源"的目标。

**同类边界（提前进位）**：

| 原始时长 | 旧主值 HOURS | 旧主值 DAYS | 新主值（两单位） |
|---|---|---|---|
| 47 小时 30 分 | `48 小时` | **`2 天`**（应为 1 天） | `47 小时` / `1 天` |
| 24 小时 30 分 | `25 小时` | `1 天` | `24 小时` / `1 天` |

DAYS 下的 47.5 小时尤其明显：`roundToInt` 把 47.5 抬到 48 → 跨过 24 小时门槛 → 主值多出一天。

---

## 2. 修改前后 50 小时 30 分的实际输出

用 Kotlin 语义（`Float` 除法 + `roundToInt` 半进位）独立复算，与源码逐字对应：

```
=== 50 小时 30 分 (50*3600000 + 30*60000) ===
  HOURS: 旧主值='51 小时'  新主值='50 小时'  金句(原始ms)='50 小时'
  DAYS:  旧主值='2 天'     新主值='2 天'     金句(原始ms)='2 天'

=== 47 小时 30 分 (DAYS 提前进位边界) ===
  HOURS: 旧主值='48 小时'  新主值='47 小时'
  DAYS:  旧主值='2 天'     新主值='1 天'

=== 24 小时 30 分 (跨天门槛) ===
  HOURS: 旧主值='25 小时'  新主值='24 小时'
  DAYS:  旧主值='1 天'     新主值='1 天'
```

**HOURS 下 50h30m 的 51→50 正是复核指出的矛盾点，修复后主值与金句逐字一致。**

> 说明：50h30m 在 **DAYS** 下旧值恰好也是 `2 天`（因为 50.5 与 50 都落在同一档），所以该边界只暴露 HOURS 侧矛盾；DAYS 侧的同类缺陷由 47h30m 边界暴露（`2 天` → `1 天`）。两条边界都已进测试。

---

## 3. 修复方案

**统一到"原始时长 → 量化到整分钟 → 公共 formatter"这一条路径**，不引入任何小时级四舍五入。

```kotlin
// YearBillHeroCard.kt
val totalMinutes = stats.totalReadingMs / 60_000L
val animatedMinutes = rememberCountUp(totalMinutes.toInt(), "stats.yearbill.hours", reducedMotion)
val cardDurationText = yearBillCardValue(animatedMinutes, durationUnit)

value = cardDurationText                                  // 主值
subtitle = "$cardDurationText $anchorYear"                // 与主值同源同文
```

新增两个纯函数作为**渲染与测试共用的单一事实源**：

```kotlin
/** 由动画当前值（整分钟）求主值文本。 */
internal fun yearBillCardValue(animatedMinutes: Int, unit: DurationUnit): String =
    formatCompactDuration(animatedMinutes.toLong() * 60_000L, unit)

/** 由原始时长直接求主值终值（测试与需要终值的调用方使用）。 */
internal fun yearBillCardValue(totalMs: Long, unit: DurationUnit): String =
    formatCompactDuration(totalMs / 60_000L * 60_000L, unit)
```

**为什么量化到"分钟"是安全的**：公共 `formatCompactDuration` 本身就以分钟为单位、不足一分钟显示 `0 分钟`，因此分钟级量化不会与公共结果产生任何偏差——它只是把动画的推进粒度定在分钟（比按毫秒推进更贴近用户读数粒度），**动画终点 = 原始时长的整分钟值**。

**关于保留数字递增动画**（任务第 3 条）：动画仍在（`rememberCountUp` 未移除，key 未变，`reducedMotion` 语义未变）；但动画的**目标值**从"整小时数"改为"整分钟数"，因此动画结束后的最终显示值与公共 formatter 对同一原始时长的结果逐字一致。`countUpStartValue` / `CountUpMemory` 的行为不受影响（它们只关心 Int 值本身）。

---

## 4. 测试

### 4.1 新增用例（`YearBillHeadlineTest.kt`，4 条）

| 用例 | 覆盖 |
|---|---|
| `card value equals the public formatter result on the original duration` | 7 个样本（0 / 30 分 / **50h30m** / **47h30m** / 23h30m / 24h30m / 100h59m）× **HOURS + DAYS**，断言 `yearBillCardValue(ms, unit) == formatCompactDuration(ms, unit)` |
| `fifty hours thirty minutes never contradicts itself between card and headline` | **R1 复现边界**：主值不得含 `51`；金句必须逐字包含主值。**HOURS + DAYS** |
| `days unit rolls over consistently between card and headline` | DAYS 提前进位：47h30m 主值必须是 `1 天`、不得含 `2 天` |
| `headline never contradicts the card whenever it mentions a duration` | 显式枚举两条**含时长**的分支（`hours>=50`、`books>=12`）× HOURS + DAYS，断言金句包含主值 |

**HOURS / DAYS 双覆盖**（任务第 5 条）：上述 4 条全部遍历 `DurationUnit.entries`。

### 4.2 未修改 `TimeUtilsTest.kt`

本片**未触碰公共 formatter 契约**（`formatDuration` / `DurationStyle` 一行未改），因此 `TimeUtilsTest.kt` 未改动。授权允许"补充与该边界直接相关的断言"，但该边界属 `YearBillHeroCard` 的量化路径而非 formatter 本身，放在 `YearBillHeadlineTest` 更贴合职责。`TimeUtilsTest` 仅作为回归一并复跑。

---

## 5. 变异验证（证明新断言真的能抓住该缺陷）

把 `yearBillCardValue` **回退为旧的整小时四舍五入**（临时副本，非仓库文件）：

```kotlin
// mutant: 恢复 R1 之前的实现
formatCompactDuration(totalMs.toFloat().div(3_600_000f).roundToInt().toLong() * 3_600_000L, unit)
```

对变异体运行 `YearBillHeadlineTest`：

```
MUTANT EXIT=1 (expect 1)
YearBillHeadlineTest > headline never contradicts the card whenever it mentions a duration FAILED
YearBillHeadlineTest > days unit rolls over consistently between card and headline FAILED
YearBillHeadlineTest > card value equals the public formatter result on the original duration FAILED
YearBillHeadlineTest > fifty hours thirty minutes never contradicts itself between card and headline FAILED
BUILD FAILED
```

⇒ **新增的 4 条用例全部对目标缺陷敏感，不是恒真断言。** 变异体已删除，正确实现已恢复（校验与通过版本逐字节相同）。

---

## 6. 命令、exit code、XML 测试数量

### 6.1 任务指定的定向测试

```
cd android
./gradlew.bat --console=plain :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.stats.components.YearBillHeadlineTest" \
  --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" --rerun
```
- exit code **0**，`BUILD SUCCESSFUL in 15s`

| 测试类 | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `...stats.components.YearBillHeadlineTest` | **10** | 0 | **0** | **0** |
| `...ui.util.TimeUtilsTest` | **16** | 0 | **0** | **0** |

（`YearBillHeadlineTest` 由 6 条增至 **10** 条；`TimeUtilsTest` 保持 16 条。）

### 6.2 任务指定的编译

```
./gradlew.bat --console=plain :app:compileDebugKotlin --rerun
```
- exit code **0**，`BUILD SUCCESSFUL in 1m 11s`，无 `e:` 错误

### 6.3 `git diff --check`

```
git diff --check -- \
  android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt \
  android/app/src/test/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeadlineTest.kt
```
- **exit code 0**，无输出（无空白错误、无行尾冲突标记）

### 6.4 过程中遇到的两次共享工作区竞争（**非本片缺陷**）

| 现象 | 归因 | 处置 |
|---|---|---|
| `compileDebugKotlin` 报 `Unresolved reference 'LegacyEpubTapArbiter'`（`reader/content/PagedEpubContent.kt:237`） | **窗口 C 的 S09-B-F2 编辑中途**：该引用在 HEAD 版本中 0 命中，工作树新增 215 行，`LegacyEpubTapArbiter.kt` 尚未落盘 | 等待其落盘后重跑，编译即通过。**未触碰窗口 C 任何文件。** |
| `compileDebugUnitTestKotlin` 报 `Unresolved reference 'AtomicBoolean' / 'awaitSubscribed'`（`data/settings/DurationPrefsTest.kt`） | **窗口 D 的 S15-F4 编辑中途**：该文件是本片开工后才出现的未跟踪新文件 | 等待其可编译后重跑，测试即通过。**未触碰窗口 D 任何文件。** |

两者都是多人共享脏工作区的并行编辑竞争；本片自己的改动在两种情况下都编译/测试正常。

### 6.5 测试过程中修正的一处**我自己的**断言缺陷

`days unit rolls over consistently between card and headline` 首版断言"金句必须包含主值"，在 47h30m 下失败——因为该时长命中的是 `else` 分支（`"在 10 天里翻开书页…"`），**该分支本身不含时长**，"金句包含主值"这个前提不成立。

这是**我的断言过宽**，不是产品缺陷。修正方式：把该用例收窄为只断言 DAYS 提前进位（主值必须是 `1 天`），另设 `headline never contradicts the card whenever it mentions a duration` **显式枚举两条含时长的分支**（`hours>=50`、`books>=12`），不再用字符串启发式猜测"这句话里有没有时长"。

---

## 7. 文件 SHA-256

```
8c0b4c9508dbad237f7be1a89546ddd20880b197dee3c80af1b8fd58b5bcfd92  main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt
2c07ce22287fadac85c306e937f69b0d364ccd7b47823f6747042a9b070624da  test/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeadlineTest.kt
```

（路径相对 `android/app/src/`。）

---

## 8. 开始与结束 `git status --short`

### 开始

```
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
378
```

### 结束

```
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
379
```

本片授权文件的状态（两个都是**已跟踪且本片修改**）：

```
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt
 M android/app/src/test/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeadlineTest.kt
```

**关于 378 → 379 的 +1**：该增量是**窗口 D 在本片执行期间新建的未跟踪文件** `android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt`（S15-F4 的产物，mtime 落在本片时间窗内）。**本片未新建任何文件**——我的两个授权文件在本片开工时已是 ` M` 状态（S15-F12 的既有修改），因此不改变文件计数。

`git diff --cached --name-only` = **0**（无 staged 变更）。

---

## 9. 声明

- **未修改原仓库** `D:\develop\Code\Codex\creation-reading-assistant`：本片所有读写均在 `…-recovery-20260919` 内。
- **未 commit / push / stage**：`git diff --cached --name-only` = 0。
- **未 reset / checkout / clean / stash / 全仓格式化**；未恢复、覆盖或重写任何其他窗口的改动。
- **未修改窗口 C / 窗口 D 的文件**。两次因它们编辑中途导致的编译失败均以"等待后重跑"处理，**未回退、未改写、未加锁**它们的任何文件。
- **未修改**协调看板、handoff、Obsidian、`android/scripts/**`。
- **未修改公共 formatter 契约**（`TimeUtils.kt` 与 `TimeUtilsTest.kt` 一行未动）。
- **未修改其他页面**（任务第 6 条）；**未处理**"所有时长文字都能长按"的 S15-F3 手势切片（任务第 7 条）。
- **未运行**全量 JVM、Lint、APK 安装、ADB、真机或模拟器。
- 数字递增动画**保留**；`reducedMotion` 无障碍语义未变。

---

## 10. 给 Codex 的复核清单

1. 确认主值 / subtitle / 金句三者都从 `stats.totalReadingMs` 出发，且**进入公共 formatter 前只有分钟级量化、没有小时级四舍五入**。可执行 `grep -n roundToInt YearBillHeroCard.kt` 核验：应只有 **1 处命中，且位于第 60 行的注释**（说明旧实现），代码中已无该调用。
2. 确认 `yearBillCardValue` 的两个重载分别是"动画当前值"与"原始时长终值"，且渲染端用的是前者。
3. 复核 §2 的 50h30m / 47h30m 输出对照是否符合你的预期。
4. 复核 §6.5：把"金句是否含时长"改为**显式枚举分支**而非字符串启发式，是否可接受。
5. **独立复跑** §6.1 的定向测试，确认 `YearBillHeadlineTest` 10/10 与 `TimeUtilsTest` 16/16。
6. 复核 §6.4 两次编译失败是否确认为窗口 C / D 的编辑中途竞争（本片未触碰它们）。
7. 复核 §8 的 +1 归因（窗口 D 的 `DurationPrefsTest.kt`）是否与你的观察一致。

---

**本片实施完成，等待 Codex 独立复核。未领取下一项。**
