# 任务3-v9、任务9-v1与Root补正核查

核对：2026-10-10。任务编号/版本：3 `CRA-J1-FUNCTION-CLOSURE-v9`；9 `CRA-PRIVATE-DB-PREUNINSTALL-EVIDENCE-FIX-v1`，Root独立E补正v2。交付状态与下一步：3限定J1合同通过，9外部成果经Root补正及独审后限定离线通过；下一任务10由唯一owner完成31项同版本联合MuMu验收与恢复。尚未D产品回灌/暂存/提交/push/PR/发布。

## 输入指纹

- 3最终996源码均实算匹配；源码清单SHA `c6b4848012bbc4a92db7d77a651c0e41200576768e81190c23601989035c7669`，tree `750333e3383e4dff6d9605a09744b4cd330a6a13eab263a28d774424d6281b1a`。相对Root已准备基树仅两仪器测试变化；产品字节未变。
- 3宿主APK SHA `187ee9e7a43d08560c0af0372fc06c560f3c30d8779e5138e58546246c11dd57`；测试APK SHA `4ad918bb4a02591e8a96b781d8786948fa83c2b05407fe704e4b79672adce6f1`。源码/双APK/实际运行配置已绑定。
- 9外部42工件均实算匹配，但清理/覆盖守卫缺口使原交付不能整体直接采纳。Root候选最终42工件清单SHA `221e4a1f0eebb4d87e73e50d63dba5e11185a9cc901c4937c575a3c885929596`；driver SHA `538925802d1a816e1f740bfb226005ed2822dc8291b21e07b2c3bc085ea46684`。新输入绑定见[task10-input-binding.json](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/task10-input-binding.json>)。

## 所有权与改动

3仅拥有独立E的J1流程；Root早先已补ReaderActions漏转发回调，v9仅修夹具和准确测试合同，未以测试放宽掩盖失败。原v8及中间失败证据保留。

9外部实现su暂存取证及三方size/SHA核对。Root发现同run/pkg/db路径会覆盖已有资产、清理失败仍返回成功、cp失败不清部分文件、包名在ADB调用后才校验。Root在新E候选补原子独占目录、所有权范围清理、严格命令退出/存在性回读、清理失败拒绝及包名先验；外部目录原件保持。只改驱动、必要离线模拟与测试，不改Android产品。

## 环境与执行与结果

3隔离MuMu；实际执行应用/模型登记不完整，不把推荐模型视为实测模型。9外部实际模型以原报告opaque ID为准，与推荐登记分别保存。Root补正离线使用低成本worker，独立只读审查与Root核对，未操作设备、未运行Gradle。

- J1既定4方法原始结果4/4成立，均exit0与`OK (1 test)`；4为纯route逻辑，其余3有native证据。journey01真实临时B、A完整12列冻结、返回A成立；返回后位置3740→4060，updated_at递增，独立页区间`3740 ≤ 4014 < 4060`。
- journey02真实两个定位落点与互斥marker成立；查询坐标依生产规则先推导再和UI相等比较。journey03仅“正常退出导航与已有位置观察”成立，不能充当新位置保存/冷读或Back完成归因。
- 3最终10类XML共100 JVM，0失败/错误/跳过，原始日志BUILD SUCCESSFUL/exit0。未重跑输入不变的任务8搜索/TTS64 JVM。APK构建原exit0及真实SHA成立。
- 9外部101离线单测与后续完整overlay门禁证据采纳；初始缺Android overlay的三个失败记录保留，以后续完整overlay原始成功证据为准。Root补正29定向、29恢复、8旧v4，共66离线用例0失败/错误。Root另外对实际外部task9-v1原驱动跑12新增保护用例，6失败/0错误/0跳过，证明旧实现确实漏保护；历史更早v6旧驱动红29只是原合同保留，不作为全部新缺口的专属复现。详见[实际外部红例](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/red-external9-guard.json>)、[最终SHA/日志核对](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/root9-verification.json>)及原始日志/退出记录。独审未发现阻断问题。

## 原始证据与恢复与副作用

- [Root机械核对](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/verification.json>)：996源码、双APK、100 XML及外部42工件；raw字段`instrumentation_success=false`只表示裁剪后的JUnit stdout未含INSTRUMENTATION_CODE行，不是方法失败。方法exit0/OK由items与原始文本交叉核对。
- [3最终报告](<E:/creation-reading-assistant/joint-j1-closure-task3-v9/REPORT.md>)及`runs/run_v9_j1_final`：4项PASS但summary.overall=FAIL，原因是旧driver私有DB直pull被拒。保留FAIL/refused原件，不能把后补恢复冒充旧驱动自动通过。
- 3owner在任何新测试包卸载前补su暂存取证；Root本地DB/WAL/SHM与远端登记size/SHA独立核同3/3。owner设备后账71文件size/ns-mtime/SHA、原APK对、8设置、映射、shell、新包对及正式包absence成立；Root本轮只当前核MuMu关闭，未重开设备重复全部71项。恢复证据范围据此区分。详见[恢复核对](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/db-recovery-check.json>)与[root当前电源](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/root-power-current.json>)。
- 3证据清单65项中64实算匹配，唯一REPORT.md因后写入导致旧清单SHA不一致；报告内容不当作冻结运行证据。native沿用v6任务标签，方法/运行窗口/包与实际APK绑定成立。两处元数据不足明确保留，不为其重跑设备/构建。
- [9原交付](<E:/creation-reading-assistant/private-db-evidence-task9-v1/REPORT.md>)；[Root最终补正](<E:/creation-reading-assistant/private-db-evidence-task9-root-v2-20261010/ROOT_REPORT.md>)；[独立审查](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/root9-independent-review.txt>)。

## 未覆盖项与阶段复盘

31项联合仍NOT_RUN：4 J1_NAV、12 TXT_ORDINARY、8 TXT_CLAMP、7 D2_RELINK；本轮4/4限定J1结果不等于31/31。真实修复已使“查阅别书后返回继续读”成立，下一目标是同版本确认TXT重开/短末项恢复和文件重关联均稳定。

保留完整流程单owner安排：任务3、9退出当前队列，不重派；10自主完成输入合流、配置绑定、必要窄修、联合测试及恢复。沿用最终双APK及充分未变证据；出现新源码才针对受影响合同重新构建/验证。无独立新任务可安全加速该设备批次，不制造平行准备/报告任务。仍未覆盖X整机中断、Back专属完成归因、全部pending保存排空、初始新save、实体手机/MIUI及正式签名发布；全产品分母未核，无法给全产品完成率。

当前交接与连续编号已替换过时现态；完整编辑前入口保存在[快照](<E:/creation-reading-assistant/tasks-3-9-codex-review-20261010/docs-before/current.md>)，历史报告不改。下一外部提示词只准备、不自动发送；用户复制启动后10取得本轮设备/候选所有权。
