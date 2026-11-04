# Root 决策批 RD-20261104

状态：`DECIDED_AWAITING_ZCODE_EXECUTION`
决策方：Codex Root（用户 2026-11-04 委任："你现在是一个决策方，你要自己思考决定"，要求多方向思考并兼顾现状与未来）
执行方：ZCode（后续会话，凭本文档 + `docs/handoff/current.md` 执行，本批为纯本地操作）

## 输入快照（2026-11-04）

- `docs/handoff/current.md` 收官版：两产品线标准质量门绿，自主执行队列清零。
- 复审报告：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/zcode-comprehensive-review-20261104/REPORT.md`（B1—B5、C1—C10、D 项与 F 修复优先级）。
- NB14 桌面 WIP 审计：20 路径功能完整、全门绿（tsc×3、vitest 981/981、契约 54/54、electron-vite build）。
- git 快照：441 行变更未提交；最后一次提交 `e39a78da9c62f02614207cffb36969a32492eaea`（2026-10-01）。即 Android 恢复源码、全部文档重组与交接记忆均未入库。
- 会话工件裸露：仓库根 `dump*.xml`（30 份）、`dump_secrets.xml`/`dump_secrets2.xml`、`product.patch`、`window_dump.xml`，及 `.opencode/`、`.scratch-shots/`、`.workbuddy-ai/`、`.zcode/`、`.zcodeignore`。
- state 写前指纹：SHA256 `6F8B59934DDC68F93919BFBF332F33AD97891DA2EE1B0B3602D15782EF936143`（写前核对此值，不符即重新读入合并）。

## 决策总览

| 编号 | 议题 | 裁决 | 核心理由 |
|---|---|---|---|
| RD-A | 桌面 WIP 提交 | 授权提交（commit 1） | 唯一有完整审计+全门绿证据的改动集；不提交才是最大风险 |
| RD-B | 文档/配置入库 | 授权提交（commit 2） | 文档重组是用户指示且已完成；交接记忆不持久化，任何树事故即断代 |
| RD-C | Android 源码入库 | 授权只读盘点+锚点核对，全过才条件提交（commit 3） | 一个月恢复成果只存于未提交树，是最高风险敞口；但无逐文件审计，必须先锚点核对 |
| RD-D | NB02（205 旧红复验） | 维持不跑 | 208 已覆盖普通退出重开同类边界；新构建+设备批无增量价值 |
| RD-E | SAF 文件夹渠道 | 产品路径保留，仪器测试挂起；NB11 降格为工作假设 | MuMu 环境阻塞非产品缺陷；产品有 NB04 已验证的应用内替代路径 |
| RD-F | 巡检自动化 | 维持暂停 | 无监控对象；复审证明此前巡检从未真正触发且 prompt 过期 |
| RD-G | 证据卫生修复 | 授权离线小批 | 复审 B1/B2/C1/C2/C3/C7/C9 中仍未闭合项 |

多方向权衡记录：曾考虑"只提交桌面、其余维持现状"——否决，因为 Android 源码未入库的风险随每次会话累积；曾考虑"一次性全量提交"——否决，因为 Android 侧无逐文件审计，混入会话工件（含 `dump_secrets*.xml`）不可接受；曾考虑"文档留待用户审"——否决，因为重组本身即用户指示的成果，提交是记录而非新决定。gitignore 增补排除会话工件属于对既有资产的保护性防呆，非删除。

## 执行顺序与总则

顺序：RD-A → RD-B → RD-G → RD-C → 收尾回写。全部串行，单一 owner（ZCode），无设备/ADB/VM 操作，无 push/PR/发布，无删除或移动既有资产，不做 D→candidate 回灌。

每次 commit 前：`git status --porcelain=v1` 全量快照留存；`git add` 只用显式路径（禁止 `-A`/`.`）；staged 清单与本合同逐条核对，多出或缺少即停。commit 后 `git show --stat` 存档。三个 commit 全部本地，不 push。

## RD-A 桌面提交（commit 1）

范围（显式路径）：

修改：`src/components/ui.tsx`、`src/features/library/ReaderSettingsDrawer.tsx`、`src/features/library/ReaderSettingsPanel.tsx`、`src/features/settings/SettingsPage.tsx`、`src/features/settings/sections/AISection.tsx`、`AppearanceSection.tsx`、`DebugSection.tsx`、`ReaderSection.tsx`、`SectionWrapper.tsx`、`StorageSection.tsx`。

新增：`src/components/ui/FontPicker.tsx`、`NumberStepper.tsx`、`Slider.tsx`、`Switch.tsx`、`src/features/library/ReaderSettingsMorePanel.tsx`、`src/features/settings/SettingsSearch.tsx`、`src/features/settings/__tests__/`、`src/features/settings/reader/`、`src/features/settings/search-registry.ts`、`src/features/settings/settings-controls.css`。

桌面验收工具（NB15/NB16 已审计绿）：`scripts/packaged-stage3-outline-focus-acceptance.mjs`、`scripts/packaged-stage4-acceptance.mjs`、`scripts/verify-reader-settings.mjs`、`scripts/visual-evidence/smoke-final.png`、`scripts/visual-evidence/smoke-log.json`。

提交消息：`feat(settings): settings search + section refactor + reader settings controls`（正文附 NB14/NB15/NB16 门禁结果）。

验证：staged 数量与上表一致（目录按展开计）；不包含任何 `android/`、`docs/` 文件。质量门不重跑（输入未变，复用 NB12-13/NB14/NB15/NB16 实测）。

## RD-B 文档/配置提交（commit 2）

范围（显式路径）：

1. `docs/` 全部修改与新增（含 `docs/coordination/`、`docs/handoff/` 新文件、`docs/document-map.csv`、archive/plans/qa/code-review/research 重组产物；D 条目=被迁移旧路径，由 document-map.csv 对应）。
2. 根文档与配置：`AGENTS.md`、`CLAUDE.md`、`README.md`、`.gitignore`、`.codex/config.toml`、`.codex/agents/android-explorer.toml`、`android-reviewer.toml`、`android-verifier.toml`、`android-worker.toml`。
3. `.gitignore` 先增补以下排除段（保护性防呆，不删除任何文件）再一并提交：
   `/dump*.xml`、`/window_dump.xml`、`/product.patch`、`/.opencode/`、`/.scratch-shots/`、`/.workbuddy-ai/`、`/.zcode/`、`/.zcodeignore`、`/android/scripts/ui_now.xml`。

提交消息：`docs: reorganize documentation tree with migration map; sync agent instructions and ignore session artifacts`。

停止条件：`git diff --stat` 中出现与文档重组/智能体约束同步无关的实质改动（尤其根文档中非文档性变更）即停并报告；`dump_secrets*.xml`、`product.patch`、各工具目录绝不入库。

## RD-G 证据卫生批（E 盘离线，零产品改动）

1. NB04（`next-v2/nb04-saf-journey/`）：REPORT.md 字节数 10759519B 更正为 10760068B（L11/L42）；L47「真机验证」改为「模拟器 MuMu 口径」；ready.json 的 REPORT pin 按实测重算重钉（实测 3670B / SHA256 以现场计算为准）；`88847948` 标注为 attempt-3 编译中间态。当批 APK 二进制若能从 capture/中间目录定位则补保全；定位不到则维持"身份由 summary/capture/evidence-manifest 钉扎"表述并如实记录。
2. NB11（`next-v2/nb11-*`）：报告补降格声明——"按钮被导航栏遮挡"从事实裁决降为工作假设，待环境验证；区分 attempt-9（enabled=true 点击无效，主证）与 attempt-10（enabled=false 根因待查）；补披露 `nb11-recover9.log` 的 `overallOk=false / PARENT_RESULT_NOT_OK` 聚合门红；无层级 dump 原件则删除 bounds 定量表述或注明来源缺失。
3. ar03：RETRACTION-20261103.md 追加一句"2026-11-02 旧报告的指纹表亦不可信"。
4. `docs/coordination/2026-11-03-next-batch-proposal.md`：页首"收官清零"与正文 `PROPOSED` 矛盾修正（标注已被 NB 线实际执行取代）。
5. state.json 卡片残留复查（B3 已部分修复，确认无新矛盾）。

纪律：原始证据（capture/summary/log/XML）只增不改；REPORT/ready 类活文档最小编辑并留更正注记；`.codex/memories` 与其他应用私有库不写。

## RD-C Android 源码盘点 + 条件提交（commit 3）

范围：`android/` 全部修改与新增（source/test/manifest/benchmark/scripts）、`scripts/android-reuse-freeze.py`、`scripts/test_android_reuse_freeze.py`、`android/scripts/diag_usb_confirm.py`、`tap_usb_install_confirm.py`、`s04_private_archive_manifest.py`、`s04_private_archive_manifest_tests.py`。排除（绝不入库）：RD-B 已 gitignore 的会话工件。

锚点核对（全过才提交，任一不符即停并报告）：
1. 目标测试源 pin `6be3b01b…`/17267B 与树内现状一致。
2. D 树 `android/` 与唯一 candidate（`E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android`）做文件级 diff 清单；每个差异文件必须归入：已知已接受任务范围（207-r3/r4、209 批次、NB 线已闭合项）/ 有消费者或测试的新文件；无法归类即停。
3. staged 清单零会话工件、零疑似敏感内容（含 `dump_secrets*` 名称模式扫描）。

提交消息：`feat(android): reader recovery candidate integration (accepted scopes 207-r3/r4, 209 batches, NB line)`（正文列锚点核对结果）。性质声明：这是盘点性入库，不扩大任何既有验收范围。

## RD-D / RD-E / RD-F（记录性裁决，无执行动作）

- RD-D：NB02 维持不跑。原红 T165V1-20261030-150607 保留；208（162905）已覆盖普通退出重开边界。未来普通退出异常 → 新合同 fresh 身份，不复跑 205。
- RD-E：SAF 文件夹渠道——产品能力保留（NB04 文件渠道 PASS + 应用内浏览器路径），仪器化树测试挂起；NB11 措辞降格见 RD-G。未来文件夹导入成为优先特性时另立合同换可达分辨率基线。
- RD-F：巡检自动化维持暂停。重建条件：下一个长无人值守阶段启动时，单 ID + 新鲜 prompt 快照 + 按当时负载定间隔；旧三套 ID/间隔口径作废。

## 收尾与未来路线

收尾：更新 state.json `rootDecisions20261104` 各项状态与 `updatedAt`；REPORT 落 `E:/creation-reading-assistant/coordination/root-decision-batch-20261104/REPORT.md`（含每 commit 的 stat、每锚点核对结果、卫生批 diff 摘要）；current.md 不再改动（本批执行前已预写全部计划）。

未来路线（记录，不制造任务）：本批执行完毕后仓库首次与恢复成果同步，此后新工作一律新合同：Android 新特性按 legado backlog 立合同；桌面新特性需用户新方向；真机/模拟器新批次需 fresh 身份链+恢复合同另行授权。205 原红与异步保存失败不确定性继续作为已记录边界保留。`dump_secrets*.xml`/`product.patch` 留存本地，若用户未来批准清理按精确批次确认后处理。
