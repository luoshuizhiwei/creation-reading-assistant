# J1-v2 — 持久化仪器测试夹具与副作用隔离返修报告

- 任务编号/版本：**J1-v2**（依据 Codex-review-J2-v1，2026-10-01：`docs/coordination/reviews/2026-10/2026-10-01-next-j1-j2-codex-review.md`）。
- 执行者：ZCode/GLM；核查：Codex；本任务不操作 MuMu/设备。
- 旧 J1 作业状态：已停止（J1 全部门禁命令均已退出；现场仅剩常驻 Gradle daemon，无活动构建；本轮未触碰其进程）。原候选 `next-round-joint-20261001` 与 `freeze/` 全程只读，原始日志未覆盖。

## 1. 输入指纹

- 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`；本轮未 commit/push/reset/checkout、未改原损坏仓库。
- 输入清单：原候选 `source-manifest-j1.csv` 六文件，**逐项 SHA 重验全部一致**（`_evidence/j1v2-input-sha-verify.txt`；首跑一次 MISMATCH 为验证脚本 android/ 前缀拼接笔误，已更正重验）。
- 新候选：`E:\creation-reading-assistant\next-round-joint-j1-v2\android`（875 文件）。与已核 J1 候选源码树逐文件 SHA 对比（排除 local.properties）：**零差异**——v2 起点 = 已核源码，未从共享脏工作树复制。独占 Gradle 缓存 `next-round-joint-j1-v2/_cache/gradle-home`（2.8G，自 J1 缓存副本派生，仅 4 个在用 `.lock` 跳过）。

## 2. 所有权与改动

仅改两文件（均在授权范围内），累计 diff 341 行：

| 文件 | 状态 | SHA（v2 修后） | diff |
|---|---|---|---|
| `android/.../SyncRepositoryPersistenceTest.kt` | MOD-J1V2 | c0873136e1ca073ea18a93aa168c3b695ef84476a4b4eac888784bd84ac28185（J1 版 f14bc703…） | `freeze/patches/j1v2-diff-SyncRepositoryPersistenceTest.patch`（322 行） |
| `android/app/build.gradle.kts` | MOD-J1V2 | ae078ff087627125b3e373fa0c9c4936a14dbca8f237441d23f9904c2dac5bb4（J1 版 4c7b7a78…） | `freeze/patches/j1v2-diff-build.gradle.patch`（19 行） |

其余 4 个已核文件零修改（SHA 不变）；产品源码、Room 约束/迁移、签名规则、版本目录（libs.versions.toml）、旧 freeze、全局交接文件均未触碰。

### 2.1 SyncRepositoryPersistenceTest 返修内容

1. **外键父行**（设备首跑 3 失败的根因）：`reading_progress.book_id → books.id` 为 Room FK。新增 `seedBook(id)` 夹具，先为 pk-keep、pk-remote、pk-b1、pk-r、pk-s 建合法 books 父行。**FK 保持开启**，未删用例、未降断言、未 mock 被测合并逻辑。
2. **断言先行**：每处 `getByBook/getById(...)!!` 改为 `requireNotNull(expr) { 中文原因 }`——先断言 pull 计数、`failedItems.isEmpty()`（失败时打印明细）与目标行存在，杜绝以 `!!` 的 NPE 遮蔽真实错误（原版 pull 内 FK 失败被 `runCatching` 吞成 failedItems、随后 NPE，正是 J1 缺陷形态）；MockWebServer 的 takeRequest 同样改为 requireNotNull 并断言请求路径。
3. **提供者隔离（本次核心）**：`SyncConfigStore` / `DeviceInfoProvider` 为 final 类，经 `SecurePrefs` 触碰 AndroidKeyStore 默认主密钥与 `sync_config` / `cra_device_identity` 加密 prefs（且 `SecurePrefs.open` 解密失败会删 prefs 重建）。J1 版误用真实实例。v2 用 **mockk-android 测试替身**整体替换两者：
   - `configStore.config` 固定返回指向进程内 MockWebServer 的测试配置（relaxed mock 吞掉 setter，`markSyncedAt` 因此为无副作用 no-op）；
   - `deviceInfoProvider.provide()` 返回固定测试设备信息；
   - `SyncApiProvider` / `AuthInterceptor` / Retrofit / OkHttp / kotlinx.serialization / SyncRepository / Room **全部走真实生产代码**（current() 读替身 config 指向 MockWebServer，wire 栈真实）；
   - `@After unmockkAll()` 释放替身；**测试对 qahost 的 prefs 文件与 KeyStore 零读写**——不是靠 Context/prefs 换名（同 UID 的 KeyStore 无法由此证明隔离），而是从构造上不存在任何 SecurePrefs/EncryptedSharedPreferences/KeyStore 调用路径。
4. **保留项**：真实 Room 落库 + close/reopen 持久化证明、真实 pull/push 链、真 wire 序列化（pull 响应与 push 请求 JSON 均走生产栈）、四项业务断言与 J1 等价并加强（新增 pull 计数/failedItems/行存在断言、push 结果 failedItems 断言、push 进度条目集合断言）。
5. **依赖 hunk**：`androidTestImplementation("io.mockk:mockk-android:1.13.12")`（字符串坐标，版本目录未改；与 JVM 侧 mockk 同版本），并按组排除其传递引入的 JVM-only JUnit5 构件（`org.junit.jupiter` / `org.junit.platform`——否则 6 个 META-INF/LICENSE.md 合并冲突使 androidTest APK 打包失败）。mockwebserver hunk 原样保留。

### 2.2 依赖解析说明

mockk-android 不在任何 E 盘缓存（缓存中仅有 JVM 侧 mockk-jvm 系列 jar）。构建未加 `--offline`，从 Maven Central（可达性已预检，HTTP 200）解析 `io.mockk:mockk-android:1.13.12` 及其传递依赖，全部落入 v2 **独占缓存**，未写共享/其他窗口缓存。mockk-android 为官方 androidTest 替身方案：final 类经 inline instrumentation 处理，要求 Android 8.0+（MuMu 为 Android 12，满足）。

## 3. 环境与执行

- JDK17 `D:\develop\Java\jdk-17.0.14`；SDK `D:\develop\Android\Sdk`；独占缓存 `next-round-joint-j1-v2/_cache/gradle-home`。
- 构建命令（与 J1 已验证变体一致）：`./gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --max-workers=1`（不带 --offline，仅为解析 mockk-android）。
- 过程：两次编译失败均已定位修复并留痕——① JUnit4 `assertNotNull(String,Object)` 无返回值不可链式（13 处改 `requireNotNull`）；② mockk-android 传递 JUnit5 构件致 `mergeQahostAndroidTestJavaResource` 冲突（依赖内按组排除）。
- **最终结果：`V2_QAHOST_EXIT=0`，BUILD SUCCESSFUL**（日志 `freeze/evidence/gate-v2-qahost.log`）。未重跑 2673 项 JVM、未重建其余 APK。

## 4. 交付物与指纹

| 项 | 值 |
|---|---|
| 新测试 APK | `next-round-joint-j1-v2/freeze/apk/app-qahost-androidTest.apk` SHA `83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8` |
| 测试 APK 包名/目标/runner | `com.creationreadingassistant.qahost.test` → targetPackage `com.creationreadingassistant.qahost`，runner `com.creationreadingassistant.testing.CraIsolatedTestRunner`（aapt 实测） |
| 宿主 APK（复用） | `app-qahost.apk` SHA `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`——**与 J1 冻结值逐位一致**：复用依据 = v2 源码树与已核候选零差异 + 产品源码/依赖（host 侧）零变化，本轮重建仅作 SHA 复核，结果逐位相同 |
| 新测试类 | `com.creationreadingassistant.data.repository.SyncRepositoryPersistenceTest`（4 用例） |
| 清单 | `freeze/source-manifest-j1v2.csv`（本轮）+ `freeze/source-manifest-j1-input.csv`（J1 输入留存） |

## 5. 编译通过 ≠ 测试通过

四项仪器测试本轮**只完成编译打包，未运行**（本任务不操作设备）。当前状态只能表述为"**待设备运行**"：mockk-android 的 inline agent 在 ART 上的实际行为、final 类替身在真机的生效、MockWebServer 端口绑定等，均需窗口在 MuMu 首跑确认。任何"设备测试通过"的结论必须来自设备端 XML/日志。

## 6. 未覆盖项

- 本测试的设备运行与四用例结果；J2-v1 其余发现（WebDAV 安全/导入结论、APK 身份、恢复对账）属 J2-v2/J2-v3 范围，本轮未触碰。
- mockk-android 替身在 device 上的运行期验证（见 §5）；若真机 inline agent 失效，备用方案需另议（SEAM REQUEST 或独立 testApp），本轮不预改。
- JVM 全量 2673 与其余 APK 未重跑：改动仅 androidTest 源集与 androidTest 依赖，host/JVM 产物不受影响（host 重建 SHA 逐位一致即证）。

## 7. 恢复与副作用

- 本轮零设备操作；未写入任何 qahost 数据（测试未运行，且替身设计使测试运行期也不写）。
- 原候选与 freeze 只读：本轮仅读取，无写入/删除；`_evidence` 均在 v2 目录内。
- E 盘新增：v2 候选、独占缓存（2.8G）、freeze（2 APK + 补丁 + 清单 + 日志）；网络仅用于 Maven Central 拉取 mockk-android 至独占缓存。
- 共享仓库仅新增本报告文件；未改其他窗口文件与交接文档。

## 8. 交付状态与下一步

- **交付状态：BUILD_READY（测试包）= true；DEVICE_ACCEPTED = false；测试运行 = NOT RUN（待设备）。**
- 下一步：Codex 核查本返修（重点：替身隔离证明、外键父行完整性、断言先行是否消除遮蔽）；核查通过后由 J2-v3 窗口在 MuMu 安装 `freeze/apk/` 的 APK 对并运行 `SyncRepositoryPersistenceTest` 四用例，按协议冷备/恢复 qahost 持久数据与设置。
- 若设备首跑发现需改动产品代码或扩大依赖，停止并提交 SEAM REQUEST，保留现场。
