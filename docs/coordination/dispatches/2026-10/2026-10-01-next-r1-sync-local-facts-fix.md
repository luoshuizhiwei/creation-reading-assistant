# R1 — SyncRepository 本地事实与远端提升列合并修复报告

- 基线：`e39a78da9c62f02614207cffb36969a32492eaea`，隔离候选 `E:\creation-reading-assistant\next-round-r1\android`（`git archive` 导出 872 文件，非共享工作树源码）。
- 所有权：仅改 `android/app/src/main/java/com/creationreadingassistant/data/repository/SyncRepository.kt` + 新增专项测试 `SyncRepositoryLocalFactsTest.kt`。未触碰其他生产文件、共享工作树、原损坏仓库、正式书库与任何设备；未 commit/push/reset/checkout。
- 结论先行：**基线红测 16 tests / 13 failures（红测先行成立）；修后定向绿测 121/121 全绿**。N3 复核指出的两类缺陷（applyBook 丢弃本机事实列、applyProgress/applySession 提升列重置默认值）已在红测中逐条复现，并在 `SyncRepository.kt` 内以逐字段合并最小修复；另发现并闭环了 push 方向的 payload 合同断裂（桌面按 payload 内嵌元数据合并且要求 id/filePath/title 硬性键）。

## 1. 合同调查（先查证后动手）

| 事实 | 证据 |
|---|---|
| 桌面 sync/pull 的 envelope payload = 桌面存储的完整业务对象 JSON（LibraryBook / ReadingProgress / ReadingSession） | `electron/main/index.ts` `buildSyncPullResponse`：`toFullEnvelope("book", item.id, item)` 等 |
| 桌面 `normalizeLibraryBook` 要求 payload 内 `id`/`filePath`/`title` 非空，否则**整条丢弃** | `electron/main/index.ts:1758` |
| 桌面 `normalizeProgressItem` 要求 `bookId`+`filePath` 非空；`progressPercent` 为 **0..1 尺度**（clamp01；0.995 完成阈值） | `electron/main/index.ts:2241-2258` |
| 桌面 `normalizeSessionItem` 要求 `id`+`bookId`+`filePath`；时间键 `startAt`（接受 `startedAt` 回退）/`endAt`；无进度键 | `electron/main/index.ts:2320-2360` |
| **桌面合并读取的是 payload 内嵌的 revision/deviceId/updatedAt/deletedAt（withSyncMetadata），不是信封元数据**；updatedAt 缺失会被盖成 now()，破坏冲突序 | `electron/main/index.ts:423 withSyncMetadata` + `mergeReadingProgressByBookId` |
| Android 本机进度流从不写 payload 列；percent 为 0..100；completed_at 为 epoch millis；状态词汇 reading/finished/shelved | `BookRepository.kt:440-515`、`ReaderProgressEffects.kt:285-295`、`ReadingEntities.kt:13 ReadingCompletionState` |
| Android 会话记录器写 payload（Android 词汇 startedAt/endedAt/durationMs/progressPercent，percent 0..100 coerceIn） | `ReadingSessionRecorder.kt:95-122,51` |
| Android 导入的书/进度/部分会话 payload 列为空 → **现状 push 发空对象，桌面按上述硬性键整条丢弃** | `ShelfImporter.kt`/`EpubRepository.kt` 无 payload 写入；`SyncRepository.kt` push 直透 |

## 2. 字段合并表（修后语义；pull 方向）

冲突门槛不变：先 `SyncMergePolicy.shouldAcceptRemote(revision, updatedAt)`，拒绝即不动；软删除 `deleted_at` 仍以信封为准（墓碑传播不变）。下表为**被接受**时的逐字段规则；「缺键/显式 null」统一保守处理为保留本地（新记录落实体默认值）——桌面 JSON.stringify 省略 undefined 字段从不产生 null，显式 null 只能来自合同外输入，不得据以清除本机数据。

### applyBook（BookEntity）

| 字段 | 远端显式给值 | 缺键 | 新记录 |
|---|---|---|---|
| title / author / format / content_hash | ✅ 用远端（合同字段，维持既有映射） | 保留本地 | payload 值或默认（title "(未命名)" / format "txt"） |
| local_uri / local_content_path | **永不取远端**（桌面 filePath/coverPath 是对端设备路径） | 保留本地 | null |
| content_status / size / cover_data_url / original_file_name / imported_at / description | 不在消费集合（维持现状；桌面同名字段中 filePath/coverPath 是对端路径、originalFileName/importedAt 语义对应但既有 apply 未消费，扩映射超出最小修复，见表后说明） | 保留本地 | 默认（available / 0 / null…） |
| payload / revision / device_id / updated_at / deleted_at | 信封（payload 真相源整体替换，不变） | — | 信封 |

### applyProgress（ReadingProgressEntity）

| 字段 | 远端显式给值 | 缺键 | 新记录 | 依据 |
|---|---|---|---|---|
| progress_percent | `progressPercent` **×100**（0..1→0..100），coerce 0..100 | 保留本地 | 0f | 桌面 clamp01 + 0.995 阈值；不镜像桌面越界行为（>1 钳成 100%）会造成误标完成，coerce 语义与桌面 clamp01 等价 |
| completion_state | `completionState` 经 `ReadingCompletionState.fromStorage` 映射（completed→finished；reading→reading；unread→reading；shelved→shelved 容错本机词汇回流） | 保留本地 | "reading" | fromStorage 是既有代码承认的唯一映射函数；不自行发明词汇 |
| completed_at | 完成态联动（见下） | 状态缺键→保留本地 | null | millis↔ISO 为两列既有表示的确定性换算；解析失败按未给出（保守） |
| last_read_at | `lastReadAt`（ISO↔ISO 同表示） | 保留本地 | null | 桌面合同 ISODateString |
| total_reading_time_ms | `totalReadingTimeMs`（ms↔ms，coerce ≥0） | 保留本地 | 0L | 单位一致 |
| current_location_json | **永不取远端** | 保留本地 | null | 桌面 currentLocation 对象与原生 locator_v2/offset JSON（`LocatorBuilder.progressJson`）不是同一合同，跨格式换算属猜测；远端对象留在 payload 真相源 |

**完成态/完成时间一致性**：远端显式状态为非完成 → `completed_at = null`（对齐原生 writer 不变式，`BookRepository.setReadingState` 同规则）；远端显式完成态 → 采纳可解析的 `completedAt`，否则保留本机值（不伪造时间戳）；状态缺键 → completed_at 仅在显式合法时更新。

### applySession（ReadingSessionEntity）

| 字段 | 远端显式给值 | 缺键 | 新记录 | 依据 |
|---|---|---|---|---|
| book_id | `bookId` | 保留本地 | ""（现状） | 既有映射 |
| started_at | `startAt`，回退 `startedAt` | 保留本地 | null | 桌面 wire=startAt；startedAt 回退与桌面 normalizeSessionItem 相同 |
| ended_at | `endAt`，回退 `endedAt` | 保留本地 | null | 桌面 wire=endAt；endedAt 为 Android 记录器词汇容错 |
| duration_ms | `durationMs`（coerce ≥0） | 保留本地 | 0L | 两侧同为总时长 ms |
| progress_percent | `progressPercent`（0..100，coerce） | 保留本地 | null | session 唯一生产者是 Android 记录器（0..100）；桌面合同无此键 |
| created_at | `createdAt` | 保留本地（现状是用 env.updatedAt 覆盖，属覆盖本机事实的同类问题，一并修正） | env.updatedAt（现状） | 创建时间是本机事实 |

## 3. push 闭环（任务项 8，均在 SyncRepository 内）

- `backfillBookPayload(e, deviceId)`：payload 列为空时回填，**补齐桌面硬性键 `id`/`filePath`**（title/author/format/contentHash 维持既有）+ `importedAt`（防桌面以 now() 伪造导入时间）+ payload 内嵌同步元数据 `revision`/`deviceId`/`updatedAt`/`deletedAt`（桌面合并依据）。
- `backfillProgressPayload(e, deviceId, book)`：回填 `bookId`/`filePath`（本书本机文件事实）/`progressPercent`（**÷100 → 桌面 0..1 尺度**，与 pull ×100 互逆）/`completionState`（finished→completed，其余原样）/`completedAt`（millis→ISO）/`lastReadAt`/`totalReadingTimeMs` + 同步元数据。不伪造 `currentLocation`（桌面会按 progressPercent 构造默认定位）。非空 payload 原样透传。
- `backfillSessionPayload(e, deviceId, book)`：按 Android 记录器词汇回填 `id`/`bookId`/`filePath`/`startedAt`/`endedAt`/`durationMs`/`progressPercent`/`createdAt` + 同步元数据。非空 payload（记录器已写）原样透传。
- 信封级 metadata（id/revision/deviceId/updatedAt/deletedAt）不变。

### SEAM REQUEST（跨文件/跨端，R1 未动）

1. **桌面端（Electron）**：`normalizeProgressItem`/`normalizeSessionItem` 把 `filePath` 定为硬性键，Android 书籍无本机正文时只能发空串 → 该条被桌面丢弃（与旧行为等价）；Android 记录器已写的**非空** session payload 缺 filePath，桌面同样丢弃。需要桌面线裁决：接受无 filePath 的远端记录，或定义 filePath 的对端占位语义。另：桌面 `ReadingProgress` 词汇无 shelved（normalize 归一为 reading），Android「搁置」状态经桌面中继后丢失——需要产品侧扩展词汇或接受该损失。
2. **原生端（其他文件）**：`ReadingSessionRecorder` 写的非空 payload 与桌面必需键不一致（同上）；JSON 备份恢复（JsonBridge）落库的 payload 列是 snake_case 实体 JSON，push 直透同样过不了桌面 normalize——如需全链路覆盖，需独立任务统一 push 序列化。

## 4. 测试证据（红→绿）

- 专项测试：`app/src/test/java/com/creationreadingassistant/data/repository/SyncRepositoryLocalFactsTest.kt`（16 用例），全部经真实 `repository.pull()/push()` 调用链捕获 DAO 实际收到的实体断言（mockk slot），无镜像 helper；覆盖：接受远端新版本（本机 9 列事实保留 + 合同字段更新）、拒绝旧版本（零写入）、新记录默认值、缺键兼容、合法 0 值、完成态/完成时间双向一致、ISO 解析失败保守、桌面/Android 双词汇 session、本机定位保留、push 三类回填 + 非空透传。
- 命令（R1 独占缓存 `E:\creation-reading-assistant\next-round-r1\_cache\gradle-home`，`--offline`）：
  - 基线红：`./gradlew.bat :app:testDebugUnitTest --tests ...SyncRepositoryLocalFactsTest` → **EXIT=1，16 tests / 13 failures / 0 errors / 0 skipped**（`_evidence/baseline-red.xml`；3 个 PASS 为现状守卫：新记录默认值、拒绝零写入、非空透传）
  - 修后绿（同命令）→ **EXIT=0**；合并回归（SyncRepositoryTest/JsonBridgeTest/LocalZipBackupTest/ProfileSyncEngineTest/ProfileViewModelTest/ShelfViewModelTest）→ **EXIT=0，XML 实算 121 tests / 0 failures / 0 errors / 0 skipped**（`_evidence/final-green-localfacts.xml`、`final-green-syncrepository.xml`）
  - 未跑全量 2628（按要求）；未编译 Release/设备变体（变更为主源码私有函数 + JSON 解析，qahost/debug 共享同一 main 源码，`compileDebugKotlin` 随测试任务已编译）。

## 5. 交付物与 SHA

| 项 | 路径（`E:\creation-reading-assistant\next-round-r1\`） | SHA-256 |
|---|---|---|
| 修复补丁（基线→修后，314 行） | `_evidence/r1-sync-local-facts.patch` | — |
| SyncRepository.kt（修后） | `android/.../data/repository/SyncRepository.kt` | 5853e50cf5f282d3b9eb2ce9d3e0416572ff94cb8eb7c3c1290d555038f7a5a5 |
| SyncRepository.kt（基线留存） | `_evidence/SyncRepository.baseline.kt` | ad3a45d3833697d0898f781e4ef3ecd1699b846d6d16cf32bf77bebbc73ff1e9 |
| SyncRepositoryLocalFactsTest.kt（新增） | `android/.../data/repository/SyncRepositoryLocalFactsTest.kt` | b39909c65dbb2d6506dbafcbb3873343f258b4497068ef1565c468d7897026d4 |
| 红测 XML / 日志 | `_evidence/baseline-red.xml`、`_evidence/baseline-run.log` | — |
| 绿测 XML / 日志 | `_evidence/final-green-*.xml`、`_evidence/final-run.log`、`_evidence/regression-run.log` | — |

## 6. 风险与未测项

- **行为变化面**：pull 接受远端时本机事实列从「被重置」变为「保留」——这正是修复目标；`created_at` 缺键不再被 env.updatedAt 覆盖；新记录/缺键/显式 null 的显式 null 语义为保守保留（报告 §2 依据）。push 空 payload 回填会改变桌面收到的 payload 形状（从被丢弃的 `{}` 变为含本机事实与元数据的完整对象）——这是闭环目的，但桌面端实际接受度受其 normalize 规则约束（SEAM REQUEST §3）。
- **未测**：设备端 LAN 同步端到端（本轮无设备任务）；桌面端对回填 payload 的真实接受度（需桌面线配合）；JsonBridge 恢复行经 push 的桌面接受度；全量 2628 回归（按要求未重复；变更文件为私有函数级，定向 121 项覆盖其调用链）。
- 本轮唯一产品文件变更 314 行补丁，未引入新依赖、未改 SyncMergePolicy/实体/DAO/payload 合同结构、未引入 SyncOptions 或分类同步。

---

# R1 返修追加（2026-10-01 第二轮，按 r1-r3 总指挥复核 4 项缺口）

> 本节为追加记录；上方第一轮红/绿记录原样保留。返修依据：`docs/coordination/reviews/2026-10/2026-10-01-next-r1-r3-codex-review.md` Spec 轴 R1 三项（P1 非空 payload 透传、P2 完成时间未按合并后状态联动、P2 新 Book 分支旧 str 解析）。
> 隔离候选/所有权/缓存不变；本轮同样未 commit/push/reset/checkout、未动其他窗口、未操作设备。

## R2-1【P1】非空 payload 合并而非透传（真实 pull→本地变化→push 闭环）

**缺口复核成立**：第一轮 backfill 在 payload 非空时直接 `return parsed`。pull 会把远端非空 payload 存入 payload 列，本机阅读/取消完成只更新提升列（不写 payload），因此「拉入旧 progress → 本地推进 → push」会把旧 percent/state/time 与旧内嵌 metadata 发回；第一轮的"非空透传"测试恰恰把这个缺陷当守卫。

**修后语义**（三个 backfill 统一改为「合并 + 可检查跳过」，返回 `JsonObject?`）：

- 起点是已存 payload，**未知字段全部保留**（对端专有键、JsonBridge snake_case 键不丢）；
- **受管理键一律取当前实体/信封值**（旧值不残留）：book = id/filePath/title/author/format/contentHash/importedAt + revision/deviceId/updatedAt/deletedAt；progress = bookId/filePath/progressPercent(÷100)/completionState/completedAt(ISO，null 则键不出现)/lastReadAt/totalReadingTimeMs + 同上 metadata；session = id/bookId/filePath/startedAt/endedAt/durationMs/progressPercent(0..100)/createdAt + 同上 metadata，且旧 payload 中的桌面词汇键 startAt/endAt 一并刷新为当前事实（桌面 normalizeSessionItem 优先读 startAt）；
- **filePath 决议**：payload 已有非空值优先（对端自己的路径，不覆盖也不伪造）→ 否则本机书籍事实（local_content_path → local_uri）→ 两者皆无且 payload 非空 → 返回 null，push **跳过该条**并记入 `failedItems`（type/id/reason="无法形成合法同步记录：payload 与本机书籍事实均未提供 filePath（桌面 normalize 要求非空），已跳过该条"），不发注定被桌面丢弃的信封；
- **空 payload 的旧行为保持不变**（从提升列整体构建、filePath 允许空串、墓碑照发）：软删除传播尽力而为语义与既有 `SyncRepositoryTest`（不可改）锁定行为不变。桌面把 filePath 定为硬性键导致无本机文件的记录（含墓碑）无法投递，仍属 §SEAM REQUEST。

**第一轮"非空透传"测试已改写**为 `push merged session payload injects filePath refreshes managed fields and keeps unknown keys`：实际 ReadingSessionRecorder 形状（Android 词汇、无 filePath、wallClock 未知键）+ 实体事实新于 payload → 断言 filePath 注入、受管理字段与 metadata 为当前值、未知键保留。

## R2-2【P2】completed_at 以最终合并后的状态为准

原实现只在「远端显式非完成态」时清空 completed_at；本地 reading/shelved + 远端缺 completionState 但带合法 completedAt 时会落成「非完成 + 完成时间」。修后：

```kotlin
val finalState = explicitState ?: base.completion_state
val finalCompletedAt = when (finalState) {
    ReadingCompletionState.FINISHED.storageValue -> explicitCompletedAt ?: base.completed_at
    else -> null
}
```

状态缺键不推断完成；进度 100% 不自动标为 finished（S05-① 显式状态权威）；远端显式完成态仍采纳可解析完成时间、否则保留本机值（不伪造）。新增红测覆盖 reading/shelved+completedAt 反例与 100%+缺状态守卫。

## R2-3【P2】新旧记录统一安全解析 + 非有限值拒绝

- `optString` 加 `isString` 要求：显式 JSON null、数字、布尔、对象/数组一律不构成字符串值——修复新 Book 分支经 `str()` 落字面量 `"null"`、把 `123`/`true` 变字符串、对对象类型直接抛错（红测证实该路径导致整条 book 被 runCatching 吞成 failed item）的问题；新/旧记录共用同一条 `base.copy(...)` 合并路径，不再有绕过安全解析的早退分支。
- `optLong`/`optFloat` 加非字符串类型要求；`optFloat` 额外 `isFinite()` 过滤——NaN/Infinity 按未给出处理，绝不流进进度/时长列。
- push 冲突条目标题同步改用 `optString`（JsonNull 不再显示为 "null"）。
- KDoc 删除「JSON.stringify 不会产生 null」这一不成立依据，改为防御式规则陈述：任何对端都可能显式发 null，解释为"清除本机数据"没有合同依据，保守保留本地。

## R2-4 回归与证据（红→绿）

- 新增/改写 6 用例（真实调用链）：progress 生命周期合并（pull 0.10/reading/1000 → 本地 55%/finished/9000 → push 断言 0.55/completed/9000/当前 metadata/未知键保留/对端 filePath 保留）、session 记录器形状合并（filePath 注入+管理键刷新+wallClock 保留）、无 filePath 跳过+failedItems 可检查原因、completed_at 最终态三连（reading/shelved+completedAt 反例、100%+缺状态守卫）、book 显式 null+非法类型（新/旧记录）、数值 NaN/字符串数字/数字冒充字符串拒绝。
- **红（当前 R1 一轮候选）**：`./gradlew.bat :app:testDebugUnitTest --tests ...SyncRepositoryLocalFactsTest` → EXIT=1，**21 tests / 6 failures / 0 errors**（`_evidence/round2-baseline-red.xml`、`round2-baseline-red.log`；失败签名逐一对应上表缺口：透传 filePath=null、stale 0.1、NaN 落库、reading+完成时间、非法类型致 upsert 不发生、无失败项记录）。
- **绿（返修后）**：同命令 + SyncRepositoryTest → EXIT=0，42/42；合并相关回归（JsonBridgeTest/LocalZipBackupTest/ProfileSyncEngineTest/ProfileViewModelTest/ShelfViewModelTest）→ EXIT=0，**XML 实算 126 tests / 0 failures / 0 errors / 0 skipped**（`_evidence/round2-final-localfacts.xml`、`round2-final-syncrepository.xml`、`round2-final-run.log`）。既有不可改的 `SyncRepositoryTest`（墓碑传播、冲突映射、空 payload 回填）全部保持通过。

## R2-5 交付物与 SHA（更新）

| 项 | 路径（`E:\creation-reading-assistant\next-round-r1\`） | SHA-256 |
|---|---|---|
| 累计补丁（基线 e39a78da → 二轮修后，430 行，取代第一轮 314 行版本） | `_evidence/r1-sync-local-facts.patch` | — |
| SyncRepository.kt（二轮修后） | `android/.../data/repository/SyncRepository.kt` | 3e3467935c339fcb73e9e8f9a0a9791427edb822d98fc42d3a887dde9b58173f |
| SyncRepositoryLocalFactsTest.kt（21 用例） | `android/.../data/repository/SyncRepositoryLocalFactsTest.kt` | e2c9d515ef821d87e6bfb5bf197dbae9e1e69b2b9dedaf4617e01e7a41c02733 |
| 二轮红测 XML / 日志 | `_evidence/round2-baseline-red.xml`、`round2-baseline-red.log` | c6116b2a19ecb497181770330eef04d1b87c2aeb42762ba300db47447771da3b（XML） |
| 二轮绿测 XML / 日志 | `_evidence/round2-final-localfacts.xml`、`round2-final-syncrepository.xml`、`round2-final-run.log` | — |

## R2-6 字段合并表修订（push 方向，取代 §3 的"非空透传"表述）

| payload 键 | 空 payload（旧行为不变） | 非空 payload（合并） |
|---|---|---|
| 未知键（含 snake_case、对端专有） | 无 | **原样保留** |
| id / bookId | 提升列回填 | **当前实体**（e.id / e.book_id） |
| filePath | 本机书籍事实，无则空串（桌面硬性键，SEAM REQUEST） | payload 非空值优先（对端路径不覆盖不伪造）→ 本机书籍事实 → 皆无则**跳过+failedItems** |
| title/author/format/contentHash/importedAt（book） | 提升列回填 | **当前实体**（缺键不残留旧值） |
| progressPercent | 当前 percent ÷100（桌面 0..1 尺度） | 同左（当前值，非旧 payload） |
| completionState | finished→completed，其余原样 | 同左 |
| completedAt | millis→ISO，null 不出现 | 同左（null 时受管理键排除，旧值不残留） |
| lastReadAt / totalReadingTimeMs | 提升列回填 | **当前实体** |
| startedAt/endedAt/durationMs/progressPercent/createdAt（session） | 提升列回填（0..100 尺度） | **当前实体**；旧 payload 的 startAt/endAt 一并刷新 |
| revision / deviceId / updatedAt / deletedAt | 与信封一致（e.device_id ?: 本机 deviceId） | **与信封一致** |

pull 方向合并表（§2）不变，仅 completed_at 行按 R2-2 修订为「以最终合并后状态为准」。

## R2-7 未解决接缝（累计）

1. 桌面 normalizeProgressItem/normalizeSessionItem/normalizeLibraryBook 把 filePath 定为硬性键：无本机文件的记录（含空 payload 墓碑）本地发出后仍会被桌面丢弃；本轮改为"合并路径无 filePath → 本地跳过+可检查原因"，但墓碑（空 payload 路径）仍照发——桌面侧需裁决接受无 filePath 的远端记录或定义占位语义。
2. 桌面 ReadingProgress 词汇无 shelved（normalize 归一为 reading），「搁置」经桌面中继丢失——需产品侧扩展词汇或接受损失。
3. JsonBridge（JSON 备份）恢复行的 payload 为 snake_case 实体 JSON：合并路径保留其未知键、注入 filePath、刷新 camelCase 管理键后可过桌面 normalize（有本机文件时）；其桌面语义等价性仍建议独立任务核验。
