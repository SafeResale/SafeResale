package com.saferesale.app.ui.screens.storage

import androidx.lifecycle.ViewModel
import com.saferesale.app.data.storage.StorageInfoRepository
import com.saferesale.app.domain.model.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(repo: StorageInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getStorageInfo())
    val info: StateFlow<StorageInfo> = _info
}
