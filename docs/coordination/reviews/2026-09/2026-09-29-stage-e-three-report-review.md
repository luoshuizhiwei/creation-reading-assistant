# 阶段 E 三窗只读报告复核（2026-09-29）

## 总判定

三个指定报告均已落盘：Android hunk 计划、跨目录资产对账、Git 入口方案。它们可作为调查线索，**尚不足以授权执行提交或换目录**。A–E 整体仍为 **4/5=80%**。本轮 Codex 只读复核，未 stage、commit、push、reset、checkout、构建、移动目录或操作设备。

## 可接受的发现

- 恢复仓库 HEAD、`main` 与 `origin/main` 均为 `5aed5939600e491843e3c44453921bd6273a233c`；当前分支为 `codex/recover-worktree-20260920`。恢复工作仍在未提交工作树。
- 原目录是 `blob:none` partial clone，缺对象不能直接推断远端历史永久损坏；在当前离线状态仍不适合作为集成基础。恢复仓库可作为候选基础，原目录及其独有资产须保留。
- 资产报告新增全树对账索引与 E 盘清单；原目录独有的 3 个 Electron 源文件及其他文档有明确去向待定。报告还发现旧备份未覆盖的文件，补保护属于下一步。
- Android 报告识别了多个混合 hunk 和 Room v15 schema、qahost 基础设施等重要依赖，适合继续细化。

## 必须纠正的结论

1. **Android 提交组不是完整、已验证的执行清单。** 当前 `git diff --name-only -- android/app/src` 有 234 个已修改文件，另有 129 个未跟踪文件；报告 G0–G8 只列部分文件，G8 和 B4/B5/B8/B9 仍未拆清。它把若干组标为“编译边界 ✓”，但没有按各组的暂存快照编译。D 阶段 3208/3208 是整个冻结工作树的 JVM 结果，不证明每个拟议提交独立可编译。`AppDatabaseMigrationTest.kt` 位于 `src/androidTest`，不能声称包含在 JVM 3208 中；应引用其实际设备测试原件或标为未核。`build.gradle.kts` 的版本号注释指向父表 `@Upsert` 数据修复，不应按报告 G1 的说法归入 Room v15 迁移。S05-C 420dpi 定向补验已在 D 退出复核中完成，报告的“手机布局 caveat”表述需要更新为准确时点边界。
2. **“内容冲突 0、原目录领先 0”尚未证实。** 资产报告自己列出 92 个 `ORIG_UNIQUE`，并承认未逐文件做语义 diff。mtime、文件体积和少量抽样只能提示调查方向，不能证明 92 个文件均被恢复目录完整取代。将 204 个 Git 归一化 blob 相同与 92 个未确认项分开记账；对后者做逐项 hunk/语义差异表，尤其标注原目录独有行或资产。E 盘 `recovery-untracked-not-backed-up.txt` 也须核对当前新增报告，避免把时点清单当作完整现状。
3. **Git 方案不可直接执行。** 它在失败分支提出 `git reset HEAD~1`，与当前“不执行 reset/checkout”约束冲突，也会在共享脏工作树上造成风险。失败时应停止、保留现场并先修复或形成新的授权方案。它要求先让用户对两个旧提交作“已覆盖/放弃”裁决，但可查明的文件等价和差异应先完成独立对账，再展示具体取舍。不能仅因 `SearchScreen.kt` 含 `contentType` 就认定 `2a428851` 全部六文件等价。每组测试方案应按实际改动和同输入证据决定，避免每提交重跑全量 JVM/V5；大型构建与产物继续放 E 盘。原仓库保留原样时，使用恢复目录作开发入口不必先移动原目录。

## 下一轮并行所有权

| 窗口 | 唯一可写报告 | 完成条件 |
|---|---|---|
| 1 Android | `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-android-hunk-plan.md` | 完整文件/hunk 归属矩阵、G8/B4/B5/B8/B9 拆分、版本号归属修正、测试范围与编译状态如实标记；剩余项明确 HOLD。 |
| 2 资产 | `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-asset-reconciliation.md` | 92 个 `ORIG_UNIQUE` 逐文件差异判定及原目录独有资产精确清单；不能用 mtime 代替语义对账。 |
| 3 Git | `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-git-entry-plan.md` | 删除默认 reset/checkout 回退；以 1/2 的结果修订最小执行/停止点、增量备份范围和相称验证；不要求用户裁决尚可查明的事实。 |

三窗只编辑各自报告，不改源码/测试/公共交接文件。其后由 Codex 汇总为一份实际可审阅的候选 hunk、依赖、资产处置方案，再处理 §E 授权门槛。
