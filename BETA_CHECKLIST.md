# Beta Readiness Checklist

> **历史快照**：本文件记录 2026-06-30 的桌面端/Capacitor v0.1.2 Beta 验收，
> 不代表当前独立原生 `android/` 的发布状态。当前文档入口见 `docs/README.md`。

Test date: 2026-06-30
Version: 0.1.2
Expected artifact: `release-beta/win-unpacked/创作阅读助手.exe`

## Maintenance update: 2026-06-30

- Replaced native browser confirmation dialogs with an in-app confirmation dialog and centralized Toast feedback.
- Added page transitions, animated panels, toast slide-in, dialog pop-in, drawer slide-in, and reduced-motion fallbacks.
- Reader “记为灵感” now gives immediate Toast feedback while keeping the existing in-reader “查看灵感 / 继续阅读” path.
- Settings, library, inspiration, search, stats, and reader surfaces now share the same paper-card motion language.
- Added interaction polish guard: `npm run verify:interaction-polish`.

## Android update: 2026-06-30

- Android 端改为 5 栏底部导航：首页、书架、灵感、统计、我的。
- 首页参考 Reeden 的阅读概览结构，保留累计阅读、阅读时长、继续阅读、灵感快捷入口和同步状态；不启用阅读目标。
- 书架改为三列书封网格，支持搜索、整卡打开、TXT/Markdown/EPUB 导入和重复导入标签。
- 灵感成为手机端一级入口，支持快速记录、来源摘录卡片、AI 候选版本展示位和按书籍/标签搜索。
- 移动端新增 SQLite schema、Capacitor Filesystem 文件保存、旧 localStorage 快照迁移、WebDAV 同步骨架。
- Added Android guards: `npm run verify:mobile-ui`, `npm run verify:mobile-storage`, `npm run verify:mobile-reader`, `npm run verify:mobile-webdav`, `npm run verify:mobile-inspiration`.

## Maintenance update: 2026-06-29

- Product direction changed from in-app novel drafting to local-first inspiration capture, AI polishing/expansion, local novel reading, and reading-time tracking.
- Startup page now presents two primary modules: 灵感中心 and 本地书库. Search is visible on the homepage; AI polishing lives inside 灵感中心 rather than as a homepage module.
- Reading settings now split application theme from book background. Reader backgrounds: 白纸、暖纸、护眼、夜间.
- Portable storage support prefers an install-adjacent `data` directory when writable, with settings buttons to copy-migrate data and library directories.
- Reading-to-inspiration now writes a structured source card with book title, author, location, progress, and selected excerpt instead of mixing source text into the inspiration body.
- TXT/Markdown import records content hash and duplicate labels; TXT/Markdown author metadata is parsed from common front matter lines.
- Markdown reading uses `markdown-it`; EPUB reading supports wheel page turning, collapsible TOC, settings drawer, and publisher-style preservation by default.
- Legacy project/chapter/card/export frontend code, renderer API types, preload exposure, and Electron IPC registrations were removed.
- Old user project data is not automatically deleted, but the application no longer exposes old project creation, chapter editing, card editing, or project export workflows.
- Global search now targets inspirations, local TXT/Markdown books, and EPUB text indexes.
- Backup/restore scope is app-data only: inspirations, AI settings metadata, encrypted AI secret file, local library records, reading progress/sessions, settings, and logs.
- Added cleanup guard: `npm run verify:clean-reposition`.
- Added reading experience guards: `npm run verify:reader-settings`, `npm run verify:portable-storage`, `npm run verify:reading-inspiration`, `npm run verify:reader-formats`.

## Scope Freeze

- No online book sources, crawling, or platform publishing.
- Android 第一版只做本地优先、电脑局域网同步和 WebDAV 优先同步；S3/其它网盘、PDF、MOBI/AZW/AZW3、听书放到后续阶段。
- AI is limited to user-configured OpenAI-compatible endpoints and local inspiration processing.
- The renderer must never receive or log the raw API Key.
- Only P0/P1 fixes are allowed before a package is accepted.

## Automated Checks

| Check | Command | Result |
| --- | --- | --- |
| Product cleanup guard | `npm run verify:clean-reposition` | Passed |
| Product repositioning guard | `npm run verify:reposition` | Passed |
| Inspiration MVP guard | `npm run verify:inspiration` | Passed |
| AI settings and secret boundary guard | `npm run verify:ai-settings` | Passed |
| EPUB restore guard | `npm run verify:epub-restore` | Passed |
| Reader settings/theme guard | `npm run verify:reader-settings` | Passed |
| Portable storage guard | `npm run verify:portable-storage` | Passed |
| Reading-to-inspiration guard | `npm run verify:reading-inspiration` | Passed |
| Reader formats/search guard | `npm run verify:reader-formats` | Passed |
| Interaction polish guard | `npm run verify:interaction-polish` | Passed |
| Reading stats UI binding check | `npm run verify:stats-ui` | Passed |
| Visual polish guard | `npm run verify:visual-polish` | Passed |
| UX polish guard | `npm run verify:ux-polish` | Passed |
| Android UI guard | `npm run verify:mobile-ui` | Passed |
| Android SQLite/Filesystem guard | `npm run verify:mobile-storage` | Passed |
| Android reader guard | `npm run verify:mobile-reader` | Passed |
| Android WebDAV guard | `npm run verify:mobile-webdav` | Passed |
| Android inspiration guard | `npm run verify:mobile-inspiration` | Passed |
| TypeScript and production build | `npm run build` | Passed |
| Full Beta gate | `npm run verify:beta` | Passed |
| Dependency audit | `npm audit --omit=dev` | Passed, 0 vulnerabilities |
| Directory package | `npm run dist:beta` | Passed |
| Release artifact gate | `npm run verify:beta:release` | Passed |
| Packaged process smoke | Start packaged exe, wait 8 seconds, stop process | Passed |

Repeatable command:

```bash
npm run verify:beta
```

Package command:

```bash
npm run dist:beta
```

Release artifact check:

```bash
npm run verify:beta:release
```

## Generated Artifact Evidence

- `release-beta/win-unpacked/创作阅读助手.exe`
  - Size: 188,784,640 bytes
  - Last write time: 2026-06-30 08:46:35
- Packaged process smoke: started the exe, waited 8 seconds, confirmed it stayed alive, then stopped it.
- Manual launch caution: close every running `创作阅读助手.exe` before opening the candidate, then verify the process path points to `D:\develop\Code\Codex\创作阅读助手\release-beta\win-unpacked\创作阅读助手.exe`. Electron single-instance behavior can otherwise focus an older test install such as `D:\develop\Code\Codex\TEST_测试安装位置\灵感阅读助手\creation-reading-assistant\创作阅读助手.exe` and make the current package look stale.

## P0/P1 Gate

Beta cannot ship if any of these fail:

- App cannot start from the packaged exe.
- First screen does not clearly prioritize 灵感中心、本地书库 and homepage search.
- Old project/chapter/card/export UI or IPC is reachable from the renderer.
- Inspiration create/edit/delete/search data does not persist after restart.
- AI output overwrites original inspiration instead of saving as a candidate variant.
- AI API Key appears in renderer settings, logs, search, debug export, or normal JSON settings.
- TXT/Markdown/EPUB import or reading cannot open imported books.
- EPUB cannot turn pages with mouse wheel/keyboard/buttons, or its TOC cannot be collapsed.
- Markdown headings, tables, code blocks, or TOC cannot render.
- Reading progress or session statistics inflate while idle/backgrounded.
- Search result clicks fail for inspirations, books, or EPUB sections.
- “记为灵感” from the reader does not preserve source book/progress context.
- Settings do not persist after restart.
- Backup/restore corrupts app-data.
- Renderer directly imports privileged Node/Electron modules.

## Creation project shell update: 2026-08-09

- Desktop creation workspace schema upgraded to v2 (`projects.setup_json`) with an atomic v1→v2 migration; existing v1 data opens with a default setup.
- Added `projects.list` query (updatedAt 倒序, per-project chapter/scene counts) and full-setup `project.create` (title + template + goals + 每周更新日 + 章节工作流), which atomically creates 项目 / “第一章” / “默认场景” and preserves created IDs.
- Added `electron/main/creation-coordinator`：懒打开、并发首开只开一次、维护期 busy 拒绝、`withWorkspaceClosed` 先 checkpoint/close 再执行目录操作、动态数据根切换、close 幂等。
- Backup/restore/data-directory migration now hold the coordinator maintenance lock; `before-quit` synchronously triggers workspace close.
- Renderer project-shell surface uses `DesktopApi.creation.listProjects` / `readProjectNavigation` / `createProject`; navigation excludes scene bodies, which are read lazily through the typed editor surface.
- Added guard: `npm run verify:creation-project-shell`.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation project shell guard | `npm run verify:creation-project-shell` | Passed |
| Creation workspace guard (unchanged) | `npm run verify:creation-workspace` | Passed |

## Creation scene editor update: 2026-08-09

- Added the desktop writing desk: project switcher, chapter/scene outline, single-scene manuscript, margin metadata and focus mode.
- Scene bodies load lazily through `readSceneBody`; saves use `updateSceneBody` with revision conflict detection. The renderer never receives raw SQL or database handles.
- The structured editor permits paragraph, scene break, quote/letter, centered text and author note blocks, plus bold/italic marks. Unsupported structures are rejected again at the workspace boundary.
- Chinese IME composition suppresses autosave until composition ends. Normal edits save after 800ms idle; `Ctrl+S` submits immediately and status is announced in the UI.
- Complex or oversized paste opens a cleaned plain-text preview. `Ctrl+Shift+V` inserts cleaned plain text directly.
- Each confirmed save keeps exactly one previous scene snapshot. After abnormal exit, the UI states that confirmed writes were restored and that the last sub-second unsubmitted input may be absent.
- Project watches survive coordinator maintenance close/reopen and automatically unsubscribe when the renderer is destroyed.
- Added guard: `npm run verify:creation-editor`; it runs editor unit tests, TypeScript contracts and the real SQLite/Electron runtime contract.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation scene editor guard | `npm run verify:creation-editor` | Passed |

## Known P2 Issues

- App icon is not configured, so the default Electron icon may be used.
- Code signing is not configured.
- Bundle size is relatively large because EPUB support pulls in reader dependencies.
- Previous package-attempt directories such as `release/`, `release-beta-final/`, and `release-beta-final-2/` have been removed; use the current `release-beta/` candidate for testing.

## Manual Regression Script

### Startup

1. Close any existing `创作阅读助手.exe`, then start the packaged app from `release-beta/win-unpacked/创作阅读助手.exe`.
2. Confirm the startup page shows 创作项目、灵感中心 and 本地书库 as the main cards, with homepage search visible.
3. In Task Manager or PowerShell, confirm the running process path is the current `release-beta` candidate, not an older installed/test copy.
4. Confirm the new 创作项目 entry opens the local project shelf; legacy project creation/opening/chapter-writing screens remain absent.

### Inspiration and AI

1. Open 灵感箱.
2. Create a new inspiration item.
3. Edit title, body, type, status, tags, and 平台标签.
4. Restart the app.
5. Expected: the inspiration item is still present and searchable.
6. Open 设置中心 / AI 助手 settings area.
7. Configure Base URL, model, and API Key.
8. Expected: UI shows API Key saved without displaying the secret.
9. Run a polish/expand action from 灵感箱.
10. Expected: AI output is saved as a candidate version and does not overwrite the original inspiration.

### Library and Reading

1. Import one TXT file.
2. Open it, scroll, close reader, reopen.
3. Expected: scroll position is restored.
4. Repeat with one Markdown file.
5. Import one EPUB file.
6. Open it, page forward, close reader, reopen.
7. Expected: EPUB CFI position is restored.
8. Open the EPUB TOC panel and click a section.
9. Expected: reader navigates to the selected section.
10. Click “记为灵感” from TXT/Markdown and EPUB readers.
11. Expected: the reader stays in place and shows 查看灵感 / 继续阅读.
12. Click 查看灵感.
13. Expected: the inspiration opens with a 来源卡片 and optional 来源摘录, plus 返回阅读.

### Sessions and Stats

1. Read a TXT or Markdown book for at least one minute.
2. Open the reading stats page.
3. Expected: today, last 7 days, by-book, and recent session data update.
4. Focus another app for longer than idle timeout.
5. Expected: reading time does not keep growing while backgrounded/idle.

### Search

1. Search a TXT/Markdown book body phrase.
2. Search an EPUB book title.
3. Search an EPUB body phrase from imported sidecar text.
4. Search a global inspiration body phrase.
5. Click each result.
6. Expected: matching inspiration or reader target opens.

### Settings / Backup / Restore

1. Change theme, reader font size, idle timeout, tracking options, and AI Base URL/model.
2. Restart the app.
3. Expected: settings persist.
4. Run backup from settings/data section.
5. Expected: backup contains manifest and app-data.
6. Restore from that backup.
7. Expected: restore creates a safety snapshot before applying data and recommends restart.

### Crash Recovery

1. Start reading a book.
2. Force close the packaged process.
3. Reopen the app.
4. Expected: unfinished session is recovered without adding offline time, and no duplicate statistics appear.
