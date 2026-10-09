package com.saferesale.app.ui.screens.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.cpu.CpuInfoRepository
import com.saferesale.app.domain.model.CpuInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CpuViewModel @Inject constructor(private val repo: CpuInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(CpuInfo())
    val info: StateFlow<CpuInfo> = _info.asStateFlow()

    private val _history = MutableStateFlow(listOf<Float>())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getCpuInfoFlow().collect { info ->
                _info.value = info
                _history.value = (_history.value + info.usagePercent).takeLast(60)
            }
        }
    }
}
