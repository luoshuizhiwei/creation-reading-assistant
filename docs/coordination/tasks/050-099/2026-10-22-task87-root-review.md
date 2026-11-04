# 87-v1 Root核验与88批分工

## 任务编号/版本
87-v1-Root，2026-10-22。采纳本轮MD普通使用整链（仅本任务定义的业务、保护、恢复、保活），不扩大为全产品或V0五合同全部退出。

## 输入指纹
交付根`E:\creation-reading-assistant\task87-md-selfuse-accept-v1`。Root实核binding中的8个输入SHA全部匹配：APK、Flow、Flow binding、严格保护adapter、准入、恢复、b_ui、b2_db；launcher、binding原件、REPORT共3项与ready也匹配。

- 当前APK：`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android\app\build\outputs\apk\debug\app-debug.apk`，SHA256 `33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df`，52,763,881B。
- 实际Flow：`E:\creation-reading-assistant\task86-exit-progress-v1\A\parser-root-r1\candidate\b2_selfuse.py`，SHA256 `d04b8ccacf10cf999ef948023cd9ec62a99c8c9810e51de9f6c9d65279a2ea17`。
- 87 launcher：`scripts\t87_run.py`，SHA256 `74369ef1072a21093c65c9fa648b40bc197f56acbc5d38846f9598b6980657de`。
- 本轮binding：`evidence\binding.json`，SHA256 `a9437f385bdf3f1bbd7231d91e95796a37dc5e7fe99afe0ae8eb555f8bb2cb88`。
- 准入`b2_admission.py`：`ab65c22950eae3ea6615e02c0a0527e7a68411fcf40bdff973caa6676e71fe9f`；恢复`b2_recover.py`：`beb3052392657258ac44801469f93ef2778ab8ebc405c4aeff33c90a3676d34d`。

## 所有权与改动、环境
87唯一owner只交launcher与必要原始证据，无产品源码改动或新构建。Root检查未发现命令行包含task87/t87的活动python/adb执行者；这不构成下批设备接管许可或实时设备状态证明。

实际run `20261022-111417-3cff82`，11:14:18—11:22:06 +0800；现场binding serial `127.0.0.1:16416`、boot `52c20782-05f2-40fb-89cf-281e7f6b448e`，实例1 pid25736/headless44944。以上均为本run历史身份，下批须重新核实。无实例关闭重启。唯一构建工作区、Gradle home和锁沿86不变。

## 执行与结果
Root读取flow.json逐bookId原始行，B/C/D/E/F均为book `10ba629a-4325-4f35-b6b6-1979d110ca2a`、offset961、progress21.9006385803223、completion=reading。B/F canonical locator完全一致；重开/冷重开同第10段marker；force-stop rc0、pid空、冷启动MAIN成功。

Root解析after-exit-MD.xml确认书架；reopen-MD.xml与cold-reopen-MD.xml确认正文marker及顶部Chrome组合21%节点，位置[112,67][483,103]。实际Flow按toInt整数截断匹配DB21.9006，非容差放宽。console-run.log最终业务/恢复/总ok均true。

报告称一次入口/attempt，与独占run-id及现有原始日志一致；不声称以现有材料穷尽证明所有外部命令历史。run-summary顶层serial缺失/null，但binding、flow、各阶段和设备回执一致绑定serial。保留此报告字段缺口，不为补字段重跑已通过MD。

## 恢复与副作用
Root直接读取本run initial/final两份readonly-protection-proof：consumer.ok=true、diffs=[]、比较71私有/820外部、实际capture71/820、DB组匹配、reasons=[]。

恢复原件`evidence\recovery\recovery.json`：RECOVERED、blockers=[]；本批包卸载成功且absent、两份本批材料按既有合同收尾、11项设置无差异、night严格no、相关包/Android-data/Download比较无差异，保活boot与准入一致。07-keepalive原始回执通过。本次无新恢复欠账；不将历史回执冒充当前实时末态。

空间：87原交付4,411,654B，严格capture2,640,078B，合计7,051,732B（约6.73MiB），低于8MiB预算。Root另保留不足1KiB失败路径反例JSON；无整工程/缓存/APK复制，无存量删除。

## 原始证据
- 交付根REPORT.md、ready.json、evidence/binding.json、evidence/console-run.log、evidence/run-summary.json。
- `evidence\run\20261022-111417-3cff82\flow.json`及after-exit/reopen/cold-reopen-MD.xml，逐阶段DB raw_tail。
- `evidence\logs\check.json`、01至07阶段原始日志及本run logcat。
- 保护根`E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\20261022-111417-3cff82\{initial,final}\readonly-protection-proof.json`。
- Root失败反例：`E:\creation-reading-assistant\task87-md-selfuse-accept-v1\root-review\early-return-red.json`。

## 新发现的入口风险与未覆盖项
87 launcher的cmd_run在try内对initial/admission/install失败直接return _finish，finally只处理logcat，后面的phase_recovery/final/keepalive不会执行。实际准入入口可能已推送材料后才报告失败，安装失败也可能留下部分状态，故这是未来复用阻塞，不能因本次成功而忽略。

Root直接导入真实87 launcher，注入阶段外部边界并mock全部Path写入，分别令admission和install返回失败：退出码均5，调用序列分别止于[initial,admission]和[initial,admission,install]，recovery_called均false。原JSON保留；这是离线反例，无设备动作、不否定87已完成恢复。

源码还存在build_binding把非零探测结果概括为absent等弱判定；本run另有准入/回执支持实际缺席，但不能单独把这些判断当作未来删除/覆盖许可。88-A在同一实际入口必要范围内处理：命令执行失败与可信缺席须区分，设备身份/读取不明即拒绝写入；不扩成通用框架。

不同布局/新host首帧页首规范化尚未覆盖，但目前未证实新产品缺陷，不自动派一套设备矩阵。EPUB、D/F、数据保全及取材旧证据与当前产品增量的适用关系应在阶段退出时定向梳理；不能由TXT/MD两条通过推出全部V0通过。

## 交付状态与下一步
87实现接线与MD普通使用模拟器整链采纳；86新版TXT通过继续复用。离线7项解析、产品6项单元、必要构建输入未变，不复跑。全产品分母未知，无实体手机/MIUI通过结论。

下一88-A与88-B真正并行：A唯一写新`E:\creation-reading-assistant\task88-failure-closeout-v1\A`，从87实际入口派生最小失败收尾修复，红绿定向验证、真实动态绑定、报告闭环；B只读既有V0合同及最新证据，唯一写`E:\creation-reading-assistant\task88-v0-exit-v1\B`，提交五合同适用关系、可复用项和最小剩余阻塞/退出建议。两者不共写产品、launcher或公共入口；Root独占集成与current。

B不重新盘点所有历史/源码、不复跑验证，不因87报告建议就默认追加字号/转屏矩阵；只对本次保存链增量影响或真实证据缺口列必要下一步。若没有未完成产品阻塞，可建议稳定自用范围退出，不制造新功能。

## 停止条件
88本批为明确的入口离线修复与阶段退出判断，不启动设备/构建：前者失败收尾必须先修，后者决定是否确有新设备必要性。此为当前依赖顺序，不是永久设备禁令或撤销用户授权。保护/恢复合同不降级，旧FAIL不覆盖。身份/owner或输入漂移先阻塞；两个自动化保持PAUSED。无D产品回灌、Git、发布、外部自动消息、桌面、正式库、HOLD/私库/云或存量清理。
