# P3 设计文档 · 局域网同步 + 扫码配对

> 阶段目标：让原生 Android 端通过局域网直连桌面端（Electron 同步服务端），完成配对与双向同步；扫码配对采用 ML Kit + CameraX（纯原生，无 WebView）。

---

## 1. 目标与背景

- **背景**：项目无独立后端，桌面端 Electron 主进程（`node:http` 自建服务）即同步服务端；移动端通过局域网直连。
- **目标**：原生端复用 `src/types/sync.ts` 的 LAN 协议契约，实现：
  1. 扫码配对（CameraX 预览 + ML Kit 条码识别）
  2. 双向同步（拉取全量 / 推送本地变更），数据与本地 Room 库对齐
  3. 鉴权（x-device-id + x-sync-token）、明文 HTTP（局域网）、动态 baseUrl

## 2. 主要变更点说明

| 类别 | 变更 |
|---|---|
| 新增传输层 | `data/remote/`：`SyncContract`（契约）、`SyncApi`（Retrofit 接口）、`AuthInterceptor`、`SyncConfigStore`（加密存储）、`DeviceInfoProvider`、`SyncApiProvider`（动态 baseUrl） |
| 新增配对 | `feature/sync/PairingManager`（QR 解析 + `/sync/pair` + 持久化） |
| 新增同步 | `data/repository/SyncRepository`（pull/push，payload 真相源映射） |
| 新增界面 | `ui/screen/QrPairingScreen`（CameraX + ML Kit） |
| 改造界面 | `ProfileScreen` 增加「扫码配对 / 立即同步 / 解除配对」；`ProfileViewModel` 增加配对与同步状态 |
| 改造导航 | `AppNavigation` 增加 `pairing` 路由，配对与「我的」共享 ViewModel |
| 配置 | `libs.versions.toml` / `app/build.gradle.kts` 增加 Retrofit/OkHttp/ML Kit/CameraX；`AndroidManifest.xml` 增加 CAMERA 权限；`network_security_config.xml` 已放行 cleartext |
| 版本 | `versionName = "0.3.0-p3"` |

## 3. 功能描述

- **扫码配对**：打开相机预览，识别首个二维码 → 解析出桌面端 baseUrl（与可选 token）→ 调 `POST /sync/pair` → 持久化配置（EncryptedSharedPreferences）。
- **立即同步**：先 `pull()` 拉取桌面端全量（书库/灵感/进度/会话）写入本地库，再 `push()` 把本地变更推回，返回冲突数。
- **解除配对**：清空本地配置。
- **数据桥接（P1 已有）**：JSON 导出/导入仍可用，作为无网络时的兜底迁移手段。

## 4. 接口定义 / 关键参数说明

### 端点（`SyncApi.kt`，对齐 `src/types/sync.ts` 与服务端）
```
POST /sync/pair                  { device, token? }            -> PairingAck { token?, deviceId?, expiresAt? }
GET  /sync/manifest              (鉴权头)                      -> SyncManifest
POST /sync/pull                  { device }                    -> SyncPullResponse { manifest, books[], inspirations[], progress[], sessions[] }
POST /sync/push                  SyncPushPayload { device, ... }-> SyncPushResult { ok, applied, conflicts[], manifest }
GET  /sync/books/{id}/file       (鉴权头)                      -> 文件流（预留）
PUT  /sync/books/{id}/file       (鉴权头)                      -> 预留
GET  /sync/books/{id}/chunks/{index} (鉴权头)                  -> 分块（预留）
```

### 关键参数
- **鉴权头**：`x-device-id`（本机 Android ID）、`x-sync-token`（配对返回 token），由 `AuthInterceptor` 统一注入。
- **信封契约**：`SyncEnvelope<T>{ id, type, revision, deviceId, updatedAt, deletedAt?, payload }`，`payload` 在传输层以 `JsonObject` 透传。
- **落库映射**（payload 真相源模式）：信封 `payload`（业务对象 JSON）原样写入各表 `payload TEXT` 列；`title`/`bookId`/`sourceBookId`/`contentHash` 等提升列从 payload 抽取，与 P1 的 V2 schema 完全同构。

### 依赖坐标（本机首次构建需联网下载）
```
com.squareup.retrofit2:retrofit:2.11.0
com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0
com.squareup.okhttp3:okhttp:4.12.0
com.google.mlkit:barcode-scanning:17.3.0
androidx.camera:camera-{core,camera2,lifecycle,view}:1.3.4
```

## 5. 使用方式

1. 桌面端开启「同步」面板，展示配对二维码。
2. 原生端「我的」→「扫码配对」→ 扫描该二维码 → 自动完成配对。
3. 「我的」→「立即同步」执行拉取+推送，状态栏显示「拉取 X 本 / 推送 Y 条 / 冲突 Z」。
4. 解绑点「解除配对」。

## 6. 一致性回检（文档 ↔ 代码）

| 文档声明 | 代码落点 | 结论 |
|---|---|---|
| 7 个端点 | `SyncApi.kt` 7 个 suspend 方法 | ✅ 一致 |
| 鉴权头两枚 | `AuthInterceptor` 注入 `x-device-id`/`x-sync-token` | ✅ 一致 |
| 动态 baseUrl | `SyncApiProvider.forBaseUrl(baseUrl)` 按 host 缓存 | ✅ 一致 |
| 二维码三种形态 | `PairingManager.parseQr`（JSON/URL/裸地址） | ✅ 一致 |
| 拉取映射 4 类 | `SyncRepository.applyBook/Inspiration/Progress/Session` | ✅ 一致 |
| EncryptedSharedPreferences | `SyncConfigStore` | ✅ 一致 |
| 权限/明文 | Manifest CAMERA + network_security_config cleartext | ✅ 一致 |
| 版本号 | `0.3.0-p3` | ✅ 一致 |

## 7. 待确认项（与服务端对齐，不影响编译）

1. **`/sync/pair` 响应形态**：本端假设返回 `PairingAck{ token, deviceId, expiresAt }`；若桌面端把 token 放在二维码 `pairingUrl` 的 query 或不返回 token，本端已做回退（优先服务端 token，否则用二维码一次性 token，再否则报错）。请对照 `electron` 服务端 `/sync/pair` 实现校正字段名。
2. **`/sync/push` 是否接受「全量推送」**：本端每次 push 发送全部本地记录（幂等，依赖服务端 revision/upsert）。若服务端要求仅推送增量，需加 dirty 标记（当前未实现，列为 P4 优化）。
3. **书籍正文文件传输**：`getBookFile/putBookFile` 端点已预留但未在 pull/push 主流程调用（避免首版复杂度），后续在 P4 补「按 bookFiles 清单增量传文件」。

## 8. 构建与 adb 测试 runbook（你本机执行）

```
# Android Studio 打开 D:\develop\Code\Codex\创作阅读助手\android
# 首次 Sync 需联网拉取 Retrofit/ML Kit/CameraX 依赖
# 连 Android 7.0+ 真机（USB 调试）
# 装包：
D:\develop\Android\Sdk\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
# 自测：
#  - 「我的」→ 扫码配对：用桌面端同步二维码（需桌面端先开同步）
#  - 立即同步：观察状态栏是否回显拉取/推送/冲突数
#  - 解绑：清配置
# 看崩溃：
D:\develop\Android\Sdk\platform-tools\adb.exe logcat | findstr creationreadingassistant
```
