# Android 恢复阶段 D 限定退出复核（2026-09-29）

## 判定与整体进度

**阶段 D 按用户授权的 MuMu 模拟器验收范围限定退出。A–E 已退出 A/B/C/D，整体阶段进度为 4/5=80%。** 这不是实体手机/MIUI、正式书库、Release 或发布验收。E 的版本与健康入口整理尚未执行；其提交、目录切换和清理按[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) §E 仍需用户明确授权。原仓库保持原状。

## 同输入门禁

| 门禁 | 原件及本轮独立核对 | 结论 |
|---|---|---|
| 冻结输入、全量 JVM、Lint、APK | [D 首轮报告](../../../qa/2026-09/2026-09-28-stage-d-build-and-mumu-gates.md)：939 个 Android 构建输入哈希；JVM 333 套件 3208/0/0/0、Lint 0 error/9 warning；Debug、qahost 主 APK 与 androidTest APK 构建；原始日志、XML、APK 哈希落 `E:\creation-reading-assistant\codex-d-stage-gates-20260928\`。V4 EPUB JVM XML 本轮 21/0/0/0，SHA `9062c0d8bb58fa6bc7297f6891a28312beac1354bdb30c95d409d911bb38b7c4`。 | 本候选构建范围 PASS；警告按报告逐类登记，不写零警告。 |
| V5 四类 Compose UI | 首轮 60/62，测试守卫反向；窄修后 [复跑报告](../../../qa/2026-09/2026-09-28-stage-d-v5-rerun.md) 的定向 XML 7/0/0/0 SHA `6076c2f5831710fbdb3eafd06888ee2620efc7d41a85de092bf89c9069c5b165`、全量 XML 62/0/0/0 SHA `8d7443a5ae0bc884518f60e16e17ca256c93468412293d469ba0a61cfd12a897` 已由 Codex 实读。测试源码 SHA `cfff5c5c8f85062e205f3ad6c32558d9f600b1f551e2dddfc87bb59ceab9110b`。 | MuMu qahost 420dpi 62/62 PASS；四类为 Compose/隔离夹具，不等于生产 MainActivity 全链。 |
| S03-1 并发 XML 刷新 | 同一 G1 全量 JVM 中 `SearchIndexRepositoryRebuildConcurrencyTest` XML 实读 19/0/0/0、时间戳 2026-09-28T14:49:24Z，SHA `04217278c21ebdbb4039af74b5d7b97502061e3fa16f4c76262de3b25524396e`。 | D10 证据刷新已做；真实 Room 自然路径沿用既有 PASS WITH LIMITATIONS，未新增专用设备隔离夹具。 |
| S04 EPUB 隔离设备路径 | Codex 在当前 qahost 安装的主 APK `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`、androidTest APK `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d` 下定向运行 `EpubReplaceRoomIsolatedTest`，`am instrument` **OK (8 tests)**、ADB EXIT 0；原始日志 `E:\creation-reading-assistant\codex-d-s05-phone-20260929\v4-epub-room-instrument.log` SHA `b3f87ec495890ca51c8817693a1520c0469765b86bf78098b9f7f2049794d4a9`。测试使用 in-memory Room 和专属文件根；运行后 qahost 主 DB 与 49 个既有书/设置文件哈希仍一致。 | 隔离 Room/文件替换路径 PASS；不代表真实生产 DB、低存储、跨卷 rename 或整包原子性。 |

## MuMu 手机 UI 与数据恢复

- [S05 三项报告](../../../qa/2026-09/2026-09-29-stage-d-s05-three-ui-acceptance.md)的 A（滑块**跳至**书末后自动保存完成）和 B（首页完成卡 8 张分段取证）可回读。C 的显式标记/取消后自愈有宽屏证据，但其“全程 420dpi 手机布局”不成立；[先前复核](2026-09-29-stage-d-s05-e-preflight-review.md)已纠正。
- 为只补 C 手机布局，Codex 在 `emulator-5556` 的隔离 qahost 做新冷备，宿主/设备 tar SHA 同为 `255ca18e0271801767ed10c183b4dd2bf982f34c43aa7542f62724ac128e0eec`。设置 `wm density 420` 后，用已有生产导入器仪器测试 `OK (1 test)` 播种测试 TXT，并**仅对测试书**直接写入受控夹具 `100.0/reading/completed_at NULL/locator_v2 offset 1103064`；随后补正文文件，其 SHA `80f3cb305d32034fd812e2bbcf132e50b46b58a55c64daa284988f893bfbd377`。直接 SQL 是夹具准备，不应误称本轮重新执行了 UI 标记/取消动作；该动作已有上一轮宽屏证据。
- `E:\creation-reading-assistant\codex-d-s05-phone-20260929\` 的原始 DB 与截图/XML 显示：打开前 DB **100.0/reading/offset 1103064**；**重开、尚未滑动时**阅读页显示 **47.4%**，DB 仍是 **100.0**、`updated_at` 未变；轻滑后 DB **47.4224/reading/offset 1102946**，`completed_at` 仍空、`updated_at` 变化；返回书架卡显示「已读 47%」。这证明手机布局下**移动触发保存后**自愈，且纠正上一报告“重开即写库”的无原件说法。证据含 `before-reopen-db.txt`、`after-reopen-before-swipe-db.txt`、`after-swipe-db.txt`、`after-exit-final-db.txt`、对应截图/XML 与测试期 `wm density` 回读；E 盘索引 SHA `6052ad8f60a2582b83f96d51d8b0e1326793a46e008fb411b264a434b6e53ea5`。
- 冷备回写后 qahost 主 DB SHA `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` 与事前一致，行数 33/16，测试书与正文目录 0 残留；36 个既有书文件、6 个 datastore、7 个 shared_prefs 共 **49/49** 与事前哈希一致，原件 `after-restore-49-hashes.txt`。V4 隔离设备测试后又核 49/49 和主 DB。设备已回 `1080x1920/280dpi`、旋转 1/0、night yes、stay-on 0、screen timeout 60000；测试中转文件已清理。主 APK/测试 APK 设备 SHA 与 D 门禁产物一致。
- S06 主题 D2 手机尺寸补一屏：`theme-phone-dark-loaded.png` 与 XML 显示 420dpi 窄屏底栏、系统夜间 yes 下已加载首页；先拍到的 `theme-phone-dark.png` 只是启动页，**不作为通过证据**。双向主题切换沿用同输入的 C 阶段 MuMu 平板结果，手机尺寸仅补静态布局，不声称手机上双向切换已验。

## 已知边界与延期项

- D1 全局夜间模式一次跨会话翻转未隔离归因，本批前后均 yes、未复现；D2 其余同帧/拖动/像素细节是验证缺口而非确证故障。D3（S09 五项接线）及 D4–D6、D8、D11 为产品合同、候选增强或依赖未齐，按[C 剩余项对账](2026-09-28-stage-c-remaining-work-audit.md)延期，不以全量测试通过冒充功能已实现。
- D7 所列 duration/slider 相关仪器用例已在 V5 四类 62 条中实跑，F-2 卡片/详情取数口径仍是独立产品判断；D9 根目录 dump XML 与桌面资产归 E 的隐私/资产治理，不自动删除；D10 已补 XML；D12 维持当前「显式标记抬至 100、取消后保留残余、再次实际位移时自愈」语义，是否改为取消瞬间对齐是后续产品选择，不是本轮已验证缺陷。
- S14 真实进程 kill、整包 DB+书籍文件原子性、S12 既有 33 书逐列对账仍**未验**；MuMu 不代表实体手机/MIUI。发布签名、Release、正式书库和桌面产品线不在 D 限定退出内。

## E 阶段起点

[E 预对账](../../legacy-stages/2026-09/2026-09-29-stage-e-entry-inventory.md)与[深夜复核](2026-09-29-stage-d-s05-e-preflight-review.md)已保存可校验文件/历史差异、原仓库缺失对象诊断和 E 盘备份。原仓库从 HEAD 历史追到 425 个缺失对象，健康恢复仓库应作为后续集成候选基础；指定代码区原目录独有 3 个 Electron 文件已另存，不能丢失。**本阶段未 stage、commit、push、reset、checkout、删改原仓库或换目录。** E 的下一步是展示候选 hunk、依赖顺序和遗留资产去向，获得计划 §E 所要求的用户授权后才执行不可逆版本整理。
