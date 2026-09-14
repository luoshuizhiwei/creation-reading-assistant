# 移动端阅读器 Bug 与复现矩阵

> **历史资料（停止作为当前验收依据）**：本文档混合了已删除的 Capacitor/WebView 产品线、
> MuMu 模拟器和早期原生 Android 记录。当前独立原生 Android 的 UX 问题、修复状态与
> 真机证据统一维护在 [`native-android-ux-ledger.md`](native-android-ux-ledger.md)。除追溯历史外，
> 不要从本矩阵推断当前实现状态。

更新日期：2026-07-18

状态说明：`已复现`、`已修复待用户真机`、`通过模拟器`、`真机通过`、`未验证`。

## 1. 已知问题基线

| 编号 | 格式 | 操作 | 预期 | 基线实际/日志 | 严重度 | 状态 | 可能根因 |
|---|---|---|---|---|---|---|---|
| B05 | 全部 | 重复导入同一文件 | 生成独立记录并标记重复 | 真实 TXT 已生成“重复导入 #2”独立副本，两条记录均可打开 | P1 | 真机通过 | 导入层允许重复，使用 duplicateIndex/importLabel 区分 |
| C01 | TXT | 跨章左滑后右滑 | 整页对齐，无旧页残影 | 真实 8 MB TXT 压力翻页未再次观察到旧页残影 | P0 | 真机通过 | WebView transform 合成 + 章节 DOM/定位时序 |
| C03 | TXT/EPUB | 返回书架后重新进入 | 直接恢复正确位置 | 短暂显示预览或错误页 | P1 | 通过模拟器 | readerPreview 被当作正文；恢复晚于首绘 |
| D01 | EPUB | 阅读接近页面底部 | 最后一行完整可见 | 用户真实 EPUB 页面底部完整，滚动模式无横向白条 | P0 | 真机通过 | 已统一正文安全区与滚动容器边界 |
| D02 | EPUB | 连续点击左右区域 | 单次触发一次前进/后退 | 真实 EPUB 第 6→7 页与第 7→6 页均单击一次只翻一页 | P0 | 真机通过 | 将整章 iframe 坐标映射到可见 viewport，去除重复事件入口 |
| D03 | EPUB | 任意页点击中央 | 立即显示/隐藏菜单 | 用户真实 EPUB 任意可见页中央点击立即显示菜单 | P0 | 真机通过 | iframe document 统一处理点击，外层不再重复接管 |
| R-OPEN-01 | 全部 | 快速连续打开 A/B 两书 | 最终只显示 B | 现代码有 requestId/seq 防护 | P0 | 需新增自动化 | 过期异步覆盖 |
| R-EMPTY-01 | TXT/MD | 打开空文件 | 错误页可返回 | 用户真实目录没有空文件，本轮未制造样本 | P0 | 未验证 | 空内容校验 |
| R-EPUB-01 | EPUB | 无 spine/空 spine | 明确解析错误 | 不得白屏/崩溃 | P0 | 未验证 | Publication 可读单元校验 |
| R-MISSING-01 | 全部 | 正文文件缺失 | 错误页、返回、重新导入 | 不重置旧进度 | P0 | 部分自动门禁 | 文件边界与错误状态 |
| R-PLACEHOLDER-01 | 全部 | 打开同步占位书 | 提示下载正文 | 不进入假正文 | P0 | 部分自动门禁 | contentStatus 边界 |
| R-LIFE-01 | 全部 | 后台/锁屏/前台 | flush 一次且不重复计时 | EPUB 后台/强停恢复位置通过；重复计时仍需日志级断言 | P0 | 部分真机通过 | visibility/appState 双事件 |
| R-RESTORE-01 | 全部 | 修改字号后重开 | 恢复到同一文本附近 | 真机调整字号后位置保持，设置已恢复 18 | P1 | 真机通过 | Locator 稳定性 |
| R-CLEANUP-01 | 全部 | 连续进入退出 20 次 | iframe/监听/Blob 不增长 | 真实 TXT 20 次无崩溃；内存回收后低于测试前 | P1 | 真机通过 | destroy 资源释放 |
| R-EPUB-SCROLL-01 | EPUB | 切换上下滚动并滑动正文 | 正文连续滚动、进度变化 | 真实 EPUB 已可纵向连续滚动，进度随位置变化 | P0 | 真机通过 | epub.js flow 与阅读容器已分模式处理 |
| R-SELECTION-01 | TXT/EPUB | 长按选中文字 | 显示复制/搜索/灵感/笔记/高亮 | TXT 与 EPUB 均出现应用选区工具栏；EPUB“记为灵感”表单保留选中文字“似的”作为来源摘录 | P0 | 真机通过 | iframe selection 已转发到应用工具栏 |
| R-THEME-01 | 全部 | 切换夜间主题后打开二级页 | 正文、顶底栏、二级页统一变暗 | 目录/设置二级页仍为白色 | P1 | 已复现 | reader theme token 未覆盖 portal/secondary page |

## 2. 格式与文件样本矩阵

| 样本 | 最低检查 | 当前状态 |
|---|---|---|
| 普通 TXT | 打开、目录、分页/滚动、恢复、返回 | 用户真实 TXT 真机通过 |
| 大 TXT | 不阻塞 UI、不重复全书解析 | 用户真实约 8 MB TXT 真机通过主链路 |
| 普通 Markdown | 标题/列表/引用/代码/表格、恢复 | 未验证 |
| 长 Markdown | 无横向溢出、内存稳定 | 未验证 |
| 标准 EPUB | 正文、TOC、前后翻页、CFI 恢复 | 用户真实 EPUB 真机通过 |
| 多章节 EPUB | spine 边界前后导航 | 用户真实多章节 EPUB 真机通过核心导航 |
| 无封面 EPUB | 第一可读 spine 正确 | 未验证 |
| 无目录 EPUB | 有 spine 即可读，TOC 空状态 | 未验证 |
| 图片 EPUB | 图片与相对资源、缩放、内存 | 未验证 |
| 中文路径 EPUB | 导入、资源 URL、恢复 | 未验证 |
| 复杂 CSS EPUB | 样式隔离、分页与主题 | 未验证 |
| 含脚本 EPUB | 脚本不执行，不能访问 Bridge | 未验证 |
| 损坏 EPUB | 错误页、重试、返回 | 未验证 |
| 空 spine EPUB | 不能进入 ready | 未验证 |
| 用户问题 EPUB | D01-D03、目录、恢复、字号 | 2026-07-18 Redmi 真机通过 |

## 3. 生命周期与竞态矩阵

| 场景 | 证据要求 |
|---|---|
| 快速 A→B 切书 | A 的 open/parse/mount 结果不得提交；A engine 必须 destroy |
| 打开中返回 | loading 可退出；随后 Promise 不得 setState |
| error 返回 | 不保存 0% 进度，不残留上一本文本 |
| ready 返回 | 最终 Locator flush 一次，保存失败不阻止返回 |
| 切后台 | visibility 与 Capacitor appState 合并去重 |
| 进程恢复 | 从 repository 读取 Locator，找不到精确位置时降级 |
| 设置变化 | 先捕获 Locator，再应用偏好并恢复，不使用旧像素页码 |
| destroy | iframe、Book、Rendition、Blob URL、listener、observer 全释放 |

## 4. Android 验收记录模板

| 字段 | 值 |
|---|---|
| 设备 | 待记录 |
| Android | 待记录（最低覆盖 12 与 13+） |
| Android System WebView | 待记录 |
| CSS viewport | 360 / 390 / 412～430 |
| devicePixelRatio | 待记录 |
| 测试文件 | 文件名、格式、大小、是否真实问题样本 |
| Engine | legacy / v2 / auto |
| 初始 Locator | 脱敏 JSON |
| 结束 Locator | 脱敏 JSON |
| 结果 | 通过/失败、错误码、截图路径 |
| 性能 | 首开时间、明显卡顿、内存趋势 |

### 2026-07-16 MuMu 设备回归记录

| 字段 | 值 |
|---|---|
| 设备 | MuMu 12，`SM_S9280`，ADB `127.0.0.1:16416` |
| 版本 | Android Debug APK，应用版本 `0.1.26` |
| 测试样本 | `reader-smoke-test.txt`、`reader-epub-smoke-test.epub` |
| TXT | 连续前翻 4 次到末章，再连续后翻 4 次回首章；无半页夹缝、旧页残影或循环 |
| EPUB | 中央点击立即显示菜单；第一章/第二章左右点击单步切换；末尾连续右点不循环 |
| 安全区 | EPUB 最后一行完整显示，外层容器按 border-box 计算，iframe 正文保留至少 48px 底边距 |
| 重新进入 | 高频截图未观察到错误章节或预览正文闪现；首帧仅有同一正确页面的 200ms 淡入 |
| 限制 | 模拟器当前为横向 Surface；用户竖屏真实 EPUB 仍需安装新包复测 D01-D03 |

## 5. 2026-07-16 Redmi K50 Ultra 真机结论

- Android 15 / SDK 35，1220×2712，CSS viewport 约 407×904，DPR 3。
- 使用 `luoshuizhiwei/reads/起点` 中真实 8 MB TXT 与 7.4 MB EPUB。
- TXT 打开、分页、目录、搜索、返回恢复和压力翻页通过。
- EPUB 解析/目录/滑动可用，但 D01、D02、D03、滚动模式和选区工具条仍失败。
- 详细证据见 `docs/testing/mobile-real-device-regression-2026-07-16.md`。

## 6. 2026-07-18 Redmi K50 Ultra 全量回归增量结论

- 测试目录：`/sdcard/luoshuizhiwei/reads/起点`，目录内 18 个 TXT、7 个 EPUB、0 个 Markdown。
- TXT：左右点击、左右滑动、目录、搜索跳转、书签、笔记、高亮、主题、进度恢复和后台恢复通过，无半页夹缝或上一页残影。
- EPUB：中央点击、左右点击单页前后翻、左右滑动、纵向滚动、目录、选区工具栏、选中文字记为灵感及来源摘录通过。
- 书架：重复导入、重复标签、网格/列表、排序、搜索、详情页、删除及重启后不复活通过。
- 其他页面：灵感详情/编辑/来源/AI 未配置提示，统计周期切换，我的二级页、扫码真实预览、更新检查均通过。
- 未覆盖：用户目录没有 Markdown；无封面、无目录、图片型、含脚本、损坏和空 spine EPUB 仍需隔离样本；本轮没有可用 WebDAV 或桌面同步服务凭据，因此只验证了页面与错误边界。
- 已发现非阻断体验问题：重复导入成功提示以“没有成功导入书籍”开头，语义矛盾；阅读样本不足时剩余时长可能显示数千小时；统计页没有单独“日”周期；书架搜索会命中未直接展示的原始文件名。

详细证据见 `docs/testing/mobile-real-device-regression-2026-07-18.md`。

## 7. 当前停止条件判断

- 当前 Legacy 阅读器的用户真实 TXT/EPUB P0 主链路已通过。
- V2 的隔离结构和单元测试已建立，但复杂 EPUB 样本、安全/资源专项和更多 Android/宽度矩阵仍未全部完成。
- Android 12 有既有模拟器记录，Android 15 真机完成主流程；Markdown 真机与复杂 EPUB 矩阵仍缺样本。

因此当前 Legacy 继续保持默认；V2 可继续灰度开发，但尚不可设为默认。
