package com.mantrasiddhi.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import org.json.JSONObject

/** Shell-only diagnostics for the real activity, available only in benchmark APKs. */
class PerfSnapshotReceiver : BroadcastReceiver() {
    @OptIn(androidx.compose.runtime.InternalComposeTracingApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("traceCompositions")) {
            AppCompositionTracer.enabled = intent.getBooleanExtra("traceCompositions", false)
            androidx.compose.runtime.Composer.setTracer(AppCompositionTracer)
        }
        val (counts, active) = WorkProbe.snapshot()
        resultData = JSONObject().put("pid", Process.myPid())
            .put("elapsedMs", SystemClock.elapsedRealtime())
            .put("cpuMs", Process.getElapsedCpuTime())
            .put("counts", JSONObject(counts)).put("active", JSONObject(active)).toString()
        resultCode = 0
    }
}

@OptIn(androidx.compose.runtime.InternalComposeTracingApi::class)
private object AppCompositionTracer : androidx.compose.runtime.CompositionTracer {
    var enabled = false
    override fun isTraceInProgress() = enabled && android.os.Trace.isEnabled()
    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        android.os.Trace.beginSection(info.take(127))
    }
    override fun traceEventEnd() = android.os.Trace.endSection()
}
