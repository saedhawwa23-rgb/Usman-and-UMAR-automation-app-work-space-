package com.opendroid.ai.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.actions.ActionDispatcher
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.core.needle.Needle3Status
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class Needle3ViewModel @Inject constructor(
    private val runtime: Needle3LocalRuntime,
    private val actionDispatcher: ActionDispatcher,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val status: StateFlow<Needle3Status> = runtime.status
    private val _diagnostic = MutableStateFlow<String?>(null)
    val diagnostic: StateFlow<String?> = _diagnostic.asStateFlow()

    fun install() {
        viewModelScope.launch { runtime.installAndLoad() }
    }

    fun runDiagnostic() {
        viewModelScope.launch {
            _diagnostic.value = "Running local model and tool call..."
            runCatching {
                val response = runtime.diagnosticToolCall()
                val call = JSONObject(response).getJSONArray("function_calls").getJSONObject(0)
                val action = call.getString("name")
                val params = call.optJSONObject("arguments")?.let { obj ->
                    obj.keys().asSequence().associateWith { obj.get(it).toString() }
                } ?: emptyMap()
                val result = actionDispatcher.execute(action, params, context)
                if (!result.success) error(result.error ?: "The selected local tool returned an error")
                "Needle 3 local tool call passed: $action → ${result.data ?: "success"}"
            }.onFailure { throwable ->
                _diagnostic.value = "Needle 3 diagnostic failed: ${throwable.message ?: "unknown error"}"
            }.onSuccess { message ->
                _diagnostic.value = message
            }
        }
    }
}
