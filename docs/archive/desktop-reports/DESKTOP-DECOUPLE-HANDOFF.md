# 桌面端功能解耦接力交接（2026-09-04）

> **给接力 Agent 的第一份文件**：阅读本文件 + `git diff` 工作区后，即可无缝继续
> 「creation-workspace/index.ts 按域切片」任务。上一 Agent 因额度耗尽提前交接，
> 工作区状态已全部验证通过（见下）。

## 1. 任务目标

把 `electron/main/creation-workspace/index.ts`（原 8276 行，巨型类）按域切片为独立
模块，让未来桌面端开发更顺利。纯移动式重构：**不改变任何行为、不加功能**，
每个切片 = 方法移入新模块 + 宿主薄委托 + 契约回归。

## 2. 已完成切片（全部验证通过，工作区保留）

| 模块（新文件） | 行数 | 移出的方法 | 回归验证 |
|---|---|---|---|
| `workspace-utils.ts` | 213 | 共享工具：isRecord / escapeLike / makeSnippet / jsonStringValues / extractSceneText / validateId / validateTitle / validateBaseRevision / parseJsonArray / isConstraintError / resolveBeforeId + 搜索/替换常量 | 三套 tsc ✓ |
| `stats-sessions.ts` | 214 | runStatsView / sessionReport / sessionDelete / runSessionList | verify:creation-stats 14 ✓ |
| `search.ts` | 246 | runSearch + searchScenes/Cards/Chapters/Projects | verify:creation-search 12 ✓ |
| `inbox.ts` | ~390 | runInboxList/Count/Create/Update/Delete/ConvertToCard + inboxFromRow | verify:creation-inbox-count 6 ✓ / inbox-convert 5 ✓ |
| `resource.ts` | ~140 | runResourceList/Attach/Detach | verify:creation-resource 4 ✓ |
| `annotation.ts` | 431 | runAnnotationList / Create / Update / Delete / Reanchor | verify:creation-annotation 5 ✓ |
| `replace.ts` | 339 | replaceScopeScenes / runReplacePreview / replaceApply | verify:creation-replace 24 ✓ |
| `structure.ts` | 670 | 卷/章/场景全部 18 个 mutation 及大纲重构/预览/事务/撤销 | verify:creation-outline 27 ✓ / verify:creation-p1-lifecycle 17 ✓ |

当前 `index.ts`：**5634 行**（从原 8276 行净减少 **2817 行**，降幅 34%）。`git diff --stat` 干净。

## 3. 切片阶段结论
A1 阶段 CreationWorkspace 八大子域（utils, stats, search, inbox, resource, annotation, replace, structure）全部切片完成！
基线状态：
- 三套 tsc（main/renderer/node）：0 错误
- 10 项创作契约测试：全部 100% 验证通过（109 个契约全部通过）
- Vitest 单元测试：783 passed / 4 skipped (81 test files)

后续接力目标：
- B1: ReaderPage（1161 行）模块化拆分子目录（TxtMarkdownReader 等）
- B2: SettingsPage（1153 行）按域拆 sections
- 桌面端全方位优化（UI/UX 细节打磨、性能、错误边界、样式与无障碍）

## 4. 每切片必跑验证（全部真实重跑）

```powershell
npx tsc -p tsconfig.main.json --noEmit   # 主进程类型（新模块必须先过这里）
npm run verify:creation-stats            # 14（stats-sessions 回归）
npm run verify:creation-search           # 12（search 回归）
npm run verify:creation-workspace        # 11
# 对应切片契约（见上表）
npx vitest run                           # 当前基线 783 passed / 4 skipped
npx tsc -p tsconfig.renderer.json --noEmit
npx tsc -p tsconfig.node.json --noEmit
git diff --check -- . ':(exclude)android/**' ':(exclude)archives/**'
```

> 注意：verify 脚本会先跑全量 tsc；若并行 Agent 的 WIP（如 replace-service.ts、
> snapshot-retention-contract.ts）报类型错，会阻塞所有 verify——先 `git status` 确认
> 是否并行改动，属于他人的文件不得修改，属本任务范围的先修再跑。

## 5. 已踩的坑（务必避免）

1. **绝不用 PowerShell `Set-Content`/`Get-Content` 写回大文件**：会加 BOM + 统一行尾，
   产生全文件 diff 噪音（曾把 index.ts 弄出 1842 行假 diff）。只准用 edit 工具改文件。
   万一污染：`git checkout -- <file>` 恢复 HEAD 后重做（本任务改动尚未提交，HEAD 即基线）。
2. **中文注释在文件里有历史乱码**（GBK/UTF-8 混淆，如 `鍗＄墖`）：
   edit 工具的 oldString 必须从 **read 工具输出**复制（read 能正确解码），
   不要从 PowerShell 输出复制；含中文的匹配失败时改用纯代码行做锚点。
3. 行号随 import/删除漂移：删除方法用「read 精确文本 + edit 整块替换」，
   不要用脚本按行号删（括号配平在字符串上不可靠）。
4. `ProjectDailyStat`/`CreationSearchHit` 等类型移出后，记得清理 index.ts 的
   import（tsc `TS6133 declared but never read` 会提示）。
5. 共享工具（isRecord 等）先移入 workspace-utils，再切模块，避免循环依赖。

## 6. 环境与边界

- 共享脏工作区：不 commit / push / stage；不碰 `android/**`、`archives/**`、公共 seam
  （`electron/main/index.ts` 有并行 Agent 的未提交搜索 charOffset 改动，勿动）。
- 契约/测试是回归网：任何"成功"必须列出实际命令与数字，不引用旧绿灯。
- 完成全部 A1 后：B1 ReaderPage（1089 行）拆 TxtMarkdownReader 子目录、
  B2 SettingsPage（1110 行）按域拆 sections、C 系列 renderer service/hook/类型按域拆
  （详见对话中的解耦计划，若不可见按 `docs/handoff/current.md` 的桌面端现状校准）。
- 桌面端当前主线是清样工作台（tokens.css）+ 创作雷达/AI 上下文包 + 阅读器升级，
  已无已知未修缺陷；本解耦任务是独立的工程收尾，不与那些方向冲突。