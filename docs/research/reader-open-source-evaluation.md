# 开源阅读器与阅读引擎评估

更新日期：2026-07-16

## 1. 调研方法与边界

- 通过 GitHub API 固定仓库默认分支的 Commit SHA，并读取 LICENSE 与实际 reader、navigator、locator、preferences、annotation、progress、lifecycle 文件。
- 参考源码保存在项目外：`D:\develop\Code\Codex\_research\reader-references`，不进入正式源码与提交。
- GPL/AGPL 项目只参考架构、交互和故障处理，不复制源码。
- 本轮没有从任何参考仓库复制实现，也没有新增第三方依赖。
- 当前项目根目录没有独立 LICENSE；在正式分发引入或复制第三方代码前，需要明确项目许可证并维护 Third-Party Notices。

## 2. 仓库快照

| 仓库 | 分支 | Commit | License | 结论 |
|---|---|---|---|---|
| `readium/ts-toolkit` | `develop` | `ce64e73cfe0d46a9dcd589dfb546d9a8ccd696fd` | BSD-3-Clause | 可依赖/可修改，须保留声明；Navigator/Locator 首要参考 |
| `readium/web` | `main` | `737f11ce689adf77909795d83c0e8dd1fc5db434` | BSD-3-Clause | 项目管理与文档仓库；代码主要位于 TS/Go Toolkit |
| `readium/kotlin-toolkit` | `develop` | `d80f7bd6348daf39cdfa8ac57b0afafb94020945` | BSD-3-Clause | 可依赖；仅在 Web POC 失败时评估 Capacitor 原生插件 |
| `futurepress/epub.js` | `master` | `eee359d0790002115a1156a9833c54f4bcd44c1d` | BSD-3-Clause（文件名小写导致 API 为 NOASSERTION） | 当前项目已依赖；继续使用，但隔离在 Engine 内 |
| `koodo-reader/koodo-reader` | `dev` | `9873733d599a11d8d72f3c19b4c67ea348958462` | AGPL-3.0 | 只参考产品架构和错误恢复，不复制 |
| `Acclorite/book-story` | `master` | `b0ab7d320aed903b56be6eff47b0049b8a7e48bf` | GPL-3.0 | 只参考 Android 状态机、checkpoint 与 UI 分层 |
| `koreader/koreader` | `master` | `6ae320c332bfe37294c5d09c1b7327d975d0e5a4` | AGPL-3.0 | 只参考模块化 ReaderUI、分页/滚动/标注职责 |
| `Anxcye/anx-reader` | `develop` | `107f4fa74db0e7247c846c49d6211df3edf9887c` | MIT | 可参考/复用但需声明；Flutter 外壳不适合直接接入 |
| `edrlab/thorium-reader` | `develop` | `775708b6a4eb2b088ed3abcc0e93f7e9d00447d8` | BSD-3-Clause | 可参考；Electron/Readium Desktop 依赖较重 |
| `johnfactotum/foliate` | `gtk4` | `67b6676d3f936c5edea91d4d903385ef39dd25c0` | GPL-3.0 | 只参考应用层导航与标注 |
| `johnfactotum/foliate-js`（补充） | `main` | `78914aef4466eb960965702401634c2cb348e9b1` | MIT | Web 引擎候选；资源释放与事件模型参考价值高 |
| `edrlab/thorium-web`（补充） | `develop` | `19ad8dedd5f40c2b43c1a09934011e1623059237` | BSD-3-Clause | Readium TS 的 React UI 参考实现 |

## 3. 许可证矩阵

| 许可证 | 允许直接依赖 | 允许复制/修改 | 衍生源码公开要求 | 本项目处理 |
|---|---|---|---|---|
| BSD-3-Clause | 是 | 是 | 无强制开源；需保留版权、条件和免责声明 | 可用于 Readium/epub.js/Thorium；若复制代码则增加 notices |
| MIT | 是 | 是 | 无强制开源；需保留版权和许可 | Anx/Foliate JS 可参考或依赖，仍需 notices |
| GPL-3.0 | 需先确认整个分发许可兼容 | 是 | 分发衍生作品通常需 GPL | Book's Story/Foliate 仅参考架构，不复制 |
| AGPL-3.0 | 默认不直接接入 | 是 | 网络交互场景也有源码提供义务 | Koodo/KOReader 仅参考架构，不复制 |

二级许可证注意事项：

- Koodo 的渲染引擎 `kookit` 为 AGPL-3.0，不能把其 bundle 或源码带入本项目。
- Anx Reader 内置 Foliate JS；Foliate JS 自身为 MIT，但 Anx 的外围实现不能被视为 Foliate JS 的许可替代。
- Readium Toolkit 使用 BSD-3-Clause，但字体、示例资源和可选 DRM/LCP 组件有各自许可证；本项目 POC 不引入这些资源。
- epub.js 当前已是项目依赖；正式发布应在 Third-Party Notices 中保留 FuturePress BSD 声明。

## 4. 重点实现审查

### 4.1 Readium TypeScript Toolkit / Readium Web

关键文件：

- `navigator/src/Navigator.ts`
- `navigator/src/epub/EpubNavigator.ts`
- `navigator/src/epub/frame/FrameManager.ts`
- `navigator/src/epub/frame/FrameBlobBuilder.ts`
- `navigator/src/epub/preferences/EpubPreferences.ts`
- `shared/src/publication/Locator.ts`
- `shared/src/publication/Publication.ts`
- `navigator-html-injectables/src/helpers/locator.ts`

结论：

- `Navigator` 明确分离当前位置、`go()`、前进/后退、事件与 `destroy()`；应用 UI 不负责解析 EPUB。
- `Locator` 使用 `href + type + locations + text`，`progression` 限制在 0～1，可同时用于书签、标注和恢复。
- `Publication` 持有 manifest、readingOrder、TOC 和资源访问，不把书架记录等同于打开后的 Publication。
- EPUB Navigator 用 frame pool、通信桥、Preferences 和 Decorations；偏好注入与导航状态分离。
- 当前 TS Toolkit 的 frame 使用 Readium 自身注入脚本与通信协议，并依赖 Web Publication Manifest/资源服务。它不是把压缩 EPUB ArrayBuffer 直接丢进 React 组件的即插即用库。
- `FrameManager` 的 sandbox 包含 toolkit 自身通信所需脚本能力。若直接接入 Capacitor，必须证明内容脚本与注入脚本隔离，不可简单把 `allow-scripts` 原样移植。

对当前项目的启示：采用 Navigator、Publication、Locator、Preferences、Decoration、destroy 的边界；不在第一步直接替换为 Readium TS。

### 4.2 Readium Kotlin Toolkit

关键文件：

- `readium/navigator/.../Navigator.kt`
- `readium/navigator/.../NavigatorFragment.kt`
- `readium/navigator/.../epub/EpubNavigatorFragment.kt`
- `readium/navigator/.../epub/EpubPreferences.kt`
- `readium/shared/.../publication/Locator.kt`
- `demos/navigator/.../ReaderOpener.kt`
- `demos/navigator/.../LocatorRepository.kt`

结论：

- Navigator 明确声明“不负责持久化最后阅读位置”，持久化属于应用 repository。
- `go(locator)`、当前位置、Preferences、Selection、Decoration 均有稳定接口。
- Fragment/ViewModel 生命周期、进程恢复和 LocatorRepository 值得参考。
- 原生方案在 Android WebView 生命周期和系统返回方面更稳，但需要 Capacitor Plugin、原生 Publication 生命周期、数据桥和 Locator 双向迁移，当前迁移成本最高。

### 4.3 epub.js

关键文件：

- `src/book.js`、`src/spine.js`、`src/navigation.js`
- `src/rendition.js`、`src/managers/default/index.js`
- `src/managers/views/iframe.js`、`src/contents.js`
- `src/locations.js`、`src/epubcfi.js`
- `src/annotations.js`、`src/themes.js`

结论：

- `Book` 已提供 package/manifest/spine/navigation/resources 抽象；`Rendition` 提供 display/next/prev/currentLocation/themes/hooks。
- 默认 manager 一次显示有限章节，continuous manager 会预加载更多章节，移动端内存风险更高。
- 位置以 CFI、href、displayed page 和 locations percentage 表示；字号变化后应以 CFI 恢复，而不是像素页码。
- iframe 默认禁用 scripted content；不能设置 `allowScriptedContent: true`。
- `Book.destroy()`、`Rendition.destroy()`、Contents/Views hooks 必须在切书和卸载时调用。
- 项目现有实现已经围绕 epub.js 修复了非线性 spine、iframe 触控、章节边界和底部安全区，直接丢弃会产生高回归风险。

### 4.4 Koodo Reader

关键文件：

- `src/pages/reader/component.tsx`
- `src/models/BookLocation.ts`
- `src/models/Bookmark.ts`
- `src/assets/lib/kookit.min.js`

结论：

- 阅读页 UI、设置和引擎有明显分层；位置、书签为独立模型。
- 多格式能力依赖 AGPL 的 kookit；只能参考职责划分、错误反馈和产品交互，不能复制 bundle 或实现。

### 4.5 Book's Story

关键文件：

- `presentation/reader/ReaderState.kt`
- `presentation/reader/ReaderModel.kt`
- `presentation/reader/model/Checkpoint.kt`
- `data/parser/file/EpubFileParser.kt`
- `data/parser/text/EpubTextParser.kt`

结论：

- ReaderState/Event/Effect、checkpoint、解析器与 Compose UI 分开，适合参考状态机和恢复流程。
- GPL-3.0，且技术栈是 Kotlin/Compose；只参考设计，不复制。

### 4.6 KOReader

关键文件：

- `frontend/apps/reader/readerui.lua`
- `readerpaging.lua`、`readerrolling.lua`
- `readerbookmark.lua`、`readerannotation.lua`、`readerhighlight.lua`

结论：

- ReaderUI 作为模块协调器，分页、滚动、书签、标注、高亮各自是插件模块。
- 位置以文档引擎稳定锚点/xpointer 处理，并有旧书签迁移和无效锚点降级。
- AGPL-3.0，只参考模块化和容错思想。

### 4.7 Anx Reader

关键文件：

- `lib/page/book_player/epub_player.dart`
- `assets/foliate-js/src/epub.js`、`view.js`、`paginator.js`、`progress.js`
- `lib/providers/bookmark.dart`、`book_toc.dart`

结论：

- Flutter UI 与内嵌 Foliate JS 引擎通过消息边界通信；书签/TOC 独立持久化。
- CFI 用于书签唯一性和跳转；这是“应用层数据不依赖临时 DOM”的正例。
- Flutter 外壳不适合当前 Capacitor 项目；Foliate JS 可单独评估。

### 4.8 Thorium Reader / Thorium Web

关键文件：

- Thorium Reader：`src/common/models/locator.ts`、`src/renderer/reader/components/Reader.tsx`、`readerLocator.ts`
- Thorium Web：`src/components/Epub/epub-core.ts`、`useReaderInit.ts`、`StatefulReader.tsx`

结论：

- Thorium 把 Readium Navigator 包在 React 状态与工具栏之外，Locator 经 Redux/持久层传递。
- Thorium Web 证明 Readium TS 可用于 React，但其资源输入、Provider、Preferences 和注入配置明显重于当前 epub.js 组件。

### 4.9 Foliate / Foliate JS

关键文件：

- Foliate：`src/reader/reader.js`、`src/reader/markup.js`、`src/annotations.js`
- Foliate JS：`epub.js`、`view.js`、`progress.js`、`epubcfi.js`

结论：

- Foliate JS 的 `View` 通过 `load`、`relocate`、annotation 事件与应用通信，并在 destroy 时销毁 renderer。
- EPUB 资源 Blob URL 有集中缓存和 revoke；音频 URL 也在结束/销毁时释放。
- Foliate App 是 GPL，只参考；Foliate JS 为 MIT，可作为未来替代 POC，但当前没有必要同时引入第二套 EPUB 引擎。

## 5. 对 20 个核心问题的归纳答案

1. 成熟实现以 Reader/Navigator 为入口，而不是让页面组件直接解析文件。
2. 文件打开分为校验、容器解析、Publication 构建、Navigator mount；空 readingOrder/spine 不进入 ready。
3. Readium 有正式 Publication；epub.js 的 Book 是事实上的 Publication。
4. package/manifest/spine/TOC 在引擎内归一化，UI 只消费 TOC/Locator。
5. Web 方案普遍通过隔离 iframe/WebView；Kotlin 也是 WebView + 原生生命周期控制。
6. 分页与滚动是 Navigator preference，不应是两套互相竞争的 UI 状态。
7. EPUB 使用 CFI/href/progression；文本使用字符/段落锚点；像素只作短期视口状态。
8. 字号变化后先保存稳定 Locator，再重排并 restore Locator。
9. 目录目标先归一化 href/fragment，再交给 Navigator。
10. Preferences 由独立 editor/repository 注入。
11. EPUB CSS 只进入 iframe 文档；App CSS 不直接穿透。
12. EPUB scripted content 默认关闭。
13. 图片/字体/相对资源由 Publication 的资源加载器或 Blob/custom scheme 提供。
14. 书签/高亮保存 Locator/CFI 和摘录，不保存 DOM Range 对象。
15. 成熟项目均将 UI 与渲染引擎分离。
16. 进入后台时 flush 当前 Locator 和会话，不继续重复计时。
17. 进程重建由 repository 读取 Locator，重新 open/mount/restore。
18. 打开任务必须有 token/taskId，旧结果不得覆盖新书。
19. destroy 释放 iframe、Book/Rendition、Blob、listener、observer 和 cache。
20. 复杂 EPUB 测试需要真实 fixtures、损坏包、无目录/封面、脚本、图片、中文路径和设备运行验证。

## 6. 实际采用内容

- 采用思想：Readium 的 Navigator/Publication/Locator/Preferences/Decoration/destroy 分层。
- 继续依赖：项目已有 `epubjs@0.3.93`，不新增引擎依赖。
- 采用安全原则：scripted content 关闭、iframe 隔离、外部资源默认拒绝、集中 cleanup。
- 未采用/未复制：Koodo、KOReader、Book's Story、Foliate App 的任何 GPL/AGPL 源码。
