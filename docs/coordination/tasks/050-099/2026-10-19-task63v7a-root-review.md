# 63-v7-A Root核验与最小修正

## 任务编号/版本
63-v7-A核验；候选63-v7-root-r1，2026-10-19。A已交付，设备联合FAIL；B此前已采纳，不重复核验。

## 输入指纹
A入口SHA：3230f621f05cd1374290d40beb7cf8e8815b5847963b7458161762a7eb60e906。
A设备运行绑定SHA：b834709fda91f0c80528527e843fd22b0d669bf25e14cb3c57973f93272a0b6e；交付后116绑定SHA：e987736d6e7aab404b5c8637a66fb2dc21d35d90bfaebad9165086e283bc2feb，不能混称同一执行版本。
Room模块设备运行33076dbb6c1649d2c418029f184b8e7aeda7273a937567963240801cc7df3427 → A交付后a2627639ef510fa74aa8fefb790c94232f9bba38d4ba059aafcfa3b396c1d10d → Root候选3bec8979325c933c23da1b8c4df78054b78ee4d5ceea64581dcd784602ad445d。
Root入口SHA：62f525d0d1f7ce2ada76a12cce300a762c223a9204dd00aa6cf00f8206702b04。
Root120绑定SHA：4b37faee2068016a0d51bf9f1312574d588c1cd4973f875df3396f3421aa5f5c。
原冻结SHA31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc；host14e573ab964a122c3d4fbb83770423d70bebd57028eca26c455a442da1a282aa；test d5cdd05d8709b6bef22d3e3b0fae9e703a50105a4adb0a2f4f3539f16cb21d7a。原构建证据复用，不新编译。

## 所有权与改动
Root只写E任务/root-review/v7-r1及必要D交接；原A/B、共享producer、Android源码及历史FAIL不改。新增薄入口重用v7编排，显式指定Root Room与绑定；未复制工程/缓存/APK。
修正真实list_room_dirs生成脚本：空glob从`[ -e "$d" ] && printf`改为if分支，保留set -e及cache目录必需探测；没有用无条件成功掩盖错误。PID采集异常纳入失败；adb调用10秒timeout、线程join12秒；证据账本写失败与原阶段错误以ExceptionGroup同时保留。其余接缝不变。

## 环境
本轮只在本地Python及Git Bash运行离线检查，设备命令0、编译0。单一构建工作区仍E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle；唯一构建owner Root按锁协调。自动化2/3保持PAUSED，未改其配置。

## 执行与结果
A新run CRA63_DE_JOINT_20261019_V7，attempt_v7_20261019_173305：ABC/clamp/SAF PASS，Room在仪器启动前`ls -d`探测rc1失败，DE/总消费NOT_RUN。不能据此宣称logcat可信通道已在设备通过。
Root使用真实生产list_room_dirs生成的脚本经本地Git Bash执行，复现A交付后for循环无匹配仍rc1；原FakeADB对for固定返回0，未覆盖真实shell语义。红证据empty-probe-red.json保留。Root修正后原13项模拟回归PASS；新增5项针对性检查PASS（真实shell空/非空、cache失败、证据写失败保留双错误、PID异常阻塞）。这是本地POSIX语义与模拟验证，不是Android shell/设备证据。
真实task53_phase_driver_v4._load_module_by_path加载Root入口；Room分发哨兵确认调用Root模块，120/120绑定实算一致。运行时clamp720、总预算3960、恢复预留1200、Room300均保持。
B沿用已核：v6同run17源指纹一致，离线DE53项accepted、总消费7/7PASS；没有新设备DE/整链通过。

## 原始证据
E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/v7-r1/：empty-probe-red.json、room-regression-green.log、evidence/room-evidence-regression.json、root-boundaries.log、candidate-checks.json、device-recovery-checks.json、两份.diff；测试夹具留存。
A原报告与ready：v7/room-fix/REPORT-63-v7-A.md、ready-63-v7-A.json；原attempt及设备恢复原档保留。

## 未覆盖项
Root候选设备attempt=0；Room仪器/logcat+PID+时间窗+目录差集+DB实证未在新候选真实运行；DE与总消费需在同一新run继续。全产品分母未知，不报全产品百分比，不称实体手机/MIUI通过。

## 恢复与副作用
Root独立核A运行保护清单：71私有与820外部路径/size/SHA前后一致；tmp归档115文件集合/size/SHA及tarSHA一致，final_protection ok/diffs空，自动恢复5步均ok。两实例关闭有host-final-state记录。
差异：A报告称最后adb为空，但host-final-state仍记录本run serial offline，adb-devices.txt是此前在线快照，未找到所引空列表证据。本轮未发设备命令，不将空列表记为已核；下批启动前只针对该连接核对，不关闭既存ADB server，不影响其他设备。
Root目录当前156279字节（约153KiB，后续ready计数略增），均差量、原始检查和保留夹具；无既有资产删除/迁移/压缩。A报告本次attempt约85.81MiB及v7约576KiB，仅引用未复制。

## 交付状态与下一步
实现候选及离线检查通过；模拟器整链未通过。只剩一个有价值的同设备门禁闭环，下一批不重派B或制造平行审查。任务63-v7-root-r1-device-1使用Root新入口/120绑定及原冻结一次fresh run，Room通过后同run DE→总消费→完整恢复。入场指纹/所有权/预算失败即不启动；运行失败保留原始证据并恢复，不现场修码重绑或第二attempt。当前Root未启动该批。
