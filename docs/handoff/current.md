# 当前交接入口（2026-11-04 收官版）

**项目状态：两产品线全部标准质量门绿，可自主执行工作清零，剩余全部为用户决策项。**

- **Android（MuMu 口径）**：V0 八卡（AR01—08）全部真实闭合，总裁决 `V0_ACCEPTED_WITH_SCOPE_DEVICE`（裁决书 `E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar08/REPORT-20261103-final.md`）；JVM 3808/3808、设备批 AR03/04/05 + NB03 图测试 + NB04 SAF 文件渠道全 PASS、166+R2 恢复全绿、lint 0 errors/7 warnings。
- **桌面**：设置重构+设置搜索 WIP（20 路径未提交）**功能已完整**（tsc×3 + vitest 981/981 + 契约 54/54 + electron-vite build 全绿，审计 `next-v2/nb14-desktop-wip-audit/REPORT.md`）；打包验收**全绿**（stage1 族 2/2 + stage3/4/4b 43/43 checks，经 NB16 维护：脚本原生支持 CREATION_PACKAGED_DIRECTORY 覆盖、stage4 app.close() 挂起已修；`next-v2/nb15-desktop-packaged-acceptance/` + `nb16-stage-scripts-maintenance/`）。
- **NB 线台账**：NB01 成因调查（无法归因，保护门守护）/ NB02 裁定不跑 / NB03 闭合 / NB04 闭合 / NB05 盘点 / NB06 文档 / NB07-09 闭合 / NB10 闭合 / NB11 BLOCKED_ENVIRONMENT（MuMu 树确认按钮被导航栏遮挡，测试源带诊断保留 candidate）/ NB12-13 闭合 / NB14 审计 / NB15 PARTIAL。报告全在 `E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。

## Root 决策批（2026-11-04 已裁决，[决策文档](../coordination/2026-11-04-root-decision-batch.md)）

原待决事项及盘点新识别事项已由 Root 全部裁决（用户 2026-11-04 委任决策），待 ZCode 按文档执行：

1. **RD-A 桌面提交**：授权 commit 20 桌面路径 + 桌面验收脚本（全门绿证据），不 push。
2. **RD-B 文档/配置提交**：授权提交文档重组成果与交接记忆，`.gitignore` 增补排除会话工件（`dump_secrets*.xml` 等只忽略不删除），不 push。
3. **RD-C Android 源码**：授权只读盘点 + 锚点核对（6be3b01b pin、D↔candidate diff 归类）；全过才条件提交，任一不符即停报告。
4. **RD-D NB02**：维持不跑（原红保留，208 覆盖同类边界）。
5. **RD-E SAF 渠道**：产品路径保留，仪器测试挂起；NB11 裁决降格为工作假设。
6. **RD-F 巡检自动化**：维持暂停；重建需单 ID + 新鲜 prompt 快照。
7. **RD-G 证据卫生**：授权离线小批修 NB04 字节数/措辞/重钉、NB11 降格披露、ar03 撤销注记（原证只增不改）。

执行顺序 RD-A → RD-B → RD-G → RD-C；报告落 `E:/creation-reading-assistant/coordination/root-decision-batch-20261104/REPORT.md`；全部 commit 仅本地，不 push。

**RD-C 分歧裁决（2026-11-05 补）**：RD-A/B/G 已执行（commit `3751b41` / `35ed1a5`），RD-C 锚点核验证伪了原合同假设——恢复成果唯一载体是 E candidate（only-in-E 123 文件 = 207/209/NB 线成果；D 树侧 89 个差异为 10 月初独立演进，验收归属无法建立）。裁决取落地路线：[RD-C-LAND 合同](../coordination/2026-11-05-rdc-landing.md)——先 archive commit 保全 D 树分歧谱系，再整体落地 candidate 快照并以 treediff 零差异为硬门；恢复期"不回灌 D"隔离措施于收官解除（理由见合同）。落地后 D 仓库为后续源码主载体，E candidate 转为冻结已接受基线（只读参照）。

## 设备与权限（仍然有效）

- MuMu instance 1 启动/验收/各批 166+R2 恢复已获用户同意（[权限纠正](../coordination/2026-11-02-mumu-acceptance-authorization-clarification.md)）；serial/boot 由执行者 fresh 探测，禁止转向实体手机，一切设备证据只能称模拟器。
- `android-zcode` 保持 PAUSED；接任会话连续执行不是恢复该自动化。
- 无 Git 暂存提交 push PR 发布、既有资产删移、D 产品回灌、pm clear/盲恢复、正式库/私库 session DB 或记忆写入许可。WIP/DB-WAL-SHM/原红/唯一证据/冻结 APK/恢复资产保留。
- 接任 Root 由用户委任，可承担普通技术决定、最小因果修复和验收；公共 candidate/Gradle/设备各单 owner，Gradle 持 `E:/creation-reading-assistant/locks/android-gradle-exclusive.lock` 串行。
- 新业务设备批前置：单 owner、fresh 身份链、原 APK/data/userState/八设置/保护根/创建物基线及 166+R2 恢复准备；保护查询后最终 identity；已成功卸载/恢复不为补证重做。
- 判伪纪律：**0 项 instrumentation 不构成通过证据**；业务真伪三件套=stdout 时长量级对照基线、logcat start/finished instr 链、testApk SHA 三方（磁盘/冻结 manifest/capture）互核。

## 产品与实际候选（2026-11-04 实核）

D 仓库：`D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919`；唯一 candidate：`E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android`。不 D 回灌、不复制工程/缓存。

| 输入 | 2026-11-04 现态 |
|---|---|
| 产品 APK | 53899310B / `c396ebe1…`（未变，接受输入） |
| androidTest APK | NB04 当批 `1555fc7f… / 10760068B`（SAF/SAFTREE 测试；其二进制未被单独保全，身份由 summary/capture/evidence-manifest 钉扎）；更早的 95e734bc 冻结包二进制保全于 `next-v2/nb04-saf-journey/artifacts/`；盘上现态已被 NB11 系列重建覆盖为 `6e2c171b… / 10768258B` |
| 打包产物 | `release-beta/win-unpacked`（NB15 构建绿）+ `release-beta-stage4-final`（NB15 构建绿） |
| 证书/runner | SHA256 `05249d78…`，`CraIsolatedTestRunner` / `com.creationreadingassistant` |

## 已有业务与恢复边界（复用，不重跑）

| 任务/批次 | 可沿用结论 |
|---|---|
| 205-v1 | 普通退出 shelf-screen 超时原 FAIL 保留（T165V1-20261030-150607）；新输入不复现不抹旧红 |
| 206-v1 | recover-only 有限关闭；原 query rc/userState/绑定等限制保留 |
| 207-r3/r4 | 源码/编译及当批 APK 对限定接受，不扩全产品 |
| 208 (162905) | 普通退出/书架重开单方法 OK；接受范围见 task208 REPORT |
| 209 A/B/D | A 图 7 项 PASS（NB03 已在冻结包复验）、B/C1 生产组合（NB03 批B 复验） |
| E151729/P5 165519/I190927/C-R2 210503 | 各自既有接受范围保留 |
| AR03/04/05 (11-03) | T165V1-20261103-114643/120753/121822，全 PASS+UNINSTALLED |
| NB03 A/B (11-04) | T165V1-20261103-234022（A：PASS+recover-only 有限收口）/235956（B：全绿） |
| NB04 (11-04) | T165V1-20261104-015548，SAF 文件渠道 PASS+UNINSTALLED |
| R2 合同 | gen10106-v1（inventory 8b2e3317、test 包 0d18、保护 71/820）；恢复优先于新 mutation |

业务 scope 根：`E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/attempt-<runId>/`；NB 交付根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。runtime：`E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r2/scripts/`（preflight_209_r2 → prepare_inputs_209_r2 --batch X → run_batch 驱动；AR02 frozen manifest 门覆盖 EP/C/I）。

## 阶段史（详情按需取，不整份加载）

- 11-02 Antigravity AG-NEXT 八卡离线交付 + Root 复核（[队列](../coordination/2026-11-02-antigravity-next-queue-v2.md)、[复核](../coordination/2026-11-02-antigravity-stage-delivery-review.md)）。
- 11-03 夜间 ZCode 接任：保护基线 18 文件恢复 → AR03/04/05 真实闭合 → AR08 终裁 V0_ACCEPTED_WITH_SCOPE_DEVICE。
- 11-04 Wave1-5：NB01-15（见头部 NB 线台账）。其 AR03-05 设备报告被审计证伪：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar03/RETRACTION-20261103.md`（原文件保全）。
- 11-04 收官+全面复审+NB16 打包验收闭合后，Root 决策批七项裁决落盘（[RD-20261104](../coordination/2026-11-04-root-decision-batch.md)），ZCode 执行 RD-A/B/G/C。
- 11-05 RD-C 分歧裁决：恢复成果载体实测为 E candidate；授权 archive+落地两段式入库（[RD-C-LAND](../coordination/2026-11-05-rdc-landing.md)）。

## 执行与状态入口

- state：`E:/creation-reading-assistant/coordination/android-zcode-luna-state.json`（写前核指纹合并；readySHA 实算）。
- 夜间巡检：`E:/creation-reading-assistant/coordination/zcode-night-watch-20261103/`（heartbeat+progress+各 run 日志）。
- [接手说明](next-codex.md)、[通用手册](continuation-playbook.md)、[单任务模板](next-agent-start-template.md)、[下一批规划](../coordination/2026-11-03-next-batch-proposal.md)。旧路径查 [document-map](../document-map.csv)。
