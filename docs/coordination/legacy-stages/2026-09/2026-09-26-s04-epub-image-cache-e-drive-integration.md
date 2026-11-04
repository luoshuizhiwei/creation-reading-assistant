# S04 EPUB 图片缓存清理：E 盘集成构建

- 日期：2026-09-26
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支 / HEAD：`codex/recover-worktree-20260920` / `5aed593`（未提交工作区组合）
- 性质：审查 C 的窄修、定向测试与 qahost 构建；未安装、未运行 MuMu/真机、未操作书库。

## 输入与审查

C 的实现报告为 `docs/coordination/operations/2026-09/2026-09-26-s04-epub-image-cache-cleanup-fix.md`。接手时源文件哈希与报告一致：`BookRepository.kt` SHA-256 `1E3AADB669B8CBAF22AA852B11AC1C06A4F75F35EE5B4AF6D048063C0B368060`；`BookResidueSafeCleanupTest.kt` 为 `99BBB6033F902230628FE5AF956FD0D007AE093EC8DB0E2663C9748DF6DB835C`。`git diff --check` 对生产源文件无输出。

源码检查：`clearBookCache` 预检现覆盖 `books/epub/<id>/`；删除阶段使用现有 `deleteTreeSparingResidue`，且图片目录先于 `<id>.epub` 文件处理。残留或不可列举返回中止，阻止 DB 指针写入；普通文件删除失败累积为 `ClearedWithRemains`。仍需设备侧验证实际目录回收与 UI 反馈。

## 本轮验证

在 `android/` 下使用 `E:\creation-reading-assistant\codex-s04-build-20260925\redirect-build-via-junction.gradle` 和同一 E 盘 `project-cache`，TEMP/TMP 指向 E 盘 `temp`；D 盘 `cra-s04-buildlink` 为指向 E 盘 `project-builds` 的接合点。

| 命令 | 退出码 | 结果 |
|---|---:|---|
| `:app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.BookResidueSafeCleanupTest --offline` | 0 | XML：29/29，0 失败/错误/跳过；Gradle 任务 `FROM-CACHE`，并非本轮重新执行全部测试方法。C 报告其直接运行 224/224。 |
| `:app:assembleQahost --offline` | 0 | `BUILD SUCCESSFUL in 1m 14s`；41 tasks，10 executed、31 up-to-date；qahost Kotlin/Hilt/Dex/Package 本轮执行。 |
| `aapt dump badging` | 0 | `com.creationreadingassistant.qahost`，versionCode 3，versionName 0.5.1，minSdk 24，targetSdk 35。 |

日志：E 盘 `test-image-cache-junction.log`、`assemble-qahost-image-cache-junction.log`。物理 APK：

`E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\outputs\apk\qahost\app-qahost.apk`

- 文件大小：52,149,270 B
- SHA-256：`15E2A9EA049995EE80288759F84C316F9664A2D41CAADDCA4D00D403EE54B51C`
- `output-metadata.json` 与 `aapt` 包身份一致。

先前 APK `24525998…` 已被新构建覆盖，不得作为本修复验收证据。C 的最终审查报告 §7 仍引用更早 APK，属过时锚点。

## 下一道门槛

由独立验收窗口 B 对上述**新哈希**在既有 MuMu 实例 `127.0.0.1:16416` 运行隔离的一次性含图 EPUB 用例：核安装前后包哈希，清理前后对账仅该 qahost 样本的 `<id>.epub`、`<id>/img/` 与 DB 指针；残留拒绝用另一一次性样本。不得清理现有 21 本书，不操作真实手机 `c49ac6cf`。模拟器结论不等于真机验收。

`AbortedMidway` 仓储映射缺少直接测试仍是覆盖缺口；此轮未新增接缝。原始仓库与正式手机均未修改。
