# 任务145-r7：保全失败总裁决与main身份接线（仅离线）

2026-10-26香港19:32；原Claude global Flash同owner，20分钟，差量256KiB/raw512KiB。Root已读r6生产host、测试关键、REPORT与完整原log，并实核三件sizeSHA。复用提权shell/点文件保真、生产hook成功链修改事实，不重刷旧11项、Android/JVM/构建。当前仍DEVICE_BLOCKED。

````text
基线recheck-r6/host/host145_r6.py20218B SHA790bc22c427edc723806f03e882988cd429daaac360ec84387b263b515222663；offline_validate_r6.py20754B SHA8321678b403cba3051a0737693375773cff5eba0a097b0b8729ef6d7ad8cc8a1；inputs-r6.json5325B SHA36b14f090230aebd4558bb80c2953ba1b16bfec76c889e3ec3bd3af4c1fed243。只新E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r7。v1-r6/raw/SYNTHETIC原件冻结不rmtree。

确定性缺口1（原log已证，非推测）：r6归档TimeoutError分支保产品且testRestored=true，但summary.primaryError=null/recoveryErrors=[]/businessOk=true/ok=true，父run_attempt返回0。父总裁决只看primary/recovery/stop/cap，不看decisions.threeRootOk。hook必须把任何保全拒绝或异常按原错误结构追加到传入recovery列表，保留异常cause/结构化拒绝原因，继续安全独立恢复，整体summary.ok=false且exit非0。不能只blocked字段/改测试期望/重写summary，不能改父136源码；最小新host hook修复即可。preserve-exception落盘失败当前宽catch pass也丢次错，须和保全原cause分列可观察，不覆盖主因。成功分支不变。

确定性缺口2：r6 run_chain实际bind_scope+install_hook后直接parent.run_attempt，没有走生产main；只有未approve负例调用H.main。main收CLI serial/boot后仅给退出保全expected，未在安装前与parent.instance本批fresh核同。新场景必须真的调用新main(argv) -> load_parent -> bind -> installed hook -> true parent.run_attempt/collect/原recover，测试只替换外部边界与加载器返回真实已mock端点的父模块，不替换main/run_attempt/collect/recover/hook体。main在任何设备修改前验证独立fresh serial/boot/user与参数同；偏差拒绝且install/instrument/uninstall次数0。只一次正确成功全链复用真实shape正例，再两个独立新scope的保全拒绝/归档异常负例：success summary真绿/产品卸载1；两负例产品卸载0、独立test/settings/protection按父安全门完成、保全原因留在recoveryErrors、summary假且exit非0。所有场景保持原父/新hook体，不测试local重写包装器。

确定性缺口3：r6 MANIFEST_SCRIPT仍find|sort|while，未捕find失败；find中途报错可被while rc0盖住，stdout部分目录清单当完整。新最小shell修正真实错误传播，保存原rc/stdout/stderr，不吞stderr。文件type用-d/-f会跟随symlink，清单必须明确拒绝symlink而非将其当正常目录/文件；tar会拒链接，不能因此源完整性检查默认绿。引用已证138脚本语法，不新增设备探测。仅定向fake验证find失败不会出现合法空/部分保全绿，原正常点文件测试复用无需全量重跑。

不要再扩展框架，按这些实际合同缺口闭环。未完成准确BLOCKED。离线脚本未匹配外部命令必须抛；场景每次fresh parent/唯一SYNTHETIC_FAKE子根，不能删除/覆盖旧证据。严格模拟b.run/Dev.sh(*args,timeout,stdin)和二进制subprocess，不捏造API/调用计划字典。测试不得让同size替代原字节，逐根完整目录/文件sizeSHA/prepost/writer/原rc回执合同保持。保全失败保产品同时独立恢复，任何部分失败不能汇总绿。

交付最小相对r6patch、新host/必要inputs/REPORT、原定向红绿log与各独立summary引用、ready.json；列前后sizeSHA、存储分类新增体积、真实入口调用顺序及限制。15rolepins与原父1369457a4c5/basec806ce36/adapter16c93794、144新匹配APK539f44c5/f2d2982b、Android源码/仪器T141BV1/craRunId/t141b实际schema全部冻结复用。无Gradle/compile/build/ADB/VM/安装卸载force-stop设置/真实hostmain/runid预算attemptsummary/F1/实体授权；所有批准分支main仅外部端点全mock并fake scope，参数--approve-device不等授权。Root不旁路build/ADB，读实际delta后才另裁。禁Git/云/私库/自动记忆/正式库/删移旧资产/kill共享进程。最终总结末行：这是任务145-r7。
````
