package com.saferesale.app.ui.screens.audio

import androidx.lifecycle.ViewModel
import com.saferesale.app.data.audio.AudioInfoRepository
import com.saferesale.app.domain.model.AudioInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(repo: AudioInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getAudioInfo())
    val info: StateFlow<AudioInfo> = _info
}
