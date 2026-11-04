# 原生 Android Release 构建证据补正与结论收窄报告 (R3)

> **任务标识**：下一轮任务 R3 — 补正 N2 Release 报告，实现每项结论与实际产物、原始日志及 DEX 证据精确对齐  
> **响应回读**：[`docs/coordination/reviews/2026-10/2026-10-01-next-n1-n3-codex-review.md`](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-n1-n3-codex-review.md) §N2  
> **基线提交**：`e39a78da9c62f02614207cffb36969a32492eaea`  
> **独占目录**：`E:\creation-reading-assistant\next-round-n2\android`  
> **证据归档目录**：`E:\creation-reading-assistant\next-round-n2\evidence-archive\`  
> **生成时间**：2026-10-01  
> **设备与产品源码约束**：不操作 MuMu，不安装正式包，不生成正式密钥，不 tag/push/发布，不改动共享仓库产品源码与桌面代码。

---

## 1. 补正总览与问题对照

依据总指挥在 `2026-10-01-next-n1-n3-codex-review.md` 中指出的具体问题，本轮 R3 逐项完成了针对性的现场实测、日志归档与报告收窄：

| 编号 | 总指挥回读问题 / 任务指示 | R3 补正动作与现场证据 | 状态 |
| :---: | :--- | :--- | :---: |
| **1** | 基线提交说明误写为交接文字，与 git 实际不符 | 运行 `git log -1` 确证实际提交说明为 `fix(android): integrate verified recovery candidate and reader state safety`，在报告中全面勘误 | **已纠正** |
| **2** | 报告所列 debug 签名 `app-release.apk` 现场不存在，无法复核 SHA | 重新构建显式 debug fallback 分支，产物已固定并归档，SHA 确认为 `0126988CB55...`，与原报告完全一致 | **已归档** |
| **3** | unsigned、debug fallback、CI 缺签名拒绝各分支需各自保留证据 | 建立 `evidence-archive/` 四个独立子目录，分别固化命令、完整构建日志、退出码和产物 | **已固化** |
| **4** | `aapt list` 仅为 ZIP 条目，不能证明 DEX 中无测试类 | 使用 `apkanalyzer.bat dex packages` 导出并穷尽分析了两个 DEX 的全部 10,301 个类、643 个包，确证测试 Runner、Activity、测试源码包均 0 命中 | **实测证明** |
| **5** | 固定每个产物的 SHA 与元数据，不混用不同构建结果 | 针对 3 个物理产物与 1 个 CI 拒绝凭据建立独立元数据卡片，独立核验包名、版本、debuggable、签名状态 | **独立核验** |
| **6** | 删除或收窄“唯一发布阻塞”“100%达标”“Release-Ready” | 删除一切绝对化表述；收窄为“构建配置通过，构建阶段就绪”，明确指出 R8 运行期未在设备回归、存在既有缺陷、升级链待核对 | **已收窄** |
| **7** | 历史 0.1.x 是否同包同证书必须有实际证据，无法确认写待核对 | 确认当前无 0.1.x 原生 APK 原件，旧版为已废弃 Capacitor，不能推导原生升级链；标注为“待核对” | **已标注** |
| **8** | 保留当前最小签名配置补丁，不扩展多余体系 | 隔离目录下仅保留 15 行签名回退控制补丁，未扩展任何其他逻辑 | **已保持** |

---

## 2. 逐项补正事实与原始证据

### 2.1 补正项 1：基线提交说明勘误
- **实测命令**：`git -C D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919 log -1 e39a78da9c62f02614207cffb36969a32492eaea`
- **Git 原始输出**：
  ```text
  commit e39a78da9c62f02614207cffb36969a32492eaea
  Author: luoshuizhiwei <2325495253@qq.com>
  Date:   Thu Oct 1 03:05:23 2026 +0800

      fix(android): integrate verified recovery candidate and reader state safety
  ```
- **勘误说明**：原 N2 报告误将交接文档总结段落当做提交说明，现已在原 N2 报告及本文中正式勘误为 `fix(android): integrate verified recovery candidate and reader state safety`。

---

### 2.2 补正项 2 & 3：多构建分支实体留存与独立证据

所有构建日志、退出码和产物已在独占 E 盘归档目录持久保存，不再由后续 Gradle clean 或覆盖操作丢失：

#### A. 默认无签名 Release APK 分支（主发布产物）
- **构建命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:ANDROID_HOME = "D:\develop\Android\Sdk"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-n2\gradle-cache"
  Remove-Item env:GITHUB_ACTIONS -ErrorAction SilentlyContinue
  .\gradlew.bat :app:assembleRelease --offline --console=plain > E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-unsigned\build.log 2>&1
  ```
- **退出码**：`0`（记录于 `branch-unsigned/exitcode.txt`）
- **产物归档**：`E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-unsigned\app-release-unsigned.apk`
- **文件大小**：`26,636,786` 字节
- **SHA-256**：`AF49CC390FFA7D2D1EE4ACF97BC2C6797C62A506BA64BE52BD618656A7809C7D`
- **`apksigner verify` 日志**（`branch-unsigned/apksigner-verify.log`）：
  ```text
  DOES NOT VERIFY
  ERROR: Missing META-INF/MANIFEST.MF
  (退出码 1)
  ```
- **判定**：标准无签名 Release 包，确立未签名不可正式分发。

#### B. 显式 Debug 兜底签名 Release APK 分支（本地验证专用）
- **构建命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:ANDROID_HOME = "D:\develop\Android\Sdk"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-n2\gradle-cache"
  Remove-Item env:GITHUB_ACTIONS -ErrorAction SilentlyContinue
  .\gradlew.bat :app:assembleRelease --offline --console=plain -PcraAllowDebugSigningFallback=true > E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-debugfallback\build.log 2>&1
  ```
- **退出码**：`0`（记录于 `branch-debugfallback/exitcode.txt`）
- **产物归档**：`E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-debugfallback\app-release.apk`
- **文件大小**：`26,649,074` 字节
- **SHA-256**：`0126988CB55B7A923C973824D65A99FD80DE09C2920655BC22499BFC7A0D8CEF`
- **`apksigner verify --verbose --print-certs` 日志**（`branch-debugfallback/apksigner-verify.log`）：
  ```text
  Verifies
  Verified using v2 scheme (APK Signature Scheme v2): true
  Number of signers: 1
  Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
  Signer #1 certificate SHA-256 digest: 05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1
  ```
- **判定**：实测现场重新构建并归档成功，SHA-256 与原报告完全吻合，确认为 Android Debug 证书，仅供本地验证，绝不可发布。

#### C. CI 缺签名拒绝分支（防护守卫）
- **构建命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:ANDROID_HOME = "D:\develop\Android\Sdk"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-n2\gradle-cache"
  $env:GITHUB_ACTIONS = "true"
  Remove-Item env:CRA_ANDROID_KEYSTORE_* -ErrorAction SilentlyContinue
  .\gradlew.bat :app:assembleRelease --offline --console=plain > E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-ci-rejection\build.log 2>&1
  ```
- **退出码**：`1`（构建明确失败，记录于 `branch-ci-rejection/exitcode.txt`）
- **构建日志定位**（`branch-ci-rejection/build.log`）：
  ```text
  FAILURE: Build failed with an exception.
  * Where:
  Build file 'E:\creation-reading-assistant\next-round-n2\android\app\build.gradle.kts' line: 97
  * What went wrong:
  CI 环境缺少 Android 发布签名（CRA_ANDROID_KEYSTORE_* 四个变量必须齐全），已中止 release 构建。
  BUILD FAILED in 2s
  ```
- **判定**：CI 阻断逻辑有效，无法在缺少签名时产出假冒包。

#### D. Release App Bundle (AAB) 分支
- **构建命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:ANDROID_HOME = "D:\develop\Android\Sdk"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-n2\gradle-cache"
  .\gradlew.bat :app:bundleRelease --offline --console=plain > E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-aab\build.log 2>&1
  ```
- **退出码**：`0`（记录于 `branch-aab/exitcode.txt`）
- **产物归档**：`E:\creation-reading-assistant\next-round-n2\evidence-archive\branch-aab\app-release.aab`
- **文件大小**：`21,871,575` 字节
- **SHA-256**：`ACABC324946CF6F0947EE410AA67B4D045F84757654544B51E2332807BCF7A71`

---

### 2.3 补正项 4：DEX 级测试类隔离实测证据

前版报告仅依赖 `aapt list`（ZIP 目录结构条目），无法排除测试代码被编译进 DEX。本轮在离线环境下调用 Android SDK 命令行工具 `apkanalyzer.bat dex packages` 针对 `app-release-unsigned.apk` 的 DEX 进行了穷尽反解：

1. **DEX 构成**：
   - `classes.dex`: 8,304,644 字节
   - `classes2.dex`: 279,956 字节
2. **全量类与包导出**：
   - 导出文件：`E:\creation-reading-assistant\next-round-n2\dex-inspection\apk-classes-and-methods.txt`（90,097 行，涵盖全部 Class / Method 定义）
   - 统计总量：10,301 个类定义，643 个唯一包定义。
3. **特定测试组件逐项搜索结果**：
   - `CraIsolatedTestRunner`: **0 命中** (`[]`)
   - `BenchmarkSeedActivity`: **0 命中** (`[]`)
   - `com.creationreadingassistant.testing`（测试支持包）: **0 命中** (`[]`)
   - `junit.*`（测试框架）: **0 命中** (`0`)
   - `androidx.test.*`（AndroidX 测试库）: **0 命中** (`0`)
   - `com.creationreadingassistant.*Test*`: **0 命中** (`[]`)
4. **合并 Manifest 审查**：
   - 审查文件：`app/build/outputs/logs/manifest-merger-release-report.txt`
   - 检索 `BenchmarkSeedActivity`、`CraIsolatedTestRunner`、`TestActivity`、`androidx.test` 均 **0 命中**。
5. **结论**：
   - **实测证据支持**测试类、测试 Runner 和测试组件未混入 Release DEX。

---

### 2.4 补正项 5：固定每个被检查产物的独立元数据卡片

| 产物与分支 | 文件路径与归档 | 大小 (Bytes) | SHA-256 校验和 | Package / Version | debuggable / testOnly | 签名校验结论 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **未签名 Release APK** | `evidence-archive/branch-unsigned/app-release-unsigned.apk` | 26,636,786 | `AF49CC390FFA7D2D1EE4ACF97BC2C6797C62A506BA64BE52BD618656A7809C7D` | `com.creationreadingassistant`<br>`versionCode=3, versionName=0.5.1` | debuggable: 无<br>testOnly: 无 | `DOES NOT VERIFY`<br>(未签名，Missing MANIFEST.MF) |
| **Debug 签名 Release APK** | `evidence-archive/branch-debugfallback/app-release.apk` | 26,649,074 | `0126988CB55B7A923C973824D65A99FD80DE09C2920655BC22499BFC7A0D8CEF` | `com.creationreadingassistant`<br>`versionCode=3, versionName=0.5.1` | debuggable: 无<br>testOnly: 无 | `Verifies: true`<br>证书: `CN=Android Debug` |
| **Release App Bundle** | `evidence-archive/branch-aab/app-release.aab` | 21,871,575 | `ACABC324946CF6F0947EE410AA67B4D045F84757654544B51E2332807BCF7A71` | `com.creationreadingassistant`<br>`versionCode=3, versionName=0.5.1` | debuggable: 无<br>testOnly: 无 | N/A (集成 baseline.prof 与 proguard.map) |

---

### 2.5 补正项 6 & 7：结论收窄与待核对事项

1. **收窄判定（删除“Release-Ready”与“100% 发布达标”）**：
   - 构建成功（Exit 0）仅代表静态语法、依赖闭包、清单合并与 R8 优化过程无编译期错误；
   - **不代表 R8 混淆后的产物在设备端运行时功能完好**：R8 激进剥除死代码或重命名可能引发未被 ProGuard 规则捕获的运行时反射异常、序列化异常或跨模块调用崩溃，这必须依赖在实体机或模拟器上的全量回归测试；
   - **不代表既有缺陷已解决**：本轮 N1 已证实整包数据恢复在真实 kill 下原子性不支持，N3 证实 Sync 同步新建实体覆写本地事实、WebDAV 405 不兼容等数据缺陷均仍在排期修复中；
   - 因此，原报告中的“Release-Ready”和“100% 达标”等过度推断已全部删除，结论严格收窄为**“Release 构建配置与静态产物输出验证完成，达到构建就绪阶段”**。
2. **历史 0.1.x 升级链事实厘清**：
   - 历史 `0.1.x` 发行版为 Capacitor 混合架构，当前 `android/` 为独立原生 Android 重构；
   - 恢复仓库中既无 0.1.x 原生 APK 实体，亦无历史签名证书原件或权威 SHA-256 记录；
   - **无法直接判定当前原生应用是否与 0.1.x 兼容同包同证书覆盖安装**；
   - 结论标注为：**待产品与发布管理者调阅历史公开发布原件与旧密钥资产核对**，不可从已废弃的 Capacitor 归档推导。

---

### 2.6 补正项 8：保留最小签名配置补丁

在独占目录 `E:\creation-reading-assistant\next-round-n2\android\app\build.gradle.kts` 中仅保留 15 行最小修改补丁（与仓库基线对比）：
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
未修改任何运行时 Kotlin 代码、数据库 schema、测试文件或根 `package.json`。

---

## 3. 最终核验结论

1. **产物真实可复核**：`evidence-archive/` 完整保留了默认 unsigned APK、显式 debug fallback APK、AAB 及其对应的构建日志与退出码；
2. **DEX 隔离已证实**：`apkanalyzer` 实测确证 10,301 个类中无任何测试类与测试框架依赖；
3. **边界明确收窄**：本报告仅确认静态构建流水线各分支行为正确，明确指出产物未签名不可分发，不为运行时兼容性、历史升级链或未解决业务缺陷背书；
4. **共享工作区完好**：除更新原 N2 报告及新增本文档外，共享仓库无未授权的 commit、push、reset 或文件修改。
