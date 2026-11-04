# 阶段 E：联合候选、M-BUG-03、App 启动边界复核（2026-09-29）

## 联合候选

- E 盘 `stage-e-b1-b2-library-combined-20260929` 存在；统一补丁 121,757 字节，SHA-256 `77fb12704c3b7b7b64a5ce0736d6434e61ad46589bb5855bff3eca821b6fc3dc` 与报告相同。抽核合流的 `BookRepository.kt`、`BookRepositoryTest.kt` SHA 与报告相同，18 行文件清单存在。
- 独立解析 `_evidence/test-results` 的 11 份 XML：**167 tests，0 failures，0 errors，0 skipped**；联合测试日志末尾 `BUILD SUCCESSFUL`。此结果只覆盖该 E 盘隔离候选的定向 JVM，不涵盖 MuMu UI、WorkManager 系统行为、正式包或真实手机。
- 报告使用 `GRADLE_USER_HOME=D:\develop\env\gradle`，与用户“构建产物尽量不要放 C/D”的偏好不一致；后续新的候选/构建缓存优先放 E 盘，已有 D 盘缓存不在此轮擅自迁移。

## 两份静态边界报告

- M-BUG-03 报告为零构建；源码/矩阵抽核确认 `ShelfSourceUpdateChecker.kt` 及其测试在总矩阵被标 `EXCLUDED`，但属来源更新闭包候选。H180 被标 B2，其测试调用 `ShelfViewModel` 的来源刷新汇总函数，归属应复核为 M-BUG-03。`ShelfImporter` 等混合 hunk 需手工拆分，尚无隔离编译。
- App 启动报告为零构建；抽核 `App.kt` 引用未在 HEAD 的 `BootMirror`，主题候选须含 `SettingsStore.kt` 等依赖。S06 最小边界仍是静态方案，未验证编译与首帧设备行为。

## 当前边界与下一步

恢复仓库 HEAD 仍为 `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空。后续提交未获授权。下一轮可在互不重叠的 E 盘目录并行：S06 启动主题隔离编译/JVM、M-BUG-03 基于已冻结联合候选手工拆分并隔离编译，以及单设备所有者对联合候选做 MuMu qahost 限定验收。设备前后须备份并恢复 DB、文件、APK、密度，不能把 MuMu 称作实体手机。整体 A–E 仍 **4/5=80%**。
