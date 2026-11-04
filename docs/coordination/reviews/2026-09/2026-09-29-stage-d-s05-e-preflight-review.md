# D 阶段 S05 与 E 入口复核（2026-09-29）

> **2026-09-29 后续状态：** [S05-C 手机布局补验及 D 限定退出](2026-09-29-stage-d-limited-exit-review.md)已完成。下文 60% 和“C 手机布局缺口”是此文件写入时的历史状态；E 仍未执行提交或目录切换。

## D 阶段状态

**A–E 全局仍为 3/5=60%，D 尚不退出。** S05 报告的 A（滑块跳至书末后自动保存）、B（首页完成卡分段采样）、C（显式标记夹具取消后百分比回落）有相应 DB/UI 原件；A/C 的两条路径被正确区分。Codex 实读 A2 DB `100/finished`、A4 DB `99.9914/finished`、C2R0 中途 offset `1103064`、C2R1 `100/finished`、C1R `100/reading`、C4R/C5R `47.4436/reading`。报告列出的 `db/C3R-reopen-db.txt` **未落盘**，实际 `db/C3-reopen-db.txt` 属第一轮作废场景；C3R 截图/XML 与 C4R/C5R DB 可支持“重开并轻滑后回落”，不足以独立证明“轻滑前 DB 已回落”。应收窄 C 的具体时点。

设备恢复方面，报告的 `prefs-after-restore.txt` 含 `files/datastore/*.pb: No such file or directory`，`files-after-restore.txt` 也仅列部分书文件，不能从这两份原件推出其声称的 13/13 与 36/36。本轮 Codex 在同一 MuMu qahost 上**只读补核**：`adb devices` 确认唯一 `emulator-5556`，主 DB SHA-256 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` 与基线相同；36 个既有书文件、6 个 datastore 和 7 个 shared_prefs 共 **49/49** 当前哈希与报告事前清单一致，设备密度现为物理 280、无 override。逐文件原件在 `E:\creation-reading-assistant\codex-e-preflight-20260929\s05-current-qahost-hash-recheck.txt`，SHA-256 `4a1223104937b082fc97465903fe96793442c1ba47f0a7c9676ecabbea936d64`。这确认**当前状态**恢复，不能补造验收结束瞬间缺失的原件。

**手机布局缺口：** B 的截图有底部导航，符合窄屏；C 的 `C1R-cancel.png`、`C5R-shelf.png` 显示左侧导航栏，属于宽屏路径。源码 `topLevelNavigationMode` 在窗口宽度 ≥600dp 使用 `RAIL`；1080px/280dpi≈617dp、1080px/420dpi≈411dp。因此 C 虽有 MuMu 功能证据，却不能凭报告“全程 420dpi 手机布局”的声明算手机布局验收。需要在 420dpi 下**定向补 C 的手机 UI**，保留操作时的 `wm density` 原件及同书 DB/UI 前后，再恢复设备密度。无需重复 A/B、全量 JVM、V5 62 条。

## E 阶段复杂前置已做

- 原仓库仅只读诊断：`git rev-list --objects HEAD --missing=print --no-object-names` 中 **425** 个缺失对象；`--all` 中 **485** 个缺失对象。损坏涉及当前 HEAD 的可追溯历史，不能归为仅索引缓存问题。恢复仓库 `git fsck --full --no-reflogs --connectivity-only --no-progress` 无输出；因此后续集成候选基础应优先为**健康恢复仓库**，原仓库保留原状。原仓库具体独有资产仍需逐项对账，不能直接换路径。
- 诊断原件在 `E:\creation-reading-assistant\codex-e-preflight-20260929\`：`original-head-missing.txt` SHA `0211932bf10104cadc346e2774416906f29bef416092f028d8b9c0b7256c5fc5`、`original-allrefs-missing.txt` SHA `545bed4293a866a5ca385ce8cd4858436bc06db750a2f9e1cafda282b817737d`、`original-fsck.txt` SHA `522283909fb700eae7c5f9a92c2edbbe9bdb58b68cdfaf12ac47ad2d0144752c`。
- 为避免未跟踪报告/源码在 E 整理中丢失，已做**E 盘只读源备份**：`recovery-docs-preE.tar.gz` 867,607,990 B、1130 个条目、SHA `efa8b6eb7d9e50e731906bde07acdfa34307f0d8d555de18b38c3dd50107b408`；`recovery-tracked-worktree.patch` **3,049,776 B**、SHA `567b6d319e59a9b3e8cc4df5c795037e4f49c94f84a69d634a197cb74c8f3864`，`git apply --reverse --check` 通过；`untracked-source-preE.tar.gz` 145 个文件、SHA `f41c318befa923be6f23248daF0634fd73da0f095410d1640825f492c26f2d84`，含 Room v15 schema 与 V5 测试文件。备份是 2026-09-29 此时点快照，后续新增文件需增量保护；未备份 `.git`、构建目录及所有其他未跟踪资产。
- 原/恢复目录指定代码区的**逐文件 SHA-256 差异清单**已落 `original-recovery-file-diff.csv`（SHA `e307b996889fa6b10f5ad6be37f6845813ed6b5937f3f908ec20177d0a620343`）。范围限 `android/app/src`、`android/app/schemas`、`src`、`electron` 及 8 个构建配置；其中 1047 文件相同、268 文件内容不同、65 文件仅恢复目录有、**3 文件仅原目录有**。后三者是 `electron/main/creation-workspace/{bundle-merge,proof-engine,validate}.ts`，已只读复制到 E 盘 `original-only-preserved/` 并逐文件哈希与原件匹配；它们属桌面端独有资产，后续须按桌面边界单独处理，不能默默丢失或混入 Android 提交。此清单不是全仓覆盖，其他目录仍需 E 阶段后续对账。
- E 盘 `android-source-candidate-inventory.csv`（SHA `e94c2a38c6a66fe367b465b5db8b9243acdf01ea0399afbf7c44104ef45fdbe9`）逐文件列出 **366** 个当前 Android 源/测试/schema/配置改动及状态、字节、SHA：188 个已修改 main + 42 个未跟踪 main、39+74 个单测、8+13 个 androidTest、1 个未跟踪 Room schema、1 个已修改构建配置。它是**审查候选清单**，不是“366 个文件都该提交”的决定；E 仍需按功能/报告和 hunk 边界筛选。

## 下一步

1. 单一 MuMu 所有者只补 S05-C 手机布局与实际密度证据；若同书状态需重建，仅在 qahost 隔离夹具操作，按新鲜冷备恢复并对账。仍保留“真实连续逐页书末”、实体手机/MIUI、S14 真实 kill 与整包 DB+文件非原子的边界。
2. D 的其他门禁已有同输入证据，不重复运行。C 手机证据补齐后才按计划 §D 将必需用例、边界、FAIL/BLOCKED/NOT RUN 分类并判断 D 限定退出。
3. E 仅继续**只读**清单与健康基础论证；提交、目录切换、清理仍受计划 §E 的用户授权门槛约束。

本轮未改产品源码、测试或原仓库工作树；未运行 Gradle，未对 MuMu 作写操作。
