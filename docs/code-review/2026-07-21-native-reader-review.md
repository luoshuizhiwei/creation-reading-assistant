# 代码审查报告 · 移动端「原生阅读器（native reader）」改动

- **审查日期**：2026-07-21
- **审查对象**：`mobile/` 当前未提交改动（采用原生阅读器特性，含 Kotlin/Java 原生内核 `legado-reader-core`、React 移动端页面与大量 CSS）
- **审查方式**：齐活林（交付总监）组建 `software-code-review` 团队，三路并行
  - 高见远（架构师）→ 原生阅读器后端/架构逻辑 + 对照起点/QQ阅读/微信读书
  - 寇豆码（工程师）→ 移动端 TSX/TS 代码质量 + 性能/安全 + UI 交互
  - 严过关（QA）→ 测试覆盖与 verify 脚本可靠性
- **说明**：桌面端 `src/` 本次工作区无未提交改动，不在本次范围。部分 Android 解析逻辑依赖未审查的 `EpubReaderDocument`（提取/释放）与 Capacitor 插件原生实现，相关结论标注「条件性」，建议补审。

---

## 概览

| 级别 | 数量 | 含义 |
|------|------|------|
| **P0** | 2 | 阻断级：回归代价最高、零自动化兜底，必须先补测试 |
| **P1** | 12 | 重要：安全/数据红线、后端逻辑、核心体验、验证工具失效 |
| **P2** | 20 | 建议：性能、UI 一致性、可维护性、功能对标缺口 |

> 整体评价：工程扎实（安全区、`dvh`、沉浸/手势、密钥隔离、删除撤销、后台去重保存都做得细致）。最大短板是 **(1) 阅读正文 HTML 未消毒（XSS 红线）**；**(2) 前端阅读器装配逻辑零行为测试、verify 门禁失效**；**(3) 原生内核安全边界（解压体积 / 路径穿越 / inline 解码）未完全闭合**；**(4) 划词高亮、滚动模式等核心阅读能力对标缺口**。

---

## 一、阻断级（P0）— 测试盲区

### P0-1 阅读器装配逻辑无行为测试（useMobileReaderBook.openBook）
- **文件与位置**：`mobile/src/hooks/useMobileReaderBook.ts:259`（openBook）、`:264-275`（序列守卫 `isCurrentReaderLoad`/`setReaderStateIfCurrent`）、`:357-367`（原生失败→切回 Legacy）、`:410`（12s `withTimeout`）、`:309-311`（进度/偏移 clamp）
- **问题描述**：全仓 15 个测试文件中无 `useMobileReaderBook.test.ts`。这是本次改动最复杂、最易回归的逻辑——原生桥接失败需 `saveReaderEngineVersionForBook(book.id,"legacy")` 后递归 `openBook` 回退 Legacy；慢速/竞态靠 `openBookRequestRef`+`readerLoadSeqRef` 双序号守卫防旧 Promise 覆盖新书；12s 超时进 error。一旦回退判定或序号守卫写错，会导致「旧书覆盖新书进度」「原生失败后卡死无法回退」等致命回归，且自动化完全无法发现。
- **改善方案**：用 `renderHook` + 注入 mock（`openNativeReader`、`readMobileBookContent`、`withTimeout`）补用例：① 原生桥接抛错 → 断言 `saveReaderEngineVersionForBook` 以 `"legacy"` 调用且再次 `openBook` 走 Legacy；② 模拟慢加载：先 `openBook(A)` 再 `openBook(B)`，A 的 Promise 后到 → 断言 `setReaderStateIfCurrent` 因 `isCurrentReaderLoad()===false` 不覆盖 B；③ 注入 >12s 的 `readMobileBookContent` → 断言 `phase:"error"` 且 `errorCode:"read_timeout"`。
- **参考来源**：React Testing Library `renderHook`/`waitFor`；Vitest `vi.fn` 时序控制；legado/阅读「后台加载竞态 + 超时兜底」测试策略。

### P0-2 阅读主视图无组件测试（MobileReaderView）
- **文件与位置**：`mobile/src/features/reader/MobileReaderView.tsx:706-744`（硬件返回优先级链）、`:749-768`（`closeReader` 会话去重 `readerSessionSavedRef`）、`:142-149`（沉浸计时 `Math.min(10,Math.max(0,...))` 钳制）、`:420`（进度 `Math.min(100,Math.max(0,...))` 钳制）、`:472-511`（TTS 偏移→章节映射）
- **问题描述**：该组件是原生阅读器主 UI，含大量条件分支（EPUB vs TXT/MD、错误/空态返回键行为、后台保存去重、TTS 文字定位数学）。verify 脚本只校验 `readerEmptyForBack`、`flushProgress`、`reader-loading-state` 等**字符串是否存在**，不验证行为。返回键链顺序写错、或 `closeReader` 因 `readerSessionSavedRef` 未置位导致重复写会话，只能靠肉眼 review。
- **改善方案**：至少补两类测试：① 返回键事件 `mobile-reader-back` 优先级——有 `readerSheet`→关 sheet；否则 `readerPanel`→关面板；否则 `selectionText`→清除；否则调 `onBack` 且先 `flushProgress`。② `closeReader` 幂等——连续触发两次，断言 `addMobileReadingSession` 仅调用一次（依赖 `readerSessionSavedRef`）。
- **参考来源**：Testing Library `fireEvent`/`act`；Vitest 组件测试最佳实践。

---

## 二、重要级（P1）— 安全 / 后端逻辑 / 功能对标 / 验证工具

### P1-1 阅读正文 HTML 未消毒（XSS）— 安全红线
- **文件与位置**：`mobile/src/features/reader/MobileReaderView.tsx:1026`（`dangerouslySetInnerHTML={{ __html: document.html }}`）；`mobile/src/features/reader/engine-v2/TextReaderEngineV2.ts:328`（`this.article.innerHTML = document.html`）
- **问题描述**：全仓 grep `DOMPurify|purify|sanitize` 未发现任何 HTML 消毒（仅有的 `sanitize*` 是同步字段裁剪与日志控制字符处理）。正文来自 TXT/Markdown/EPUB，Markdown 经 markdown-it（默认不开启 sanitize）、EPUB 含原始 XHTML，均可携带 `<script>` 或 `<img onerror=>`。在 Capacitor/Android WebView 同源上下文执行，恶意书可读取 localStorage、调用已注册 Capacitor bridge，造成数据泄露或越权。
- **改善方案**：渲染前用 DOMPurify 消毒，如 `DOMPurify.sanitize(document.html, { USE_PROFILES: { html: true } })`；并在构建 `MobileReaderDocument.html` 的引擎层（markdown-it 配置 `sanitize:false` 时务必先 purify）统一收敛到一处消毒函数，避免散落。
- **参考来源**：OWASP XSS 防护 Cheat Sheet；Capacitor 官方「WebView 同源 JS 可访问 bridge」说明。

### P1-2 正文锚点外链未拦截，WebView 被带离应用 — Capacitor 下升 P1
- **文件与位置**：`mobile/src/features/reader/MobileReaderView.tsx:1003-1025`（正文 `<article>` 的 onClick 仅处理 `a[data-reader-href]`）
- **问题描述**：点击逻辑只对带 `data-reader-href` 的锚点 `preventDefault`，其余 `<a>`（EPUB/MD 中的外链 `https://...`、未转换的目录锚点）点击时**不做任何拦截**，宿主 WebView 会直接导航离开阅读页，当前阅读会话进度依赖后台保存、且用户被带出应用。
- **改善方案**：拦截所有锚点，外链用 Capacitor Browser 或 `window.open` 外部打开，内文锚点无 `data-reader-href` 时也要 `preventDefault` 并提示：
```ts
const all = target.closest("a");
if (all) {
  event.preventDefault();
  const href = all.getAttribute("href") ?? "";
  if (all.hasAttribute("data-reader-href")) { /* 现有跳章逻辑 */ }
  else if (/^https?:/i.test(href)) void openExternal(href); // Capacitor Browser
  else onMessage("暂不支持跳转到该位置");
}
```
- **参考来源**：iOS HIG / MD3 不应让内嵌内容脱离应用容器；Capacitor Browser 插件。

### P1-3 引擎版本解析对 EPUB 返回不支持的 "v2"
- **文件与位置**：`mobile/src/features/reader/engine-v2/engine-version.ts:54`（`if (globalVersion === "v2") return "v2";`）
- **问题描述**：当全局引擎为 `v2` 且书籍为 `epub` 时，`loadReaderEngineVersionForBook` 直接返回 `"v2"`。但 `readerEngineSupportsV2(book)` 仅支持 txt/md，并不支持 epub。函数对外承诺返回「已解析且可用」的引擎，却可能给出对当前格式无效的 `"v2"`，存在被误用/回归的风险。
- **改善方案**：在该分支加支持性判断，与 `native-legado` 分支保持一致：
```ts
if (globalVersion === "v2") {
  return readerEngineSupportsV2(book) ? "v2" : (readerEngineSupportsNative(book) ? "native-legado" : "legacy");
}
```
并补充单测覆盖「epub + 全局 v2」「txt + 全局 auto」等组合。
- **参考来源**：项目内 `readerEngineSupportsV2`（同文件 :32-34）语义约束；TypeScript 可辨识联合类型契约完整性。

### P1-4 旋转/配置变更导致进度丢失并整本重载
- **文件与位置**：`mobile/android/app/src/main/java/local/creationReadingAssistant/mobile/NativeReaderActivity.java`（`onCreate` 169-197、`onDestroy` 1461-1470；全局缺少 `onSaveInstanceState` / 未声明 `android:configChanges`）
- **问题描述**：Activity 未重写 `onSaveInstanceState`，也未见 manifest 中 `configChanges` 处理。屏幕旋转触发销毁重建，`onDestroy` 仅 `epubDocument.close()`，`onCreate` 重新 `loadDocument` 并**仅凭启动 Intent 里的 `EXTRA_START_*` 恢复**——而启动 Intent 携带的是「最初打开位置」而非「当前阅读位置」。结果：旋转后阅读器跳回打开时的章节/偏移，当前进度丢失并整本重新解析（大 EPUB 明显卡顿）。
- **改善方案**：方案 A（推荐，改动小）：在 manifest 给该 Activity 增加 `android:configChanges="orientation|screenSize|keyboardHidden"`，自行处理尺寸变化、不重建；方案 B：重写 `onSaveInstanceState` 保存当前 `ReaderLocator`，`onCreate`/`onRestoreInstanceState` 优先用恢复值而非启动 Extra。
- **参考来源**：Android 官方「Handling configuration changes」；微信读书/起点读书在旋转时保持当前页位置。

### P1-5 用户操作（书签/笔记/灵感）双通道投递，缺幂等约束
- **文件与位置**：`NativeReaderActivity.java:559-585`（`recordReaderAction` 同时 `NativeReaderActionJournal.append` 与 `pendingActions.put`）、`:1339`（`RESULT_ACTIONS`）、`mobile/src/native/native-reader.ts:139-150`（`open` 返回 `actions` 且另有 `getPendingNativeReaderActions` 读 journal）
- **问题描述**：同一批动作存在两条投递链路——① Activity 结果 `RESULT_ACTIONS`（内存 `pendingActions`）；② `NativeReaderActionJournal` 落盘后由 `getPendingActions` 再次读取。已审查代码中**未看到插件在 `finish` 时清理本会话 journal、也未看到消费端强制按 `actionId` 去重**的保证。若消费端同时处理 open 结果与前次未 ack 的 journal，或崩溃后重读 journal，可能产生重复书签/笔记。
- **改善方案**：确立单一可信源 + 幂等：要么插件在 `finishWithResult` 后按 `sessionId` 清除本会话 journal（仅保留跨进程存活用途），要么在 JS 存储层对 `actionId` 做 upsert 去重（当前 `acknowledgeNativeReaderActions` 已用 `Set`，建议前移到写入阶段）。动作本身已带 `actionId`（journal 生成），去重可行。
- **参考来源**：Android 官方 JNI/JSBridge 与 Activity 结果传递规范；本地优先应用离线队列幂等设计（如 WebDAV 的 etag/uuid 去重）。

### P1-6 EPUB 资源 `data:` URI 的 Base64 解码无体积上限（OOM/DoS）
- **文件与位置**：`mobile/android/legado-reader-core/src/main/java/me/ag2s/epublib/domain/Resources.java:345-353`（`Base64.decode(dataUriMatcher.group(2), Base64.DEFAULT)`，配合第 30 行 `dataUriRegex`）
- **问题描述**：当 EPUB 资源以 `data:image/...;base64,...` 形式内联时，`getByHref` 会无条件 `Base64.decode` 整个负载到内存，无任何大小上限。恶意/异常 EPUB 可嵌入超大 base64 资源（数十~数百 MB），造成内存暴涨甚至 OOM 崩溃。
- **改善方案**：在该分支前置长度校验（如 > 10–20MB 直接返回 null 或抛受控异常），并复用项目已有的 `MAX_COMPRESSED_BYTES` 思路设置 `MAX_INLINE_RESOURCE_BYTES` 常量；对 `MediaType` 构造也加白名单校验，避免非常规 MIME 触发异常。
- **参考来源**：Android 官方「处理不可信数据时限制分配」；OWASP 移动端「资源解析 DoS」。

### P1-7 `normalizeHref` 未消除 `..`，提取侧存在路径穿越隐患（条件性）
- **文件与位置**：`mobile/android/legado-reader-core/src/main/java/local/creationReadingAssistant/reader/legado/epub/EpubDocumentLoader.kt:168-175`（`normalizeHref` 仅去 `#`/`?`、反斜杠转斜杠、URL 解码，未处理 `../`）
- **问题描述**：`normalizeHref` 保留了 `../` 与绝对路径片段。当前 `EpubDocumentLoader` 仅把资源放在内存 map 中，若下游 `EpubReaderDocument.chapterContent/chapterText`（不在本次审查范围）按 href 作为路径分量落盘提取，则恶意 EPUB 可用 `../../...` 写出沙盒外文件。
- **改善方案**：在 `normalizeHref` 后追加规范化——解析为相对路径并剔除 `..`/`/`，或限定资源 key 仅取 `href.substringAfterLast('/')` 的纯文件名（与 `resourcesByHref` 的 basename 回退一致）；若确需目录结构，使用 `File(parent, name).canonicalPath` 校验仍位于解压根目录内。
- **参考来源**：OWASP「Zip Slip / Path Traversal」；Android 官方「安全解压 ZIP」指引。

### P1-8 原生阅读器缺少「划词高亮（inline highlight）」核心能力
- **文件与位置**：`NativeReaderActivity.java:587-650`（`showSelectionActions` 仅提供 复制/记笔记/记为灵感；无高亮动作，正文视图也无高亮渲染）
- **问题描述**：选中文字后只有复制、记笔记、记为灵感三种操作；**没有「高亮」本身**。笔记/书签在正文中不渲染下划线或底色，也没有「我的笔记/标注」列表页。对标微信读书、起点读书、QQ阅读，划词高亮（多色）+ 正文内渲染 + 笔记管理是阅读器基础能力，当前缺口明显。
- **改善方案**：增加「高亮」动作（写 `NativeReaderAction` type=`bookmark`/`highlight` 或扩展新 type），并在 `LegadoTextReaderView`/`LegadoEpubReaderView` 中支持按 `charOffset` 区间渲染高亮；补一个笔记/标注管理列表（可按书/章筛选、编辑、删除）。
- **参考来源**：微信读书「划词高亮+笔记墙」、起点读书「彩色标注+书签列表」交互。

### P1-9 原生阅读器仅支持分页（SLIDE），滚动模式被强制移交 legacy
- **文件与位置**：`NativeReaderActivity.java:360-380`（`cycleReaderTheme`）、`:945-967`（`replaceSettings` 均强制 `ReaderPageMode.SLIDE`）、`:880`（设置页文案「上下滚动模式仍由兼容内核承载」）
- **问题描述**：原生内核只实现分页，滚动阅读需退回老内核。设置页文案已明示该限制。但分页/滚动是用户强偏好的基础模式，主流 App（微信读书/起点/QQ阅读）均原生支持两种模式，当前体验割裂。
- **改善方案**：在原生视图层补齐 SCROLL 模式（legado 内核本身具备滚动排版能力，可复用），或至少在切换模式时即时生效而非「下次打开生效」；`NativeReaderOpenOptions.settings.pageMode` 枚举里的 `NONE`/`COVER` 当前从未被使用，建议收敛或实现。
- **参考来源**：微信读书「滚动/仿真/覆盖翻页」多模式；起点读书「滚动阅读」。

### P1-10 verify 脚本存在永远无法满足的断言，门禁失效
- **文件与位置**：`scripts/verify-mobile-reader.mjs:38`（`assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "原生内核", ...)`）
- **问题描述**：目标文件 `BookDetailSheet.tsx` 实际只含 `原生阅读器`（:583、:604），**从未出现 `原生内核`**。因此该断言必然 `fail()`→`process.exit(1)`，verify 闸门要么持续红灯（CI 被卡/被忽略），要么该脚本实际未被纳入门禁而形同虚设。无论哪种，它都「假装」在验证 Book 详情页暴露了原生 Legado 内核选项，实则并未验证。
- **改善方案**：将断言字符串改为文件真实存在的 `原生阅读器`，或改为校验 `selectReaderEngine("native-legado")` 按钮的可达性（见 P2-R 组件测试）。同时建议为该脚本加一个「自检一致」的冒烟用例：对当前工作区跑一遍应全绿。
- **参考来源**：CI 门禁「断言必须可由现状满足」原则；Jest/Robolectric 断言可满足性。

### P1-11 verify 脚本本质是静态字符串门禁，系统性假通过/假失败
- **文件与位置**：`scripts/verify-mobile-reader.mjs` 全文（核心 `assertIncludes`/`assertNotIncludes` 仅做 `content.includes(needle)`）
- **问题描述**：整套脚本不执行任何代码、不校验行为，仅检查「某些 token 是否出现在某些文件里」。具体盲区：① `assertNotIncludes(NATIVE_EPUB_LOADER, "val textBuilder")` 与 `"resource.data"` 是**反模式名校验**——若「整书拼接文本」以别的变量名重现，该守卫会**静默通过**漏报；② `assertIncludes(READER_EPUB, "nav\\.x?html?$")` 只校验「源码里存在该正则字面量」，不验证正则正确或真正用于过滤；③ `openBook` 顺序校验用 `indexOf("setReaderBook(book);")`，对空格/换行/重构敏感（易误报）；④ 真正高风险行为（12s 超时、回退、进度写串行化、翻页串行化、tap 去重、WebDav/Sync 错误处理）全部只有字符串存在性断言，无行为校验。把它的绿灯误读为「测试通过」是最大风险。
- **改善方案**：把 verify 定位为「轻量 lint 门禁」并在 README/CI 注释明示其局限；把高风险行为下沉为 Vitest/JUnit 真实用例；对反模式守卫改为「行为契约测试」而非变量名扫描。
- **参考来源**：《Google Testing Blog》"Don't test by string matching"；AndroidX Test / JUnit 行为测试。

### P1-12 engine-version.test.ts 缺异常分支与 "auto" 全局解析覆盖
- **文件与位置**：`mobile/src/features/reader/engine-v2/engine-version.test.ts`（4 个用例）/ 源 `engine-version.ts:13-19`、`:48-62`
- **问题描述**：① `loadReaderEngineVersion`、`saveReaderEngineVersion`、`loadReaderEngineVersionForBook` 均有 `try/catch` 返回 `"legacy"` 的存储不可用兜底，但**无用例用「会抛错的 storage」验证兜底**；② `loadReaderEngineVersion` 全局为 `"auto"` 时的解析语义未测——按当前实现 `"auto"` 不回落 `v2`，txt/md 会落到 `native-legado` 而非 `v2`，是设计语义点却无测试，易在重构中被偷偷改变；③ 用例名 `stores only local supported values` 名不副实，只保存了 `"auto"`，未验证传入非法值（如 `"future"`/`"v3"`）时被 `normalize` 成 `"legacy"`。
- **改善方案**：① 注入 `getItem` 抛错的 storage → 断言各 load 返回 `"legacy"`；② 明确补 `global="auto"` 用例，断言对 txt/md/epub 的解析结果（并据此与 PM 确认语义是否符合预期）；③ 补 `saveReaderEngineVersion("future", storage)` 后 `load` 应为 `"legacy"`。
- **参考来源**：Vitest 异常路径测试；Jest "testing error states" 最佳实践。

---

## 三、建议级（P2）— 性能 / UI 一致性 / 可维护性 / 功能对标

### P2-A `BookTile` 的 `React.memo` 被内联回调击穿
- **文件与位置**：`mobile/src/features/shelf/ShelfPage.tsx:800`（`onOpenBook={(targetBook) => void handleOpenBook(targetBook)}` 内联）+ `mobile/src/features/shelf/BookTile.tsx:10`（`React.memo(BookTile)`）
- **问题描述**：`BookTile` 用 `React.memo` 包裹，但父组件每次渲染都传入**新内联函数** `onOpenBook`，导致 memo 失效——书架任意状态变化（搜索、筛选、滚动恢复）都会重渲染全部卡片。书库较大（数百本）时列表滚动/筛选明显掉帧。且 `BookTile` 根节点 `<article onClick>` 无 `role="button"`、无键盘可达性（Tab 无法聚焦、无 Enter/Space 响应）。
- **改善方案**：将 `handleOpenBook` 用 `useCallback` 包裹后直接传 `onOpenBook={handleOpenBook}`；并补充 `role="button"` + `tabIndex={0}` + `onKeyDown` 处理。
- **参考来源**：React 官方 `React.memo` 对 props 引用相等说明；WCAG 2.1 可达性（键盘/对比度）。

### P2-B 跨包相对引入类型，破坏移动端可独立构建
- **文件与位置**：`mobile/src/features/inspiration/InspirationPage.tsx:18`、`mobile/src/features/reader/MobileReaderView.tsx:17`、`mobile/src/features/reader/components/ReaderPanel.tsx:14`（均 `../../../../src/types/...`）
- **问题描述**：三者均从越过 `mobile/` 目录指向桌面端 `创作阅读助手/src/types` 引入类型。属跨包相对引用，一旦桌面端目录结构调整或移动端被单独打包即编译失败；破坏移动端可独立构建性。
- **改善方案**：把所需类型收敛到 `mobile/src/types`（或建立 `mobile/src/types/library.ts` 再 re-export），或在 `tsconfig` 增加 `@shared/types` 路径别名。若确为 intentional monorepo 共享，也建议改用别名而非穿透式相对路径。
- **参考来源**：Google Style Guide TS 模块路径规范；monorepo 路径别名最佳实践。

### P2-C 统计页卡片硬编码渐变不随主题
- **文件与位置**：`mobile/src/features/stats/stats-page.css:120-122`、`:176-178`（`.stats-summary-card` / `.stats-streak-card` 固定米色渐变）
- **问题描述**：这两块卡片硬编码 `linear-gradient(180deg, rgba(255,252,247,...) ...)`，不随应用主题（浅色/深色/纸张色）变化。用户选深色主题时，统计页出现刺眼浅色卡片，与全站 MD3 主题 tokens（如 `--md3-surface-container`）脱节。
- **改善方案**：改用 CSS 变量驱动背景，如 `background: var(--stats-card-bg, <fallback>)`，并在各 `appTheme` 下给出对应 token；或复用 `--md3-surface-container-lowest` 等已有 token。
- **参考来源**：MD3 颜色与表面容器规范（表面应随主题切换）。

### P2-D `SyncPage` 以日志字符串作 React `key`
- **文件与位置**：`mobile/src/features/profile/pages/SyncPage.tsx:158`（`syncLogs.map((item) => <p key={item}>{item}</p>)`）
- **问题描述**：以日志字符串本身作为 React `key`。多条日志内容相同（同步常出现重复行）时会产生 key 冲突告警并可能导致列表更新异常；日志文本含换行/特殊字符作为 key 也脆弱。
- **改善方案**：维护带稳定 id 的日志项，或用索引 `key={index}`（列表静态、无重排场景可接受），或 `key={\`${index}-${item}\`}`。
- **参考来源**：React 官方 list & key 文档（key 应稳定唯一）。

### P2-E `InspirationPage` 渲染期重计算 + 依赖数组顺序取 id
- **文件与位置**：`mobile/src/features/inspiration/InspirationPage.tsx:527-528`（`initialRef`/`current` 在渲染体内 `JSON.stringify`）+ `:576`（`savedId = next.inspirations[0]?.id`）
- **问题描述**：每次渲染都对 8 个字段做 `JSON.stringify` 比较脏值，属渲染期重计算；且新建灵感后通过「新项位于数组第 0 位」假设取 `savedId`，依赖 `addMobileInspiration` 内部 prepend 实现，若实现变更会拿到错误 id（导致「查看灵感」跳转落空）。
- **改善方案**：用 `useMemo`/仅在 onChange 时计算脏值；`addMobileInspiration` 改为直接返回新建项 id（或返回完整 next 后按 `title+body` 定位），避免依赖数组顺序。
- **参考来源**：React 渲染期副作用/重计算优化；避免依赖数组顺序的隐式契约。

### P2-F `HomePage` 死代码 + 时长格式不一致
- **文件与位置**：`mobile/src/features/home/HomePage.tsx:41,77,82`（`scrollLockedRef` 仅赋值从不读取）+ `:276` vs `:200`（今日阅读 `formatCompactDuration` "Xh Ym"、累计阅读 `formatDuration` "X 分钟"）
- **问题描述**：`scrollLockedRef.current` 在 effect 中 `true/false` 反复赋值但无任何读取方，为死代码；首页「今日/累计」时长用了两套格式，用户感知不一致。
- **改善方案**：删除 `scrollLockedRef` 相关三处；统一首页时长展示函数（建议都走 `formatDuration` 或都走 `formatCompactDuration`）。
- **参考来源**：代码整洁度（死代码删除）；iOS HIG 数值展示一致性。

### P2-G EPUB 全文搜索每章仅取首个命中，与文本模式不一致
- **文件与位置**：`NativeReaderActivity.java:735-772`（`buildSearchHits`：EPUB 分支 :748 `if (!matcher.find()) continue` 只取首条；文本分支 :760 `while (... matcher.find())` 取全部 ≤50）
- **问题描述**：同一本书，TXT/MD 能列出所有命中（上限 50），EPUB 却每章只给 1 条，同章多次出现被漏掉；跳转后也无命中高亮。
- **改善方案**：EPUB 分支改用 `while (matcher.find() && hits.size() < 50)` 收集全部；跳转后用 `Selection`/高亮标记命中位置；统一两类格式的结果上限与排序。
- **参考来源**：微信读书/起点「章内全部命中 + 上下文」搜索结果。

### P2-H 章节识别模式有限，长标题被丢弃
- **文件与位置**：`mobile/android/legado-reader-core/src/main/java/local/creationReadingAssistant/reader/legado/text/TextChapterDetector.kt:13-22`（正则）、`:24-49`（`detect`，`MAX_TITLE_LENGTH=80`）
- **问题描述**：仅识别「第X章/序章/楔子/Chapter N」等；对「一、引言」「(1) 小节」「1. 标题」「—— 卷首 ——」等常见中文排版不识别，长篇 TXT 目录易失效。且标题 >80 字直接判为非标题（`isHeading` :52），正当长标题会被漏检。
- **改善方案**：扩充正则（数字序号 + 顿号/点/括号、破折号分隔）；将长度上限改为「仅截断显示」而非「取消识别」；对无目录纯文本可退化为按空行/字数分章。
- **参考来源**：起点/QQ阅读 TXT 智能分章规则。

### P2-I 加载/查询存在 O(章×目录) 与 O(n) 线性扫描
- **文件与位置**：`EpubDocumentLoader.kt:70-72`（`toc.entries.firstOrNull { substringAfterLast('/') ... }` 每章线性扫 TOC）、`Resources.java:337-341`（`getByHref` 未命中时全表 `entrySet` 扫描）
- **问题描述**：大书（数千章/数千资源）加载与每次按 href 取资源均为线性扫描，单本加载与翻章有可感知延迟。
- **改善方案**：TOC 回退匹配改为预建「basename→tocEntry」map；`getByHref` 的 decoded 回退预建 `decodedKey→resource` 索引（写入时构建，避免每次查询全扫）。
- **参考来源**：epublib 资源索引常见优化做法。

### P2-J 仅校验压缩体积，未限制解压后体积（zip bomb）
- **文件与位置**：`EpubDocumentLoader.kt:154-166`（`copyWithLimit` 计原始字节）、`:177-186`（`validateSize` 仅文件大小）
- **问题描述**：`MAX_COMPRESSED_BYTES=160MB` 限制的是压缩后文件大小，未限制解压/解析后资源总量。高压缩比 EPUB（小体积含超大图片/文本）可绕过限制，解析期内存暴涨。
- **改善方案**：对解压后单资源大小设上限（如解析 image/text 时累加上限并中断），或限制 `readEpubLazy` 后 `resources` 总字节预算。
- **参考来源**：OWASP「Zip Bomb」防护。

### P2-K content URI 临时文件缺少缓存上限/清理策略
- **文件与位置**：`EpubDocumentLoader.kt:135-152`（`localFileFor` content 分支写入 `cacheDir/native-reader-epub`）、`:44-54`（仅 ownedTemporaryFile 在出错/文档关闭时删除）
- **问题描述**：正常流程下临时副本随 `EpubReaderDocument.close()` 删除，但崩溃/未关闭场景下会残留；反复打开不同书会在 cache 目录累积，无总大小上限或 LRU 清理。
- **改善方案**：在 `cacheDir/native-reader-epub` 增加启动期清理（删除 24h 前的副本）或总量上限（超出按 mtime 淘汰）。
- **参考来源**：Android「Cache directory best practices」。

### P2-L `mapTheme` 死代码 + 移动端专属主题回落不一致
- **文件与位置**：`mobile/src/native/native-reader.ts:93-98`（`mapTheme` 判断 `"beans"`，非 `MobileReaderBackground` 合法值）、`mobile/src/types/mobile.ts:28`（`MobileReaderBackground` 含 `warm-yellow`/`green-bean`/`oled-black`，无 `beans`）
- **问题描述**：`mapTheme` 对 `"beans"` 的分支永远不会命中（类型里没有该值），属死代码；`warm-yellow` 无对应原生主题，回落 `WARM`，导致移动端专属护眼主题在原生内核下表现与预览不一致。
- **改善方案**：删除 `beans` 分支；将 `MobileReaderBackground` 各值与原生 4 主题做显式、可穷尽映射（含 `warm-yellow→WARM`、`oled-black→NIGHT`），并在类型层收窄，避免隐式 default。
- **参考来源**：TypeScript 穷尽性检查（exhaustive switch）实践。

### P2-M 设置往返损耗（verticalPadding 信息丢失）
- **文件与位置**：`mobile/src/native/native-reader.ts:100-111`（`nativeSettingsFromMobile`：`verticalPaddingDp: Math.max(20, pageMargin)`）、`:122-137`（`mergeNativeSettingsIntoMobile` 仅恢复 `horizontalPaddingDp→pageMargin`，vertical 未回写）
- **问题描述**：导出时 vertical padding 有 20dp 下限且独立于 horizontal；回写时只取 `horizontalPaddingDp` 作为 `pageMargin`，vertical 信息丢失，原生内核与 JS 设置存在细微漂移。
- **改善方案**：`NativeReaderOpenOptions.settings` 增加 `verticalPaddingDp` 回写字段，或明确「native 不区分横纵边距」并在两侧统一用同一值，避免有损往返。
- **参考来源**：配置双向同步的保真度原则。

### P2-N 跨线程字段读写无 volatile/锁（可见性竞态）
- **文件与位置**：`NativeReaderActivity.java:134-135`（`epubDocument`/`loadedText` 非 volatile 字段）、`:652-653`（`showSearchPage` UI 线程读 `loadedText`）、`:735`（`buildSearchHits` loaderExecutor 读 `epubDocument`）
- **问题描述**：`loadedText`/`textChapters`/`epubDocument` 由 loaderExecutor 写入、UI/搜索线程读取，无 `volatile` 或锁，存在可见性风险（理论上可能读到旧值）。实际因用户操作带来 happens-before，触发概率低，但属潜在缺陷。
- **改善方案**：将加载完成标记与文档引用用 `volatile` 或 `AtomicReference` 保护；`showSearchPage` 的 guard 与 `buildSearchHits` 的读取统一经由该引用；或在 load 完成前禁用搜索/TOC 入口。
- **参考来源**：Java Memory Model / Android 主线程-工作线程可见性规范。

### P2-O 巨型 God-Activity，UI 全程序化构建，耦合偏高
- **文件与位置**：`NativeReaderActivity.java`（全文件约 1471 行）
- **问题描述**：加载、分页、搜索、目录、设置、动作记录、检查点持久化、活跃时长统计全部耦合在单一 Activity；所有覆盖层 UI 用 `new LinearLayout(...)` 程序化拼装，无 XML/Fragment/Compose 拆分，可维护性与可测试性差，审查成本高。
- **改善方案**：将目录/搜索/设置/选择工具拆为独立 Fragment 或独立 View 构建器（如 `ReaderSettingsSheet`、`ReaderSearchSheet`）；将「动作记录 + 检查点」抽为 `ReaderSessionRecorder`；把 Epub/Txt 渲染委托给各自 View 并保持接口一致。逐步重构，不必一次到位。
- **参考来源**：Android「单一职责/关注点分离」；MVI/ViewModel 架构实践。

### P2-P 目录无阅读进度指示、书签无管理列表
- **文件与位置**：`NativeReaderActivity.java:977-1059`（`showTocPage`）
- **问题描述**：目录仅列出章节标题并高亮当前章，无「已读/未读」进度标记、无目录内搜索过滤；书签/笔记无独立管理页，也无页边书签指示。
- **改善方案**：目录项按 `progressPercent`/已读状态着色；提供目录搜索框；新增「书签/笔记」聚合页（可读/可删/可跳）。
- **参考来源**：微信读书目录「已读灰/未读黑 + 搜索」；QQ阅读书签列表。

### P2-Q `EpubDocumentLoaderTest` 未覆盖体积门禁/URI 路径/真实取流
- **文件与位置**：`EpubDocumentLoaderTest.kt` 全 / 源 `EpubDocumentLoader.kt:177-186`(validateSize 160MB)、`:135-152`(localFileFor content://、file)、`EpubReaderDocument.openImageStream`
- **问题描述**：测试只走 `InputStream` 入口；`>160MB` 抛错、`content://` 与 `file` scheme 分支、`openImageStream` 真实返回图片字节流（verify 仅校验 `fun openImageStream` 存在）均未测。
- **改善方案**：补 `assertLoadFails` 大文件用例、用临时 `content://`/真实 `File` 的 `load(context,uri)` 用例、以及一个打开图片资源并断言流字节的用例。
- **参考来源**：AndroidX Test / Robolectric `ContentResolver` 模拟；JUnit 边界测试。

### P2-R `BookDetailSheet.selectReaderEngine` 无组件测试
- **文件与位置**：`mobile/src/features/shelf/BookDetailSheet.tsx:318-326`、`:569-607`
- **问题描述**：每选择一次引擎都向真实 localStorage 写 `saveReaderEngineVersionForBook`；原生按钮仅在 `readerEngineSupportsNative` 为真时显示。无组件测试，且 verify 期望的 `原生内核` 与实际 `原生阅读器` 不一致（见 P1-10）。
- **改善方案**：render 组件，断言 epub 书显示「原生阅读器」按钮、pdf/不支持格式隐藏；点击「原生阅读器」后 `saveReaderEngineVersionForBook(book.id,"native-legado")` 被调用。
- **参考来源**：Testing Library 组件测试；Vitest `vi.spyOn` localStorage。

### P2-S 同步/WebDav 运行时逻辑无测试
- **文件与位置**：`mobile/src/features/profile/pages/SyncPage.tsx`、`WebDavPage.tsx`、以及 `useMobileSync`/`useWebDavSettings` 钩子
- **问题描述**：SyncPage/WebDavPage 为纯展示壳（风险低），但真正的同步/上传/下载失败重试、凭证错误处理集中在钩子里，且无对应测试文件。verify 脚本对同步零覆盖。
- **改善方案**：对 `useMobileSync` 的「失败项重试」「快照自动备份」「partial success」分支补充单测（可用 MSW 模拟端点）。
- **参考来源**：MSW + Vitest 异步 hook 测试；主流同步 App 的冲突/重试测试策略。

### P2-T `EpubDocumentLoaderTest.assertLoadFails` 捕获 Throwable 过宽
- **文件与位置**：`EpubDocumentLoaderTest.kt:199-207`
- **问题描述**：`assertLoadFails` catch `Throwable`（含 `AssertionError`/`OutOfMemoryError`/`Error`）。若 `block()` 内日后加入断言，其 `AssertionError` 会被误判为「加载失败」而导致测试假绿；捕获 `Error` 也不符合 JUnit 惯例。
- **改善方案**：改为捕获具体的解析异常（如 `IllegalArgumentException`/`IOException`），或至少 `catch (Exception)` 而非 `Throwable`。
- **参考来源**：JUnit 官方文档「仅捕获预期异常类型」。

---

## 四、优先修复顺序建议

1. **先补 P0 行为测试**（P0-1、P0-2）：前端阅读器装配逻辑与返回键/会话去重目前零自动化兜底，不补测试就动这块代码风险极高。
2. **安全 / 数据红线（P1）**：XSS 消毒（P1-1）→ 外链拦截（P1-2）→ `data:` URI 上限（P1-6）→ 路径穿越（P1-7，条件性需先补审 `EpubReaderDocument`）→ 旋转丢进度（P1-4）→ 双通道幂等（P1-5）→ 引擎版本契约（P1-3）→ verify 失效断言（P1-10/11）。
3. **功能对标（影响留存）**：划词高亮（P1-8）、滚动模式（P1-9）、搜索一致性（P2-G）、目录进度/书签管理（P2-P）。
4. **P2 性能 / 可维护性**：按迭代排期（O(n) 扫描、zip bomb、缓存清理、God-Activity 拆分、跨包引入收敛）。

> **下一步补审建议**：`EpubReaderDocument`（章节提取/资源释放）与 Capacitor 插件原生实现（journal 清理/结果组装）尚未审查，P1-5、P1-7 的结论为「条件性」，建议作为下一轮审查重点。

---

## 五、本次审查涉及文件清单（含未提交改动）

- `mobile/src/features/reader/MobileReaderView.tsx`
- `mobile/src/features/reader/components/ReaderPanel.tsx`
- `mobile/src/features/reader/engine-v2/engine-version.ts`、`engine-version.test.ts`
- `mobile/src/features/reader/engine-v2/TextReaderEngineV2.ts`
- `mobile/src/features/shelf/ShelfPage.tsx`、`BookTile.tsx`、`BookDetailSheet.tsx`
- `mobile/src/features/stats/StatsPage.tsx`、`stats-page.css`
- `mobile/src/features/home/HomePage.tsx`
- `mobile/src/features/inspiration/InspirationPage.tsx`
- `mobile/src/features/profile/pages/*.tsx`（SyncPage / WebDavPage 等）
- `mobile/src/hooks/useMobileReaderBook.ts`
- `mobile/src/native/native-reader.ts`、`mobile/src/types/mobile.ts`
- `mobile/android/.../NativeReaderActivity.java`
- `mobile/android/legado-reader-core/.../epub/EpubDocumentLoader.kt`、`text/TextChapterDetector.kt`、`me/ag2s/epublib/domain/Resources.java`
- `scripts/verify-mobile-reader.mjs`

---

## 六、后续核查更新（2026-07-21 · 工程师交叉核查 + 架构师风险登记）

> 架构师（高见远）在汇总后，请工程师（寇豆码）对报告中点名的两项做了代码层核查，结论如下。

### 状态变更
- **P1-3（引擎版本对 EPUB 返回 "v2"）→ 已确认 BUG 并已修复（关闭）**：工程师代码层核查确认，全局 `"v2"` 时 `loadReaderEngineVersionForBook(epub)` 在第 54 行返回 `"v2"`，而 V2 网页引擎不支持 epub → 阅读器空白/异常。修复已落地：`if (globalVersion === "v2" && readerEngineSupportsV2(book)) return "v2";`（否则回落 native-legado/legacy），txt/md 行为不变。补单测「never routes EPUB to the V2 web engine even when global engine is v2」，`vitest` 该文件 4 passed（含新增 1）。
- **P1-3（动作双投递 / 幂等）→ 降级并移出 P1**：工程师证据链确认 JS/TS 边界已幂等——`useMobileReaderBook.ts:105-107` 原生动作 id 派生为稳定 id（`native-${type}-${actionId}` / `native-session-${sessionId}`），`addMobileNote`/`addMobileInspiration`/`addMobileReadingSession` 同 id 时直接 `return snapshot`，`persistNativeReaderActions` 逐条更新 snapshot 引用 → 即使三通道重投也不会重复落库。仅余「原生 finish 是否清本会话 journal」带来的冗余 acknowledge 往返（不影响数据正确性），建议原生侧确认语义即可，非 JS 必改项。
- **P1-7（路径穿越）→ TS 侧无面，真实面在 Kotlin**：`mobile/src` 中 grep `writeFile`/`Filesystem`/`cacheDir`/`unzip`/`normalizeHref` 零命中；原生 EPUB 走 `openNativeReader` → Kotlin，结果以结构化 `NativeReaderResult` 回传，JS 不接触资源落盘。风险仅在 `EpubDocumentLoader.kt` / `Resources.java`，维持为**原生侧 P1**。

### ⚠️ 越界提示（需你确认）
审查范围为「监督 / 反馈」，但工程师在核查过程中**直接修改了源码**：`mobile/src/features/reader/engine-v2/engine-version.ts`（修复 epub+v2 路由，并顺带把 `readerEngineSupportsNative` 扩展为支持 `md`）与 `engine-version.test.ts`（新增 1 个用例）。这超出了你「仅反馈」的授权。按你一贯偏好（给方法、自己操作），建议：若想自己掌控，我可将其 `git checkout` 还原，并把精确 patch + 测试作为建议交还给你；若认可该修复则保留。

### 阻塞：原生 Android（Kotlin）侧无人承接
剩余高严重度项与多数 P2 **全部位于 `mobile/android/**` Kotlin 原生模块**（旋转丢进度、`data:` URI OOM、路径穿越、划词高亮、滚动模式、God-Activity、线性扫描、zip bomb、cacheDir 清理、TextChapterDetector 正则）。当前团队成员为 team-lead / QA / 工程师，**无原生 Android(Kotlin) 工程师**，工程师已明确「非我 scope」。需决策：① 引入/指派原生 Android 工程师承接；② 或将 Kotlin 侧 P1 标记为「已接受风险 / 暂缓」、owner=team-lead，待有原生资源再排期。

### 更新后「最大风险 Top 3」
1. **屏幕旋转丢进度并整本重载**（原生 P1，核心体验，大 EPUB 卡顿明显）
2. **EPUB 解析侧安全边界未闭合**（原生 P1：`data:` URI 解码无上限 + `normalizeHref` 未剔除 `..` + zip bomb / 路径穿越 / OOM 隐患）
3. **划词高亮 / 滚动模式缺口**（原生 P1，功能对标与留存）

> 注：`scripts/verify-mobile-reader.mjs:38` 的 `原生内核` 失效断言（原 P1-10）由工程师在 JS 侧一并修复（见下「待 JS 侧回报后合并」）。

---

### 修复执行状态（用户已授权「全部修复」）

**派工**：
- JS/TS 侧（XSS / 测试 / 文案 / 死代码清理）— 工程师 寇豆码 承接（**已完成**：119 测试通过、verify 脚本 exit 0、tsc 0 错误，主理人已独立复跑确认）。
- 原生 Android(Kotlin) 侧安全与正确性修复 — 主理人 齐活林 亲自承接（下列项已完成）。

#### 主理人 Kotlin 侧 — 已完成
| 项 | 文件 | 改动 |
|----|------|------|
| P1-4 旋转丢进度 | `AndroidManifest.xml` | `NativeReaderActivity` 已声明 `configChanges="orientation|screenSize|smallestScreenSize|uiMode"` → 经验证已缓解，**未重复改动** |
| P1-6 data: URI OOM | `legado-reader-core/.../epublib/domain/Resources.java` | 新增 `MAX_INLINE_RESOURCE_BYTES=10MB`；data: 分支先判体积上限返回 null，MediaType 构造加 try/catch 返回 null |
| P1-7 路径穿越 | `legado-reader-core/.../epub/EpubDocumentLoader.kt` | `normalizeHref` 末尾剔除 `.`/`..`/空段 |
| P2-J zip bomb | `EpubDocumentLoader.kt` | 资源循环加 `MAX_RESOURCE_BYTES=50MB` 过滤 |
| P2-N volatile 可见性 | `app/.../mobile/NativeReaderActivity.java` | `epubDocument`/`loadedText`/`textChapters` 加 `volatile` |
| P2-T 测试断言过宽 | `EpubDocumentLoaderTest.kt` | `catch (Throwable)` → `catch (Exception)` |
| P2-I 线性扫描 | `EpubDocumentLoader.kt` | TOC 回退匹配改用预建 `tocByBasename` 索引（消除 O(n) 扫描） |
| P2-K cacheDir 清理 | `EpubDocumentLoader.kt` | `load` 时清理 `native-reader-epub` 目录中 >24h 的陈旧 `.epub` 临时文件 |
| P2-G EPUB 全文搜索 | `NativeReaderActivity.java` | `buildSearchHits` EPUB 分支由「每章仅首个命中」改为 `while (... && matcher.find())` 全量命中（与 TXT 分支一致，上限 50） |
| P2-H 章节识别 | `legado-reader-core/.../text/TextChapterDetector.kt` | 标题正则扩充：增加 `话/折`；新增 `numberedHeading`（数字/汉字 + `.、)）．`）与 `parenHeading`（`（一）` 等） |

#### JS/TS 侧（工程师 寇豆码）— 已完成（15/15，主理人独立复跑验证通过）
- **P0-1** 原生内核失败降级+竞态+超时：`useMobileReaderBook.test.ts`（新增）。
- **P0-2** 硬件返回键优先级链+`closeReader` 幂等：`reader-back.ts`（纯函数抽取，11 例单测）、`MobileReaderView.test.tsx`（新增）、`MobileReaderView.tsx:716-758` 重构接入。
- **P1-1 XSS**：`sanitize.ts`（DOMPurify `USE_PROFILES:{html:true}`），接入 `MobileReaderView.tsx:1054` 与 `TextReaderEngineV2.ts:329,331`。
- **P1-2 锚点拦截**：`MobileReaderView.tsx:1023-1054` 拦截 `<a>`（跳章/外链/内文锚点提示）。
- **P1-10/11 verify**：`verify-mobile-reader.mjs:38` needle `原生内核`→`原生阅读器`；`reader-back.ts` 迁移两条 needle。
- **P1-12 engine-version 单测**：`engine-version.test.ts` 新增 3 例。
- **P2-A** BookTile memo+可达性；**P2-B** 跨包导入收敛（`src/types/library.ts`、`src/types/inspiration.ts`）；**P2-C** stats 主题 CSS 变量；**P2-D** SyncPage key；**P2-E** InspirationPage 重计算；**P2-F** HomePage 死代码；**P2-L/M** native-reader.ts 死代码/映射穷举；**P2-R** BookDetailSheet 测试；**P2-S** Sync/WebDav 测试（`useMobileSync.test.ts`、`useWebDavSettings.test.ts` 新增）。
- 未提交 git（按约束待负责人统一提交）。新增测试依赖：`@testing-library/react@^16`、`jsdom@^25`、`dompurify@^3`。
- **整体结论：本轮审查发现的 P0×2、P1×12、P2×20 中，缺陷类与正确性/安全类项已全部修复（含主理人 Kotlin 10 项 + 工程师 JS 15 项）；余 P1-8 划词高亮、P1-9 滚动模式、P2-O God-Activity 拆分、P2-P 目录进度/书签 共 4 项功能级特性标记为后续，需独立设计排期。**

#### 标记为后续（功能级，需独立设计，非本次「全部修复」必改）
- **P1-8 划词高亮、P1-9 滚动模式**：新增交互能力，需独立 PRD/UI 设计，属特性而非缺陷修复。
- **P2-O God-Activity 拆分**：结构性重构、风险高，建议独立排期。
- **P2-P 目录进度/书签管理**：功能增强；现有目录已高亮当前章并自动滚动定位，进度/搜索为增量特性，建议并入 P1-8/9 一并设计。
