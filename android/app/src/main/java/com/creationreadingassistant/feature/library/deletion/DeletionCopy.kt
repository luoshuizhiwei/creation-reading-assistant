package com.creationreadingassistant.feature.library.deletion

import android.content.Context
import com.creationreadingassistant.R

/**
 * 删除 / 撤销的用户文案 —— 书架、书架搜索、阅读历史三个入口共用这一份。
 *
 * 集中在这里是为了让「同一件事在不同页面说法一致」成为结构上的保证，而不是靠三处
 * 各自记得写对。尤其是作用范围的区分：搁置只改阅读状态、移除正文只丢本地正文缓存、
 * 删除整本资料才连阅读数据与关联一起移除，混着说会直接误导用户对数据留存的判断。
 *
 * 这些函数取 [Context] 而不是 `@Composable`：文案既要在 Composable 里渲染，也要在
 * Snackbar 回调这类非组合上下文中生成，两套实现必然漂移。
 */

/** 确认框标题。 */
fun deletionConfirmTitle(context: Context, scope: DeletionScope): String = context.getString(
    when (scope) {
        DeletionScope.SHELVE -> R.string.deletion_scope_shelve_title
        DeletionScope.REMOVE_CONTENT -> R.string.deletion_scope_remove_content_title
        DeletionScope.DELETE_BOOK -> R.string.deletion_scope_delete_book_title
    },
)

/** 确认按钮文字。 */
fun deletionConfirmAction(context: Context, scope: DeletionScope): String = context.getString(
    when (scope) {
        DeletionScope.SHELVE -> R.string.deletion_scope_shelve_confirm
        DeletionScope.REMOVE_CONTENT -> R.string.deletion_scope_remove_content_confirm
        DeletionScope.DELETE_BOOK -> R.string.deletion_scope_delete_book_confirm
    },
)

/**
 * 确认框正文：这一次到底动了什么、留下什么、能不能撤销。
 *
 * [undoSeconds] 取自 [BookDeletionCoordinator.undoWindowSeconds]，与实际凭证有效期同源，
 * 所以不会出现「说能撤销 12 秒、其实只有 5 秒」；窗口为 0（宿主没有协调器）时不承诺撤销。
 * 是否承诺撤销也由 [DeletionScope.retention] 派生：搁置与移除正文本就没有撤销凭证。
 */
fun deletionConfirmBody(
    context: Context,
    scope: DeletionScope,
    bookCount: Int,
    undoSeconds: Int,
): String {
    val scopeLines = when (scope) {
        DeletionScope.SHELVE -> listOf(
            context.getString(R.string.deletion_scope_shelve_body),
        )
        DeletionScope.REMOVE_CONTENT -> listOf(
            context.getString(R.string.deletion_scope_remove_content_body),
            context.getString(R.string.deletion_scope_remove_content_warning),
        )
        DeletionScope.DELETE_BOOK -> listOf(
            if (bookCount > 1) {
                context.getString(R.string.deletion_scope_delete_book_batch_body, bookCount)
            } else {
                context.getString(R.string.deletion_scope_delete_book_body)
            },
            context.getString(R.string.deletion_scope_delete_book_inspiration_note),
        )
    }
    val undoLine = if (scope.retention().undoableInSession && undoSeconds > 0) {
        context.getString(R.string.deletion_scope_delete_book_undo_note, undoSeconds)
    } else {
        null
    }
    return (scopeLines + undoLine).filterNotNull().joinToString("\n")
}

/**
 * 撤销结果 → 用户文案。
 *
 * 「已恢复」不等于「快照里每一项都写回来了」。让路给操作之后的合法改动、关系目标已被
 * 删除、正文缓存需要重建这三类情况可以同时发生，全部如实列出，而不是只挑一条说。
 */
fun deletionUndoMessage(context: Context, outcome: DeletionUndoOutcome): String {
    if (!outcome.restored) {
        return when (outcome.failure) {
            DeletionUndoFailure.RESTORE_FAILED ->
                context.getString(R.string.deletion_undo_failed, outcome.error?.message.orEmpty())
            DeletionUndoFailure.UNAVAILABLE,
            null,
            -> context.getString(R.string.deletion_undo_unavailable)
        }
    }
    val skippedNewer = if (outcome.skippedNewerChangeCount > 0) {
        context.getString(R.string.deletion_undo_skipped_newer, outcome.skippedNewerChangeCount)
    } else {
        null
    }
    val missingRelations = if (outcome.skippedRelationTargetCount > 0) {
        context.getString(R.string.deletion_undo_relations_missing, outcome.skippedRelationTargetCount)
    } else {
        null
    }
    val contentRebuild = if (outcome.contentPayloadDropped) {
        context.getString(R.string.deletion_undo_content_rebuild)
    } else {
        null
    }
    return listOfNotNull(
        context.getString(R.string.deletion_undo_done),
        skippedNewer,
        missingRelations,
        contentRebuild,
    ).joinToString("，")
}

/** 删除失败提示；事务已回滚，书架数据保持删除前的状态。 */
fun deletionFailedMessage(context: Context): String =
    context.getString(R.string.deletion_failed)
