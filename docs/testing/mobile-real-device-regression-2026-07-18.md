# Android 真机全量回归记录（2026-07-18）

## 1. 结论

本轮在用户真实手机和真实书籍目录上完成了移动端主链路回归。此前阻断阅读的 EPUB 点击区域错误已修复：整章分页 iframe 的坐标现在会换算为可见阅读区域坐标，中央点击、左右点击和左右滑动不再互相误判；重复的 rendition 触摸/点击监听也已移除。

当前结论：Legacy 阅读器可继续作为默认；Reader Engine V2 保持可回滚灰度状态，不设为默认。

## 2. 环境

| 项目 | 值 |
|---|---|
| 设备 | Redmi K50 Ultra（Xiaomi 22081212C） |
| Android | 15 / SDK 35 |
| 分辨率 | 1220 × 2712 |
| 密度 | 480 dpi |
| CSS viewport | 约 407 × 904 |
| devicePixelRatio | 3 |
| Android System WebView | `com.google.android.webview 149.0.7827.159` |
| 应用 | `local.creationReadingAssistant.mobile` 0.1.26（versionCode 26） |
| 引擎开关 | Legacy 默认；V2 未默认启用 |
| 书籍目录 | `/sdcard/luoshuizhiwei/reads/起点` |
| 目录格式统计 | TXT 18、EPUB 7、Markdown 0 |

## 3. 阅读器结果

### TXT

- 打开用户真实 TXT，首屏、后续页和章节边界可读。
- 左右点击和左右滑动均为整页移动，没有半页错位、夹缝或上一页文字残留。
- 目录二级页可滚动，硬件返回恢复原页。
- 搜索 `505` 返回 2 个结果，点击结果跳到正确正文并选中命中词。
- 高亮、笔记、书签入口可用；黄色高亮保存后出现在高亮列表。
- 白纸、暖纸、护眼、夜间主题完整切换；设置二级页可返回。
- 强制结束并重新启动后，从首页继续阅读恢复到原 TXT 页与约 0.68% 进度。
- 前后台切换后阅读页和位置保持。

### EPUB

- 用户真实 EPUB 可打开，无白屏、无退出应用。
- 中央点击立即显示菜单。
- 第 6→7 页右点一次只前进一页，第 7→6 页左点一次只后退一页。
- 左右滑动可前后翻页；纵向滚动模式可连续滚动，无横向白条。
- 目录、设置、搜索等均为阅读器内二级页。
- 长按正文出现 Android 选择条和应用工具栏：高亮、记为灵感、存笔记、搜索。
- 点击“记为灵感”后，表单的独立“来源摘录”带入选中文字“似的”，并保留书名与章节来源。
- 页面底部正文没有被工具栏或安全区裁掉。

## 4. 书架与业务页面

- 同一本 TXT 可作为独立副本导入，详情显示“重复导入 #2 · 导入于 2026/7/18”。
- 删除该测试副本后，切换页面、强制结束并重新启动，书籍数稳定从 6 变为 5，副本未复活。
- 网格为三列，列表行高一致；排序下拉、搜索、详情二级页均可用。
- 灵感列表紧凑；详情页显示结构化来源；编辑页可滚动并包含标题、正文、标签、类型与状态；AI 未配置时给出中文配置提示。
- 统计页的本周、本月、本年、累计可切换，无崩溃。
- 我的页面的阅读设置、应用外观、我的阅读、存储、标签、分类、书单、局域网同步、WebDAV、AI、关于均可进入二级页。
- 标签、分类、书单各实际执行了临时新增和删除。
- 扫码页显示真实摄像头预览、关闭与粘贴 URL 备用入口；清空 logcat 后重复开启/关闭未复现 renderer crash。
- 检查更新返回“已是最新版本”，未再出现 GitHub 403。
- 二级页按返回键逐级关闭；到首页后连续返回最终退出应用。

## 5. 自动化与构建

通过的命令：

```text
npm test --prefix mobile -- --run                       73 passed
npm run mobile:build                                   passed
npm run verify:mobile-*                                all passed
npm run verify:beta                                    passed
npm run build                                          passed
npm run cap:sync --prefix mobile                       passed
mobile/android/gradlew.bat assembleDebug               passed
```

APK：

- 路径：`mobile/android/app/build/outputs/apk/debug/app-debug.apk`
- 大小：23,297,459 字节
- SHA-256：`6DAE7DA9CBFBBFDC75F8FC365B7518FC5DFF51D1D9583838BC8E59D669CB5118`

## 6. 证据

截图和 UI XML 位于：

`mobile/android-screenshots/full-regression-20260718/`

关键文件：

- `037-epub-center-fixed.png`
- `038-epub-before-right-fixed.png` ～ `042-epub-swipe-right.png`
- `051-epub-scroll-before.png`、`052-epub-scroll-after.png`
- `059` ～ `065`：TXT 翻页、目录和返回
- `067` ～ `069`：TXT 搜索
- `088-highlight-list.png`、`089.xml`：高亮与笔记
- `095` ～ `101`：主题、设置和返回
- `103-progress-restored-after-relaunch.png`
- `115-book-detail-page.png`、`119-after-delete-relaunch.png`
- `145-scan-preview.png`
- `155-epub-longpress.png`、`156-epub-inspiration-form.png`

## 7. 未覆盖与已发现问题

未覆盖：

- 用户目录没有 Markdown 文件，因此 Markdown 只有构建、门禁与 fixture 自动化证据，没有本轮真实文件真机证据。
- 无封面、无目录、图片型、复杂 CSS、含脚本、损坏、空 spine EPUB 未形成完整隔离样本矩阵。
- 本轮没有可用桌面同步服务、WebDAV 账号和 AI 配置，因此未执行真实网络端到端写入。
- 尚未完成 360、390、412～430 三档独立设备/模拟器截图矩阵。

确认的非阻断问题：

1. 重复导入成功提示以“没有成功导入书籍”开头，语义矛盾。
2. 阅读样本不足时“预计还需”可能显示数千小时，应达到最低阅读时长/字数后再估算。
3. 统计页缺少单独“日”周期。
4. 书架搜索会命中原始文件名等未直接展示字段，结果原因不够直观。

上述问题不阻断 Legacy 当前真实 TXT/EPUB 主链路，但应作为下一轮 P2 体验修复。
