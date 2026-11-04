# Root 自主实施与隔离 MuMu 验证

## 任务编号/版本

CRA-ROOT-RUNTIME-DEVICE-20261015，部分完成，设备已安全恢复。用户要求 Root 接手剩余工作，直到另行要求外部提示词；设备范围为登记的隔离 MuMu 实例1。

## 输入指纹

- D HEAD：e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；既有 WIP 保留。
- 起点：45-v1 的1020源码、63验收工件与原双APK；不回灌 D 产品代码。
- 当前 Native v4-r2：1020源码，tree fbc2022773cc1bc4328d052b5f28a2cbdadcc3d8efee2d6ed410ebac32e4d0ec。
- [强冻结](<E:/creation-reading-assistant/root-runtime-device-20261015/freeze-v4-r2/freeze.json>) SHA 62d5ecdcf66f74446f3038e54368ffbd71bc8cdd642d151e9b3591b7dd9c3461。
- 宿主复用 SHA 7ab63a0b430b36d461329275d2d6824e5df7bd1a73ada8556a68f11b9777c91b；测试APK SHA 51aa71cd4987deb4a52e92f38188d160845c20aba23e125c35dfa9174aa4f263。
- 每次实际运行使用独立工具清单；[v4实际运行清单](<E:/creation-reading-assistant/root-runtime-device-20261015/evidence/tool-freeze-v4-actual-run.json>)含29项，入口实际核验通过。

## 所有权与改动

Root 独占设备与集成。实现、构建和原始证据均在 [E盘工作根](<E:/creation-reading-assistant/root-runtime-device-20261015>)。Native 到 v4 只改 androidTest，宿主与 main/config 未改；主要修复 SAF 测试归属凭证、受限存储下 dump 路径、Provider 删除后授权自动撤销的严格清理观察及 shell 命令闭合。主机修复实际生产链接线、完整清单门禁、shell 脚本传输、暂存副本权限、Windows 短备份路径、保留时区与纳秒的修改时间恢复、真实窗口组件提取。

## 环境

MuMu 实例1，现场 serial 127.0.0.1:16416，SDK32；原先关闭，实例0保持关闭。不是实体手机或 MIUI 验收。原 plain qahost/.test 与初始 absent 的隔离 j1/.test 分开保护。

## 执行与结果

- v4-r2 唯一持锁 `assembleQahostAndroidTest`、强冻结 wrap 和独立 verify 均 PASS；宿主明确复用。
- ABC-v4启动前主机快照的定向回归21/21通过；这是离线接线/反例证据，不冒充设备结果。Root随后增加基线须停进程/恢复前停进程保护，该最新增量尚未跑完生产CLI回归，不沿用旧绿。
- 同版 MuMu 清理仪器12/12通过。
- 同版真实 SAF 创建导出、结束进程后的冷读、删除及权限恢复3/3通过。
- 同版分享选择器打开、观察、取消1/1通过；没有发送给接收应用。
- 六项 ABC 实际仪器4PASS/2FAIL：A1和三个B通过；A2未按真实控件层级退出阅读器，C的missing夹具被首页就绪检查挡住，没有到要测的Reader加载失败。整体消费及内层恢复未通过，不计联合通过。
- 进程取证确认有接口缺陷：旧回执取instrumentation启动前PID，可能为旧进程或空；独立采样补丁4个离线用例通过，但未合入实际生产入口、未做设备正向验证。
- A2/C与自建EPUB副本清理的三文件夹具修复已放独立E目录。Root补查发现预存拒绝失败后teardown仍可能误删同内容预存文件，已补明确“初始不存在才有清理权”的布尔保护，并将missing夹具改为拒绝预存；尚未构建，不计通过。
- 短末项实时ready→UI→receipt握手已写出独立草稿，子代理在14:13左右因账户额度中断；未完成最终审查/编译/设备验证。原错误提示建议14:59后可重试，不把该时间当保证。


## 原始证据

- [SAF v4](<E:/creation-reading-assistant/root-runtime-device-20261015/device/saf-final-v4/results.json>)、[分享 v4](<E:/creation-reading-assistant/root-runtime-device-20261015/device/share-final-v4/results.json>)、[12项仪器](<E:/creation-reading-assistant/root-runtime-device-20261015/device/saf-cleanup-v4-units.txt>)。
- [实际ABC v4日志](<E:/creation-reading-assistant/root-runtime-device-20261015/logs/abc-v4.log>)，逐方法原始 instrumentation、设备文件映射及消费结论在 device/abc-v4/harvest。
- 原失败运行与各版源码、构建日志保留；不以最终绿覆盖原反例。

## 未覆盖项

短末项的 S/R/S2/退出/冷读仍需当前 PID 内的真实 UI 握手；D 无手势重测量与 F 旧 UI 回调迟到派发尚未闭环。F 的 IO 派发乱序88项 JVM 和当前相同源码证据复用。X 整机中断、实体手机/MIUI、正式签名升级/R8实际运行、发布、桌面均不在本轮通过范围。Android全产品分母未核定。

## 恢复与副作用

本轮外层设备恢复已完成；与ABC-v4内层失败恢复分开记账，旧失败不改写为通过。

- 原plain两包71私有文件的size/SHA/ns-mtime/mode/owner/context全部匹配，数据库组含DB/WAL/SHM指纹一致；原APK对和820外部原文件匹配。
- 两个Root恢复暂存目录另有172新增文件（78+94）。先逐项size/SHA验证E盘tar，再只删除精确Root目录；整个/data/local/tmp重库存616文件与基线完全一致。其他原外部文件在删除前已核同，删除范围与其不交叠，不为同一输入再全量扫描。
- j1/.test在逐包卸载前取证：测试包私有0文件且APK归档；宿主99文件完整归档并逐SHA核同、包含数据库相关文件。两APK分别保存且匹配冻结身份；卸载均Success。
- 本设备`pm path`对不存在包返回rc1；工具原helper将其unknown，未把失败误作absence。另用rc0的`pm list packages --user 0`精确无包项及私有目录不存在，独立证明恢复初始absent；记录保留unknown原回执及补证。
- 8项约定设置、shell身份、映射和Home组件已核；MuMu实例1已通过Manager关闭，实例0原样关闭。ADB残留offline条目不代表模拟器还运行。

证据：[完整保护与限定清理后账](<E:/creation-reading-assistant/root-runtime-device-20261015/device/final-protection-result-closed-files.json>)、[逐包卸载前证据](<E:/creation-reading-assistant/root-runtime-device-20261015/device/final-uninstall-forensics/result.json>)、[关闭状态](<E:/creation-reading-assistant/root-runtime-device-20261015/device/host-final-state.json>)。原1.18GB备份与失败内层stage已在E保留。

## 交付状态与下一步

部分完成，仍由Root接手，不派外部提示词；当前没有仍运行的构建/设备任务。子代理额度中断，半成品先保全，不能直接安装或算验收完成。

接续顺序：

1. 审查并完成PID运行窗口采样生产接线及mock/离线兼容；最新Root quiescence前置与finally增量一起回归；不覆盖Root integration文件。
2. 合流三文件Task34夹具补丁和经审核的短末项live握手到唯一新v5源码；单owner构建、冻结并更新固定APK/工具身份。v4冻结保持只读，不在验收中改源码。
3. 重新核登记实例/执行占用与输入指纹后启用实例1；复用充分且输入未变的原保护证据，只补所需现场基线，运行受影响ABC/短末项并完成恢复。D/F另保留最小可达性核验，不能用人工旧callback或一次无事件采样放行。

独立补丁目录：`E:/creation-reading-assistant/root-runtime-pid-fix-20261015`、Root工作根下`task34-fixture-fix-v1`、`E:/creation-reading-assistant/root-clamp-live-seam-20261015`。无D产品回灌、暂存、提交、push、PR或发布许可。
