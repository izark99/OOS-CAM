package io.github.ooscam.spike

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File

/** Spike log: Logcat (tag OOSCamSpike) plus an in-memory list the spike screen shows. Main thread only. */
object SpikeLog {
    val lines = mutableStateListOf<String>()

    /** Segment files recorded by the spike service, oldest first. */
    val segments = mutableListOf<File>()

    fun i(message: String) {
        Log.i(SPIKE_LOG_TAG, message)
        lines.add(message)
    }

    fun e(message: String) {
        Log.e(SPIKE_LOG_TAG, message)
        lines.add("ERROR $message")
    }
}
