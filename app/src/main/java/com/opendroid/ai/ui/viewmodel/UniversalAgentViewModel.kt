package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.connector.UniversalAgentEvent
import com.opendroid.ai.core.connector.UniversalLocalAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UniversalAgentViewModel @Inject constructor(
    private val agent: UniversalLocalAgent
) : ViewModel() {
    private val mutableEvents = MutableStateFlow<List<UniversalAgentEvent>>(emptyList())
    val events: StateFlow<List<UniversalAgentEvent>> = mutableEvents.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    fun run(goal: String) {
        if (goal.isBlank() || mutableRunning.value) return
        viewModelScope.launch {
            mutableRunning.value = true
            mutableEvents.value = emptyList()
            mutableEvents.value = agent.run(goal).events
            mutableRunning.value = false
        }
    }
}
