package com.yashraj.glidr.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KeepAwakeState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val nextInSec: Int = 0,
    val elapsedSec: Int = 0,
)

/** Observable keep-awake state plus the "finger on pad" flag the scheduler honours. */
class KeepAwake {
    private val _state = MutableStateFlow(KeepAwakeState())
    val state: StateFlow<KeepAwakeState> = _state.asStateFlow()

    @Volatile
    var touching = false

    fun update(block: (KeepAwakeState) -> KeepAwakeState) = _state.update(block)
    fun set(s: KeepAwakeState) {
        _state.value = s
    }
}
