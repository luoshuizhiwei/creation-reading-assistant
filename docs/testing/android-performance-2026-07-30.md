# Android 性能优化验证记录（2026-07-30）

## 范围

本轮覆盖原生 Android 主线的启动、主页面切换、书架图片、统计聚合、TXT 打开路径与阅读器订阅范围。未修改数据库实体、同步协议、Locator、分页几何或阅读进度格式。

## 已实施

- 新增 `:benchmark` Macrobenchmark/Baseline Profile 模块，包含冷/热启动、主页面切换、书架滚动与搜索、打开阅读器和关键路径 Profile 生成器。
- 启动普通跟踪改用系统 Trace；上次崩溃回放、重命名和临时文件清理延后到首页数据可用并完成 fully-drawn 报告之后。
- `AppLog` 改为线程安全时间格式和固定容量持久列表缓冲。
- Home、Stats、Shelf、Profile 的组合与派生计算移至 ViewModel/后台 Flow；Home 与 Stats 使用单一稳定 UI 状态。
- Stats 改用 Room 窄投影，仅读取统计需要的列，不再在 Composable 收集五张完整实体表。
- Home、Stats 长页面改为带稳定 key 的惰性列表；封面与阅读器图片按实际布局/视口尺寸请求解码。
- Reader 合并路由状态，书摘、笔记、灵感和会话只观察当前书籍；分类和标签仅在相关弹层打开时订阅。
- TXT 内部文件直接读取，避免每次打开整书复制；大文件索引增加磁盘缓存和失效校验。
- 修复 Room 1–6 版 schema 快照中外键建表 SQL 的括号错误，并把测试 schema 复制产物移至 `build/generated`。

## 本地验证

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 446 项通过，0 失败，0 跳过 |
| Android Lint | 0 error；保留 89 warning |
| Debug APK | 构建成功 |
| Debug instrumentation APK | 构建成功 |
| Benchmark app APK | 构建成功 |
| Macrobenchmark test APK | 构建成功 |
| `git diff --check`（本轮文件） | 通过，仅有行尾转换提示 |

## 真机阶段状态

- 全程仅使用真实设备 `c49ac6cf`，未使用 MuMu。
- 长时间测试期间将 `stay_on_while_plugged_in` 从 `0` 临时设为 `3`，结束后已恢复为 `0`。
- MIUI 会阻止 Compose 测试框架从后台启动宿主 Activity；仅在仪器测试期间临时将自定义 AppOp `10021` 设为 `allow`，结束后已恢复为 `ignore`。
- 中间版本 Debug APK 的 10 次 `am start -W -S` 冷启动对比：
  - 改动前 P50：2314.5 ms；P95：2402.9 ms。
  - 中间版本 P50：1407 ms；P95：1523.15 ms。
  - P50 改善 39.2%，P95 改善 36.6%。
- 上述结果是 Debug APK 的同设备人工对比，只能作为趋势证据，不是最终 Macrobenchmark 结论。
- Room schema 已正确打包进 AndroidTest APK；最终 Debug APK 上 29/29 项真机仪器测试通过。
- 使用指定目录内的真实文件导入“测试 TXT”“测试 EPUB”，最终书架保留 9 本测试书籍；未修改来源目录。

## Macrobenchmark 结果

所有场景均使用 profileable benchmark 构建，每项 10 次。首轮 Profile 前为空数据状态；Profile 后书架已有真实数据，因此启动前后数据用于观察趋势，不作严格 A/B 归因。

| 场景 | 指标 | 结果 |
|---|---|---:|
| 冷启动 | 首帧 P50 | 543.3 ms |
| 冷启动 | fully drawn P50 | 781.1 ms |
| 热启动 | 首帧 P50 | 172.4 ms |
| 热启动 | fully drawn P50 | 279.7 ms |
| 五个主页面切换 | `frameOverrunMs` P95 | 16.9 ms |
| 书架搜索 | `frameOverrunMs` P95 | 6.0 ms |
| 书架滚动 | `frameOverrunMs` P95 | 6.2 ms |
| 测试 EPUB：打开与菜单开合 | `frameOverrunMs` P95 | 11.6 ms |
| 测试 TXT：打开与菜单开合 | `frameOverrunMs` P95 | 13.7 ms |

Baseline Profile 嵌入后，主页面切换的 `frameOverrunMs` P95 从 26.8 ms 降至 16.9 ms，改善约 36.9%，但仍未达到计划中的 P95 不大于 0。冷启动首帧中位数从 585.8 ms 降至 543.3 ms；由于第二轮已有真实书架数据，只作为趋势参考。

## Baseline / Startup Profile

- 生成器已拆成仅启动路径与主页面关键路径，避免把五个页面全部错误标记为启动必编译。
- `app/src/main/startup-prof.txt` 与 `app/src/main/baseline-prof.txt` 已保存。
- Benchmark APK 中 `assets/dexopt/baseline.prof` 为 20,705 字节、`baseline.profm` 为 2,966 字节，低于 Android 的 1.5 MB 二进制 Profile 上限。

## 最终设备状态

- 仅保留当前原生主应用 `com.creationreadingassistant`；仪器测试包与 benchmark 包均已卸载。
- 历史 Capacitor 应用 `local.creationReadingAssistant.mobile` 未触碰。
- 当前原生版本：`0.4.0-p4`（versionCode 1）。（注：2026-07-30 测试时点快照；当前版本见 `docs/release/ANDROID_RELEASE.md`，发布经 `-PcraVersionCode/-PcraVersionName` 注入。）
- 主应用可启动，书架数据仍在；常亮与 MIUI 临时权限均已恢复。

## 后续性能重点

1. 优先分析 Home → Profile（19.9 ms）和 Home → Stats（15.9 ms）的 Perfetto Trace，避免为快速的 Inspiration 路径增加无意义预加载。
2. 优先处理 TXT 文档加载（`ReaderOpen` 中位 228.0 ms）、首屏分页呈现（超帧 P95 209.6 ms）和菜单完整往返（35.0 ms）；翻页与跳章次之。
3. 200+ 本书架优先优化按书名排序触发的全量列表重排（41.2 ms）；封面缓存已经把暖滚动 P95 降至 5.5 ms。
4. 继续缩小启动触达面；当前原始 startup profile 较宽，说明首屏仍加载了较多框架与页面代码。

## 后续体验闭环（按 3 → 4 → 6 → 1 → 2 → 5 → 7 实施）

### 3：批量文件与文件夹导入

- 文件选择支持一次选择多个 TXT、Markdown 与 EPUB。
- 文件夹导入使用 SAF 文档树递归扫描，不依赖真实文件系统路径；限制扫描深度、访问文件数与候选书籍数，避免异常目录拖垮应用。
- 导入过程串行解析大型文件，并提供成功、重复、跳过、失败的批次进度与结果“收据”；失败项可单独重试。
- 修复导入历史未从 DataStore 取回旧记录的问题。

### 4：丢失或移动书籍重新定位

- 书籍详情提供“重新定位文件”，并明确保留阅读进度、书签和笔记。
- EPUB 在完整解析新文件后才原子替换旧副本，失败时恢复备份。
- TXT/Markdown 重新选择后统一复制到应用内部目录，使用原子替换，并更新本地 URI 与内容路径。
- 保留原书籍 ID、用户标题与作者信息；文件可用性检查不再把不存在或空的本地文件判断为可读。

### 6：阅读连续性

- TXT 进度记录精确绝对字符偏移；旧版百分比记录仍可回退恢复。
- EPUB 滚动与分页模式都按章节内偏移恢复。
- 初次恢复完成前禁止后台位置回调用 0 覆盖旧进度。
- 应用进入后台、停止或阅读页释放时立即保存最新位置。

### 1、2：可定位的页面与阅读器性能基准

- 五个主页面切换拆为独立场景，每个场景 10 次，不再用单个混合 P95 掩盖具体慢页面。
- 阅读器拆为测试 EPUB/TXT 首次正文、菜单开合、翻页和 EPUB 跳章。
- `ReaderOpen` Trace 从打开请求覆盖到正文加载完成或失败，首屏耗时不再依赖固定等待时间。

### 5：隔离的 200+ 本书架压力场景

- Benchmark 构建使用 `com.creationreadingassistant.benchmarktarget`，与用户日常应用的数据库、文件和偏好完全隔离。
- Benchmark 专用 Seeder 生成 200 本混合元数据书籍及中性的测试 TXT/EPUB，覆盖长标题、混合格式与封面缓存。
- 增加大书库排序、冷封面滚动、暖封面滚动与搜索场景。

### 7：极端布局与可访问性

- 新增 320dp、393dp、430dp/矮横屏，以及字体缩放 1.3、1.5 的阅读器控制区测试。
- 验证主要操作可点击、至少 48dp，并保持在根布局边界内。
- 长错误文案在 320dp、1.5 字体缩放下仍需显示返回与重试操作。

### 本轮验证状态

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 446 项通过，0 失败，0 跳过 |
| Android Lint | 0 error；89 warning |
| Debug / AndroidTest / Benchmark APK | 全部构建成功 |
| `git diff --check -- android` | 通过，仅有行尾转换提示 |
| 正式 Debug APK | 已在真实设备 `c49ac6cf` 覆盖安装，保留应用数据 |
| 真机 instrumentation | 33 项通过，0 失败；包含 4 项极端阅读布局 |
| 拆分后的 Macrobenchmark | 页面、阅读器与 202 本书架场景均完成 10 次有效采样 |

### 拆分后真机性能结果

所有数据来自真实设备 `c49ac6cf` 的 profileable Benchmark 构建；除特别标注外均为 `frameOverrunMs P95`，每项 10 次。

| 场景 | 结果 |
|---|---:|
| Home → Shelf | 8.1 ms |
| Home → Inspiration | 1.9 ms |
| Home → Stats | 15.9 ms |
| Home → Profile | 19.9 ms |
| Shelf → Home | 11.7 ms |
| 测试 EPUB 文档加载 `ReaderOpen` 中位 | 20.5 ms |
| 测试 EPUB 首屏分页呈现超帧 P95 | 23.5 ms |
| 测试 TXT 文档加载 `ReaderOpen` 中位 | 228.0 ms |
| 测试 TXT 首屏分页呈现超帧 P95 | 209.6 ms |
| 阅读菜单打开并关闭 | 35.0 ms |
| 测试 EPUB 翻页 | 11.4 ms |
| 测试 TXT 翻页 | 12.9 ms |
| 测试 EPUB 跳章 | 6.5 ms |
| 202 本书架冷缓存滚动 | 8.1 ms |
| 202 本书架搜索并清空 | 10.2 ms |
| 202 本书架按书名排序 | 41.2 ms |
| 202 本书架暖封面缓存滚动 | 5.5 ms |

### 真机测试中修正的基准缺口

- 阅读菜单准备阶段改为只在菜单可见时隐藏，并确认显隐完成；单次显隐偶尔没有可归属帧，因此最终场景定义为完整的“打开 → 关闭”往返。
- 文档加载完成不等于分页布局完成；分页宿主新增“分页正文已就绪”语义标记，翻页与菜单测试等待真实页面生成后再开始。
- `ReaderOpen` 只报告目标应用内的文档加载阶段；首屏分页呈现使用等待“分页正文已就绪”期间的帧指标，两者分开记录，不把加载完成误称为第一页已经绘制。
- 书架筛选后重新获取 UI Automator 对象，避免异步重组产生 `StaleObjectException`。
- 每轮书架压力测试先停止隔离目标包并显式启动 `MainActivity`，清除搜索页、键盘和旧返回栈对下一轮的干扰；这些操作都在测量区外。

测试结束后已卸载 `com.creationreadingassistant.test`、`com.creationreadingassistant.benchmark` 和 `com.creationreadingassistant.benchmarktarget`；202 本压力数据随隔离包删除。`stay_on_while_plugged_in` 已恢复为 `0`，MIUI AppOp `10021` 已恢复为 `ignore`。未使用 MuMu，未向用户日常书库写入压力数据。


## 基线复测（2026-08-22，分页引擎重写后）

设备同 `c49ac6cf`（Redmi 22081212C），`:benchmark:connectedBenchmarkReleaseAndroidTest`，10 次迭代，Baseline Profile 已嵌入。当日书架为空库（上午数据被清），读场景 6 项因种子未落地跳过，见下。

| 场景 | 指标 | 2026-08-22 | 2026-07-30 参考 |
|---|---|---:|---:|
| 冷启动 | 首帧 P50 | 554.6 ms | 543.3 ms |
| 冷启动 | fully drawn P50 | 723.4 ms | 781.1 ms |
| 热启动 | 首帧 P50 | 210.6 ms | 172.4 ms |
| 热启动 | fully drawn P50 | 340.5 ms | 279.7 ms |

结论：冷启动与 07-30 基本持平（首帧 +2%，fully drawn −7%），分页引擎重写未伤启动路径；热启动略升（数据/条件不同，趋势参考）。

**本机 benchmark 库无法从该设备提取 frameOverrunMs 百分位**（perfetto trace 无 expect/actual slice，`switchHomeToStats` 即因此失败）；07-30 的 frameOverrun 数字继续作为流畅度参照。本轮捕获 frameCount P50：页面切换 27–30 帧、书架滚动 83.5、搜索 123、按名排序 83（空库+种子前，仅供同条件回归对比）。

**待办**：① 读场景 6 项（打开 TXT/EPUB、翻页、跳章、菜单）——书架空导致 `BenchmarkSeedActivity` 种子后仍搜不到书，需在下一轮真机 session 排查种子落地与 08-05 后搜索路由交互（`filterShelfForBenchmark` 依赖的搜索流已迁独立页）；② `switchHomeToStats` 的 perfetto 提取失败需升级 benchmark 库或换 trace 解析。