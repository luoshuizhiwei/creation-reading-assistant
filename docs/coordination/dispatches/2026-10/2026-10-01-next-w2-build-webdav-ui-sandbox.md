# W2-build-v1 — WebDAV UI 验收宿主沙箱构建报告

- 任务编号/版本：**W2-build-v1（v1）**（依据 Codex-review-J1v3-J2v4-W1v2 的 W2 定义：为 WebDAV UI 验收构建独立 applicationId 宿主）。
- 执行者：CodeArts（隔离构建与冻结工件）；设备窗口 DSH/J2-v4，**本轮零 ADB、零设备操作**。
- 任务目录：`E:\creation-reading-assistant\next-round-w2-webdav-ui-build`（新建，工作树 `…\android`）。
- 唯一源码输入：`E:\creation-reading-assistant\next-round-joint-j1-v3\android`（只读，未改动）。
- 恢复仓库内本轮**只写本报告**一个文件；未 commit / push / reset / checkout / 移动目录。

## 1. 输入指纹

- HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`（未变更）。
- 父候选 J1-v3 六文件指纹 `freeze/six-file-sha256.txt` **重算 6/6 MATCH**，作为允许输入确认（详见 `freeze/evidence/six-file-recheck.txt`）。
- 派生后 W2 对照该清单：5 个文件字节一致，1 个（`app/build.gradle.kts`）为本任务允许变更，其余无差异。
- 未发现同任务既有工件、也未发现并行构建运行；本目录此前为全新创建。

## 2. 所有权与改动（唯一源码配置变化）

派生方式：robocopy 源码级复制（排除生成目录），未复制任何大缓存；复用既有 E 盘 GRADLE_USER_HOME 缓存。

**排除规则（J1-v3 与 W2 两棵树统一口径，写入 `READY.json` 与 `freeze/source-sha256.csv` 说明）：**

- 目录（任意深度，按名排除）：`.gradle`、`.kotlin`、`build`、`.cxx`、`.idea`、`.git`
- 文件：`local.properties`、`*.iml`
- 其余文件一律计为源码。

**唯一变更（单 hunk，1 个文件）：**

`app/build.gradle.kts`（qahost 块，第 119 行附近）：

```diff
-            applicationIdSuffix = ".qahost"
+            applicationIdSuffix = ".qahostwebdav"
```

- 补丁：`freeze/evidence/qahostwebdav-single-hunk.patch`（-1/+1）。
- 前后 SHA：`ae078ff087627125b3e373fa0c9c4936a14dbca8f237441d23f9904c2dac5bb4` → `83e4512cc910a526649e4ed8835bea3663a04b97743899cc919b7bff4ae550d4`。
- 除上述一处字面量外，**debug/release/benchmark 配置、namespace、产品源码、测试源码、依赖、Manifest 源文件均字节一致**。
- 逐文件核对：J1-v3 875 文件 / W2 875 文件；identical 874，only-in-J1-v3 0，only-in-W2 0，changed 1 → `SINGLE_CHANGE_OK=True`（`freeze/evidence/source-manifest-verify.log`、`source-diff.csv`）。

## 3. 环境

| 项 | 值 |
|---|---|
| Java | `D:\develop\Java\jdk-17.0.14` |
| Android SDK | `D:\develop\Android\Sdk`（build-tools 34.0.0；platforms android-34/35） |
| Gradle wrapper | 8.9（`android\gradlew.bat`） |
| GRADLE_USER_HOME | `E:\creation-reading-assistant\next-round-joint-j1-v2\_cache\gradle-home`（复用既有依赖与任务缓存） |
| Python | `D:\develop\Python\Python313\python.exe`（清单/比对脚本） |
| 机器重建的机器本地文件 | `android\local.properties`（`sdk.dir`，按排除规则不计入源码比对） |

## 4. 执行与结果

| 项 | 值 |
|---|---|
| 构建命令 | `. \gradlew.bat --console=plain :app:assembleQahost`（工作目录 `…\next-round-w2-webdav-ui-build\android`） |
| 采集方式 | `cmd /c "chcp 65001 >nul && .\gradlew.bat --console=plain :app:assembleQahost > <LOG> 2>&1"`（字节保真，不经 PowerShell 重编码） |
| 退出码 | **0**（`freeze/evidence/build-assembleQahost.exit.txt`） |
| 首次结果 | BUILD SUCCESSFUL in 1m 51s；41 actionable tasks: 21 executed, 20 from cache |
| 复跑校验 | BUILD SUCCESSFUL in 26s；41 actionable tasks: 41 up-to-date（幂等，产物字节不变） |
| 日志 | `freeze/evidence/build-assembleQahost.log`（GBK/codepage 936，ASCII 部分直接可读；唯一非 ASCII 行为签名告警，不影响结果） |
| 命令记录 | `freeze/evidence/build-command.txt` |
| 未运行 | `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、任何 androidTest 构建/安装 |

## 5. APK 身份与核对

| 核对项 | 结果 |
|---|---|
| 冻结路径 | `E:\creation-reading-assistant\next-round-w2-webdav-ui-build\freeze\apk\app-qahost.apk` |
| 大小 / SHA-256 | 51,306,172 字节 / `0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594` |
| applicationId | **`com.creationreadingassistant.qahostwebdav`**（aapt2 badging 实测，精确匹配合同） |
| Application | `com.creationreadingassistant.App`（dex 命中 2；Hilt 组件 `App_HiltComponents` 命中 46） |
| MainActivity | `com.creationreadingassistant.MainActivity`（dex 命中 4；LAUNCHER） |
| 隔离测试 Application | **未使用**（dex 中无 `CraIsolatedTestRunner`）→ 运行真实 App/Hilt/MainActivity，可供实际 UI 操作 |
| FileProvider authority | `com.creationreadingassistant.qahostwebdav.fileprovider`（exported=false, grantUriPermissions=true） |
| AndroidX startup authority | `com.creationreadingassistant.qahostwebdav.androidx-startup`（exported=false） |
| MLKit init authority | `com.creationreadingassistant.qahostwebdav.mlkitinitprovider`（exported=false） |
| 旧包 authority 残留 | **0**（残留字面量扫描） |
| sharedUserId | **不存在**（未新增） |
| 权限集 | 与父候选归一化后**完全一致**（8 项，未增未减） |
| Manifest 差异 | 归一化后 unified diff hunk 数 **0** → 差异仅为包名派生的必要变化 |
| 签名 | apksigner verify **exit 0**（v2 方案通过；v1/v3/v3.1/v4=false）；1 个签名者 |
| 证书 | `C=US, O=Android, CN=Android Debug`；SHA-256 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1` |
| 最小/目标 SDK | 24 / 35 |

权限集（与父候选一致）：`INTERNET`、`CAMERA`、`POST_NOTIFICATIONS`、`WAKE_LOCK`、`ACCESS_NETWORK_STATE`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`、`com.creationreadingassistant.qahostwebdav.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。

## 6. 原始证据

`E:\creation-reading-assistant\next-round-w2-webdav-ui-build\freeze\evidence\`：

- `build-command.txt`、`build-assembleQahost.log`、`build-assembleQahost.exit.txt`
- `qahostwebdav-single-hunk.patch`、`source-diff.csv`、`source-manifest-verify.log`、`six-file-recheck.txt`
- `apk-badging.txt`、`apk-manifest-xmltree.txt`、`apk-signature.txt`、`apk-sha256.txt`、`dex-product-classes.txt`
- `manifest-diff.txt`、`permission-comparison.txt`、`parent-apk-badging.txt`、`parent-manifest-xmltree.txt`

`freeze/source-sha256.csv`（875 条源码清单）；`freeze/apk/app-qahost.apk`（冻结工件）。
任务目录 `READY.json`（最后写入）：`BUILD_READY=true`、`DEVICE_ACCEPTED=false`。

## 7. 未覆盖项

- **未做任何设备验收**：未 `adb`、未安装、未启动、未截图；`DEVICE_ACCEPTED=false` 恒成立。
- 未构建/安装 androidTest APK（本任务不需要）；qahostwebdav 宿主的仪器运行不在本轮范围。
- 未重跑 2673 项 JVM 与全量 Lint（未受影响，故按授权不跑）。
- APK 为 **debug 签名回退**（`-PcraAllowDebugSigningFallback` 语义的 qahost 变体），非正式分发签名；仅用于本机/设备验收。

## 8. 恢复与副作用

- 未修改 J1-v3 候选、J1-v2 缓存、其他窗口的工作树与任何 HOLD/Claude 独立记忆库。
- 未操作 ADB，未改动设备。
- 恢复仓库仅新增本报告；工作树无其他改动。
- 副作用：向 `E:\creation-reading-assistant\next-round-w2-webdav-ui-build` 写入候选、冻结工件与证据；使用 J1-v2 E 盘 gradle 缓存（只读复用其依赖/任务缓存）。

## 9. 交付状态与下一步

- **BUILD_READY=true**：唯一源码变化、单 hunk、包名/authority/权限/签名/Manifest 差异全部核对成立。
- **DEVICE_ACCEPTED=false**：设备验收未开始，本 READY 仅为设备窗口的输入合同。
- 下一步（DSH/J2-v4）：以 `freeze/apk/app-qahost.apk`（SHA-256 `0221d88f…`，applicationId `com.creationreadingassistant.qahostwebdav`）作为独立宿主安装并执行 WebDAV UI 验收；因 applicationId 与既有 `…qahost` 不同，可与父宿主并存、authority 无冲突。
