package com.saferesale.app.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.network.NetworkRepository
import com.saferesale.app.domain.model.NetworkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(private val repo: NetworkRepository) : ViewModel() {
    private val _info = MutableStateFlow(NetworkInfo())
    val info: StateFlow<NetworkInfo> = _info.asStateFlow()

    private val _publicIp = MutableStateFlow("")
    val publicIp: StateFlow<String> = _publicIp.asStateFlow()

    init {
        viewModelScope.launch { repo.getNetworkInfoFlow().collect { _info.value = it } }
        viewModelScope.launch { _publicIp.value = repo.getPublicIp() }
    }
}
