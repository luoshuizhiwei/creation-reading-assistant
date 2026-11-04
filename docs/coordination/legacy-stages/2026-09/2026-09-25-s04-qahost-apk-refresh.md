# S04 QA host APK 新鲜离线复核 — Zcode 报告

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed593`，基点 `origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`）
- 性质：**只读复核 + 构建**。仅运行 Gradle 构建命令并读取 `android/app/build/` 产物；未修改任何产品源码、测试源码或构建配置；未运行 adb、安装、instrumentation；未操作手机；未触碰原始仓库、E 盘备份、用户书籍或 Claude 独立记忆库。
- 唯一新增文件：本报告。
- 对照文档：`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-offline-integration.md`（集成者报告）。

---

## 1. 结论摘要

**复核通过，版本闭合成立，无阻塞项。**

- 命令新鲜离线执行 **EXIT 0**（BUILD SUCCESSFUL in 28s，72 actionable tasks 全部 up-to-date）。
- 两个 APK 的 SHA-256 与集成者报告**逐字节一致**。
- host / test 包名、`targetPackage`、`CraIsolatedTestRunner` 全部在 APK 二进制内直接核实，与集成报告完全一致。
- 输入稳定性在本轮离线构建范围内有证据支持（见 §4），因此**不标 BLOCKED**；证据口径与不外推边界见 §4、§5.4、§7。

需要透明记录的一点：本次运行的 72 个任务全部 up-to-date，APK 文件时间戳为
`2026-09-25 07:53`（即集成者此前 5m4s 构建的产物）。本次运行的意义在于：
Gradle 以当前输入重新校验了整个任务图并判定无需重做，叠加 APK 哈希一致，
支持「本轮离线构建所核对的产物与当前构建输入对应」这一**有限结论**（论证与边界见 §4③、§7）。
该结论**不外推**为「所有源码逐字节绝对未变」，也**不外推**为设备或运行期安全。

---

## 2. 构建命令与退出码

执行目录：`android/`

```
.\gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain
```

| 项 | 值 |
|---|---|
| 退出码 | **EXIT 0** |
| 构建结果 | `BUILD SUCCESSFUL in 28s` |
| 任务统计 | `72 actionable tasks: 72 up-to-date` |
| 日志文件 | （临时，未入库）末次输出行 94–95 为 SUCCESSFUL 与任务统计 |
| 配置期输出 | `?? 未配置正式签名，使用 debug 密钥兜底签名…`（预期的本地路径提示，qahost 显式使用 debug 签名，`build.gradle.kts:113`） |

**未运行** `adb`、未安装、未跑 instrumentation、未跑 `:app:tasks --all` 之外的任何其他任务。

---

## 3. 产物 SHA-256（与集成报告对照）

| APK | 本次计算 SHA-256 | 集成报告值 | 一致 |
|---|---|---|---|
| `android/app/build/outputs/apk/qahost/app-qahost.apk` | `064f53341475ccfdc898205adfb36451e380f3a49c8c014aecea9373bc7dc054` | `064F53341475CCFDC898205ADFB36451E380F3A49C8C014AECEA9373BC7DC054` | ✅ |
| `android/app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk` | `fa48a6bc6da850c367fa4dd53679384e21d5a7c5c717dd9d711fb002f9a21b54` | `FA48A6BC6DA850C367FA4DD53679384E21D5A7C5C717DD9D711FB002F9A21B54` | ✅ |

（大小写差异仅为哈希工具输出格式；值相同。）

---

## 4. 输入稳定性证明（为何不标 BLOCKED）

三层证据，全部在本次会话内取得：

**① 构建前后输入指纹完全一致**

| 指纹对象 | 构建前 | 构建后 |
|---|---|---|
| `android/app/build.gradle.kts` 内容 SHA-256 | `aac93f05a51d801759bfd0a6678a1267f5d34dfb2b64132c0bf4fbeb63439606` | 同左（逐字节一致） |
| `git status --short --untracked-files=all -- android/app/src/ android/gradle/` 排序后哈希 | `edde3835e204bda401685ec443eefd29f8855d3a129340dca422bea0f56bf3ab` | 同左 |

该快照覆盖 androidTest 源集内的未跟踪新文件
（`IsolatedTestContext.kt`、`EpubReplaceRoomIsolatedTest.kt` 等）——它们在磁盘上、
参与构建、且构建窗口内 `git status` 输出未变化。

**口径限定（①）**：上表 `build.gradle.kts` 一项是内容 SHA-256，可直接证明该文件字节未变；
而 `git status` 排序后哈希**只反映状态输出**（改动 / 未跟踪标记），**不能**证明被跟踪源码的
内容逐字节未变 —— 若某文件在构建窗口内被改动又改回，或改动未改变 `git status` 的显示形态，
该哈希都不会体现。故此项仅作辅助旁证，本轮更强的证据是 ③ 的 Gradle up-to-date 判定与
APK 哈希一致（其支持范围与边界见 §4③、§7）。

**② 其余构建配置文件相对 index 无改动**

`android/build.gradle.kts`、`android/settings.gradle.kts`、`android/gradle/libs.versions.toml`、
`android/gradle.properties`、`android/gradlew.bat`、`android/app/proguard-rules.pro`
的 `git status` 均为空（在快照时刻相对 index 无可见改动；同「口径限定（①）」，
此为状态输出层面的旁证，不作逐字节内容断言）。

**③ Gradle 自身的 up-to-date 判定**

72 个任务全部 up-to-date 意味着 Gradle 对每个任务重新计算了输入指纹（源码、配置、依赖），
并与产物生成时（07:53）记录的指纹比对后判定一致。在本轮离线构建的输入范围内，
若某个被 Gradle 追踪的输入自那之后发生实质变化，对应的 compile/package 任务理应重跑，
APK 哈希也就不可能与报告一致。

**口径限定（③）**：该判定支持「本轮离线构建所核对的产物与当前构建输入对应」这一有限结论，
但**不能**外推为「所有源码逐字节绝对未变」（Gradle 只追踪纳入其任务图的输入，指纹比对
也不等于对全仓内容做逐字节审计），更不能外推为设备或运行期安全（见 §7）。

**并行窗口影响核查**：本轮窗口 C 负责修订 S04 基线工具、窗口 D 负责编写 S04 离线验机清单
（二者均非「S09 只读调查」或「D-2-B 真机验收」）。构建窗口内 `build.gradle.kts` 内容哈希与
`android/app/src/`、`android/gradle/` 的 `git status` 快照前后一致，**未观察到**与本轮构建输入
相关的改动迹象；但如上文「口径限定（①）」所述，`git status` 快照不能单独排除
「改动后又改回」等情形，故此处仅表述为「未观察到」，不作「无任何窗口改动」的绝对断言。

---

## 5. APK 内容核对（aapt 直接读二进制，非文档转述）

工具：`D:/develop/Android/Sdk/build-tools/34.0.0/aapt.exe`

### 5.1 host APK（`app-qahost.apk`）

```
package: name='com.creationreadingassistant.qahost' versionCode='3' versionName='0.5.1'
sdkVersion:'24'  targetSdkVersion:'35'  compileSdkVersion='35'
```

| 项 | 期望（集成报告 / build.gradle.kts） | 实测 | 一致 |
|---|---|---|---|
| 包名 | `com.creationreadingassistant.qahost` | 同左 | ✅ |
| versionCode | 3（`build.gradle.kts:29` 缺省） | 3 | ✅ |
| versionName | 0.5.1（`:30` 缺省） | 0.5.1 | ✅ |
| minSdk / targetSdk | 24 / 35 | 24 / 35 | ✅ |

`output-metadata.json` 的 `applicationId` 亦为 `com.creationreadingassistant.qahost`。

### 5.2 test APK（`app-qahost-androidTest.apk`）

```
package: name='com.creationreadingassistant.qahost.test'
E: instrumentation
  android:label="Tests for com.creationreadingassistant.qahost"
  android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"
  android:targetPackage="com.creationreadingassistant.qahost"
```

| 项 | 期望 | 实测 | 一致 |
|---|---|---|---|
| test 包名 | `com.creationreadingassistant.qahost.test` | 同左 | ✅ |
| `targetPackage` | `com.creationreadingassistant.qahost` | 同左 | ✅ |
| Runner | `com.creationreadingassistant.testing.CraIsolatedTestRunner` | 同左 | ✅ |

### 5.3 Runner 类实际存在（不只 manifest 引用）

test APK 解包后有 14 个 dex；`CraIsolatedTestRunner` 位于 `classes12.dex`，
且可提取出完整类描述符：

```
Lcom/creationreadingassistant/testing/CraIsolatedTestRunner;
```

即 manifest 声明的 Runner 在 dex 中**真实存在**，不是悬空引用。

### 5.4 默认 debug 构建路径的配置未受影响（有限核对）

`build/outputs/apk/debug/` 与 `build/outputs/apk/androidTest/debug/` 产物仍存在
（`testBuildType` 缺省回落 `debug`，`build.gradle.kts:16`），`-PcraAndroidTestBuildType=qahost`
只作用于本次显式构建，未观察到其改变默认 debug 构建路径的配置。

**口径限定（5.4）**：现存 debug APK 文件的存在**只能**说明磁盘上仍有历史 debug 产物，
**不能单独证明默认 debug 构建门禁通过**——这些文件可能是更早构建遗留，本轮也未重跑
`:app:assembleDebug` 等默认 debug 任务。要证明默认 debug 门禁通过，需另跑对应默认
debug 构建任务并核对退出码，不在本轮范围内。

---

## 6. 与既有集成报告的一致性

| 集成报告声明 | 本次独立复核 | 一致 |
|---|---|---|
| 命令 EXIT 0 | EXIT 0（本会话复跑） | ✅ |
| host 包 `com.creationreadingassistant.qahost` | aapt 实测同左 | ✅ |
| test 包 `com.creationreadingassistant.qahost.test` | aapt 实测同左 | ✅ |
| `targetPackage=com.creationreadingassistant.qahost` | aapt 实测同左 | ✅ |
| Runner `com.creationreadingassistant.testing.CraIsolatedTestRunner` | manifest + dex 双重实测 | ✅ |
| host SHA `064F5334…` | `064f5334…` | ✅ |
| test SHA `FA48A6BC…` | `fa48a6bc…` | ✅ |
| `build.gradle.kts` 含 `testBuildType`、`qahost` buildType、`add("qahostImplementation", …)` | 读源码逐条确认（`:16`、`:105-114`、`:245`） | ✅ |

`git diff 5aed593 -- android/app/build.gradle.kts` 的内容与集成报告「实际修正」一节描述的三处改动
（`testBuildType`、`qahost` buildType、`add("qahostImplementation", …)`）**逐一对应**，
且保留了报告所述的既有未提交改动（versionCode 3 / versionName 0.5.1 / CraIsolatedTestRunner）。

---

## 7. 阻塞项

**无。** 本轮未发现任何需要标 BLOCKED 的事项：
命令成功、哈希闭合、包名核对全部通过、输入稳定性在本轮离线构建范围内有证据支持
（其支持范围与不外推边界见 §4、§5.4）。

范围外说明（非阻塞，仅边界重申）：编译与 manifest 核实**不能**证明系统设置、共享存储、
ContentProvider、设备权限或 34 个 androidTest 类的运行期行为无旁路副作用——
这与集成报告 §「判定与下一门槛」的措辞一致，真机验收边界由该文档与后续裁决决定，不属于本次 APK 复核范围。

---

## 8. 本轮操作清单（合规自证）

| 动作 | 是否执行 |
|---|---|
| 读 AGENTS.md / 集成报告 / build.gradle.kts | ✅ |
| `git status` / `git diff` / `git rev-parse`（只读） | ✅ |
| `sha256sum`（build.gradle.kts 与两个 APK） | ✅ |
| `unzip` APK 到 `%TEMP%` 并删除（只读检查产物内容） | ✅ |
| aapt dump badging / xmltree（只读） | ✅ |
| 指定 Gradle 命令（--offline） | ✅ EXIT 0 |
| 修改产品/测试源码、构建配置 | ❌ 未执行 |
| adb / 安装 / instrumentation / 操作手机 | ❌ 未执行 |
| commit / push / stage / reset / checkout / clean | ❌ 未执行 |
| 触碰原始仓库 / E 盘备份 / 用户书籍 / Claude 记忆库 | ❌ 未执行 |
| 修改他人窗口文件或既有报告 | ❌ 未执行 |

唯一新增文件：`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-apk-refresh.md`（本报告）。
`android/app/build/` 下产物未发生实质变化（72 任务 up-to-date，哈希与时间戳均未变）。
