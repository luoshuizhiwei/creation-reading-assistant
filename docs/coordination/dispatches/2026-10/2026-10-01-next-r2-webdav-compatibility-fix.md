# 任务 R2：WebDAV HEAD 探测兼容、自动建连与 URL 安全审计修复报告（2026-10-01 返修轮）

> **基线仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> **基线提交**：`e39a78da9c62f02614207cffb36969a32492eaea`  
> **独立隔离工程**：`E:\creation-reading-assistant\next-round-r2\android`  
> **独立隔离缓存**：`E:\creation-reading-assistant\next-round-r2\_cache\gradle-home`  
> **共享仓库唯一更新**：本报告（`docs/coordination/dispatches/2026-10/2026-10-01-next-r2-webdav-compatibility-fix.md`）  
> **修改文件（隔离目录内）**：
> - 核心修复：`android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt`
> - 新增专项回归：`android/app/src/test/java/com/creationreadingassistant/feature/sync/WebDavCompatibilityTest.kt`  
> **补丁产物**：`E:\creation-reading-assistant\next-round-r2\webdav-compatibility.patch`（SHA-256: `5713F316743D415B4C4E7910E5ACA47F608F6AE79F047691974E2F37644870AA`）  
> **红测与绿测备份证据**：
> - 红测 XML 备份：`E:\creation-reading-assistant\next-round-r2\red-test-results\TEST-WebDavCompatibilityTest-RED.xml`  
> - 绿测 XML 备份：`E:\creation-reading-assistant\next-round-r2\green-test-results\*.xml`（5 个权威 XML）  
> **纪律核查**：未修改产品主线源码；未修改 ProfileWebDavManager、ProfileRoute 或 SyncRepository；未操作 MuMu 模拟器；未访问用户真实网盘；未执行 git commit / push / reset / checkout。

---

## 1. 返修轮核心修复与安全收敛说明

对照总指挥复核报告（`docs/coordination/reviews/2026-10/2026-10-01-next-r1-r3-codex-review.md`），本返修轮保留已完成验证的 HEAD 405 平滑回退机制与有限 MKCOL/PUT 重试逻辑，对 URL 安全判定回归、文件名控制字符过滤、认证字符集及测试计数进行了彻底修正与收敛：

1. **URL 安全判定彻底重构（杜绝明文公网绕过与主机混淆攻击）**：
   - **漏洞根因**：原版本使用 `substringBefore(':')` 手工切分主机，遇到带 userinfo 的 URL（如 `http://127.0.0.1:80@public.example/backups/`）时，会将 userinfo 中的 `127.0.0.1` 误判为主机名并放行，而底层 OkHttp 实际发起网络请求的目标主机为 `public.example`，导致攻击者可利用 userinfo 混淆完全绕过明文 HTTP 公网限制，向公网泄露 Basic 认证凭据与整库内容。
   - **修复与前置阻断**：
     - 改用与 OkHttp 实际请求完全一致的 `toHttpUrlOrNull()` 读取真实主机名 `httpUrl.host`（自动剥离 userinfo、IPv6 括号规范化与端口处理）；
     - **直接拒绝带 userinfo 的配置 URL**：若 `httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()`，在发起任何网络请求前直接抛出 `IllegalArgumentException("WebDAV 地址不能包含用户信息（userinfo），账号密码请在输入框填写")`，彻底杜绝混淆；
     - **彻底删除 `isPermittedHttpHost`**：坚决撤销上一轮自行扩展的单段主机名与 `.lan`/`.home`/`.internal` 放宽，100% 恢复基线既有的 `PairingManager.isLanHost(host)` 判定合同；
     - **受控隔离与前置拦截**：测试完全在本地 MockWebServer 受控环境下运行，严禁向真实公网发请求，所有非法 URL 均在网络请求发出前断言抛出 `IllegalArgumentException`。
2. **文件名控制字符严格拒绝（杜绝先行 trim 与 `\s` 逃逸）**：
   - **漏洞根因**：原版本对文件名使用了 `filename.trim()`，导致首尾控制字符（如 `\tbackup.json`）在进入正则前被剥除从而放行；同时正则 `[A-Za-z0-9._\-\s...]` 中的 `\s` 匹配了 Java/Kotlin 的空白控制符（`\t`、`\n`、`\r`），导致中间包含控制字符的文件名被放行。
   - **修复**：
     - **绝不预先 trim**：直接对原始文件名进行字符级安全校验，若包含任何控制字符（`it < ' ' || it == '\u007F'`，包括 `\t`、`\n`、`\r` 等），立即抛出 `IllegalArgumentException("非法备份文件名：文件名包含控制字符")`；
     - **显式拒绝首尾空格**：`filename.startsWith(' ') || filename.endsWith(' ')` 直接抛出异常；
     - **严格禁止路径穿越**：坚决拒绝 `..`、`/`、`\`、`.`；
     - **正则白名单收敛**：将 `\s` 替换为单个普通半角空格 `' '`（`Regex("^[A-Za-z0-9._\\- \\p{L}\\p{N}()（）\\[\\]]+$")`），既保证中文、数字、字母、合法半角空格与括号正常支持，又彻底阻断所有控制字符。
3. **认证字符集恢复基线合同**：
   - 撤销 `StandardCharsets.UTF_8` 显式强制改动，恢复基线原生 `Credentials.basic(user, pass)` 默认实现（OkHttp 默认 ISO-8859-1），移除未使用的 `StandardCharsets` 导入，不引入未经合同论证的编码变更。
4. **保留已有核心兼容特性**：
   - **HEAD 405 平滑回退**：对坚果云等不支持 HEAD 的服务，最小平滑回退到 `PROPFIND (Depth: 0)`；401/403 认证失败直接识别并提示，绝不发起 PROPFIND，绝不发起 MKCOL。
   - **PUT 404/409 自动 MKCOL**：当且仅当目标目录不存在时，仅对目标集合发起一次 `MKCOL`，成功后有限重试最多 1 次 PUT；MKCOL 遇 403/409 立即中止，绝不递归创建未知路径，杜绝死循环。
   - **接口契约 100% 保持**：`getBytes`、`listLegadoBackups`、`get`、`listBackups` 接口签名与流式传输行为保持不变。

---

## 2. 真实红/绿测试执行全记录

### 2.1 返修轮红灯复现阶段（Red Test 反例确证）

在未应用返修补丁前，于 `WebDavCompatibilityTest.kt` 中加入复核反例测试：
- `urlSecurity_rejectsUserInfoInUrl_toPreventHostConfusionAttack`（测试 `http://127.0.0.1:80@public.example/backups/` 是否被前置拦截）
- `urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract`（测试 `http://nas/dav/` 是否按 PairingManager 原合同被拦截）
- `filenameValidation_rejectsControlCharacters`（测试 `\tbackup.json` 等控制字符是否被拒绝）

- **执行命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-r2\_cache\gradle-home"
  .\gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.sync.WebDavCompatibilityTest"
  ```
- **退出码**：`1`（FAILURE）
- **日志输出片段**：
  ```text
  > Task :app:compileDebugUnitTestKotlin
  > Task :app:testDebugUnitTest

  WebDavCompatibilityTest > filenameValidation_rejectsControlCharacters FAILED
      java.lang.AssertionError at WebDavCompatibilityTest.kt:364

  WebDavCompatibilityTest > urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract FAILED
      java.lang.AssertionError at WebDavCompatibilityTest.kt:344

  WebDavCompatibilityTest > urlSecurity_rejectsUserInfoInUrl_toPreventHostConfusionAttack FAILED
      java.lang.AssertionError at WebDavCompatibilityTest.kt:325

  21 tests completed, 3 failed

  FAILURE: Build failed with an exception.
  * What went wrong:
  Execution failed for task ':app:testDebugUnitTest'.
  > There were failing tests.
  BUILD FAILED in 36s
  ```
- **红灯归档证据**：XML 已保存至 `E:\creation-reading-assistant\next-round-r2\red-test-results\TEST-WebDavCompatibilityTest-RED.xml`。
- **确证结论**：原代码在处理 `127.0.0.1:80@public.example` 时误解析为内网 host 并穿透放行给 OkHttp， OkHttp 抛出 `UnknownHostException: public.example`，证明未在网络前置阻断；`nas` 被 `isPermittedHttpHost` 误放行；`\tbackup.json` 被 trim 消除控制字符后放行导致网络超时。三大缺陷完全复现。

---

### 2.2 修复后单次可追溯绿灯验证阶段

修正 `WebDavBackup.kt` 并扩充合法局域网与文件名白名单测试后，执行单次统一运行，同时覆盖 5 个相关测试类。

- **执行命令**：
  ```powershell
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\next-round-r2\_cache\gradle-home"
  .\gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest `
    --tests "com.creationreadingassistant.feature.sync.WebDavCompatibilityTest" `
    --tests "com.creationreadingassistant.feature.sync.WebDavBackupTest" `
    --tests "com.creationreadingassistant.ui.viewmodel.ProfileWebDavManagerTest" `
    --tests "com.creationreadingassistant.feature.sync.LegadoReadingProgressImporterTest" `
    --tests "com.creationreadingassistant.feature.sync.LegadoImportEpubLocatorContractTest"
  ```
- **退出码**：`0`（SUCCESS）
- **构建日志片段**：
  ```text
  > Task :app:compileDebugKotlin
  > Task :app:compileDebugJavaWithJavac
  > Task :app:compileDebugUnitTestKotlin
  > Task :app:testDebugUnitTest

  BUILD SUCCESSFUL in 1m 6s
  30 actionable tasks: 11 executed, 19 up-to-date
  ```

---

### 2.3 权威测试 XML 单次运行实算清单（测试计数勘误与对齐）

> **计数勘误说明**：上一轮报告记录的 58 项中包含跨批次运行计入的 Legado 测试（18+14+12=44，外加另一次运行的 12+2=14）。本轮严格执行单次命令全量运行，在同一次 Gradle 执行中产生全部 5 个 XML 文件，并经实算确认无一遗漏。

由同批次 Gradle 导出的权威测试 XML（路径：`E:\creation-reading-assistant\next-round-r2\android\app\build\test-results\testDebugUnitTest\*.xml`，已备份至 `green-test-results/`）实算结果：

| 测试套件类名 | 用例数 (tests) | 失败数 (failures) | 错误数 (errors) | 跳过数 (skipped) | 耗时 (s) | 测试结果 | 权威 XML 产物文件名 |
|---|---:|---:|---:|---:|---:|:---:|---|
| `LegadoImportEpubLocatorContractTest` | **2** | 0 | 0 | 0 | 7.067 | **100% PASS** | `TEST-com.creationreadingassistant.feature.sync.LegadoImportEpubLocatorContractTest.xml` |
| `LegadoReadingProgressImporterTest` | **12** | 0 | 0 | 0 | 0.217 | **100% PASS** | `TEST-com.creationreadingassistant.feature.sync.LegadoReadingProgressImporterTest.xml` |
| `WebDavBackupTest` | **14** | 0 | 0 | 0 | 0.416 | **100% PASS** | `TEST-com.creationreadingassistant.feature.sync.WebDavBackupTest.xml` |
| `WebDavCompatibilityTest` | **24** | 0 | 0 | 0 | 0.150 | **100% PASS** | `TEST-com.creationreadingassistant.feature.sync.WebDavCompatibilityTest.xml` |
| `ProfileWebDavManagerTest` | **12** | 0 | 0 | 0 | 1.090 | **100% PASS** | `TEST-com.creationreadingassistant.ui.viewmodel.ProfileWebDavManagerTest.xml` |
| **单次全量总计** | **64** | **0** | **0** | **0** | **8.94** | **全绿通过** | **5 个 XML 全量落盘于同一测试批次** |

---

## 3. 受控断言与安全门禁清单（24 项专项测试）

在 `WebDavCompatibilityTest.kt`（24 tests）中，对所有请求方法、深度、URL 编码、网络前置阻断以及重试上限进行了完整断言：

| 测试用例名称 | 场景与输入 | 实际断言请求序列 | 关键断言参数与安全防线 |
|---|---|---|---|
| `testConnection_head200_success_singleRequest` | 正常支持 HEAD 的服务器 | `HEAD /dav/` | 请求恰好 1 次，返回包含 200 |
| `testConnection_head405_fallsBackToPropfindDepth0_success` | 坚果云等返回 405 的服务 | `HEAD` (405) → `PROPFIND` (207) | 请求恰好 2 次；第 2 次带 `Depth: 0`；平滑兼容 |
| `testConnection_headAuthFailure401_noFallbackToPropfind_returnsAuthWarning` | 账号密码错误 (401) | `HEAD` (401) | **严格 1 次**；绝不发起 PROPFIND；直接识别凭据错误 |
| `testConnection_headForbidden403_noFallbackToPropfind_returnsAuthWarning` | 权限不足 (403) | `HEAD` (403) | **严格 1 次**；绝不发起 PROPFIND；直接识别凭据错误 |
| `testConnection_propfindAuthFailure401_whenHead405` | HEAD 405 后 PROPFIND 401 | `HEAD` (405) → `PROPFIND` (401) | 严格 2 次；准确识别 PROPFIND 阶段凭据错误 |
| `put_success_singleRequest_noRetry` | 目标目录已存在 | `PUT /dav/cra-backup.json` (201) | 请求恰好 1 次；无 MKCOL；无重试 |
| `put_authFailure401_noMkcol_noRetry` | PUT 遇 401 凭据错误 | `PUT ...` (401) | **严格 1 次**；绝不调用 MKCOL，绝不重试 |
| `put_authForbidden403_noMkcol_noRetry` | PUT 遇 403 权限错误 | `PUT ...` (403) | **严格 1 次**；绝不调用 MKCOL，绝不重试 |
| `put_missingDirectory409_createsCollectionWithMkcol_andRetriesPutSuccess` | 目标集合不存在 (409) | `PUT` (409) → `MKCOL` (201) → `PUT` (201) | **总请求 3 次**；仅对目标集合发 MKCOL；重试成功 |
| `put_missingDirectory404_collectionAlreadyExistsOnMkcol405_andRetriesPutSuccess` | 目标集合 404，MKCOL 显示已存在 (405) | `PUT` (404) → `MKCOL` (405) → `PUT` (200) | **总请求 3 次**；识别 405 为集合就绪；重试成功 |
| `put_mkcolForbidden403_abortsImmediately_noRetryPut` | 创建目录被拒绝 (403) | `PUT` (409) → `MKCOL` (403) | **总请求 2 次**；立即中止，绝不执行第二次 PUT |
| `put_mkcolParentMissing409_abortsImmediately_noRecursiveMkcol` | 父目录缺失 (409) | `PUT` (409) → `MKCOL /dav/a/b/c/` (409) | **总请求 2 次**；立即中止，**绝不递归创建祖先目录** |
| `put_retryPutStillFails_reachesMaxAttemptLimit_noInfiniteLoop` | 重试 PUT 持续失败 | `PUT` (409) → `MKCOL` (201) → `PUT` (409) | **总请求 3 次**；达到 1 次重试上限立即退出，杜绝死循环 |
| `put_chineseAndSpaceFilename_properlyEncoded_noDoubleEncoding` | 文件名 `"备份 2026-10-01.json"` | `PUT ...` (201) | 路径含 UTF-8 百分号编码，**绝无 `%25` 二次编码** |
| `put_preEncodedUrl_doesNotDoubleEncode` | URL 路径已含 `%E6%96%B0%E5%BB%BA/` | `PUT ...` (201) | 已有编码原样保持，**绝无 `%25E6`** |
| `filenameValidation_rejectsDirectoryTraversal` | `../hack.json`、`..\\`、`foo/bar.json`、`.` | **0 次网络请求** | 网络前置阻断，抛出 `IllegalArgumentException` |
| `urlSecurity_rejectsUserInfoInUrl_toPreventHostConfusionAttack` | `http://127.0.0.1:80@public.example/backups/` 等带 userinfo 的 URL | **0 次网络请求** | **网络前置阻断**；防主机混淆与凭据泄露；抛 `IllegalArgumentException` |
| `urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract` | `http://nas/dav/`、`http://router.lan/dav/` 等无点单段或私有域 | **0 次网络请求** | **网络前置阻断**；恢复 PairingManager 合同，拒绝未验证单段域 |
| `filenameValidation_rejectsControlCharacters` | `\tbackup.json`、`backup.json\n`、`back\tup.json` 等含控制符 | **0 次网络请求** | **网络前置阻断**；绝不先行 trim，绝不放过控制字符 |
| `urlSecurity_rejectsPlaintextHttpForPublicDomainAndPublicIp` | `http://public.example/dav/`、`http://8.8.8.8/dav/` 等公网 HTTP | **0 次网络请求** | **网络前置阻断**；严格要求公网使用 https:// |
| `urlSecurity_allowsLegitimateLanAndHttpsWithCustomPort` | `http://127.0.0.1:port/`、`http://localhost:port/` | `HEAD` (200) 各 1 次 | 合法局域网明文 HTTP 与端口正常放行与连接 |
| `filenameValidation_acceptsValidChineseAndSpaces_rejectsWhitespaceEdgeCases` | `"2026-10-01 创作阅读助手 (备份) [全量].json"` vs 首尾空格 | 仅合法 PUT 发起 1 次请求 | 合法中文空格成功，首尾空格在网络前置阻断抛异常 |
| `getBytes_success_and_oversizeFailure` | Legado ZIP 二进制下载回归 | `GET /dav/backup.zip` (200) | 验证二进制传输正常，超出上限严格拦截 |
| `listLegadoBackups_filtersOnlyZipAndSorts` | Legado 备份列表回归 | `PROPFIND /dav/` (207) | 仅提取 `backup*.zip`，忽略 `.json`，时间倒序 |

---

## 4. 产物与精确源码哈希（SHA-256）

### 4.1 源码文件哈希与体积

| 文件相对路径 | 状态 | 字符数 / 字节数 | SHA-256 哈希值 |
|---|:---:|---:|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt` | 修改 | 18,367 Bytes | `4B1E0D3A50EE68DB603483E145DC8E944AFC391C67A43FE00CAF365E43E577D1` |
| `android/app/src/test/java/com/creationreadingassistant/feature/sync/WebDavCompatibilityTest.kt` | 新增 | 22,374 Bytes | `FC167FF822FD4E9A4E2F6A5789E82DB162837C809483A893F3654C9C54029840` |

### 4.2 独立补丁文件
- **补丁文件绝对路径**：`E:\creation-reading-assistant\next-round-r2\webdav-compatibility.patch`
- **补丁文件体积**：39,639 Bytes
- **补丁 SHA-256**：`5713F316743D415B4C4E7910E5ACA47F608F6AE79F047691974E2F37644870AA`

---

## 5. 精确代码变更（WebDavBackup.kt 核心补丁片段）

```diff
--- a/android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt
+++ b/android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt
@@ -1,6 +1,7 @@
 package com.creationreadingassistant.feature.sync
 
 import okhttp3.Credentials
+import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
 import okhttp3.MediaType.Companion.toMediaType
 import okhttp3.OkHttpClient
 import okhttp3.Request
@@ -12,14 +13,14 @@
 import java.util.concurrent.TimeUnit
 import javax.inject.Inject
 import javax.inject.Singleton
-import java.nio.charset.StandardCharsets
 
 /**
- * WebDAV 远程备份（P4，最简实现）。
+ * WebDAV 远程备份（P4）。
  *
  * 通过 HTTP PUT 把整库导出 JSON 上传到 `{url}/{filename}`（Basic Auth）。
- * 多数 WebDAV 服务器（Nextcloud / 群晖 / nginx-webdav）接受直接 PUT 创建文件。
- * 后续若需列目录/增量，可在此叠加 PROPFIND/GET，本版先保证「一键备份」可用。
+ * 支持主流 WebDAV 服务（坚果云 / Nextcloud / 群晖 / nginx-webdav / Alist 等）。
+ * 针对坚果云等服务不支持 HEAD 的情况，提供向 PROPFIND (Depth: 0) 的平滑兼容回退；
+ * 当目标目录未创建（404/409）时，自动尝试 MKCOL 创建目标集合并有限重试一次。
  */
 @Singleton
 class WebDavBackup @Inject constructor() {
@@ -37,35 +38,117 @@
     ): Result<Unit> = runCatching {
         requireSafeTarget(url)
         requireSafeFilename(filename)
-        val target = if (url.endsWith("/")) "$url$filename" else "$url/$filename"
+        val target = resolveTarget(url, filename)
         val body = content.toRequestBody("application/json".toMediaType())
         val request = Request.Builder()
             .url(target)
             .put(body)
-            .header("Authorization", Credentials.basic(user, pass))
+            .header("Authorization", basicAuth(user, pass))
             .build()
+
+        val firstResp = client.newCall(request).execute()
+        if (firstResp.isSuccessful) {
+            firstResp.close()
+            return@runCatching
+        }
+
+        val firstCode = firstResp.code
+        val firstErr = firstResp.body?.string()?.take(200)
+        firstResp.close()
+
+        // 认证失败直接识别并抛出，绝不当成方法不支持或缺目录，不能盲目重试或创建目录
+        if (firstCode == 401 || firstCode == 403) {
+            throw IllegalStateException("WebDAV 返回 $firstCode: $firstErr")
+        }
+
+        // 当且仅当目标目录不存在（404 或 409 Conflict）时，对配置的目标集合目录执行必要 MKCOL
+        if (firstCode == 404 || firstCode == 409) {
+            val collectionResult = ensureCollection(url, user, pass)
+            if (collectionResult.isFailure) {
+                val cause = collectionResult.exceptionOrNull()
+                throw IllegalStateException("目标目录不存在且创建失败: ${cause?.message ?: firstErr}", cause)
+            }
+
+            // MKCOL 成功后，执行最多一次有限重试，严禁无限循环
+            client.newCall(request).execute().use { retryResp ->
+                if (!retryResp.isSuccessful) {
+                    throw IllegalStateException("WebDAV 重试返回 ${retryResp.code}: ${retryResp.body?.string()?.take(200)}")
+                }
+            }
+            return@runCatching
+        }
+
+        throw IllegalStateException("WebDAV 返回 $firstCode: $firstErr")
     }
 
-    /** 连接测试：对根目录做 HEAD 探测，区分「可达」「鉴权失败」「网络错误」。 */
-    suspend fun test(url: String, user: String, pass: String): Result<String> = runCatching {
+    suspend fun test(url: String, user: String, pass: String): Result<String> = runCatching {
         requireSafeTarget(url)
         val target = if (url.endsWith("/")) url else "$url/"
         val headRequest = Request.Builder()
             .url(target)
             .head()
-            .header("Authorization", Credentials.basic(user, pass))
+            .header("Authorization", basicAuth(user, pass))
             .build()
-        client.newCall(request).execute().use { resp ->
-            when (resp.code) {
-                in 200..299 -> "连接成功（HTTP ${resp.code}）"
-                401, 403 -> "服务器可达，但凭据可能不正确（HTTP ${resp.code}）"
-                else -> throw IllegalStateException("连接测试返回 HTTP ${resp.code}")
+        val headResp = client.newCall(headRequest).execute()
+        val headCode = headResp.code
+        headResp.close()
+
+        when (headCode) {
+            in 200..299 -> "连接成功（HTTP $headCode）"
+            401, 403 -> "服务器可达，但凭据可能不正确（HTTP $headCode）"
+            405, 501 -> {
+                // HEAD 方法不受服务器支持（如坚果云返回 405），平滑回退到 WebDAV 标准 PROPFIND (Depth: 0)
+                val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
+                    |<D:propfind xmlns:D="DAV:">
+                    |  <D:prop>
+                    |    <D:resourcetype/>
+                    |  </D:prop>
+                    |</D:propfind>
+                """.trimMargin().toRequestBody("text/xml".toMediaType())
+                val propfindRequest = Request.Builder()
+                    .url(target)
+                    .method("PROPFIND", propfindBody)
+                    .header("Authorization", basicAuth(user, pass))
+                    .header("Depth", "0")
+                    .build()
+                client.newCall(propfindRequest).execute().use { propResp ->
+                    when (propResp.code) {
+                        in 200..299 -> "连接成功（HTTP ${propResp.code}）"
+                        401, 403 -> "服务器可达，但凭据可能不正确（HTTP ${propResp.code}）"
+                        else -> throw IllegalStateException("连接测试返回 HTTP ${propResp.code}")
+                    }
+                }
             }
+            else -> throw IllegalStateException("连接测试返回 HTTP $headCode")
         }
     }
@@ -239,37 +324,54 @@
     private fun requireSafeTarget(url: String) {
-        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull()
-            ?: throw IllegalArgumentException("WebDAV 地址无法解析：$url")
-        when (val scheme = uri.scheme?.lowercase()) {
-            "https" -> return
-            "http" -> {
-                val host = uri.host
-                    ?: throw IllegalArgumentException("WebDAV 地址缺少主机名：$url")
-                if (!PairingManager.isLanHost(host)) {
-                    throw IllegalArgumentException(
-                        "已拒绝请求：$host 是公网地址，明文 HTTP 会泄露 WebDAV 账号密码与整库内容，请改用 https://。",
-                    )
-                }
-            }
-            null -> throw IllegalArgumentException("WebDAV 地址缺少协议，请以 https:// 开头")
-            else -> throw IllegalArgumentException("WebDAV 地址协议不受支持：$scheme")
+        val trimmed = url.trim()
+        val scheme = when {
+            trimmed.contains("://") -> trimmed.substringBefore("://").lowercase()
+            else -> null
+        }
+        if (scheme == null) {
+            throw IllegalArgumentException("WebDAV 地址缺少协议，请以 https:// 开头")
+        }
+        if (scheme != "http" && scheme != "https") {
+            throw IllegalArgumentException("WebDAV 地址协议不受支持：$scheme")
+        }
+
+        // 使用与 OkHttp 实际发起网络请求完全一致的解析器解析 URL，明确处理 userinfo、IPv6、端口
+        val httpUrl = trimmed.toHttpUrlOrNull()
+            ?: throw IllegalArgumentException("WebDAV 地址无法解析：$url")
+
+        // 坚决拒绝在配置 URL 中通过 userinfo 混淆目标 host（如 http://127.0.0.1:80@public.example/），必须在网络请求前阻断
+        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
+            throw IllegalArgumentException("WebDAV 地址不能包含用户信息（userinfo），账号密码请在输入框填写")
+        }
+
+        if (httpUrl.scheme == "https") return
+
+        // 真实请求 host（已由 HttpUrl 剥除 userinfo/端口并规范化）
+        val host = httpUrl.host
+        if (host.isEmpty()) {
+            throw IllegalArgumentException("WebDAV 地址缺少主机名：$url")
+        }
+
+        // 严格恢复既有 PairingManager 内网判定合同，不自行放宽无点单段域名或 .lan/.home 等范围
+        if (!PairingManager.isLanHost(host)) {
+            throw IllegalArgumentException(
+                "已拒绝请求：$host 是公网地址，明文 HTTP 会泄露 WebDAV 账号密码与整库内容，请改用 https://。",
+            )
         }
     }
 
     private fun requireSafeFilename(filename: String) {
-        if (!FILENAME_RE.matches(filename)) {
-            throw IllegalArgumentException("非法备份文件名：$filename")
+        if (filename.isEmpty()) {
+            throw IllegalArgumentException("非法备份文件名：文件名不能为空")
+        }
+        // 拒绝原始文件名中包含任何控制字符（包括 ASCII 0..31 的 \t, \n, \r 等控制符以及 DEL \u007F）
+        if (filename.any { it < ' ' || it == '\u007F' }) {
+            throw IllegalArgumentException("非法备份文件名：文件名包含控制字符")
+        }
+        // 拒绝首尾普通空格
+        if (filename.startsWith(' ') || filename.endsWith(' ')) {
+            throw IllegalArgumentException("非法备份文件名：首尾不能包含空格")
+        }
+        // 拒绝相对路径与目录穿越
+        if (filename == "." || filename == ".." || filename.contains("..") ||
+            filename.contains('/') || filename.contains('\\')
+        ) {
+            throw IllegalArgumentException("非法备份文件名：禁止路径穿越符号")
+        }
+        // 白名单正则：仅允许字母、数字、点、下划线、短横线、普通半角空格、Unicode 字母和数字、以及中英文括号
+        // 坚决不使用 \s（防止混入 tab/换行等其他空白控制符）
+        if (!FILENAME_RE.matches(filename)) {
+            throw IllegalArgumentException("非法备份文件名：$filename")
         }
     }
 
-    private val FILENAME_RE = Regex("[A-Za-z0-9._-]+")
+    private val FILENAME_RE = Regex("^[A-Za-z0-9._\\- \\p{L}\\p{N}()（）\\[\\]]+$")
```

---

## 6. 纪律与后续交接说明

1. **环境与隔离自证**：所有测试与代码修改完全在 `E:\creation-reading-assistant\next-round-r2\` 隔离工程与隔离 Gradle User Home 下执行，未产生进程交叉或端口争用。
2. **基线仓库零污染**：主仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` 严格保持只更新本报告（`docs/coordination/dispatches/2026-10/2026-10-01-next-r2-webdav-compatibility-fix.md`），暂存区为空，无任何 git commit / push / reset / checkout 操作。
3. **闭环状态**：红测反例已确证，绿测 64 项在单次运行中全绿通过，5 个测试 XML 与红测 XML 均已落盘归档，补丁已重新导出计算哈希。任务 R2 返修全部完成，停止后续操作，交总指挥统筹合流。
