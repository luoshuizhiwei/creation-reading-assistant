# S04 EPUB 测试文档注释更正（隔离说明过期）

- **日期**：2026-09-25
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`
- **改动文件**（仅此两个，均在授权范围内）：
  - `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubReplaceRoomIsolatedTest.kt`（**仅注释**）
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-isolation-comment-correction.md`（本文）
- **性质**：**纯注释更正**。测试方法、断言、生产逻辑、Gradle 一律未动（§4 给出证明）。
- **未运行真机**（纯注释改动无需真机验证）。

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目录 / 分支 / HEAD | 一致 ✓ |
| 目标报告是否已存在 | **否** → 首次撰写 ✓ |
| 是否覆盖他人改动 | **否** —— `IsolatedTestContext.kt`、`CraIsolatedTestRunner.kt`、`build.gradle.kts` 哈希见 §4，均未改动 ✓ |

---

## 1. 先核对的四项事实（更正依据）

更正前先核实，避免把「应该已修」当成「确实已修」：

| # | 事实 | 核对方式 | 结果 |
|---|---|---|---|
| 1 | `testInstrumentationRunner` 实际指向 | `app/build.gradle.kts:32`（`defaultConfig` 内，故 qahost 变体继承） | `com.creationreadingassistant.testing.CraIsolatedTestRunner` ✓ |
| 2 | Runner 行为 | `CraIsolatedTestRunner.kt` | 覆写 `newApplication`，注入 `CraIsolatedTestApplication` ✓ |
| 3 | 隔离 Application 是否继承生产 `App` | `CraIsolatedTestApplication.kt` | `class CraIsolatedTestApplication : Application()` —— **裸 Application，不继承** ✓ |
| 4 | qahost 变体是否真用该 runner | 解包已构建的 `app-qahost-androidTest.apk` 的 `AndroidManifest.xml` | runner = `…CraIsolatedTestRunner`，targetPackage = `com.creationreadingassistant.qahost` ✓ |

⇒ 结论：`App.onCreate` 的三条 `cra-startup` 接线在 qahost 测试进程中**不会执行**。
原注释「目标 App 启动副作用 ❌ 不成立 / 未消除，已提交 SEAM REQUEST」**确已过时**。

---

## 2. 更正内容（命题 C）

### 2.1 主更正：类级 KDoc 的命题 C 行

| | 原表述 | 更正后 |
|---|---|---|
| 判定 | ❌ **不成立 / 未消除** | ✅ **成立**（2026-09-25 更正，原表述已过时） |
| 依据 | 「消除它需改 test manifest / runner / build.gradle —— 超出本窗口所有权，已提交 SEAM REQUEST」 | 「`defaultConfig` 已把 `testInstrumentationRunner` 指向 `CraIsolatedTestRunner`；该 Runner 注入 `CraIsolatedTestApplication`（不继承生产 `App`）⇒ 三条 `cra-startup` 接线不会执行」 |

并新增「命题 C 的准确边界」小节，**把两个不同命题分开写**（这是本次更正的要点）：

| 命题 | 判定 | 说明 |
|---|---|---|
| **「qahost 测试进程使用隔离 Application」** | ✅ **成立** | 测试进程内目标 Application 是 `CraIsolatedTestApplication`；`App.onCreate` 不执行；不会经 Hilt 构造正式 `AppDatabase`；不触发搜索索引 / 目标调度 / 系统夜间模式设置 |
| **「正式应用的所有设备状态绝对安全」** | ❌ **未证明，且不成立** | 换 Application 只改变**测试进程内**的 Application 实例，不改变设备上的其他事实 |

「已知残余」三条（保留，防止读者由 C 成立过度外推）：

1. `androidx.startup` 的 `InitializationProvider`（ContentProvider）由主 APK 合并清单驱动，
   **先于** `Application.onCreate` 执行 ⇒ `WorkManagerInitializer` 等仍会运行并创建
   `androidx.work.workdb`。**已实测确认**：解包 `app-qahost.apk` 的 manifest 可见
   `androidx.startup.InitializationProvider` 与 `androidx.work.WorkManagerInitializer`。
   该库不是 Room 正式库，且换 Application 无法影响 ContentProvider 时序。
   （此条已在本仓库 `CraIsolatedTestApplication` KDoc 中登记，本次仅引用。）
2. 设备上的其他状态（Keystore、系统设置、包管理器状态、其他应用数据）不在本测试可控范围。
3. 正式 APK 自身仍是 `com.creationreadingassistant.App`，行为不受本文件影响。

另加「历史说明」引用块，写明 C 行此前为 ❌ 是**集成者切换 runner 之前**的状态，
该 SEAM 已落地故更正为 ✅ —— 避免后续读者误以为两处记录矛盾。

### 2.2 顺带更正：同类过期/不精确表述（均在同一文件）

按「检查同一文件其他紧邻说明是否存在同类过期表述」的要求，发现并修正 **5 处**。
它们**不是**「命题 C 过期」，而是**「正式目录」一词在 qahost 变体下不准确**：

| 位置 | 原表述 | 问题 | 更正 |
|---|---|---|---|
| 类 KDoc 命题 B 行 | 「正式 `files/books/epub/` 不在任何被测写入路径上」 | qahost 下 targetPackage 是沙箱包，不叫「正式」 | 改为「**目标包**的 `files/books/epub/`」，并注明随变体而异 |
| `setUp` 清理注释 | 「不再依据 bookId 去**应用正式目录**里逐个删文件」 | 同上 | 改为「目标包的书目录」 |
| 契约 1 内联注释 | 「而非**应用正式 books 目录**」 | 同上 | 改为「目标包自己的 books 目录」 |
| `replaceNeverWritesIntoProductionFilesDirectory` 的 KDoc | 「应用正式 `files/books/epub/` 目录必须仍然不存在……」 | 未区分变体，读者会误以为该断言始终在检查正式包 | 改为「**目标包**的……」，并**新增一段**说明：qahost 变体下检查的是沙箱包私有目录，debug 变体下才是正式包；两变体下断言均成立，但**不得**据此声称「正式应用所有设备状态绝对安全」 |
| `newRepository` / `assertInsideRootSelfCheck` 注释 | 「**正式目录**不参与」「target 会落到**正式目录**」 | 同上 | 改为「目标包的书目录」 |

### 2.3 另一处小修正：KDoc 链接

`[CraIsolatedTestApplication]` / `[CraIsolatedTestRunner]` 是跨包符号且未 import，
KDoc 链接无法解析。已改为普通代码格式 `` `CraIsolatedTestApplication` ``，
避免留下断链。（同文件其余 `[...]` 链接均指向同包符号，保持不变。）

---

## 3. 未改动边界

- **测试方法**：8 个，名称、顺序、语义全部未动（`@Test` 计数 8 → 8）。
- **断言**：99 处 `assert*` 调用全部未动；断言**消息字符串**逐字保留
  （例如 `"正式 files/books/epub/ 不得出现本次 bookId 的文件，实际=$intruders"` 保持原样
  —— 更正只动注释，不动运行期字符串）。
- **生产逻辑**：未触碰。
- **Gradle / manifest / runner / Application**：未触碰。
- **其他 agent 文件**：未触碰。

---

## 4. 改动性质的证明（为何是「纯注释」）

该文件在本仓库中**未被 git 跟踪**（`??`），无法用 `git diff` 直接对比，
且下文 §5 证明本工程构建**不可复现**，故 APK 哈希也不能作为「仅注释变化」的证据。
因此改用**结构性证明**：

**（a）逐条校验：本轮所有编辑点都落在注释行上**

脚本对 13 个标记性短语逐条定位，要求其所在行 `strip()` 后以 `*`、`//` 或 `/*` 开头：

```
markers checked: 13
OK: every edited marker sits on a comment line
```

**（b）代码行内容未变**（剥离注释与空行后）：

```
code_lines: 459
code_sha256: 56843edb0cc2ca967ff20888f7b6347024dc7d6987fb5bb150e9d88a54b5bca7
```

**（c）方法签名清单未变**（8 个测试 + 9 个辅助/覆写，行号有位移属正常）：

```
setUp / tearDown
sameUriV1ToV2ReplaceReadsFreshBytesAndKeepsBookIdAndProgress
invalidEpubReplaceIsRejectedAndPreservesEverything
unreadableSourceIsRejectedWithoutTouchingTarget
postSwapDatabaseWriteFailureRollsBackFileAndKeepsOldRow
databaseUnderTestIsInMemoryAndNeverTheProductionDatabase
replaceNeverWritesIntoProductionFilesDirectory
backupResidueWithTargetPresentIsRefusedAndPreservesBothSides
backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores
newRepository / assertInsideRootSelfCheck / DirectFileOps.renameTo / DirectFileOps.copyTo
assertNoResidue / md5Of / writeEpub
```

**（d）未改动文件的哈希**（与接手时一致）：

| 文件 | sha256 |
|---|---|
| `…/reader/EpubRepository.kt`（生产） | `f3846ef7f2af78458ee8bdf4e302b12ae31edd1f47af331ad76c53d6ffc2214b` |
| `…/reader/EpubParser.kt`（生产） | `af223434c24d4773c12f2b7e34b338a271738ce6b3082b67aba37e405bdfdeaf` |
| `app/build.gradle.kts` | `aac93f05a51d801759bfd0a6678a1267f5d34dfb2b64132c0bf4fbeb63439606` |
| `…/reader/IsolatedTestContext.kt`（他人文件） | `7d97e13ba846eb13e198a9d82c9bcbc9c8dd20cd92054569090a5fa8f099c038` |
| `…/testing/CraIsolatedTestRunner.kt`（他人文件） | `343b7fb11797705cb83ef77473ae828d31cef62b2537185bc62b8eb791a6b074` |

---

## 5. 构建记录

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:compileQahostAndroidTestKotlin --offline -PcraAndroidTestBuildType=qahost` | **0** | BUILD SUCCESSFUL（复跑两次均 0） |
| 2 | `./gradlew :app:assembleQahostAndroidTest --offline -PcraAndroidTestBuildType=qahost` | **0** | BUILD SUCCESSFUL |

产物：`app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk`，
1,554,297 字节，sha256 `c611649c6d0acd9157adbf104102a55bbc1b0641b3342182cf7b8a32f7c33775`。

> ⚠️ **重要发现：本工程 androidTest APK 构建不可复现。**
> 对**同一份源码**连续两次 `--rerun-tasks` 构建，得到不同哈希：
> `c611649c…` vs `6d19c244…`。逐条目比对显示 87 个条目中 77 个完全相同，
> 差异集中在 **10 个 `classes*.dex`**（共 14 个 dex）—— 属 D8/R8 多 dex 分包
> 与 debug 元数据中的**非确定性**，**不是**本次注释改动造成的。
>
> **含义**：本工程的 APK sha256 **不能**用作「产物与某次源码状态一一对应」的证据，
> 也不能用来证明「仅注释变化」。引用哈希时须同时说明这一点。
> （此发现与本任务无关但值得登记；不属本窗口应修的范围，未作处理。）

---

## 6. 未覆盖 / 边界

1. **未运行真机**、未用 adb、未安装/重装 APK、未运行 instrumentation ——
   纯注释改动不需要，故本文所有「通过」仅指**编译与构建**。
2. 更正**只解决文档准确性**，不改变任何运行期行为；
   命题 C 的「已知残余」（`InitializationProvider` / `WorkManagerInitializer` 仍运行）
   **依然存在**，本次未处理（也不在授权范围）。
3. `CraIsolatedTestRunner.kt` 自身 KDoc 仍写着「在集成者切换 runner 之前，本类不会被
   任何测试使用，属无害死代码」与「设备行为尚未验证」—— **该文件不属本窗口所有权，
   未修改**。此处登记，供集成者后续处理。
4. 本工程 APK 构建不可复现（§5），属既有工程问题，未处理。
5. `contentResolver` 之外未被 `IsolatedTestContext` 覆盖的 context 成员，沿袭既有边界。

---

## 7. 合规自证

| 动作 | 是否执行 |
|---|---|
| 修改授权文件（测试类注释 + 本报告） | ✅ |
| 改测试方法 / 断言 / 断言消息字符串 | ❌ |
| 改生产逻辑 / Gradle / manifest / runner | ❌ |
| 覆盖其他 agent 文件 | ❌ |
| adb / 安装 / instrumentation / 操作手机 | ❌ |
| 操作正式书库 / E 盘备份 / 原始仓库 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
