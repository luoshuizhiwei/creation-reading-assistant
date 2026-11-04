# 121-v1 停止门禁具体缺口闭合

2026-10-25 香港07:29。Root已读A stop-review报告8379B，接受停止范围及依赖恢复门禁，但G1不可解析ps当absence及G3非零返回即当结束仍违反未知writer不能竞争恢复的裁决。只补这些实际分支，不扩大功能；B同owner闭环，Root核实际小差量及原始边界结果后推进，避免再重复全宿主审查。

## B 国内DeepSeek：一次闭合G1/G3及身份探针成功判据

````text
接续121-v1 B。A lane-a/stop-review/REPORT.md与ready已出，Root已读。原停止范围/依赖恢复/四分支通过充分复用，但Root不同意G1/G3非阻断评级：空/不可解析ps不能证明absence；adb客户端非零退出不证明设备instrument已结束。按以下明确最小范围一次修齐，不改产品/仪器/恢复合同，不重建APK/重跑旧套件。

修改前host34292B SHAb0ad7c81608340418cb18b292aab3b95abd6dd823d2b7281509d5d9b7eeb3ee5；不符先报冲突。保全必要单份，patch/前后SHA、原始新日志和REPORT/ready写lane-b/stop-gap-closure≤2MiB，不能覆盖旧原始记录。
1.G1：ps -A -o PID,NAME只有rc0且stderr空不够；明确校验输出表头与至少一条可解析PID/NAME记录，空白/缺表头/不可解析记录按探针失败BLOCKED，不能names=[]就证明缺席。保持pidof与ps两个条件均满足、包名精确/冒号子进程匹配；不做通用解析框架。
2.G3：仪器调用返回不等设备运行结束。只有rc0且仪器协议有明确唯一终止记录（实际INSTRUMENTATION_CODE行）才能标记完成；非零/终止记录缺失或畸形均保持结束未确认，走已实现本run包级停止+设备确认，再取证/恢复。业务判定仍由原evaluate_instrument_result，普通断言FAIL但协议正常结束不能误改绿、不必额外停；保留原业务输出与主错。检查最终finally触发条件，不仅改注释。无需另改仪器/runner，不要新类探测或矩阵。
3.停止前get-state/boot/user三个身份探针同时必须rc0、stderr空和精确预期值，不能仅stdout看似正确即过；固定user0时包级force-stop也显式--user 0，避免核user后隐含默认目标。任何失败不停止、不修改依赖资产。只这几处必要hunk。

仅针对新增边界验证真实代码：空/缺头/畸形ps失败；正常有效ps和pidof缺席可确认；非零instrument返回、rc0无终止协议都需要停止；普通业务FAIL有完整正常终止记录不额外停止；身份rc/stderr不合格不得force-stop。可隔离mock真实现有函数，原四分支同输入证据复用；不写镜像实现测试/通用框架，不实际ADB。主错、stop错、恢复错不能互相覆盖，停止未确认继续BLOCKED collect/卸载/原test恢复，安全独立恢复继续。该新增代码完成后由Root核具体delta/原始结果，不在已明确范围外继续泛审计。

你不是独自工作，不改A/D/current。当前仍禁实际ADB/设备/安装/host主入口/attempt，设备完整批次另放行；APK74d106b6/test6c294a04/inputs3f86c47b保持，禁止再构建。所有私库含Claude/Cowork/DSH/.codex/memories及自动记忆、Git暂存提交push发布、云/SFTP/买号、正式库桌面HOLD实体手机、存量删移压迁、关闭重启杀VM共享ADB其他进程继续禁；1800业务+至少1200恢复、一次attempt、71/820/原test/strictnightno/VM运行合同不变。最后单独一行：这是任务121-v1 B-停止缺口闭合。
````
