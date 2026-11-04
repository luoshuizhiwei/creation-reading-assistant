# 102-v1 Root核验：倒计时行为红与恢复闭合

## 任务编号/版本、环境、所有权与改动

102-v1，2026-10-24香港时间。Claude唯一E构建/模拟器owner；Root D文档与只读核验。原task59工作区/Gradle/JDK17/SDK不变。产品未改；新增受控仪器一例。不是生产UI真实声音旅程。

## 输入指纹、执行与结果、原始证据

E:/creation-reading-assistant/task102-tts-timed-stop-v1原REPORT/ready/logs/evidence已读。实际Controller c005dcf3c61400c62b213943e1d04c61123f332c687834e6ad9ae90121e3955a核同；仪器ca4d097f155b5724618d8a907a1e1a8d8570e145ddb7d514133f52b70fbab1b6(11679B)、patch63d0619a3d1ae2843e8687bbae324c05700b565f7c60612e2201091ead243ef9(2673B)、实际run test d0d2f0ccdcfbd348efd9859db643df476443d32fc2453a73f61aae7cd6839b44(10567324B)单份保全实物核同。产品仍5049b01f(52763781B)。原协调器XML8/8零fail/error/skipped，日志编译/assemble成功。

一次主run T102V1-20261024-070800原instrument stdout：1项1 AssertionError，timedStopLifecycleOnRealController第219行预期850..900实际0，失败码-2；系统instrument终码-1不掩盖测试失败，派生XML明确标派生。失败后停批，无产品修改或设备重试。

Root源码核定：Controller381-395首次tick使用handler.post立即入队，后续才postDelayed1000；先play再另一次onMain触发onStart，首次tick可先见idle，387行的status门控调用stopCountdown清零。原报告“首个1秒tick”措辞不精确，应称首次posted tick。Coordinator deadline仍武装，不证明到期停止失败。修复仅应让已武装倒计时与异步播放状态解耦，pause/stop/关闭/release既有清理保留。

## 恢复与副作用

Root解析02-protection-final原stdout：ok=True，private71/external820，diffs=[]，db_groups_match=True/reasons=[]。00-original-test-verified与06-original-test-restored的APK SHA0d18d788、完整inventory、userState逐字段相同。07-settings-restored显示night=no与约定size/density/rotation/保活等；summary recoveryErrors=[]，collect成功NO_EXTERNAL_EVIDENCE，fixture NONE_DECLARED，产品原缺席→卸载复原，原test恢复；实例66432仍运行。无本批测试书/导出创建物。未执行新的ADB补测。

实际目录13584758B（ready自报13584754B差4B，按Root当前实物计），含run test10567324B，其余3017434B；产品未复制/build增量未测。无Git发布/D回灌/私库读写/存量清理。

## 未覆盖项、交付状态与下一步

102状态为受控Controller行为FAIL且恢复接受，不是功能PASS。模拟器无TTS引擎，真实声音/真实UI/真实15分钟到期未验。播完本章跨章/调音量淡出仅观察，本批不扩修复。方向复核：此为具体已复现现有功能缺口，有价值下一步是同owner最小Controller修复与受影响复验；不扩大格式/音频/跨章矩阵、不重跑98八焦点。103采用新源码/输入绑定和新批次，须先修复/编译/原件保护后一次主run，完整恢复预算合同保持。全产品分母未知。
