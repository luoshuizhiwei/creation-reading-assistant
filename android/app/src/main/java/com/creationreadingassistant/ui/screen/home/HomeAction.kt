package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.local.entity.BookEntity

/**
 * Home 纯 Screen 与 Route 之间的单向交互协议。
 *
 * Route（拥有 ViewModel/NavController/SnackbarHost 的那一层）实现 `onAction: (HomeAction) -> Unit`，
 * Screen 只负责把用户点击翻译成具体 action，不直接处理导航/业务/DAO。
 */
sealed interface HomeAction {
    /** 顶栏右上角打开搜索。 */
    data object OpenSearch : HomeAction

    /** 累计阅读摘要——进入真实导航栈中的“我的阅读”。 */
    data object OpenMyReading : HomeAction

    /** 继续阅读 block：打开"管理继续阅读"列表 sheet。 */
    data object OpenContinueSheet : HomeAction

    /** 继续阅读 sheet 内部或遮罩点击——关闭 sheet。 */
    data object DismissContinueSheet : HomeAction

    /** 用户点击继续阅读卡片或已完成卡片——打开阅读器。Route 层做 readiness 校验。 */
    data class OpenBook(val book: BookEntity) : HomeAction

    /** 继续阅读 block 为空时的空态引导——跳到书架。 */
    data object NavigateToShelf : HomeAction

    /** 灵感 block 标题右侧"查看全部"——进入首页专属的最近灵感二级页。 */
    data object OpenRecentInspirations : HomeAction

    /** 点击灵感行——打开该条灵感详情页（?inspId=xxx）。 */
    data class NavigateToInspirationDetail(val inspId: String) : HomeAction

    /** 已完成 block 标题右侧"查看全部"——进入首页专属的已读完成二级页。 */
    data object OpenCompletedBooks : HomeAction
}
