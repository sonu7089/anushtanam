package com.mantrasiddhi.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import org.json.JSONObject

/** Shell-only, benchmark-only readout. Does not reset counts or change application state. */
class PerfSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (counts, active) = WorkProbe.snapshot()
        resultData = JSONObject().put("pid", Process.myPid())
            .put("elapsedMs", SystemClock.elapsedRealtime())
            .put("cpuMs", Process.getElapsedCpuTime())
            .put("counts", JSONObject(counts)).put("active", JSONObject(active)).toString()
        resultCode = 0
    }
}
