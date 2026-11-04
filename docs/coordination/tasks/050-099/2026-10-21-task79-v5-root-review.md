# 79-v5-Root 单次设备验收与恢复交接（2026-10-21）

任务编号/版本：79-v5-Root。owner为Codex Root。用户明确授权关闭实例1游戏后验收，并随后明确批准仅删除已保全的本轮导出文件以恢复外部清单。

## 输入指纹、所有权与改动、环境
唯一59工作区/Gradle home/锁不变。原v4源码41870a3a…588a、test8f601226…2f53、hostceb60dd1…e79a均复用，编译0/APK复制0。仅E integration/v5任务身份与台账绑定差量，runner94cfb76ec0c4fc57c426397e5948edd6dd686d5c364ac50620c8a1f11091051e。六类离线入场检查通过；未重复既有全量JVM/消费者。设备前关闭游戏一次am force-stop并HOME，未卸载游戏/清游戏数据，VM不关不重启。

## 执行与结果、原始证据
唯一run20261021-111254-e1d7c7，11:12:54–11:18:03，exit1。probe_verify真实JUnit OK(1 test)。Material FAIL：导出文档必须含本轮摘录，源码1534行；后三旅程NOT_RUN。业务分母0PASS/1FAIL/3NOT_RUN。原tar内本轮MAT65V3-9f76e6的picker/destination/asset回执支持DocumentsUI包名、Downloads导航、唯一文件名、CreateDocument返回及URI读取，不能称完整导出通过。未现场修码/重绑/第二试。
完整报告/ready/原字节/Root验证位于E:/creation-reading-assistant/task79-picker-runtime-v1/C/root-v5/；原始run位于同根attempts/20261021-111254-e1d7c7。E REPORT记载全部准确SHA及准备阶段非设备错误。

## 恢复与副作用
原runner final_protection_check拒绝新增Downloads文件，保留原99-summary及恢复错误。与仪器“保留文档、不删除”的冻结合同冲突；不能静默PASS。用户精确批准后按本轮expectedDisplayName/runId、原基线无该资产证明，先保全再删唯一文件，无递归处理。
重要传输证据：初次shell捕获572B，校验设备SHA失败阻止删除；exec-out取得实际555B/SHA 1a7b0fefbe9c5a24770fd64e904eaa33571b9611247c0036417e1f6c3926c4f0，核设备SHA一致后只删除该文件。E同时保留传输副本、真实原字节、首次失败及最终删除回执。
使用既有绑定恢复worker，仅续接final_protection_check，420秒硬deadline内114.4秒PASS。Root比原基线与续接final：protected_files/external_files/db_groups/apk_pair/settings/uri_permissions/pkg_states、forward/reverse/serial全部一致，71私有/820外部diffs空；四冻结原件/快照及双tarSHA匹配。11:25:17 fresh确认device/boot1/原boot16a3…95d7/nightno，候选两包absent、游戏pidof空。实例保持运行，launch/shutdown/reboot请求0；原仪器FAIL及初次恢复FAIL仍保留。恢复只指合同范围，不称整个虚拟磁盘/提供者数据库回滚。

## 未覆盖项、空间、交付状态与下一步、停止条件
本批新增6740850B（ready写入前，统计C/root-v5、integration/v5及本run；不含旧批次），没有工程/缓存/APK复制。唯一删除为用户明确批准的本轮导出文件，无存量资产清理。
下一步离线定位“导出缺少摘录”，以及文档保留与外部基线拒绝新增的合同接缝；先用已保全555B原字节和原tar，不能放宽断言/静默忽略外部新增。当前停止新增设备批次；无必要恢复欠账。两个自动化保持PAUSED。无外部消息/Git/D产品回灌/桌面/云/正式书库/私有库。全产品分母未知，不称实体手机/MIUI通过。
