package com.creationreadingassistant.ui.screen.readinghistory

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.viewmodel.MyReadingFilter

sealed interface MyReadingAction {
    data object Back : MyReadingAction
    data class UpdateQuery(val value: String) : MyReadingAction
    data class SelectFilter(val value: MyReadingFilter) : MyReadingAction
    data class OpenBook(val book: BookEntity) : MyReadingAction
    data class ManageBook(val book: BookEntity) : MyReadingAction
}
