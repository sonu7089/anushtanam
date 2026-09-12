package com.mantrasiddhi.presentation.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mantrasiddhi.domain.util.CalendarTime

/** One wait per visibility boundary, cancelled when hidden; rechecked on every resume. */
@Composable
internal fun rememberDeadlineTime(boundaries: List<Long>): Long {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val clock = remember { CalendarTime() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context, clock) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { clock.invalidate() }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(owner, boundaries) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            clock.deadlines({ time -> boundaries.filter { it > time }.minOrNull() }).collect { now = it }
        }
    }
    return now
}
