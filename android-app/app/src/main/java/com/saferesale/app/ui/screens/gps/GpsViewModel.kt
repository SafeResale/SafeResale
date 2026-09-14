package com.saferesale.app.ui.screens.gps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.gps.GpsRepository
import com.saferesale.app.domain.model.GpsInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GpsViewModel @Inject constructor(private val repo: GpsRepository) : ViewModel() {
    private val _info = MutableStateFlow(GpsInfo())
    val info: StateFlow<GpsInfo> = _info.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun startListening() {
        _isListening.value = true
        viewModelScope.launch {
            repo.getGpsInfoFlow().collect { _info.value = it }
        }
    }

    fun stopListening() { _isListening.value = false }
}
