package com.saferesale.app.ui.market

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

sealed interface ApiState<out T> {
    data object Loading : ApiState<Nothing>
    data class Success<T>(val data: T) : ApiState<T>
    data class Error(val message: String) : ApiState<Nothing>
}

@Composable
fun <T> rememberApi(key: Any?, block: suspend () -> T): ApiState<T> {
    val state by produceState<ApiState<T>>(initialValue = ApiState.Loading, key) {
        value = try {
            ApiState.Success(block())
        } catch (e: Exception) {
            ApiState.Error(e.message ?: "Network error")
        }
    }
    return state
}