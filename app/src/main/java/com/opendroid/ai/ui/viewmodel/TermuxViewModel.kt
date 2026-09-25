package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.termux.TermuxIntegration
import com.opendroid.ai.core.termux.TermuxResult
import com.opendroid.ai.core.termux.TermuxTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermuxViewModel @Inject constructor(
    private val termux: TermuxIntegration
) : ViewModel() {
    val result: StateFlow<TermuxResult?> = termux.results
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _installed = MutableStateFlow(termux.isInstalled())
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    fun refresh() { _installed.value = termux.isInstalled() }

    fun run(task: TermuxTask) = viewModelScope.launch {
        refresh()
        val outcome = termux.run(task)
        _status.value = outcome.fold(
            onSuccess = { "Sent ${task.label} to Termux. Waiting for output..." },
            onFailure = { it.message ?: "Unable to start Termux task." }
        )
    }

    override fun onCleared() {
        termux.close()
        super.onCleared()
    }
}
