# E盘项目占用盘点与最小交付规则

## 任务编号/版本
2026-10-16存储整改，不新建产品实施任务；48-v4/49-v4原功能范围与编号不变。本页记录清理前盘点与方案；用户随后授权的A批已执行，见[清理完成记录](2026-10-16-e-cleanup-a-delivery.md)。B批及其他目录未清理，原体积保留为扫描快照。

## 输入指纹
当前产品状态仍见[48–50 v3核验](../../reviews/2026-10/2026-10-16-tasks48-50-v3-review-next.md)。本轮新盘点见[完整体积清单](../../2026-10-16-e-space-inventory.json)、[A批精确候选](../../2026-10-16-e-cleanup-batch-a.json)、[B批待审构建目录](../../2026-10-16-e-build-candidates.json)。体积按逻辑文件字节，不是承诺可释放的磁盘分配量；扫描期间活跃任务可能继续产生文件。只扫描E项目根与项目备份根，未扫描其他软件/私人库，跳过19个重解析点，枚举错误0。

## 所有权与改动
Root只维护项目AGENTS、交接与小型盘点报告。已将最小差量交付与单一构建区规则写入仓库AGENTS.md；没有修改外部执行者的候选或中断进程。重要恢复备份、DB/WAL/SHM、源码/WIP、唯一证据、最终冻结APK不作缓存处理。

## 环境
扫描快照E盘空闲60.95 GiB（6.54%），项目根315.04 GiB/约497万文件，项目恢复备份7.57 GiB。约457个一级任务/材料目录。全项目枚举400.82秒；A批十个缓存子目录单独核对41.64秒，错误0。报告为小型D文档，不向E新复制材料。

## 执行与结果
发现60个不互相嵌套的gradle-home合计167.31 GiB；其中部分除缓存还混入android工程，不能按名字整个删除。排除gradle-home内部后有178个Android app/build目录合计53.52 GiB，含测试报告/生成APK，不能直接批量清空。当前工作根、48/49-v4及桌面保留资产排除后169个旧app/build合计51.67 GiB仅列待审，尚未形成删除许可。上述各组与项目315 GiB总量有包含关系，不相加。

### A批：推荐优先确认的旧任务Gradle缓存
精确10项合计33.008 GiB；仅删除下列caches子目录，不删除gradle-home父目录，不动同级android、gradle.properties、wrapper及其他内容。内容为可再生成的依赖/转换/构建缓存，不含按此清单界定的产品源码/证据。旧脚本如仍指定这些home，需改用保留的共享home，或重新下载/转换依赖；首次旧环境重建会变慢。清理前需再次核当前进程占用、解析绝对目标及清单身份，不结束其他进程。

| 精确目录（均在E:/creation-reading-assistant下） | GiB |
|---|---:|
| d2-relink-identity-fix-v4/_cache/gradle-home/caches | 4.82 |
| d2-relink-identity-fix-v3/_cache/gradle-home/caches | 3.24 |
| d2-relink-identity-fix-v2/_cache/gradle-home/caches | 3.19 |
| d2-relink-identity-fix-v1/_cache/gradle-home/caches | 3.11 |
| j1-crossbook-temporary-seam-v3/gradle-home/caches | 3.18 |
| j1-crossbook-temporary-entry-v1/android/gradle-home/caches | 3.15 |
| txt-reentry-fix-v2/gradle-home/caches | 3.09 |
| txt-reentry-fix-v3/gradle-home/caches | 3.09 |
| txt-reentry-fix-v4/gradle-home/caches | 3.04 |
| txt-reentry-persist-fix-v1/gradle-home/caches | 3.08 |

保留当前已知共用 E:/creation-reading-assistant/txt-persist-codex-completion-20261006/gradle-home（整体约5.99 GiB），以及D既有依赖缓存，不擅自迁移。A批已按后续授权完成10/10，实测净增32.97 GiB；不是所有旧缓存的清空许可。

### B批：旧编译产物，需先精确保留证据
169目录51.67 GiB只是上限候选，不能整批直接删除。可再生成的intermediates/generated/tmp/kotlin等优先；保留唯一测试XML/原始日志/冻结APK及恢复材料，逐项分离后另给精确清单。不以“旧版本”推断源码可删，不为腾空间删除当前48-v4、49-v4或共同工作根。

### 保留
- cra-db2约20.84 GiB，实为数据库及WAL/SHM等取证材料，不当缓存删除。
- creation-reading-assistant-backups约7.57 GiB，原恢复备份保留。
- root-runtime-device-20261015约6.62 GiB，包含当前基线与设备恢复证据，保留。
- 已采纳50-v3、当前48/49-v4、源码/WIP、配置、唯一原始证据和最终冻结APK保留。
- task44桌面目录为保留资产，本轮不因当前排除桌面开发就删除。

## 原始证据
三份本页链接JSON包含完整路径/体积/数量/扫描错误与保留说明。只读脚本在系统临时目录cra-e-space-audit-20261016.py与cra-e-cleanup-plan-20261016.py，不是删除脚本，没有rm/Remove-Item/移动操作。

## 未覆盖项
未证明全部60份Gradle home逐字节重复，未扫描其他E盘应用，未核所有旧build的唯一证据，不承诺逻辑体积等于最终释放空间。没有清理回收站、共享缓存、设备或MuMu。

## 恢复与副作用
原盘点阶段无删除；后续A批按用户授权完成，执行回执与最终复核另存。A批属于可重建缓存，恢复方式是使用保留缓存或重新获取依赖，不新复制一份同体积备份来腾空间；代码与证据保留，无移动或设备操作。

## 交付状态与下一步
盘点、A批授权执行与提示词规则整改完成，本批停止，不扩展B批。不创建产品新任务，不重启已在做的48-v4或已交付49-v4。下一轮先核现有任务成果，再由Root选用现有一份候选作为唯一构建区，不逐版本新增完整树。

后续执行者默认交付overlay/改动源码、最小patch、输入/输出SHA、REPORT及必要日志/XML。禁止复制build/.gradle/.kotlin/node_modules、旧证据/备份/镜像/完整Gradle home；并行执行者只改互斥差量，完整编译由唯一owner串行复用现有环境。无完整编译则如实标待Root验证，不为交付复制工程。已建目录不擅自删，已运行命令不擅自中断，已完成任务不重做。
