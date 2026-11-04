# 任务12-v1与6-v3核查、并行返修

核对：2026-10-11。任务编号/版本：12-v1、6-v3。交付状态与下一步：12合流/构建材料已交付，31为18 PASS/1 FAIL/12 NOT_RUN，联合返修；6材料对旧输入限定成立，计数/夹具路径保护待修。下一12-v2产品加载/恢复接缝和定向JVM、13-v1冻结身份绑定工具、6-v4既有材料分类/夹具安全并行；全部独立E输出，无设备/最终APK。12与13核查后再派唯一联合构建/设备owner；6不是设备前置，不阻塞该门。无D产品/Git/发布授权。

## 输入指纹、所有权与改动

Root实算998源码、44验收全部SHA一致，55项交付证据SHA匹配。最终产品清单SHA3579a0966a9c58ab56fdaf6b7ef560a3e753b849e2ba638b159b078291d4ebee；验收清单SHA8ffdc53280987e7953f34a05e0782d6c151edfa50a6ae3c00946adbeb9c8bbcd。双APK实算匹配：host a13747a0ff63efc8e14b514cfa96a2f79a5cc2efc8c75b7fe00fbac50a8f1f23，test404e11051fdd990b00b93948cf00e8fa72f99c5c27ce44b48983696f4a077d02。原apk-freeze SHA4a0d177c6e433ea2f98aad0fbd25eaa288b1f2127c7b395b17f2100692855da2。

源变化3改1增、验收2改，限定阅读恢复/生命周期保存与clamp回调/夹具身份。APK实际字节成立不等于全部来源字段正确：apk-freeze的source/acceptance路径仍指merge清单，acceptance_tree为旧3e4f...而最终为6e988...，build_log指早期构建。13须区分原件、勘误和可证构建来源，不伪造无法复原的构建前源码快照。最后证据目录平铺，报告raw/items子目录写法也需按实物纠正。

## 环境、执行与结果

隔离MuMu不是实体手机。最终f3 summary为18/1/12；普通TXT12/12和J1四项已通过，clamp_00/10通过，clamp_20 offset0失败，后5clamp和7D2未运行。Root核14个普通/clamp原始OK输出，18定向JVM XML零失败；未重新设备/构建。HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空，既有WIP保留。

独立源码审查确认ON_RESUME会重置生命周期保存标记，未见永久抑制dispose保存；要补三种生命周期回归。Loading的Shimmer占位参与布局，但恢复落位未检查Loaded，支持占位导致提前clamp的假设；诊断未直接记录失败帧loader状态，不能称唯一根因已完全证明。同功能加载就绪穿线已有授权，跨文件/函数参数本身不需要新用户决策。优先现有ScrollUnitContentLoader.stateFor接缝，Loading/Failed不清pending、不把offset0保存；不改验收合同，不用尺寸>0或超时冒充正文就绪。

## 原始证据、恢复与副作用

[Root机械核对](<E:/creation-reading-assistant/tasks12-6-codex-review-20261011/verification.json>)与verify.py记录998/44、55证据、双APK、18JVM、矩阵。owner恢复后账ok=true/problemlist为空，声称71持久文件/原APK对/8设置/映射/shell核同。Root实际计算本地保护DB/WAL/SHM备份size/SHA3/3一致；MuMuManager当前进程/Android均false。Root没有重启设备复跑71项，恢复结论据此限定。之前17轮诊断/多次构建不重做；下一先离线修复与反例，再联合设备唯一owner。

## 任务6限定核查

旧INDEX真实56条，与v3交集2取代，新1条：应54原样保留+2取代+1新增=57。报告53保留和指纹superseded_by_v3=3均错误，需精确路径集合修正。Root按脚本声明pwsh执行默认核验真实14项OK/exit0（独立审查口头11计数不采用）；曾误用Windows PowerShell 5导致UTF8脚本解析错误，已按声明工具重试，不当产品缺陷。

夹具模式接受任意FixtureDir后递归删除replay-work，无路径归一化和任务专属边界，不能称普遍安全。6-v4移除对既有目录的删除/复用，仅新建专属随机子目录；阻断路径穿越和reparse point，不写D。只修材料逻辑，不重新刷新尚将变化的产品源码；v3输入997+旧44保持历史限定，最终新源码刷新等退出后Codex差量一次安排，不每轮重做旧56补丁。

## 未覆盖项、退出条件

DEVICE_ACCEPTED=false；X整机中断、Back专属完成归因、队列排空、初始新save、实体手机/MIUI未覆盖；全产品分母未核。下一三任务所有权互斥：12 Android；13自有冻结勘误/验证工具；6材料脚本/分类。均不设备、不构建最终APK、不修改D或原输入。12/13通过后再唯一合流冻结与clamp+D2/最终31及恢复，不依赖6准备进度。
