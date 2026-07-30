# Reader Engine V2 架构决策

> **历史快照（mobile/Capacitor，非当前 android/ 架构）**：本文记录 2026-07-16 的
> React/epub.js 迁移方案。当前独立原生 Android 以
> [`native-android-reader.md`](native-android-reader.md) 为准。

状态：开发中，Legacy 仍为默认

日期：2026-07-16

## 1. 当前问题

当前移动端已支持 TXT、Markdown、EPUB，但职责分散在：

- `useMobileReaderBook`：文件打开、缓存、状态、history；
- `MobileReaderView`：UI、进度、生命周期、工具栏、格式分支；
- `useReaderDocument`：TXT/Markdown 解析；
- `EpubReaderView`：epub.js Book/Rendition、iframe 事件、导航与位置；
- `useReaderNavigation`：文本分页、章节与进度持久化。

现有代码已有请求序列保护和错误页，但 ReaderState 仍是可选字段较多的普通接口；UI 直接感知 epub.js handle；Locator 仍以现有 `ReadingLocation` 的多处分支构造。继续在组件中增加补丁会提高耦合和竞态风险。

## 2. 方案评分

满分 100，权重按任务要求。

| 方案 | React/Capacitor 25 | EPUB 20 | Locator 15 | Android 10 | 安全 10 | 活跃 5 | 测试 5 | 许可 10 | 总分 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A. Readium TS/Web | 20 | 19 | 15 | 7 | 8 | 5 | 5 | 10 | 89 |
| B. 重新按 epub.js 直接接入 | 24 | 16 | 11 | 7 | 8 | 3 | 4 | 10 | 83 |
| C. 现有 epub.js + Navigator/Locator 适配 | 25 | 17 | 14 | 9 | 9 | 4 | 5 | 10 | **93** |
| D. Readium Kotlin 局部原生化 | 8 | 20 | 15 | 10 | 10 | 5 | 5 | 10 | 83 |
| E. 整套移动端重写 | 3 | 10 | 8 | 6 | 5 | 2 | 2 | 5 | 41 |

## 3. 决策

选择方案 C：保留现有 epub.js 解析/渲染能力，在隔离模块中建立 Reader Engine V2。

原因：

- 当前项目已依赖 epub.js，并已有真实 Android EPUB 修复和用户数据。
- Readium TS 模型优秀，但直接接入需要 Publication Manifest/资源服务、注入脚本通信和更大的迁移面。
- V2 首要目标是把打开、导航、Locator、Preferences、cleanup 从 React UI 中抽离，而不是立即换解析库。
- 方案 C 可逐本/逐格式灰度，可在失败时回退 Legacy，不改变 bookId、同步协议和已有进度。
- 若隔离 POC 证明 epub.js 在 Android WebView 仍有不可规避的能力限制，再重新评估 Readium TS；只有 Web POC 失败且证据指向 WebView 能力边界时才评估 Kotlin 插件。

## 4. 目标结构

```text
features/reader/engine-v2/
├─ types.ts
├─ locator.ts
├─ state.ts
├─ ReaderController.ts
├─ ReaderEngineFactory.ts
├─ LegacyReaderEngineAdapter.ts
├─ EpubReaderEngineV2.ts
├─ TextReaderEngine.ts
├─ MarkdownReaderEngine.ts
├─ ReaderProgressRepository.ts
└─ __tests__/
```

职责：

- `ReaderController`：taskId、状态机、打开取消、最终 flush、回退协调。
- `ReaderEngine`：open/mount/restore/navigation/current locator/preferences/events/destroy。
- `ReaderProgressRepository`：V2 Locator 与旧 ReadingLocation 互转、节流和最终保存。
- `ReaderEngineFactory`：`legacy | v2 | auto` 选择，不允许双引擎同时持久化。
- UI：只显示 state、调用 controller，不解析 EPUB、不直接操作 Book/Rendition。

## 5. ReaderState

```ts
type ReaderState =
  | { status: "idle" }
  | { status: "loading"; bookId: string; taskId: string; phase: ReaderLoadPhase }
  | { status: "ready"; bookId: string; taskId: string; publication: ReaderPublication; engine: ReaderEngine }
  | { status: "error"; bookId?: string; taskId?: string; code: ReaderErrorCode; message: string; retryable: boolean };
```

不变量：

- 旧 taskId 不能提交状态。
- ready 必须至少有一个可读单元。
- error 不携带上一本文本/engine。
- destroy 后不触发状态更新。
- loading/error 都允许返回。

## 6. ReaderLocator 与旧数据兼容

V2 Locator 使用 0～1 的 `progression`：

```ts
interface ReaderLocator {
  version: 2;
  bookId: string;
  format: "txt" | "markdown" | "epub";
  progression?: number;
  chapterId?: string;
  href?: string;
  fragment?: string;
  textOffset?: number;
  paragraphIndex?: number;
  epub?: { cfi?: string; position?: number; totalProgression?: number };
  updatedAt: number;
}
```

兼容策略：

- 旧 `progressPercent` 除以 100，非法值 clamp，NaN 降为 undefined/0。
- EPUB 优先 CFI，其次 href/spine/chapter，最后 progression。
- TXT/Markdown 优先 charOffset/chapterRef/paragraphIndex，最后 progression。
- V2 保存时仍写入现有 `ReadingLocation` 可选字段，不改变同步 payload 的必填结构。
- 不保存 DOM、Blob URL、本地绝对路径。
- 精确 Locator 不可用时按章节或 progression 降级。

## 7. 功能开关与回滚

```ts
type ReaderEngineVersion = "legacy" | "v2" | "auto";
```

- 开发阶段默认 `legacy`，设置保存在本机，不进入同步。
- `v2`：仅使用 V2，失败显示可诊断错误。
- `auto`：先 V2；仅在 open/mount/restore 失败且未写入新 Locator 时回退 Legacy。
- 回退记录 `engine`, `phase`, `code`, `bookId`，不记录正文。
- 同一时刻只有 controller 选中的 engine 可以保存进度。
- 验收前不删除 Legacy。

## 8. EPUB V2 安全边界

- `allowScriptedContent` 永远为 false。
- iframe 必须保留 sandbox；不向 EPUB document/window 注入 Capacitor 对象。
- EPUB 样式只进入 iframe，App CSS 不直接穿透。
- 远程 HTTP/HTTPS 资源默认不主动加载；外链交给应用安全策略。
- 切书/卸载时调用 Rendition.destroy、Book.destroy，移除监听，revoke 自建 Blob URL。
- 日志只记录书 ID、错误码、阶段、资源路径摘要，不记录完整正文。

## 9. 迁移阶段

1. 基础类型、状态机、Locator、Controller、Factory、Legacy Adapter。
2. 隔离 EPUB V2 POC；不接正式书架入口。
3. TXT/Markdown 通过适配器接入，不改稳定解析算法。
4. 正式 ReaderPage 只接 controller 和 state。
5. 旧进度/书签/笔记/灵感来源/统计兼容验证。
6. `legacy/v2/auto` 灰度与 Android 对比。
7. 所有停止条件通过后才把 V2 设为默认。

## 10. 默认启用门槛

当前结论：**V2 不可默认启用，Legacy 继续为默认。**

必须全部满足：

- 用户真实 EPUB 打开、目录、跳转、恢复、字号、前后台、返回稳定；
- TXT/Markdown 无回退；
- P0 全清；
- 无重复进度保存；
- destroy/Blob/listener 测试通过；
- Android 12 与 Android 13+、360/390/412～430 CSS px 验证通过；
- APK 可安装启动；
- 许可证和 notices 完成。
