# 阶段 E 第二轮报告复核（2026-09-29）

## 判定

三窗补正均已落盘，A–E 整体仍 **4/5=80%**。Android 分组与跨目录差异还不能支撑提交或目录切换；Git 方案已纠正默认 reset/checkout 回退，仍依赖前两项。Codex 本轮只读核查报告和 E 盘 CSV，不执行 stage、commit、push、reset、checkout、构建或设备操作。

## 已核原件

- `stage-e-full-coverage-matrix.csv` 存在，SHA-256 `eb289a71bca83fe5c6e8b4fee845fc4717008e1e278b8ab498c2fc30fd2ba66c`，PowerShell `Import-Csv` 实算 **376 行**：G0 5、G2 4、G3 3、G4 9、G5 7、G6 6、G7 3、G8 103、EXCLUDED 56、**HOLD 180**。矩阵中**没有 G1**；`android/app/build.gradle.kts` 整行是 HOLD，而报告正文把版本号 hunk 放 G1。该文件应按 hunk 表示多个归属，不能用单一行无说明地覆盖。
- `orig-unique-full-reconciliation.csv` 存在，SHA-256 `87b57341f8106c7272e4c2551cde9fdde3788ea116a798ab4438f12e66072fdc`，实算 92 行：`CONFIRMED_COVERED` 21、`CONFIRMED_COVERED_MIGRATED` 6、`LIKELY_COVERED` 23、`NEED_REVIEW` 42。**65 条待审项的 `handling` 字段均含异常 `.Count` 文本及插入的源代码行**（例如 `android/app/build.gradle.kts`）；这不是可信的逐项处置建议，需修生成脚本并重新导出、校验 CSV。行集合包含或符号迁移是有用线索，但忽略注释/空行/上下文后，不能单独证明功能、配置或资源语义等价；“27 确认覆盖”须按证据等级收窄。65 项仍未完成语义裁决。
- Git 方案已移除默认 `git reset HEAD~1`、checkout 和移动目录作为失败回退，承认 preflight 备份是时点快照，并将大构建放 E 盘。原仓库 partial clone 离线缺对象与永久损坏区分得更准确。其最终提交顺序与旧提交归属仍是待定草案。

## 下一步并行边界

**当前快照保护：** Codex 已在 E 盘创建 `E:\creation-reading-assistant\codex-e-preflight-20260929\recovery-current-docs-android-20260929.tar.gz`，覆盖当前 `docs/`、`android/app/src/`、`android/app/schemas/`、`android/app/build.gradle.kts` 共 2325 个归档条目，871,234,334 B，SHA-256 `dd80bbca39d0d46552829752496ad6a188f6e61bcc2a7c4d079de0afe54e0f53`；`tar -tf` 可列出条目。它是**本时点快照**，不覆盖其他源码/配置目录、Git 对象及后续新增文件。E 的目录处置前仍须对未覆盖资产和新增改动增量保护。

1. Android 由互不重叠的调查窗口拆成：数据/同步/配置 HOLD、UI（非 reader）HOLD、reader/pager G8 与 reader UI；各写独立报告/矩阵，不直接覆盖总矩阵。`build.gradle.kts` 等共享文件只给 hunk 归属建议，由 Codex 集成。不能把尚未拆清的代码排除或默认为可提交。
2. 资产窗口修复 CSV 生成错误，并对 65 项分批作语义差异判断。若原目录提交对象不可读，保留“未核”；工作树的逐文件差异仍可核。原目录独有 3 个 Electron 文件与其他文档必须保留，桌面线单独处理。
3. 先补 E 盘增量保护并校验，再综合各报告形成**可审阅的精确候选清单**。用户只在清单明确后按收口计划 §E 授权 stage/commit 或目录处置。

上述各项未闭合前，阶段 E 不退出，不将 80% 上调。
