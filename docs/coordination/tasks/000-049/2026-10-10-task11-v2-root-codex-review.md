# 任务11-v2核查与Root补齐、下一批并行

核对：2026-10-10。任务编号/版本：11-v2 CRA-SQL-PROBE-TARGET-GUARD-v2 + Root补齐。
交付状态与下一步：外部v2两个主要反例已修；独立审查发现PM宽泛UID回退、task/run绑定缺口、恢复测试手动清标志与合法ID禁词误拒。Root在独立E候选补正，保留外部原件。最终采纳Root 44工件与10-v3 997源码；下一12-v1唯一owner合流/冻结/限定MuMu，6-v3并行离线更新已有集成材料（输入发生真实变化，不重做旧56补丁）。无D产品/Git/发布授权。

## 输入指纹、所有权与改动

外部43工件实算全部一致，清单SHA ab3194265bef878a693ff8070e3b3edec5d8506c3f453462e3bd62628bbdb7ab；13项证据SHA全部匹配。外部REPORT SHA ab265499ec5e590ef7a72216c4226bdb697b7c3520af60aca12571d6d9cced38。外部自报Antigravity/DeepSeek，不据此推断更细运行型号。报告“干净只读”不能解释为既有WIP不存在；Root HEAD仍e39a78da9c62f02614207cffb36969a32492eaea、暂存区空，既有WIP保留。

Root最终44工件清单SHA 81365a8d0af21b8e383b0c462ebcf52593e6fb518d7dbb04362596f266f36695；完整输入绑定见[下一批绑定](<E:/creation-reading-assistant/task11v2-codex-review-20261010/next-input-binding.json>)。Root仅改callback、对应旧测试并新增Root测试，完整root-relative.patch保留。Root候选旁的3个Kotlin/manifest仅为旧离线验证器synthetic夹具，不是产品997源码，12必须使用绑定中的10-v3真实输入。

## 环境、执行与结果

Root复跑外部新增套件19项：16实际通过、3红专用跳过，exit0；其两个主要反例修复成立。Root补齐后24项：21实际执行通过、3跳过；真实审计文件阻塞→恢复测试、无目标包的泛UID输出拒绝、缺失/漂移task/run零命令拒绝、合法keyword/双连字符ID均有回归。

Root删除PM无包名userId/appId回退；完整匹配bookId并精确比较可信SELECT模板，不扫描合法字符串内容为SQL禁词；补cfg task/run缺失/占位/漂移拒绝；真实审计失败只能在重新授权意图成功持久化后清失败标记，并清缓存、重做全部身份探测。

verify_seams成功；初次verify_v4_regress为44 PASS/2 FAIL，失败涉及Root候选未复制旧验证器的兄弟synthetic夹具依赖。补齐自有夹具后仅复跑两个受影响验证器verify_e2e与verify_chain_v3，均exit0；不把第一次失败改写为46/46新全量通过。原始失败与补正日志全部保留。外部132总数包含3跳过，不写132项全部实际执行通过。

## 原始证据

- [Root原交付机械核查](<E:/creation-reading-assistant/task11v2-codex-review-20261010/verification.json>)。
- [Root定向原始日志](<E:/creation-reading-assistant/task11v2-codex-review-20261010/root-tests.log>)、verify_seams.py.log、verify_v4_regress.py.log（首失败）、verify_e2e.py.log与verify_chain_v3.py.log（补正）。
- [Root最终验收工件](<E:/creation-reading-assistant/task11v2-codex-review-20261010/candidate/acceptance>)与acceptance-sha256.json、root-relative.patch。
- 10-v3源码/15JVM证据复用，不重复其Gradle。

## 恢复与副作用、未覆盖项

本轮无设备、APK或D产品写入，仅自有E Python候选/夹具。真实设备PM UID/sqlite与pending保存闭环尚未验；DEVICE_ACCEPTED=false；31仍沿用10 PASS/1 FAIL/20 NOT_RUN。X整机中断、Back专属完成归因、保存队列排空、初始新save、实体手机/MIUI未覆盖。全产品分母未核。

## 并行与退出决定

12拥有自有合流候选、联合构建、冻结与唯一MuMu；先真实能力/身份及首异常定向闭环，再最终31和逐包卸载取证/恢复。6-v3只更新旧集成材料中的本轮变化与冻结引用，输出PREPARED_ONLY、不构建、不用设备、不应用D；不能宣布待验源码已设备通过。二者只读已冻结输入，物理输出/逻辑所有权互斥。若12必要窄修改变输入，6现有输出标明旧输入限定，Codex后续核差量，不自行追踪/覆盖12可变目录。旧1—5/7—11充分未变证据不重跑。
