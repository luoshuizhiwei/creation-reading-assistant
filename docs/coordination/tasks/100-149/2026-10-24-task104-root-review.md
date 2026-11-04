# 104-v1 Root核验：倒计时单例闭环

## 任务编号/版本、环境、所有权与改动

104-v1，2026-10-24香港时间。原Claude唯一模拟器owner；Root只读取证与D协调。唯一task59环境不变，无产品/测试源码修改、构建或APK复制。引用103保全产品694bf93dcae49b0c36375856537c6a195c42604b1e843554b1daf061edc52f3b(52763781B)与test960808b52a4bffad1273ddd07538df487d70c85678f669c73b69046b0eb953d6(10567324B)，103 Root实物核同且本批输入绑定相同。

## 输入指纹、执行与结果、原始证据

E:/creation-reading-assistant/task104-tts-countdown-reverify-v1 REPORT/ready/原logs/20-instrument-stdout.log已核。T104V1-20261024-073700一次run，实际class ReaderTtsControllerAudioFocusTest、test timedStopLifecycleOnRealController、numtests1、状态1→0、OK (1 test)、Time1.388、最终INSTRUMENTATION_CODE -1，无失败。派生XML只作派生证据，原stdout为直接依据。

既有一例按生产Controller+Handler断言武装850..900、关闭保持Playing/剩余0、重设、pause清零、resume显式外部mock onStart后Playing/重新武装、release清零，整例通过。Controller1546dab4/测试93d01170未再改，102行为红→103最小修复/夹具FAIL→104修后夹具整例通过关系成立。

## 宿主误判、恢复与副作用

Root核实际scripts/host104.py第32行TEST_CLASS为class#method，468行用于am instrument selector正确；474行同值又传evaluate，374行按状态class精确比较，仪器仅报base class，所以宿主summary primaryError为class mismatch。原host FAIL/summary/raw保持，不篡改为绿；它不推翻原仪器通过。未来复用此单方法host必须分开selector与expected status class，不为修工具重跑104或制造解析矩阵。

Root解析原protection final：ok=True/private71/external820/db_groups_match=True/diffs=[]；00-original-test-verified与06-original-test-restored的apkSha/inventory/userState逐字段相同。summary recoveryErrors=[]、collect成功NO_EXTERNAL_EVIDENCE、fixture NONE_DECLARED、原缺席产品卸载、原test恢复；settings night=no且约定size/density/rotation/保活恢复，VM仍运行记录。无新fixture/导出清理。无Git发布/D回灌/私库读写/存量删除迁移。

## 未覆盖项、交付状态与下一步

倒计时实现及受控模拟器单例接受，宿主选择器误判单列，102/103原FAIL保留。非真实TTS声音/真实UI/系统焦点/真实15分钟到期/Edge/实体手机。98八焦点/协调器8与既定96/97/99/100/101充分范围复用，不追加设备确认。

方向复核：timer目标在上述边界内闭合，下一只读窄核既有播完本章(-1)是否在自动续章中正确消费；这是102记录的代码观察，未先称已证设备缺陷。原explorer已接收具体调用/测试核实，Root不抢写E；无真实依据不制造实现。全产品分母未知，单例通过不是全项目退出。
