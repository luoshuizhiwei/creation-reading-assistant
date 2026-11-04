# 任务10-v3与11-v1核查

核对：2026-10-10。任务编号/版本：10-v3 CRA-JOINT-31-ACCEPT-CLOSURE-v3；11-v1 CRA-SQL-PROBE-TARGET-GUARD-v1。
交付状态与下一步：10-v3恢复实现/15定向JVM限定采纳；11-v1返修，两个Fake-only反例证明低层任意SQL及审计失败重试缺口。下一11-v2先补门，不解锁合流/设备。10输入未变不重复测试。当前确实只有一个独立剩余实现：11的callback与对应测试；合流/冻结/设备依赖其通过，不为凑并行数制造准备或重复验证。不是把单设备限制推广到整个项目。

## 输入指纹、所有权与改动

Root实算10最终997源码全部SHA匹配，996输入只变两文件/新增一测试；源码清单SHA eebceb4c7f4fbd6a632042b22944af722351fcd892217b8b9dca7df0efc09c5a，REPORT SHA24e867b41e435b41bc18c2f6d3dd5d53deb9e377737243ecb4da41f390d53949，交付27项SHA全部匹配。11最终43工件SHA全部匹配，5修改/1新增；清单SHA1a2fc74b98fe0adeae4959c2fa1490fbddec3f9801d2cfdce12aa23cdff5b4d5，REPORT SHAf3ea0975f586016f72cc3f41687880960382c07a0886b3304f33f79bcb0ef64c。

11证据清单24项中22项匹配，清单自身与gen_evidence_sha.py两项不符。11自报运行夹具误改只读输入bat后恢复；Root实算原42输入全部恢复，不等于抹去该过程副作用。下一必须在新候选运行夹具，清单不纳入自身指纹。

## 环境、执行与结果

两项均独立E候选、无设备/最终APK；HEAD仍e39a78da9c62f02614207cffb36969a32492eaea、暂存区空。相关进程快照未见任务/GradleDaemon占用，不代表所有外部UI状态已知。实际应用/模型按报告自报opaque身份，11报告未充分给出实际执行模型标识，不能把推荐GLM5.3Flash当实际身份。

10生产修复在layoutChanges真实变化后先landed，复用未改plainRestoreSettled，保留pending、未就绪、取消、中间项拒绝/尾项clamp。独立只读核查认为四个滚动次数2→1断言符合新行为；sampleCount ==3→>=3降低计数精度但保留实质合同。原始红2失败、最终绿15/15 XML及exit0日志成立。reader包过滤执行BUILD SUCCESSFUL，REPORT的734/95不能由交付仅2个XML独立证明，不采用该计数。离线回归证明循环缺陷，不单独证明设备永久pending因果。

Root再次运行11新增离线套件：12测试，11执行通过、1红专用跳过，exit0；但该套件未覆盖以下反例，因此不足以通过验收：
1. 合法隔离db路径下，直接_sqlite3_rows传入ATTACH保护库SQL被送入Fake SQL通道而未拒绝（没有真实数据库操作）。路径白名单不能限制SQL内部另开库。
2. audit始终失败：第一次身份审计失败时0 SQL，但身份缓存已存；同Ctx第二次调用执行1 SQL后才报审计失败，违反执行前拒绝门。

静态另见runtime只比较数据目录/db的stat UID相等，未与PackageManager报告的宿主UID比较；缓存只按db路径绑定。下一11-v2补精确运行时身份和上下文失效，不把此静态缺口写成设备已读错库。低层只允许可信进度SELECT，任意SQL/脚本和shell注入在任何ADB前拒绝；成功身份审计及每次查询意图必须在SQL前持久化。

## 原始证据

- [Root清单核对](<E:/creation-reading-assistant/tasks10v3-11-codex-review-20261010/verification.json>)。
- [Root Fake-only反例](<E:/creation-reading-assistant/tasks10v3-11-codex-review-20261010/guard-probes.json>)与probe_guard.py，没有真实ADB。
- [完整Android diff补件](<E:/creation-reading-assistant/tasks10v3-11-codex-review-20261010/android-complete-relative.patch>)：外部patch漏新增测试，Root按清单补齐2改1增，未改候选字节。
- 两外部REPORT、红绿日志和定向XML保留；不重复充分验证或扩大构建。

## 恢复与副作用、未覆盖项

本轮Root没有启动设备或构建。11原输入误写已复核恢复；设备后账沿用上一轮限定结论，没有新设备恢复/验收证据。双方DEVICE_ACCEPTED=false。31仍沿用v1的10 PASS/1 FAIL/20 NOT_RUN，未解锁新批次。X整机中断、Back专属完成归因、保存队列排空、初始新save、实体手机/MIUI仍未覆盖；全产品分母未核。

当前不改D产品、暂存、提交、push、PR或发布。下一11-v2只写新E Python候选与对应测试，定向离线闭环；通过后再唯一owner合流997源码和最终验收工件、双APK冻结绑定、先首异常定向复验再限定31及完整恢复。保留既有合同、已验任务及原始失败，不新增无关产品范围。
