package io.github.ooscam.capture

import io.github.ooscam.core.StopReason

/** Recording session state as seen by the service, the notification and the UI. */
sealed interface CaptureState {
    data object Idle : CaptureState

    data class Recording(val startedAtWallMs: Long, val segmentIndex: Int) : CaptureState

    data class Stopping(val reason: StopReason) : CaptureState

    data class Stopped(val reason: StopReason) : CaptureState
}

/**
 * Pure state machine of one capture session. The first stop reason wins: a later stop request
 * (for example the user tapping Stop while a thermal stop is finalizing) does not change it.
 */
class CaptureStateMachine {
    var state: CaptureState = CaptureState.Idle
        private set

    val isActive: Boolean get() = state is CaptureState.Recording || state is CaptureState.Stopping

    fun start(startedAtWallMs: Long): Boolean {
        if (isActive) return false
        state = CaptureState.Recording(startedAtWallMs, segmentIndex = 1)
        return true
    }

    fun segmentStarted(index: Int) {
        val current = state as? CaptureState.Recording ?: return
        state = current.copy(segmentIndex = index)
    }

    fun requestStop(reason: StopReason): Boolean {
        if (state !is CaptureState.Recording) return false
        state = CaptureState.Stopping(reason)
        return true
    }

    /** The recorder finalized its last segment (or failed). */
    fun finished(error: Boolean) {
        state = when (val current = state) {
            is CaptureState.Stopping -> CaptureState.Stopped(current.reason)
            is CaptureState.Recording -> CaptureState.Stopped(if (error) StopReason.ERROR else StopReason.USER)
            else -> current
        }
    }
}
