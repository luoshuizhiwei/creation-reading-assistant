# 109-v1 Root核验与110方向

## 任务编号/版本、输入指纹

109-v1，唯一run T106A-T109V1-20261024-124125（兼容仪器正则的新编号，非旧106/108）。REPORT ecccbe5e4ba123659889e4fc5b3615187ec458c5c59da1571dc805a8daa94df9(6566B)、ready1d81ee66421ba8c6052ce934f3fd6552746c6a6e64e5b092e8daad28906c51cc(4385B)，host cf7cee34d1c50e0745fec3af7f3ff4da144e8bafaa1ecb68d69c727bc0a7693a(19163B)。产品bf/testa5c9/仪器a255沿原实物保全，零构建/APK复制。

## 所有权与改动、环境

Root D协调/current，ZCode官方Start Plan GLM-5.3 E唯一源码/host/build/device owner。host以108 f219基线3hunk接线；离线实际KotlinRegex/本批id匹配，设备确已越过setUp并产生fixture与21/23/22，故接线修复生效，不扩大为解析矩阵验收。MuMu1非实体手机，下一批fresh核不沿旧serial当授权。

## 执行与结果、原始证据

Root直接读原21/23/22/99：按钮节点1，boundsInRoot/InWindow均0矩形，size198x0，window1080x1920，可见高度0；选2文案存在，立即与2秒选择条仍在，无详情/合并或拒绝提示，两本批行未变。原20秒assertNotNull失败，业务167.754秒，一次attempt已耗，采用/筛选/重开NOT_RUN；collect成功6原件，logcat2040行。原FAIL不改绿。

Root核生产InspirationList.kt371–374 LazyColumn.fillMaxSize位于Column中，后置选择条397–438无预留空间；按钮onClick430先onAction再清selectMode/IDs。实测零高本身构成可操作性缺陷，现有布局测量链与其一致，授权最小空间分配修复。谨慎边界：选择条仍在不单独严格排除onAction同步抛错；本批也未执行真实DAO成功合并，不将报告“状态二/三全排除”放大为所有生产链无缺陷。修后同旅程继续核真实Room和后续采用。

## 恢复与副作用

Root核initial/final原wrapper rc0、consumer71私有/820外部/diffs空、db_groups_match true；原test verified/restored apkSha0d18/inventory/userState逐字段相同；summary recoveryErrors=[]、collect6文件、Download初始179新增0、product UNINSTALLED/blocked空、test恢复。night=no与实例pid运行记录沿原回执，Manager started字段波动不据此称VM关闭。无本批外部资产残留。恢复接受，完整原summary保留ok=false业务失败。

## 未覆盖项、交付状态与下一步

产品真实合并→采用→筛选→重开仍未闭合。目录实际3623165B（REPORT3623058B差107B收尾时点差），build0/APK复制0。1073/3+B1/1等充分证据继续复用，无需重复。106/108/109原FAIL保留。

方向自审：已经有直接零高证据，停止诊断确认批，下一110同owner只修InspirationList LazyColumn占位为weight(1f)并保宽度，给选择条空间，不改合并/数据合同；必要main编译与product/test构建后新输入一次主run。用既有仪器原始bounds核修后正高度及原业务断言，成功可同run核后续采用；若新失败停业务完整恢复后报告，不批内再试。公共build/device串行同owner，没有需另造的独立离线审查；实际目标优先，不扩素材功能/格式/UI矩阵。
