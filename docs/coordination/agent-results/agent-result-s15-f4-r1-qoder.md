# S15-F4-R1 结果报告（窗口 D / Qoder）— 补强 DurationPrefs 空白盘默认值测试

- 任务：S15-F4-R1｜上游：`docs/coordination/agent-results/agent-result-s15-f4-qoder.md`（评审指出第 1 条契约的覆盖是假的）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`（HEAD 未动）
- 时间：2026-09-22 11:05–11:36（本机 +08:00）
- 唯一源码事实：`android/app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt`（只读，哈希与 mtime 见第六节，未改）
- 本轮写入：`android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt`、本报告，以及原报告 §二第 1 行的一处纠错（其余章节未动）
- 结论：**默认值契约现在由「空白盘 + 真实上游读取」共同支撑，8 个用例同一命令连续 4 次全绿（exit code 0，`tests=8 / failures=0 / errors=0 / skipped=0`），执行顺序两次逐条一致；未新增依赖、未加生产 DI seam、未改 `build.gradle.kts`、未改任何生产文件。**
  诚实声明（任务第 7 条）：**本类是「测试类级独立临时 DataStore」，不是「每用例独立文件」**；用例之间的隔离靠各自显式建立前置状态 + 收敛式断言，唯一有先后依赖的是 `a0` 必须第一个接触该 DataStore，这一点由 `a0` 自己的前置断言把守（第四节探针已证明它不是摆设）。

---

## 一、R1 补强了什么（对上游缺口的逐条回应）

原用例 `fresh instance exposes hours as its declared default before anyone subscribes` 只读了一行 `stateIn(..., initialValue = HOURS).value`：不起订阅、不看磁盘，**即使盘上已经是 DAYS 也照样通过**；而且 `aaa_unknown` 排在它前面并把共享文件写成 DAYS，所以它连"空白盘"都不是。R1 的处理：

| 任务【必须完成】 | 落地位置 |
|---|---|
| 1 让空白盘默认值测试成为类内第一个访问委托的用例 | 方法名 `a0 …`（`NAME_ASCENDING` 下 `'0'(0x30) < 'a'(0x61)`，排在 `aaa_…` 之前） |
| 2 测试开始前断言 `duration_prefs.preferences_pb` 不存在 | `a0` 第一句 `assertFalse(storeFile().exists(), "…磁盘上已经有 duration_prefs.preferences_pb…")` |
| 3 构造后启动真实 Flow 订阅，等真实上游完成首次读取 | `collectScope.launch { prefs.unit.collect { subscribed.set(true) } }` + `awaitSubscribed()`：轮询的是**收集体真的收到了第一帧**，不是"等一会儿"；随后 `cancel()` 让订阅归零，避免 `stateIn` 里可能仍握着旧快照的 caching job 参与后续读取 |
| 4 断言真实读取结果为 HOURS，不能只读声明初值 | 判别子 `toggle()`：其内部是 `DurationUnit.fromStorage(ds.data.first()[KEY])`，这条 `ds.data.first()` 必须等一次真实上游读取才返回，与 `stateIn` 初值无关。空白盘 → 真实读到「无值」→ HOURS → `toggle()` 必返回 **DAYS**；若上游其实读到 DAYS → `toggle()` 必返回 **HOURS**。两个方向可区分，再配第二条 `toggle()`（盘上已是 DAYS → 必须切回 HOURS）双向夹住 |
| 5 默认值测试之后再跑脏值与其他状态测试 | 顺序见第三节 |
| 6 方法命名明确保证顺序 | `a0 …`（空白盘）→ `aaa_…`（脏值）→ `b1…b5`（setUnit/toggle/连续）→ `c0`（无订阅 toggle）；`b*/c*` 每条第一句都是显式 `setUnit` + 等磁盘到位 |
| 7 报告诚实写明类级而非用例级 | 本报告结论段 + 第五节 |
| 8 不加依赖、不加生产 seam、不改 `DurationPrefs.kt` | 全部满足；测试文件只 import 已有的 junit/mockk/coroutines/JDK |
| 9 保留 setUnit/toggle/无订阅/脏值覆盖 | 8 条用例仍在，`b1…b5`、`c0`、`aaa_` 的断言未削弱（`aaa_` 另加了一条真实读路径断言，见第二节） |

`a0` 里仍保留了 `assertEquals(HOURS, prefs.unit.value)`，但它的作用被明确降级为「`stateIn` 声明面」，默认值契约由上面那条真实读路径承担。

## 二、`aaa_` 脏值用例：一处被实测否证的推断

我在 R1 开头按「DataStore 的 actor 常驻内存，同一 JVM 内不再重读磁盘」推断：`a0` 抢走唯一一次冷读后，`aaa_` 就只能是「外部脏文件不可被采纳」的语义，于是把哨兵断言反写成 `assertFalse(哨兵)`。第一次运行（r1a）直接把这条打红了，失败消息里带出磁盘 hexdump：

```
0a 17 0a 0d 64 75 72 61 74 69 6f 6e 5f 75 6e 69 74 12 06 2a 04 64 61 79 73
0a 1c 0a 12 73 31 35 66 34 5f 73 65 6e 74 69 6e 65 6c 5f 6b 65 79 12 06 2a 04 6b 65 70 74   (55 B)
```

`duration_unit=days`（小写，即 DataStore 重写后的值）与 `s15f4_sentinel_key=kept` 并存 —— 哨兵只可能来自我手写的脏文件被**真正解析进内存**，所以 `ds.data` 的新收集确实会重新读盘（`WhileSubscribed(5_000)` 停掉上游后，下一个订阅/`first()` 是全新收集）。据此把 `aaa_` 恢复成冷解析语义，并新增一条此前没有的真实读断言：脏值 `"Days"` 必须经 `toggle()` 表现成 HOURS（返回 DAYS）。

同一份 hexdump 也顺手校准了 protobuf 布局：`Preference.value` 的 oneof 外层是 `12 <len>`，内层 string 是 `2a <len>`（原先写成 `12 <len> 2a <len> <bytes>` 的嵌套，正是这段字节把它证实了）。

## 三、完整执行顺序（两次运行逐条对照，取自 XML）

| # | 用例 | r1e | r1h |
|---|---|---|---|
| 1 | `a0 blank data store default is proven by a real upstream read, not by the stateIn initial value` | 0.381 s | 0.381 s |
| 2 | `aaa_unknown persisted value falls back to hours without crashing or wiping the file` | 0.049 s | 0.049 s |
| 3 | `b1_setUnit days reaches disk and is read back by newly created instances` | 0.075 s | 0.075 s |
| 4 | `b2_setUnit hours reaches disk and replaces the previous value` | 0.078 s | 0.077 s |
| 5 | `b3_toggle from hours returns days and persists days` | 0.046 s | 0.046 s |
| 6 | `b4_toggle from days returns hours and persists hours` | 0.060 s | 0.046 s |
| 7 | `b5_consecutive toggles agree with the returned unit and the persisted bytes` | 0.279 s | 0.278 s |
| 8 | `c0_toggle with no active subscriber follows the persisted value, not the stale flow value` | 0.022 s | 0.018 s |

两次顺序完全一致，`failures=0 errors=0 skipped=0`。

## 四、两条"覆盖不是假的"探针（会主动报警的守卫）

- **探针 A（乱序）**：把 `c0` 临时改名为 `A0zz_guard probe …` 让它抢到第一（该用例会先把盘写成 DAYS），重跑后**恰好红 2 条**：
  - `a0`：`前置条件不成立：磁盘上已经有 duration_prefs.preferences_pb…`（DurationPrefsTest.kt:80）—— 证明"空白盘"前提是真的被检查，不是修辞；
  - `aaa_`：`expected:<DAYS> but was:<HOURS>` —— 顺带证明 `toggle()` 的判别子确实跟着真实读到的值走（这次读到的是内存里的 DAYS 而不是脏值 `Days`）。
  探针跑完立即改回原名，文件哈希回到认证过的那一份（见第六节）。
- **探针 B（哨兵）**：即第二节 r1a 的反向断言失败，证明脏文件确实被解析进内存，`aaa_` 的"回落且不清档"不是自说自话。

## 五、临时 DataStore 的创建与清理（类级，非用例级）

- 创建：`@BeforeClass` → `Files.createTempDirectory("cra-duration-prefs-test")`，预建 `datastore/` 子目录；mockk Context 的 `filesDir` 指向该目录，`getSharedPreferences`/`deleteSharedPreferences` 复用仓库既有 `InMemorySharedPreferences`。落盘路径 `%TEMP%\cra-duration-prefs-test<随机>\datastore\duration_prefs.preferences_pb`。
- 清理：`@AfterClass` 取消本类创建的全部作用域后 `deleteRecursively()`，并 `assertFalse(classDir.exists())` —— 清理不干净会让测试变红。
- 跑完实测：`ls %TEMP%\cra-duration-prefs-test*` → `No such file or directory`（4 次运行后仍无残留）。
- 隔离证据：其他测试类共享的 `%TEMP%\cra-datastore-tests\datastore` 仍是 7 个文件（`app_settings / continue_reading / import_history / library_root / search_history / shelf_prefs / sync_prefs`），**没有** `duration_prefs.preferences_pb`；用户真机偏好路径从未被任何用例指向。
- 为什么只能类级：`preferencesDataStore(name)` 委托按**顶级属性**缓存进程级单例，第一次访问时用当时的 Context 解析 `filesDir` 并永久绑定；在不改生产代码、不加依赖的前提下做不到每用例一份独立文件（沿用 F4 的 P3-1 blocker 结论）。

## 六、验证命令、退出码与哈希

```
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.data.settings.DurationPrefsTest" --rerun
```

工作目录 `android/`；退出码经 `powershell.exe` 的 `$LASTEXITCODE` 读取（Git Bash 下 `cmd /c` 会假报 0）。本轮只运行了这一条命令，共 9 次（r1a…r1h + 一次乱序探针；其中 r1a 与探针是故意/中间态的失败跑）：

| 标记 | 内容版本 | exit code | XML |
|---|---|---|---|
| r1a | 中间版（探针 B 失败） | 1 | `tests=8 failures=2 errors=0 skipped=0`，`time=150.752`（`awaitStarted` 写错导致 60 s watchdog） |
| r1b | 修好判别子后首绿 | 0 | `tests=8 failures=0 errors=0 skipped=0`，`time=1.009` |
| r1c / r1d | 内容 `0b41259d…` | 0 / 0 | 均 `8/0/0/0`，`0.962 / 0.985 s` |
| 探针 A | 临时乱序 | 1 | `8/2/0/0`，`0.903 s` |
| r1e / r1f / **r1g / r1h** | **最终内容 `40adb2c8…`** | r1b–r1d 与探针均已显式打印；r1g `EXITCODE=0`、r1h `EXITCODE=0`；r1e/r1f 未单独回显退出码，改以日志 `BUILD SUCCESSFUL in 19s / 12s` 佐证 | 四次全部 `tests=8 skipped=0 failures=0 errors=0`，`0.991 / 0.989 / 0.956 / 0.971 s` |

- `BUILD SUCCESSFUL in 12s`，`30 actionable tasks: 1 executed, 29 up-to-date` —— 只有 `testDebugUnitTest` 真正执行，主源码编译任务是 `UP-TO-DATE`，没有代并行窗口重编生产代码。
- 空白检查（按任务指定方式，并做了解耦验证）：
  `git diff --no-index --check -- NUL <测试文件>` → **无任何输出，rc=1**（"内容不同但无空白错误"的正常退出码）。
  反向对照：同样命令跑一个故意带行尾空白的临时文件 → 打印 `ws_negative_control.kt:4: trailing whitespace.` 且 **rc=3**。
  所以「rc=1 且无输出」= 干净；真正的 whitespace error 会打印并且 rc=3。
- 文件哈希：

| 文件 | SHA-256 | 大小 | mtime |
|---|---|---|---|
| `DurationPrefsTest.kt`（最终，产出 r1e/r1f/r1g/r1h） | `40adb2c8cec978e7ab9577ecdbe62e394176d15feec1bf7fda66b63251a4b26b` | 21157 B | 2026-09-22 11:33:14 +08:00 |
| `DurationPrefsTest.kt`（探针 A 之前的等价内容，r1c/r1d） | `0b41259d3806183471edbf092cce480ead50787a4fd4b80b9fdce87bc10fa230` | — | — |
| `DurationPrefs.kt`（只读事实源，未改） | `19e09dc1de955fa70c12aded64f1c607625685646c2c19aaec021be1b170df45` | 3298 B | 2026-09-15 23:41:12 +08:00（本轮未动） |

- git 取证（本轮结束时）：`git status --short -- <测试文件> <原报告>` → 两条 `??`（测试文件与报告所在目录本就是 untracked）；`git diff --cached --name-only | wc -l` = **0**；`HEAD` = `5aed5939600e491843e3c44453921bd6273a233c`，分支 `codex/recover-worktree-20260920`。全仓 `git status --short` 约 378 条属多窗口并行结果，可归因于窗口 D 的只有：测试文件、本报告、原报告 §二第 1 行的更正。

## 七、原报告同步纠错（只动了一处）

`docs/coordination/agent-results/agent-result-s15-f4-qoder.md` §二契约映射表第 1 行：原文称 `fresh instance exposes hours…` 覆盖"全新 DataStore 默认 HOURS"，并补了一句"磁盘侧的默认由第 8 条和 `setUnit hours…` 用例共同钉住"。该说法高估了 —— 第 8 条把磁盘写成脏值文件（并非空白盘），它自己就是第一个创建该文件的用例，因此原用例对"空白盘默认值"没有任何证明力。已把该行改成删除线 + 错因说明 + 指向本报告，**其余章节一字未动**。

## 八、遗留风险（交给集成者，未擅自处理）

1. **`aaa_` 的哨兵断言带时序成分**：它是本类唯一依赖「上游会重启并重新读盘」的断言。本类强制顺序下稳定 —— 判别子修好之后共 7 次运行全绿（r1b…r1h），其中最终内容 `40adb2c8…` 连续 4 次（r1e–r1h）。但探针 A 显示：若 `a0` 从未建立订阅，`ds.data` 会走内存快照，哨兵就被整档重写丢掉。因此该断言若将来翻红，第一步应查顺序/订阅生命周期是否被动过，而不是直接判产品回归 —— 这条判断依据已写进用例 KDoc。
2. **顺序耦合仍在**：`a0` 必须是类内第一个接触该 DataStore 的用例。守卫是它自己的 `assertFalse(exists)`（探针 A 验证过会红），不是注释。彻底解掉仍需 F4 报告里的 P3-1 生产 seam，本轮按要求没做。
3. 未跑全量 JVM（任务禁止）。共进程风险面与 F4 相同：全仓库只有本类构造 `DurationPrefs`（本轮复核 `grep "DurationPrefs("` 只有本类一处），`duration_prefs` 这个委托属性不会被别的 JVM 测试绑定。
4. 真实 IO + 最长 10 s 轮询：整类 4 次运行都在 0.95–1.0 s，余量充足，但这是真实磁盘测试固有属性。

## 九、边界与合规声明

- 未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；全程只在 `creation-reading-assistant-recovery-20260919` 内工作。
- 未 commit、未 push、未 stage、未 stash / reset / checkout / clean、未全仓格式化；`git diff --cached` 为 0，HEAD 仍是固定基点 `5aed5939`。
- 未修改任何生产文件（含 `DurationPrefs.kt`，哈希+mtime 见第六节）、未修改 `android/app/build.gradle.kts`、未新增依赖、未加生产 DI seam；未改协调看板 / handoff / Obsidian / 窗口 B、C 的文件，也未检查、恢复或评价它们。
- 只运行了任务允许的那一条 Gradle 命令（6 次，含两次探针/中间版）；未运行全量 JVM、单独主编译、Lint、APK 打包、ADB 或真机。
- 本轮写入：测试文件、本报告、原报告 §二第 1 行更正。`%TEMP%` 下的日志/XML 副本与负控文件均在仓库外，类级临时目录已确认清理。
- 数据隐私：只用自建临时目录，不触碰用户真实书籍与真实偏好；报告不涉及任何真实书名。

已完成本报告，停止，不领取下一项。
