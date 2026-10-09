package com.saferesale.app.ui.screens.benchmark

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.benchmark.BenchmarkProgress
import com.saferesale.app.data.benchmark.BenchmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val repo: BenchmarkRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _progress = MutableStateFlow(BenchmarkProgress())
    val progress: StateFlow<BenchmarkProgress> = _progress.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun startBenchmark() {
        if (_isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = true
            repo.runBenchmark(context.cacheDir).collect { p ->
                _progress.value = p
                if (p.isDone) _isRunning.value = false
            }
        }
    }
}
