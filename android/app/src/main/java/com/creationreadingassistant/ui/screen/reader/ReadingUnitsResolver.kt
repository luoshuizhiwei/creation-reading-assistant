package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit

/**
 * readingUnits 当前值的裁决 seam（组合阶段只读，JVM 可测）。
 *
 * 流式 TXT 的单一真相在文档构造层：`PlainTextDocument.fromFileIndex` 构造时即构建
 * readingUnits，文档到达组合层时 units 已就绪（首帧可用），组合层**不写回** document。
 *
 * 本对象把「document 自有 units vs 外部派生 units」的取舍规则集中为可测行为：
 *
 * - [document] 在场（流式 TXT）：返回 [documentOwnedUnits]。文档自有 units 是身份绑定的
 *   真相 —— 构造期就绪（首帧可用）、同文档被替换时调用方传入的已是新值（同一文档更新
 *   不 stale）、切换文档后以本次传入的身份为准（切换文档不 stale）。
 * - [document] 在场但自有 units 为空：如实返回空，**不**回退 [externallyDerivedUnits]。
 *   分页（TxtChapterSource）、搜索、滚动全部读 document.readingUnits；若此处回退到派生
 *   units，分页与滚动/搜索会读到两套不同的列表，破坏全书偏移一致性。
 * - [document] 缺席（小文件 plainContent 路径）：返回 [externallyDerivedUnits]。
 */
internal object ReadingUnitsResolver {

    fun resolve(
        document: Any?,
        documentOwnedUnits: List<ReadingUnit>,
        externallyDerivedUnits: List<ReadingUnit>,
    ): List<ReadingUnit> = when {
        // 文档在场：文档自有 units 即当前应使用的值（身份绑定）。
        // 空也如实返回：与 TxtChapterSource / 搜索读到的 document.readingUnits 保持一致。
        document != null -> documentOwnedUnits
        // 文档缺席（小文件）：组合层从 plainContent / chunks 派生的 units。
        else -> externallyDerivedUnits
    }
}
