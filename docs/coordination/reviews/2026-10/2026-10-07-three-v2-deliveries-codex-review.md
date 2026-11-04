# 三项v2交付核查与模型分工补正（2026-10-07）

## 任务编号/版本
CRA-THREE-V2-DELIVERIES-CODEX-REVIEW-v1。用户报告三项完成，同时要求提示词明确应用/模型、作用及新手操作说明。Root核实际成果，不将报告交付当功能通过。

## 输入指纹
活动仓库HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。三个独立E输入为txt-accept-chain-repair-v2、d2-relink-identity-fix-v2、j1-crossbook-temporary-seam-v2；原A981/981内容当前一致，J1自报误改后已恢复。Root原始证据目录为本报告E目录。

## 所有权与改动
Root核TXT源码/APK/日志和只读设备后账；三个独立reviewer核各自产物，未构建、未改产品、未安装卸载或设置。D只维护本报告与current.md。所有原交付/失败证据不改。后续提示词必须框外给推荐应用、主模型/备选、任务作用、启动/等待及退出条件；模型按实际可用和本任务风险，非永久路由。

## Standards
TXT984/984完整源码清单、双APK及构建日志实算一致，宿主复用/测试新构建身份分别记录。原A981输入当前无漂移。D2原始红70项2失败、绿70/70和日志EXIT0成立，980清单一致；final-sha两patch旧值需纠正，EPUB成功repair运行声明无对应用例。J1候选982/982、原XML89/89（10类）、日志SHA成立。J1施工误改上游A后字节恢复，但mtime变化记录；其私有缓存删除约3.5G已自报，与无资产删除措辞矛盾，不扩大清理授权。

## Spec
### TXT仍需验收链返修，不能签纯产品保存冻结
固定dump本轮确实交付：r2e canonical、原批dump和Kotlin载荷SHA匹配，uiDumpPresent=true；失败停止依赖项也有进展。但callback先启动/打开、移动、等待、back并退出，随后driver才启动55s仪器；Kotlin只在退出后连续读DB，两行不是独立退出前S2与退出后Exit，sessionPid不是先前UI进程身份。连续会话接缝未完成。
另：55s嵌套exit漏bookId，checker要求该字段；65s检查仍引用旧65步骤dump且exit_pair在普通路径未赋值，成功短路走到此处会异常。FUSE路径predicate实际接受OTHER-TASK/OTHER-RUN staging与j1-accept-foreign等无关路径，违反严格task/run/step映射；离线复制谓词没有覆盖这些反例。UI前后字节仍和旧m3t相同，仅几乎离屏节点消失和主体117→0几何变化，不能证明新可保存source位置或pending稳定。因此普通6/12方法PASS、55sFAIL、后5NOT_RUN及clampNOT_RUN，保留真实DB观察但不签产品根因。
### D2存在2P1/1P2，70绿不足以合流
固定books/<id>/reselect-staging.<format>先delete可命中同名真实正文，校验前删除/覆盖且finally再删除；没有操作级归属，同书请求也能相互改写。应在明确自有区域原子创建唯一暂存文件。
严格重关联门作用于refreshFromSource共用函数，使来源内容已更新后的正常刷新被拒绝；旧测试被改成同内容掩盖回归。严格门需限定重新关联，保留既有刷新语义及原测试，不将退化列待决。
helper在进入外层finally前复制，部分读写后失败可遗留半截文件；需helper内部按自有创建物清理和中途失败用例。未知身份拒绝与TXT同staging字节换入的方向可保留；真实Room用户记录保留/EPUB新接缝仍未证明。
### J1有真实接线但pending返回接缝未闭合
A→B临时会话已成功，B→C尚加载或失败时，state仍是B/[A]；现返回动作直接弹A，跳过应保留B。abandon仅清pending不导航回B，失败页仍C；Back也不作废pending，协调器可接受之后late commit，破坏合法栈/新导航。89测试只验abandon后状态，未走真实pending→返回/取消→late结果链。需按未提交请求和已提交会话分别处理返回与取消，作废请求并保护较新导航；不改普通保存核心。实际Compose时序/页面回退仍需限定设备证据，不能外推A持久进度损坏。

## 环境
Windows/Python3.13。Root本次仅只读adb-s已核serial127.0.0.1:16416的隔离qahost后账；无产品构建和设备写入。报告模型自报GLM-5.3-Flash、deepseek-v4.1-flash等不等于Root已核菜单；应用名不等于实际模型。当前Codex工具可用模型元数据及官方文档支持Sol作复杂任务、Luna作明确固定任务；具体High配置是本项目风险判断。

## 执行与结果 / 原始证据
source-build-review.json：TXT984/A981和双APK/日志实算。
device-readonly-after-review.json：当前隔离包/受保护数据与设置核查。
D2原red/green日志与XML、J1原10XML/日志通过已核，复用不重跑全量。TXT原r2e及canonical/harvest文件保留，不改旧run签PASS。

## 恢复与副作用
Root当前受保护71/71哈希文件（含DB/WAL/SHM）、原APK对2/2、七设置7/7一致；新沙箱双包absent、forward/reverse空。hostVM及约定前台状态未完整独核，不能称全部恢复通过。Root没有设备恢复写入、产品修改或Git操作。

## 未覆盖项
TXT产品根因、普通后5/clamp、X整机中断清零未证；D2真实Room保留/EPUB新接缝/新增暂存冲突未验；J1pending返回/取消/late结果设备旅程未验；全产品分母未核，无实体手机/MIUI/发布结论。

## 交付状态与下一步
三项v2报告已交付3/3，完整功能/验收闭环0/3；限定测试70/89分别保留，不改分母宣称全产品完成。三项均返修，先源码/离线门过审再联合构建或设备。
模型分工：关键TXT会话设计、D2数据安全、J1并发状态审查首选当前可用GPT-6.1 Sol high，难点持续阻断才考虑Astra；明确小patch和定向执行可给已实际用过且当时可用的GLM-5.3-Flash/DeepSeek-v4.1-Flash，固定哈希/日志工作给Luna，所有复杂方案由强审查先定边界，避免多轮机械返修。此为本轮建议，不固定历史路由，也不假定外部菜单/额度。后续每任务框外用白话给模型、作用、启动步骤和完成标准。未自动派发/发外部消息；D回灌/Git/发布授权不变。
