# 47-v2生产正向反例核查与47-v3接续

## 任务编号/版本
2026-10-15 12:39 香港时间。47-v2交付后Root直接核查，无子代理；当前仍外部执行、Android-only。47-v2并未完整完成，不放行设备。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空；既有WIP保护。45-v1共同源码/双APK/freeze继续只读复用；47-v2现存工具清单独立verify checked26、零漂移。

## 所有权与改动
47-v2自动构造runner、缺失清单拦截、串入try/finally等实际增量采纳。47-v3唯一写独立E主机执行链/必要本地依赖副本/测试，原产品与原交付只读；Root只维护报告/交接/索引/规划及TODO。

## 环境与验证
实际22 passed,9 deselected in44.12s、exit0。9项CLI组未重跑，因其生产探活可能调用真实ADB，不能拿拒绝CLI代替正向生产覆盖。22项内一项原测试构造不存在serial并执行get-state探活；部分缺参原测试也可能探活，故不声明本轮零ADB。没有对已核在线设备执行安装/卸载/仪器/设置写入，没有启动模拟器或Gradle。Root新反例全部截获最终命令边界至离线backend，结果不属DEVICE证据。

## 执行与结果
1. 生产正向仍不通：给非注入生产适配器的get-state边界响应device，其余由离线状态机响应，运行真实生产编排。主入口通过探活后停produce_admission_schema，admission=False、level=DEVICE；38-v5 producer仍device_ready=False并用expect_real=False自校验，必然拒绝DEVICE。36-v4 cmd_invoke仍expect_real=False，后续仍有同类接缝；不能只改外层模式或只验证离线注入正向。
2. 清单完整性未修：默认现存26项verify确实通过，缺文件拦截也采纳。但Root在自有E构造仅保留host/tool_freeze.py、删全部external_refs并按格式更新digest的清单，verify返回ok=True、checked1、零问题。必须绑定必要项集合及引用来源，而不只是核清单自称的条目。
3. 恢复账本仍为空：creation_ledger仅初始化/传入，没有append/update。mutation_attempted在本地hv.mkdir前就置true。Root把自有harvest路径设置为普通文件，目录创建失败、forensics_ok=false、未做备份，仍进入恢复并记restored=true；只证明多余恢复发生，不据此宣称设备已被破坏。需要按实际设备修改区分保护暂存/目标变化/恢复状态，填充真实账本，不用本地mkdir作为设备变更标记。
4. owner所谓白名单实为cra-/task47-/future-device-前缀和arbitrary/unregistered/unknown/fake子串黑名单。Root生产反例用cra-no-task-permission仍可通过这层。不能将命名规则称独立任务许可；无需扩建权限系统，绑定后续明确任务上下文与现场范围即可。该问题保持，不为其单开新任务。
5. 测试中的双故障用例没有让恢复失败，最后仍断言restored=True，不能证明两个失败同时保留；47-v3用实际外部命令失败或准确内部故障点补真正反例。

## 原始证据
[22原测试](<E:/creation-reading-assistant/task47v2-codex-review-20261015/offline-tests.log>)；[生产正向反例与完整边界命令](<E:/creation-reading-assistant/task47v2-codex-review-20261015/production-positive-counterexample.json>)；[缩减清单](<E:/creation-reading-assistant/task47v2-codex-review-20261015/omitted-dependencies-manifest.json>)；[缩减清单错误通过](<E:/creation-reading-assistant/task47v2-codex-review-20261015/omitted-dependencies-result.json>)；[现存26项验证](<E:/creation-reading-assistant/task47v2-codex-review-20261015/existing-manifest-verify.json>)；[本地失败仍恢复](<E:/creation-reading-assistant/task47v2-codex-review-20261015/premature-restore-result.json>)。

## 未覆盖项
设备NOT_RUN、真实生产正向未交；V0未退出。D/F、SAF Provider/9组仪器、X、实体/MIUI/正式升级发布边界不变，Android全产品分母未知。47报告R1-R4全部CLOSED不采纳。

## 恢复与副作用
Root新反例外部边界全离线，本地仅自有E，现存清单未重写。原产物/D产品/Git/桌面/HOLD/私库/网络/定时任务不改。文档写前字节核验、原件E快照。

## 交付状态与下一步
47-v3同一owner只修已复现生产producer→consumer→invoke合同、清单必要集合和记账/恢复状态。先跑与is_injected判定解耦的生产正向边界测试，再扩失败验证；合格证据必须包含实际CLI子进程沿生产模式到invoke和恢复的记录，不能仍用offline注入分支成功代替。继续不设备/不Gradle/不改Android/不重APK；核过再派唯一MuMuowner。一次完整提示词，建议DeepSeek强推理，替代Gemini3.8；约60–100min，75min预算点交精确阻塞，不无限扩张。独立产品工作无新证据缺口，不凑并行任务，共享执行/恢复接口单owner。
