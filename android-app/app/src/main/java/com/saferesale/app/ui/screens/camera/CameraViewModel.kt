package com.saferesale.app.ui.screens.camera

import androidx.lifecycle.ViewModel
import com.saferesale.app.data.camera.CameraInfoRepository
import com.saferesale.app.domain.model.CameraInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(repo: CameraInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getCameraInfo())
    val info: StateFlow<CameraInfo> = _info
}
