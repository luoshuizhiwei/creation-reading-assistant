# 原生 Android UX 问题台账

更新日期：2026-08-24

适用范围：`android/` 独立原生 Android 产品线。真机验证只使用当前连接设备；记录统一使用
“测试 TXT”“测试 EPUB”“测试 Markdown”等中性名称。真实书籍截图只保留在本地临时目录，
不提交到仓库。

状态：`OPEN`、`FIXED_CODE`、`DEVICE_VERIFIED`、`BLOCKED`。`FIXED_CODE` 仅表示代码和自动化门禁通过，
不等同于完整真机矩阵通过。

| 编号 | 严重度 | 问题 | 根因与修复 | 验证 | 状态 |
|---|---|---|---|---|---|
| RUX-001 | P1 | 普通控制栏隐藏后正文下方仍永久空出约 160dp | 正文视口错误依赖控制栏实测高度。正文现只避让系统安全区及页眉页脚，普通栏改为覆盖层；控制栏开关不再触发重分页 | `ReaderChromeLayoutTest`；真机隐藏前后截图，隐藏后正文延伸至页脚且页首不变 | DEVICE_VERIFIED |
| RUX-002 | P1 | 横向翻页中出现移动白色矩形纸片，存在文字透叠风险 | `PageTurner` 只覆盖正文列且各页面纸面契约不一致。翻页动画提升到完整阅读视口，正文边距内移，静止页、相邻页、快照页和揭页共用 `ReaderPaperSurfaceSpec` | `PagedReaderViewportGeometryTest`；AndroidTest 编译通过；真机中间帧未见白色纸片或文字透叠 | DEVICE_VERIFIED |
| RUX-003 | P1 | 满高图片可能与分页页眉重叠或被裁切 | 图片最大高度未扣除 `contentTopPaddingPx` | `ChapterPaginatorTest` | FIXED_CODE |
| RUX-004 | P2 | Unicode 忽略大小写搜索可能返回错误原文坐标 | 对全文 `lowercase()` 后索引，某些字符大小写映射会改变字符串长度。改为在原文上用 `regionMatches(ignoreCase = true)` 扫描 | `SearchResultOffsetTest` | FIXED_CODE |
| RUX-005 | P1 | MIUI 安装确认依赖用户手动点击，且旧脚本会把受限安装误报为成功 | 脚本改为推送唯一临时 APK、启动 MIUI 安装器、按包名限定的资源 ID 自动确认，并在完成页核验安装；失败返回非零且清理临时文件 | 主 APK 与 androidTest APK 均在 `c49ac6cf` 上自动安装成功；未修改手机安全设置 | DEVICE_VERIFIED |
| RUX-006 | P1 | 选区工具栏两行七按钮，遮挡正文且单手操作密集 | 主栏收敛为“高亮、笔记、复制、更多”；AI 解读、记为灵感、搜索和取消选择进入更多菜单。颜色选择为独立二级状态，每个色点具名且触控区为 48dp | `ReaderSelectionToolbarModelTest`；Compose 测试编译；测试 TXT 真机截图与 UI dump | DEVICE_VERIFIED |
| RUX-007 | P2 | 搜索空查询时是整片空白；搜索中或取消后会误报“未找到匹配结果” | 搜索面板按查询和会话阶段显式区分引导、搜索中、已取消、无结果和结果列表；空查询显示简短提示，结果区显示总数及当前位置 | `ReaderSearchSheetBodyStateTest`；`ReaderSearchSheetTest` 编译；测试 TXT 真机空查询、80 条结果与下一处 UI dump | DEVICE_VERIFIED |
| RUX-008 | P1 | TXT/EPUB/Markdown 真实阅读矩阵尚未完成 | 需重新导入中性测试样本，覆盖搜索、标注、TTS、恢复、横竖屏及替换能力边界 | 待真机矩阵 | OPEN |
| RUX-009 | P1 | Compose 真机仪器测试进入测试类后挂起，无结果返回 | MIUI Activity/仪器测试启动或 Compose idling 通道异常；本轮两次定向运行均停在测试类入口。已强停清理，不判通过也不判业务失败 | `am instrument` 现场日志；JVM、Lint、APK 和 AndroidTest 编译不受影响 | BLOCKED |

## 2026-08-24 截图证据（本地临时文件）

- 修复前隐藏控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-hidden-before.png`
- 修复后隐藏控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-hidden-after.png`
- 修复后显示控制栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-controls-after.png`
- 修复后横向滑动中间帧：`C:\Users\23254\AppData\Local\Temp\cra-reader-swipe-after.png`
- 紧凑选区主栏：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-toolbar.png`
- 选区更多菜单：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-more.png`
- 高亮颜色二级状态：`C:\Users\23254\AppData\Local\Temp\cra-reader-selection-colors.png`
- 搜索空查询引导与键盘：`C:\Users\23254\AppData\Local\Temp\cra-reader-search-prompt.png`
- 搜索结果列表与结果计数：`C:\Users\23254\AppData\Local\Temp\cra-reader-search-results.png`

这些截图不进入 Git；后续持久化视觉回归应使用仓库内中性 fixture。

## 当前验收边界

- 已满足：普通栏不永久挤压正文、控制栏开关不重分页、横向翻页无移动白色纸片、滑动中间帧无文字透叠、行高默认值统一为 `1.85`。
- 尚未宣称：所有设备尺寸、厂商字体、分屏/折叠屏、真实 EPUB/Markdown、TTS 听感与完整无障碍矩阵均已通过。
- 下一轮顺序：RUX-008 跨格式真机矩阵；RUX-009 仪器测试通道作为独立基础设施问题继续追踪。
