# EPUB 回退复制分支（`renameTo` 失败 → `copyTo`）· 接缝实现与门禁报告（2026-09-24）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（staged 0）
- **本窗口独占并改动的文件**：`EpubRepository.kt`、`EpubRepositoryReplaceTest.kt`、新增 `FileOps.kt`、本报告。
  未触碰其他窗口的文件；**未**执行 `commit / push / stage / reset / checkout / clean / stash`。
- **未操作手机、未接触用户书库、未在真机或任何真实文件系统上制造故障。**
- 本报告区分「**实测**」与「**静态推断**」；凡未实测者均已显式标注。

## 0. 先纠正上一份设计稿的三处问题

上一份设计稿 `docs/code-review/2026-09/2026-09-24-epub-backup-copy-fallback-test-design.md` 有三处需要撤回或降级。**这三处都是本轮实测后才看清的，属于该稿的错误，不是本轮新发现的风险。**

### 0.1 撤回：「同目录 target 与 backup 跨挂载点 rename」这一不适用例子

设计稿 §1 与 §8-3 用「**跨挂载点**导致 rename 失败」论证回退分支「生产中真实可达」。**这个例子在本代码里不成立**：

```kotlin
val target = File(context.filesDir, "books/epub/${stored.id}.epub")
val backup = File(target.parentFile, "${stored.id}.backup")   // 与 target 同一父目录
```

`backup` 由 `target.parentFile` 构造，二者**必然同目录、同文件系统**，`rename(2)` 的 `EXDEV` 分支不可能触发。**撤回该例。**

同目录下 `renameTo` 仍可能失败的真实原因只有：目录项写入失败（ENOSPC）、权限/SELinux 拒绝、OEM ROM 的 FUSE/sdcardfs 特例等。**这些都是推断，本仓库内没有任何证据表明它在生产中发生过。** 因此本分支的正确定位是：

> **一个廉价且正确的安全网，而不是一个已知的热点缺陷。** 其生产触发频率**未取证**，不得据此提高优先级。

### 0.2 撤回：「R1/R2 当前为 RED」不得当作实测

设计稿 §6.2 / §6.3 把 R1/R2 标为「当前代码 RED」。那是**静态推测**（依据：现有注入手法只能让 `copyTo` 零字节抛错，`:140` 不可达）。

**本轮实测结论是：该「RED」无法按原设计验证。** 原因：R1/R2 依赖接缝注入，而接缝一旦落地，原代码就已经不再是「当前代码」了 —— 「用新用例在旧代码上跑出 RED」在物理上做不到（旧代码没有 `fileOps` 参数，用例根本无法构造）。

本轮改用**变异测试**替代（§4），实测结果比原「RED」声明更强，见 §4。

### 0.3 纠正：`@Inject` 实现类**不会**自动绑定接口

设计稿 §4.3 建议「让实现类带 `@Inject constructor`，Dagger 就能自行构造，**不用改任何 Hilt 模块**」。**这个说法是错的**：

- 给实现类加 `@Inject constructor` 只为**实现类本身**建立绑定，`FileOps` 接口仍是 `cannot be provided without an @Provides-annotated method`。
- 设计稿把它当成「零 Hilt 模块改动」的推荐做法，会直接导致 Hilt 编译期失败。

**本轮改为显式接口绑定**（沿用本仓库 `LibrarySourceModule` / `DeletionModule` 的既有 `@Provides` 写法），并**以真实 Hilt 编译产物证明**，见 §3.2。

### 0.4 另一处设计稿未察觉的约束：可见性

设计稿 §4.2 写 `internal interface FileOps`。但 `EpubRepository` 是**公开类**，其构造函数参数类型不能是 `internal`（Kotlin 会报 `'public' function exposes its 'internal' parameter type`）。**本轮把 `FileOps` 定为 `public`**，实现类与 Hilt 模块定为 `private` / `internal`，公开面最小。

---

## 1. 改动清单（4 个文件）

| # | 文件 | 改动 | 规模 |
|---|---|---|---|
| 1 | **新增** `feature/reader/FileOps.kt` | `interface FileOps`（`renameTo` / `copyTo` 两个方法）+ `private class DefaultFileOps` + `@Module @Provides` 绑定 | 新增 68 行 |
| 2 | `feature/reader/EpubRepository.kt` | 构造函数新增 `private val fileOps: FileOps`（`:32`） | +2 行 |
| 3 | `feature/reader/EpubRepository.kt` | `:136` `target.renameTo(backup)` → `fileOps.renameTo(target, backup)`；`:141` `target.copyTo(backup, overwrite = true)` → `fileOps.copyTo(target, backup)` | 2 行 |
| 4 | `EpubRepositoryReplaceTest.kt` | 构造调用补 `fileOps = fileOps`；新增 `FakeFileOps` 与 3 条用例；类级文档更新 | +3 用例（10 → 13） |

### 1.1 明确**未**改动（逐项核对）

| 项 | 状态 |
|---|---|
| `:96` 入口残留守卫 | **未弱化**，一字未改 |
| 自动恢复策略 | **未改动**（守卫仍是「只拒绝、不恢复」） |
| `:126-127` `backupReady` / `promoteStarted` 两个标记 | 未改 |
| `:142` `require(backup.length() == target.length())` | **未改**，仍是残缺备份不得被采纳的唯一闸门 |
| `:144` `target.delete()` 的位置 | 未改（仍在 `require` 之后） |
| `:189-199` 回滚分流 | **未改**（保持按 `backupReady` 分流） |
| `:205` `finally { staging.delete() }` | 未改 |
| `staging` 的复制 / 提升、回滚侧的 `backup.renameTo/copyTo` | **未接缝化**（接缝范围刻意保持最小） |

`DefaultFileOps` 与 `File.copyTo(dest, overwrite = true)` 逐字同义（直接转发），不引入行为漂移；由 §4 的 G1 钉住。

---

## 2. 三条新用例

均通过 `FakeFileOps` 确定性注入，无时序依赖、无 FS 竞态。

| 用例 | 注入 | 覆盖的路径 |
|---|---|---|
| `...falls back to copy when rename fails and completes the swap`（G1） | `renameResult = false`，`copyBehavior` = 真实复制 | **回退分支 happy path**：`renameTo` 失败 → `copyTo` 成功 → `require` 通过 → `target.delete()` → 替换完成，无残留 |
| `...keeps target intact when fallback copy throws mid-write`（R1） | `renameResult = false`，`copyBehavior` = 写一半后抛 `IOException` | `copyTo` 中途失败 → `backupReady` 仍 false → 回滚走 `if (backup.exists()) backup.delete()`；**旧 target 逐字节存活** |
| `...rejects truncated fallback backup via length check`（R2） | `renameResult = false`，`copyBehavior` = 少写 1 字节后**正常返回** | `:142` `require` 被触发（**该分支在本片之前从未被任何用例执行过**）；截断备份未被采纳 |

### 2.1 每条都带「确实进入目标分支」的正向证据

```
assertTrue(fileOps.renameCalls >= 1)     // 证明走到 :136
assertEquals(1, fileOps.copyCalls)       // 证明走到 :141
R2 额外：caught.message 含 "备份旧副本时文件不完整"   // 证明 :142 执行
```

没有这组证据，断言可能因失败发生在更早的 staging 阶段而**恒真**（旧断言里「backup 必须不存在」正是这种恒真断言的例子）。三条用例对旧 target 的结局一律用 `assertArrayEquals(oldBytes, target.readBytes())` **逐字节**断言，而非仅 `exists()`。

### 2.2 backup 断言的方向

R1/R2 中 `backup` 是**残缺文件**、`target` 是完好副本 ⇒ backup 不是唯一副本，`:198` 删掉它是**正确**的。因此断言是「**backup 不存在**」，而不是「backup 必须保留」。后者只适用于 `backupReady == true` 的场景，已由既有 post-swap 用例覆盖。

---

## 3. 编译验证（实测）

### 3.1 命令与退出码

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --offline` | **0** | BUILD SUCCESSFUL（6m40s） |
| 2 | `./gradlew :app:assembleDebug --offline` | **0** | BUILD SUCCESSFUL（38s） |
| 3 | `./gradlew :app:compileDebugAndroidTestKotlin --offline` | **0** | BUILD SUCCESSFUL（9s） |
| 4 | `git diff --check`（仅限本窗口 2 个已跟踪文件） | **0** | 无空白错误 |

定向运行（先于全量）：`./gradlew :app:testDebugUnitTest --tests "...EpubRepositoryReplaceTest" --offline` → 退出码 0，`13 tests completed, 0 failed`。

### 3.2 Hilt 绑定生效的**编译产物证据**

设计稿只做了静态推断，本轮实测。`app/build/generated/hilt/component_sources/debug/com/creationreadingassistant/DaggerApp_HiltComponents_SingletonC.java` 中：

```java
// :1276 —— EpubRepository 的 fileOps 参数由 Hilt 注入
return (T) new EpubRepository(
    ApplicationContextModule_ProvideContextFactory.provideContext(...),
    singletonCImpl.bookDao(),
    singletonCImpl.readingProgressDao(),
    CoroutineScopeModule_ProvideIODispatcherFactory.provideIODispatcher(),
    singletonCImpl.provideFileOpsProvider.get());     // ← FileOps 绑定

// :1282
return (T) FileOpsModule_ProvideFileOpsFactory.provideFileOps();
```

`App_HiltComponents.java:156` 也登记了 `HiltWrapper_FileOpsModule.class`。⇒ **Hilt 图完整，接口绑定成立，无需在其它 DI 文件中登记。**

### 3.3 全量 JVM 测试汇总（XML 聚合）

解析 `app/build/test-results/testDebugUnitTest/TEST-*.xml`（323 个 XML）：

```
tests = 3051   failures = 0   errors = 0   skipped = 0
```

### 3.4 APK

| 项 | 值 |
|---|---|
| 路径 | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | 52,484,589 字节 |
| `sha256` | `33282ff501d276193dc9c23c008cb15270fde9872acf0cb8335bb55742275892` |

`compileDebugAndroidTestKotlin` 实际执行（`kspDebugAndroidTestKotlin` + `compileDebugAndroidTestKotlin` 均非 UP-TO-DATE）；本轮**未**跑 `assembleDebugAndroidTest`，因此**不产出** androidTest APK 哈希。

---

## 4. 变异测试：把「旧实现会丢数据」从推断变成实测（本轮最强证据）

新用例只有在能抓住**真实缺陷**时才有价值。因此做了一次变异验证：把回滚临时改回旧实现（把 `backup.exists()` 当作「备份可用」判据），看新用例是否报警。

### 4.1 变异内容与还原

```
变异：:189-199 的 backupReady 分流  →  旧写法 if (backup.exists()) { ... }
变异后哈希：feb234cf07116e16d7971447cf57432dac1fc57e2fcfcff6f27e9107f0ec5655
还原后哈希：45e0331dc5e85627459ffef4bb3777de387baeea115104f8f78b85489ab760e1  ← 与变异前逐字节一致
```

（还原用变异前的备份覆盖，并以哈希比对确认；`grep MUTANT` 无命中，无残留标记。）

### 4.2 实测结果

| 用例 | 变异下的结果 | 失败消息（原文） |
|---|---|---|
| G1（回退成功） | **通过** | — （正确：该路径下旧实现行为相同） |
| R1（中途抛错） | **失败** ✅ 抓住 | `旧 target 必须逐字节完好，绝不能被残缺备份覆盖: array lengths differed, expected.length=1467 actual.length=733` |
| R2（截断返回） | **失败** ✅ 抓住 | `截断备份绝不得覆盖完好旧副本: array lengths differed, expected.length=1467 actual.length=1466` |

**这不是「断言不够严」，是真的丢数据**：旧实现下，一份完好的 1467 字节旧正文被残缺备份覆盖 —— R1 场景只剩 733 字节（正好是写了一半的长度），R2 场景只剩 1466 字节（少 1 字节）。**用户仅存的正文被静默替换成不可读的半截文件。**

⇒ 新用例确实钉住了 `backupReady` 分流这条防线，且覆盖了它。这是对设计稿 §6.2「RED」声明的**实测替代**。

### 4.3 一个测试写法上的坑（值得记录）

R1 最初用 `caught === injected` 做异常身份判定，**必然失败**。原因：`kotlinx.coroutines` 的**栈回溯恢复**（stack trace recovery）会在异常跨协程边界（`withContext`）时**复制**异常对象 —— 副本保留原消息与原栈帧，但引用不同。实测两者消息相同、构造帧完全相同（均指向声明 `injected` 的那一行），而 `identityHashCode` 不同。

⇒ 跨 `withContext` 边界断言异常身份**必须**改用「类型 + 独有消息」，不能靠 `===`。**这不是产品代码缺陷**，是测试写法的陷阱；已在用例注释中记录，避免后来者重踩。

---

## 5. 仍然未覆盖 / 未验证（不得当作已覆盖）

### 5.1 本片之后仍然存在的覆盖缺口

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **长度相同、内容不同的损坏备份** | `:142` 只比长度。R2 只能证明「**截断**被拦」，**不能**证明「同长度内容不符被拦」。R2 **不得**被用来冒充这一点。 |
| 2 | **回滚自身失败** | `:191 backup.renameTo(target)` 失败后 `:192 backup.copyTo(target, overwrite = true)` 若再抛错，异常会**顶替**原始 `error`（`:203 throw error` 不再执行）。数据可恢复（backup 仍在盘上），但目标处于半程。**本轮未覆盖**（回滚侧未接缝化）。 |
| 3 | **`require` 通过之后、`target.delete()` 之前进程被杀** | 此时 target 尚在、backup 已完整、内存标记丢失。下次运行由入口守卫接管，但守卫**只拒绝、不恢复**，会把用户卡住。属崩溃残留状态机的独立设计面。 |
| 4 | **无 `fsync`** | `copyTo` 返回后目录项未必落盘；掉电时「已返回但未持久化」可能出现。设计稿 `2026-09-24-txt-backup-crash-recovery-design.md` §7-3 已列此硬边界。 |
| 5 | **同一本书的并发替换** | 本分支无 per-book 互斥。 |

### 5.2 明确区分的验证层级

- ✅ **JVM / 构建层**：已实测通过（§3）。
- ✅ **Hilt 依赖图**：已实测通过（§3.2，编译产物证据）。
- ✅ **回退分支的三条路径**：已实测覆盖，且经变异测试证明用例有效（§4）。
- ❌ **真实 Room / 真机行为**：**本轮未执行**，不得写成 PASS。
- ❌ **回退分支的生产触发频率**：未取证（§0.1）。§0.1 已撤回「跨挂载点」这一不适用例子。

---

## 6. 快照与文件哈希

| 文件 | `sha256` |
|---|---|
| `feature/reader/FileOps.kt`（新增） | `1948949584928b5337d3c334cd7c3f21f72de55b68cb2e7c87564297371133b5` |
| `feature/reader/EpubRepository.kt` | `45e0331dc5e85627459ffef4bb3777de387baeea115104f8f78b85489ab760e1` |
| `feature/reader/EpubRepositoryReplaceTest.kt` | `84cf2be000109a9b6c1a62ce881bffed01247a72766ef74a8b3a7b7fbbd48692` |

改动后的关键行号（供其他窗口引用）：`:32` `fileOps` 参数 · `:96` 入口守卫 · `:126-127` 两个标记 · `:136` `fileOps.renameTo` · `:141` `fileOps.copyTo` · `:142` `require` 长度校验 · `:144` `target.delete()` · `:189` `if (backupReady)` · `:198` `backup.delete()` · `:203` `throw error` · `:205` `staging.delete()`。

**注意**：`EpubRepositoryReplaceTest.kt` 在 git 中**未被跟踪**（`??`，从未提交过），因此 `git diff` 对它无输出。其内容以本报告 §6 的哈希为准。

---

## 7. 边界声明

- 只改动 §1 列出的 3 个源码/测试文件 + 本报告。**未**触碰其他窗口的文件、总账或既有工作区改动。
- **未**操作手机 / 模拟器 / ADB；**未**接触用户书库；**未**在真机或任何真实文件系统上制造故障（故障全部在 JVM 临时目录内注入）。
- **未**弱化入口残留守卫；**未**改动自动恢复策略；**未**改变 `require` 长度校验与 `target.delete()` 的相对位置。
- **未执行** `commit / push / stage / reset / checkout / clean / stash`。HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0。
- 本报告不含真实书名、私人目录名或日志原文。
