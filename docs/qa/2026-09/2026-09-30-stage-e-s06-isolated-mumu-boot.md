# 阶段 E：S06 最小候选 MuMu 启动窗口验证（2026-09-30）

## 对象与结论

- 对象是 `E:\creation-reading-assistant\stage-e-s06-boot-isolated-20260929\android`：HEAD `513891d` 加 S06 的 8 文件最小补丁，补丁见同目录 `patches/stage-e-s06-boot-minimal.patch`。这是隔离候选，**不是**恢复仓库完整工作树。
- `GRADLE_USER_HOME` 在该 E 盘目录；`./gradlew.bat :app:assembleQahost --offline --max-workers=1 --console=plain` 退出码 0，日志 `_evidence/gradle-04-assemble-qahost-codex.log`。候选 APK SHA-256 为 `2790030183BF49B4E3F61EBFFC7E241F23AF8225535E1CF8CA3755E631AC2C38`。
- MuMu `emulator-5556`、Android 12 API 32、隔离包 `com.creationreadingassistant.qahost`：通过应用 UI 设为显式深色，系统设为浅色，确认 `cra_boot_mirror.xml` 为 `dark`。分别在 1080×1920 / 280 dpi（平板式侧栏）与临时 420 dpi（约 411 dp 宽、手机式底栏）下 force-stop 后冷启动录屏。两段录屏的启动窗口、启动图与后续 Compose 页面均为深色；逐帧目视未见大面积浅色闪帧。手机宽度录屏 62 帧 / 2.98 秒，中央区域亮像素比例最大 5.37%（该最大值来自启动前桌面）；平板宽度录屏 64 帧 / 2.88 秒，最大 2.97%。
- 结论限定为这两次 MuMu/qahost 录屏观察。录屏无法证明两帧之间绝无更短闪烁；未验证实体手机、其他 ROM、生产包或 G6 时长功能。

## 证据与恢复

视频、接触图和逐帧图片在 `E:\creation-reading-assistant\stage-e-s06-boot-isolated-20260929\_evidence\`：

- `s06-cold-app-dark-system-light.mp4`、`s06-cold-contact.png`、`cold-all-frames/`
- `s06-cold-phone-app-dark-system-light.mp4`、`s06-cold-phone-contact.png`、`phone-all-frames/`
- `qahost-before-sha256-manifest.txt`、`qahost-after-sha256-manifest.txt`、`qahost-after-phone-sha256-manifest.txt`

安装前，53 个持久文件哈希与既有冷备 `E:\creation-reading-assistant\stage-e-b1-b2-library-device-20260929\backup\qahost_backup_before.tar` 对应的上轮恢复状态逐行一致；冷备 tar SHA-256 为 `CAE16E820BCAFB1E269FAD7682F44C832581BFB986E5CCE6FA81084B388026F0`。两轮结束均卸载候选及测试包，重新安装原 APK，并回写该冷备；最终 **53/53 个文件哈希与本轮安装前一致**。已安装原 qahost APK 哈希 `E82904CC9C1315466ED5C521693F93F541560FEEF038BF074CE7F7CF8324EF8A`，测试 APK 哈希 `FDBD019011088505FA37D71565259A0D76F6927407AF69148379E2EF7C27038D`。屏幕恢复 1080×1920 / 280 dpi、系统夜间模式恢复 `yes`；临时 tar 已删除。

本轮不改恢复仓库源码、不 stage/commit/push、不触碰正式包或正式书库。阶段 E 整体状态仍为 **4/5（80%）**；这条证据仅收口 S06 隔离候选的核心 MuMu 启动观察，不能替代联合候选或完整门禁。
