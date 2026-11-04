# TXT 重新选文件：崩溃残留第一道守卫

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（未变）
- **日期**：2026-09-24

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 入口无条件 `backup.delete()` 销毁崩溃残留 | **已修**（改为检测即停止并保留现场） |
| 定向测试 | **46 → 50 条，全绿**；红相已验证（恰好 3 条新用例失败） |
| 自动恢复状态机 | **未实现**（按指示，属 B 设计稿范围） |
| 无残留时的正常修复与进程内回滚 | **行为不变**（既有用例全绿） |

---

## 1. 基线

### 1.1 改动文件

| 文件 | 改前 SHA-256 | 改后 SHA-256 |
|---|---|---|
| `feature/library/ShelfImporter.kt` | `86c147eede38aae7c21b1adb883fc55fa8aaae5000dcde9395c73839b33539c3` | `d1b36c5395e760051ec1af28db7361eb8efdb527519a49d795409713b3fd7e54` |
| `ui/viewmodel/ShelfImporterTest.kt` | `70241b0327148762e71dee87b91d4e12906903fed12f4a5a2f487ffffc68f90c` | `68fd028bf72f72040865eb1af3bf3c1a61082a50cd5e3f8d92b7c01f1e31833e` |

**未触碰**：`EpubRepository.kt`、`PendingTurnQueue.kt`、总账、其他窗口文件。
**无 SEAM REQUEST** —— 改动落在授权文件内。

### 1.2 测试基线（改前，本人亲自读 XML）

改前最后一次 `ShelfImporterTest` 运行是**窗口 D** 在 `2026-09-24 14:41` 的产物：
`tests=46 skipped=0 failures=0`。我的源码编辑发生在 14:46，故 **46 是干净基线**。

---

## 2. 缺陷与修复

### 2.1 原实现（改动前 `:953-954`）

```kotlin
staging.delete()
backup.delete()      // ← 无条件
```

`backup` 的来源在入口处**无法判定**：

- **可能是上次进程被杀后唯一完好的正文** —— 崩在「旧文件已挪进 backup、新文件尚未落位」
  （设计稿 §3.2 的 K2 / `SWAPPING` 态）：此时 `internalFile` 已不存在，
  **backup 就是用户仅存的那份正文**。入口删掉它 = 永久销毁唯一副本。
- **也可能是上次已提交成功的过期副本** —— 崩在 S9 提交后、S10 清理前
  （K4 / `COMMITTED` 态）：此时它确实可以清理。

仅凭 `target` 是否存在或长度**无法区分**这两种情况（设计稿 §E1.1 已论证：
`SWAPPING` 与「COMMITTED 后 target 被外部删除」磁盘形态完全相同）。

### 2.2 修复（`:954-967`）

```kotlin
staging.delete()
if (backup.exists()) {
    throw IllegalStateException(
        "检测到上次未完成的替换残留（${backup.name}），已停止本次替换以免覆盖可能完好的旧正文；" +
            "请先在书架中确认该书内容，再重试",
    )
}
```

要点：

1. **不删除、不覆盖、不自动恢复** —— 只检测并停止。
2. **保留现场**：`backup` 原样保留、`internalFile` 一字未动、DB 记录不动。
3. **失败结果可识别**：异常经 `repairFileTyped` 的既有 `catch (e: Throwable)`
   （`ShelfImporter.kt:331-338`）转成 `RepairOutcome(false, "修复失败：<msg>")`，
   调用方拿到的是带「残留」字样的可读消息，而非静默成功。
4. **`staging.delete()` 保持无条件**：`staging` 永远是「本次所选文件的未完成副本」，
   用户手上仍有来源文件，删掉不丢用户数据。
5. **守卫位置在 `try` 之前**：因此不会进入 `catch` 的阶段化回滚，
   也就不会有任何 `delete()` 落到 `internalFile` 或 `backup` 上。
6. **无残留时零变化**：`backup` 不存在 ⇒ 直接走原有流程，进程内回滚（阶段分流）语义不变。

> **未实现的部分**：设计稿 §E2.2 的 N1–N10 自动恢复状态机（按 DB hash 匹配决定
> 恢复 / 清理 / 保留）**本轮不做**。本守卫只做「不销毁」这一件事。

---

## 3. 测试

### 3.1 新增 4 条（`ShelfImporterTest.kt`，接在既有 reselect 用例之后）

| # | 用例 | 构造 | 断言 |
|---|---|---|---|
| 1 | `txt repair stops and keeps residue when backup exists and target is missing` | `target` 缺失 + `backup` 存在 | 报「修复失败」；**backup 逐字节保留**；target 未被创建；`bookDao.upsert` / `runInTransaction` **零调用** |
| 2 | `txt repair stops and keeps both files when target and backup both exist` | `target` 与 `backup` 均存在 | **两者都逐字节未被改写**；零写库 |
| 3 | `txt repair stops when a same-name residue exists under a newly chosen file name` | DB 指向 `<旧名>`，本次 safeName=`<新名>`，`<新名>.backup` 存在 | 残留与 DB 指向的旧文件**都逐字节不变**；本次新名 target 未被创建；零写库 |
| 4 | `txt repair succeeds normally when no residue exists` | 无残留 | 正常完成（`《书b1》正文已修复`）；副本换为新内容；**成功后不留 backup/staging** |

用例 4 兼作**回归护栏**：若有人把守卫写成「总是停止」，它会红；
若成功路径忘了清 `backup`，下一次修复会被守卫挡住，它也会红。

### 3.2 红相（受控临时回退本人文件 → 跑 → 立即还原）

回退内容：把守卫换回 `backup.delete()`。

| 尝试 | 结果 | 说明 |
|---|---|---|
| 第 1 次 | **退出码 1**，但失败在 `compileDebugUnitTestKotlin` / `bundleDebugClassesToCompileJar` | **非有效红相**：当时**窗口 D 正在并发跑 Gradle**（`find build -mmin -1` 显示 2743→5152 个文件在动），属构建争用 |
| 第 2 次（等构建静默后） | **退出码 1**，`50 tests completed, 3 failed` | **有效红相** |

有效红相的失败明细（XML `<failure>`）：

| 失败用例 | 失败信息 | 说明 |
|---|---|---|
| `…backup exists and target is missing` | `AssertionError: backup 必须仍然存在` | **直接证明旧实现把残留删掉了** |
| `…both files when target and backup both exist` | `FileNotFoundException: …\a.txt.backup` | 旧实现删掉 backup 后，断言读取备份文件即报「找不到」 |
| `…same-name residue under a newly chosen file name` | `FileNotFoundException: …\b.txt.backup` | 同上 |

第 4 条（无残留正常成功）在红相中**仍通过** —— 符合预期（它锁的是「守卫不能过度拦截」）。

还原后 SHA-256 复核 = `d1b36c53…`（与修复版一致）。

### 3.3 绿相

```
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --no-parallel
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 1m 4s`）
- XML 实测：`ShelfImporterTest` **tests=50 skipped=0 failures=0 errors=0**
- 新增 4 条用例在 XML 中均存在且通过
- 命令含 `compileDebugKotlin` 与 `compileDebugUnitTestKotlin`，编译成功
  （仅有 `ReaderDocumentLoaderProfileTest.kt` 的既有 opt-in 警告，与本轮无关）

**与窗口 D 错开**：运行前检查 `tasklist` 与 `find build -mmin` 确认无活跃构建；
遭遇一次争用后等待至静默（14:55:23 显示 0 个新文件）才重跑。

---

## 4. 本轮是否失去分支覆盖：**没有**（更正说明）

我最初以为守卫会让「备份复制中途失败」这条分支失去覆盖，并据此写了一版「已失去覆盖」的说法。
**核对后确认该说法有误，已更正**：那条用例属于**同目录的 EPUB 侧**
（`EpubRepositoryReplaceTest.kt`，另一窗口所有），**不在 `ShelfImporterTest` 内**。

**TXT 侧的事实**（本人逐条核对 `ShelfImporterTest.kt` 中全部 8 条 `txt repair` 用例）：

| 检查 | 结果 |
|---|---|
| 是否有用例在调用前**预置 `.backup` 文件** | **没有**（只有新加的 3 条守卫用例写 `.backup`，且它们断言的是「保留」） |
| 其余 5 条如何产生 backup | 全部由**被测代码自身**在 S3 阶段建立（`internalFile.renameTo(backup)`） |
| 守卫对它们的影响 | 入口处 `backup` 不存在 ⇒ 守卫不触发 ⇒ **路径不变** |

⇒ **本轮守卫未使任何既有 TXT 用例失效，未失去任何分支覆盖**；
`ShelfImporterTest` 的 4 条既有 `txt repair` 用例（含阶段化回滚）**全部原样通过**，
新加 4 条覆盖守卫本身。46 → 50 的增量**纯属新增**。

> 至于「`renameTo` 失败 → `copyTo` 失败」这条分支在 **TXT 侧**是否可达：
> 该分支由被测代码内部的 `if (!internalFile.renameTo(backup))` 进入，
> **不依赖预置 backup**，故守卫**不影响**它。它在 TXT 侧本就**没有**专门用例
> （既有 5 条都不注入该失败），属**既存缺口**，非本轮引入。

---

## 5. 仍无法自动恢复的状态（本守卫不解决）

守卫只保证「不销毁」，**不保证「能恢复」**。以下状态在守卫命中后仍需人工介入：

| 状态 | 为何本轮不能自动处理 |
|---|---|
| 残留 backup 是**上次崩溃后唯一完好的正文**（K2） | 需与 DB 的 `content_hash` 做正向匹配才能授权恢复；本轮不实现判定状态机 |
| 残留 backup 是**已提交后的过期副本**（K4） | 同上：不能仅凭 `target` 是否存在或长度猜（设计稿 §E1.1 反例） |
| `>32 MiB` 的大文件 | `books.content_hash` 为 `null` 是**既定策略值**，`matchesDB` 不可判定 ⇒ 永远无法自动恢复 |
| `H(B)` 计算失败 / backup 损坏 | 缺证据，只能保留 |
| 用户**更换文件名**后的残留 | 该路径与 DB 无引用关系，恢复即错配 ⇒ 只能保留 |
| `staging` 残留 | 本守卫**不检测** `staging`（见 §2.2 第 4 点）；它由 `finally` 清理，不承载用户数据 |

**恢复入口本身也不存在**：当前没有任何启动期扫描会去处理这些残留（设计稿 SR-1）。
⇒ 用户看到的是「修复失败 + 请确认内容」，需要自己判断；**本轮不改变这一点**。

---

## 6. 未覆盖 / 未验证

1. **真实 Room 未覆盖**：`bookDao` 为 MockK 替身；「零写库」是**调用计数断言**，
   不等于真实 SQLite 事务语义。
2. **断电 / SIGKILL 未覆盖**：本守卫只处理「残留已存在于磁盘」这一**既成事实**，
   不改善崩溃瞬间的原子性。
3. **Android 文件系统语义未验证**：测试跑在 JVM `TemporaryFolder`。
4. **守卫的用户可见呈现未验证**：错误消息经 `repairFileTyped` → `RepairOutcome(false, …)`
   → 调用方，具体 UI 文案**未在真机确认**。
5. **未跑全量门禁**（按要求）：未跑完整单测、Lint、androidTest、APK 构建。
6. **未做逐行变异测试**：红相是整块回退，未逐一验证守卫内部各分支。
7. **并发修复未加锁**：同一本书被两个入口同时修复时无互斥（设计稿 §7-6 已登记）。

---

## 7. 边界声明

- **未运行** ADB / 真机 / 模拟器；未接触用户书库。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939…`。
- **未修改** `EpubRepository.kt`、`PendingTurnQueue*.kt`、总账或其他窗口文件；
  本轮 `find -mmin -40` 命中的 `EpubRepository.kt`（14:47）与
  `2026-09-24-epub-backup-residue-guard.md`（14:55）**均属其他窗口**，非本人写入。
  （§4 的更正即源于此：我一度把 EPUB 侧的用例误当作 TXT 侧。）
- 报告内**不含**真实书名、私人目录名或私有路径（夹具用 `a.txt` / `b.txt` / `书b1` 等中性名）。
- 唯一越界相关动作：为验证红相，**临时**回退并**立即还原**了本人所有的 `ShelfImporter.kt`
  （还原后 SHA-256 已复核一致），未触碰他人文件。

完成后停止。
