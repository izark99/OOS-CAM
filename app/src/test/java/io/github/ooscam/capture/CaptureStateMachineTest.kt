package io.github.ooscam.capture

import io.github.ooscam.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateMachineTest {
    private val machine = CaptureStateMachine()

    @Test
    fun start_fromIdle_entersRecordingAtSegment1WithStartTime() {
        assertTrue(machine.start(startedAtWallMs = 1_000L))
        assertEquals(CaptureState.Recording(startedAtWallMs = 1_000L, segmentIndex = 1), machine.state)
    }

    @Test
    fun start_whileRecording_isIgnored() {
        machine.start(1_000L)
        assertFalse(machine.start(2_000L))
        assertEquals(1_000L, (machine.state as CaptureState.Recording).startedAtWallMs)
    }

    @Test
    fun segmentStarted_updatesSegmentIndexOnlyWhileRecording() {
        machine.segmentStarted(2)
        assertEquals(CaptureState.Idle, machine.state)
        machine.start(1_000L)
        machine.segmentStarted(3)
        assertEquals(3, (machine.state as CaptureState.Recording).segmentIndex)
    }

    @Test
    fun requestStop_firstReasonWins() {
        machine.start(1_000L)
        assertTrue(machine.requestStop(StopReason.THERMAL))
        assertFalse(machine.requestStop(StopReason.USER))
        assertEquals(StopReason.THERMAL, (machine.state as CaptureState.Stopping).reason)
    }

    @Test
    fun requestStop_whenIdle_isIgnored() {
        assertFalse(machine.requestStop(StopReason.USER))
        assertEquals(CaptureState.Idle, machine.state)
    }

    @Test
    fun finished_afterStop_entersStoppedWithThatReason() {
        machine.start(1_000L)
        machine.requestStop(StopReason.LOW_STORAGE)
        machine.finished(error = false)
        assertEquals(CaptureState.Stopped(StopReason.LOW_STORAGE), machine.state)
    }

    @Test
    fun finished_withErrorWhileRecording_stopsWithErrorReason() {
        machine.start(1_000L)
        machine.finished(error = true)
        assertEquals(CaptureState.Stopped(StopReason.ERROR), machine.state)
    }

    @Test
    fun start_afterStopped_beginsNewSession() {
        machine.start(1_000L)
        machine.requestStop(StopReason.USER)
        machine.finished(error = false)
        assertTrue(machine.start(5_000L))
        assertEquals(CaptureState.Recording(5_000L, 1), machine.state)
    }
}
