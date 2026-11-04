# RD-C 落地合同 RD-C-LAND（2026-11-05）

状态：`DECIDED_AWAITING_ZCODE_EXECUTION`
决策方：Codex Root（用户委任决策权延续）
执行方：ZCode
前置证据：`E:/creation-reading-assistant/coordination/root-decision-batch-20261104/REPORT.md` RD-C 节 + `attachment-rdc-treediff.json` / `attachment-rdc-android-changes-291.txt`

## 背景（分歧实测结论）

RD-20261104 的 RD-C 锚点核验证伪了原合同假设"D 树未提交修改 = 恢复成果载体"。实测：

- only-in-E 123 文件（androidTest 64 + test 51 + main 8）= 207-r3/r4、209 批次、NB 线成果，**只存在于 E candidate**；原锚点 pin `6be3b01b…`/17267B 描述的是 candidate 现态。
- content-diff 89 文件：D 树侧为 10 月初独立演进（BookDao/SyncRepository/App.kt 等），验收归属无法建立。
- only-in-D 49 文件：全部任务执行日志（已被 `*.log` gitignore 覆盖）。

两树自 10-01（`e39a78d`）后双向独立演进。

## 裁决：取落地路线（option a）

**将 E candidate android/ 快照落地 D 仓库，先 archive commit 保全 D 树分歧谱系，再整体覆盖落地，以 treediff 零差异为硬门。**

理由（多方向权衡）：
1. 已接受产品源码目前唯一载体是无版本控制的 candidate 树 + 冻结 APK/报告——这是全项目最大持续风险敞口；E 盘历史上存在清理治理先例，源码不应永久留在任务目录。
2. D 树侧 89 个无验收归属的差异不会丢失：archive commit 先行保全，未来如需甄别可随时 diff 或 cherry-pick。
3. 恢复期"不回灌 D"是修复期的隔离措施，其前提（防止未验证改动污染主树）在恢复收官、全部范围已接受后不再成立；解除措施是决策权内的正常退出动作，理由随合同入库。
4. candidate 覆盖 = 落地"已接受状态"（host `c396ebe1` 所出自的唯一 candidate），不是引入新验收；既有验收范围不变、不扩大。
5. 落地后拓扑永久澄清：**D 仓库 = 后续源码主载体；E candidate = 冻结已接受基线（只读参照）**；E 盘仍按 AGENTS 约定作为构建/缓存工作区，未来合同各自定义工作副本。

备选 (b) 维持现状被否决：风险敞口继续存在，且每次后续会话都要在"哪个树是权威"的歧义上消耗判断；等未来再 reconciliation 只会更贵。

## 执行步骤（串行，全本地，无 push/设备）

### Step 0：合同入库（commit 3.0）

显式 add：本合同文件 + `docs/handoff/current.md`（本批已预写更新）。消息：`docs: RD-C landing contract (RD-20261104 adjudication)`。

### Step 1：archive commit（commit 3a）

显式 add：D 树 `android/` 下当前全部修改与新增（`*.log` 已被 gitignore 自动排除；排除 build/.gradle 类目录）+ `scripts/android-reuse-freeze.py`、`scripts/test_android_reuse_freeze.py`、`android/scripts/diag_usb_confirm.py`、`android/scripts/tap_usb_install_confirm.py`、`android/scripts/s04_private_archive_manifest.py`、`android/scripts/s04_private_archive_manifest_tests.py`（含 3 个已删除测试文件的删除记录）。

先全量 status 快照存档；staged 清单确认零 `docs/`、零桌面 `src/`（均已入库）。消息：`archive(android): preserve pre-landing diverged working tree (early-October lineage, superseded by accepted candidate in next commit)`。

### Step 2：落地 commit（commit 3b）

从 `E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android` 复制全树覆盖到 D 仓库 `android/`，排除：`build/`、`.gradle/`、`.kotlin/`、`local.properties`、`*.log`。candidate 侧全程只读。

复制后硬门（全过才 commit，任一不符即停报告）：
1. 重跑文件级 treediff（排除同上）→ **必须为空集**；
2. 锚点 1 pin 文件（REPORT RD-C 节记录的 candidate 路径）在 D 树实测 = `6be3b01b…`/17267B；
3. `git status` 中 android/ 的 M/A 全部来自本次复制，无合同外路径。

消息：`feat(android): land accepted recovery candidate (host c396ebe1 lineage; scopes 207-r3/r4, 209, NB line)`，正文注明：恢复期回灌隔离措施于收官解除，理由见 RD-C-LAND 合同；验收范围不扩大。

### Step 3：收尾

REPORT 追加 `REPORT-LANDING.md`（同目录，含三次 commit 的 staged 清单与 stat、复制排除清单、treediff 空集证明、pin 实测值）；state 更新 `rootDecisions20261104.rdcLanding.status = EXECUTED` 及各 commit 记录（写前核对指纹合并）；current.md 不再改动（已预写）。

## 边界与停止条件

- 不 push / PR / 发布；不删除任何文件（被覆盖文件已先入库于 3a）；无设备/ADB/VM；不写 `.codex/memories`。
- `.git/index.lock` 死锁按 RD-A 先例处理并留痕。
- candidate 侧出现必须复制的 build 产物、treediff 非空、pin 不符、staged 出现合同外路径——任一即停。
