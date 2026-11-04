# 任务145-r8-r1：fake安装角色定位及必要成功链（仅离线）

2026-10-26香港19:50，原global Flash同owner；原r8已BLOCKED_PARTIAL原件冻结，本次10分钟差量128KiB/raw256KiB，只新recheck-r8/recheck-r1/。

````text
Root已读r8实际host14162B b70406c011f79862a0fa2f896125ebc6a0c6a4f83db6ff7443956caa36dfdd31、REPORT/03b组件门原json/测试install和pm path分支。新preserve真正调用新版清单、inputs只读、cmd_*真函数保留修改事实接受复用；成功链仍未过，旧红不覆盖。

具体定位：测试offline_validate_r8.py已存在产品pm path分支（安装集合含H.PKG才返回），不是缺返回分支；install处理却用 `self.installed.add(H.PKG if H.PKG in joined else H.TEST_PKG)`。真实产品install argv是app-debug.apk绝对路径，通常不含字符串com.creationreadingassistant，因此很可能把产品也记成test。请按actual install argv与inputs固定productApk/testApk绝对路径核对这个假设并证明，不凭口头根因；若成立只修fake按精确APK path识别角色并正确记账，不改生产host/父/base/adapter/断言。未知APK必须拒，不用文件名模糊contains匹配。

仅新修改后测试/相对r8最小patch/REPORT/原必要定向raw及独立summary/ready.json在E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r8/recheck-r1/。生产host和inputs未变则引用r8原件sizeSHA，不复制或改r8。沿真实main/父run_attempt/collect/recover与真cmd_*体，只mock外部出口；成功链实际instrument启动一次/业务collect恢复summary真/产品卸载1、原test/settings/protection恢复证据；新find失败场景必须经实际保全新清单而非只单独H.source_manifest，断言summary假/exit非0/产品卸载0/独立恢复回执。原拒绝/TimeoutError性质已有证据，未变输入可复用，不刷整套5项。若成功后又遇必要fake签名/时序缺陷，同owner在本10分钟内按实际API最小修好留所有红，不改生产去迎合fake，不整套suite重跑。

原v1-r8/raw/SYNTHETIC不改不删不rmtree，新fake场景已存在拒覆盖。所有真实外部出口guard，SYNTHETIC_FAKE隔离，无真实ADB/VM/安装卸载force-stop设置/hostmain/runid预算attemptsummary/F1/设备/Android源码APKGradle/buildcompile授权。固定15rolepins/原父1369457a4c5/basec806ce36/adapter16c93794/144匹配APK539f44c5与f2d2982b/真实仪器schema保持。Root收到实际最小delta/原log裁，不自动设备；无Git云私库自动记忆正式库删移旧资产kill共享进程。最后一行：这是任务145-r8-r1。
````
