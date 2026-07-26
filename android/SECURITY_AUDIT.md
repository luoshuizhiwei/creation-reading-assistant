# 原生移动端（`android/`）安全审计与修复记录

> 审计时间：2026-07-26
> 审计范围：`android/`（Kotlin + Jetpack Compose 原生端）
> 审计方式：6 个方向并行审查（清单/构建、JS-原生边界、WebView、EPUB 解析、输入边界、存储与隐私），每条发现经独立对抗性复核，剔除误报后保留。
> **验证状态：已验证**。`:app:assembleDebug` BUILD SUCCESSFUL；新增的 7 个安全边界单测全部通过。
> 注意：单测**无法通过 Gradle 运行**，原因是项目路径含中文导致的既有环境问题，与本次改动无关（见文末「四、单测环境既有故障」）。

---

## 一、已修复

### 1. 配对二维码可指向任意公网主机（高危）

**问题**：`PairingManager.parseQr()` 接受任意字符串作为同步地址，`normalize()` 还会给无协议的输入强行补上 `http://`，全程没有任何主机校验。配对成功后本机会把**整个书库与创作数据**推送给对端。

因此一张伪造的二维码（贴在公共场所、混在截图里、或由他人发送）就足以让用户的全部书籍、笔记、灵感静默上传到攻击者的公网服务器，用户侧只会看到「已配对」。

**修复**：新增 `requireLanTarget()` + `isLanHost()`，只放行回环与私有网段：

- IPv4：`127/8`、`10/8`、`172.16/12`、`192.168/16`、`169.254/16`
- IPv6：`::1`、`fc00::/7`、`fe80::/10`
- 主机名：仅 `localhost` 与 mDNS 的 `*.local`

三条关键设计：

- **不做 DNS 解析**。解析既会引入主线程网络调用，也会让攻击者用一个解析到内网地址的域名绕过校验（DNS rebinding）。因此非 IP 主机名一律拒绝。
- **拒绝前导零写法**（`010.0.0.1`、`127.0.0.01`）。不同解析器对前导零有十进制/八进制两种解释，留着就意味着「本地判定为内网、实际连到别处」的绕过空间。同理拒绝 `0x7f.0.0.1`、`127.1`、`2130706433` 等非规范形态。
- **用 `URI.getHost()` 取主机**，而不是自己切字符串。这样 `http://192.168.1.5@evil.com/` 会被正确识别为 `evil.com` 并拒绝 —— 这是经典的 userinfo 混淆绕过。

同时修正了一处会让防护失效的结构问题：JSON 分支原本把 `normalize()` 调用包在 `runCatching{}` 里，拒绝异常会被 catch 吞掉并悄悄落到后续分支。现已把校验移出 `runCatching`。

### 2. EPUB 图片抽取存在路径穿越（高危，Zip Slip 变体）

**问题**：`EpubParser.extractImage()` 用 `src.substringAfterLast('.', "png")` 当扩展名，而 `src` 来自书内 `<img>` 标签，是不可信输入。形如

```
<img src="cover.pn/../../../../databases/app.db">
```

的 `src`，其「最后一个点之后」的部分会带上 `/..` 片段，使写入路径逃出图片缓存目录，从而以书内任意内容**改写应用私有文件**（如 Room 数据库）。一本恶意 EPUB 即可触发。

**修复**：扩展名限制为「≤5 位纯字母数字」，不满足则回落 `png`；并追加规范化路径断言，确保最终写入路径仍在缓存目录内。

### 3. WebDAV 凭据与整库导出可经明文 HTTP 发往公网（中危）

**问题**：`WebDavBackup` 的四个入口（`put`/`test`/`get`/`listBackups`）都用 `Credentials.basic()` 发送账号密码（Basic Auth 只是 Base64，等同明文），目标地址完全由用户输入，且应用全局放行 cleartext。用户填一个 `http://` 公网地址，路径上任何人都能拿到 WebDAV 密码和全部书库内容。

**修复**：新增 `requireSafeTarget()`，四个入口全部接入。公网地址强制 `https`；局域网自建服务（群晖、nginx-webdav）仍允许 `http`，复用与配对同一套内网判定。

### 4. 加密存储失败静默降级为明文（中危，3 处）

**问题**：`SyncConfigStore`、`WebDavConfigStore`、`SettingsStore`（AI API Key）都是同一模式 —— `catch (e: Throwable)` 后直接回退普通 `SharedPreferences`，只打一行 `Log.e`。于是在 AndroidKeyStore 受限的设备上，**同步 token、WebDAV 密码、AI API Key 全部明文落盘**，用户完全无感。

**修复**：抽出统一入口 `data/security/SecurePrefs.kt`，改为三级策略：

1. 正常创建加密存储；
2. 失败时删除可能损坏的 prefs 后**重试一次** —— 主密钥轮换/失效、文件截断属可自愈情况，重建即可继续加密（代价是该项配置需重填，而那些数据本来也已读不出来）；
3. 仍失败才回退明文，并把 prefs 名记入 `SecurePrefs.degraded`，供设置页向用户明确告警。

> 待接：UI 尚未消费 `SecurePrefs.hasDegraded`。建议在「我的 → 同步/AI/WebDAV」页加一条警示，否则第 3 级仍是静默的。

### 5. FileProvider 暴露面过宽（中危）

**问题**：`file_paths.xml` 用 `path="."` 同时暴露 `files-path`、`external-files-path`、`cache-path` 三个根目录，等于让 Room 数据库、缓存的书籍原文都落在可被 `content://` 寻址的范围内。而全项目只有一处 `getUriForFile` —— 导出诊断日志。

**修复**：诊断日志改写入 `cacheDir/diagnostics/`；`file_paths.xml` 收窄为仅该子目录一条。

### 6. 备份未排除凭据存储（中危）

**问题**：`allowBackup="true"` 且未声明任何排除规则，凭据 prefs 会进入云备份与换机直传。正常情况下加密值离开 AndroidKeyStore 主密钥不可解，但叠加上面第 4 条的明文回退，备份就会直接带走明文凭据。

**修复**：保留 `allowBackup="true"`（书库与创作数据只存在本机，不备份的数据丢失风险更大），新增 `backup_rules.xml`（API 23–30）与 `data_extraction_rules.xml`（API 31+），排除 `sync_config` / `webdav_config` / `ai_secrets` 三个 prefs。

### 7. 清单冗余的 `usesCleartextTraffic`

`minSdk = 24`，`networkSecurityConfig` 存在时 `android:usesCleartextTraffic` 完全被忽略，两处并存只会造成「到底哪个生效」的误读。已移除该属性，明文策略只由 `network_security_config.xml` 单点表达。

---

## 二、已确认但未修复（需你决策）

### A. 同步链路本身仍是明文 HTTP（高危，架构级）

桌面端只提供明文 HTTP，`SyncApiProvider` 显式带上 `ConnectionSpec.CLEARTEXT`，同步 token 以 `x-sync-token` 头逐请求发送。修复 1 已把攻击面从「全互联网」压缩到「同一局域网」，但**局域网内的主动攻击者（ARP 欺骗、开放 Wi-Fi 嗅探）依然可以截获 token 并冒充已配对设备，读写整个书库**。

为什么没在配置层收紧：Android 的 `<domain-config>` 只能匹配具体主机名，不支持 CIDR 网段通配，而配对目标是每次可能变化的局域网 IP，无法在 XML 里表达。这也是为什么该约束只能落在代码层。

彻底修复需要**桌面端配合**：启用 TLS（自签证书即可），把证书指纹放进配对二维码，客户端侧做指纹固定（自定义 `TrustManager`）。这是跨端改动，超出本次范围。

另外两点来自桌面端的复核发现，建议一并处理：

- 配对 token 在 10 分钟有效期内**可重复使用**（`handlePairingRequest` 未在成功后清除），网络邻近攻击者可借此登记一台持久的攻击者设备，而不只是窃听一次。
- 同步服务监听 `0.0.0.0`，建议绑定到选定的局域网网卡。

### B. Release 未开启代码压缩混淆（低危）

`isMinifyEnabled = false`。**本次刻意未改** —— 在无法编译验证的情况下开启 R8 极易让 Room / Hilt / kotlinx.serialization 在运行时炸掉。建议你在能构建时单独开启并跑一轮完整回归，而不是与安全修复混在一起。

### C. `android/` 与 `mobile/android/` 的重复问题

同一批明文配置在 Capacitor 端 `mobile/android/` 也存在（`network_security_config.xml`、`AndroidManifest.xml:12` 的 `usesCleartextTraffic`、`capacitor.config.ts` 的 `allowMixedContent: true`）。你已明确本次目标是 `android/`，故未改动 Capacitor 端。若网页端仍在分发，这些问题依然成立，其中 `allowMixedContent: true` 允许 `https://localhost` origin 加载明文的**活动**混合内容（脚本/fetch/iframe），风险高于原生端。

---

## 三、验证结果

已执行并通过：

- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**。全部 Kotlin 改动编译通过，两个新增备份规则 XML 通过资源链接与清单合并。
- 合并后清单已确认：`allowBackup="true"` + `fullBackupContent="@xml/backup_rules"` + `dataExtractionRules="@xml/data_extraction_rules"` 均在位，`usesCleartextTraffic` 已按预期消失。
- `PairingManagerLanHostTest` **7 个用例全部通过**（`OK (7 tests)`）：内网 IPv4/IPv6 放行、localhost 与 `*.local` 放行、公网 IPv4/IPv6 拒绝、公网主机名拒绝（不做 DNS）、前导零/十六进制/简写/十进制整数等非规范写法拒绝。

仍需真机确认（构建与单测覆盖不到的部分）：

1. 扫描指向公网地址的二维码应显示「已拒绝配对：… 不在局域网内」，扫描局域网二维码仍能正常配对。
2. 导出诊断日志仍能正常分享（FileProvider 路径已从 cacheDir 根收窄到 `cacheDir/diagnostics/`）。
3. WebDAV 若原先配置的是 `http://` 公网地址，现在会被拒绝并提示改用 https —— 确认这符合预期。

## 四、单测环境既有故障（与本次改动无关，但会挡住所有单测）

`./gradlew :app:testDebugUnitTest` 下**所有**测试类都报 `initializationError` / `ClassNotFoundException`，
包括本次未触碰的 `PlainTextDecoderTest` 与 `SearchViewModelTest`。这不是代码问题：

Gradle 启动测试 worker 时的命令行显示工作目录为

```
D:\develop\Code\Codex\�����Ķ�����\android\app
```

`创作阅读助手` 被解码成乱码。类路径通过参数文件 `@…\.gradle\.tmp\gradle-worker-classpath….txt` 传给 worker，
其中的中文路径段编码错乱，worker 拿到的类路径条目全部指向不存在的目录，于是任何测试类都加载不了。
`gradle.properties` 里的 `android.overridePathCheck=true` 正是当初用来压掉 AGP 这条路径警告的 —— 那条警告是对的。

已验证：把编译产物复制到纯 ASCII 路径后用 `java -cp … org.junit.runner.JUnitCore` 直接运行，7 个用例全绿。
即字节码与测试本身都没问题，**只有 Gradle 的 worker 路径传递被中文目录名破坏**。

尝试过但无效的办法：在 `testOptions.unitTests.all` 里注入 `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`。
参数确实进了 worker 命令行，但参数文件在写入阶段就已经错了，且 Windows 上原生路径解码在 JVM 启动前就已确定。该改动已回退。

**根治办法只有一个：把项目移到不含非 ASCII 字符的路径**，例如
`D:\develop\Code\Codex\creation-reading-assistant\`。

### 已于 2026-07-27 根治

项目目录已从 `创作阅读助手` 改名为 `creation-reading-assistant`。改名后首次运行
`./gradlew :app:testDebugUnitTest` 即 88 个用例全绿（此前恒为 `ClassNotFoundException`）。

**因此本节描述的绕行方案已作废** —— 不要再把编译产物复制到 ASCII 目录、
不要再用 `java -cp … JUnitCore`，直接跑 Gradle 即可。
`gradle.properties` 里的 `android.overridePathCheck=true` 也已无必要。

## 五、同一家族的第二个环境陷阱：Kotlin 守护进程编码（已修）

2026-07-27 在做阅读器 P0 时发现：`gradle.properties` 只给 `org.gradle.jvmargs` 设了
`-Dfile.encoding=UTF-8`，**没给 `kotlin.daemon.jvmargs` 设**。Kotlin 编译守护进程是独立 JVM，
不继承那份参数，在中文 Windows 上按平台默认编码 GBK 读源文件。

后果：**全项目所有中文字符串字面量都可能被静默读错**。实测 `EpubParser.decodeEntity` 表里的
`'“'`（UTF-8 `E2 80 9C`）被读成 `U+9225`（GBK 的「鈥」）加一个残字节 `U+FFFD`，于是
`&ldquo;` `&mdash;` `&hellip;` 解出来全是乱码，而走 `Character.toChars` 的十六进制数值实体
（`&#x201C;`）却正常 —— 因为后者不经过源码字面量。

最恶劣的地方在于它**不报编译错误**，且是否出现取决于当时用的是哪个守护进程实例：
同一份代码，昨天测试全绿，今天换个守护进程就挂。

已在 `gradle.properties` 修复：

```
kotlin.daemon.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

改完需要 `./gradlew --stop` 杀掉旧守护进程才生效。

## 四、改动文件

| 文件 | 改动 |
|---|---|
| `app/src/main/java/.../feature/sync/PairingManager.kt` | 新增内网校验；修正 runCatching 吞异常 |
| `app/src/main/java/.../feature/reader/EpubParser.kt` | 修复图片抽取路径穿越 |
| `app/src/main/java/.../feature/sync/WebDavBackup.kt` | 新增 `requireSafeTarget()`，接入 4 个入口 |
| `app/src/main/java/.../data/security/SecurePrefs.kt` | 新增：加密存储统一入口 |
| `app/src/main/java/.../data/remote/SyncConfigStore.kt` | 改用 `SecurePrefs` |
| `app/src/main/java/.../feature/sync/WebDavConfigStore.kt` | 改用 `SecurePrefs` |
| `app/src/main/java/.../data/settings/SettingsStore.kt` | 改用 `SecurePrefs` |
| `app/src/main/java/.../ui/screen/ProfileScreen.kt` | 诊断日志改写入 `cacheDir/diagnostics/` |
| `app/src/main/res/xml/file_paths.xml` | 收窄为仅 `diagnostics/` |
| `app/src/main/res/xml/network_security_config.xml` | 注释改为准确描述（代码层强制内网） |
| `app/src/main/res/xml/backup_rules.xml` | 新增：备份排除凭据 |
| `app/src/main/res/xml/data_extraction_rules.xml` | 新增：备份/直传排除凭据 |
| `app/src/main/AndroidManifest.xml` | 接入备份规则；移除冗余 `usesCleartextTraffic` |
| `app/src/test/java/.../feature/sync/PairingManagerLanHostTest.kt` | 新增：锁定内网边界 |
