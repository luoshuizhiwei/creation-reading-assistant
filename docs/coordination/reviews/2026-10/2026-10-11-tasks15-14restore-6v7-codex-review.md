# 任务15、14恢复与6-v7核查及下一批

核对：2026-10-11 22:29（+0800）。Root负责核查/全局文档，D产品只读；HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。最新范围取代上一轮待派状态，不重派已交付原版本。

## 任务编号/版本、输入指纹、所有权与改动

任务编号/版本：15-v1、14-v1恢复补账、6-v7。交付状态与下一步：15源码/JVM限定通过；14保护后账与当前关闭状态已核；6安全子门待小修。下一16-v1/6-v8/17-v1/18-v1并行。所有权：Root只维护本核查、当前入口与编号索引；各候选原件不回灌D。

## 环境、执行与结果及原始证据

15-v1源码1000/1000实算SHA匹配，manifest SHA 21986d41b19998b316217e0b15187509a444fc7c9a00a73ad37157ea25226f7d，tree 9584e40049f2d71682320d7bbc95115c294119592430b764684e29dcfdfbaf21。相对14改7/增1/删0。会话实例、即时观察、退出实时列表坐标兜底、先保存再disarm均接入生产入口，pending/marker/临时禁存保护保留。Root XML实读reader96类766/0，新guard11/0；旧语义变异体11/4失败有行为红灯。pristine仅编译红不能当行为根因；Compose时序不由纯JVM证明。全量目录实读352类3499/1失败，原14隔离36/1同WebDav失败日志存在，故不称全量绿；Settings偶发归因仍仅限定观察。

14补账final-ledger SHA d303a7fbe21759239ae998de973e7fddc719c2e8bb2bcab55e027c32d742a786；Root独立比较原baseline与ledger-after全部71持久文件size/SHA/ns-mtime及8设置，零差异；DB三件套/原APK对/端口/shell/创建物以原始快照、57采集命令和73比较命令核对，非零pm path为absent预期，有包列表互证。补账时MuMu被外部MAA占用，报告如实未恢复关闭状态；Root22:29独立只读info实例1 is_android_started=false/is_process_started=false、adb devices空、无MAA/MuMu/Headless进程，当前关闭状态已恢复。保留20:36历史失败，不把后来关闭归因为14动作。31/31原始合同仅属14旧冻结；报告披露起跑阶段外部工具活动，下一设备批次必须排除并发输入，不能称旧批独占成立。

6-v7 SUMS88/88 SHA匹配；65patch与v6逐件相同、26supersedes完整存在匹配；默认只读核验exit0；D15WIP当前/d_pre一致。8条行包含仅行级证据，7条SUPERSEDED共128hunks、292新增行未在12-v2逐字节出现，语义保留待审，规模或符号存在不当等价证明。新发现BoundarySelfTest行123-124无条件递归删除预存_rp_target，材料安全子门不通过；当前scratch空不消除此复用风险。

Root核查未运行Gradle/设备写入。只读子审查意外运行15 verify_input.py，重写其logs/input-verify.json为同值结果；仅此E证据副作用已登记，未改产品。后续不运行会写原件的审计脚本。Root证据：[verification.json](<E:/creation-reading-assistant/tasks15-14restore-6v7-codex-review-20261011/verification.json>)、occupancy-current.json；外部REPORT/raw/XML保留。

## 恢复与副作用、未覆盖项、里程碑复盘与下一任务

保留15源码/JVM与14原31充分证据，退出重复恢复任务；设备新输入不能复用旧31为新版本通过。下一4提示词现在独立并行：16-v1唯一构建/设备owner，复制15和14验收工件、补真实守卫仪器测试、强冻结双APK、仅受影响TXT12/clamp8/J1导航4及新增守卫场景；D2旧7只复用历史限定，不写新版本31/31。6-v8只修自检清理并负测，不重制65。17-v1只审5个导入/EPUB WIP文件逐hunk，18-v1只审SyncRepository/WebDavBackup两文件逐hunk；两者只读D/12/6材料、只写各自E审查目录，无Gradle设备代码。三个离线任务不占设备或联合构建。

16须开工核设备外部占用，启动后重新核；遇活跃外部会话不关闭/接管，保全离线冻结并报告DEVICE_BLOCKED。无恢复旧定时任务/外部发消息/切节点。每包卸载前私有源→自有暂存→本地size/SHA，恢复71分母须按当轮真实清单重算、DB/WAL/SHM/原APK对/8设置/端口/shell/创建物/实例关闭全核。下一源码修改仅16自有E受影响测试与同功能最小修复，输入改变重新绑定/定向验证，不擅自扩功能。

17/18允许给等价/缺失/需修复判断及精确hunk建议，不授权消除用户资产或回灌D。6-v8不代17/18作语义批准。全产品分母未核；X整机清零/Back专属完成归因/队列排空/初始new save/实体手机MIUI/正式签名发布仍未覆盖。无D产品回灌、暂存、提交、push、PR、发布授权。
