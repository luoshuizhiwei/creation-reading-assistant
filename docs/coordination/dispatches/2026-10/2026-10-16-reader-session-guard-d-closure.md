# Reader session guard D 修复收尾

更新时间：2026-10-16 09:00 +08:00。本轮已完成限定修复与 MuMu 仪器核验并停止，等待用户下一条命令。旧夜间 automation-2 已暂停；不派新批次。

## 修复结果

- 恢复后的真实列表坐标变化不再单独等同主动阅读。真实拖动按本次输入起点判断、释放和快退同步取末坐标；真实 UserInput 垂直消费经现有 callbacks 接线。明确 TXT 跳转、搜索、音量键和自动阅读继续授权保存。临时/分页/pending/搜索聚焦保护保留；没有新增像素阈值。
- 重测量边界来自同书真实生产布局回调，root 轮询确认时间独立记录；前后非空正文 marker、PID/bid/seq 因果、合法坐标、生产裁决与完整12列比较均保留。
- r12 的墙钟等待未驱动 Compose ui-test 1.9.0 的 TestCoroutineScheduler。r13 只改采样循环，推进实际测试主时钟并记录起止，继续等待真实生产事件，不调用 guard/注入位置/合成裁决。Foundation 实际打包为1.7.6，测试库为1.9.0；版本按APK元数据及对应本地字节码核实。

## 验证与证据

| 范围 | 结果 | 原始证据 |
|---|---|---|
| 原生定向 JVM | 48/48，6 XML 零失败/错误 | `night-v12/jvm-final-xml/`、`targeted-jvm-r3.log` |
| 纯生产 guard 反例 | 旧3/3失败，新14/14通过；不当设备证明 | `night-v12/pure-r2-red/`、`pure-r2-green/` |
| 主机恢复/执行链 | v12 79/79；v13身份与清单3/3 | `night-v12/host-regression.log`、`night-v13/host-identity-regression.log` |
| 阅读旅程 | v12六项Native及逐项严格全通过，内层恢复true | `device/resume-v12/abc/` |
| 主动输入 | v12快速退出/小幅位移/临时隔离/真实语义ScrollBy 4/4 | `device/resume-v12/probes/user-input-regression/` |
| D真实重测量 | v13 1/1；真实边界→有效位移→生产guard阻断，无保存派发 | `device/resume-v13/probes/d-expanded-moderate/` |

D包：`E:\creation-reading-assistant\root-runtime-device-20261015\device\resume-v13\probes\d-expanded-moderate\remeasure_6be3f23c__real_viewport_remeasure.json`，SHA `9814cac9bc67a5e4155ce2df380fb7a50a29a0c145166f324677a207e2039523`。重测量期间12列 `rowChangedColumns=[]`、identitySufficient=true、guardVerdict=guard_blocked_no_dispatch；退出后续读offset仍8019。观察窗测试主时钟 5216→5328ms，裁决窗 5328→5776ms。此证据是受控 Compose 测试时钟下的 MuMu 仪器结果，不推断自然墙钟时序或实体手机/MIUI通过。

## 输入与交付

- 唯一工作区：`E:\creation-reading-assistant\task48-temporary-return-freeze-v4\android`；最终1033源码，source清单SHA `38a0ab28ff71091713a36e75db0ad213b7fe383f7fdf243758f6a28c92f757c5`。
- 最终freeze：`E:\creation-reading-assistant\root-runtime-device-20261015\freeze-v13\attempt1\freeze.json`，SHA `9ff778e78b837063d747b05ee9e9a951f04f63cbe2218cb5d999afa8cf67a06a`，wrap、独立verify与结束前fresh verify均PASS。
- host APK：`b0db82b0e7d7efbb59bafc9c5dd4c59d886334584058a6c082e0a579a55eeea3`；test APK：`ec7617e6da1fc9d7ba564b40f592a5e0ff8021ff525f9b7d90e57b37ec6b2d7f`。v13产品APK与v12逐字节相同，唯一源差异是D仪器探针；未受影响的v12阅读/主动输入/JVM证据按 `night-v13/input-reuse-receipt.json` 复用，不重复执行。
- 相对v11最小交付：`E:\creation-reading-assistant\root-runtime-device-20261015\night-v13\delivery`，19份变更/新增源码共469223字节，含patch和前后SHA。历史v11/v12源差量ZIP与immutable v9引用可重建旧输入；没有复制完整工程/Gradle缓存。E当前剩余约92.98 GiB，没有扩大资产清理。

## 数据恢复与停止点

v12与v13均已通过完整外层恢复：原71私有文件、820外部文件、DB/WAL/SHM、原APK对、约定系统设置/映射核同；本次新增两包取证后卸载恢复absent，本轮tmp已归档核对后精确清理，原616tmp保持。v13 `final-protection-result.json` 与 `host-final-state.json` 均ok=true，结束后live manager再次核实例0/1均关闭，adb无在线设备。失败的旧试次不覆盖；恢复及取证原件留E。

本轮“恢复后未主动移动时的重测量误存”修复退出条件已满足。整体 `DE.strict` 联验仍缺原合同的统一bundle，v12总consumer仍FAIL；不能将本轮单项D、四项输入或六项阅读通过写成统一联合验收通过。下一批统一联验、发布签名/R8、实体手机/MIUI及X整机中断原范围均未因本轮被宣布完成。全产品分母未核，不报全产品百分比。

阶段复核：保留 Android 稳定自用方向；本轮只解决现有保存/证据缺陷，未扩桌面或新功能。后续集成/设备仍需单一owner及同版身份门禁，等待用户新命令。D产品未回灌、未暂存/提交/push/发布；结束HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。
