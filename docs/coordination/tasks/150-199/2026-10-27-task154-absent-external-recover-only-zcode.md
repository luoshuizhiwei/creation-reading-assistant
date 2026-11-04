# 任务154-v1：152原设备批 recover-only，缺席external的精确保全

Root按实际原log纠正152报告：三业务失败是主线程约束异常，不是重复complete。本任务不处理业务错误、不再instrument。唯一设备/host恢复owner=A。B仅隔离源码差量，不改本任务输入/候选/APK。

基线：E:/creation-reading-assistant/task152-exit-page-single-run-v1/ready.json 和其evidence-index、host152.py、inputs-152.json仅只读。原scope E:/creation-reading-assistant/task146-quiesce-recovery-offline-v1/task152-T141BV1-20261027-023205。原业务4项1PASS3FAIL、summary整体false永久保留；CE/DE已稳定保全，external listing rc3不等于已证明不存在，产品KEPT_BLOCKED。原test/settings/protection独立恢复证据充分直接复用，不能把旧summary改绿。原报告generatedAt03:40为预写时间，非本次真实完成时钟。

目标：先核实际归档接缝和原始rc/stdout/stderr，区分external整根确实不存在与权限/命令失败。只有新鲜同serial/boot/user0、本批安装所有权及instrument-stop/无writer门全部满足，完整原始命令回执明确证明根不存在，才可将其精确表示为ABSENT（不伪造空tar/目录/文件），保持存在根CE/DE逐项完整源前后清单与本地tar size/SHA/目录集合核同。拒绝permission denied、异常rc、不确定身份、符号链接等，保留KEEP_PRODUCT并独立安全恢复。不要把所有rc3映射成缺席，不降低既有稳定保全或父安全门。

所有权/实现：只新E:/creation-reading-assistant/task154-absent-external-recovery-v1/，最小新恢复接缝引用固定旧实现；不改旧host/父adapter/inputs/raw/summary。新逻辑先小型strict external mock定向验证：确实缺席可表示、权限失败拒绝、存在根归档不回归；未知外部命令抛错。测试实际新恢复函数，不复制谓词或内部替换冒充链。原始cause/次错保留并进入recovery总裁决。

离线必要门通过后，本轮直接recover-only：fresh设备身份/实际包内容SHA和旧批所有权核同，确认仪器不运行；必要只读探测和原字节保全，产品若仍活遵守已有明确单批quiesce许可/原门，禁止无保全stop。满足完整保全后才调用父精确正常卸载一次；原test/settings/protection依父安全门独立恢复核验，Download .exmu-cfg1.data历史差异如实，不造文件。若仍无法证明缺席/恢复，停止依赖卸载并完整报告具体阻塞，不循环试探。禁止新main/attempt/instrument/业务复跑/Gradle/APK安装或构建、VM重启关闭/kill共享ADB/其他实例实体。

交付：REPORT、最小patch/新源、前后SHA、必要原log/proof、独立恢复summary和ready.json在新根。只有实际恢复绿且产品缺席才声明本批恢复完成；原业务仍红，不称项目完成。准确空间估计与分类：source128KiB，offline raw+fake128KiB，设备归档/证据最多64MiB；复制前核free，复用旧不复制工程/APK/cache，不删移旧资产。25分钟从实际UI发送计，不重置；安全恢复不中断。先自查实际globals/出口与新缺席分支确实接入，再交付，不用口头绿。禁Git提交发布/云/正式库/私库/自动记忆。最终总结末行：这是任务154-v1。