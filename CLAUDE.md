# 项目目标与工作约定

> 本文件每次打开项目都会自动加载。目录结构与构建命令见 [AGENTS.md](AGENTS.md)。

## 产品目标与范围

仓库有两条活跃且运行时隔离的产品线，任务开始时先按路径确定归属，禁止顺手跨线修改：

- `src/`、`electron/`：Electron 桌面创作工作台，主线是项目、正文写作、结构化创作资料与本地资料阅读。
- `android/`：独立原生 Android 阅读端，主线是中文阅读体验、流畅度和本地创作辅助。
- `archives/frozen-mobile/`：已删除 Capacitor 产品线的只读历史存档，不接受功能修改。

Android 任务的优先级仍是阅读体验与流畅度优先，再参考 `docs/research/legado-with-md3-analysis.md` 补齐能力。两端都坚持本地优先；不做登录、云服务或自建服务器。

详细目录边界、构建命令和设备约束以 [AGENTS.md](AGENTS.md) 为准。

## 不要逐段汇报

做完一个阶段 → 提交 → 更新记忆 → **立刻开始下一个阶段**，不要停下来等确认。
只在三种情况下打断用户：需要他拍板且会改变后续方向的决策、需要他动手的事、
他必须知道的坏消息（数据风险、方向性错误）。

## 用户是非技术人员

- 结论先说，用大白话。技术细节写进项目文档，对话里只留用户需要知道的
- 需要决策时，把选项翻译成后果，不要让用户在技术方案之间选
- 完整协作约束见本仓库 `AGENTS.md` 与 `D:\Application\文档\Obsidian\Codex\AGENTS.md`

## 环境陷阱

**1. 项目路径含中文 → Gradle 跑不了单元测试。（2026-07-27 已根治）**
项目已从 `创作阅读助手` 改名为 `creation-reading-assistant`，
`./gradlew :app:testDebugUnitTest` 现在能正常跑 —— 直接用它，
**不要**再走「复制编译产物到 ASCII 目录 + JUnitCore」那套绕行方案（历史文档里还有残留描述）。
原故障是测试 worker 的类路径经参数文件传递时中文路径段编码错乱，导致全部测试类
`ClassNotFoundException`，无法在配置层修复，只能换路径。

**2. Kotlin 守护进程编码（已修，别改回去）。**
`gradle.properties` 的 `kotlin.daemon.jvmargs` 必须带 `-Dfile.encoding=UTF-8`。
守护进程是独立 JVM，不继承 `org.gradle.jvmargs`；缺了它会在中文 Windows 上按 GBK 读源码，
**全项目中文字面量静默变乱码**，且是否发作取决于当时哪个守护进程在跑。改动后需 `./gradlew --stop`。

## 已定的技术决策（勿反复推翻）

**阅读器内核自研，不引第三方。** 当前架构见
`docs/architecture/native-android-reader.md`；后续功能候选见
`docs/plans/legado-feature-backlog.md`。

- Readium：分页锁在 WebView 里，与纯原生冲突
- `archives/frozen-mobile/legado-reader-core`：其 `ZhLayout` 在仓库里是死代码，接进来拿不到中文排版
- **许可证不再是约束**：用户确认本应用仅自用、不外传，GPL 义务只在分发时触发。
  因此可以直接参考并改写上游 legado 的算法，不必"只读规格从零重写"。
  （若将来要分发，需补回来源说明与 GPL 材料。）

## 验证方式

用户已授权 adb 连接真实手机、安装 debug 包和截图。
**UI 改动必须真机截图确认**，多次证明读代码看不出问题。禁止使用 MuMu
模拟器；执行前用 `adb devices` 核对当前设备，并用 `adb -s <serial>` 明确指定。
长测试若临时开启 `stay_on_while_plugged_in`，结束时必须恢复原值。
