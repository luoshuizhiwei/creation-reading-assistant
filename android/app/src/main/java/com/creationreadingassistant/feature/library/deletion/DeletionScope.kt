package com.creationreadingassistant.feature.library.deletion

/**
 * 一次「让书从书架消失」的操作到底动了什么，必须先说清楚。
 *
 * 三种作用范围互相不可替代：把清缓存说成删除、或把删除说成可完整恢复，都会让用户
 * 对数据留存做出错误判断。UI 文案与撤销可用性都由这里派生。
 */
enum class DeletionScope {
    /**
     * 搁置：只改阅读生命周期状态（reading_progress.completion_state）。
     * 书籍资料、正文、进度、阅读记录、笔记、高亮、分类/标签/书单关系、已读章节全部保留。
     */
    SHELVE,

    /**
     * 移除正文：释放本地正文缓存与文件关联（book_content 载荷、book_files、books 的本地路径）。
     * 书架资料与全部阅读数据、关系保留；正文需要重新选择文件或重新下载才能恢复。
     *
     * 这不是备份，也不构成可靠恢复：缓存被清掉后无法从本地还原原文件内容。
     */
    REMOVE_CONTENT,

    /**
     * 删除整本资料：书籍与其阅读数据、关系一并软删除，并丢弃分类/标签/书单/已读章节关联行。
     * 仅在会话内、凭证有效期内可撤销；进程重启后不提供撤销。
     */
    DELETE_BOOK,
}

/** 某作用范围是否保留某类资料，供文案与回归测试共用同一份真相。 */
data class DeletionScopeRetention(
    val keepsBookRecord: Boolean,
    val keepsLocalContent: Boolean,
    val keepsReadingData: Boolean,
    val keepsRelations: Boolean,
    val undoableInSession: Boolean,
)

fun DeletionScope.retention(): DeletionScopeRetention = when (this) {
    DeletionScope.SHELVE -> DeletionScopeRetention(
        keepsBookRecord = true,
        keepsLocalContent = true,
        keepsReadingData = true,
        keepsRelations = true,
        undoableInSession = false,
    )
    DeletionScope.REMOVE_CONTENT -> DeletionScopeRetention(
        keepsBookRecord = true,
        keepsLocalContent = false,
        keepsReadingData = true,
        keepsRelations = true,
        undoableInSession = false,
    )
    DeletionScope.DELETE_BOOK -> DeletionScopeRetention(
        keepsBookRecord = false,
        keepsLocalContent = false,
        keepsReadingData = false,
        keepsRelations = false,
        undoableInSession = true,
    )
}
