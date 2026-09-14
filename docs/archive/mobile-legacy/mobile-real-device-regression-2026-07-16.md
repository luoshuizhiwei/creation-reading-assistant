# Android 真机完整回归报告（2026-07-16）

## 1. 测试范围

- 应用版本：`0.1.26`
- 主设备：Redmi K50 Ultra（22081212C），Android 15 / SDK 35，1220×2712，CSS viewport 约 407×904，DPR 3
- 辅助设备：MuMu 12（SM-S9280），Android 12 / SDK 32，1080×1920，density 280
- 测试书籍：仅使用手机目录 `luoshuizhiwei/reads/起点` 中的真实书籍
- 重点样本：
  - `《亚人娘补完手册》作者：伊巍蟹.txt`（8,067,284 字节）
  - `亚人娘补完手册 (伊巍蟹) (Z-Library).epub`（7,390,002 字节）
  - `当青春幻想具现后 (转角吻猪) (Z-Library).epub`（1,466,640 字节）
  - `反正我是超能力者 (吃书妖) (Z Library)`（已导入 EPUB）
  - `回到过去变成猫 (陈词懒调) (Z-Library).dt`（不支持格式验证）
- 证据目录：`mobile/android-screenshots/real-device-20260716/`

## 2. 总结

自动构建和门禁全部通过，但真机仍存在阅读器 P0 缺陷。当前版本不应因为自动测试全绿就判断为可发布。

### 2.1 阻塞发布的问题

| 编号 | 严重度 | 结果 | 真机现象 | 证据 |
|---|---:|---|---|---|
| R-EPUB-TAP-01 | P0 | 失败 | 多本 EPUB 点击左右区域不翻页；滑动可以翻页 | `175-epub-right-20.png`、`180-large-epub-after-taps.xml`、`181-large-epub-after-swipe.png` |
| R-EPUB-MENU-01 | P0 | 失败 | 部分 EPUB 页面点击中央不唤出菜单；滑动到其他页面后才可能恢复 | `176-epub-center-menu.png`、`178-epub-center-after-swipe.xml` |
| R-EPUB-SCROLL-01 | P0 | 失败 | EPUB 切换为“上下滚动”后，在正文章节向上滑动，画面和进度完全不变 | `190-vertical-text-before.png`、`191-vertical-text-after.png` |
| R-SELECTION-01 | P0 | 失败 | TXT、EPUB 选中文字后只出现 Android 原生复制菜单，应用的“记为灵感/笔记”工具条不出现 | `38-txt-selection-real.png`、`194-epub-selection.png` |
| D01 | P0 | 失败 | 指定 EPUB 在竖屏页面底部存在正文裁切/被底栏遮挡 | `14-epub-right-2.png` |

### 2.2 高优先级体验问题

| 编号 | 严重度 | 结果 | 真机现象 | 证据 |
|---|---:|---|---|---|
| R-THEME-01 | P1 | 失败 | 夜间主题只覆盖正文，目录/设置等二级页面仍为白色，主题不统一 | `83-epub-night-theme.png` |
| APP-BACK-01 | P1 | 失败 | 首页第一次返回显示“再按一次退出”，但在提示时限内第二次返回仍不退出 | `92-home-back-once.png` |
| SYNC-SCAN-01 | P1 | 失败 | 相机权限通过后扫码仍失败，提示 `play() request was interrupted by a new load request` | `145-scan-camera.png`、`145-scan-camera.xml` |
| STATS-TIME-01 | P1 | 失败 | 统计页显示约 1.1 小时，但“我的阅读”累计与单本时长全部为 0 秒 | `101-stats-total.xml`、`108-reading-history.png` |
| DIAG-LOG-01 | P2 | 失败 | 扫码错误未进入诊断日志，诊断页仍显示 0 条日志/0 个错误 | `149-diagnostics.png` |
| TTS-FEEDBACK-01 | P2 | 待确认 | 点击听书后无可见状态变化、无错误提示；是否实际发声仍需人工听觉确认 | `200-tts-panel.png`、`200-tts.xml` |
| WEBDAV-VALIDATE-01 | P2 | 失败 | WebDAV 配置为空时点击测试连接没有清晰的必填提示 | `146-webdav-page.png`、`147-webdav-validation.png` |

## 3. 已通过项目

### 3.1 应用框架与导航

- 五个底部 Tab 可切换，页面状态基本保持。
- 全局搜索可检索书籍、灵感和来源，并高亮命中词。
- 阅读器二级页面（书籍信息、AI 面板等）按 Android 返回键可优先关闭，再返回正文。
- 应用浅色、深色、跟随系统可切换；测试后已恢复“跟随系统”。
- Android 12 辅助环境可启动，无启动崩溃。

### 3.2 书架与导入

- 真实 8 MB TXT 和 7.4 MB EPUB 可导入、可打开。
- 重复导入同一本书允许生成独立记录并有重复标识。
- 网格/列表切换、搜索、排序、筛选、详情页和删除持久化通过。
- 不支持的 `.dt` 文件在系统文件选择器中被禁用，不能误导入。
- 取消文件选择可返回应用，不产生新书。

### 3.3 TXT 阅读器

- 大 TXT 首开、目录、跨章分页、左右翻页、返回恢复通过。
- 连续右点 40 次、左点 20 次无崩溃、ANR 或 OOM。
- 当前版本未再次观察到半页夹缝或旧页残影。
- 搜索、书签、笔记、无选中文本记灵感、来源记录通过。
- 选中文字后的应用自定义工具条仍失败，见 `R-SELECTION-01`。

### 3.4 EPUB 阅读器

- 多本真实 EPUB 可解析，目录可打开并跳转章节。
- 左右滑动可跨 spine 进入下一章节。
- 后台、强制停止后重进可恢复阅读位置。
- 修改字号后恢复到同一文本附近；测试后字号已恢复 18，阅读模式恢复“左右翻页”。
- 横竖屏切换时阅读器未崩溃，恢复竖屏后仍可继续阅读。
- 点击区域、滚动模式、选区工具条和部分底部安全区失败，见阻塞项。

### 3.5 灵感、统计与个人中心

- 灵感创建、详情编辑、来源卡片、卡片菜单、删除通过；测试数据已清理。
- 标签、分类、书单新增/重命名/删除通过；临时数据已清理。
- 笔记/书签列表和删除通过；临时数据已清理。
- 日/周/月/年/总统计切换与前后周期导航通过。
- 数据导出可调起系统分享；数据导入可调起文件选择器。
- 关于页更新检查本次成功，显示 0.1.26 为最新版本，没有复现 403。
- 隐私、存储、AI 设置、同步、WebDAV 页面可进入。

### 3.6 稳定性与资源释放

- 真实 TXT 连续进入/退出 20 次，无崩溃、ANR、OOM。
- PSS 从 273,242 KB 短时升至 295,503 KB；发送系统内存回收后降至 260,498 KB，未观察到不可回收的单调增长。
- 真机测试期间 logcat 未发现 `FATAL EXCEPTION`、`ANR`、`OutOfMemoryError` 或 fatal signal。

## 4. 自动验证

以下命令全部通过：

- `npm run mobile:build`
- `npm test --prefix mobile`：10 个测试文件、70 个测试全部通过
- `npm run verify:mobile-reader`
- `npm run verify:mobile-reading-experience`
- `npm run verify:mobile-ui`
- `npm run verify:mobile-storage`
- `npm run verify:mobile-inspiration`
- `npm run verify:mobile-import-chain`
- `npm run verify:beta`
- `npm audit --prefix mobile --omit=dev`：0 个漏洞

构建仍有一个非阻塞告警：移动端主 chunk 约 778 KB，建议后续继续按页面/阅读器引擎拆包。

## 5. 因条件不足未完成的项目

以下项目不能标记为通过：

- Markdown：用户指定的“起点”目录中没有 `.md` 样本。
- 空 TXT/Markdown、损坏 EPUB、空 spine、无目录/无封面 EPUB：真实目录中没有可确认的异常样本；本轮未制造或破坏用户文件。
- WebDAV 成功上传/下载：缺少可用 WebDAV 账号。
- 局域网完整同步：本轮未启动桌面同步服务；扫码入口本身已复现失败。
- AI 实际生成：移动端未配置独立 API Key，本轮没有读取或写入任何密钥。
- TTS 发声质量：ADB 不能代替人工听觉验收。

## 6. 修复顺序建议

1. 统一 EPUB iframe 内的点击、中央菜单和手势转发，只保留一个导航入口并做互斥锁。
2. 修复 EPUB scroll flow：滚动容器、iframe 高度、`rendition.flow("scrolled-doc")`/对应布局必须一致。
3. 建立跨 iframe 的 `selectionchange` 桥接，选区变化时显示应用工具条；原生菜单不能是唯一入口。
4. 统一 reader theme tokens 覆盖正文、顶底栏和全部二级页面，并修复底部安全区。
5. 修复首页双返回状态机、扫码 video 生命周期、统计时长数据源一致性。
6. 为上述真机缺陷补自动测试；当前门禁全部通过却未发现这些问题，说明门禁偏“代码存在性检查”，不足以证明运行时行为。
