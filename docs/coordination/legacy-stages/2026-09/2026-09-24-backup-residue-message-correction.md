# 残留备份守卫提示语修正

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（未变）
- **日期**：2026-09-24

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| TXT / EPUB 守卫提示语误导 | **已修**（两处，措辞一致） |
| 定向测试 | **60 条全绿**；红相已验证（恰好 2 条命中） |
| 自动恢复 / 其他重构 | **未做**（按指示） |
| `git diff --check` | **退出码 0**（我的 3 个已跟踪文件） |

---

## 1. 原提示语的问题

两处守卫（`ShelfImporter.kt`、`EpubRepository.kt`）此前抛出同一句话：

```
检测到上次未完成的替换残留（<name>.backup），已停止本次替换以免覆盖可能完好的旧正文；
请先在书架中确认该书内容，再重试
```

**末句「请先在书架中确认该书内容，再重试」有三重误导**：

1. **暗示重试能解决** —— 但守卫的判据是「磁盘上是否已有残留」，
   而守卫本身**不会**清理该残留，所以**重复重试会再次命中同一守卫**，
   永远无法通过。用户会陷入「确认 → 重试 → 同样失败」的循环。
2. **暗示存在自助恢复入口** —— 应用当前**没有任何**恢复入口
   （既无启动期扫描，也无 UI 操作能把 `<name>.backup` 还原成正文）。
   「确认内容」这个动作即便做了，也不改变守卫行为。
3. **可能引导破坏性操作** —— 用户为了「让重试通过」，
   自然的下一步是去删书或清理那个 `.backup` 文件 ——
   而那正是**销毁唯一完好副本**的做法，与守卫的初衷相反。

---

## 2. 修改后的提示语

两处**完全一致**（含相同的前置注释）：

```kotlin
if (backup.exists()) {
    // 提示语必须如实：重复重试仍会命中本守卫，**不会**自动恢复；
    // 应用当前也没有任何自助恢复入口，故不得暗示「确认内容后再重试即可」，
    // 也不得引导用户删除书籍或清理备份（那正是销毁唯一完好副本的做法）。
    throw IllegalStateException(
        "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
            "原有文件与备份均已保留、未被改动。重复重试不会自动恢复该备份",
    )
}
```

逐条对应要求：

| 要求 | 落实 |
|---|---|
| 检测到上次替换留下的备份 | 「检测到未完成的替换残留（`<name>.backup`）」 |
| 本次替换已停止 | 「本次替换已停止」 |
| 原有文件 / 备份受到保护 | 「原有文件与备份均已保留、未被改动」 |
| 重复重试不会自动恢复 | 「重复重试不会自动恢复该备份」 |
| 不暗示存在自助恢复入口 | **已删除**「请先在书架中确认该书内容」 |
| 不建议删书 / 清理备份 | 全文**不含**「删除」「清理」等字样（有断言锁定） |
| 不实现自动恢复、不扩大重构 | 仅改字符串字面量 + 加 3 行说明性注释；**逻辑零改动** |

**保留的措辞**：`未完成的替换残留` 这一短语**刻意保留** ——
`EpubRepositoryReplaceTest` 的两条既有守卫用例都以
`message.contains("未完成的替换残留")` 作为「可识别为残留拒绝」的判据。
保留它使本修正**不破坏既有断言**，同时让上层仍能稳定识别该错误类别。

---

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `feature/library/ShelfImporter.kt` | 仅守卫抛出的字符串 + 3 行注释 |
| `feature/reader/EpubRepository.kt` | 仅守卫抛出的字符串 + 3 行注释 |
| `ui/viewmodel/ShelfImporterTest.kt` | 守卫用例新增 6 条提示语断言 |
| `feature/reader/EpubRepositoryReplaceTest.kt` | 守卫用例新增 5 条提示语断言 |

**未触碰**：`PageTurner.kt`、`PendingTurnQueue*.kt`、总账、其他窗口文件；
未新增/删除任何文件。

### 新增断言（锁定「不误导」这一性质）

`ShelfImporterTest` 守卫用例新增：

```kotlin
assertTrue("提示必须点出未完成的替换残留", msg.contains("未完成的替换残留"))
assertTrue("提示必须说明本次替换已停止", msg.contains("本次替换已停止"))
assertTrue("提示必须说明重试不会自动恢复", msg.contains("重复重试不会自动恢复"))
assertTrue("提示必须说明原有文件与备份已保留", msg.contains("均已保留"))
assertFalse("不得暗示「确认后再重试」这类自助恢复路径", msg.contains("再重试"))
assertFalse("不得引导用户删除书籍", msg.contains("删除"))
```

`EpubRepositoryReplaceTest` 守卫用例新增同类 5 条（含 `assertFalse` 两条）。
⇒ 提示语若回退到旧措辞，这些断言会立刻转红（见 §4 红相）。

---

## 4. 验证

### 4.1 绿相

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --no-parallel
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 2m 3s`）
- XML 实测：

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ShelfImporterTest` | **50** | 0 | 0 | 0 |
| `EpubRepositoryReplaceTest` | **10** | 0 | 0 | 0 |
| **合计** | **60** | **0** | **0** | **0** |

命令含 `compileDebugKotlin` 与 `compileDebugUnitTestKotlin`，编译成功
（仅 `ReaderDocumentLoaderProfileTest.kt` 的既有 opt-in 警告，与本轮无关）。

**与并行窗口错开**：运行前轮询 `find android/app/build -mmin -1` 确认无活跃构建
（15:18:52 显示 0）后才启动。

### 4.2 红相（受控临时回退本人文件 → 跑 → 立即还原）

回退内容：把两处提示语换回**旧的误导措辞**。

- **退出码 `1`**，`60 tests completed, 2 failed`
- 失败用例**恰好**是两条带新断言的守卫用例：

| 失败用例 | 说明 |
|---|---|
| `ShelfImporterTest > txt repair stops and keeps residue when backup exists and target is missing` | 旧措辞不含「本次替换已停止」/「重复重试不会自动恢复」，且**含**「再重试」⇒ 新断言转红 |
| `EpubRepositoryReplaceTest > replaceStoredEpub refuses and preserves target when a backup directory residue exists` | 同上 |

⇒ 新断言是**可证伪**的：它们真正锁住了「提示语必须如实」这一性质，而非空转。
还原后两文件 SHA-256 复核一致（见 §5）。

### 4.3 `git diff --check`

```bash
git diff --check -- \
  android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt \
  android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt \
  android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt
```

- **退出码 `0`**，无输出 ⇒ **无 whitespace error**。

**说明**：`EpubRepositoryReplaceTest.kt` 在工作区是**未跟踪**（`??`），
`git diff --check` **只检查已跟踪文件**，对它**不产生结论**。
故另做直接检查（逐行 `line.rstrip() != line`）：

| 文件 | 行尾空白行 | 结尾换行 |
|---|---|---|
| `ShelfImporter.kt` | 0 | 有 |
| `EpubRepository.kt` | 0 | 有 |
| `ShelfImporterTest.kt` | 0 | 有 |
| `EpubRepositoryReplaceTest.kt` | 0 | 有 |

全仓 `git diff --check` 的输出中只有**二进制文件匹配提示**与 CRLF 归一化警告
（源自其他窗口既有改动），**无 `whitespace error`**。

---

## 5. 最终文件哈希

| 文件 | SHA-256 |
|---|---|
| `ShelfImporter.kt` | `de1ea2bc2523ce56b41989a47d2c3bd2c6709cdddb694d0a3b18864fc85b027e` |
| `EpubRepository.kt` | `dd52027f877aa5666f62ba99897dfdff291401f3459237a327c0ee063ebf3b87` |

（红相回退后已按备份还原，上述哈希为还原后复核值。）

---

## 6. 仍未覆盖的路径

### 6.1 真实 Room / 设备路径

1. **真实 Room 未覆盖**：两个测试文件的 `bookDao` / `bookContentDao` / `bookFileDao` /
   `readingProgressDao` 全为 MockK 替身。本轮断言的是「零调用」，
   **不等于**真实 SQLite 事务、外键、触发器下的行为。
2. **提示语的最终呈现未验证**：`IllegalStateException.message` 经
   `ShelfImporter.repairFileTyped` 的 `catch (e: Throwable)` 转成
   `RepairOutcome(false, "修复失败：<msg>")`；EPUB 侧则直接向上抛。
   两层封装后的**实际 UI 文案**（是否被截断、是否与其它前缀拼接）**未在真机确认**。
3. **设备文件系统语义未验证**：测试跑在 JVM `TemporaryFolder`（真实 FS 但非 Android 沙箱）；
   Android 上 `filesDir` 配额、Scoped Storage 对 `.backup` 的影响未测。
4. **断电 / SIGKILL 未覆盖**：守卫只处理「残留已存在于磁盘」这一既成事实，
   不改善崩溃瞬间的原子性。

### 6.2 EPUB 备份复制回退分支（已知缺口，本轮未触及）

`EpubRepository.kt` 中「`renameTo(backup)` 失败 → 退回 `copyTo(backup)` →
`copyTo` 中途失败」这条分支，以及紧随其后的
`require(backup.length() == target.length())` 长度校验分支，**在当前 JVM 单测中不可达**：

- 该分支的进入条件是 `target.exists() && !target.renameTo(backup)`；
- 同目录内 `renameTo` 在 JVM `TemporaryFolder` 下**几乎必然成功**，
  现有用例无法稳定构造「rename 失败但 target 仍在」的前置状态；
- 覆盖它需要**专门的注入接缝**（可注入的 `File` 工厂或文件系统抽象），
  该缺口已在 `EpubRepositoryReplaceTest` 的类级文档与相关用例中**显式登记**
  （用例内以断言声明「长度校验分支未被本用例执行」），**本轮未做任何弱化或绕过**。

**TXT 侧**同形分支（`ShelfImporter.kt` 的 `if (!internalFile.renameTo(backup))`）
同样**没有**专门用例 —— 属既存缺口，非本轮引入。

---

## 7. 边界声明

- **未安装 APK、未操作真机 / 模拟器、未接触用户书库**。
- **未执行** `commit / push / stage / reset / checkout / clean`；HEAD 仍 `5aed5939…`。
- **未撤销或覆盖**其他窗口改动；本轮只改 §3 列出的 4 个文件。
- 报告内**不含**真实书名、私人目录名或私有路径（夹具用 `a.txt` / `b.txt` 等中性名）。
- 唯一越界相关动作：为验证红相，**临时**回退并**立即还原**了本人所有的两个生产文件
  （还原后 SHA-256 已复核一致），未触碰他人文件。

完成后停止。
