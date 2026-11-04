# 任务10-v1核查与v2继续

核对：2026-10-10。任务编号/版本：10-v1 `CRA-JOINT-31-ACCEPT-CLOSURE-v1`。交付状态与下一步：材料交付已核，联合验收返修；下一10-v2在同功能内连续诊断/修复/定向验证/最终31与恢复，不另造新编号。无D产品回灌或Git/发布授权。

## 输入指纹、所有权与改动

Root实算996源码等于任务3-v9，tree `750333e3383e4dff6d9605a09744b4cd330a6a13eab263a28d774424d6281b1a`；双APK仍为宿主 `187ee9e7…`、test `4ad918bb…`。未重新构建、未改产品。42验收工件相对Root9只有`driver/j1_ui_callback.py`变化，最终SHA `53210493545714e94e6df3a4fdacaf07a7de23b238bf0bf6641196e67bab2657`。修订包括sqlite3旧版本非-json回退、数值归一化、55s切scroll模式；不据此认定保存根因已修。

Root核313个清单工件SHA与报告SHA，合计314/314存在且匹配；最终源码清单SHA `7daf3bb60b35e1fe2896979c87c82a27916b7d6a82279e9ea0859149398292c7`，验收清单SHA `d2de9d0488cc6e4d87384de490b83ef62e49b118f725fe8b7913caf6e19d12d7`。下一输入完整绑定：[10-v2指纹](<E:/creation-reading-assistant/task10-codex-review-20261010/task10-v2-input-binding.json>)。原v1只读，新owner只写独立`E:/creation-reading-assistant/joint-31-closure-task10-v2`，设备/构建唯一owner。

## 环境、执行与结果

实际应用/模型以v1报告自报Claude Code及两opaque可见ID登记，不将推荐DeepSeek当作实测身份。隔离MuMu，非实体手机；Root未重新启动设备/构建/重跑充分JVM。

最终`run_t10_joint31_r9`的原始summary/items/stdout交叉成立：

| 子合同 | PASS | FAIL | NOT_RUN | 分母 |
|---|---|---|---|---|
| J1_NAV | 4 | 0 | 0 | 4 |
| TXT_ORDINARY | 6 | 1 | 5 | 12 |
| TXT_CLAMP | 0 | 0 | 8 | 8 |
| D2_RELINK | 0 | 0 | 7 | 7 |
| 合计 | 10 | 1 | 20 | 31 |

10个PASS均有原始`OK (1 test)`及item exit0。失败55s item为FAIL/exit1、原始JUnit失败，整批overall=FAIL；外部启动wrapper的exit0不代表通过。113离线用例与4验证器日志成功仅为离线范围，不能覆盖设备失败。

## 首异常、根因边界与独立审查

55s真实marker由0000–0087变为0044–0087，存活前台仍是隔离宿主；独立SQL读行offset=4193、updated_at与S/R一致，约12秒15次观测未出现新S2。驱动因此正确拒绝退出；仪器首断言是缺`handshake_exit.json`，不是可通过伪造Exit文件消除的根因。

独立只读审查确认：不能将报告“ADB手势不触发就地保存”作为已证根因。两份辅助probe无有效对照：save全20次rows为空、未验证真实打开；with_fixture前置三方法均FAIL/缺binding。静态config、SQL路径指向正确隔离包，SQL列顺序/类型归一化未见本次错误，不能反向证明实际保存链完整。

下一诊断应观察真实LazyList index/scrollOffset/canScrollForward、pending恢复门控、debounce/守卫、SaveProgress派发与落盘，结合实际SQL目标/UID后决定修输入、门控或产品回调接缝。保持S/R/S2完整行/updated_at、阅读器活着先S2再退出、marker/位移与pending合同，不用一次yield/人工采样顺序签时序，不改为退出后双采样。

## 原始证据、恢复与副作用

- [外部原报告](<E:/creation-reading-assistant/joint-31-closure-task10-v1/REPORT.md>)及其r1–r9失败轮次全部保留。
- [Root机械核对](<E:/creation-reading-assistant/task10-codex-review-20261010/verification.json>)；[独审](<E:/creation-reading-assistant/task10-codex-review-20261010/independent-review.txt>)。
- [取证门核对](<E:/creation-reading-assistant/task10-codex-review-20261010/private-db-gate-check.json>)：最终r9新测试包卸载前DB/WAL/SHM原件、暂存、本地三方size/SHA实核3/3，清理成功，旧直pull拒绝已由Root9真实门禁改善。新包对卸载有exit0原始记录。
- owner独立恢复后账`restore-verify-final2.json`为71文件size/SHA/ns-mtime、原APK对、8设置、映射、包/data状态与tmp顶层核同，problems为空；Root核其脚本/结果并实算本地保护DB/WAL/SHM备份3/3，当前MuMu进程/Android均关闭。Root未重开设备复跑全部71项，恢复结论据此限定。
- 本轮保护qahost的WAL/SHM曾消失，owner已按原备份还原字节/mtime，后账成立；“SQLite checkpoint”触发来源未定位。所查工具和日志未出现SQL打开保护原库，不能归因于这些脚本，也不能推为隔离S2读错库。下一轮需目标身份拒绝、命令审计及前后保护文件核对，追查误选/工具动作/原包后台生命周期，禁止为诊断直接打开保护原库或删sidecar绕过。

## 未覆盖项与下一步

31联合未通过；X整机中断、Back专属完成归因、全部pending保存排空、初始新save、实体手机/MIUI仍NOT_COVERED，TXT_CLAMP/D2本批NOT_RUN。全产品分母未核，不报全产品完成率。

继续10-v2推荐当前可用DeepSeek，仍单设备owner。先定向完成真实依赖前置与55s诊断/窄修/冷读，再最终完整31一次；同输入同首异常未新增证据不得再盲跑整批。只有产品/仪器改变才针对性构建/测试；不变输入复用已核双APK和100/64 JVM。任务1—9不重派。下一完整提示词见[10-v2](2026-10-10-task10-v2-closure-prompt.md)，用户只复制即可，不负责中间技术操作。
