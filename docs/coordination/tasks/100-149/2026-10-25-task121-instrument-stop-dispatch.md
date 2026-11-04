# 121-v1 超时后本批writer停止门禁

2026-10-25 香港07:14。Root读A timeout-review：参数传递PASS，但R1明确host侧adb超时不证明设备am instrument结束，取证/卸载/原test恢复可能竞争。Root不同意此项“非阻断”的评级：持久数据与原test完整恢复必须建立在本批writer已结束的事实之上，不能只靠错误分列接受并发恢复。仍未设备运行；只作必要host修正，不重建APK、不扩大功能。

## B 国内DeepSeek：同owner闭合timeout writer门禁

````text
接续121-v1 B。A timeout-review已出，Root接受你remaining/耗尽不启动/独立恢复的实际delta，旧代码和离线不重跑。但A R1指出真实风险：subprocess timeout只杀主机adb，设备仪器可能继续写，同时collect/卸载/原test恢复。Root将此裁为设备前阻断，不接受仅记恢复错误而与活跃writer竞争。你仍是唯一host owner。只补这条本批仪器停止/确认与依赖恢复门禁，不改产品/119/保护适配器/公共runner，不重建APK。

基线host28954B SHAdac903b936aa58ab15d5d65b08b4e29981e49dfb4517e716aeea5e0cce5c4c4f，APK/inputs不变。先读lane-a/timeout-review/REPORT.md R1和实际Dev.sh/base run超时返回方式，别猜异常类型。修改前核指纹、必要单份源保全、最小patch、前后SHA。仅对本批已经启动且发生超时/异常、不能确认正常结束的仪器，记录原业务首错，按fresh同serial/boot/user=0与本批安装归属只停止本批产品com.creationreadingassistant及本批test com.creationreadingassistant.test的仪器/进程；不杀VM/共享ADB/其他进程，不触碰未知归属或原未替换test会话。实现采用现有ADB边界和Android明确包级停止/只读状态确认，禁止通用进程管理框架，禁止以主机adb客户端退出替代设备结束确认。该停止仅为后续获Root完整设备批放行时的本run恢复能力，本轮绝不实际ADB。

先确认本批writer已停止，再按原顺序collect/清理/卸载/原test代码数据恢复。若停止/状态确认失败或归属不明，相关产品/仪器证据/原test替换与数据恢复必须BLOCKED保留，不能与仍可能写入的仪器竞争；记录独立stop错误与主业务错误，安全独立的设置/保护/保活及只读取证仍可继续，不能吞错或把业务FAIL改绿。正常返回路径不额外停进程、不新增重跑；预算耗尽未启动路径不误停原test。一次attempt不变，不重试业务。不要为绿删证据/清状态/放宽断言。

仅离线针对真实边界验证：超时且停止确认成功后才恢复；停止/确认失败阻断依赖动作而独立恢复继续；未启动不停止；原始业务超时不被stop/恢复错误覆盖。可隔离mock边界执行真实函数，不ADB、不导入带入口副作用模块，复用已有充分证据不全套重跑。输出lane-b/instrument-stop/REPORT.md、ready.json、必要patch/原件SHA和原始离线日志≤2MiB。清楚说明实际停止确认依据与局限，不能以报告替设备证据。Root核关键delta后才决定设备批，不自行开跑，不轮询空转。

你不是独自工作，不改A文件/D/current。所有私库含Claude/Cowork/DSH/.codex/memories及自动记忆、Git暂存提交push发布、云/SFTP/买号、正式库桌面HOLD实体手机、存量删移压迁、关闭重启杀VM共享ADB其他进程继续禁止；当前也禁所有实际ADB/安装/设备/host主入口/attempt。保留1800业务+至少1200恢复、71/820/原test完整代码数据/strictnightno/VM运行合同，旧J1冻结。最后单独一行：这是任务121-v1 B-仪器停止门禁。
````
