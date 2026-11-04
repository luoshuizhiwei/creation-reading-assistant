# CRA-JOINT-ACCEPTANCE-TESTS-v2：仪器测试合同返修（未运行设备）

## 任务编号/版本

`CRA-JOINT-ACCEPTANCE-TESTS-v2`，日期 2026-10-04。输出根目录：`E:\creation-reading-assistant\next-round-joint-acceptance-tests-v2`。

## 输入指纹

- 恢复仓库基线：`e39a78da9c62f02614207cffb36969a32492eaea`。
- 输入为只读 `next-round-joint-acceptance-tests-v1/android` 编译树；本次新目录排除了其 `build/` 与 `.gradle/` 生成物。原件没有修改。
- 精确 payload 路径、SHA-256、字节数和复制后核对在 [manifest.json](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/payload/manifest.json>)；三个 payload 均与 v2 编译树同名源逐字节相同。

## 所有权与改动

- 仅修改 v2 自有的 `EpubReplaceFreshSourceAcceptTest.kt`；复用的 `MdTrueChapterAcceptV1Test.kt` 保持原始字节，`ReaderAnnotationPendingConsumptionTest.kt` 保持原始字节。未修改产品源码、配置、runner、D 盘原仓库、Git 或设备。
- 辅助离线核验仅为 `offline_contract_check.py`，使用 Python 标准库 ZIP/SQLite；它不执行 Android instrumentation，也不替代设备验收。

## 三个 androidTest 输入指纹

| 路径 | SHA-256 | 状态 |
|---|---|---|
| `acceptance/MdTrueChapterAcceptV1Test.kt` | `991aa720d81ce892f67bb3cb534a1e2047c12a8f53e36d860ad277adbb1d1ab7` | v1 字节复用，v2 实算相同 |
| `acceptance/EpubReplaceFreshSourceAcceptTest.kt` | `945af8eb22d08befe4c2c126499b36afd8f720a092448685ac3fd2c182f6e246` | v2 修复后的唯一测试源码变更 |
| `ui/screen/reader/ReaderAnnotationPendingConsumptionTest.kt` | `12cfd95cdedf77c2d2909d07a3df1c6604bfb1f52f9b3929ca9ae28166eb3e9f` | v1 字节复用，v2 实算相同 |

v1 的三个 androidTest 相对路径与 v2 完全相同，均为 39 个源文件；没有增加或删除 androidTest 类。

## 环境

- JDK：`D:\develop\Java\jdk-17.0.14`；Python：`D:\develop\Python\Python313\python.exe`。
- Gradle 命令：`./gradlew --offline --no-daemon :app:compileDebugAndroidTestKotlin`。
- 每次命令退出码、缓存位置和最终停止状态见 [execution.json](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/execution.json>)。

## 执行与结果

1. `bytesContain` 已替换为对 fixture 的 `OPS/text/ch1.xhtml` 使用 `ZipFile` 解压读取。DEFLATED EPUB 的原始 ZIP 字节不再被错误当作 UTF-8 正文。`jera_10` 同时断言 V2 来源章节、目标章节，以及目标与 V2 来源的完整字节相同。
2. `tearDown` 按 Room v15 实际 schema 精确清理：`highlights`、`notes`、`reading_sessions`、`book_files`、`book_content`、`reading_progress` 使用 `book_id`，`books` 使用 `id`。DB 清理失败仍进入 finally 清理 fixture 文件并关闭 DB；恢复异常以主异常和 suppressed 形式保留，不吞掉。
3. 离线反例在 `offline_contract_check.py` 已执行并通过：DEFLATED ZIP 的原始字节不含正文 marker、解压 entry 含 marker；旧的 `book_id OR id` SQL 在 `reading_progress` 等真实列形状上报 `no such column: id`，精确 SQL 完成后七张表均为空。

## 编译证据

命令在 v2 自有 android 树执行：

```text
JAVA_HOME=D:\develop\Java\jdk-17.0.14
GRADLE_USER_HOME=E:\creation-reading-assistant\next-round-joint-acceptance-tests-v1\_cache\gradle-home
./gradlew --offline --no-daemon :app:compileDebugAndroidTestKotlin
```

新 v2 专属缓存不含 Android Gradle 插件，因此首次离线解析在插件阶段失败，未触及 Kotlin 编译；按任务允许且确认无并行占用后，串行复用 v1 专属缓存。复用运行的原始输出在 `compile-androidtest-v1-cache.log`：`BUILD SUCCESSFUL in 1m 3s`，30 个任务中 11 执行、19 来自缓存。唯一警告来自既有 `LocalZipRestoreAcceptanceTest.kt:626` 的 Json format 创建，不属于本次三源。

SE4 源码和生产 `ReaderProgressEffects` 已定向核对：测试真实组合生产 effect，`pendingHighlightId`、`isLoading` 与 `pendingSourceLocatorJson` 分别作为可变 Compose state 传入；生产 effect 在 source 导航存在时让位、`isLoading` 为真时返回、消费后清空 pending，并以 `jumpToPlainOffset` 为可观察出口。本轮只确认前置接线与编译，未把它宣称为设备行为通过。

## 原始证据

- [三源 payload](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/payload>) 与 [SHA manifest](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/payload/manifest.json>)。
- [离线 ZIP/SQLite 契约脚本](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/offline_contract_check.py>)：本轮 exit 0。
- [独立缓存离线失败日志](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/compile-androidtest.log>)：exit 1，插件解析阶段，未进入 Kotlin。
- [串行 v1 缓存编译日志](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v2/compile-androidtest-v1-cache.log>)：exit 0，BUILD SUCCESSFUL。

## 未覆盖项

- 12 个 instrumentation 方法仍全部 **NOT RUN**：`jera_10`、`jera_20`、`se4_10/20/30` 及 7 个 `mdtc_*`。未连接或操作 MuMu，未安装 APK。
- 本报告不把旧 MD 或旧 EPUB 设备证据绑定到 v2；最终合流后仍需对同一宿主/测试 APK 重新执行限定设备验收。

## 恢复与副作用

无设备、系统设置、正式书库或 APK 副作用。输入候选、产品源码和 Git 均未修改；产物、缓存、日志和 payload 都位于 E 盘专用目录。

## 交付状态与下一步

payload、SHA 清单、执行记录、离线契约与 androidTest 编译证据已交付；仪器行为仍是 NOT RUN。下一位合流所有者可只读取 `payload/manifest.json` 中的三条路径，将其纳入统一候选后再执行限定 MuMu 验收。本任务在此停止。
