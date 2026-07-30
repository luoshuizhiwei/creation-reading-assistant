# 创作阅读助手原生 Android 稳定化与阅读内核实施计划

> **历史阶段计划**：本文保留 2026-07-26 的实施基线，许多条目已完成或被后续
> 阅读内核实现替代。当前架构与未完成事项分别见
> `docs/architecture/native-android-reader.md` 和
> `docs/testing/native-android-gap-audit-2026-07-29.md`。

## 一、项目范围

项目路径：

```text
D:\develop\Code\Codex\creation-reading-assistant
```

本任务唯一允许修改的产品线：

```text
android/
```

技术栈：

- Kotlin
- Jetpack Compose
- Room
- DataStore
- Hilt
- Retrofit / OkHttp
- CameraX / ML Kit
- 原生 Android 文件系统

禁止修改：

```text
mobile/
src/
electron/
```

这些分别属于旧 Capacitor 移动端和桌面端，不属于本任务。

## 二、最终目标

将 `android/` 建设为唯一正式 Android 主线，达到：

1. 应用可稳定启动，不出现闪退、白屏和循环崩溃。
2. TXT、Markdown、EPUB 可以稳定导入、打开和恢复进度。
3. 阅读器不整本加载大文件。
4. 分页、点击、滑动、返回、菜单行为符合阅读 App。
5. Room 是业务数据唯一事实源。
6. 阅读器设置、进度、笔记、书签、灵感来源保持一致。
7. 不依赖 WebView、React、Capacitor 或旧版运行时代码。
8. 每个阶段均可独立构建、安装和回滚。

## 三、不可违反的约束

### 3.1 源码边界

必须遵守：

- 只修改 `android/**`。
- 不复制 `mobile/src` 进入原生工程。
- 不让 Compose 页面调用旧 Capacitor 插件。
- 不引入 WebView 作为阅读正文载体。
- 不通过本地 HTTP 服务连接旧移动端。
- 不删除桌面端、旧移动端或用户数据。
- 不改应用 ID，除非用户明确授权。
- 不执行破坏性 Room migration。
- 不使用 `fallbackToDestructiveMigration()` 掩盖迁移问题。

### 3.2 阅读内核约束

严禁：

- 将整本大型 TXT 读入一个 `String`。
- 将整本 TXT 放进单个 `Text` 或 `BasicTextField`。
- 一次性解析 EPUB 全部章节正文和图片。
- 一次性解压整个 EPUB 到内存。
- 用 `Thread.sleep()` 解决竞态。
- 在主线程执行文件读取、ZIP 解析、分页或数据库操作。
- 每次点击翻页都重新解析整个章节。
- 用页码作为唯一恢复位置。
- 解析失败后显示空白页面。
- 捕获异常后静默忽略。

### 3.3 UI 约束

本阶段不允许大规模美化或重新设计所有页面。

只允许调整与以下问题直接相关的 UI：

- 阅读正文布局。
- 阅读器顶部栏、底部栏。
- 目录、搜索、设置等二级页面。
- 加载、空状态和错误状态。
- 文件导入反馈。
- 崩溃恢复提示。

不得增加：

- 营销页面。
- 阅读目标。
- 会员系统。
- 书源、爬虫。
- 云账号。
- 与当前任务无关的新功能。

### 3.4 外部开源代码

可以参考 Legado 等开源项目的：

- 阅读器分层思路。
- 分页算法设计。
- Locator 模型。
- 页面缓存策略。
- 触摸区域设计。

但必须先确认许可证兼容性。未经确认不得直接复制源码。若复制或改写第三方代码，必须：

- 保留许可证和版权声明。
- 在项目文档记录来源。
- 说明修改内容。
- 不声称为完全自研。

## 四、目标架构

Compose 只负责应用页面和阅读器操作层，分页核心使用独立的纯 Kotlin 阅读引擎。

```text
ReaderScreen
    ↓
ReaderViewModel
    ↓
ReaderController
    ├── TextReaderEngine
    ├── MarkdownReaderEngine
    └── EpubReaderEngine
            ↓
       PageLayoutEngine
            ↓
       ReaderPageCache
            ↓
       文件系统 / ZIP 条目
```

### 4.1 核心接口

必须建立或整理以下接口，命名可以按现有项目调整，但职责不能混合：

```kotlin
interface ReaderEngine {
    suspend fun open(source: BookSource, locator: ReaderLocator?): ReaderDocument
    suspend fun loadPage(locator: ReaderLocator): ReaderPage
    suspend fun next(locator: ReaderLocator): ReaderLocator?
    suspend fun previous(locator: ReaderLocator): ReaderLocator?
    suspend fun search(query: String): Flow<SearchResult>
    suspend fun close()
}
```

稳定位置模型必须包含：

```kotlin
data class ReaderLocator(
    val bookId: String,
    val format: BookFormat,
    val chapterIndex: Int?,
    val chapterHref: String?,
    val charOffset: Long?,
    val progressPercent: Double,
)
```

禁止仅保存：

```kotlin
pageIndex = 15
```

因为字号、屏幕宽度、页边距变化后页码会失效。

## 五、实施阶段

## 阶段 0：建立基线

### 工作

1. 阅读仓库根目录和 `android/` 下的说明文件。
2. 检查当前 Git 状态，不覆盖其他 Agent 的未提交改动。
3. 记录当前版本号、应用 ID、数据库版本。
4. 构建当前原生应用。
5. 记录现有测试结果和已知错误。
6. 不修改代码就能复现的问题，先记录复现步骤。

### 必须执行

```powershell
cd D:\develop\Code\Codex\creation-reading-assistant\android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

### 通过标准

- `assembleDebug` 成功。
- 明确列出失败的测试，不得跳过后假装通过。
- 输出当前 APK 路径、大小和版本。

## 阶段 1：启动与崩溃防护

### 工作

1. 检查 `Application`、`MainActivity`、Hilt、Room 初始化顺序。
2. 修复启动阶段未捕获异常。
3. 加入统一崩溃日志文件，但不得记录 API Key、密码和书籍正文。
4. 读取设置或数据库失败时进入可操作错误页。
5. 禁止出现白屏。
6. 恢复异常退出后，优先回到书架，不自动重新打开可能导致崩溃的书。

### 状态模型

启动状态至少包含：

```kotlin
sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Error(val message: String, val retryable: Boolean) : StartupState
}
```

### 通过标准

- 冷启动连续 20 次无闪退。
- 杀进程后重新启动能进入应用。
- 数据库读取失败时有中文错误提示和重试入口。
- 不允许异常后停留在空白 Compose 页面。

## 阶段 2：统一数据事实源

### 工作

1. Room 是以下数据的唯一事实源：
   - 书籍
   - 文件记录
   - 阅读进度
   - 阅读会话
   - 灵感
   - AI 候选版本
   - 笔记
   - 高亮
   - 标签
   - 分类
   - 书单
   - 同步状态
2. DataStore 只保存轻量设置。
3. 正文文件保存在应用文件目录，不进入 Room 大字段。
4. 删除书籍必须使用事务。
5. 不允许 UI 自己维护与 Room 长期分离的数据副本。

### 删除事务应包含

- 标记或删除书籍记录。
- 清理对应文件记录。
- 处理阅读进度和会话。
- 清理书单、分类、标签关联。
- 保留或明确处理来源灵感。
- 文件删除失败时返回可见错误。

### 通过标准

- 删除书籍后切换页面、重启应用都不会复活。
- 修改标签、分类、书单后立即生效。
- Room migration 单元测试通过。
- 不使用破坏性迁移。

## 阶段 3：TXT/Markdown 文件级读取

这是 P0 阶段。

### 工作

1. 建立章节索引，索引只保存偏移量和标题。
2. 使用 `FileChannel`、`RandomAccessFile` 或等价方案按区间读取。
3. 识别 UTF-8、GB18030/GBK、UTF-16 BOM。
4. 章节识别不得复制整本正文。
5. 当前页面只缓存：
   - 当前页
   - 前 1～2 页
   - 后 1～2 页
6. 搜索采用流式扫描，支持取消。
7. 超大 TXT 不得因为大小直接拒绝打开。

### 必须支持

- 无章节 TXT。
- 单行超长 TXT。
- 大量空行。
- Windows 和 Unix 换行。
- 中文编码。
- 重复章节标题。
- 超长章节。
- 100 MB 以上文件的基本打开能力。

### 分页要求

分页输入：

```text
正文片段
字体
字号
加粗
行距
段距
页面宽高
安全区
页边距
```

分页输出必须包含：

```text
起始偏移
结束偏移
显示文本
章节信息
上一页 locator
下一页 locator
```

### 通过标准

- 50 MB TXT 不发生 OOM。
- 首次打开不要求全文读入后才显示。
- 左右翻页无半页错位。
- 修改字号后可根据字符偏移恢复到相近位置。
- 连续翻页 100 次不明显增加常驻内存。

## 阶段 4：EPUB 按章解析

这是第二个 P0 阶段。

### 工作

1. 打开 EPUB 时只解析：
   - mimetype
   - container.xml
   - OPF
   - manifest
   - spine
   - nav/NCX
   - 封面元数据
2. 不在打开时解析所有章节正文。
3. 当前章节进入时才读取 XHTML。
4. 图片必须按屏幕尺寸采样。
5. 章节离开后释放不再使用的 DOM、Bitmap 和文本。
6. 保留当前章、前一章、后一章缓存。
7. 空章节、图片章节、封面章节不能导致退出。
8. EPUB 解析失败必须进入错误页。

### EPUB 必须覆盖

- EPUB 2。
- EPUB 3。
- NCX 目录。
- nav.xhtml 目录。
- 嵌套目录。
- 中文文件名。
- URL 编码 href。
- fragment href。
- 无目录 EPUB。
- 空 spine 项。
- 图片型章节。
- 损坏 ZIP。
- 超大图片。
- 缺少资源文件。

### 通过标准

- 打开 EPUB 不一次扫描整本正文。
- 章节切换不退出应用。
- 点击左右区域可以正确翻页。
- 中间点击始终可以唤起菜单。
- 底部正文不被系统导航栏或工具栏遮挡。
- 解析失败时可以返回书架、重新导入或查看错误。

## 阶段 5：专业分页内核

### 技术决策

Compose 负责外围 UI，但正文分页建议使用：

```text
自定义 Android View
+
AndroidView 接入 Compose
```

不要强行让一个巨大的 Compose `Text` 完成整页排版。

### 工作

1. 实现统一 `PageLayoutEngine`。
2. TXT、Markdown、EPUB 共用页尺寸、主题和手势协议。
3. 支持：
   - 横向分页。
   - 上下滚动。
   - 点击翻页。
   - 滑动翻页。
4. 默认横向分页。
5. 分页模式和滚动模式使用不同进度算法。
6. 页面宽度必须按真实内容区域计算。
7. 翻页完成后必须吸附到整页边界。
8. 禁止出现上一页文字残留。

### 点击区域

默认：

```text
左侧 30%：上一页
中间 40%：显示/隐藏菜单
右侧 30%：下一页
```

必须支持用户关闭点击翻页。

### 状态

阅读器必须使用显式状态机：

```kotlin
sealed interface ReaderState {
    data object Idle : ReaderState
    data class Loading(val stage: String) : ReaderState
    data class Ready(val page: ReaderPage) : ReaderState
    data class Error(val message: String, val recoverable: Boolean) : ReaderState
}
```

### 通过标准

- 快速点击右侧 50 次不会循环或卡在同一页。
- 左滑后右滑能回到原页。
- 跨章节翻页不残留旧章节文字。
- 旋转屏幕、改变字号后重新分页但不丢阅读位置。
- 翻页过程中不重复提交相同动作。

## 阶段 6：阅读器交互

### 主界面

正文优先，默认隐藏操作栏。

顶部栏：

- 返回。
- 书名/章节名。
- 搜索。
- 更多。

底部栏：

- 目录。
- 进度。
- 记为灵感。
- 书签/笔记。
- 设置。

### 二级页面

以下功能必须使用阅读器内部二级页面，不使用承载长内容的底部弹窗：

- 目录。
- 搜索。
- 书签。
- 笔记。
- 灵感。
- 阅读设置。
- 书籍信息。
- 错误详情。

### 返回优先级

硬件返回键依次执行：

1. 关闭选择菜单。
2. 关闭阅读器二级页面。
3. 关闭顶部/底部操作栏。
4. 保存最终进度。
5. 返回书架。

不得直接退出整个 App。

### 主题

统一使用 ReaderTheme tokens：

- 白纸。
- 暖纸。
- 护眼。
- 夜间。

必须覆盖：

- 正文。
- 状态栏。
- 导航栏。
- 顶部栏。
- 底部栏。
- 二级页面。
- 输入框。
- 进度条。
- 菜单。

禁止局部残留上一主题颜色。

## 阶段 7：进度、会话与灵感

### 进度

- 翻页时更新内存进度。
- 使用节流写入 Room。
- `onPause`、返回书架和进程退出前强制 flush。
- 不能每次触摸都写数据库。
- 阅读设置变化不能错误累加阅读时间。

### 会话

必须区分：

```text
activeDuration
idleDuration
wallDuration
```

空闲超时后暂停 active 计时。

### 灵感

选中文字：

- 选中文字保存到 `source.excerpt`。
- 用户想法保存到正文。
- 不将摘录塞进灵感正文。

未选中文字：

- 保存书名。
- 作者。
- 格式。
- 章节。
- Locator。
- 进度。
- 创建时间。

### 通过标准

- 返回书架再进入，恢复位置准确。
- 切换字号后恢复到相近文本位置。
- 强制结束进程后最多丢失一个保存周期的进度。
- 记为灵感后有明确反馈。
- 灵感来源可再次打开对应书籍位置。

## 阶段 8：性能治理

### 必须测量

- 冷启动时间。
- 热启动时间。
- TXT 首屏时间。
- EPUB 首章时间。
- 翻页响应时间。
- 帧率和卡顿帧。
- 常驻内存。
- EPUB 峰值内存。
- 连续阅读 30 分钟耗电。
- 100 次翻页后的内存变化。

### 建议验收目标

这些是项目目标，不是当前已实现事实：

- 普通手机冷启动：2 秒内进入可操作页面。
- 热启动：1 秒内。
- 普通 TXT 首屏：1 秒内。
- 普通 EPUB 首章：3 秒内。
- 点击翻页反馈：100 ms 内开始。
- 不出现持续增长的页面缓存。
- 连续翻页不触发 ANR。
- 大文件失败时显示错误，不允许进程退出。

### 缓存约束

缓存必须设置明确上限：

- 页面缓存数量。
- 章节缓存数量。
- Bitmap 缓存大小。
- 搜索结果上限。
- 封面缓存大小。

禁止无界 `Map`、无界列表和静态 Activity 引用。

## 阶段 9：旧版数据迁移

原生应用与旧应用包名不同，不能自动读取旧沙箱。

必须提供明确迁移方案，至少选择一种：

1. 旧版导出 JSON + 书籍文件，原生版导入。
2. 通过电脑局域网同步把旧数据推送到原生版。
3. 用户手动选择旧版备份文件。

迁移范围：

- 书籍元数据。
- 书籍文件。
- 阅读进度。
- 阅读会话。
- 灵感。
- AI 候选版本。
- 笔记和高亮。
- 标签、分类、书单。
- 阅读器设置。

不得迁移：

- API Key 明文。
- WebDAV 密码明文。
- 无法验证来源的敏感数据。

## 六、测试要求

### 自动测试

每个阶段完成后必须执行：

```powershell
cd D:\develop\Code\Codex\creation-reading-assistant\android

.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

如果新增 instrumentation test：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

任何命令失败都不得宣称阶段完成。

### 真机测试

不得使用 MuMu 模拟器。只使用用户连接的真实手机。

使用 ADB 前必须：

1. 执行 `adb devices`。
2. 确认目标是真机。
3. 所有命令显式指定 `adb -s <serial>`。
4. 测试期间临时保持唤醒。
5. 测试结束恢复原来的息屏设置。

不得卸载应用或清除数据，除非用户明确授权。

### 真机测试书籍

覆盖：

- 小 TXT。
- 大 TXT。
- GBK/GB18030 TXT。
- 无章节 TXT。
- 超长单章 TXT。
- 正常 EPUB。
- EPUB 2。
- EPUB 3。
- 无目录 EPUB。
- 图片章节 EPUB。
- 损坏 EPUB。
- 超大图片 EPUB。

不得修改或删除用户原始书籍，只能复制到测试位置。

## 七、每阶段交付格式

Agent 每完成一个阶段，必须提供：

```text
阶段名称：
修改文件：
问题根因：
实现方式：
没有修改的范围：
自动测试结果：
真机测试结果：
已知剩余问题：
APK 路径：
```

禁止只回复“已修复”“已优化”或“测试通过”。

## 八、停止条件

遇到以下任一情况必须停止并报告，不得继续扩大修改：

- 当前工作区存在无法判断归属的重叠修改。
- Room migration 可能丢失用户数据。
- 需要修改 applicationId。
- 需要删除旧应用数据。
- 第三方代码许可证不明确。
- 真机未连接但任务要求真机验证。
- 测试失败且连续三次修复仍无法定位。
- 修改范围将超出 `android/`。
- 需要引入 WebView、Capacitor 或旧 Web 运行时。

## 九、提交要求

建议每个阶段单独提交：

```text
fix(android): stabilize startup and crash recovery
refactor(android-reader): introduce reader engine contracts
feat(android-reader): add file-backed text reader
feat(android-reader): add lazy epub chapter loading
feat(android-reader): add deterministic pagination
fix(android-reader): persist locator and sessions safely
```

提交前必须：

- 检查 `git diff`。
- 不提交 APK、日志、临时截图或用户书籍。
- 不提交密钥。
- 不夹带桌面端或旧移动端修改。
- 不覆盖其他 Agent 的未提交更改。

## 十、最终完成定义

只有全部满足以下条件，才能宣布原生 Android 主线完成：

- 原生工程可从干净环境构建。
- TXT/Markdown/EPUB 可导入和阅读。
- 大 TXT 不整本载入。
- EPUB 不整本预解析。
- 翻页无文字残留、循环和页边界错位。
- 返回键不会把用户困在阅读器。
- 阅读进度、会话、灵感可持久化。
- 删除书籍后不会复活。
- 主题切换没有残留颜色。
- 真机连续阅读 30 分钟无崩溃、ANR、明显内存增长。
- Room 升级不丢数据。
- 没有依赖 `mobile/` 运行时代码。
- 自动测试、构建和真机测试均有可核验记录。

本计划的关键原则是：先建立稳定的文件读取、定位和分页内核，再完善阅读器交互，最后做视觉打磨。禁止通过增加延时、隐藏错误、限制文件大小或整本加载来伪装修复。
