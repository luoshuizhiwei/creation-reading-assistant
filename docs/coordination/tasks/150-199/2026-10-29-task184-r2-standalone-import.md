# 任务184-r2：纠正独立生产路径，补真实失败回执分支

原184 owner同会话，10分钟自全文实际发送起评估。只新 E:/creation-reading-assistant/task184-strict-keepalive-entry-offline-v1/recheck-r2/，唯一ready.json。旧v1/r1不覆盖，Root维护state/current。无真实设备/probe/main/恢复/ADB/VM/Gradle，无candidate或APK修改。

Root接受r1已实现的current-b.write回执归属、取消重新loader/cwdfallback、已绑定b函数还原核心实现；不重复这些源码工作。r1 entry5333B SHA68efc739aec86e67b6688c0f6faad06ed11529b5aa206af72824c370e30c9caa。唯一实际生产阻塞与验证误报如下：

## 必须修复的路径与独立启动

r1 `R1_DIR=...task184.../recheck-r1`，其parent是task184根；然后 `R3_DIR=R1_DIR.parent/'task179...'/recheck-r3` 得到不存在的嵌套目录。R4_DIR同样重复task184。这是本次新引入的真实路径错误，区别于v1适配器自行加r2/r1依赖的Root旧初猜。

Root已在独立`python -I -B -c`进程、先将subprocess.run设为全部拒绝、仅importlib加载r1入口并运行_ensure_paths/make_resolver（未执行真实IO）得到：

```
R3_EXISTS False E:/creation-reading-assistant/task184-strict-keepalive-entry-offline-v1/task179-instance-identity-adapter-offline-v1/recheck-r3
ModuleNotFoundError No module named 'identity_adapter_r3'
```

r1验证脚本先行sys.path插入正确179r3和host路径，掩盖默认生产入口错误。新r2仅纠正这些路径为实际公共E:/creation-reading-assistant根或复用179r3既有路径初始化，不增加fallback搜索、不搬复制工程/adapter。新独立验证进程不得在入口bind之前预导入host/adapter或手工加它们的正确sys.path；允许只通过绝对path加载被测r2入口。严格外部IO拦截必须在任何生产import前建立，未知调用抛错。

## 验证结论不能超出代码

r1 T1源码注释明确b1/b2都成功，rc1=rc2=0，未触发失败回执；T3只证明写盘错误传播，不能替代resolver拒绝后失败回执。沿同一组T1只把第二scope的最深外部manager/ADB返回注入明确stderr失败（不能mock resolver或内部keepalive），实际保存b2失败receipt到自己的b.DEV，保留原argv/rc/完整stderr，assert非零与receipt.ok=false及原错误内容。这是已有要求的缺分支，不再扩四case大suite。

r1 loaderIdentityRestored目前比较__name__而非原函数对象，异常路径只核resolve的module；将既有T2断言改为保留的原loader/resolve/cmd函数对象is比较，正常/异常均核，不增加内部mock。原生产restore实现若已正确无需重写。新验证最外层try/finally真正还原subprocess.run，不能仅main返回后恢复。只有这三组必要定向证明，不main离线回放、不旧身份全suite。

## 证据与元数据

r1 14pins中13同，索引仍包含自身并尺寸/hash错，不采“禁自引用”口头声明。实际r1全部64565B，ready3479B SHA82a42d665e901808d1dc4ab1c0cc1ff9b2b7cd749b7fd7ac6497a89f9188fd71；ready的54819总数与最终实际不一致。新索引只收实际新文件，不收自身及ready；REPORT实际互斥分类/总数，禁止预写结束时间。历史v1真实只读IO越界/argv未知永久留，不重建设备历史、不把本次严格离线变成历史零IO证明。

交新entry+最小修改验证（可复用夹具引用不复制）+实际子进程rc/stdout/stderr+完整调用回执+REPORT/ready，所有新文件path/size/fullSHA。独立进程运行使用临时新测试scope，不覆盖旧fixture回执。source/doc/report/ready<=96KiB，全部raw/新fixture<=96KiB；不删证据补绿。默认生产入口仍支持真实IO，但本批所有运行一律严格离线。未知外部命令立即失败，不绕过guard。冻结身份/业务/166门与manager原false保持。

禁止真实设备/probe/main/install/instrument/stopuninstall/Home/launchrebootconnectkill/设置/Gradle、Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆；用户stopEsc/锁屏/login/安全权限即停。完整修复验证交付同owner闭环。最后单独一行“这是任务184-r2。”
