# 阶段 E：B2 与库浏览器隔离结果回读（2026-09-29）

## 证据复核

- B2 E 盘候选存在。纯 B2 补丁 84,881 字节、SHA-256 `2c0effec08f645455274b94e35241b9862cabc204f813c093c4799b6045f67b1`；夹具接缝补丁 1,964 字节、SHA-256 `2676c84fff34869c83a2d1a82acb5e99ca82db7a1e07b6f2aa43ada620ac0165`，均与报告一致。v1 日志为 EXIT 1，4 份 XML 为 76 测试中 1 失败；v2 日志为 EXIT 0，4 份 XML 合计 31+18+14+13=**76/76**，0 failure/error。失败由旧 `BookRepositoryTest` relaxed Context 缺真实 `filesDir` 引发；夹具补丁是 B2 落地的必要接缝。`StorageSubPage.kt` UI 接线未纳入候选，用户可达流程未验。
- 库浏览器候选补丁 SHA-256 `c225daf845e0c17288c386ffc5180ffb73aba1ecb7635eb659b2a1924414696f`，隔离 `LibraryBrowserViewModel.kt` 与 `LibraryBrowserSelectionTest.kt` 哈希与报告一致；3 份 XML 合计 3+22+4=**29/29**，0 failure/error。排序 `ADDED_DESC` 未纳入。
- 恢复仓库 HEAD 仍为 `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空。两候选仅单独隔离通过；尚未验证 B1+B2+库浏览器合并状态，未进行设备 UI 验收，也未授权后续提交。

## 下一步

单一窗口在 E 盘以 HEAD 构造 B1+B2（必须含夹具接缝）+库浏览器的联合候选，核对混合 `BookRepository.kt` 不丢改动、精确文件边界，定向编译/JVM 并留可复算证据。其他窗口只读梳理 M-BUG-03 与 App 启动依赖边界，不并行改联合候选文件。整体 A–E 仍 **4/5=80%**。
