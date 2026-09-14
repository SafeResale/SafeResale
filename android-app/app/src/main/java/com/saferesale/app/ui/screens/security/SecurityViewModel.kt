package com.saferesale.app.ui.screens.security

import androidx.lifecycle.ViewModel
import com.saferesale.app.data.security.SecurityInfoRepository
import com.saferesale.app.domain.model.SecurityInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(repo: SecurityInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow(repo.getSecurityInfo())
    val info: StateFlow<SecurityInfo> = _info
}
