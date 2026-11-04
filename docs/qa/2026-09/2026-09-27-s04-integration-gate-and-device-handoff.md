# S04 集成门禁与设备验收准备（2026-09-27）

- **角色**：S04 单轮集成门禁 + 设备验收单准备（集成者口径）
- **工作区**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（已核实；当前 shell 位于 `android/` 子目录，仓库根一致）
- **输入**：`docs/handoff/current.md`、`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md`（S04 父项 PARTIAL，以页尾 2026-09-24 对账为准）、`docs/coordination/legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md`（09-27 回滚链终审）
- **纪律**：本轮未修改任何产品源码/测试（门禁一次通过，无修复迭代）；未操作设备与正式书库；无 Git 写操作；构建产物全部经既有 E 盘 junction 重定向（`redirect-build-via-junction.gradle`），本报告为唯一写入仓库的文件。

> **修订 v2（2026-09-27 晚，隔离包关卡）**：初版 §4 使用普通 Debug APK（applicationId 与正式应用同为
> `com.creationreadingassistant`），不能安装到用户现有正式手机。本次修订以当前源码构建了新鲜 **qahost 隔离包**
> （applicationId 实测 `com.creationreadingassistant.qahost`，§2.3），§4 验收单全部改写为显式针对 qahost 包的
> 命令，并加入 WAL 一致性数据库快照方法。初版 Debug APK 门禁结论保留在 §2.1–2.2；普通 Debug APK 自此
> **不得用于真机验收安装**（仅作 JVM/Lint 构建载体的产物留存）。

---

## 1. S04 当前实现范围、测试类清单与开放验收项

### 1.1 S04 实现涉及的测试类（当前源码）

| 测试类 | 用例数 | 覆盖点 | 本轮门禁 |
|---|---|---|---|
| `feature.reader.EpubRepositoryReplaceTest` | 21 | EPUB 替换全链：content_hash 更新/清除、损坏拒绝、**回滚链**（备份建立/回退复制/恢复失败/取消/残留守卫两种形态/同 URI 刷新/新鲜来源）、调用方异常面 + AppLog 记录 | ✅ 21/21 |
| `ui.viewmodel.ShelfImporterTest` | 50 | 来源刷新入口（reselect EPUB/TXT/MD）、导入批处理、格式拒收、反馈摘要 | ✅ 50/50 |
| `feature.library.ShelfSourceUpdateCheckerTest` | 6 | 来源更新检测（S04 前置判定） | ✅ 6/6 |
| `data.repository.SearchIndexBookRefreshCoordinatorTest` | 6 | S04-2 异步逐书索引重建协调器 | ✅ 6/6 |
| `ui.viewmodel.ShelfViewModelTest` | 21 | 书架状态（S04/S05 共享域，本轮未纳入定向门禁，全量跑时覆盖） | 相邻证据 |
| `data.repository.BookRepositoryTest` | 23 | 书仓层（消费 SearchIndexBookRefreshCoordinator，S04 相邻） | 相邻证据 |

### 1.2 仍开放的验收项（以账本页尾对账 + 09-27 文档为准）

1. **S04 父项 = PARTIAL**（账本 L292/L320 口径）：真机端到端来源刷新/替换验证未完成——TXT 小文件真机自然路径曾 PASS（账本 L277），**EPUB 真机、大文件（>32 MiB content_hash 置 null）、系统 Provider 故障路径均未由真机覆盖**。本报告 §4 验收单即为该缺口的执行方案。
2. **回滚链（09-27 终审交付）**：JVM 已闭环（83/83 含回滚全部分支）；**真机 `renameTo` 返回 false 的触发条件与频率、并发替换、低存储/跨卷场景未验证**（safety-fix 报告 §5、终审报告 §5 均如实登记）。
3. **S04-5 备份覆盖扩展**（账本 L181 计划项）：工作树已实现——`JsonBridge.kt` 已含来源引用导出 DTO（`LibrarySourceRefEntity`/`toEntity()`，本次 grep 证实）；其**备份恢复端到端行为属 S14 范围**，本轮未验证、不重复登记结论。
4. 账本历史 OPEN 项（`incompleteBookIds()` 接线属产品决策、A5/N1 笔记入口等）与本轮门禁无关，维持原状。

---

## 2. 集成门禁执行记录

### 2.1 门禁 A：S04 定向 JVM（4 类）

```bash
cd D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android
export GRADLE_USER_HOME=D:/develop/env/gradle
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
  --tests "com.creationreadingassistant.feature.library.ShelfSourceUpdateCheckerTest" \
  --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" \
  --init-script E:/creation-reading-assistant/codex-s04-build-20260925/redirect-build-via-junction.gradle \
  --offline > E:/creation-reading-assistant/codex-s04-build-20260925/gate-a-s04-jvm.log 2>&1
# GATE_A_EXIT=0，BUILD SUCCESSFUL in 45s（编译任务复用 09-27 终审轮的同源码增量，测试为本次真跑）
```

| 测试类 | 结果 | XML（junction 路径，`D:\develop\Code\Codex\cra-s04-buildlink\app\test-results\testDebugUnitTest\`，mtime 2026-09-27 15:16:43） | SHA-256 |
|---|---|---|---|
| EpubRepositoryReplaceTest | 21/21/0/0 | `TEST-…feature.reader.EpubRepositoryReplaceTest.xml` | `75fa140cd505ef6cb62b9606f8f54204a1a113439bb873f27f876c5895c9df29` |
| ShelfImporterTest | 50/50/0/0 | `TEST-…ui.viewmodel.ShelfImporterTest.xml` | `96eda97f3ae7b4696e4d6b3a0d0e33d124edf65d38a3ae513d9fd02c92aaed97` |
| ShelfSourceUpdateCheckerTest | 6/6/0/0 | `TEST-…feature.library.ShelfSourceUpdateCheckerTest.xml` | `2e572f4dfdc5d7aa17e9805fd06dd1b7c67b7fb47c273dd8635e9348b43ec0fc` |
| SearchIndexBookRefreshCoordinatorTest | 6/6/0/0 | `TEST-…data.repository.SearchIndexBookRefreshCoordinatorTest.xml` | `cd040d015da410d702485b94bf422f07ba4d3be231ef962df2c846428716fc40` |

合计 **83 项，0 失败 / 0 错误 / 0 跳过**。日志：`E:\creation-reading-assistant\codex-s04-build-20260925\gate-a-s04-jvm.log`。

### 2.2 门禁 B：Lint + Debug APK + androidTest 编译

```bash
./gradlew :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin \
  --init-script E:/creation-reading-assistant/codex-s04-build-20260925/redirect-build-via-junction.gradle \
  --offline > E:/creation-reading-assistant/codex-s04-build-20260925/gate-b-lint-apk.log 2>&1
# GATE_B_EXIT=0，BUILD SUCCESSFUL in 4m 50s
# （备注：首次调用因 shell cwd 漂移到仓库根而 EXIT=127，未执行任何构建，原地修正后复跑；如实记录）
```

- **Lint**：`0 errors / 9 warnings`（3 ObsoleteLintCustomCheck、2 DefaultLocale、3 GradleDependency、1 UnusedResources——全部既有类别，**无一位于 S04 文件**，不阻断，不为此开新一轮）。
- **Debug APK**（`packageDebug`/`assembleDebug` 本次执行）：
  - 路径：`D:\develop\Code\Codex\cra-s04-buildlink\app\outputs\apk\debug\app-debug.apk`
  - 大小：51,699,412 字节
  - **SHA-256：`762a6f681b4d82a7167c77da7dcb8f13abfeb9b716388fae2af2ee15d3a082d5`**
  - 构建留存副本：`E:\creation-reading-assistant\codex-s04-gate-20260927\app-debug-20260927-gate.apk`（仅作本轮 Debug 通道构建证据；**不得用于真机验收安装**，真机一律用 §2.3 的 qahost 隔离包）
- **androidTest 编译**：`compileDebugAndroidTestKotlin` 通过（隔离 runner 所需 test APK 可打包，本轮未打包设备件）。
- 注：qahost 变体的隔离包构建见 §2.3（初版此处的「qahost 非本轮产物」提示已被 §2.3 取代）。

### 2.3 门禁 C（修订 v2）：qahost 隔离包构建与隔离性核实

机制背景（`android/app/build.gradle.kts`）：qahost 变体 `initWith(debug)` + `applicationIdSuffix = ".qahost"`（L105-114）；AGP 每次调用只为一个 build type 生成 androidTest 变体，由 `testBuildType = gradleProperty("craAndroidTestBuildType") ?: "debug"` 切换（L14-16，设计注释明示 QA-host 用途）。**未修改任何构建配置。**

```bash
# C-1 隔离宿主 APK
./gradlew :app:assembleQahost --init-script E:/…/redirect-build-via-junction.gradle --offline
# EXIT=0，BUILD SUCCESSFUL in 3m 8s（41 tasks: 23 executed）
# C-2 隔离测试 APK（首次尝试 :app:compileQahostAndroidTestKotlin / 无属性 assembleQahostAndroidTest 均
#     EXIT=1「task not found」——testBuildType 默认 debug 下 qahost androidTest 变体不存在，属预期机制而非缺陷；
#     带属性重试通过）
./gradlew :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost \
  --init-script E:/…/redirect-build-via-junction.gradle --offline
# EXIT=0，BUILD SUCCESSFUL in 24s
```

| 项 | 值 |
|---|---|
| 宿主 APK | `D:\develop\Code\Codex\cra-s04-buildlink\app\outputs\apk\qahost\app-qahost.apk`（mtime 2026-09-27 15:44，本新鲜构建） |
| 宿主大小 | 51,699,420 字节 |
| **宿主 applicationId（aapt dump badging 实测）** | **`package: name='com.creationreadingassistant.qahost'`**（versionCode 3 / versionName 0.5.1） |
| 宿主 SHA-256 | `a4669f8532055656be6341444f29eaf3a972678533e5ceb351ff34e40021a638` |
| 测试 APK | `…\apk\androidTest\qahost\app-qahost-androidTest.apk`（SHA-256 `6d19c24449d75f893c20fef947f165fd005f08733187ad1f3e5c5662c1456ec7`） |
| 测试 APK 清单（aapt 实测） | package `com.creationreadingassistant.qahost.test`；instrumentation `com.creationreadingassistant.testing.CraIsolatedTestRunner`，**targetPackage `com.creationreadingassistant.qahost`** |
| E 盘留存 | `E:\creation-reading-assistant\codex-s04-gate-20260927\app-qahost-20260927-gate.apk` 与 `…\app-qahost-androidTest-20260927-gate.apk` |

日志：`gate-c-qahost.log`、`gate-c-qahost-androidtest3.log`（含两次任务名探索失败的 `gate-c-qahost-androidtest.log`/`…2.log`，如实保留）。同目录旧 qahost APK 为历史产物，已被本节取代，不得引用。

### 2.4 门禁结论

**全部通过，无失败项，未触发任何 S04 修复迭代**；产品源码本轮零改动。门禁 A/B（§2.1–2.2）与门禁 C（§2.3）全部 EXIT=0；隔离包身份与测试 APK 的 `targetPackage` 均经 aapt 实测核实。

---

## 3. 已有 JVM 证据（无需重测清单）

以下证据对应当前源码状态，验收者**无需重跑**，可直接引用：

1. **本轮门禁 A 的 83 项定向 JVM**（4 份 XML + SHA-256，§2.1）——覆盖 EPUB 替换回滚全部分支、来源刷新链路、来源更新检测、异步索引协调器；
2. **09-27 终审轮的 `EpubRepositoryReplaceTest` 21/21**（XML `3b0730ae…`）——与门禁 A 同源码状态，本轮同源码重跑已覆盖并以 §2.1 为准；
3. **本轮 androidTest 编译通过**（debug 通道）+ **qahost 隔离测试 APK 构建并通过清单核实**（`targetPackage=com.creationreadingassistant.qahost`、Runner=`CraIsolatedTestRunner`，§2.3）——隔离 instrumentation 通道的编译与打包前提成立；
4. 历史账本中的 JVM 数字（58/58、39/39、2238 项等）为**历史批次口径**，不得当作当前结论引用。

需要**真机补充**的证据：见 §4 验收单；其中并发、低存储/跨卷、`renameTo` false 触发频率**明确不在本验收单强制范围**（§5）。

---

## 4. 设备验收单（可直接交独立验收者；v2 起全部基于 qahost 隔离包）

> **隔离纪律（v2）**：验收全部在隔离包 **`com.creationreadingassistant.qahost`** 上进行。该包与正式应用
> `com.creationreadingassistant` 是两个独立应用：私有目录（`files/`、`databases/`、WorkManager 库）互不可见，
> APK 包名与私有目录隔离已由构建产物核实；对正式应用及其书库的实际影响仍须由设备前后对账验证，不能预先宣称零影响。因此：
> ① 严禁对 `com.creationreadingassistant`（正式包）执行 install / run-as / force-stop / pm clear / uninstall；
> ② 允许强停的**唯一包名是 `com.creationreadingassistant.qahost`**；
> ③ 本验收单的测试书与私有数据库应仅写入 qahost 私有目录；验收后是否卸载及何时清理，由验收者在保存证据并核对正式应用状态后决定。
> 其余纪律不变：仅用真机（先 `adb devices` 核对，所有命令 `adb -s <serial>`）；禁用模拟器；样本一律中性命名
> 「测试EPUB…」；**除 §4.3 明确允许的测试书自身残留清理外，不得读取/修改/删除设备上任何书籍数据**；
> `stay_on_while_plugged_in` 长测前记录原值、结束后恢复。

### 4.0 准备

| 步骤 | 操作 | 通过标准 |
|---|---|---|
| P1 | 安装隔离宿主 `E:\creation-reading-assistant\codex-s04-gate-20260927\app-qahost-20260927-gate.apk`（先本地核对 SHA-256 = `a4669f85…638`；`adb shell pm list packages` 应见 `com.creationreadingassistant.qahost` 而**正式包安装状态不受影响**） | 安装成功；优先 `scripts/install_with_confirm.ps1`（传 qahost APK 路径，自动 MIUI 确认），direct `adb install -r -t` 只能标注 FALLBACK，不得记为脚本 PASS |
| P2 | （可选，仅走 instrumentation 路径时）安装隔离测试 APK `…\app-qahost-androidTest-20260927-gate.apk`（SHA-256 `6d19c244…ec7`；清单实测 targetPackage = `com.creationreadingassistant.qahost`、Runner = `CraIsolatedTestRunner`）。本验收单主体为手工步骤，测试 APK 非必需 | 安装成功且正式包测试 APK（`com.creationreadingassistant.test`）**不得安装** |
| P3 | 准备两个中性样本：`测试EPUB回滚` V1 与 V2（**同名文件、内容不同**，各 ≤32 MiB，正文带可辨识标记串如 `V1MARKER`/`V2MARKER`）；（推荐，可选）`测试EPUB大文件` >32 MiB | 来源刷新场景用同一文件名覆盖内容，复现「同一来源更新」 |
| P4 | 在 **qahost** 内启动应用完成首次初始化（确认是沙箱空书架，而非正式应用的书库——若看到用户真实书籍说明装错包，立即停止并上报） | qahost 书架为空；正式应用未被打开、未被改动 |

### 4.0.1 对账命令约定（全部显式针对 qahost 包，后文反复使用）

- **文件清单**：`adb -s <serial> shell run-as com.creationreadingassistant.qahost ls -la files/books/epub/`
- **数据库一致性快照（WAL 完整法，v2 强制）**——qahost 使用 Room 默认 WAL 模式，**仅复制主 `.db` 文件不含已进 `-wal` 的变更，不构成完整快照**；正确方法：
  1. `adb -s <serial> shell am force-stop com.creationreadingassistant.qahost`（**唯一允许强停的包**；静止后不再有并发写入，主库与 WAL 之间状态一致）；
  2. 三件一起拉取（`-wal`/`-shm` 可能不存在，缺失时如实记录并跳过）：
     `adb -s <serial> exec-out run-as com.creationreadingassistant.qahost cat databases/creation_reading_assistant_native > snap.db`
     同法追加 `…_native-wal > snap.db-wal` 与 `…_native-shm > snap.db-shm`；
  3. 本地 `sqlite3 snap.db` 打开——SQLite 自动回放同目录 WAL，得到**含全部已提交事务**的一致快照；
  4. 查询口径：`books(id,title,size,content_hash,local_content_path,updated_at)` 与 `reading_progress(book_id,progress_percent,current_location_json)`；每次对账后记录 `snap*` 三文件的 SHA-256。
  - 禁止项：不得对正式包做任何上述操作；不得在 qahost 运行中只拉主 `.db` 冒充完整快照；不得用 `sqlite3` 在设备上在线改库。

### 4.1 场景 A：EPUB 来源刷新成功路径（必做）

1. 在 qahost 中导入 `测试EPUB回滚`（V1）→ 打开阅读至某一章节/百分比 → 退出；按 §4.0.1 留 DB 基线与文件清单。
2. 用 V2 内容**覆盖同名来源文件** → 书架对该书执行「重新选择来源 / 来源更新」。
3. **判定标准（全部满足为 PASS）**：
   - 操作完成无报错；打开书正文为 **V2 标记串**、不含 V1 标记；
   - DB：`books.content_hash` = 新内部副本的 MD5（≤32 MiB 必为 32 位小写十六进制，**不得残留旧哈希**）、`size` 更新、`local_content_path` 仍指向 `files/books/epub/<id>.epub`、`updated_at` 变化；
   - **阅读进度保留**：替换前记录的章节/百分比在重进后仍在（不得清零）；
   - 文件：`files/books/epub/` **无 `.backup` / `.repairing` / `.importing` 残留**；
   - 诊断页（我的 → 日志与诊断）：可打开、有条目、**无「EPUB 回滚恢复未完成」**（成功路径不应出现）。

### 4.2 场景 B：大文件 hash 清除（推荐，可选）

同场景 A 但用 `测试EPUB大文件`。**判定**：替换成功、正文为新内容、`books.content_hash` **为 NULL**（>32 MiB 策略性清除，不得保留旧哈希）；其余同 A。

### 4.3 场景 C：备份残留守卫拒绝（必做）

1. 前置：场景 A 完成后，从 DB 取该书 `id`，人为放置残留：
   `adb -s <serial> shell run-as com.creationreadingassistant.qahost sh -c 'echo x > files/books/epub/<id>.backup'`
2. 再次对该书执行「重新选择来源」。
3. **判定标准（全部满足为 PASS）**：
   - 操作**立即失败**，提示文案同时包含「检测到未完成的替换残留」「本次替换已停止」「重复重试不会自动恢复」（`EpubRepository.kt` L96-106 的守卫合同）；
   - **target 与 backup 均逐字节未变**（前后 `run-as ls -la` + 字节对比）；
   - DB `books` 行 `updated_at` 不变、无任何写入；
   - 诊断页无「EPUB 回滚恢复未完成」（守卫拒绝不触发回滚段）。
4. **收尾**：仅删除**本测试书自身**的 `<id>.backup`（`run-as rm`），记录删除动作；不得触碰任何其他文件/书籍。

### 4.4 场景 D：诊断页记录核对（必做，轻量）

打开「我的 → 日志与诊断」：条目可查看/导出，模块/消息为固定描述格式，**不含真实书名、文件名、路径、正文或异常消息**（本轮新增的回滚诊断记录即此契约）。「EPUB 回滚恢复未完成」仅在恢复失败时出现——设备级常规操作无法触发，**不作为本验收单的强制判定项**。

### 4.5 场景 E：替换中进程中断（观察性，不计 PASS/FAIL）

替换进行中强杀 qahost 进程（**唯一允许**：`adb -s <serial> shell am force-stop com.creationreadingassistant.qahost`），重进后只记录现场：书**必须仍可读**（旧正文或新正文完整），若出现 `.backup` 残留则按场景 C 判定守卫行为；出现任何「书不可读且无残留可恢复」即 FAIL 并升级。该场景形态取决于中断时机，**两种合法现场（已提交 / 残留拦截）都算未损坏**。

---

## 5. 明确未验证边界（本轮与验收单均不覆盖）

1. **真机**：§4 验收单尚未执行——本报告不声称任何真机 PASS；
2. **并发替换**：`ShelfImporter` 无互斥、入口守卫竞态窗口（异常面审查 §2 范围外观察）——未验证、未修复；
3. **低存储 / 跨卷**：`renameTo` false 的真实触发条件与频率、低存储下回滚行为——未验证（可作后续专项，不在本验收单强制范围）；
4. **S04-5 备份恢复端到端**：导出/恢复含 `library_source_refs` 的合并行为属 S14 范围，未验证；
5. **隔离包真机行为**：qahost 宿主/测试 APK 本轮已新鲜构建并核实隔离身份（§2.3），但**未在真机安装或执行过**（本轮不操作设备）；`run-as com.creationreadingassistant.qahost` 的可用性（debuggable 包标配）与 WAL 三件拉取法在目标 ROM 上的实际效果，由验收者按 §4.0.1 首次对账时当场验证；
6. 账本历史 OPEN 项（`incompleteBookIds()` 接线、A5/N1 入口决策等）维持原状，与本轮门禁无关。

---

## 6. 结论（通过 / 阻塞 / 未验证）

- **通过**：门禁 A——S04 四类定向 JVM **83/83**（XML 哈希在档）；门禁 B——`lintDebug` 0 errors / 9 warnings（全部既有、非 S04 文件）、`assembleDebug` 构建、`compileDebugAndroidTestKotlin` 通过；**门禁 C（v2）——qahost 隔离包构建**：宿主 APK `assembleQahost` EXIT=0、aapt 实测 applicationId = `com.creationreadingassistant.qahost`（SHA-256 `a4669f85…638`，51,699,420 字节），测试 APK `assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost` EXIT=0、清单实测 targetPackage 指向 qahost、Runner 为 `CraIsolatedTestRunner`（SHA-256 `6d19c244…ec7`）；两个 APK 已留存 E 盘验收目录，同目录历史 qahost APK 一律作废。
- **阻塞**：无（两轮门禁零失败，未触发修复迭代，产品源码零改动——qahost 变体与 `craAndroidTestBuildType` 开关为既有配置，本轮未改）。
- **未验证**：真机端到端验收（§4 验收单待执行，全部基于 qahost 隔离包；执行后才可关闭账本 S04 父项的 PARTIAL）、隔离包在目标 ROM 上的 run-as/WAL 快照法实测、正式应用及书库的前后无变化对账、并发替换、低存储/跨卷、备份恢复端到端（S14）。qahost 清理须在保存证据和核对状态后单独记录，不能预先宣称正式应用绝对不受影响。
