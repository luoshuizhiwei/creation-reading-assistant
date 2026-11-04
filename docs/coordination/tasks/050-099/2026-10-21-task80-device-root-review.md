# 80-v2-Root-device 单次验收交接（2026-10-21）

任务编号/版本：80-v2-Root-device。用户明确“都运行，不用问我了”，本批一次验收及已证新建导出原字节保全后精确收尾已获授权，同范围不重复询问；不扩大为无限同批重试或旧资产删除许可。Root唯一设备owner。

## 输入指纹、所有权与改动、环境
C hostdc736485…1d95/testb8f615ce…0500、runner ea51a306…e18a及D/Root-r1实际绑定核同，构建0、APK复制0、源码/冻结不改。初始fresh实例0/1均关闭；按既有启动许可及本次执行授权Root仅launch实例1一次，未关闭重启任何实例。新PID50716/headless16916，created_timestamp1777974446694936相同，当前serial127.0.0.1:16417、新boot8f595ca2-91d7-4198-b0fe-7e7527d0f3db；不沿用历史16416/PID/boot。启动前为何关闭不推断。前置launcher历史PID/端口校验阻塞后只读纠正，main未调用；startup-readiness记录launch后boot1。实例0不启动。

## 执行与结果、原始证据
唯一run20261021-142021-8246eb，14:20:21–14:24:12、exit1。baseline门禁拒绝，install_attempted=false/phase_started=false，probe及四旅程全NOT_RUN（业务0PASS/0FAIL/4NOT_RUN）。台账一次main，Root启动请求1、runner内部启动0分开统计，无shutdown/reboot。新C产品逻辑未在设备执行，不能称产品测试失败。
根因已证：device_protection.py初始/最终检查重放original-settings.json中的value["argv"]，其-s参数固定127.0.0.1:16416；当前实际16417。inventory当前serial采集正常，但历史命令返回空值导致拒绝。Root未改共享producer/历史基线，未现场改码重绑第二试。
完整原始日志和Root报告/ready位于E:/creation-reading-assistant/task80-export-content-v1/root-device-80v2/；原始attempt位于同根attempts/20261021-142021-8246eb。

## 恢复与副作用
本run初始/结束protected_files/external_files/db_groups/apk_pair/settings/uri_permissions/pkg_states全部相同；未安装、未仪器、无新导出、无设备文件删除，因此D精确导出收尾不适用。
原最终保护仍FAIL（同一旧serial探针原因），原件不改。Root以同一历史只读设置命令仅替换选定ADB及当前serial16417重新采集，11项size/density/rotation/user_rotation/night/stay_awake/timeout/volume/shell_uid/forward/reverse均与约定输出一致，rc0。此为Root直接状态证据，不将原严格入口结果冒充PASS。71私有/820外部保护清单与原APK/DB/权限未变，未发现需要继续恢复的实际状态差异。
14:28:20 fresh-final：device/boot1/本次新boot未变、候选两包absent，实例1保持运行。四冻结原件及本run input-bytes快照SHA全部核同。原入场/恢复错误与summary/ledger保留。

## 未覆盖项、空间、交付状态与下一步、停止条件
单次设备动作已执行并收口为BLOCKED_AT_BASELINE_HISTORICAL_SERIAL_REPLAY。现有新APK的独立选区和四旅程均未验。Root无新构建/源码修改，新增3357109B（ready前，root-device-80v2及本run），无工程/缓存/APK复制，无存量清理。
下一步针对候选设备保护调用修复历史设置argv到本轮serial的绑定：只替换目标serial、不改期望值，不修改共享producer/原基线，覆盖变端口和错误设备拒绝；由同一owner完成最小修复/定向验证/新绑定，复用现有APK，不重做A/B/C/D验证。当前同批一次main已用完，不重复运行；下一明确批次需先接收该修复，不盲目重试。授权范围、归属、恢复状态不明停止依赖动作。两个自动化保持PAUSED，无Git/外部消息/D产品回灌/桌面/云。全产品分母未知，无实体手机/MIUI通过。
