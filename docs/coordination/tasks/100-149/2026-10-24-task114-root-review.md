# 任务114-v1 Root核验

## 任务编号/版本、输入指纹
114-v1；唯一run T112V1-T114V1-20261024-145951。Root 2026-10-24核实际文件，不重跑设备。
仪器366c2f3249a7c7364f874837ae59a42a35841509cdd37453e559539d1a27f311，33825B；host bc7cfe8d7b8c7886077df7b0defa5904d933d1c8d03d79c94057b0717deab133。test 2e63baa60704cf22fe7de73efdf2ebd9a784f8ce2a90a79ab43d884d19b7e17c，10605000B单份实核。

## 所有权与改动、环境
ZCode官方Start Plan GLM-5.3唯一E仪器/host/build/device owner，Root D文档。产品6a复用110，生产零改动。MuMu1模拟器，不是实体手机。114新增删除前置观测和滚动重解析，仍沿用错误any-sibling归属谓词。

## 执行与结果、原始证据
E:/creation-reading-assistant/task114-saved-view-delete-observation-v1。原stdout1 test/1 failure、6.228秒；host132.0146秒，attempt耗尽。03 compile10秒/04 assemble8秒原EXIT_CODE=0；01编译失败保留。
71 Store原件515B/SHA3a6f11fa3f4a15381edb19952edfe2d2e8d2a890dfc948094ad9b8da6a817f41，双方含名；72原件293B/SHA43a849bdb672e1036facd5eb30369787aa613b5a8fca67a68c7cf563f4e556ed，只含目标，对照已删。Root直接核原bytes内容。71两个删除候选449/525、各102x70、位置y1424/1516；rowSiblingName都错误显示对照名。源码402-403 any parent-sibling匹配任意目标，419 indexOfFirst可选错节点，观测归属同样不可靠。
本run实际点击移除了对照，证明这次生产删除操作写入成功。不能据此宣称113历史现场已直接取证、生产删除全链完全正常或排除所有其他缺陷；113失败Store终态已丢，114树也缺失，共享parent具体结构需115离线/运行观测核清。
本run在captureDeleteObservation:457 onRoot printToString因两个root失败，原目标消失断言未执行；不是目标删除通过。原FAIL保持。
REPORT f99d41ae0a005adb509bc90735b8560a6f3cb91deef430528af00e9f24776892，7719B；ready e8353454d554f05e268e31e13521c5913599132421b11de9e24565a23c07a174，6217B。实物14193535B/test10605000B/其余3588535B，报告差13936B收尾时点差，不重跑。交付尚以补丁脚本为主，115需补114必要最小patch/源码准确交接，不复制工程。

## 未覆盖项
73/74观测、原70目标删除断言、80重开、90实体关系不变NOT_RUN。imeActive仅InputMethodManager.isActive，不等于键盘可见，报告“IME打开”超出该API证据。

## 恢复与副作用
Root核原test verified/restored apkSha256、inventory、userState逐字段同。initial/final rc0/oktrue，71私有820外部，diffs=[]，db_groups_match=true。summary recoveryErrors=[]、collect13、Download初始179新增0、产品UNINSTALLED、blocked=[]、test恢复。night=no，keepalive pid66432/serial attached/boot存在/androidstartedtrue。恢复接受，无设备重跑。

## 交付状态与下一步
接受本run错删对照的诊断证据及完整恢复；不接受全L1通过或历史根因已直接定论。115同owner最小修精确pill-delete配对及多root采集，保留真实触摸，删除目标且保留对照后同run80/90。产品不改。复用107Store/111素材，禁止确认式复跑。
方向自审：产品剩余目标仍L1精确删除和重开完整性，具体仪器缺口已有证据；两修在同一仪器且共用testAPK/build/device，只串行一个owner，无独立有价值第二实现。115成功则核阶段剩余队列继续；失败保全恢复后依新证据裁决。私库/Git/存量/设备保护边界不变。
