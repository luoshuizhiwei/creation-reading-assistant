# TXT 重进恢复 v4 窄返修：末单元身份 + effect key 显式接入

- 任务编号/版本：`CRA-TXT-REENTRY-FIX-v4`（TXT-v3 窄返修）；2026-10-05 指定，实际执行 2026-10-05T15:47–16:40+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。未操作设备/ADB。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。只读输入 = [v3 候选](<E:/creation-reading-assistant/txt-reentry-fix-v3/android>) 及其 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/source-sha256.json>)（980 项复制后逐文件实算 980/980 匹配，零漂移）。同编号任务占用核查：`txt-reentry-fix-v4` 此前不存在。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-reentry-fix-v4\`（android 副本、payload、freeze、test-results、scripts、execution.json）与本唯一报告（唯一 D 写入）。生产所有权仍限 ReaderScrollPosition.kt、ReaderProgressEffects.kt 及 TXT 恢复测试；D 产品源码/旧候选/公共配置/EPUB/Markdown/其他窗口产物/全局交接零改动；不 commit/push/reset/checkout；不触模拟器/正式书库；Gradle home 3.1G 本任务私有。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；仅定向 JVM，无联合 APK 构建。

## 执行与结果

### 修复 A：plainRestoreSettled 末单元身份（Codex P1 反例）

v3 的 clamp 分支只检查 `target > first && lastVisible == target`，缺末单元身份参数——40 单元下 `first=36/lastVisible=37/target=37`（目标只是恰好贴视口底边的中间项）被误判为已落位，生产随即释放 pending，`currentPlainListOffset` 从首项 36 计算出错误位置。v4：新增 `isLastUnit` 参数（生产调用点 = `target.itemIndex == units.lastIndex`），clamp 分支**仅当目标就是最后一个阅读单元**才允许；正常分支（首项==目标）不变。反例（36/37/37 + isLastUnit=false）现在拒绝；合法末项 clamp（38/39/39 + isLastUnit=true）仍放行（[vs-978 补丁](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/vs-978_ReaderScrollPosition.kt.patch>)）。

### 修复 B：effect key 显式接入书籍/列表身份（Codex P2）

恢复 LaunchedEffect 的 key 集合显式加入 `bid`（书籍身份）与 `plainListState`（列表身份）——两者本就是 `ReaderProgressEffects` 的函数参数（L85/L95），此前只是不在 key 集合内；切书或列表状态对象重建时 effect 以新 key 取消旧恢复协程。保留模式/目标等既有 keys。注释从"书籍/列表身份失效即取消"（隐式、未接入）改为"已显式列入 key 集合"（[vs-978 补丁](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/vs-978_ReaderProgressEffects.kt.patch>)）。生产 `runPlainRestoreLoop` 实现零改动（保留 v3 的同状态等待、书尾 clamp 语义经 isLastUnit 约束、pending 保存门控）。

### 证明边界（如实声明）

- **测试能证明**：settled 的中间项反例（v3 误判场景现已拒绝）与末项 clamp 放行直接调用生产实现；wiring-guard 测试钉住"恢复 effect 的 key 块源码包含 bid 与 plainListState"这一接入事实。
- **测试不能证明**："key 失效真的取消并重启 effect"是 Compose 运行时行为，JVM 无法驱动真实重组——wiring-guard 只钉住接入事实，实际取消/重启行为留给 MuMu 设备回归。

### 验证（[green.log](<E:/creation-reading-assistant/txt-reentry-fix-v4/test-results/green.log>) EXIT=0）

定向 7 套件 **37 tests / 0 failures / 0 errors / 0 skipped**：v1/v2/v3 全部既有回归保留（TxtReentryRestoreTest 7、ReaderScrollPositionTest 5、ReaderProgressActionsPersistTest 5、TxtScrollChapterTargetTest 7、S12-① 3、MD visible 2）+ FlowTest 8（原 7 项迁移动态回归 + 新增"settled v4 反例"与"wiring guard"）。

### 冻结交付

- 补丁：相对 978 基座 [vs-978_*.patch](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/vs-978_ReaderScrollPosition.kt.patch>) ×4（最终应用基准）+ 相对 v3 的精确变更清单 [vs-v3_*.patch](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/vs-v3_ReaderScrollPosition.kt.patch>) ×4（A=45L、B=32L、FlowTest=76L、RestoreTest=0L 原样）。
- 完整源码指纹 [source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/source-sha256.json>)（980 项）；[payload-manifest.json](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/payload-manifest.json>)（base978/v3/v4 三方 SHA）；`payload/android/`；原始 XML×7 + 日志 + 退出码在 `freeze/evidence/`。

## 恢复与副作用

D 仓库/Git/v3 原件/输入目录只读未动（收工 HEAD/暂存区复核一致）；无设备操作；测试数据仅在本任务 E 目录；候选树构建产物已清理。开发中一处测试正则字面量跨行语法错误当轮发现当轮修复，最终 37/37 为最终代码状态结果。

## 未覆盖项

- **MuMu 未运行**：源码/JVM 通过不等于设备恢复通过——"切书真的取消旧恢复"、"重进恢复到原偏移所在单元（含书尾 clamp 书）+ DB 不再被清零"须由唯一设备 owner 冻结新 APK 做 J1 回归与受影响邻接验收（J1 负向谓词按既定口径绑定阅读单元 + 范围 + UI 可见标记）。
- wiring-guard 是源码级钉子，非 Compose 行为测试；`settleSnapshot` visibleItemsInfo 空列表兜底未经设备验证。
- 历史跨候选 J2–J5 证据 4/5=80% 仅复用，不是本次新跑；全产品分母未核。

## 交付状态与下一步

v4 payload（4 文件，生产 2 + 测试 2）+ 980 项源码冻结 + 37/37 绿交付完毕。本轮到此停止：不回灌 D、不提交、不冻结 APK。交回 Codex 核查；通过后由唯一设备 owner 绑定 prep-v2 工件构建/运行 MuMu J1 及受影响邻接验收（J5 不重复派发）。
