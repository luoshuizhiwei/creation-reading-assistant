# J1-v3 — 测试 UTC 预期修正报告

- 任务编号/版本：**J1-v3**（依据 Codex-review-J2v3-W1-v1 Spec.1：唯一仪器失败为预期字面量误算）。
- 执行者：ZCode/GLM（实施与构建）；核查：Codex；设备归 DSH/J2-v4，**本轮零设备操作**。
- 候选：`E:\creation-reading-assistant\next-round-joint-j1-v3`（自已核 v2 源码派生，排除 build/.gradle/local.properties；复用 v2 的 E 盘缓存，未复制 2.8GB）。原候选、freeze、原报告与日志只读，未覆盖他人修改。

## 1. 输入指纹

- HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`；未 commit/push/reset/checkout。
- 派生核验：v3 源码树（875 文件，排除 build/local.properties）对 v2 已核树逐文件 SHA 对比——**差异恰为 1 个文件**（SyncRepositoryPersistenceTest.kt），其余零变化。

## 2. 唯一源码变更（单 hunk）

`app/src/androidTest/java/com/creationreadingassistant/data/repository/SyncRepositoryPersistenceTest.kt`
第 356 行 completedAt 断言：`"2025-10-09T07:46:40Z"` → `"2025-10-09T08:53:20Z"`。

- 补丁：`freeze/evidence/j1v3-single-hunk.patch`（-1/+1，仅此一处字面量）。
- **独立验算**（`freeze/evidence/utc-verification.txt`，三通道一致）：
  - Python `datetime.fromtimestamp(ms/1000, UTC)`：`1_760_000_000_000 ms → 2025-10-09T08:53:20Z`；
  - 纯算术：`1760000000 = 20370×86400 + 32000` → day 20370 + `08:53:20`；
  - JDK17 `java.time.Instant.ofEpochMilli`：同值。
  - 同批核验 W1 夹具值：`1_767_000_000_000 ms → 2025-12-29T09:20:00Z`（供 W1-v2）。
- 毫秒夹具（`completed_at = 1_760_000_000_000L`）、产品代码、依赖与其他断言**零修改**。

## 3. 构建与身份

| 项 | 值 |
|---|---|
| 构建命令 | `:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost`（复用 v2 E 盘缓存） |
| 退出码 | **0**（BUILD SUCCESSFUL，`freeze/evidence/gate-v3-qahost-test.log`） |
| 新测试 APK | `freeze/apk/app-qahost-androidTest.apk` SHA `03b2fa0d6b8ce5674ffab4e81e5d4b91ea0266bebb1e082ab92880fcebbd28a3` |
| 签名 | apksigner verify exit 0；CN=Android Debug；证书 SHA-256 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（与 J2-v3 设备回读一致） |
| targetPackage / runner | `com.creationreadingassistant.qahost` / `com.creationreadingassistant.testing.CraIsolatedTestRunner`（aapt 实测） |
| 宿主（复用） | v2 冻结包 `app-qahost.apk` SHA `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`——v3 未构建宿主（构建输出仅 androidTest/），产品源码零变化即复用依据 |

## 4. 六文件源码 SHA 清单（`freeze/six-file-sha256.txt`）

```
3e3467935c339fcb73e9e8f9a0a9791427edb822d98fc42d3a887dde9b58173f  SyncRepository.kt（未变）
4b1e0d3a50ee68db603483e145dc8e944afc391c67a43fe00caf365e43e577d1  WebDavBackup.kt（未变）
ae078ff087627125b3e373fa0c9c4936a14dbca8f237441d23f9904c2dac5bb4  app/build.gradle.kts（未变）
e2c9d515ef821d87e6bfb5bf197dbae9e1e69b2b9dedaf4617e01e7a41c02733  SyncRepositoryLocalFactsTest.kt（未变）
fc167ff822fd4e9a4e2f6a5789e82db162837c809483a893f3654c9c54029840  WebDavCompatibilityTest.kt（未变）
9d84c4d17aefe9a68e7ff5fc72e5c423a79bb761ab0e6f904977e96482dc7d17  SyncRepositoryPersistenceTest.kt（本 hunk）
```

## 5. 未覆盖项与边界

- 四项仪器**仍未运行**（设备归 J2-v4）；本轮只修正预期字面量，不能宣称设备通过。
- J2-v3 失败点**之后**的断言链（filePath/未知字段/metadata 一致等）在本轮设备结果出来前仍为未验证状态，不得因字面量修正而整链判通过。
- 未重跑 2673 项 JVM（改动仅 androidTest 字面量；产品侧零变化，宿主不重建）。

## 6. 交付状态

- **READY.json 已写入**（构建成功且清单/补丁/双 APK SHA 核对成立后）：`BUILD_READY=true`、`DEVICE_ACCEPTED=false`，引用清单、补丁与双 APK 完整 SHA。
- 下一步：DSH/J2-v4 以本对（宿主 ea68aa16… + 测试 03b2fa0d…）运行四项仪器并完成恢复；唯一预期行为变化 = UTC 断言由 actual（设备日志中已是 08:53:20Z）匹配 expected。
