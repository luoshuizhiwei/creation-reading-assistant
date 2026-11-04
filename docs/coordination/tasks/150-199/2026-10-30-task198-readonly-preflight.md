````text
# 任务198-v1：新LIFO设备批前的只读fresh身份、APK归属与恢复基线准备

时间2026-10-30，建议沿当前GLM-5.3-Flash，原197/193/194会话同owner。预计15分钟。你是唯一原MuMu instance1的只读preflight/runtime输入owner；本阶段禁止任何设备变更及业务执行。当前无第二必要独立项，不凑并行。下一业务run须Root核本ready后另发明确执行裁决，不能把本提示词当main授权。

Root已核197-r4-final：ready4458B SHA eb0e1fedb5639cb6c1ced21eb1a6a353a5e8e77c5aa0b69c4d81c9fc9022132a；38pins同、所有交付after与candidate同；真实五类XML42项0fail/error，新增8项timestamp19:26:46，原test-unit5 rc0/23.7s，旧rc1和旧XML错误保留；compile主21s/AndroidTest6.7s/两assemble9.8s6.9s原rc0。Root实际APK签名两rc0，同证书05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1，manifest runner com.creationreadingassistant.testing.CraIsolatedTestRunner target com.creationreadingassistant。这仅离线编译与定向验证有限接受，尚非LIFO/12列设备通过；报告“生产语义本身无缺陷”过强，普通生命周期接线/首锚等仍需真实链路验。原35分钟预算超时永久留。

固定candidate E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，只读。产品app/build/outputs/apk/debug/app-debug.apk 53899310B SHA277b83bfba6e712fc46f09a2e2cd12c4b102e58d283a56ebdde1dca07d07b8c8；测试app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 10696613B SHA122ea146000dee3f8a72a7364321443cc478d1363fa6bbe43ee249d8d00f0984。不重构建、不复制APK。现有ReaderInternalTemporaryLifoTest及fixture/强12列与精确正文/LIFO不改，先核实际source SHA、APK内目标class/method及runner/target，关联本轮APK。

唯一新根 E:/creation-reading-assistant/task198-lifo-fresh-preflight-v1/，唯一ready=根ready.json。只新增最少runtime准备和原始只读回执/REPORT，不写全局state/current或共享candidate；旧task193/194/192/184/179/166及所有原证只读。参考task193-reader-internal-lifo-device-v1/preflight_193.py、prepare_inputs_193.py和194恢复报告，仅按链接读必要文件，禁止执行旧run_batch/main/recovery或旧inputs冒充fresh。复用已接受192真实entry/collector/26role机制与184r2strictkeepalive/179r3完整strictresolver，离线证明充分不重跑旧suite/main。

## 本轮只读步骤与成功标准
1. 先核本机没有另一owner的ADB/VM/构建/候选写入冲突；只观察，不stop/kill/改锁。通过完整179r3生产身份链fresh核原instance1（id/创建时间/VM路径/manager/adb路径/唯一serial/transport/boot与在线身份），严判rc/stderr，managerprocess=false等旧flags原样分记，不能只attached或tasklist弱证。旧serial/boot只是历史，不硬写当前。若原VM不在线/身份不唯一/serial漂移无法绑定，直接BLOCKED；本轮不launch/reboot/connect/kill以修环境。
2. 全部只读命令adb -s本轮核实serial。核产品pm/user0状态及三根缺席（194关闭基线），原test APK SHA0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c/原数据inventory d6ac3c5ff7845b4afc9b384fb11b7a2bf921b9f722d4706bb6fe10801d003e29/userState；存在差异不自动restore/install/uninstall。
3. fresh采实际设置、焦点、无活动instrumentation/其他owner。本轮settings必须用实际值与既有恢复约定对比，不复述194ready错误1600/160/stay3；194原证实际1080x1920/280/stay0/volume100仅参考，新读原值逐项记录。冻结保护consumer71private/820external/DB baseline按已有合法只读capture路径验证，不访问正式书库或扩展许可，不对设备写入。所有argv/原rc/完整必要stdoutstderr及时持久，不能只后写布尔摘要；最后完整identity在全部采集之后。
4. 用上述fresh证据准备下一新batch的26role实际输入闭包/owner/preflight草案，固定当前APK与source/runner/class/method/原恢复体/strictentry和保护consumer；真实恢复必须原冻结166.run_recovery/sameHOST158/preserve166/RUNNER/父正常门，不用182手写卸载。此阶段不调用任何main/instrument/恢复/stop/uninstall/install，仅准备并核接缝引用；输入无法准备则如实BLOCKED，不伪完成。
5. ready明确READ_ONLY_PREFLIGHT_READY_PENDING_ROOT_EXECUTION_GATE或具体BLOCKED，给完整pins/实际差异、每项原IO回执、独占owner/输入绑定、原test/data/settings/保护基线、最终identity以及下一执行的停止条件。Root验前必须等待，不自己继续业务run。

预算source/最小runtime/REPORT/ready<=128KiB，新增普通文字raw<=128KiB；必要完整保护capture封装单列实际字节（复用既有baseline，不复制整个历史/工程/APK/cache），先估算空间。超额保全如实列，不删证据补绿。历史193业务FAIL、194有限恢复关闭、181/182/184缺证/越界、197各次错误和预算超额永久留，不用新证倒推历史。

禁止任何main/instrument/业务重试/安装卸载/stop/Home/Back/设置/恢复体、launch/reboot/connect/kill/其他VM或实体/GPU策略、Gradle/candidate源码APK修改、Git暂存提交pushPR发布、删移既有资产、云/正式库/私库sessionDB/.codex/memories/自动记忆。UI/锁屏/login/安全权限/用户stop或Esc/所有权冲突立即停止相应操作不旁路。本任务不放行业务、不称全产品完成。最后独立一行：这是任务198-v1。
````
