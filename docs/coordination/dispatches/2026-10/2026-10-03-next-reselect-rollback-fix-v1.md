# CRA-RESELECT-ROLLBACK-FIX-v1：重新定位时暂存复制失败误删旧正文的修复

## 一、任务编号/版本

- **任务编号/版本**：`CRA-RESELECT-ROLLBACK-FIX-v1`
- **性质**：先复现、再最小修复。**零设备、零 ADB、零全量门禁。**
- **缺陷依据**：[三项交付核查](../../reviews/2026-10/2026-10-03-next-three-deliveries-codex-review.md) §Spec 新发现 P1（静态可达，尚未运行复现）
- **状态**：**已交付待 Codex 核查**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 / HEAD | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（实读一致，收工未变） |
| 只读输入 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（882 文件） |
| 输入清单 | `E:\…\next-round-integration-ready-v1\final-manifest.json`，SHA-256 `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`（与复核一致） |
| 唯一工作目录 | `E:\creation-reading-assistant\next-round-reselect-rollback-fix-v1` |
| 重复执行检查 | 目标报告与本任务目录开工时均不存在 → **首次执行** |
| 修复前两文件 SHA | `ShelfImporter.kt` `d6bd2396…`、`EpubRepository.kt` `60e138dc…`（与只读输入一致） |

**采用 Codex 补正**：原 relink 预查中「取消异常可能留下文件已换、DB 未写」的结论**已按复核更正**——两条路径的内层 `catch (Throwable)` 先做文件回滚再重抛，`CancellationException` 亦被该分支覆盖；强杀另计未验证。本轮以此为准，并用一条取消用例把该语义锁住（§五）。

## 三、所有权与改动

**唯一仓库写入**：本文件。**唯一工作目录**：`E:\creation-reading-assistant\next-round-reselect-rollback-fix-v1`。

### 3.1 改动范围（与只读输入逐文件比对，`scope-check.json`）

| 项 | 值 |
|---|---|
| 输入 / 候选文件数 | 882 / **883** |
| 内容不同 | **3** |
| 新增 | **1** |
| 相同 | **879**；仅输入有 **0** |
| 是否越界 | **否**（`changed_is_allowed = true`、`new_is_allowed = true`） |

| 文件 | 改前 SHA-256 | 改后 SHA-256 |
|---|---|---|
| `app/src/main/java/…/feature/library/ShelfImporter.kt` | `d6bd239662b21d0d805897f33a22d0c9970e318627d56051467b724981d66b90` | `b0afec7619bb648ecfa06474c12a4888c6d7c9480c1d6e2359c5417f3dba3c80` |
| `app/src/main/java/…/feature/reader/EpubRepository.kt` | `60e138dcfd93341d05fcfaf893b55c099594f5339b43c8765687745bdb987519` | `d239050bbd41f6ff54d71e6d02461b880412a71f0a6ed7559ccd7005f1a4a984` |
| `app/src/test/java/…/ui/viewmodel/ShelfImporterTest.kt` | `afc60d289368ea04da4e83c949ebfb950bcc04fc6c247285fa409de2083d46e9` | `d1291888bbfb4a2f21e6d034c9827f5b165de6bfe10d35b63791c97f79811508` |
| `app/src/test/java/…/feature/reader/EpubRepositoryReplaceRollbackTest.kt` | （新增） | `ae8ccbd0349650568186c4777c11cd7d7e31a87b3dd0bf3cd183d6773bda0bae` |

### 3.2 精确 hunk 清单

**`ShelfImporter.kt`（6 hunks @U0）**

1. `reselectPlainText` 内新增 `var swapStage = SWAP_UNTOUCHED` 与阶段推进（原 `if (internalFile.exists() && !internalFile.renameTo(backup))` 拆成「先判存在 → 再备份 → 置 `SWAP_BACKED_UP`」，并在写目标前置 `SWAP_TARGET_WRITTEN`）
2. `catch` 由「无条件 `internalFile.delete()` + 有 backup 就复位」改为 `when (swapStage)` 三分支
3. 新增私有助手 `restoreBackupOver(target, backup)`
4. `private companion object` 新增 `SWAP_UNTOUCHED`/`SWAP_BACKED_UP`/`SWAP_TARGET_WRITTEN`

**`EpubRepository.kt`（6 hunks @U0）**

1. 文件级私有常量三枚（放在 import 之后、`@Singleton` 之前，**不打断注解与类的绑定**）
2. `replaceStoredEpub` 同样的阶段推进（`target.exists()` 判定拆开）
3. `catch` 改为 `when (swapStage)` 三分支（`memoryCache.remove` 保持先执行）
4. 新增私有助手 `restoreBackupOver(target, backup)`

**未改动**：`books`/`book_content`/`book_file` 的写入字段与顺序、`existing.copy(...)` 的保留语义、`bookId`、`memoryCache` 处理、`takePersistableUriPermission`、`finally { staging.delete() }`。

### 3.3 最小修复的语义

```
SWAP_UNTOUCHED      —— 旧目标尚未被触碰（暂存复制阶段失败）
   catch: 只清掉可能半写的 backup，**不动旧目标**
SWAP_BACKED_UP      —— 旧目标已安全移入 backup、目标位空缺
   catch: 复位旧字节
SWAP_TARGET_WRITTEN —— 新目标已（部分）安装（含 rename 失败后的覆盖兜底）
   catch: 先删新字节，再复位旧字节
```

## 四、环境

Windows 11 / Git Bash；Gradle wrapper 8.9，`--offline --console=plain --max-workers=1`，`--rerun`（确保测试任务实际执行，非 `FROM-CACHE`）。自有 `GRADLE_USER_HOME`（`_cache/gradle-home`，由既有 E 盘缓存整体复制）。**零 ADB、零 MuMu、零安装、零正式书库访问。**

## 五、执行与结果

### 5.1 红测（只读输入的原代码 + 最终测试集）

```
./gradlew.bat :app:testDebugUnitTest --rerun --offline --console=plain --max-workers=1 \
  --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest \
  --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceRollbackTest
```

**退出码 1；33 tests / 4 failures / 0 errors / 0 skipped**，失败项恰好是四条缺陷复现：

| 用例 | 失败断言 | 证明 |
|---|---|---|
| `reselect txt keeps old content when provider cannot open the selected file` | `旧正文文件必须仍然存在` | 旧 TXT 被删 |
| `reselect txt keeps old content when staging copy fails midway` | `FileNotFoundException: …\books\b1\b.txt` | 旧 TXT 被删（连读取都失败） |
| `reselect txt keeps old content when the backup cannot be established` | `备份建立失败时旧正文必须原样保留` | 备份都没建成，旧 TXT 仍被删 |
| `replaceStoredEpub keeps old copy when staging cannot be written before backup` | `旧 EPUB 副本必须仍然存在` | 旧 EPUB 副本被删 |

**其余 29 条在基线上通过**（含 EPUB 成功路径、EPUB DB 失败回滚、TXT DB 失败回滚、TXT 取消回滚）。

### 5.2 绿测（修复后 + 同一测试集）

**退出码 0；33 tests / 0 failures / 0 errors / 0 skipped。**

### 5.3 失败注入方法（不新增生产接缝、不使用反射）

| 用例 | 注入 |
|---|---|
| TXT provider 打不开 | `every { resolver.openInputStream(any()) } throws IOException` |
| TXT 复制中途失败 | 自定义 `InputStream`：首次 `read` 成功、再次抛 `IOException`（staging 半写） |
| TXT 备份建立失败 | 把 `<name>.backup` 预置为**非空目录**：`backup.delete()` 删不掉 → `renameTo` 失败 → `copyTo` 抛错 |
| EPUB 暂存无法写入 | 把 `books/epub/<id>.repairing` 预置为**非空目录**：`staging.outputStream()` 抛 `FileNotFoundException`；解析侧 `mockkObject(EpubParser)` 桩掉，使失败点精确落在暂存复制 |
| DB 写入失败 | `coEvery { bookDao.runInTransaction(any()) } throws`（TXT）／`coEvery { bookDao.update(any()) } throws`（EPUB） |
| 取消 | `coEvery { bookDao.runInTransaction(any()) } throws CancellationException` |

### 5.4 按风险补的必要用例（要求 5）

| 风险 | 是否覆盖 | 说明 |
|---|---|---|
| 尚未触碰旧目标时失败 | ✅ | 2 条 TXT（provider/中途）+ 1 条 TXT（备份建立失败）+ 1 条 EPUB |
| 旧目标已备份后失败 | ✅ | TXT/EPUB 的 DB 写入失败用例（`SWAP_BACKED_UP`→`SWAP_TARGET_WRITTEN` 之间由安装步骤覆盖） |
| 替换后失败能恢复旧字节 | ✅ | 2 条 DB 失败用例逐字节断言 |
| 取消异常 | ✅ | 1 条：断言原样上抛 `CancellationException` **且**文件回滚到旧字节（锁住 Codex 补正的语义） |
| 正常成功路径 | ✅ | TXT/EPUB 各 1 条：断言新字节已安装 + 无 `.backup`/`.repairing` 残留 |
| 目标安装（覆盖兜底）中途失败 | ❌ **未注入** | 该分支与 `SWAP_TARGET_WRITTEN` 走同一条回滚；无法在不新增接缝的前提下稳定注入，见 §七 |

### 5.5 断言强度

全部用例断言**原文件实际字节**（`assertArrayEquals`）与 **DB 交互**，不只断言异常消息；成功路径额外断言无残留文件。用例不触碰 `reading_progress`/`notes`/`highlights`/`tags`——本次改动也**未**触碰它们（`existing.copy(...)` 语义原样保留）。

## 六、原始证据

工件根：`E:\creation-reading-assistant\next-round-reselect-rollback-fix-v1\`

| 路径 | 说明 | SHA-256 |
|---|---|---|
| `logs/red-final-tests.log` | 红测完整 Gradle 控制台原日志（EXIT 1） | `22e3b68558834d0e07949915201358ec31714c492bd6828ec372b90ada958f43` |
| `logs/green-final.log` | 绿测完整 Gradle 控制台原日志（EXIT 0） | `cc394c164788037888766d178823e098da8a4ec5ae31e043a56ffa279eeaed5a` |
| `results/TEST-red-ShelfImporterTest.xml` | 红测原始 XML（30/3/0/0） | `1e449911666f1ddd53f04cf795533a3fe49296defd3dd87ee11834195490fc60` |
| `results/TEST-red-EpubRepositoryReplaceRollbackTest.xml` | 红测原始 XML（3/1/0/0） | `907bdcfbe23273a7150f5414d0d50e9a20e91e675a4ae7b0326f5a2f3e87730a` |
| `results/TEST-green-ShelfImporterTest.xml` | 绿测原始 XML（30/0/0/0） | `a613d6438e062c63c642f329fee83cd5487fc7a8ee627cb3c239427638d5a04d` |
| `results/TEST-green-EpubRepositoryReplaceRollbackTest.xml` | 绿测原始 XML（3/0/0/0） | `67337a5681068c672a0d1fa01848553088dbe3006918cd621f4eb7acb10c6981` |
| `patch/reselect-rollback-fix.patch` | 4 文件补丁（a/b 前缀，`-p1`） | `233381cc0b22f6dfe5128f77949726bed08f275883e5ad6dc8e3025812b6c42e` |
| `scope-check.json` | 改动范围核对结果 | `98436e1bf8607e9a0547eb1101aa4f1a6cc77f65eeda9c136a7df7bc5f28a1d5` |
| `verify_scope.py`、`execution.json` | 核对脚本与执行记录 | — |

**补丁验证**：在只读输入副本上 `git -c core.autocrlf=false apply --check` 通过、`apply` 后 4 个文件与候选**逐字节相同**（`cmp` 全 OK）。

**任务实际执行**：红/绿两份日志的 `> Task :app:testDebugUnitTest` **均无** `FROM-CACHE`/`UP-TO-DATE` 后缀。

## 七、未覆盖项

| # | 项 | 说明 |
|---|---|---|
| 1 | **目标安装覆盖兜底中途失败** | `staging.copyTo(target, overwrite = true)` 半写后抛错的分支未单独注入；它与 `SWAP_TARGET_WRITTEN` 共用回滚路径，且在不新增生产接缝的前提下无法稳定制造 |
| 2 | 真实 SAF provider / 真实磁盘满 / 真实权限故障 | 全部为 JVM 注入，非真实设备故障 |
| 3 | 真实进程终止（强杀） | 未验；Codex 已明确强杀另计 |
| 4 | 设备 / UI / SAF 用户可见路径 | 未运行（本任务不使用 ADB/MuMu） |
| 5 | 全量 153 或整库门禁 / Lint / Release | 未跑（按任务边界只跑定向） |
| 6 | `library_source_refs` 基线、`content_hash` 更新、内容身份判定 | **本轮刻意不做**（属 relink 预查的其他缺口，不夹带） |

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；未 stage/commit/push/reset/checkout；未搬目录。**唯一仓库写入 = 本文件**。
- **只读输入**：`next-round-integration-ready-v1` 未修改、未复制覆盖（882 文件原样）。
- **冻结输入 / D 产品源码 / 其他候选 / 其他窗口工件**：未触碰。
- **设备**：零 ADB、零 MuMu、零安装、零正式书库。
- **原损坏仓库 / HOLD / 其他应用私有库**：未操作、未读取。
- **构建与缓存**：全部在自有 E 目录；`_patchcheck` 临时目录已删除。
- **过程说明**：红绿切换通过替换两份生产文件完成，结束已还原为修复版并复核 SHA（`b0afec76…` / `d239050b…`）。

## 九、交付状态与下一步

**已交付待 Codex 核查。**

- **复现**：4 条红测在只读输入的原代码上失败，失败断言均为「旧正文/旧副本必须仍然存在」或读取旧文件抛 `FileNotFoundException`——**旧字节确实被删**，与 Codex 静态结论一致。
- **修复**：阶段敏感回滚（`UNTOUCHED` / `BACKED_UP` / `TARGET_WRITTEN`），未触碰旧目标时不再删它，替换后失败仍正确恢复旧字节；**未**夹带内容身份判定、确认页、journal 或书库重构。
- **结果**：红 33/4 → 绿 33/0；改动限于 3 个既有文件 + 1 个新测试文件（879 文件逐字节不变）。
- **建议下一步**：Codex 核红/绿 XML 与补丁；通过后并入受影响范围（`ShelfImporter.kt` / `EpubRepository.kt` + 两个测试文件），并注意**合流需新清单**——原 25 候选的设备验收不因本独立副本而改写。§七-1 的覆盖兜底分支如被判定必须覆盖，需先批准一个最小测试接缝（**SEAM REQUEST**），否则保持未验。
