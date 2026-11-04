# TXT 保存→加载→恢复链与仪器接缝：只读根因分析

## 任务编号/版本
CRA-TXT-RESTORE-CAUSE-REVIEW-v1，2026-10-06 交付。只读分析；不用 ADB、不构建、不写产品/测试源码、不操作 Git、不改全局交接/TODO。

## 输入指纹
- 产品基线 [TXT-v4 android](<E:/creation-reading-assistant/txt-reentry-fix-v4/android>)：freeze/source-sha256.json 980 条实算 **980/980 一致**（本窗口 Python 实算）。
- 失败批（只读）[txt-final-mumu-accept-v1](<E:/creation-reading-assistant/txt-final-mumu-accept-v1>)：runs/j1-txtv4-20261005-a 全部 raw/*.txt、items/*.json、raw/j1_ui/*.xml/png、binding.json、config、freeze、evidence 已核。
- 对照 [prep-v2 payload](<E:/creation-reading-assistant/txt-final-acceptance-prep-v2/payload>)：测试源与本批 diff 仅两处（binding 目录 /data/local/tmp、chapters 断言 isEmpty→size==1）。
- 前序核查 [2026-10-05-txt-mumu-prep-v2-codex-review.md](2026-10-05-txt-mumu-prep-v2-codex-review.md)。

## 所有权与改动
本窗口仅写本报告与 [E 分析工件目录](<E:/creation-reading-assistant/txt-restore-cause-review-v1>)（4 件：证据取证/代码追踪/候选原因/设备观测清单）。未用 ADB，未构建，未改 D/E 产品、Git、原损坏仓库、正式书库与其他应用私有库。设备与新证据目录归另一窗口，本报告未读取其任何在写结果。

## 环境
Windows 本地静态审查（Python 实算 SHA/解析 dump；Read 通读产品与测试源码）；未接触设备、模拟器与网络。

## 执行与结果

### 已确认事实（证据级）
1. 产品输入=TXT-v4 成立（980/980 SHA）。
2. **旧批测试 APK 绑定不一致重新定性**：apk-sha256.json（17:36）与 installed-test-readback.apk 记录的是 18:32 重建前的旧测试包 SHA（7e342388…）；freeze 现存测试 APK 实算 5c516b4d…，与 config/binding 声明一致。**行为学证明实际执行的是新包**：j1_00 通过了仅新版才有的两处行为（从 /data/local/tmp/j1-accept 读绑定；断言 chapters.size==1"全文"兜底——旧包在这两处必然失败）。P1 应降级为"账本陈旧工件"，不构成"执行错包"，也不推翻 4 个 FAIL 的方向。仍需设备 owner 从设备实测拉回测试包 SHA 彻底闭合。
3. **关键 S 观测缺失机理**：测试观测 JSON 写 getExternalFilesDir/j1-accept，harvest 却按 run-as 内部 files/j1-accept 拉取——内外错配 → harvest 恒空。S 数值、rawProgressJson、恢复目标无法独立复核。
4. **j1_60 FAIL 不是独立产品证据**：其动作 dump（ui_new_scroll）是启动器应用列表、back 后是桌面——滑动与退出作用在桌面上（阅读器已被 j1_40 仪器启动杀掉）；j1_60 读到的 0 是 j1_20 会话写入的传播值。
5. **时间线**：j1_10 动作滚动至末单元（dump 段落编号 132-149）→ back 退出 → j1_10/j1_15 仪器（裸 SQL，无 deleted_at 过滤）读到 locator=S>0（两次，间隔 50s）→ j1_20/30/40 三种重进（暖/暖/强停冷启，均全新进程）后阅读器视口停在 unit 0（dump 段落 0-43）、进度头"TXT · 全文 · 0%"，DB 行在打开后 6-12s 为 locator=0（断言原文"实际=0"=locator_v2.offset 真值 0，非解码失败）。
6. **首页矛盾（本窗口核心新发现）**：j1_20 打开书卡前 9.6s，首页统计已渲染仍显示继续阅读空态"还没开始读"（hasBookBeenRead=false ⇒ percent≤0 且无会话，或行被 deleted_at 过滤）；同刻±4s 测试裸 SQL 读到 locator=S。两读并存 ⇒ **行状态在"首页过滤视图"与"裸 SQL 视图"之间分裂或隐藏**（软删 → 首页/加载器不可见而裸 SQL 可见；或 percent/locator 分裂）。
7. j1_30/j1_40 的失败是 j1_20 写入 0 后的自确认传播（冷启动加载 (0,0) → 目标 0 直通 → 再写 0），非独立事件；独立产品失败事件只有第一次重进（j1_20 会话）。

### 代码推导（文件/行号级，详见工件 2）
- 保存链：滚动防抖（ReaderProgressEffects.kt:308-381）与退出保存（ReaderScreen.kt:476-481 → ReaderProgressActions.kt:201-330）均被 `pendingInitialPosition` 门控（:864-867 / :226）。
- 恢复链：恢复 effect（:452-506）→ `runPlainRestoreLoop`（ReaderScrollPosition.kt:213-258）；**唯一"不滚动即清 pending"的分支是目标≤0 直通（:230）**；目标优先级 pagedAbsOffset≥0 > savedPlainOffset > savedPlainPercent（:38-44），滚动模式 pagedAbsOffset 恒 -1（remember{-1}，写入点仅分页宿主回调——无遮蔽）。
- 加载链：`ReaderDocumentLoader.kt:59 getByBook`（deleted_at IS NULL）→ TXT initialAbsoluteOffset（:239-243/345-350）与 initialProgressPercent（:260）。**getByBook 返回 null ⇒ 两保存值均为 0 ⇒ 恢复直通 0 ⇒ pending=false ⇒ 防抖保存写 (percent=0, locator={offset:0})**（mergeReaderProgress 主键 REPLACE，ReadingEntities.kt:67-95 + ReadingDao.kt:12-14）。
- locator 编解码：写读同 codec、同全书字符空间、unitIndexForOffset 与保存换算同 units 表；j1_10 实证回读。**编解码不是根因。**
- 首页/书架/阅读器同一 bookId（`reader/${book.id}`）与同一进度行。
- pending 写入点穷尽：滚动模式下唯一清除路径=恢复循环返回；保存侧 500ms 防抖读的是采样值，门控语义在"pending 成立"时正确。

### 待设备验证假设（≤3，均有支持/反证/缺失证据，详见工件 3）
- **C1（主）**：重进会话加载读到 null/零行（行被软删或被未识别写入方清零）→ 恢复直通 0 → 0 落库 → 冷启动自确认。静态上这是"视口顶部 + 新鲜 0 行"的唯一产品代码路径；缺口=软删/清零写入方在主源码不可达（无 sync 配置、书未删、三条启动接线不碰 progress、无 SQL 级 UPDATE/DELETE）。
- **C2**：真实 Compose 布局接缝致恢复链未按 JVM 模型运行（FlowTest 自认布局提交未复现）。但"循环挂起→DB 保持 S"与"落位→视口离开顶部"均与证据矛盾，单独成立可能性低，更可能是 C1 的伴随项。
- **C3**：观察过早（恢复 >6-12s 才完成）。反证：小书三会话全 0 且无一例中途值；首页"未读"矛盾独立于读库时点。
- **裁决**：设备 owner 一次"不杀进程"的最小复现 + 三时点行级真值（E1）+ 延迟视口 dump（E2）即可同时裁定 C1/C2/C3 并分叉 C1 的 A1（软删）/A2（零行）。观测清单见[工件 4](<E:/creation-reading-assistant/txt-restore-cause-review-v1/04-device-owner-observations.md>)，不要求重跑全批。

### 测试/产品责任边界
- **产品侧（未被排除的指控）**：首次重进会话把 DB 行替换为 (percent=0, locator=0) 的新鲜行，且 UI 停留顶部 0%——"重进清零"的用户可见缺陷方向成立；但"加载输入为何异常"未闭合，不得据此直接宣称生产根因。
- **测试侧缺陷（不推翻 FAIL 方向，但削弱证据效力）**：①j1_60 旅程无效（作用在启动器）；②harvest 内外目录错配致观测 payload 全失；③apk-sha256.json/readback 陈旧账；④checker 失败集合与 summary 分裂（以 summary+raw 断言为准）；⑤binding_task_id 失败系任务号变更（预期）。
- **既定语义不重开**：pending 门控、"目标≤0 直通"、mergeReaderProgress 覆盖语义、每书一行主键均为既有已验收决策，本报告不重开。

### 确认后最小修复的所有权与回归范围（不写 patch）
- 若 E1 裁定 A1（软删）/A2（零行写入方）：修复落点在**进度行生命周期**（BookRepository/SyncRepository/删除链单一 owner 的 E 候选）+ **加载器对 null/零行时的防 0 覆盖防御**（ReaderProgressEffects/ReaderScrollPosition，恢复目标无效时保持 pending、绝不以 0 直通清 pending 落库）。
- 回归范围：TxtReentryRestoreFlowTest/TxtReentryRestoreTest（JVM，补"行不可见时不得写 0"用例）+ 定向 MuMu 最小复现（seed→滚动退出→重进三时点观测），**不重跑全批**；D 集成/Git 仍按交接入口暂停。
- 若裁定 C3：修的是验收合同（观测时点/等待期定义），产品不动。

## 原始证据
- 断言原文：[j1_20 raw](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/raw/j1_20_reentryFromHome.txt>)、j1_30/j1_40/j1_60 同目录。
- UI dump 链：[raw/j1_ui](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/raw/j1_ui>)（ui_scrolled=132-149；ui_reentry_home/shelf、ui_cold=0%+段落 0-43；ui_new_scroll=启动器）。
- 绑定/账本：[binding.json](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/binding.json>)、[apk-sha256.json](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/freeze/apk-sha256.json>)、[installed-test-readback.apk](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/installed-test-readback.apk>)。
- 分析工件：[01 证据取证](<E:/creation-reading-assistant/txt-restore-cause-review-v1/01-evidence-review.md>)、[02 代码追踪](<E:/creation-reading-assistant/txt-restore-cause-review-v1/02-code-trace.md>)、[03 候选原因](<E:/creation-reading-assistant/txt-restore-cause-review-v1/03-candidates.md>)、[04 设备观测清单](<E:/creation-reading-assistant/txt-restore-cause-review-v1/04-device-owner-observations.md>)。

## 恢复与副作用
本窗口未接触设备，无副作用。上一批恢复缺口（夜间 no→yes、43 文件前后账、原测试 APK 后账）维持 Codex 前序核查定性，待设备 owner 按既有口径处理（E5 可选项）。

## 未覆盖项
设备在跑进程的行级真值、恢复 effect 进程内行为、j1_60 之外三条重进路径的 >30s 延迟观测、MD 邻接与末项 clamp（前序 NOT_PROVEN/NOT_RUN 维持）、实体手机/MIUI。既有跨候选 J2–J5 4/5=80% 范围不变。

## 交付状态与下一步
1/2 交付完成（只读因果分析）。下一动作：设备 owner 按[工件 4](<E:/creation-reading-assistant/txt-restore-cause-review-v1/04-device-owner-observations.md>) 执行 E1-E4 最小观测并回写结论；Root 核定两报告后再派修复任务；D 集成/Git/发布不执行。规划保持"稳定阅读与数据保全"主线：先区分产品状态变化与仪器观测错误，再修根因。
