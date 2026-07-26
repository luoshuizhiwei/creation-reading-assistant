package com.creationreadingassistant.feature.reader.layout

/**
 * 排版内核的数据模型。
 *
 * **本包（`feature/reader/layout/`）禁止 `import android.*`。**
 * 这不是洁癖：项目路径含中文导致 Gradle 跑不了单元测试（见 SECURITY_AUDIT.md 第四节），
 * 只有零 Android 依赖的纯 JVM 代码才能把编译产物复制到 ASCII 路径后用
 * `java -cp … org.junit.runner.JUnitCore` 直接跑。排版是最容易出细微错误的一层，
 * 它必须是可测的。平台相关实现放在 `layout/android/` 子包。
 */

/** 段落角色。标题参与 keep-with-next，且不做首行缩进、不做两端对齐。 */
enum class BlockRole { BODY, HEADING }

/** 排版输入：一个段落。 */
data class LayoutParagraph(
    val text: String,
    val role: BlockRole = BlockRole.BODY,
    /** 该段首字符在章内的字符偏移。位置恢复的真源是字符偏移，不是页号。 */
    val charOffset: Int = 0,
)

/**
 * 一段文本按字素簇切分后的测量结果。
 *
 * 之所以以「簇」而非「字符」为单位：代理对（𠮷）、组合字、ZWJ emoji 家族都必须
 * 整体移动，按字符切会切出半个字。
 */
class Clusters(
    val text: String,
    /** 长度 count + 1；末位为 text.length，便于取任意簇的 [start, end) */
    val startInText: IntArray,
    /** 每簇的推进宽度（px） */
    val advance: FloatArray,
    /** 每簇的字符类别，取值见 [CharClass] */
    val klass: IntArray,
) {
    val count: Int get() = advance.size

    fun textOf(cluster: Int): String =
        text.substring(startInText[cluster], startInText[cluster + 1])
}

/**
 * 排好版的一行。
 *
 * [clusterX] 长度为 (endCluster - startCluster) + 1，最后一个元素是行末笔尖位置。
 * 它同时服务四件事：绘制、两端对齐、选区命中、高亮矩形 —— 这也是自研断行的真正理由，
 * 一旦有了逐簇 x 坐标，断行几乎是顺带的。
 */
class LayoutLine(
    val startCluster: Int,
    val endCluster: Int,
    val startInText: Int,
    val endInText: Int,
    /** 行首缩进后的起始 x */
    val startX: Float,
    val clusterX: FloatArray,
    /**
     * 每簇首字符在段内的字符偏移，长度与 [clusterX] 相同（末位为行末字符偏移）。
     *
     * 必须实存，不能由「首末偏移按簇数等分」反推：簇与字符不是一一对应
     * （代理对 𠮷、ZWJ emoji 家族、组合字都是一簇多字符），插值会让整行的
     * 点击定位、选区、高亮矩形集体错位 —— 而且错的方向随行内容而变，没法事后校正。
     */
    val clusterStarts: IntArray,
    val isParagraphStart: Boolean,
    val isParagraphEnd: Boolean,
    val role: BlockRole,
    /** 本行为满足禁则而溢出，绘制时需按负字距压回 */
    val overflowed: Boolean = false,
) {
    val clusterCount: Int get() = endCluster - startCluster

    /** 行末笔尖位置。两端对齐的非末行应精确等于可用宽度。 */
    val endX: Float get() = clusterX.last()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutLine) return false
        return startCluster == other.startCluster &&
            endCluster == other.endCluster &&
            startInText == other.startInText &&
            endInText == other.endInText &&
            startX == other.startX &&
            clusterX.contentEquals(other.clusterX) &&
            clusterStarts.contentEquals(other.clusterStarts) &&
            isParagraphStart == other.isParagraphStart &&
            isParagraphEnd == other.isParagraphEnd &&
            role == other.role &&
            overflowed == other.overflowed
    }

    override fun hashCode(): Int {
        var r = startCluster
        r = 31 * r + endCluster
        r = 31 * r + startInText
        r = 31 * r + endInText
        r = 31 * r + startX.hashCode()
        r = 31 * r + clusterX.contentHashCode()
        r = 31 * r + clusterStarts.contentHashCode()
        r = 31 * r + role.hashCode()
        return r
    }

    override fun toString(): String =
        "LayoutLine([$startInText,$endInText) startX=$startX endX=$endX ${if (overflowed) "OVERFLOW " else ""}$role)"
}

/** 排好的一页。 */
class LayoutPage(
    val index: Int,
    /** 页首字符在章内的偏移。位置恢复用这个，不用页号。 */
    val startCharOffset: Int,
    val endCharOffset: Int,
    val lines: List<LayoutLine>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutPage) return false
        return index == other.index &&
            startCharOffset == other.startCharOffset &&
            endCharOffset == other.endCharOffset &&
            lines == other.lines
    }

    override fun hashCode(): Int =
        (index * 31 + startCharOffset) * 31 + endCharOffset

    override fun toString(): String =
        "LayoutPage(#$index [$startCharOffset,$endCharOffset) ${lines.size} lines)"
}
