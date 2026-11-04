# D 阶段首轮门禁复核（2026-09-28）

## 当前判定

- A–E 全局阶段进度仍为 **3/5=60%**。D 未退出：全量 JVM、Lint、APK 和 V4 EPUB 重锚门禁报告通过，V5 的 62 条 MuMu qahost UI 测试实跑为 **60 PASS / 2 FAIL**，S05 三项残留设备场景未运行。
- Codex 本轮实读 `E:\creation-reading-assistant\codex-d-stage-gates-20260928\xml\G5\TEST-emulator-5556 - 12-_app-.xml`：根元素 `tests=62, failures=2, errors=0, skipped=0`，共 62 个 testcase，失败均在 `DurationPillGestureTest`。XML SHA-256 `3e9b447316a1ad6a1e589f91e38946e4acaa555ace4814613564a8b8d7b01d44` 与报告一致；E 盘 `ARTIFACT-INDEX.txt` SHA-256 `858901d9c7c7220ee4dda9d121259a10b9874ad281c4009d65a86040dc9f2bf8` 与报告一致。
- 源码实读 `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt`：`assertNoClickSemanticsOnPillText` 用 `assertFalse(textNodes.isNotEmpty())`，与“应当有展示文本”相反，且令随后检查文本点击语义的循环无法执行。两条失败复用此守卫。**测试断言根因有源码证据**；修后产品行为是否符合断言仍需设备复跑，不能提前判 PASS。
- HY3 只读报告确认 62 个目标测试、FQCN、qahost 卸载数据风险与 S05/S12/S14 结论边界；它完成时 DeepSeek 门禁尚未交付，报告内 V5 的 `NOT RUN` 是当时快照，以本报告首轮实跑为准。

## 下一轮

1. 唯一测试文件所有者窄修上述反向守卫，不动产品源码。修后先定向 7 条，再完整重跑 V5 62 条，保存新 XML、日志、命令、退出码及新 APK/输入哈希。即使失败位移也按原件报告，不预判全绿。
2. 同期另一窗口可只读整理 S05 三项残留场景的可执行、最短 MuMu 验收路线与数据对账要求；不占设备、不跑 Gradle、不改同一文件。
3. V5 绿门禁后，单设备所有者执行 S05 三项，再统一判断 D 是否限定退出。S14 真实 kill 和整包 DB+文件非原子、S12 全表逐列对账、实体手机/MIUI仍是明确边界，不因本轮门禁自动关闭。

依据：[D 构建与设备报告](../../../qa/2026-09/2026-09-28-stage-d-build-and-mumu-gates.md)、[D 独立审查](2026-09-28-stage-d-independent-review.md)。本轮未重新运行 Gradle 或 ADB。
