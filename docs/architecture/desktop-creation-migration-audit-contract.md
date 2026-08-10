# 桌面端创作工作台 — 旧数据只读迁移审计契约（切片 3 现状）

> 状态：切片 3（只读迁移审计）实现完成，本契约按 2026-08-09 实际实现记录（含验收 P2 修复后的现状）。
> 本文是契约记录而非设计文档：以 `electron/main/creation-migration-audit/` 当前代码为准。
> 新增来源、issue code、报告字段或审计行为时，必须同步更新本文件与运行时契约测试 `contract.ts`。

## 1. 结论

切片 3 的深 module 已落地：唯一的公开函数 `auditLegacyDesktopData`，只读扫描旧数据目录（支持自定义书库目录）并生成迁移计划报告，契约门禁 `verify:creation-migration-audit` 验证 10 条黑盒契约与静态禁写门禁。

本切片刻意零写入：**不建目标 store、不改旧 JSON（含 `.bak`）、不写激活指针、不建备份**。报告只描述“已发现 / 计划迁移 / 将跳过”的状态，任何激活动作都留给后续切片。

与切片 2（CreationWorkspace）不同，本模块不依赖 Electron 或原生模块，只使用 `node:` 内置能力；契约门禁由普通 Node 直接运行。

## 2. 实现文件与门禁

| 文件 | 作用 |
|---|---|
| `electron/main/creation-migration-audit/index.ts` | `auditLegacyDesktopData` 实现（唯一公开函数） |
| `electron/main/creation-migration-audit/types.ts` | 全部公开类型 |
| `electron/main/creation-migration-audit/contract.ts` | 运行时契约测试（esbuild 打包后由 Node 执行） |
| `scripts/verify-creation-migration-audit.mjs` | 门禁：静态禁写扫描 → `tsc --noEmit -p tsconfig.main.json` → esbuild bundle → 运行 contract.ts 并校验证据 |
| `package.json` | `scripts.verify:creation-migration-audit` |

模块导入白名单（`index.ts` 顶部）：`node:fs` 的 `access / readFile / readdir / stat / statfs`、`node:path`、`node:crypto` 的 `createHash`。没有任何写能力或数据库导入。

## 3. 公开 interface（唯一入口）

```ts
export interface AuditLegacyDesktopDataOptions {
  dataRoot: string;      // 调用方显式传入旧数据目录；类型必填
  libraryRoot?: string;  // 可选书库根；缺省或空白时回退为 dataRoot/AppLibrary
}

export async function auditLegacyDesktopData(
  options: AuditLegacyDesktopDataOptions
): Promise<LegacyMigrationAuditReport>;
```

路径解析规则：以 `AppLibrary` 为前缀的相对路径（书库 JSON 与三个资产目录）从 `libraryRoot` 解析；其余（`app-settings.json`、`inspirations.json`、`ai-secrets.json`）始终从 `dataRoot` 解析。

防御性实现：`options?.dataRoot` 非字符串时按空目录处理，最终表现为 `root-missing` 阻断；`libraryRoot` 非字符串或空白时使用默认 `dataRoot/AppLibrary`。数据根解析（storage pointer → portable → fallback）不属于本模块职责。

报告顶层：

```ts
interface LegacyMigrationAuditReport {
  reportVersion: 1;
  auditedAt: string;             // ISO 时间戳
  canProceed: boolean;           // 不存在任何 blocking issue
  activated: false;              // 类型级保证：本切片永不激活
  writesPerformed: 0;            // 类型级保证：本切片零写入
  root: { exists: boolean; readable: boolean; writable: boolean; freeBytes?: number };
  sources: Record<LegacySourceName, LegacySourceAudit>;
  issues: MigrationAuditIssue[];
  inspirationPlan: { discovered: number; migratable: number; skipped: number;
                     mappingStrategy: "preserve-if-available"; items: InspirationMigrationPlanItem[] };
  readerCompatibility: { books: number; booksByFormat: Record<"txt"|"md"|"epub"|"unknown", number>;
                         highlights: number; bookmarks: number; progress: number;
                         files: number; covers: number; searchIndexes: number;
                         assetBytes: number; policy: "legacy-reader-owned" };
  targetStore: { status: "absent" | "present"; inspectedByOpening: false };
}
```

报告中不出现数据根路径、绝对路径、书名、正文或密钥值；`root` 只暴露布尔与剩余空间数值。

## 4. 识别源（10 个）

| 源名 | 相对路径（默认根） | policy | 读取方式 |
|---|---|---|---|
| `appSettings` | `app-settings.json`（dataRoot） | `migrate` | JSON 解析，无条目提取（count 记为 1） |
| `inspirations` | `inspirations.json`（dataRoot） | `migrate` | 顶层 `{items}` 或裸数组 |
| `aiSecrets` | `ai-secrets.json`（dataRoot） | `keep-in-place` | 仅 `stat`，绝不读取内容 |
| `library` | `AppLibrary/library.json`（libraryRoot） | `legacy-reader-owned` | 顶层 `{books}` 数组 |
| `highlights` | `AppLibrary/highlights.json`（libraryRoot） | `legacy-reader-owned` | 顶层 `{items}` 或裸数组 |
| `bookmarks` | `AppLibrary/bookmarks.json`（libraryRoot） | `legacy-reader-owned` | 顶层 `{items}` 或裸数组 |
| `readingProgress` | `AppLibrary/reading-progress.json`（libraryRoot） | `legacy-reader-owned` | 顶层 `{items}` 或裸数组 |
| `libraryFiles` | `AppLibrary/files`（libraryRoot） | `inventory-only` | 递归计数文件数与字节数 |
| `libraryCovers` | `AppLibrary/covers`（libraryRoot） | `inventory-only` | 递归计数文件数与字节数 |
| `searchIndexes` | `AppLibrary/search-index`（libraryRoot） | `inventory-only` | 递归计数文件数与字节数 |

自定义 `libraryRoot` 时，`library / highlights / bookmarks / readingProgress` 四个 JSON 与 `files / covers / search-index` 三个资产目录全部从新根解析；`appSettings / inspirations / aiSecrets` 不受影响。

`migrate` 来源（设置、灵感）是后续激活迁移的对象；`legacy-reader-owned` 来源只做兼容性统计，不复制为项目卡（规格 §7.2 步骤 5）；`inventory-only` 目录只统计不读取内容。

## 5. 源状态与 issue 语义

`LegacySourceStatus`：

| 状态 | 含义 |
|---|---|
| `missing` | 文件/目录不存在（info 级 `source-missing`，`retryable: false`） |
| `empty` | 主文件存在但内容为空且无可用 `.bak`（warning 级 `source-empty`，不阻断） |
| `valid` | 主文件解析成功且结构识别成功 |
| `recoverable-backup` | 主文件不可用但 `.bak` 可解析（warning 级 `using-backup`，`retryable: true`） |
| `invalid` | 主 + `.bak` 均不可用（blocking 级，`retryable: true`） |
| `metadata-only` | 只取存在性与大小，不读内容（`aiSecrets` 与三个资产目录） |

`MigrationAuditIssue`：

```ts
interface MigrationAuditIssue {
  severity: "blocking" | "warning" | "info";
  code: string;                                   // 稳定 code
  source: LegacySourceName | "dataRoot" | "targetStore";
  legacyId?: string;                              // 仅失败/跳过条目
  message: string;                                // 固定中文文案，无路径、无堆栈
  retryable: boolean;
}
```

现有 code 全集：`source-missing`、`source-empty`、`using-backup`、`invalid-json-shape`、`json-unreadable`、`inspiration-item-invalid`、`inventory-error`、`root-missing`、`root-not-directory`、`root-unreadable`、`target-store-present`。

`canProceed = !issues.some(i => i.severity === "blocking")`。缺根目录、根是文件、根不可读、任何 JSON 源主 + 备份双损坏都会阻断；空文件、备份兜底、资产目录盘点失败均只是 warning，不阻断。

## 6. primary + `.bak` 语义

审计自带区分式读取（不复用既有 `readJson` 的静默回退，避免把损坏当正常）：

- 主文件不存在 → 尝试 `.bak`；都不存在 → `missing`（info）。
- 主文件存在但解析失败 → 视为不可用；`.bak` 可解析且结构可识别 → `recoverable-backup` + `using-backup` warning。
- 主文件解析成功但结构不可识别（如 `inspirations.json` 既非 `{items}` 也非数组）→ 同样尝试 `.bak`，`.bak` 有效则 `recoverable-backup`。
- 主文件存在、内容为空（`trim()` 后为空）且**无 `.bak`** → `empty` + `source-empty` warning（“旧数据文件为空，将按无条目处理。”），`retryable: true`，**不阻断，`canProceed` 保持 true**。
- 空主文件但存在 `.bak`：`.bak` 可解析 → `recoverable-backup`；`.bak` 也不可解析 → 仍按 `json-unreadable` 阻断。
- 主 + `.bak` 均不可用（非空主文件或空主文件但 `.bak` 损坏）→ `invalid` + blocking（`json-unreadable`：解析失败；`invalid-json-shape`：结构不可识别）。

## 7. root 预检

- `exists` / 是目录：`stat` 判断；根缺失、非目录、不可读分别产出 blocking code。
- `readable` / `writable`：`access(R_OK)` / `access(W_OK)` 权限位探测；**写探针在本切片被明确禁止**（实现注释原文：A write probe is intentionally forbidden in this slice），因此 `writable` 只是权限位结果。
- `freeBytes`：`statfs` 的 `bavail * bsize`，仅作参考值，缺失不阻断。

## 8. inspirationPlan 隐私字段

```ts
interface InspirationMigrationPlanItem {
  legacyId: string;              // 原 ID；缺失时为 "unknown-N"
  target: "global-inbox";        // 旧灵感统一进全局收件箱
  proposedTargetId: string;      // 计划保留原 ID（preserve-if-available）
  bodySha256: string;            // 正文 SHA-256 指纹，绝不携带正文本身
  variantsCount: number;         // AI 候选数量
  hasSource: boolean;            // 是否存在 source / sourceBookId / sourceLocation
}
```

- 条目缺稳定 `id` 或非字符串 `body` → 计入 `skipped`，产出 warning `inspiration-item-invalid`（带 `legacyId`，`retryable: true`）。
- `mappingStrategy: "preserve-if-available"`：只表达计划；最终 ID 映射铸造属于激活迁移事务，本切片不生成映射文件。
- 报告不含标题、正文、候选内容/提示词/模型名或任何密钥；`bodySha256` 是唯一内容指纹。

## 9. readerCompatibility

仅统计、不迁移：

- `books` 与 `booksByFormat`（`txt`/`md`/`epub`，其余归 `unknown`）：来自 `library.json` 的 `books` 数组，只读 `format` 字段。
- `highlights` / `bookmarks` / `progress`：对应 JSON 源的条目计数。
- `files` / `covers` / `searchIndexes`：三个资产目录的递归文件计数；`assetBytes` 为三者字节合计。
- `policy: "legacy-reader-owned"`：书库与阅读记录继续由旧阅读模块使用，不复制成项目卡；只有用户执行“摘录到项目”时才建资料卡（规格 §7.2 步骤 5，不在本切片）。

## 10. ai-secrets：只 stat

- 仅 `stat` 存在性与文件大小，状态为 `metadata-only`（`count: 1` / `bytes`），缺失则为 `missing`。
- 绝不 `readFile`、绝不解密、不触碰 `safeStorage`；policy `keep-in-place` —— 明文只留在原位置，不复制、不进报告、不进日志。

## 11. target store：只 stat

- 仅 `exists` 探测 `dataRoot/CreationWorkspace/workspace.sqlite`：存在 → `targetStore.status = "present"` + warning `target-store-present`（“检测到 target store；本审计未打开、修改或激活它。”）；不存在 → `absent`。
- `inspectedByOpening: false` 是类型级保证：从不打开目标库（避免创建 `-wal` / `-shm` 副作用），不校验 schema 版本、不初始化。
- **版本检查明确留待激活前预检**（规格 §7.2 步骤 1）：本模块只回答“存在/不存在”，目标 store 的版本与可打开性由激活迁移的前置预检负责，不在本切片。

## 12. 10 条契约与静态禁写门禁

门禁 `scripts/verify-creation-migration-audit.mjs` 执行三步：

1. **静态禁写扫描**：扫描 `electron/main/creation-migration-audit/` 下**除 `contract.ts` 外的全部 `.ts` 文件**（contract.ts 是夹具写入方，显式排除），合并源码后检查，包含以下任一能力字符串即失败：`writeFile(`、`appendFile(`、`mkdir(`、`copyFile(`、`rename(`、`unlink(`、`rm(`、`open(`、`truncate(`、`chmod(`、`utimes(`、`createWriteStream(`、`node:child_process`、`from "electron"`、`better-sqlite3`、`node:sqlite`。
2. **类型检查**：`tsc --noEmit -p tsconfig.main.json`。
3. **运行时契约**：esbuild 打包 `contract.ts` 后用 Node 运行，解析末行证据，要求 `allPass === true && tests === 10`。

10 条黑盒契约（`contract.ts`，夹具均使用“测试 EPUB”“测试 TXT”等中性名称）：

| # | 场景 | 断言 |
|---|---|---|
| 1 | 数据根不存在 | `canProceed=false`（`root-missing`） |
| 2 | 数据根是文件 | `canProceed=false`，含 `root-not-directory` issue |
| 3 | 正常夹具只读性 | 审计前后目录指纹（相对路径 + 大小 + SHA-256）逐项相等；`writesPerformed=0`、`activated=false`；审计后不存在 `CreationWorkspace` 目录 |
| 4 | 正常夹具报告语义 | `canProceed=true`；灵感计划 `bodySha256` 与正文哈希一致、`variantsCount=1`；`readerCompatibility` 按格式计数（epub=1、txt=1、files=1）；`aiSecrets` 为 `metadata-only`；`targetStore.inspectedByOpening=false` |
| 5 | 报告隐私 | 序列化报告不含 ai-secrets 明文、灵感正文、候选内容/提示词/模型名、真实书名、数据根绝对路径 |
| 6 | 主文件解析失败 + `.bak` 有效 | `recoverable-backup` + `using-backup` warning |
| 7 | 主文件结构不可识别 + `.bak` 有效 | 回退 `.bak`，`recoverable-backup` |
| 8 | 空主文件且无备份 | `canProceed=true`；`status="empty"`；含 `source-empty` warning |
| 9 | 自定义 `libraryRoot` | 自定义书库根下的 `library.json` 与 `files/` 被正确盘点（books=1、files=1） |
| 10 | 主 + `.bak` 均损坏 | `invalid`，`canProceed=false`（blocking） |

门禁实际输出（2026-08-09 复核）：`[verify-creation-migration-audit] 10 read-only audit contracts verified.`

## 13. 明确未做（零写入边界）

- 未写目标 store：无 schema、表、事务、索引；从未打开，无 `-wal` / `-shm`；不做版本检查（留待激活前预检）。
- 未改写旧 JSON 及其 `.bak`。
- 未写激活指针（userData 中的 storage pointer 不属本模块职责）。
- 未创建备份：无 manifest、校验和或目录拷贝（升级前备份属激活阶段，规格 §7.2 步骤 2）。
- 未铸造 ID 映射文件：`proposedTargetId` 只是计划，最终映射在激活事务中决定。
- 无写探针、无 `safeStorage` 调用、不解密任何内容；资产目录盘点失败只报 `inventory-error` warning，不写任何探针文件。
- 无 IPC / preload / renderer 接线，无 UI。
- 失败项只带旧 ID 与固定中文原因，不携带路径、堆栈、正文或真实书名。

## 14. 下一切片边界

按规格 §9，切片 3 之后是**切片 4「项目壳与新建闭环」**（项目首页、两级导航、三步向导、首章默认场景），本审计模块在切片 4 不承担任何职责。

本报告是后续**切片 11「旧数据激活迁移」**的输入契约：激活迁移将消费 `canProceed`、`sources`、`inspirationPlan`、`readerCompatibility` 与 `targetStore`，并执行升级前备份、复制构建、灵感入库、校验、指针切换与失败回退（规格 §7.2 步骤 2–9）；目标 store 的版本检查也在激活前预检（步骤 1）中完成。在激活迁移实现之前，本报告**不得被当作已完成迁移的证据**。
