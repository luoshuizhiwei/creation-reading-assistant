# 任务3-v6与8-v1核查

时间：2026-10-09香港时间。报告2/2；限定退出1/2（8）。全产品分母未核。

- Root逐项实算3的992源码/41工件/双APK SHA一致，仅两授权测试改变。原始first_failure记录`SearchScreen.kt:526`导航在CoroutineScheduler线程调用LifecycleRegistry，Activity仍RESUMED；随后tearDown销毁异常为下游事件。首次失败原因成立，但不能由Compose测试栈推定非测试生产环境也必现。
- 查询坐标现在使用生产tokenizer与命中选择规则正向推导，UI严格相等校验，不再观察值反写。查询BETA的unigram b合法命中8优先于beta171；正文回溯成立。页区间、B marker、A12列冻结、返回及真实位移仍未运行，整项FAIL。
- Root核8的996源码与7字节完全一致；4份原始XML SHA/清单绑定成立，33+19+8+4=64，failure/error/skipped均0。最终原日志30 executed、exit0成立。JOINT_DIRECTED_JVM_PASS限定接受，不是设备通过。无需重跑该64未变输入。
- Root当前设备复核：71保护文件size/nsmtime/SHA、原APK对、包状态、映射、shell正常；night实际yes而约定no。已记录漂移并补no，再完整核同8设置。最终MuMu process/android均false。外部恢复报告保留，当前结论由Root补正证据覆盖。

Root证据：`E:/creation-reading-assistant/tasks-3-8-v6-codex-review-20261009/verification.json`、`root-readonly-restore-drift.json`、`root-readonly-restore-proof.json`、`root-power-final.json`。

下一3-v7以8已验996树为基线，在独立E叠加3-v6两J1测试；仅搜索点击导航线程边界最小修正，IO换算保留IO，Main负责导航/反馈。不能把缺显式Main本身当生产根因，应区分Compose测试拦截器影响并用修前/后真实单方法验证。此举避免旧992树修复后再合流构建。最终31仍等待Root核。7/8不重派，不制造独立重复任务。D产品/Git/发布、C2签名选择及X整机中断等边界不扩展。
