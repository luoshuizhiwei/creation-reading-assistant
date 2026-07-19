# Android v0.2.0 正式发布与真机回归记录

日期：2026-07-19

设备：Redmi K50 Ultra，Android 15，ADB serial `c49ac6cf`

约束：只使用真实手机，未启动 MuMu 或其他模拟器。测试期间临时把 `stay_on_while_plugged_in` 从 `2` 调整为 `3`，结束后已恢复为 `2`。

## 1. 发布结果

- 源码版本：`0.2.0`，Android `versionCode 27`。
- GitHub Actions：`29681441017`，结论 `success`。
- 公开 Release：<https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases/tag/v0.2.0>
- Release 资产共 11 个，包含签名 APK、Windows 安装版、Windows 免安装版、GPL 对应源码、许可文件、中文说明、更新清单和 SHA256 清单。
- GitHub Raw 与 jsDelivr 的 `latest-mobile.json` 均返回 `0.2.0`。
- 公开 APK：`creation-reading-assistant-0.2.0-android.apk`
- 公开 APK SHA256：`5EE4D2913281104596D6582F0DBCE8235A501C2E426494E65157B3CD5D290AA7`

GitHub Runner 在干净环境中完成发布门禁、Windows 打包、Android 单元测试、`assembleRelease` 和 `apksigner verify`。本机从公开 Release 下载 APK 时遇到 GitHub 大文件连接超时，未把该次本地下载作为验证依据；公开资产的 GitHub 服务端 digest 与 `SHA256SUMS.txt` 一致。

## 2. 安装状态

- 包名：`local.creationReadingAssistant.mobile`
- 真机安装版本：`0.2.0` / `versionCode 27`
- 当前焦点可正常在 `MainActivity` 与 `NativeReaderActivity` 之间切换。
- 旧版覆盖安装链路此前已通过 `adb install -r`，签名证书与现有安装链一致。

## 3. TXT 原生阅读器

- 用户已有 TXT 从首页继续阅读进入 `NativeReaderActivity`。
- `NativeReaderPerf`：`load-ready format=text elapsedMs=90`。
- 连续右点两页，每次正文区域均发生大范围变化。
- 连续左点两页返回原页，排除状态栏后的阅读区域像素差为 `0`。
- 中央点击可立即唤出菜单，目录、搜索、设置均存在。
- 第一次系统返回关闭菜单，第二次系统返回退出阅读器并回到 `MainActivity`。
- 未发现 FATAL、ANR 或 OOM。

## 4. EPUB 原生阅读器

- 用户已有 EPUB 从书架进入 `NativeReaderActivity`。
- `NativeReaderPerf`：`load-ready format=epub elapsedMs=75`。
- 连续右点两页，每次正文区域均发生大范围变化。
- 连续左点两页返回原页，排除状态栏后的阅读区域像素差为 `0`。
- 页面底部正文完整，没有被安全区或菜单遮挡。
- 中央点击可立即唤出菜单，目录、搜索、设置均存在。
- 退出阅读器后重新进入，恢复页与退出前阅读区域像素差为 `0`。
- 未发现 FATAL、ANR 或 OOM。

## 5. 结论

`v0.2.0` 的签名发布链、应用内更新清单、TXT/EPUB 原生打开、左右点击翻页、反向返回、菜单、硬件返回和位置恢复均通过本轮验证。Markdown 仍按设计使用 Web 阅读器；损坏 EPUB、多品牌与多宽度设备矩阵属于后续兼容性工作，不阻断本次正式发布。
