package com.saferesale.app.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.battery.BatteryInfoRepository
import com.saferesale.app.domain.model.BatteryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatteryViewModel @Inject constructor(private val repo: BatteryInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getBatteryInfoOnce())
    val info: StateFlow<BatteryInfo> = _info.asStateFlow()

    private val _history = MutableStateFlow(listOf<Float>())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getBatteryInfoFlow().collect { info ->
                _info.value = info
                _history.value = (_history.value + info.percentage.toFloat()).takeLast(60)
            }
        }
    }
}
