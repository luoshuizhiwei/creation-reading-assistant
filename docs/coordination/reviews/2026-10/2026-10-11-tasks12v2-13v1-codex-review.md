# 任务12-v2、13-v1与6-v4交付核查

核对：2026-10-11 08:46 +08:00。任务编号/版本：12-v2、13-v1、6-v4。交付状态与下一步：12源码/JVM限定通过，设备待验；13离线工具已交付但构建来源门禁返修为13-v2；6-v4约定目录/REPORT未找到，沿用同版本补交，不重复已有成果。下一13-v2与6-v4独立E并行，均不构建最终APK/不使用设备；14唯一合流/设备批次等待13-v2核查通过。6不作设备前置。

## 输入指纹、所有权与改动

Root实算12-v2源码999/999、证据157/157 SHA匹配。源码清单SHA 7085bdcf181cc324887ab7caa1247a2d29323b0ee17050f3de88c8def58b3669，Android tree df0e379ab9da80ae06b4580fbfa74882c748539730670619d6c30757bdac1082。8生产修改、3测试修改、1测试新增，共12路径，加载器共享及就绪观察接缝属于已授权功能范围。

验收采用12-v1最终44工件，Root逐项SHA匹配；清单SHA 8ffdc53280987e7953f34a05e0782d6c151edfa50a6ae3c00946adbeb9c8bbcd。13-v1的73项证据SHA/size全部匹配，工具SHA c6cb8fb37bf2e9ba3b37f91182bcc2c3136340347aed508d6e21fea57a5a3114。6-v4仅查约定目录与E项目根相关名称，未找到；不能断言不存在任何其他位置交付，也不能记为验收完成。未改输入原件。

## 环境、执行与结果

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。Root机械核对XML：12红线39项4失败，绿色39项0失败；134相关类1053项0失败/错误/跳过。复用原始充分证据，不再构建或全量测试。独立源码审查未发现确定性提前clamp反例：可见单元须Loaded，Loading/Failed/空集合不释放pending；revision/readiness进快照等待，共享loader；生命周期标记测试成立。

Compose实际加载状态与二次测量的顺序、snapshotFlow唤醒、切源重进仍需限定MuMu确认。不能称设备唯一根因已证，也不能把999候选当已通过旧31。旧31结果仍为18 PASS/1 FAIL/12 NOT_RUN。

13独立执行selftest为20/20、exit0；历史freeze为35 PASS/3 SKIP/0 FAIL，即PASS_WITH_SKIPS。源码/清单/身份漂移辅助检查有价值，但标准构建门未退出：snapshot记录既有APK，freeze强制SHA不变，原路径正常重建会拒绝（工具398、567-570行）；新独立输出路径可规避此冲突但必须明确合同。构建退出码来自config、日志仅字符串/mtime，未证明实际命令结果与APK绑定；verify只从freeze重建prebuild而不独立读原快照/校验SHA（735-745行）。300秒freshness容差不能当构建先后证明。13-v2需以可执行包装流程保留原快照、真实process exit、日志和产物、严格PASS/无SKIP，补正向重建与反例。README19与实际20应同步。

## 原始证据

[Root核对JSON](<E:/creation-reading-assistant/tasks12v2-13v1-codex-review-20261011/verification.json>)、verify.py、verify13.py及[next-input-binding](<E:/creation-reading-assistant/tasks12v2-13v1-codex-review-20261011/next-input-binding.json>)。12原始XML位于joint-31-closure-task12-v2/evidence；13原始日志/工具/测试位于freeze-provenance-task13-v1。所有权：Root只维护本报告、current/index；13-v2只写独立冻结工具目录；6-v4只写自身材料目录，公共源码/设备无并行写者。

## 恢复与副作用

本轮Root未构建、安装、卸载或启动/关闭设备。当前仅MuMuNxMain主程序与adb server存在，adb devices无在线设备；不能据主程序推断Android实例已运行/正在验收。没有发现本任务Gradle/测试Python执行进程，外部聊天是否空闲仍须执行者开工核占用，不据进程表消除全部会话冲突。设备后账复用上一报告限定，未重新全量读设备持久文件。D产品/Git/发布未授权，既有WIP保留。

## 未覆盖项与里程碑复盘

目标仍为稳定阅读位置恢复与相关联合合同。12实现退出，保留加载到真实测量/切源运行时风险；13不能靠新增文档冒充构建来源，下一返修必须工具正向闭环；6只补材料安全，不提前刷新尚未设备退出的产品补丁。两条可执行线真实独立，14等待冻结门，不扩展到全产品功能。全产品分母未核；X整机清零、Back专属完成归因、队列排空、初始新save、实体手机/MIUI及正式签名/发布不覆盖。
