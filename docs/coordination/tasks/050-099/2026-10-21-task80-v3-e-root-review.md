# 80-v3-E Root核验（2026-10-21）

任务编号/版本：80-v3-E-Root。交付状态：serial绑定与导入修正可复用；本次设备主验收失败，未执行仪器。全产品分母未知。

## 输入指纹、所有权与改动、环境
Root负责本报告、current页首及独立核验日志，不改E实现、共享producer、历史输入或APK。检查未发现匹配task80_v3/complete_recovery的活动Python/PowerShell执行者。唯一构建工作区仍为E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，Gradle home D:/develop/env/gradle；本轮构建0、设备命令0。修后serial模块实际SHA为3c2c6701c4a096a3ff9f24b0d8f8dc99f5a75d929eacef68294d910186f8bb8f。

## 执行与结果、原始证据
复用E/v3/REPORT.md及offline-result中的14项离线证据、5项mutation反例、11项设备只读设置探测；它们不代表业务旅程通过，不重跑未变的A/B/C或编译。
原始attempt为20261021-144736-7a1cc4：entry worker导入real_runner失败，安装0、仪器0、导出新建/删除0；唯一attempt已经消耗。修正发生在attempt之后，不追认为原运行通过。
Root定向核验修后真实entry/recovery/keep三个worker：分别由全新Python进程执行，显式继承已绑定ADB，传入不存在的spec；3/3均越过实际导入到FileNotFoundError，没有ModuleNotFoundError。退出码1是预期的缺spec失败，只证明bootstrap，不证明任何设备动作。日志及结构化结果在E:/creation-reading-assistant/task80-export-content-v1/root-review-80-v3-E/verification.json及三个stderr文件。

## 冻结证据审计
00-input-bytes.json记录的freeze、binding、ready、provenance四份原字节快照4/4 SHA匹配，但其规范路径当前SHA均已改变（4/4）。原attempt快照仍可追溯；不得宣称规范冻结原件保持不变，也不得以事后修正的ready覆盖旧失败结论。下一批必须使用新的独立版本路径，freeze与恢复继续入口一致后再执行；本报告不补造旧模块源字节证据。

## 恢复与副作用、未覆盖项
原始recovery-completion.json整体ok=false保留：restore_settings成功，final_protection_check成功（71私有/820外部，diffs=[]），keep_running_verify错误委托给不支持该step的task56 worker。独立supplementary证据在14:59:04记录serial 127.0.0.1:16417在线、boot=1、两个宿主PID及端口alive，支持当时实例保持运行，不把整体失败改写成全绿，不代表当前实时状态。
这些均为MuMu模拟器证据。E报告“真机”用语不采纳。本轮没有实体手机/MIUI验收。旧FAIL/日志和恢复汇总均保留。

## 交付状态与下一步、停止条件
下一批拟80-v4-F，由唯一集成/设备owner闭环：先修正恢复续接绑定（不能直接使用仍固定C/v2的D/root-r1），保持原真实进程排除自身修复；保活经task75 keep worker；检查实际子进程接线后封存新freeze/binding/ready，再执行一个main/run/attempt，probe_verify→material→orphan→source_state→md，失败停止后续并完成必要恢复。全部APK复用，无需Gradle。已有“都运行，不用问我了”授权覆盖该验收和仅本run确证新建、已保全原字节且size/SHA一致的精确导出收尾，不扩展到旧资产或无限重试。实例允许使用/启动，不关闭不重启，结束保持运行。
本批入口、恢复契约、绑定与设备互相依赖，同一owner串行；没有发现可独立增加价值的产品差量，不凑并行核验。两个自动化保持PAUSED，不发送外部消息，不Git/发布/桌面/云/存量清理。未知身份、冻结漂移、预算不足、未知资产或恢复失败即阻塞；不得同批修后第二次main。
Root新增约数KiB日志与报告，实际字节见verification.json同目录的root-delivery-size.json；必要证据保留，无APK/工程/缓存复制。
