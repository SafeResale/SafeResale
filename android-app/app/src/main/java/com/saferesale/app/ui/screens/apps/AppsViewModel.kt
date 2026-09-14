package com.saferesale.app.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.apps.InstalledAppsRepository
import com.saferesale.app.domain.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppSort { NAME, SIZE, INSTALL_DATE, UPDATE_DATE }
enum class AppFilter { ALL, USER, SYSTEM }

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: InstalledAppsRepository
) : ViewModel() {

    private val _allApps  = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _query    = MutableStateFlow("")
    private val _sort     = MutableStateFlow(AppSort.NAME)
    private val _filter   = MutableStateFlow(AppFilter.USER)
    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()
    val sort: StateFlow<AppSort> = _sort.asStateFlow()
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    val apps: StateFlow<List<AppInfo>> = combine(_allApps, _query, _sort, _filter) { all, q, s, f ->
        all.filter { app ->
            val matchesFilter = when (f) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
            }
            val matchesQuery = q.isEmpty() || app.appName.contains(q, true) || app.packageName.contains(q, true)
            matchesFilter && matchesQuery
        }.sortedWith(when (s) {
            AppSort.NAME         -> compareBy { it.appName.lowercase() }
            AppSort.SIZE         -> compareByDescending { it.appSizeBytes }
            AppSort.INSTALL_DATE -> compareByDescending { it.installDate }
            AppSort.UPDATE_DATE  -> compareByDescending { it.updateDate }
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _allApps.value = repo.getInstalledApps(includeSystem = true)
            _isLoading.value = false
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setSort(s: AppSort) { _sort.value = s }
    fun setFilter(f: AppFilter) { _filter.value = f }
}
