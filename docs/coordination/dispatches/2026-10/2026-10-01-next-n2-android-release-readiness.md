# 原生 Android Release 构建配置与发布准备验证报告 (N2 / R3 补正版)

> **任务标识**：下一轮任务 N2 / R3 — 原生 Android Release 构建配置验证与可复现构建准备证据补正  
> **基线提交**：`e39a78da9c62f02614207cffb36969a32492eaea`（提交说明实际为：`fix(android): integrate verified recovery candidate and reader state safety`，前版报告提交说明勘误）  
> **隔离工作目录**：`E:\creation-reading-assistant\next-round-n2\android`（精准导出 872 个基线文件，独占目录与构建缓存）  
> **证据归档目录**：`E:\creation-reading-assistant\next-round-n2\evidence-archive\`（各分支产物、完整构建日志与校验输出持久归档）  
> **报告落盘路径**：`docs/coordination/dispatches/2026-10/2026-10-01-next-n2-android-release-readiness.md`  
> **验证时间**：2026-10-01  
> **约束与边界**：未操作 MuMu 模拟器（设备由窗口 1 独占）；未修改共享仓库产品 Kotlin 源码或桌面端文件；全流程离线构建；构建通过仅代表静态编译与打包阶段无阻塞，不代表 R8 混淆包已在设备端完成全量功能回归。

---

## 1. 执行摘要与结论收窄

本轮任务从阶段 E 最终验收提交 `e39a78da9c62f02614207cffb36969a32492eaea` 导出原生 Android 工程至独占隔离目录，针对 Release 构建配置、签名回退控制、R8 混淆与资源缩减、Baseline Profile、清单与权限以及 DEX 类隔离进行了验证，并对前版报告中的过度外推断言进行了严格收窄。

### 核心结论（严格限定于构建与静态验证范围）
1. **构建与元数据规范符合预期**：`applicationId`（`com.creationreadingassistant`）、版本号（`0.5.1(3)`，支持动态注入）、SDK 版本（minSdk 24 / targetSdk 35 / compileSdk 35）均与配置规划一致；
2. **清单安全确证**：Release 产物中 `android:debuggable` 与 `android:testOnly` 属性均不存在（即 false）；
3. **DEX 测试类隔离确证**：使用 `apkanalyzer.bat dex packages` 导出并穷尽分析了 APK 内两个 DEX 的全部 10,301 个类、643 个包，确证 `CraIsolatedTestRunner`（0 命中）、`BenchmarkSeedActivity`（0 命中）、`com.creationreadingassistant.testing` 包（0 命中）及任何 JUnit/测试框架类均未泄漏至发布 DEX 中；同时合并清单报告（`manifest-merger-release-report.txt`）亦无测试组件；
4. **混淆与优化静态生效**：R8 混淆开启，`resources.txt` 记录了资源缩减明细，APK 与 AAB 内部均已打入 `baseline.prof` 与 `baseline.profm`；
5. **签名多分支闭环留存**：
   - 纠正了基线脚本本地构建无签名时静默兜底 debug 签名的行为，在隔离配置中改为默认产出标准未签名包 `app-release-unsigned.apk`；
   - 保留 `-PcraAllowDebugSigningFallback=true` 用于显式开启本地调试签名；
   - 验证了 CI 环境下（`GITHUB_ACTIONS`）缺少签名直接抛出异常中止（Exit 1）；
   - 各分支产物、完整日志与退出码已在 `evidence-archive/` 独立归档。
6. **结论收窄（拒绝无充分证据的 Release-Ready 判定）**：
   - **不能判定为“Release-Ready”或“100% 发布达标”**：构建通过仅证明编译、混淆和打包无语法与配置错误，**不代表 R8 混淆后的产物在设备运行时没有反射被删或类型转换崩溃**；
   - **正式签名仅是发布阻碍之一**：正式分发还受制于 R8 产物未在设备上全量回归、历史 0.1.x 升级链证书与包名尚未核实（待核对）、以及既有运行期缺陷（如数据恢复原子性不支持、同步覆盖本地事实等）尚未解决。

---

## 2. 基线提交与构建环境核查

### 2.1 基线提交勘误与隔离树
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **基线 Commit**：`e39a78da9c62f02614207cffb36969a32492eaea`
- **提交作者**：`luoshuizhiwei <2325495253@qq.com>`
- **实际提交说明**：`fix(android): integrate verified recovery candidate and reader state safety`（前版报告误写为交接文字，在此正式勘误）
- **隔离导出**：使用 `git archive e39a78da android | tar -x` 导出 872 个文件至 `E:\creation-reading-assistant\next-round-n2\android`，完全避开共享工作树。
- **独占 Gradle Cache**：`E:\creation-reading-assistant\next-round-n2\gradle-cache`（2.15 GB 离线缓存）。

### 2.2 工具链环境
- **JDK**：17.0.14 (`D:\develop\Java\jdk-17.0.14`)
- **Android SDK**：`D:\develop\Android\Sdk` (compileSdk 35 / build-tools 34.0.0)
- **分析工具**：`D:\develop\Android\Sdk\build-tools\34.0.0\aapt.exe`、`apksigner.bat`、`D:\develop\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat`
- **Gradle**：8.9 (Gradle Wrapper) / AGP 8.5.2 / Kotlin 1.9.24

---

## 3. 配置审查与 DEX 深度分析

### 3.1 应用与版本属性
- **`applicationId`**：`com.creationreadingassistant`（无变体后缀）；
- **版本号**：`versionCode = 3`, `versionName = "0.5.1"`；支持 `-PcraVersionName` 与 `-PcraVersionCode` 命令行动态注入；
- **SDK 规范**：`minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`, `isCoreLibraryDesugaringEnabled = true`。

### 3.2 混淆（R8）与资源缩减
- `isMinifyEnabled = true`, `isShrinkResources = true`；
- `proguard-rules.pro` 保留了崩溃堆栈行号（`-keepattributes SourceFile,LineNumberTable`）、kotlinx.serialization Companion/单例序列化器，以及 Retrofit HTTP 注解；
- AAR 依赖库（Room、Hilt、OkHttp、Coil、CameraX、ML Kit）均使用官方内置 Consumer 规则；
- 产物内置 Baseline Profile：`assets/dexopt/baseline.prof` 与 `baseline.profm`。

### 3.3 DEX 类与合并清单深度审查（DEX Analysis）
针对前版报告仅使用 `aapt list`（仅能核对 ZIP 条目）的局限，本轮使用 `apkanalyzer.bat dex packages` 对 `app-release-unsigned.apk` 内的两个 DEX 文件（`classes.dex` 8.30 MB，`classes2.dex` 279 KB）进行全类导出，并对合并清单报告（`manifest-merger-release-report.txt`）进行了全文检索：

| 检查项 | 分析对象与范围 | 检索结果 | 结论 |
| :--- | :--- | :--- | :--- |
| **测试 Runner** (`CraIsolatedTestRunner`) | 全部 10,301 个类、643 个包 | 0 命中 (`[]`) | 确证未混入 Release DEX |
| **测试 Activity** (`BenchmarkSeedActivity`) | 全部 10,301 个类、643 个包 | 0 命中 (`[]`) | 确证未混入 Release DEX |
| **测试代码包** (`*.testing`) | 全部 643 个包 | 0 命中 (`[]`) | 确证测试源码包已剥除 |
| **JUnit 框架类** (`junit.*`) | 全部 10,301 个类 | 0 命中 (`0`) | 确证未混入测试框架依赖 |
| **AndroidX 测试库** (`androidx.test.*`) | 全部 10,301 个类 | 0 命中 (`0`) | 确证未混入仪器测试依赖 |
| **CRA 工程内测试类** (`com.creationreadingassistant.*Test*`) | 全部 10,301 个类 | 0 命中 (`[]`) | 确证测试类已完全剥除 |
| **合并清单测试组件** | `manifest-merger-release-report.txt` 全文 | 0 命中 (`0`) | 确证清单无测试组件注册 |

### 3.4 清单与组件安全
- `android:debuggable`: 不存在（即 false）；
- `android:testOnly`: 不存在（即 false）；
- 核心 Activity：`MainActivity` 为唯一 exported=true 的应用入口（带 MAIN / LAUNCHER）；
- `FileProvider`: `exported=false`, `grantUriPermissions=true`；
- 权限范围：显式申请 `INTERNET`、`CAMERA`（optional）、`POST_NOTIFICATIONS`，未申请危险外部存储读写权限，全 SAF 架构；
- 备份排除：`backup_rules.xml` 与 `data_extraction_rules.xml` 显式排除了 `sync_config.xml`、`webdav_config.xml` 与 `ai_secrets.xml`。

---

## 4. 签名机制审查与多分支独立证据

### 4.1 最小签名配置改进补丁
基线 `android/app/build.gradle.kts` 本地未配置签名时静默 fallback 为 debug 签名。隔离目录中的最小修改补丁如下：
```diff
--- a/android/app/build.gradle.kts
+++ b/android/app/build.gradle.kts
@@ -98,8 +98,15 @@ android {
                         "CI 环境缺少 Android 发布签名（CRA_ANDROID_KEYSTORE_* 四个变量必须齐全），已中止 release 构建。",
                     )
                 }
-                signingConfig = signingConfigs.getByName("debug")
-                logger.lifecycle("⚠️ 未配置正式签名，使用 debug 密钥兜底签名；产物仅可用于本地验证，绝不可发布！")
+                val allowDebugFallback = providers.gradleProperty("craAllowDebugSigningFallback")
+                    .orNull?.toBoolean() ?: false
+                if (allowDebugFallback) {
+                    signingConfig = signingConfigs.getByName("debug")
+                    logger.lifecycle("⚠️ 未配置正式签名，已显式启用 debug 密钥兜底签名；产物仅可用于本地验证，绝不可发布！")
+                } else {
+                    signingConfig = null
+                    logger.lifecycle("⚠️ 未配置正式签名，产物为未签名 APK（app-release-unsigned.apk），不可正式分发！若需本地设备验证，可添加 -PcraAllowDebugSigningFallback=true。")
+                }
             }
         }
```

### 4.2 各分支构建证据与实体归档（不混用产物）

为了彻底避免产物被后续构建覆盖，所有分支产物、完整日志与退出码均已按分支持久归档至 `E:\creation-reading-assistant\next-round-n2\evidence-archive\`：

#### 分支 1：默认无签名 Release APK 分支（主发布准备产物）
- **构建命令**：`.\gradlew.bat :app:assembleRelease --offline --console=plain`
- **退出码**：`0`（记录于 `evidence-archive/branch-unsigned/exitcode.txt`）
- **构建日志**：`evidence-archive/branch-unsigned/build.log`
- **产物文件**：`evidence-archive/branch-unsigned/app-release-unsigned.apk`
- **文件大小**：`26,636,786` 字节
- **SHA-256**：`AF49CC390FFA7D2D1EE4ACF97BC2C6797C62A506BA64BE52BD618656A7809C7D`
- **`apksigner verify` 日志**（`evidence-archive/branch-unsigned/apksigner-verify.log`）：
  ```text
  DOES NOT VERIFY
  ERROR: Missing META-INF/MANIFEST.MF
  (退出码 1)
  ```
- **判定**：**规范未签名中间产物**。确立“未签名不可正式分发”的合规基准。

#### 分支 2：显式 Debug 兜底签名 Release APK 分支（本地验证专用）
- **构建命令**：`.\gradlew.bat :app:assembleRelease --offline --console=plain -PcraAllowDebugSigningFallback=true`
- **退出码**：`0`（记录于 `evidence-archive/branch-debugfallback/exitcode.txt`）
- **构建日志**：`evidence-archive/branch-debugfallback/build.log`
- **产物文件**：`evidence-archive/branch-debugfallback/app-release.apk`
- **文件大小**：`26,649,074` 字节
- **SHA-256**：`0126988CB55B7A923C973824D65A99FD80DE09C2920655BC22499BFC7A0D8CEF`
- **`apksigner verify --print-certs` 日志**（`evidence-archive/branch-debugfallback/apksigner-verify.log`）：
  ```text
  Verifies: true
  Verified using v2 scheme: true
  Number of signers: 1
  Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
  Signer #1 certificate SHA-256 digest: 05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1
  ```
- **判定**：**仅供本地实体机/测试环境验证混淆包专用**。绝不可作为正式包对外分发。

#### 分支 3：CI 缺签名拒绝分支（保护守卫验证）
- **触发条件**：环境变量 `$env:GITHUB_ACTIONS = "true"`，未配置签名变量
- **构建命令**：`.\gradlew.bat :app:assembleRelease --offline --console=plain`
- **退出码**：`1`（构建明确失败，记录于 `evidence-archive/branch-ci-rejection/exitcode.txt`）
- **构建日志**：`evidence-archive/branch-ci-rejection/build.log`
  ```text
  FAILURE: Build failed with an exception.
  * Where:
  Build file 'E:\creation-reading-assistant\next-round-n2\android\app\build.gradle.kts' line: 97
  * What went wrong:
  CI 环境缺少 Android 发布签名（CRA_ANDROID_KEYSTORE_* 四个变量必须齐全），已中止 release构建。
  BUILD FAILED in 2s
  ```
- **判定**：**CI 强门禁有效**。彻底杜绝未签名或伪签名包从 CI 流水线逃逸。

#### 分支 4：Release App Bundle (AAB) 分支
- **构建命令**：`.\gradlew.bat :app:bundleRelease --offline --console=plain`
- **退出码**：`0`（记录于 `evidence-archive/branch-aab/exitcode.txt`）
- **构建日志**：`evidence-archive/branch-aab/build.log`
- **产物文件**：`evidence-archive/branch-aab/app-release.aab`
- **文件大小**：`21,871,575` 字节
- **SHA-256**：`ACABC324946CF6F0947EE410AA67B4D045F84757654544B51E2332807BCF7A71`
- **判定**：包含完整 `BUNDLE-METADATA`（`baseline.prof` 与 `proguard.map`），适用于应用商店。

---

## 5. 局限与待核对事项（收窄说明）

1. **历史 0.1.x 升级链与旧证书归属（待核对）**：
   - 当前仓库无 0.1.x 原生 Android 历史 APK 实体或签名证书指纹；
   - 历史 0.1.x 实为旧 Capacitor 混合架构，**不能从 Capacitor 归档推断当前原生应用是否能与 0.1.x 保持相同包名覆盖安装**；
   - 结论标记为：**升级链与证书归属待产品及发布管理者依据历史真实 release 原件核对**。
2. **构建通过不等于运行正常**：
   - 本次仅验证了静态编译与打包；R8 混淆后的 Release APK/AAB 尚未在真实设备或 MuMu 上运行整套功能与无障碍回归测试；
   - 不能排除混淆引发的潜在运行时问题。
3. **既有运行时缺陷依然存在**：
   - 本轮 N1 发现真实进程中断下整包恢复原子性不支持（K3/K4/K5 现场 DB 升级但文件缺失、F1 孤儿解压缓存、F2 torn staging 残留）；
   - N3 审查指出 SyncRepository 在 applyBook/applyProgress 时新建实体覆写本地事实、WebDavBackup HEAD 探测 405 不兼容等数据缺陷；
   - 上述问题独立于 Release 打包流水线，但属于发布前必须解决的核心缺陷。

---

## 6. 最终结论

1. 原生 Android 的 Release 构建配置、多分支行为控制、DEX 级测试类隔离及 AAB 打包能力**在构建层面验证完成，且有独立归档证据支持**；
2. 产物当前明确保持为**未签名（unsigned）状态，不可正式分发**；
3. 后续正式发布仍需解决：正式签名凭据注入、历史证书升级链核对、R8 混淆包真机运行回归以及运行时已知数据缺陷的收口修复。
