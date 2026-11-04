# G1-fix-accept-v3：Codex 接手、MuMu 限定书库验收与恢复

日期：2026-10-02。结论：**本任务限定验收通过**，四条旅程 + 恢复检查 5/5；不是完整 D2、全产品、实体手机或发布验收。用户本轮“已经暂停了那个任务，你接手吧”授权 Codex 独占接手原 G1；不沿用旧提交/自动接力许可。

任务编号/版本：`G1-fix-accept-v3`；执行者：Codex。输入指纹：HEAD、父候选与最终源码/APK SHA 见下；暂停时原件保全在 E，不复用旧提交权限。

## 所有权与改动

- 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`；结束仍相同、暂存区空。
- 候选：[G1-v3 android](<E:/creation-reading-assistant/next-round-g1-fix-accept-v3/android>)，父候选 G1-build-v2。
- 唯一实现写入：候选的 `app/src/androidTest/java/com/creationreadingassistant/feature/library/LibraryLifecycleAcceptanceTest.kt`；产品、runner、构建配置未修改，没有复制到 D 仓库。另写 E 验收工具/工件和本报告/交接现态。
- [接手前副本](<E:/creation-reading-assistant/g1-codex-takeover-20261002/before>)包含暂停时测试源码、freeze APK/基线/证据及原运行日志；历史失败保留。[差异](<E:/creation-reading-assistant/g1-codex-takeover-20261002/test-correction.patch>)。
- 上轮使用不存在的 `assembleQahostDebugAndroidTest`。改用 `:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --max-workers=1`；两次构建分别 25 秒/11 秒成功。依赖复用既存 E 盘 gradle-home；所有新构建产物在 E。
- 上轮把“无 StatusRail 调用”误推为“没有状态筛选 UI”，改成 ALL+TXT，缩小了协议。实际路径：书架标题下拉“打开书架整理”→阅读状态→在读。测试恢复 **READING+txt** 的真实 UI 选择；五书夹具包含 1 本在读 TXT、3 本非在读 TXT、1 本在读 EPUB，同时核两个筛选条件。
- 结果 JSON 增加实际进程 PID，阶段内截屏，避免 Activity recreate 冒充进程冷启动。
- [源码核对](<E:/creation-reading-assistant/g1-codex-takeover-20261002/source-audit.json>)检查 856 个 kt/kts/xml/json/pro/properties 文件（排除 build/.gradle/.kotlin/local.properties），855 字节一致，只该仪器类不同。此分母是本次扩展名集合，不冒充旧 876 全资产清单。

## 最终 APK 合同

| 工件 | SHA-256 |
|---|---|
| 复用父宿主 `.qahostlibrary` | `4bf670ab4006c6d60a7e18d8d31d0856d55755d6b8ff6994b3f41edc3b272531` |
| [最终测试 APK](<E:/creation-reading-assistant/g1-codex-takeover-20261002/final-test.apk>) | `23a35d8ed94af3e3c7ae7d93cd7a2746a1db2937f4c178044d9ada83dde66c51` |
| 最终仪器类 | `d59289b5ddf5db293b9216513a48002841369f985795fd03c299b228e9afc93c` |

测试包 `com.creationreadingassistant.qahostlibrary.test`，target `com.creationreadingassistant.qahostlibrary`，runner `com.creationreadingassistant.testing.CraIsolatedTestRunner`。实际安装后 pull 回 APK 与冻结 SHA 一致。[APK 后账](<E:/creation-reading-assistant/g1-codex-takeover-20261002/installed/apk-sha256.json>)、[签名](<E:/creation-reading-assistant/g1-codex-takeover-20261002/final-test-signing.txt>)：debug 证书 SHA `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。

旧 freeze 内 `sha256.txt` 不随上轮重建更新，已保全且视为旧记录；最终合同以本报告/E 接手证据为准。

## 环境

设备为 MuMu：ADB localhost 16416 对应本机 MuMuVMMHeadless，guest 存在 nemu 服务。厂商/型号伪装为 Samsung 不代表实体手机。屏幕 **1080×1920、density 280，实际竖屏**；布局使用宽屏导航栏，未声称覆盖窄手机布局。

## 执行与结果

所有下表用例在最终同一 APK 对执行，原始结果为 `OK (1 test)`。种子是独立新 UID 的中性夹具，未访问正式书库。

| 检查 | 实际操作/证据 | 结论 |
|---|---|---|
| 夹具准备 | seedLifecycleFixture；真实创建夹具正文/行 | 准备成功，不算产品通过 |
| 搁置 | 首页继续阅读右箭头→该书“操作”→搁置本书；assertAfterShelve 纯读状态 shelved、正文指针及文件仍在 | PASS |
| 移除正文 | 书架更多→批量选择→全选唯一夹具→清缓存；assertAfterRemoveContent 纯读指针清空、missing、文件实际缺失，进度/笔记保留 | PASS |
| 删除/撤销 | deleteUndoChainRealUi 同一仪器调用，真实长按/确认删除/撤销；原始 SQL 纯读软删，夹具新笔记明确写入，撤销后原行/关联恢复，新笔记不被覆盖，预删标签不复活 | PASS；158ms < 12000ms |
| 动态视图 | savedFilterUiChain 真 UI 选在读+TXT、保存、读回；Activity recreate 标记为 Activity 重建 | PASS；PID 14674 |
| 冷启动 | force-stop 沙箱后另一次仪器启动 l1ColdStartReapply；无 seed、无重保存；先读既有 READING+txt，再 UI 应用，5 本中仅在读 TXT 可见 | PASS；PID 14878 |

最终六次仪器调用（1准备+5验证）6/6 成功；限定旅程为搁置、移除、删除撤销、动态视图保存/冷启动四项。

## 原始证据

- 原始日志：[搁置](<E:/creation-reading-assistant/g1-codex-takeover-20261002/run-assertAfterShelve.txt>)、[移除](<E:/creation-reading-assistant/g1-codex-takeover-20261002/run-assertAfterRemoveContent.txt>)、[删除撤销](<E:/creation-reading-assistant/g1-codex-takeover-20261002/run-deleteUndoChainRealUi.txt>)、[保存](<E:/creation-reading-assistant/g1-codex-takeover-20261002/run-savedFilterUiChain.txt>)、[冷启动](<E:/creation-reading-assistant/g1-codex-takeover-20261002/run-l1ColdStartReapply.txt>)。
- [生命周期 JSON](<E:/creation-reading-assistant/g1-codex-takeover-20261002/lifecycle-final>)、[删除撤销 JSON](<E:/creation-reading-assistant/g1-codex-takeover-20261002/delete-undo-final>)、[保存 PID/条件](<E:/creation-reading-assistant/g1-codex-takeover-20261002/saved-final/l1-saved.json>)、[冷启动 PID/条件](<E:/creation-reading-assistant/g1-codex-takeover-20261002/cold-final/l1-cold-start-readback.json>)。
- [冷启动套用截图](<E:/creation-reading-assistant/g1-codex-takeover-20261002/cold-final/l1-cold-start-reapply.png>)经 Codex 目视：竖屏、2项筛选、共5本显示1本。删除撤销阶段截图仍可能处于异步 UI 未重绘的空架/撤销条，**不作为 UI 已重新显示书卡的证据**；本项恢复结论来自同会话 DB 断言。书卡视觉重绘未单独设验收点。
- 一次探索性首页书卡长按实际进入阅读器，已返回；仅发生于可删除沙箱夹具，不计搁置动作。真实搁置/移除步骤的截图/XML在 E `before-shelve`、`book-actions`、`before-removecontent` 等工件中保留。

## 恢复与副作用

- [后账](<E:/creation-reading-assistant/g1-codex-takeover-20261002/restoration-audit.json>)：原 G1 测前记录 46/46 一致（含 DB/WAL/SHM 和 files）；Codex 接手补录 databases/files/shared_prefs/no_backup **65/65 字节哈希与路径集合一致**，无新增/缺失。
- 原 qahost 宿主 SHA `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`、测试 SHA `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d` 前后完全相同；无需重新安装或覆盖原数据。
- [设置前账](<E:/creation-reading-assistant/g1-codex-takeover-20261002/takeover-baseline/settings.txt>)与[后账](<E:/creation-reading-assistant/g1-codex-takeover-20261002/restored/settings.txt>)逐字一致：size/density、旋转、stay_on、飞行模式、休眠及动画设置；forward/reverse 清单同样一致。与原记录 1080×1920/280 及七设置一致。
- 两个本批新增 `.qahostlibrary` APK 已各自 uninstall 成功，私有目录确认不存在；新 UID 数据/KeyStore 随卸载清理，不保留测试状态。临时 UI XML 已清除。没有清除正式应用数据或原 qahost。
- [恢复 APK 对](<E:/creation-reading-assistant/g1-codex-takeover-20261002/restored/apk-sha256.json>)。本次未做 qahost 启动、安装或数据库写入。

## 交付状态与下一步

G1-fix-accept-v3 限定 5/5=100% 退出，MuMu所有权释放；本批 N1/G1 完整验收 **1/2=50%**。N1-v6已产生报告，属于已交付待独立核查；本轮未审其新代码，不能把61绿声明当验收，也不自动集成/提交。

## 未覆盖项

保留：D2移除后的重新关联尚未成立（Duplicate不等于重新关联）；正式手机/MIUI、窄手机布局、F发布、整包DB+文件原子性及既有跨端接缝。当前动态视图已实现，无需再为测试另造状态筛选功能。下一关键路径只剩 N1-v6语义核查→成立后精确合流/构建/MuMu，减少重复G1与旧迁移/W2重跑；产品方向最终共享理解未答，不据草案扩新功能。全产品分母尚未核定，不能给可靠百分比。

未执行 Git 暂存/提交/push/reset/checkout；没有动原损坏仓库、桌面/HOLD或 Claude 独立库。本轮停止于 G1 报告与现态同步。
