package com.saferesale.app.ui.screens.ram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.ram.RamInfoRepository
import com.saferesale.app.domain.model.RamInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RamViewModel @Inject constructor(private val repo: RamInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(RamInfo())
    val info: StateFlow<RamInfo> = _info.asStateFlow()

    private val _history = MutableStateFlow(listOf<Float>())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getRamInfoFlow().collect { info ->
                _info.value = info
                _history.value = (_history.value + info.usagePercent).takeLast(60)
            }
        }
    }
}
