# 所有 Agent 的共同约束

你在参与用户明确授权的原生Android多Agent开发。先完整阅读仓库AGENTS.md、本目录README.md和你的任务文件。

## 开工与环境

- 只在指定工作树工作。先输出pwd、分支、HEAD、git status；已有改动属于用户/其他agent，不回滚、不覆盖、不clean、不stash。
- Android源码根为android/app/src；桌面src/、electron/、旧archives/均不在范围。
- Trae/Qoder不stage/commit/push，不创建PR，不自行切分支；交付可审查diff和自有报告。当前提交授权由Codex用于整理基线；后续Git操作由集成者统一处理。
- 工作树不包含忽略的local.properties和构建缓存。优先使用已有JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT；缺少SDK位置可只读主仓库android/local.properties，不复制密钥，不修改构建脚本来补环境路径。
- 同机Gradle编译占用较大内存；代码编写可并行，构建/测试默认预约串行。先检查现有Gradle任务，忙时继续代码审查/写报告，不能杀他人的Java进程或启动三个全量构建。
- 针对自己模块运行必要回归；完整门禁在集成后串行执行。测试真正业务行为和之前失败条件，不靠文本匹配源码证明功能。

## 共享接口

你不是唯一工作者。严格遵守README的文件所有权；独立工作树仍需避免共享文件争抢。
需要DAO/entity/schema、公共设置、导航、Reader宿主等改动时，在自有报告写：

```text
SEAM REQUEST
目标文件：
需要的接口/字段/行为：
调用位置：
为什么现有接口不足：
兼容方案与测试：
是否阻塞本轮：
```

向用户/Codex报告请求后继续不依赖部分，不擅自扩大改动。不得为绕开所有权使用反射、原始SQL、临时数据库副本或复制一套业务实现。

## 阅读与数据契约

- source坐标用于持久化，display坐标只能派生，搜索/选区/高亮/TTS/恢复必须同口径。
- 大TXT有界读取、投影缓存有界；不可按ReadingUnit近似实现全章正则；不支持场景保留原文且说明原因。
- 保留既有纸墨主题、圆角、触控尺寸、减少动态效果、手机/平板/横屏适配。
- 不修改用户原书，不自动迁移/清除设备数据，不输出真实测试书名、正文、API Key、数据库快照。
- 不新增网络依赖或更改数据库版本来绕过实现困难；确有需求先提交共享接口请求，由Codex集成。

## 设备和安装

- 独立真机验收由WorkBuddy负责；开发agent默认不安装、不操作手机。诊断需要设备时向Codex预约独占时段，不能和WorkBuddy抢手机。
- 所有获授权的设备任务先运行adb devices确认真实serial，后续每条设备命令都加 `adb -s <serial>`；不用MuMu。
- 安装主APK或androidTest APK先调用 `android/scripts/install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>`。
- **用户明确允许回退**：脚本实际超时或MIUI阻止后，保留命令/退出码/日志，允许 `adb -s <serial> install -r <absolute-apk>`；test APK需要时使用 `install -r -t`。此回退允许继续验收，不代表安装脚本PASS。
- 不要求用户手动确认MIUI，不修改安全设置，不卸载应用规避签名，不清应用数据。
- 长时间测试记录并临时调整stay_on_while_plugged_in，结束恢复原值（原值null则删除键）。权限、规则、进度与临时资料按事先记录恢复；不能删除用户既有记录。

## 交付

报告写入本目录 `reports/<agent>-r1.md`，仅本人文件。包含：基线SHA、改动路径、完成需求、定向命令/退出码/测试数、实际未完成项、接口请求、WorkBuddy验收路径。
没有安装/视觉证据就写未验收；菜单可点、预览命中、测试通过均不能替代正文真实生效。
