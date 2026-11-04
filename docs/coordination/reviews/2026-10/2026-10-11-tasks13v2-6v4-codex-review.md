# 任务13-v2、6-v4核查与Root定向补正

核对：2026-10-11 11:33 +08:00。任务编号/版本：13-v2+Root、6-v4+Root。交付状态与下一步：两个离线返修范围已关闭，使用Root独立E补正版；设备未验。下一14-v1唯一构建/冻结/31与恢复，6-v5独立E准备冻结999源码的集成差量，两项并行。6-v5候选准备不作为设备通过或D应用授权。

## 输入指纹、所有权与改动

Root核13-v2证据22/22及6-v4证据11/11 SHA匹配。12-v2产品清单仍7085bdcf181cc324887ab7caa1247a2d29323b0ee17050f3de88c8def58b3669；最终44验收清单仍8ffdc53280987e7953f34a05e0782d6c151edfa50a6ae3c00946adbeb9c8bbcd。复用上一已核999源码/157证据与39/1053 JVM，不重复广测。旧6集合56、新差量3、交集2，54+2+1=57成立。

13原v2遗漏命令/cwd与配置的硬绑定，init-config默认引用不存在的bat。Root在freeze-provenance-task13-root-v2-20261011补命令/cwd核对、wrap启动前拒绝覆写、显式必填构建命令/cwd、清单root与候选root一致性。正常无record/SKIP拒绝逻辑保留，不靠配置exit0。下一owner需自建专属wrapper及新不存在产物路径，并从自身候选重绑清单，不构建原12输入。

6原v4默认路径门已改善，但安全测试SEC05/06仍固定目录Force复用/递归删除。Root在integration-payload-task6-root-v4-20261011补每次UUID自有目录及安全测试；仅移除自身marker/空目录，无递归Force；自有junction保留并记录，不清理用户资产。默认verify脚本与v4原件字节一致。原报告与原件保留，不覆盖。

## 环境、执行与结果

HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。外部13-v2/6-v4报告实际模型均标DeepSeek/Antigravity；不以建议GLM替代实际标识。独立review运行13原31/31，Root补正版35/35离线通过（31原有+4新增）；py_compile通过。6默认14/14，Root安全六类拒绝成立，JSON另含junction保留断言，共7记录。原v4夹具通过声明不代替安全脚本正确性，后续用Root版本。

## 原始证据

[Root机械核验](<E:/creation-reading-assistant/tasks13v2-6v4-codex-review-20261011/verification.json>)与[next-input-binding](<E:/creation-reading-assistant/tasks13v2-6v4-codex-review-20261011/next-input-binding.json>)。13 Root报告、TOOL_SHA256与logs/run-selftest.log；6 Root报告、SHA256SUMS及evidence/verification.log、security-suite.log、security-rejection-cases.json均位于各独立Root E目录，Root逐项SHA核对结果见JSON。

## 恢复与副作用

无Android产品修改、Gradle/新APK、安装/卸载、设备写入或Git变更；adb devices当前无在线设备，MuMuNxMain主程序存在。6自有UUID junction保留于Root fixtures，不能递归扫描/清理跨reparse资产。原13/6输入保持原件。D源码、既有WIP、原损坏仓库、桌面/HOLD及私有库边界不变。

## 未覆盖项与里程碑复盘

离线工具通过不等于真实Gradle来源或MuMu通过。14必须绑定自身源码/验收/双APK/真实命令日志，优先复测clamp_20加载→Loaded→测量、切源重进，保留pending/marker合同，再闭合31。所有卸载前取证及完整恢复不能省略；旧31仍18 PASS/1 FAIL/12 NOT_RUN。X整机清零、Back专属完成归因、队列排空、初始新save、实体手机/MIUI、正式签名/发布未验，全产品分母未核。

为加速调整材料安排：此前6不提前刷新可变输入；目前12-v2已冻结且源码/JVM退出，6-v5可并行准备该不可变999候选差量，标CANDIDATE_ONLY/DEVICE_ACCEPTED=false，不读取14可变候选、不等待长轮询。若14修新路径，Root在验收后仅核最终差量并决定材料有效性，避免重做旧57材料。14与6所有权互斥，D产品回灌/暂存/提交/push/PR/发布均未授权。
