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

## Creation outline update (slice 6a): 2026-08-10

- Workspace schema upgraded to v3 with an atomic v2→v3 migration: new `volumes` table, `chapters.volume_id / status / numbering_kind / custom_number / deleted_at`, `scenes.planning_json / deleted_at`. Each existing v2 project gets a default volume「正文」and its chapters attach to it; v1 databases migrate through v2→v3 in one open.
- Added 16 structure commands: `volume.create/rename/reorder/delete`, `chapter.create/rename/reorder/move/delete/setStatus/setNumbering`, `scene.create/rename/reorder/move/delete`. All use stable IDs, `beforeXxxId`-based insertion for reorder, revision-checked renames/status/numbering, and soft delete (deleted_at) with cascade for volume/chapter deletion.
- `project.create` now also creates the default volume「正文」in the same transaction.
- Added `project.outline` query: volumes → chapters → scenes tree with derived display numbers (`第N章`/`序章`/`番外`/custom), chapter workflow status, and per-scene non-whitespace word counts.
- Default chapter workflow changed from `["起草"]` to the spec default `["规划","待写","写作中","初稿","修订","定稿","已发布"]`; `chapter.setStatus` validates against the project's configured workflow.
- Added guard: `npm run verify:creation-outline` (15 runtime contracts covering structure commands, outline, reorder/move semantics, soft-delete cascade and the v2→v3 migration).

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation outline guard | `npm run verify:creation-outline` | Passed |
| Creation workspace guard (re-verified) | `npm run verify:creation-workspace` | Passed |
| Creation project shell guard (re-verified) | `npm run verify:creation-project-shell` | Passed |

## Creation safe-reorganize update (slice 6b): 2026-08-10

- Added `chapter.split`: split a chapter at a scene boundary (the split scene onward moves into a new chapter placed right after the source; the source must keep at least one scene).
- Added `chapter.merge`: merge two same-volume chapters, appending source scenes to the target's end and soft-deleting the source.
- Added `chapters.setStatus` batch status change: validates all chapters belong to one project and the status is in its workflow.
- All three reuse the revision-checked structure transaction envelope, emit typed `committed` events, and are covered by the `verify:creation-outline` runtime contracts (now 16).

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation outline guard (16 contracts) | `npm run verify:creation-outline` | Passed |
| Desktop full build | `npm run build` | Passed |

## Creation outline UI update (slice 6c): 2026-08-10

- Added IPC channels `creation:readProjectOutline` and `creation:runStructure` (all 19 structure commands through one typed channel), wired through preload → `DesktopApi.creation` → `creation-service`.
- Renderer store caches `project.outline` per project; `useCreationActions` gained `loadOutline` / `runStructure`; watch events now refresh both navigation and outline so the outline stays live without a page reload.
- Writing desk left rail replaced with a two-view outline: the **outline tree** (volumes → chapters → scenes with derived numbers, workflow status select, per-scene word counts) and the **card board** (scenes grouped by chapter or by workflow status). Both views share the same outline data and scene selection.
- Tree actions: new volume/chapter/scene, inline rename, two-click delete (cascade), up/down reorder, move chapter to another volume, move scene to another chapter, status select.
- End-to-end verified on a real Electron instance: project creation, scene body write (word count 16), volume/chapter tree rendering, card-board grouping, and live outline refresh (a newly created volume appears within ~1.5s without reloading the page).

## Creation cards update (slice 7a): 2026-08-10

- Workspace schema upgraded to v4 with an atomic v3→v4 migration: new `card_types` and `relation_types` tables, `cards.aliases_json / fields_json / tags_json / deleted_at`, `card_relations.note`. Built-in seeds: 8 card types (角色/地点/组织/物品/世界规则/情节事件/伏笔线索/资料) and 4 relation types (认识/登场于/隶属于/持有), seeded idempotently for both fresh databases and migrations.
- Added 7 card commands: `cardType.create` (custom type with field schema), `relationType.create` (forward/reverse names + allowed kinds), `card.create / update / delete` (soft delete cascades its relations), `cardRelation.create / delete`.
- Added 5 card queries: `cards.list` (kind filter + title/alias search), `card.read`, `cardTypes.list`, `relationTypes.list`, `card.relations` (outgoing/incoming with forward names).
- Field schema supports 10 kinds (text/multiline/number/date/select/multiSelect/boolean/cardRef/url/attachment) with required/options/defaults; card create/update validate values against the type's schema (unknown fields and missing required fields are rejected).
- Added guard: `npm run verify:creation-cards` (8 runtime contracts); all creation guards and `npm run build` pass.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation cards guard | `npm run verify:creation-cards` | Passed |
| Creation workspace guard (re-verified) | `npm run verify:creation-workspace` | Passed |
| Creation outline guard (re-verified) | `npm run verify:creation-outline` | Passed |

## Creation cards UI update (slice 7b): 2026-08-10

- Added 5 card query IPC channels (`cardsList` / `cardRead` / `cardTypesList` / `relationTypesList` / `cardRelations`) and broadened `creation:runStructure` to accept card commands; wired through preload → `DesktopApi.creation` → `creation-service`.
- Renderer store caches card types, relation types, card list and per-card relations; `useCreationActions` gained `loadCardTypes` / `loadRelationTypes` / `loadCards` / `loadCardRelations` / `readCard`.
- Project view now switches between 写作 and 卡片; the cards page provides type filter, title/alias search, new/edit card form with 10 field control kinds (text/multiline/number/date/select/multiSelect/boolean/cardRef/url/attachment), alias and tag inputs, and relation listing with an inline create-relation form.
- End-to-end verified on a real Electron instance: tab switch, 3-card list rendering, 8 built-in type filter options, and field-value display (e.g. item 暗刃 with 备注 field).

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Desktop full build | `npm run build` | Passed |
| Renderer unit tests | `npm run test` | Passed (147) |

## Creation cards board update (slice 7b follow-up): 2026-08-10

- Card management upgraded to a **board view**: cards laid out in columns by type (8 built-in kinds), each card is a draggable visual card; drag a card to another column to change its type (list view kept as an alternative).
- `card.update` now accepts an optional `kind` to move a card across types: target type must exist, old field values that match the new schema are preserved, others dropped, and required fields are re-validated (a card lacking a new type's required field is rejected).
- CardsPage subscribes to the project watch channel, so card/relation changes (including board drag-to-move and changes made elsewhere) refresh the board live without reloading the page.
- End-to-end verified on a real Electron instance: 8 type columns render with correct card distribution, and moving a card to another type updates the board within ~1.8s.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation cards guard (9 contracts) | `npm run verify:creation-cards` | Passed |
| Renderer unit tests | `npm run test` | Passed (147) |

## Creation history update (slice 8a): 2026-08-10

- Added trash/recycle-bin commands: `trash.list` (soft-deleted volumes/chapters/scenes/cards with title, deletion time and revision), `trash.restore` (restore a soft-deleted entity; restoring a volume also restores its chapters and scenes, restoring a chapter restores its scenes), and `trash.purge` (permanent delete including cascades; errors on already-persisted entities).
- Added named snapshots: `snapshot.create` (a reason-labeled milestone capturing a scene body or a card's title/aliases/fields/tags), `snapshot.list` (per project with optional subject filter), and `snapshot.restore` (object-level restore of a scene body or card fields).
- Added guard: `npm run verify:creation-history` (9 runtime contracts covering delete→restore cycles, cascade restore, purge, and scene/card snapshot round-trips); all creation guards and `npm run build` pass.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation history guard | `npm run verify:creation-history` | Passed |
| Creation cards guard (re-verified) | `npm run verify:creation-cards` | Passed |

## Creation history UI update (slice 8b): 2026-08-10

- Project view gained a **历史** tab with two panels: **回收站** (lists soft-deleted volumes/chapters/scenes/cards with type, title and deletion time; restore or two-click permanent delete) and **版本快照** (lists named snapshots with reason and time; restore a scene or card from a snapshot).
- IPC: added `creation:trashList` / `creation:snapshotList` query channels and broadened `creation:runStructure` to accept history commands; wired through preload → `DesktopApi.creation` → `creation-service` → `useCreationActions`.
- End-to-end verified on a real Electron instance: a deleted card appears in the trash list with type/time, and clicking 恢复 empties the trash.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Desktop full build | `npm run build` | Passed |
| Renderer unit tests | `npm run test` | Passed (147) |

## Creation trash expiry update (slice 8c): 2026-08-10

- Recycle-bin items older than 30 days are permanently deleted automatically when the workspace opens (volumes cascade to chapters/scenes, cards cascade to relations). Open-time purge failures never block opening.
- Contract test added: a soft-deleted scene back-dated 40 days disappears from `trash.list` after close/reopen (history guard now 10 contracts).

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation history guard (10 contracts) | `npm run verify:creation-history` | Passed |
| Creation cards guard (re-verified) | `npm run verify:creation-cards` | Passed |

## Creation export update (slice 9a): 2026-08-10

- Added `project.export` query: aggregates volumes → chapters → scenes with derived display numbers and per-scene plain-text body (blocks joined by blank lines, scene breaks as spacing). Soft-deleted entities are excluded. This is the data basis for final-draft export (platform-clean text / review draft) and project bundles.
- Added guard: `npm run verify:creation-export` (10 runtime contracts covering text aggregation, multi-volume/multi-chapter structure, and soft-delete exclusion); all creation guards and `npm run build` pass.

Automated Checks (appended):

| Check | Command | Result |
| --- | --- | --- |
| Creation export guard | `npm run verify:creation-export` | Passed |
| Creation history guard (re-verified) | `npm run verify:creation-history` | Passed |

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
