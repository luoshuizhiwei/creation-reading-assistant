# 任务182-r1：原owner只读末态闭合与历史缺口分记

编号182-r1；推荐沿原ZCode模型；15分钟评估，从实际收到本全文开始。你继续原182会话，拥有唯一原instance1只读设备观察权。Root维护全局state/current，你不写它们。输出只在 E:/creation-reading-assistant/task182-recover181-only-v1/recheck-r1/，唯一 ready.json。不要覆盖182-v1任何文件。

## Root裁决与目标

182-v1 ready4616B SHA d796079ebbcdd8bf7edafa0fceca2fb4cf4a5bddfd020c027467ecf4a338a253 仅接受部分事实，不接受完整恢复完成。Root已实际读取run7的pre/post四tar：CE各28成员、DE各4成员，成员路径去精确root前缀后，类型/size/内容SHA与各manifest完全一致；各source-before=after，CE/DE pre=post。CE tar1278464B SHA4c33d50ead829fa326808320cc2e2d55c3a202ab4bdcd38fecbb863fb8b3c3f7；DE86016B SHA6ad2ae3fb0ed641a540a5b0e2170bc3e5752513a44d5bf6df6eba7c153033877。复用此核验，不再复制/生成tar。

run7 scope=E:/creation-reading-assistant/task176-contract-b-host-binding-offline-v1/recheck-r3/attempt-T165V1-20261029-013950。原driver-run7.log证明after.stdout字典异常发生在源码uninstall调用后，但不证明该命令成功rc、精确次数或完整父166门。recover-log.json被run8覆盖；当前T165V1-20261029-014052仅productPath=None/BLOCKED_BASELINE_DRIFT。end-state-fresh.json的checkedAt含01:0x且多个字段无原argv/rc/stderr；完整71/820/DB/focus consumer未在driver实际调用。P4 rc未门控、P3未核与P1同serial/boot，P6直接adb uninstall而非完整父生产恢复门；这些历史缺口永久留，不能用新观察补造旧receipt或写旧PASS。

现在只补当前状态的严格只读证据与准确报告。严禁执行recover_182.py/任何main/恢复体/stop/uninstall/install/instrument/Home/Back/launch/reboot/connect/kill/start-server/设置写入/Gradle。不改变设备状态，不修旧driver后重跑。若当前不符或transport出错，记录拒绝并结束，不自行恢复/重连。

## 独立并行边界

B183-r1只改现有candidate的Nested测试并构建新testAPK。你完全不读candidate可变源码/APK或新构建，使用原测试APK冻结资产和现有冻结consumer/identity。你不改任何共享driver/runtimeinputs。设备仅你观察；B无ADB。两线因此独立，不以构建成功当设备通过。

## 执行

1. 先核182-v1实际退出且无其设备命令仍执行；保留原报告。局部新wrapper只用于记录只读调用，完整保存真实argv、开始/结束UTC及HK、rc、stdout、stderr，不截断，不用全局覆盖日志。新合法attempt-完整runId沿实际正则和真实时间，必须区分真实scope/离线标记。每个外部错误严格拒绝，不能静默默认。空间估算后再写。
2. 复用179r3生产身份适配器：E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r3/entry_wiring_r3.py（2047B SHA c300fce8d5e88934347294d53b2f68009b81b656ba95fd7ee10d3c454b640f7c）与adapter_r3.py（16872B SHA a0c12c4da7743fa5493e37471797bc668a5f5fec73404c7cf561fc3d3da93cbc）。仅调用现有只读identity入口，不调用host.main。核原instance1 created1777974446694936、精确配置/磁盘/进程/监听/ADB/boot/user0完整链，核前后同serial/boot。manager旧processfalse另记，不伪改flag。所有ADB必须带本轮核实-s serial。
3. 保存精确user0产品pm path/包缺席与CE/DE/external三根严格祖先stat/完整父枚举缺席proof，复用现有158只读probe，不因pidof rc1空stderr误称transport错误，也不放宽非空stderr。检查实际活动instrument进程/系统状态；pm list instrumentation仅注册清单，不冒充正在运行。遇活动instrument停止新观察并报告，不杀它。
4. 原test冻结 E:/creation-reading-assistant/task94-organizing-export-integration-v1/v5/preserved/original-test-v3.apk 10508820B SHA0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c。只读核当前已安装test实际APK SHA、原test inventory/data与userState。原inventory解码LF694B SHAd6ac3c5ff7845b4afc9b384fb11b7a2bf921b9f722d4706bb6fe10801d003e29；这是解码UTF8重编码文本，不是设备原生bytes。沿181原恢复合同核完整设置（night no/1080x1920/density280/rotation0/stay0/volume100以及原合同其他项），不补写任何值。
5. 使用冻结完整只读保护适配器 E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/candidate/protection_check.py（7864B SHA16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd），final --serial 本轮serial --out-dir 在其真实OUT_BASE下的唯一新合法目录。先看原OUT_BASE/CLI，不改脚本/consumer/baseline/pins。消费者E:/creation-reading-assistant/task53-de-joint-mumu-v1/device_protection.py；基线E:/creation-reading-assistant/root-runtime-device-20261015/device/protected-original-baseline-v2.json。必须原71private/820external/DB/focus完整capture及consumer diffs；不只看ok。桌面focus若仍不符如实FAIL，不Home补绿。必要保护capture单列实际空间。
6. 本地仅统计182-v1任务目录与其实际新建013503/013630/013812/013915/013950 scopes的文件数/实际字节/文本raw/源码报告/必要tar，复用原证不复制。纠正报告约55min为实际dispatch01:29:33.300到ready文件时间，原错误保留。定位是否还有未覆盖原stop/uninstall回执；没有就写MISSING/次数UNKNOWN，不以源码或当前缺席推导成功一次。不要全E盘历史扫描。

## 交付与停止

source+doc+REPORT+ready<=128KiB，raw<=256KiB；完整保护capture与已有tar真实必要保全单列，超限照实不删证据。REPORT与ready提供每个新产物精确绝对path/size/fullSHA、实际argv/rc日志、真实时间、current状态与historical缺口分栏。不得声称181业务通过、历史182完全合规恢复或全产品完成。当前证据可写CURRENT_READONLY_CLOSURE_ACCEPTABLE_WITH_HISTORICAL_GAPS，任何当前门失败则PARTIAL/FAILED。不自引用索引SHA。完成后不重复探针，结尾独立一行“这是任务182-r1。”
