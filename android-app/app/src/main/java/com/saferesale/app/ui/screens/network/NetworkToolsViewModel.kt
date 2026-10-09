package com.saferesale.app.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.network.NetworkToolsRepository
import com.saferesale.app.domain.model.NetworkToolResult
import com.saferesale.app.domain.model.NetworkToolType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkToolsViewModel @Inject constructor(
    private val repo: NetworkToolsRepository
) : ViewModel() {

    private val _result = MutableStateFlow<NetworkToolResult?>(null)
    val result: StateFlow<NetworkToolResult?> = _result.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun ping(host: String)   { run { repo.ping(host) } }
    fun dns(host: String)    { run { repo.dnsLookup(host) } }
    fun http(url: String)    { run { repo.httpTest(url) } }
    fun checkNet()           { run { repo.checkConnectivity() } }
    fun traceroute(host: String) { run { repo.traceroute(host) } }

    private fun run(block: suspend () -> NetworkToolResult) {
        viewModelScope.launch {
            _isRunning.value = true
            _result.value = null
            _result.value = block()
            _isRunning.value = false
        }
    }
}
