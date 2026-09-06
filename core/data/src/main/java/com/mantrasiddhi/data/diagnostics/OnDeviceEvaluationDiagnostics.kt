/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.diagnostics

import android.content.Context
import com.mantrasiddhi.domain.evaluation.diagnostics.DiagnosticEvent
import com.mantrasiddhi.domain.evaluation.diagnostics.EvaluationDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device implementation of [EvaluationDiagnostics].
 *
 * Appends one JSON line per flagged recitation to a capped log file in the app's
 * private storage. There is **no network** and no PII beyond the transcription the
 * user just spoke (which already lives in the local session DB). When the log grows
 * past [MAX_LINES] the oldest entries are trimmed, so it can never grow unbounded on
 * a demo device. Every operation is wrapped so a logging failure can never surface to
 * the user or break evaluation.
 */
@Singleton
class OnDeviceEvaluationDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context
) : EvaluationDiagnostics {

    private val logFile: File by lazy { File(context.filesDir, FILE_NAME) }

    override suspend fun record(event: DiagnosticEvent) {
        withContext(Dispatchers.IO) {
            runCatching {
                appendLine(event.toJsonLine())
                trimIfNeeded()
            }
        }
    }

    private fun appendLine(line: String) {
        logFile.appendText(line + "\n")
    }

    /** Keeps only the most recent [MAX_LINES] entries to bound on-device storage. */
    private fun trimIfNeeded() {
        val lines = logFile.readLines()
        if (lines.size <= MAX_LINES) return
        logFile.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    private fun DiagnosticEvent.toJsonLine(): String = JSONObject().apply {
        put("timestampMs", timestampMs)
        put("transcription", transcription)
        put("overall", overall.toDouble())
        put("correctness", correctness.toDouble())
        put("completeness", completeness.toDouble())
        put("pronunciation", pronunciation.toDouble())
        put("lowConfidence", lowConfidence)
        put("audioCaptured", audioCaptured)
        put("acousticAssessed", acousticAssessed)
        put("snrDb", snrDb?.toDouble() ?: JSONObject.NULL)
    }.toString()

    private companion object {
        const val FILE_NAME = "eval_diagnostics.jsonl"
        const val MAX_LINES = 500
    }
}
