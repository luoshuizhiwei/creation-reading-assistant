# 任务179-r1：按180真实形态修复身份接缝并有界只读核验

2026-10-29；推荐保持当前 ZCode GLM-5.3-Flash 最高。原179 owner负责实现、必要离线验证、一次有界只读核验及交付，20分钟从本消息实际发送起评估，超时保留，不重置。你不是唯一工作者，不覆盖他人修改。B180已经完成，不发继续、不重做采集任务。当前后续设备批依赖本接缝，本批无Android源码/Gradle/APK工作，不为并行制造任务。

先读活动仓库AGENTS.md与docs/handoff/current.md最新页首，仅沿本任务引用读必要文件。仓库 D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919。

## 基线与Root裁决

179-v1 ready 5493B SHA256 8c118c51b8c94bf0615918bccaf553ac201fe46c93acee6073150f7b747b39d7；identity_adapter.py 15099B e957bdc3c00125052fa5ef1a5bc378b775fef5e4accdc338f1a70dd2f75bf5a4；entry_wiring.py 3365B 7cab8d775ac81165ef6a2d5c09202d30cbd98b72b805202ddfbbeb4f0d61b934。目录 E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1。旧9case和原rc0证明其夹具下行为，不能证明真实环境可用；原件全部保留。

180-v1目录 E:/creation-reading-assistant/task180-instance-binding-readonly-v1，ready 5826B d6aefe365f03ddcdd6b5f0e95df941060ff91ef61a8820a57a27776a6c2ac3af；evidence.json 8508B c46d88e795f8f70d5531a62297b243f282885a030be718b613e844ec1f646db3。Root已核14项声明文件大小/SHA和原始配置、进程、PowerShell监听回执。接受“manager状态与进程/ADB不一致”的观察及精确--comment关联，不把其成因直接称为已证注册表损坏。netstat截断后空输出不能证明无监听/稳定；配置无磁盘引用，目录列表不等于完整磁盘引用绑定。历史180快照仅用于离线真实形态，未来使用必须fresh重采。

冻结文件只读，前后核SHA：
- E:/creation-reading-assistant/task94-organizing-export-integration-v1/v3/scripts/t94v3_device.py：16583B c806ce361c9c3929c4c32e08f6435e5e1f5b8ce088c8e6f0aa487cdee1d5fdbe
- E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/scripts/host136.py：52645B 9457a4c5989e2c408923048b9c6b3c818b35573e6c9345eeb048632a9c5912b2
- E:/creation-reading-assistant/task176-contract-b-host-binding-offline-v1/recheck-r3/host/host176_r3.py：21289B 9ff38f2626f7eac901c5c479fbeb5b6f1e06521cc089259ab8fb7e98da059cb7

## 唯一实现范围与具体缺口

只新写 E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r1/。复用旧源做最小新版本，不改旧文件、candidate/APK/父/host/全局state/current/记忆库。不要复制工程、缓存、历史证据或APK。无新增大目录，预计源码文档≤128KiB，全部raw+fixtures+验证scope≤128KiB，临时/pyc/根目录回执计入实际分类；复制前核空间，不删旧证据补预算。

1. 精确读取配置 vm.nat.port_forward.adb.host_port；180实际是嵌套vm→nat→port_forward→adb→host_port字符串16416，同配置还有其它服务端口，禁止递归汇总所有port选择。manager可为按index嵌套的对象，adb_port冲突校验须取已选instance，缺port/false标志原样保留，不伪改。
2. adb get-state是transport命令，实际argv必须 adb -s <freshSerial> get-state，绝非shell get-state。其它shell命令依原语义。strict fake必须按正确argv匹配，未知命令抛错。
3. 精确端口监听用有界结构化只读查询，如Get-NetTCPConnection -LocalPort <configPort> -State Listen并输出address/port/PID；不要只依赖英文LISTENING或全netstat截断。拒绝多PID、不明代理/非本地回环关联。记录完整rc/stdout/stderr，前后两次实际执行成功，失败不能与空输出比较后放行。
4. 真实进程示例：C:\Program Files\MuMuVMMVbox\Hypervisor\MuMuVMMHeadless.exe，命令行为带引号exe后 --comment MuMuPlayerGlobal-12.0-1 --startvm 9698dfa5-24a6-fa03-1000-000000000001 --vrde config。它不含完整VM路径或ginstance。按命令行实际参数精确匹配--comment与原VM目录basename，验证已知exe路径与监听PID，不接受同名进程/子串或-10冒充-1。PID/UUID/ginstance/boot均不可预填为当前事实。
5. vm_config没有disk refs不能当作已经验证磁盘归属。仅只读原VM目录 E:/MuMuPlayer/vms/MuMuPlayerGlobal-12.0-1 内当前.nemu小描述文件和必要元数据，机械核--startvm UUID及该实例可写磁盘引用；解析相对路径/规范化后严格核真实祖先，不用字符串startswith，不把目录存在等同引用成立。共享只读基础镜像若引用在外如实记录与既有归属合同核对，不能把所有外部基础镜像一刀切误拒。未知/未证明则明确BLOCKED，不放宽。禁止扫描其他VM/哈希或复制85GB data.vdi等镜像。
6. 配置SHA用原始read_bytes计算；解码内容与raw文件bytes/SHA分开。前后配置/实例created/监听/进程/boot稳定性要核两端rc/stderr、非空有效值，不能rc0空boot当成功。保持失败即拒、原问题不吞掉。
7. 接线要证明actual run_main路径最终load_parent所用parent.b就是绑定模块；旧测试只对bind返回对象调用instance不足以证明下一次load_parent仍复用。只读检查真实loader，新增最小内存接线必要时在本新wrapper实现，冻结文件不改。通过真实入口的离线strict外部IO回放捕获它实际调用新resolver，不禁用内部instance/owner/baseline/安全门，不用预填身份lambda替代。无需重做176整套大suite或模拟整套恢复；无法越过无关生产门时明确到达阶段和实际拒绝，不宣称完整main通过。

## 最小验证与一次真实只读探针

先用180原始形态机械构造离线输入并跑真实新adapter，覆盖正常缺manager port、多个非ADB服务端口、精确comment；对端口冲突/错comment或exe/实例漂移/第二次rc错误或空boot/相对磁盘越界做定向负例。保持旧FAIL、独立子进程退出码和完整必要原log；不为覆盖数字重复已充分suite。

上述通过后允许一次有界真实只读identity探针（不调用生产run_main/main）：原index1，created_timestamp=1777974446694936；manager D:/Application/NetEase/MuMuPlayer/nx_main/MuMuManager.exe；ADB同目录adb.exe；配置仅原VM目录configs/vm_config.json。通过新生产adapter实际fresh读取配置→精确监听→PID/命令行/描述文件→已attached serial get-state/bootCompleted/bootId/user0→前后一次稳定复核，保存argv/rc/stdout/stderr与原始文件SHA。只能使用fresh配置推导且实际附着的serial，不connect、launch、reboot或kill服务；实例不在线/未知归属即交真实BLOCKED，不循环修设备。真实探针本身也限时，外部命令timeout明确。

本批严禁install/instrument/stop/uninstall/Home/设置改变/业务attempt/Gradle、关闭或点击其它移动端应用/其他VM/系统GPU策略。不要执行178或旧恢复体。未来业务批仍待Root验收、新鲜APK/owner/基线绑定和真实166恢复合同；本身份探针不等于设备业务PASS。

## 交付与停止

唯一ready：E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r1/ready.json。交付最小源码/patch、输入与前后完整SHA、REPORT、必要原log/独立rc、actual main接线到达证据、fresh只读probe或精确BLOCKED。REPORT给所有新增文件互斥体积统计（含fixtures/scope/root receipts，不仅raw目录）、实际时间、旧179/180限制、未完成项；无自引用伪ready SHA。冻结三源前后核同。未知命令、归属、所有权或预算冲突停止依赖动作并报告，不宽泛catch/空默认值伪绿。无Git暂存提交pushPR发布、云、正式书库、私库/sessionDB/.codex/memories或自动记忆。

最终总结最下面单独一行：这是任务179-r1。
