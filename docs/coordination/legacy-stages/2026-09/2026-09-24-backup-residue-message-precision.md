# 残留备份守卫文案精度修正（target 存在 / 缺失两种现场）

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（未变）
- **日期**：2026-09-24

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 文案在 target 缺失时误示「旧正文存在」 | **已修**（改为条件式表述） |
| 定向 JVM 测试 | **60 条全绿**（`--rerun-tasks` 全新执行） |
| 红相 | **4 条失败**（两种现场 × 两侧），证明断言可证伪 |
| `:app:assembleDebug` | 退出码 **0**；APK SHA-256 见 §5 |
| `git diff --check` | 退出码 **0**（3 个已跟踪文件） |

---

## 1. 问题

上一轮把文案统一改为：

```
检测到未完成的替换残留（<name>.backup），本次替换已停止；
原有文件与备份均已保留、未被改动。重复重试不会自动恢复该备份
```

**「原有文件与备份均已保留」在 target 原本缺失时不真实。** 守卫触发的两种现场：

| 现场 | 磁盘状态 | 「原有文件…已保留」是否成立 |
|---|---|---|
| **A：target 存在** | 旧正文在 `target`，残留 backup 另有一份 | 成立 |
| **B：target 缺失** | 旧正文**已挪进 backup**，`target` 根本没有文件 | **不成立** —— 没有「原有文件」这样一份东西 |

现场 B 正是守卫最想保护的场景（backup 可能是唯一完好的正文）。
此时说「原有文件…已保留」会让用户以为**旧正文还在原处**，
从而误判「书没丢、不用管」，而实际上 `target` 已不存在。

---

## 2. 修正后的文案（两处一致）

```kotlin
if (backup.exists()) {
    // 提示语必须对**两种现场都成立**：target 存在（旧正文仍在）与 target 缺失
    // （旧正文已挪进 backup）。故不能断言「原有文件已保留」—— target 缺失时并没有
    // 这样一份文件，那样写会让用户误以为旧正文还在原处。
    // 也不能暗示存在自助恢复入口，或引导用户删书/清备份（那正是销毁唯一副本的做法）。
    throw IllegalStateException(
        "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
            "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
            "重复重试不会自动恢复该备份",
    )
}
```

逐条对应要求：

| 要求 | 落实 |
|---|---|
| 本次替换停止 | 「本次替换已停止」 |
| 若旧正文存在，本次操作未修改它 | 「旧正文**若仍存在**，则未被本次操作修改」（条件式，两种现场都真实） |
| 残留备份已保留 | 「残留备份已保留」 |
| 重复重试不会自动恢复 | 「重复重试不会自动恢复该备份」 |
| 不暗示自助恢复入口 | 全文无「确认后再重试」类表述（有断言锁定） |
| 不建议删书 / 清备份 | 全文无「删除」字样（有断言锁定） |
| 不实现自动恢复、不扩大重构 | 仅改字符串字面量 + 注释；**逻辑零改动** |

**保留的措辞**：`未完成的替换残留` 刻意保留 —— 两侧既有守卫用例都以
`contains("未完成的替换残留")` 作为「可识别为残留拒绝」的判据。

---

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `feature/library/ShelfImporter.kt` | 守卫字符串 + 注释（`:962-973`） |
| `feature/reader/EpubRepository.kt` | 守卫字符串 + 注释（`:94-104`） |
| `ui/viewmodel/ShelfImporterTest.kt` | 两条守卫用例的文案断言 |
| `feature/reader/EpubRepositoryReplaceTest.kt` | 两条守卫用例的文案断言 |

**未触碰**：`PageTurner.kt`、`PendingTurnQueue*.kt`、总账、其他窗口文件；未新增/删除文件。

### 测试如何分别锁定两种现场

| 现场 | 用例 | 该现场独有的断言 |
|---|---|---|
| **target 缺失** | `ShelfImporterTest > txt repair stops and keeps residue when backup exists and target is missing`<br>`EpubRepositoryReplaceTest > …refuses residue and never promotes backup when target is missing` | 断言 `contains("若仍存在")`（必须条件式）<br>**并且** `assertFalse(contains("原有文件与备份均已保留"))` |
| **target 存在** | `ShelfImporterTest > txt repair stops and keeps both files when target and backup both exist`<br>`EpubRepositoryReplaceTest > …refuses and preserves target when a backup directory residue exists` | 断言 `contains("若仍存在")`（同一文案要覆盖两种现场，故仍须条件式）<br>断言 `contains("残留备份已保留")` |

两条现场都断言「条件式表述」，**缺失**现场额外断言「不得出现无条件表述」——
这正是本轮的判别核心：旧文案在「缺失」现场必红（见 §4 红相），在「存在」现场却不会红。

---

## 4. 原始命令、退出码与结果

### 4.1 定向 JVM 测试（绿相，全新执行）

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --no-parallel --rerun-tasks
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 4m 56s`，`30 actionable tasks: 30 executed`）
- XML 实测（`timestamp=2026-09-24T08:39`）：

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ShelfImporterTest` | **50** | 0 | 0 | 0 |
| `EpubRepositoryReplaceTest` | **10** | 0 | 0 | 0 |
| **合计** | **60** | **0** | **0** | **0** |

> 首轮绿相用默认（非 `--rerun-tasks`）执行，末次任务 `FROM-CACHE`；
> 为免「缓存冒充新鲜执行」，本轮**追加了一次 `--rerun-tasks` 全新执行**并以此为准。

### 4.2 红相（受控临时回退本人文件 → 跑 → 立即还原）

回退内容：把两处文案换回**上一轮的无条件措辞**（「原有文件与备份均已保留」）。

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --no-parallel
```

- **退出码 `1`**，`60 tests completed, 4 failed`
- 失败用例（**两种现场 × 两侧，恰好 4 条**）：

| 失败用例 | 失败信息 |
|---|---|
| `txt repair stops and keeps residue when backup exists and target is missing` | `AssertionError: 提示必须说明残留备份已保留` |
| `txt repair stops and keeps both files when target and backup both exist` | `AssertionError: 提示必须说明残留备份已保留` |
| `replaceStoredEpub refuses and preserves target when a backup directory residue exists` | `AssertionError: 提示必须说明残留备份已保留` |
| `replaceStoredEpub refuses residue and never promotes backup when target is missing` | `AssertionError: 提示必须说明残留备份已保留` |

⇒ 文案若回退到无条件措辞，这 4 条立刻转红 ⇒ 新断言**可证伪**，非空转。
还原后两文件 SHA-256 复核见 §5。

### 4.3 构建 Debug APK

```bash
./gradlew.bat :app:assembleDebug --no-parallel
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 1m 10s`）

### 4.4 `git diff --check`（所属已跟踪文件）

```bash
git diff --check -- \
  android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt \
  android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt \
  android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt
```

- **退出码 `0`**，无输出 ⇒ 无 whitespace error。

**说明**：`EpubRepositoryReplaceTest.kt` 在工作区是**未跟踪**（`??`），
`git diff --check` **只检查已跟踪文件**，对它**不产生结论**。
故另做直接检查（逐行 `line.rstrip() != line`）：4 个文件**行尾空白均为 0 行**、结尾均有换行。

**与并行窗口错开**：每次运行前轮询 `find android/app/build -mmin -1`，
等待至 0 个新文件（16:25:46 / 16:32:21 两次 SETTLED）后才启动。

---

## 5. 产物与文件哈希

| 产物 / 文件 | SHA-256 |
|---|---|
| `android/app/build/outputs/apk/debug/app-debug.apk`（52,482,700 bytes） | `2999c079b880f939ab8a7e0d580673943a29942de508de1987d35bf7096a53ad` |
| `ShelfImporter.kt` | `5120e75c269e14f3b68a2065151908460656d8752e64831caf398dea8d70f8e5` |
| `EpubRepository.kt` | `c5495fc5021205ae87906e8adfb579794ec0dac24633d6ab624145355a0cb097` |

（红相回退后已按备份还原；上述哈希为还原后复核值。）

**APK 哈希的效力边界**：该哈希只证明「本机以当前工作区源码成功打包出该文件」，
**不证明**它在设备上安装、启动或行为正确 —— 本轮**未安装、未操作手机**。

---

## 6. 未覆盖项

1. **真实 Room 未覆盖**：两个测试文件的 DAO 全为 MockK 替身；「零写库」是**调用计数**断言，
   不等于真实 SQLite 事务语义。
2. **文案的最终 UI 呈现未验证**：`IllegalStateException.message` 在 TXT 侧经
   `repairFileTyped` 的 `catch` 转成 `RepairOutcome(false, "修复失败：<msg>")`，
   EPUB 侧直接上抛。两层封装后的**实际展示文案**（截断、拼接、是否被上层再加工）
   **未在真机确认**。
3. **APK 未安装、未启动**：`assembleDebug` 只证明可打包，**不构成任何设备验收**。
4. **设备文件系统语义未验证**：测试跑在 JVM `TemporaryFolder`；Android 上 `filesDir` 配额、
   Scoped Storage 对 `.backup` 的影响未测。
5. **断电 / SIGKILL 未覆盖**：守卫只处理「残留已存在」这一既成事实，不改善崩溃瞬间的原子性。
6. **EPUB 备份复制回退分支仍不可达**（已知缺口，本轮未触及）：
   `renameTo(backup)` 失败 → 退回 `copyTo(backup)` → `copyTo` 中途失败，
   以及紧随的 `require(backup.length() == target.length())` 长度校验分支。
   同目录内 `renameTo` 在 JVM `TemporaryFolder` 下几乎必然成功，现有用例无法稳定构造
   「rename 失败但 target 仍在」的前置状态；覆盖它需要专门的注入接缝（可注入的 `File`
   工厂或文件系统抽象）。该缺口已在 `EpubRepositoryReplaceTest` 类级文档与用例内断言中**显式登记**，
   本轮未做弱化或绕过。**TXT 侧**同形分支同样没有专门用例，属既存缺口。
7. **未跑全量门禁**：未跑完整单测、Lint、androidTest。本轮只跑了所属两组定向测试与
   `assembleDebug`。

---

## 7. 边界声明

- **未安装 APK、未操作手机 / 模拟器、未接触用户书库**。
- **未执行** `commit / push / stage / reset / checkout / clean`；HEAD 仍 `5aed5939…`。
- **未覆盖他人改动**：只改 §3 列出的 4 个文件。
- 报告内**不含**真实书名、私人目录名或私有路径（夹具用 `a.txt` / `b.txt` 等中性名）。
- 唯一越界相关动作：为验证红相，**临时**回退并**立即还原**了本人所有的两个生产文件
  （还原后 SHA-256 已复核一致），未触碰他人文件。

完成后停止。
