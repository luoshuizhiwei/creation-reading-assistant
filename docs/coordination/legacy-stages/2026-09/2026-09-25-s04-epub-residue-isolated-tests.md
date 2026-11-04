# S04 EPUB「已有 .backup 残留时拒绝替换」隔离测试补强

- **日期**：2026-09-25
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`
- **改动文件**（仅此两个，均在授权范围内）：
  - `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubReplaceRoomIsolatedTest.kt`
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-residue-isolated-tests.md`（本文）
- **结论**：新增 **2** 个确定性测试；**编译 / 构建通过**。
  **未运行真机测试** —— 因此只能写「编译/构建通过」，**不得**写「新用例通过」。

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目录 / 分支 / HEAD | 一致 ✓ |
| 目标报告是否已存在 | **否** → 首次撰写，非重复施工 ✓ |
| 两个授权文件是否正被他人改动 | **否** —— 测试类 mtime `2026-09-25 03:00`（约 13 小时前），本次为当前唯一写入者 ✓ |
| 是否覆盖他人改动 | **否** —— 见 §4，`IsolatedTestContext.kt` 中他人新增的 `getApplicationContext()` 覆写**原样保留** ✓ |

> 说明：`IsolatedTestContext.kt` 属**其他 agent** 的文件（不在我的授权范围），
> 它在会话间新增了 `getApplicationContext()` 覆写。我**未修改**它，且我的新用例
> 依赖它带来的隔离保证（见 §2.3）。

---

## 1. 被锁定的生产行为（守卫）

`EpubRepository.replaceStoredEpub`（`EpubRepository.kt:96`）的入口守卫：

```kotlin
if (backup.exists()) {
    throw IllegalStateException(
        "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
            "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
            "重复重试不会自动恢复该备份",
    )
}
```

设计意图（源码 KDoc 原话）：仅凭 target 是否存在或长度**无法区分**
「上次被杀死在中间」与「上次已提交成功但留下过期 backup」两种现场，
故**一律拒绝**，且**绝不**删除、覆盖或自动恢复任何一侧。

关键位置事实：守卫在 `EpubParser.parse` **之前**（第 96 行 vs 第 119 行），
因此拒绝时不会落下任何 `.importing`。

---

## 2. 新增测试（2 个）

两个用例都只用**合成测试 EPUB** + **测试专属目录** + **in-memory Room**；
不触碰正式应用目录或用户书籍；清理仅限本次创建的 `testRoot`。

### 2.1 现场 A：target 与同名 `.backup` **同时存在**

`backupResidueWithTargetPresentIsRefusedAndPreservesBothSides`

真机常见形态：上次替换已把旧副本复制进 backup，随后进程被杀在「提升新副本」之前。

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 抛 `IllegalStateException`，且消息含「未完成的替换残留」「本次替换已停止」「重复重试不会自动恢复」 | 拒绝发生且错误可识别 |
| 2 | 旧 target **逐字节不变**（`assertArrayEquals`） | 旧正文未被破坏 |
| 3 | 残留 backup **逐字节不变**（内容刻意与 target 不同，便于识别被覆盖） | 残留不被删除 / 不被覆盖 / **不被提升为 target** |
| 4 | `books` 行与 `reading_progress` 行**整行数据类相等** | 守卫在任何 DAO 调用之前，Room 零推进 |
| 5 | 不产生**新的** `.importing` / `.repairing` | 守卫先于 parse，本次未落任何临时文件 |

**关键的构造细节**：用例把来源**改成合法的新 EPUB** 之后才调用替换。
这样「被拒绝」只可能来自残留守卫，而**不可能**被误读成「来源非法所以失败」。

**为何不复用 `assertNoResidue`**：该辅助断言「`.backup` 必须不存在」，
与本现场的「backup 必须原样保留」**正相反**，复用会自相矛盾。已在用例 KDoc 中注明。

### 2.2 现场 B：target **缺失**、`.backup` 存在（K2 现场）

`backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores`

构造：用 `target.renameTo(backup)` 制造 K2 现场 —— 旧副本已整体挪进 backup，
target 消失，此时 **backup 很可能就是唯一完好的旧正文**。

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 仍抛 `IllegalStateException`（不能因 target 缺失就「顺手」继续替换） | 守卫不依赖 target 是否存在 |
| 2 | backup **逐字节保留** | 唯一副本未被销毁 |
| 3 | `target` 仍然**不存在** | **不自动恢复** —— 不擅自把 backup 提升成 target |
| 4 | 消息含「若仍存在」（**条件式**表述） | 提示语对「target 缺失」现场仍为真，**不虚称** target 仍在 |
| 5 | Room 整行不变（含 `updated_at`） | 不写库、不虚增行 |

第 4 条是对**提示语诚实性**的断言：同一句文案要覆盖 target 存在/缺失两种现场，
因此对旧正文的表述必须是条件式；若文案改成「原有文件已保留」这类绝对说法，本用例会失败。

### 2.3 隔离保证（沿用，未新造）

两个新用例的路径全部落在测试专属 `testRoot` 内：

- 仓储拿到 `IsolatedTestContext`（`filesDir` → `<testRoot>/files`）；
- 每个 target / backup 路径都经 `assertInsideRoot` 取得，再跑 `assertInsideRootSelfCheck`；
- backup 由测试直接写入 `<testRoot>/files/books/epub/<id>.backup`；
- 现场 B 的 `renameTo` 是 testRoot 内的同目录移动；
- `@After` 只删 `testRoot` 本身（唯一命名目录），**不对宽泛路径递归删除**；
- 全程无 `Room.databaseBuilder`，只用 `inMemoryDatabaseBuilder`。

---

## 3. 构建记录（原始命令 / 退出码 / 产物）

工作目录 `…/creation-reading-assistant-recovery-20260919/android`，全部 `--offline`
并带 `-PcraAndroidTestBuildType=qahost`。

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:compileQahostAndroidTestKotlin --offline -PcraAndroidTestBuildType=qahost` | **0** | BUILD SUCCESSFUL in 29s |
| 2 | `./gradlew :app:assembleQahostAndroidTest --offline -PcraAndroidTestBuildType=qahost` | **0** | BUILD SUCCESSFUL in 17s |

**产物**：

| 项 | 值 |
|---|---|
| 路径 | `app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk` |
| 大小 | 1,554,302 字节 |
| SHA-256 | `cefb77c0aebc075264095e49827182ec5b0dddc5e41832cef89cad8eeaa061ef` |
| 构建时间 | 2026-09-25 16:49:53 +0800 |

**包名 / Runner / targetPackage**（从测试 APK 的 `AndroidManifest.xml` 解出）：

| 项 | 值 |
|---|---|
| targetPackage | `com.creationreadingassistant.qahost` |
| 测试包名 | `com.creationreadingassistant.qahost.test` |
| instrumentation runner | `com.creationreadingassistant.testing.CraIsolatedTestRunner` |

> 附带观察：runner 已由集成者从默认 `AndroidJUnitRunner` 换成
> `CraIsolatedTestRunner`（覆写 `newApplication`，用 `CraIsolatedTestApplication`
> 替换目标 Application）。这与早前隔离报告中「命题 C：目标 App 启动副作用」的
> SEAM REQUEST 对应 —— 沙箱变体下该副作用应已消除。**但本窗口未验证其设备行为**，
> 仅登记此事实。

---

## 4. 未改动 / 未覆盖

**未改动**：

- 既有 **6** 个测试**语义与顺序原样保留**（`@Test` 总数 6 → 8，新增的 2 个追加在
  第 6 个之后、`辅助` 区之前）；
- `IsolatedTestContext.kt`（他人文件）**未触碰**，其 `getApplicationContext()`
  覆写原样保留；
- 生产源码 `EpubRepository.kt` / `EpubParser.kt` / `App.kt` **未改动**；
- `build.gradle.kts` / `AndroidManifest.xml` / 其他 agent 文件 **未改动**。

**未覆盖 / 边界**：

1. **未运行真机**。本文所有「通过」仅指**编译与构建**；
   两个新用例是否在设备上通过 **UNVERIFIED**。
2. **未运行 instrumentation**、未用 adb、未安装/重装 APK、未操作 E 盘备份、
   未触碰原始仓库或手机。
3. `.backup` 为**目录**形态的残留（JVM 版已覆盖）在 androidTest 层**未复现** ——
   本层覆盖的是**文件**形态，即真机更常见的形态。
4. **断电 / 进程被杀**仍不可测（沿袭既有登记）。
5. `contentResolver` 之外未被 `IsolatedTestContext` 覆盖的 context 成员
   （`getExternalFilesDir` / `getDir` / `getDatabasePath`）在本可达路径中未被调用，
   沿袭既有边界说明。
6. 未运行 `connectedQahostAndroidTest` / `connectedDebugAndroidTest`；
   未运行其余 androidTest 类。

---

## 5. 合规自证

| 动作 | 是否执行 |
|---|---|
| 离线编译 + androidTest APK 构建（qahost 变体） | ✅ 2 条命令 |
| 修改授权文件（测试类 + 本报告） | ✅ |
| 修改生产源码 / 共享 Gradle / manifest | ❌ |
| 覆盖其他 agent 文件 | ❌ |
| adb / 安装 / 重装 / instrumentation / 操作手机 | ❌ |
| 操作 E 盘备份 / 原始仓库 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
