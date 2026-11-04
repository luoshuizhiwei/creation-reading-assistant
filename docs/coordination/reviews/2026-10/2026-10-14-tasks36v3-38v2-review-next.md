# 任务36-v3/38-v2实核：保留修复，补齐接缝和安全恢复

## 任务编号/版本
2026-10-14 09:02香港时间。36-v3/38-v2已交付2/2，完整主机闭环尚未通过；下一36-v4、38-v3互斥并行。Root继续只核查规划，无自主实施或设备任务。

## 输入指纹
沿用上一轮已核Root-v2源码1019及36-v1双APK/冻结，不重复构建或全源码测试。执行者输入核查保留，Root本轮核验新主机工具并记录其实际文件SHA于verification.json；D HEAD仍e39a78da9c62f02614207cffb36969a32492eaea、暂存空。原产物核验身份见[上轮报告](2026-10-14-tasks36v2-38v1-review-next.md)。

## 所有权与改动
36-v4只运行/harvest/映射持久化/总消费和准入schema接收；38-v3只基线/准入/备份/恢复/安全编排及schema产出。各自E新版本，不改原交付、Android或对方模块。Root只核查自有E与D全局文档。两项核查后才唯一owner合流新工具冻结；当前无设备执行者。

## 环境
Windows Python3.13；所有外部设备argv由mock接收，未真实ADB/设备/Gradle。原测试以模块载入，ROOT/TMP定向改到Root自有E核查目录，不覆盖外部原日志、不删除原资产。外部模型实际菜单未核，wrapper不等于真实底层模型。

## 执行与结果
36原测试实跑29/29 exit0，38原测试实跑15/15 exit0。36前轮错误phase/marker/行冻结/真实C文案/B结果协议等已修，运行层已有注入命令链；38空字段基线及失败inventory拒绝、pull回读、数据回写与设置恢复可保留。

Root独立实核阻断：
1. 36实际invoke成功报告有mapping，但不产生consume要求的mappings.json；原测试总PASS前手工拼该索引。真实invoke→consume闭环未接通，需自动持久化并由真实入口消费，不让测试补接线。
2. 38 CLI admit输出admission/problems，缺serial/run_id，36 load_admission因此明确拒绝。共同schema需要两owner各自实现，Root后续合流；不要求用户传递。
3. 38 baseline在pm path、forward/reverse、dumpsys全部mock命令失败时仍admission_ok=true，把APK查不到当absent，并且check_baseline_admission放行。protected_roots入参未使用。新编排只基线检查，没有原v1的freeze/APK/manifest完整准入，退化未保留。CLI仍硬编码旧qahost单包，实际冻结包为qahostj1persistv2且必须保护原APK对。
4. 38完整备份成功后，原包清空、非空原forward、URI/state变化，恢复只报缺失；无原APK安装、mapping重建、URI/state恢复命令。只remove-all映射，且会波及非本serial/global forward；无所有者/范围筛选。非DB文件在单独forensics可传入，但orchestrate未传backup_files，因此正常shared_prefs在编排后缺备份。恢复cp/chmod无原owner/context/mtime恢复，mock常量mtime掩盖真实回写影响。
5. 创建物实际probe命令失败rc1被当absent，仍restored=true；部分读取/映射清除/dumpsys失败且stdout为空会与空原值匹配成功，receipts还硬编码rc0。必须区分失败和不存在并核每个外部命令。
6. 取证本地/设备暂存按basename扁平，多个目录/包同名会碰撞；stage未用run_id，restore临时路径也非run专属。只比较DB组，APK原件备份/原触碰根完整账仍未实现。不得以本轮15测试扩大为安全完整。

## 原始证据
[实际测试和独立反例](<E:/creation-reading-assistant/tasks36v3-38v2-codex-review-20261014/verification.json>)；[核查脚本](<E:/creation-reading-assistant/tasks36v3-38v2-codex-review-20261014/verify.py>)；[36原测试重跑](<E:/creation-reading-assistant/tasks36v3-38v2-codex-review-20261014/36-tests.log>)；[38原测试重跑](<E:/creation-reading-assistant/tasks36v3-38v2-codex-review-20261014/38-tests.log>)。原REPORT只读。

## 未覆盖项
设备全NOT_RUN、D/F NOT_PROVEN、X/实体/MIUI/发布不扩大。旧APK冻结有效不表示新工具冻结或device_ready。全产品分母未知，不能算全产品进度。

## 恢复与副作用
无实际设备/数据操作，无D产品/Git/发布，无私库/正式库/损坏仓库/桌面HOLD访问；无子代理、外部消息、节点切换、定时任务。Root测试仅自有E模拟材料。

## 交付状态与下一步
本批交付2/2，完整主机闭环验收0/2，前述正确修复限定保留。下一36-v4输出E:/creation-reading-assistant/task36-joint-runtime-bind-freeze-v4，重点实际CLI持久化和接缝，DeepSeek普通可用档即可，粗估30–50分钟；38-v3输出E:/creation-reading-assistant/task38-device-admission-restore-closure-v3，重点既有安全合同闭环，建议GLM5.3或DeepSeek较强推理档换视角，粗估60–100分钟。以当前实际菜单为准，不外派GPT/Claude。两线立即并行，无构建/设备；不新增重复审计。两项完成Root核后单一owner整合工具/新冻结，再安排限定MuMu。不能以未知恢复能力允许破坏性测试；确实不可恢复则准确阻断具体动作/范围。
