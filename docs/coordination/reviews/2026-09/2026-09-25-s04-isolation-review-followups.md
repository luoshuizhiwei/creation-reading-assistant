# S04 隔离审查跟进（窄范围离线修正）

- **日期**：2026-09-25
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`（工作区含大量未提交改动，全部保留，本窗口未触碰）
- **输入**：`docs/code-review/2026-09/2026-09-25-s04-isolation-independent-review.md`（独立只读审查）
- **所有权范围**：仅下列三文件 + 本报告：
  - `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/IsolatedTestContext.kt`
  - `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubParserInstrumentedTest.kt`
  - `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt`
- **本窗口未执行**：Gradle、adb、任何设备/书籍数据操作、commit/push/stage/reset/checkout/clean。全部改动为源码级窄改，**未经编译、未经真机验证**。

---

## 0. 结论先行

三项任务（A/B/C）全部完成，无 SEAM REQUEST，未越界。

---

## 1. 修改明细

### A. IsolatedTestContext：`getApplicationContext()` 覆盖 + 审计 KDoc 更新（对应审查 P1-2 / P2-3）

- 新增 `override fun getApplicationContext(): IsolatedTestContext = this`，使
  `context.applicationContext` 不再返回 base 的真实 Application Context（其
  `filesDir` 指向正式目录），而返回隔离包装本身。被测代码无论经几次
  `applicationContext` 转手，取径仍落在 `testRoot` 内。
- 覆盖审计 KDoc 表格新增一行：`applicationContext` → ✅ 覆盖 → 返回本包装自身，
  并注明审查依据（P1-2 / P2-3）与逃逸机制（默认委托 base → 正式目录，且
  `assertInsideRoot` 只校验调用方传入路径、拦不住这种间接取径）。

### B. EpubParserInstrumentedTest：@Before 顺序 + @After 显式报告（对应审查 P2-1 / P2-2）

- `@Before`：`assertRootDisjointFromRealStorage()` 提前到 `testRoot.mkdirs()`
  **之前**，消除「先建目录后检查」的顺序颠倒。若将来 testRoot 构造被改到正式
  `filesDir` 下，现在会在落盘前失败。
- `@After`：`deleteRecursively()` 返回值不再被忽略 —— 当 `testRoot` 存在且删除
  失败时抛 `AssertionError` 显式报告残留（残留仅可能位于自身 cacheDir 子树内，
  因双防线已确认 testRoot 不在正式目录下，故不触碰根外）。
- **特意保留的行为**：`testRoot` 不存在（典型场景：`@Before` 防线已失败，JUnit
  仍运行 `@After`）时不报告，视为已清理。这是审查报告 §2.2 明确认可的既有
  正确行为，本次改动刻意未破坏它。另外按审查 P2-2 的建议形式，用抛异常而非
  「至少留日志」——更符合本项目「失败即停」的既有风格。

### C. CraIsolatedTestRunner KDoc 更新（对应审查 P2-4）

- 「该行为**尚未经编译**或设备验证」改为「离线编译与 androidTest APK 打包已通过
  （`compileDebugAndroidTestKotlin` / `assembleDebugAndroidTest` 均 EXIT 0，见集成
  报告）；**设备行为尚未验证**」。未扩张为「真机通过」——真机验收仍未发生。

---

## 2. 风险

1. **`getApplicationContext()` 返回类型为 `IsolatedTestContext`**：协变返回类型
   合法（Kotlin override 允许收窄），但静态分析若按 `Context` 持有并调用
   `getApplicationContext()` 后立刻做 Application 强转，将得到包装而非裸
   Application。本测试链路（`EpubParser` / `EpubRepository`）经 grep 确认无此
   用法，风险仅存在于「将来新代码」；若将来需要真实 Application（如注册
   listener），该 override 会挡路 —— 届时应显式取 base 而非回退本 override。
2. **@After 新增 AssertionError 可能让原本通过的用例失败**：仅当删除确实失败
   （文件占用等）时触发；这正是审查要求的行为（显式报告而非静默），但需在
   真机验收时知晓「报删除失败 ≠ 解析逻辑错误」。
3. 三处 Kotlin 改动**未经编译验证**。改动均为常规 override / 语句重排 /
   KDoc 文本，编译风险低，但存在（见 §3）。

---

## 3. 未验证项（诚实登记）

1. **未编译**：本窗口禁止 Gradle，三个文件的 Kotlin 改动未经过
   `compileDebugAndroidTestKotlin`。
2. **未真机验证**：@Before/@After 新顺序与断言、`getApplicationContext()`
   override 在设备上的实际行为均未运行。
3. 「删除失败时 @After 抛 AssertionError」与「@Before 失败后 @After 不误报」
   两条路径的逻辑推演仅基于源码，无运行证据。
4. 审查报告的其余发现（P1-1 workdb 残留任务、P2-5/6/7/8/9 文档与跨文件项）
   **不在本窗口所有权内，均未处理**，应由对应所有者跟进。

---

## 4. 后续建议（给集成者 / 真机验收窗口）

- 集成者跑一次 `compileDebugAndroidTestKotlin` 即可覆盖 §3.1。
- 真机验收时，若某用例失败于 @After 的 AssertionError，按 §2.2 区分对待。
