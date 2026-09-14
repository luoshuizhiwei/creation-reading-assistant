# Android 原生阅读器懒加载回归报告

日期：2026-07-19

设备：Redmi K50 Ultra，Android 15，ADB serial `c49ac6cf`

约束：只使用真实手机，未使用 MuMu 或其他模拟器。测试期间临时启用连接电源保持唤醒，结束后恢复设备原设置。

## 1. 改造目标

解决原生 EPUB 初次打开时全量解析章节、拼接全书文本、加载全部图片造成的长时间等待和高内存问题，并回归 TXT/EPUB 的核心阅读链路。

## 2. 自动验证

以下命令全部通过：

- `npm run verify:mobile-reader`
- `mobile/android/gradlew.bat :legado-reader-core:testDebugUnitTest :app:compileDebugJavaWithJavac`
- `npm run test --prefix mobile`：15 个测试文件，89 项测试通过
- `npm run mobile:build`
- `npm run verify:mobile-reading-experience`
- `npm run verify:mobile-storage`
- `npm run verify:mobile-import-chain`
- `npm run verify:mobile-inspiration`
- `npm run cap:sync --prefix mobile`
- `npm run verify:beta -- --skip-build`
- `npm audit --prefix mobile --omit=dev`：0 vulnerabilities
- `mobile/android/gradlew.bat :legado-reader-core:testDebugUnitTest :app:assembleDebug`

## 3. 真机结果

### EPUB 样本一：亚人娘补完手册（约 8 MB）

- 旧实现初次打开约 22 秒；懒加载后首次测得 496–507 ms，重进恢复 247 ms。
- 目录可滚动并从第 1 章跳到第 30 章，标题和进度同步为 `第30章 茉莉 / 12.1%`。
- 全书搜索关键词 `30` 约 10 秒返回跨章节结果，点击结果回到正确章节。
- 退出后重新进入恢复到第 30 章；强制结束应用后重新启动，书架恢复为第 103 页、12.2%。
- 夜间主题覆盖正文和图片页，无主题残留。
- 长按文字出现“复制 / 记笔记 / 记为灵感”原生工具页。
- 页面底部不再出现下一页的半行文字。

### EPUB 样本二：当青春幻想具现后

- 打开耗时 116 ms。
- 恢复到 `第2章 男孩子要保护好自己 / 1.4%`。
- 原生 Activity 正常显示、返回，无 FATAL/OOM/ANR。

### TXT 样本：亚人娘补完手册

- 打开耗时 122 ms。
- 连续右点 3 页，每页正文均发生完整变化。
- 连续左点 3 页回到原页，正文截图裁剪区像素差为 0。
- 左右滑动翻页后返回原页，正文截图裁剪区像素差为 0；无夹页、残影或错位。
- 系统返回键能退出原生阅读器。

## 4. 内存

8 MB EPUB 懒加载首次打开后：

- TOTAL PSS 约 218 MB（全书搜索后约 229 MB）
- TOTAL RSS 约 404 MB（全书搜索后约 424 MB）
- Java Heap 约 25–29 MB
- Native Heap 约 34–45 MB

进程总量包含仍存活的 Capacitor MainActivity/WebView，不能等同于纯阅读内核占用；但相比旧实现约 286 MB PSS / 477 MB RSS 已明显下降，且翻页、搜索和重进过程中未出现持续失控增长。

## 5. 产物

- APK：`mobile/android/app/build/outputs/apk/debug/app-debug.apk`
- 大小：24,784,808 bytes
- SHA256：`86E007779894A66FD8056639CA9D0BFA633BA520BB301A6C676EA9E7A1B3C9FD`
- 证据截图与 UI XML：`mobile/android-screenshots/native-*`

## 6. 剩余风险

- EPUB 全书搜索仍需逐章解析，8 MB 样本约 10 秒；后续应增加进度和取消反馈。
- 尚未覆盖损坏、无目录、无封面、中文内部路径、DRM、超长单章 EPUB。
- 尚未建立 360/390/430dp 多宽度与多品牌 Android 真机矩阵。
- 当前 APK 为 Debug 包；正式分发仍需稳定签名和 GPL 分发材料检查。
