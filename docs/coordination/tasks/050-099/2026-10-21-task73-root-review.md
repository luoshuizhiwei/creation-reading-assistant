# 73-v1及Root-r1：离线阶段收口，设备保持暂停

## 任务编号/版本
73-v1-A/B、73-root-r1，2026-10-21香港时间。并行只读核生命周期和Android测试合流；Root发现单一快照缺项后在本轮直接委派最小修复、独立复核并最终重绑，没有再派用户往返小修。设备保持不动。

## 输入指纹
host ceb60dd1a24455efbfcbcac4143a058b2d30e45b6f42659653ec7034925ae79a；test cb7faf93503f53c4d2ddd277967cfbcb428385668b247732c5e97642ece55cec，Root实算匹配。产品源码未变，host未重建。
生命周期改前65faa45a0827c504ffbc56bbdaabec0cc795ce2d8c219abe7e502b5949a9a8db，Root-r1改后673d98f865c23587ff514f1bcfee598b3280260a1a0e36980eede3c530e9e9ec。
最终真实入口root-r1/candidate/task73_v1_runner.py：2199d0516e72fbafe9bc8100ff5e15d8c25f5af3358900c90165b23062b365c2；freeze7f15d8fde3b89d014fe7bd4c179620ded632b0a00dfaae8fcc131dc225aff556；binding4a8a04d40eadaa26864bb51109079e7399d766df8b2c323d69a399415e10f656。runner仅改新冻结/绑定文件名，entry/shutdownworker保持原字节，动态导入实际修复lifecycle。

## 所有权与改动
原A交付、B交付、旧冻结/FAIL、共享工具及Android产品/测试源码Root未改。Root-r1独立小候选：worker只拥有lifecycle和定向tests/evidence，Root拥有runner/entry/shutdownworker副本与最终freeze/binding；独立审阅者只读复核。没有覆盖别人文件。相关task73/59 Python/Java活动查询为空，不据此认领设备。

## 环境
59工作区、D:/develop/env/gradle与既有依赖复用。Root不构建/设备/端口或进程身份探测。比例核验技能与系统调试技能只用于本次具体缺陷；原充分证据不全量重跑。

## 执行与结果
A/B交付采纳：原pytest XML57项、errors/failures/skipped均0；三项ViewModel状态测试已真实JVM运行，原XML3/0/0/0；真实test打包BUILD SUCCESSFUL。helper把中央点击/等待/返回异常纳入原始failure，MD/取材退出成功记录已在必需断言后；独立比对B最终4文件和工作区SHA一致，5项Compose/Room留instrumentation，未执行。
73原两项生命周期反例已修：unknown不当closed、ownership前置到关机请求之前，原红3/3及绿3/3记录对应生产函数；真实子进程拒绝路径原证据保留复用。
本轮发现快照非空但缺本轮index/记录或身份字段时仍被放行，已直接修复：显式current_info要求非空dict、本轮index完整记录及4项身份值匹配；current_info=None的静态调用语义保留，worker自身仍拒绝未提供快照。
定向红测原生产函数11项中9失败，Root-r1生产函数+真实shutdown_worker._run受控dp计数绿11/11。拒绝场景manager请求0，完整身份正常路径请求1。这是受控函数级离线证据，不是新增真实OS子进程或设备验收。独立复核未见本补丁回归，采纳；未再跑57项充分回归。
Root实际调用新入口verify_binding、verify_freeze、audit_import_closure、check_budget_plan全部ok。预算5100=3180阶段+1680恢复+240裕量。初次importlib核验漏加candidate sys.path而停止，改成普通脚本对应搜索路径后成功；不是生产入口失败。

## 原始证据
E:/creation-reading-assistant/task73-offline-closeout-v1/A/REPORT、evidence/pytest-73-v1-A.xml、red/green-73证据、logs/01与02；B/REPORT/ready/src/patch；integration/ready/STATUS与JVM XML。Root原交付核验root-review/checks.json。
Root-r1：candidate实际入口/模块、tests/test_task73_snapshot_gate.py、evidence/red-before-fix.log、green-snapshot-gate.log、task73-lifecycle.patch、verification/final-checks.json、ready-73-root-r1.json。原路径引用，不复制APK、工程或历史证据。

## 恢复与副作用
本轮零设备命令、零MuMu动作、Root零构建，无新恢复义务。71最初运行实例归属缺口仍未解决；用户“不确定，先保持设备不动”持续有效，不因离线修复自动解除。
原73交付又有工件保全缺口：重建覆盖72 test004a36c1…9e23，未先留同SHA副本。不能以源码/冻结记录替代旧二进制，不声称旧资产均完整保全；旧72冻结不可按原路径执行。不扫描磁盘找回、不重建补造历史。当前73 test已有integration/artifacts单份副本，Root-r1不重建、不覆盖APK。
Root-r1统计在本报告写入前实际181516字节，均为必要小工具/差量/日志/JSON；无新增工程/缓存目录或APK复制，未删除迁移既有资产。原73交付约10.15MiB按其报告口径保留，非Root新增。

## 未覆盖项
material/orphan/source_state/md四旅程仍未有新设备结果；5项Compose/Room未执行；3项JVM通过不能替代它们。71真实MD位移2612/UI2410及充分历史63有限链/D/F继续复用，不据局部推整体通过。实体手机/MIUI未验，全产品分母未知。

## 交付状态与下一步
当前授权的离线实现、必要JVM/回归、test构建、来源/最终绑定和交接已收口，最终以root-r1候选为准。原73未补快照缺项的入口仅留历史，不作为最新执行入口。没有新证据支持再造离线开发/审查任务来凑并行；暂停设备也不能以Mock替代验收。保持工件冻结，等用户明确恢复设备授权后再给单次完整批次；即使获准，遇到归属不明的运行实例仍拒绝接管。
automation-2/3保持PAUSED，不自动外部投递、Git、发布、桌面、正式库、HOLD、私有记忆或云部署。

## 后续设备授权更新（2026-10-21）

用户明确重新允许设备工作，并选择“允许使用运行中实例，但不关闭重启”。该最新授权替代此前全面设备暂停；可只读核实并使用项目隔离运行实例，不能关闭、重启或把运行态恢复成关闭态。Root随后仅执行MuMuManager info -v all与adb devices -l，均rc0；实例0/1均is_android_started=false、is_process_started=false，ADB列表为空，未执行状态修改。已进一步询问是否允许启动实例1并在验收后保持运行，尚待答复，不自动将使用运行中实例扩大为启动许可。现有runner含关闭/guest reboot恢复路径，禁止原样运行；后续批次须将不关机/不重启落实到实际生产恢复入口及最终运行态合同。automation-2/3仍PAUSED，不自动外部投递。

## 启动授权已明确（2026-10-21）

用户进一步选择：“允许启动，验收后保持运行”。因此74-v1范围为fresh核实后启动或接入项目隔离实例1，执行一次验收并按合同恢复数据/APK/设置，实例结束保持运行。全程禁止MuMu shutdown、reboot/reboot -p、emu kill、taskkill/Stop-Process或其他变相关闭/重启，包括失败和超时路径；这次不再采用恢复成关闭态的旧合同。现有Root-r1仅作为适配基线，不得原样执行其shutdown恢复。适配需在真实入口离线证明零关机/重启请求，再重绑；不改Android产品、不重建已采纳APK、不放松数据/消费者门禁。仅一次attempt，失败停止后续并完成必要数据恢复，保全现场且保持实例运行。此授权不恢复自动化、不授权向Claude等外部应用自动发送消息。
