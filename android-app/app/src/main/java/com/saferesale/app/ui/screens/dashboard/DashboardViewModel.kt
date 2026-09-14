package com.saferesale.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.battery.BatteryInfoRepository
import com.saferesale.app.data.cpu.CpuInfoRepository
import com.saferesale.app.data.network.NetworkRepository
import com.saferesale.app.data.ram.RamInfoRepository
import com.saferesale.app.data.storage.StorageInfoRepository
import com.saferesale.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val cpuRepo: CpuInfoRepository,
    private val ramRepo: RamInfoRepository,
    private val batteryRepo: BatteryInfoRepository,
    private val storageRepo: StorageInfoRepository,
    private val networkRepo: NetworkRepository,
) : ViewModel() {

    private val _cpu = MutableStateFlow(CpuInfo())
    val cpu: StateFlow<CpuInfo> = _cpu.asStateFlow()

    private val _ram = MutableStateFlow(RamInfo())
    val ram: StateFlow<RamInfo> = _ram.asStateFlow()

    private val _battery = MutableStateFlow(BatteryInfo())
    val battery: StateFlow<BatteryInfo> = _battery.asStateFlow()

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    private val _network = MutableStateFlow(NetworkInfo())
    val network: StateFlow<NetworkInfo> = _network.asStateFlow()

    // Rolling history (last 60 samples)
    private val _cpuHistory = MutableStateFlow(listOf<Float>())
    val cpuHistory: StateFlow<List<Float>> = _cpuHistory.asStateFlow()

    private val _ramHistory = MutableStateFlow(listOf<Float>())
    val ramHistory: StateFlow<List<Float>> = _ramHistory.asStateFlow()

    init {
        viewModelScope.launch {
            cpuRepo.getCpuInfoFlow(1000L).collect { info ->
                _cpu.value = info
                _cpuHistory.value = (_cpuHistory.value + info.usagePercent).takeLast(60)
            }
        }
        viewModelScope.launch {
            ramRepo.getRamInfoFlow(1000L).collect { info ->
                _ram.value = info
                _ramHistory.value = (_ramHistory.value + info.usagePercent).takeLast(60)
            }
        }
        viewModelScope.launch {
            batteryRepo.getBatteryInfoFlow().collect { _battery.value = it }
        }
        viewModelScope.launch {
            _storage.value = storageRepo.getStorageInfo()
        }
        viewModelScope.launch {
            networkRepo.getNetworkInfoFlow(5000L).collect { _network.value = it }
        }
    }
}
