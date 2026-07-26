package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    repo: StatsRepository,
) : ViewModel() {
    /** 实时统计：任意阅读会话 / 进度 / 书籍 / 灵感变化后自动重算。 */
    val stats: StateFlow<StatsRepository.Stats?> = repo.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
