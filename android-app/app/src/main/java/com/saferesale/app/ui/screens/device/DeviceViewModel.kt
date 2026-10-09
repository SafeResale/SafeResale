package com.saferesale.app.ui.screens.device

import androidx.lifecycle.ViewModel
import com.saferesale.app.data.device.DeviceInfoRepository
import com.saferesale.app.domain.model.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(repo: DeviceInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getDeviceInfo())
    val info: StateFlow<DeviceInfo> = _info
}
