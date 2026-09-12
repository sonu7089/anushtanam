package com.mantrasiddhi.data.local

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.local.entity.JapaEntryEntity
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.repository.JapaHistoryRepositoryImpl
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/** Isolated Room histories; never opens the app database or creates cloud records. */
class HistoryBaselineTest {
    @Test fun measureRepresentativeHistories() = runBlocking {
        assumeTrue("Performance workload is opt-in; use scripts/perf-baseline.ps1 -HistoryOnly",
            InstrumentationRegistry.getArguments().getString("perfBaseline") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val results = JSONArray()
        val iterations = (InstrumentationRegistry.getArguments().getString("iterations") ?: "20").toInt()
        require(iterations > 0)
        val sizes = InstrumentationRegistry.getArguments().getString("historySizes")?.split(',')?.map { it.toInt() }
            ?: listOf(0, 1_000, 10_000)
        require(sizes.isNotEmpty() && sizes.all { it in listOf(0, 1_000, 10_000) })
        val runId = InstrumentationRegistry.getArguments().getString("runId") ?: "manual"
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        val dir = File(context.getExternalFilesDir(null), "perf-baseline/$runId").apply { mkdirs() }
        fun save(complete: Boolean) {
            val output = JSONObject().put("fixtureVersion", 1).put("complete", complete)
                .put("requestedIterations", iterations).put("device", android.os.Build.MODEL)
                .put("sdk", android.os.Build.VERSION.SDK_INT).put("zone", ZoneId.systemDefault().id)
                .put("results", results)
            val temp = File(dir, "history.tmp")
            temp.writeText(output.toString(2))
            check(temp.renameTo(File(dir, "history.json")))
        }
        save(false)
        WorkProbe.enabled = true
        try {
            for (size in sizes) {
                status("Seeding $size sessions")
                val name = "perf-history-$size.db"
                check(name != MantraSiddhiDatabase.DATABASE_NAME)
                context.deleteDatabase(name)
                val queries = AtomicLong()
                val db = Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name).addCallback(ChangedRecordJournal)
                    .setQueryCallback({ sql, _ ->
                        if (sql.trimStart().startsWith("SELECT", true)) queries.incrementAndGet()
                    }, java.util.concurrent.Executor { it.run() }).build()
                try {
                    seed(db, size)
                    status("Measuring $size sessions")
                    assertEquals(size, db.recitationDao().getStats(USER).totalSessions)
                    // This read-only adapter uses the real DAO, entity mapping and domain use cases.
                    // It excludes sync/save work and does not claim to measure a ViewModel or UI.
                    val repository = object : RecitationRepository {
                        override suspend fun saveSession(session: RecitationSession): Long = error("Read-only fixture")
                        override suspend fun getStats(userId: Long) = db.recitationDao().getStats(userId)
                        override fun getSessionsForUser(userId: Long) = WorkProbe.observe("fixture.sessions",
                            db.recitationDao().getSessionsForUser(userId).map { rows ->
                                WorkProbe.increment("fixture.materializedRows", rows.size.toLong())
                                rows.map { it.toDomain() }
                            }.flowOn(Dispatchers.IO))
                    }
                    val samples = JSONArray()
                    results.put(JSONObject().put("sessions", size).put("samples", samples))
                    repeat(iterations) {
                        val beforeQueries = queries.get()
                        val beforeRows = WorkProbe.snapshot().first["fixture.materializedRows"] ?: 0
                        val cpu = Process.getElapsedCpuTime()
                        val start = SystemClock.elapsedRealtimeNanos()
                        withContext(Dispatchers.Main) {
                            withTimeout(60_000) {
                                val core = combine(GetRecitationStatsUseCase(repository)(USER),
                                    GetDailyRecitationCountsUseCase(repository)(USER),
                                    GetAccuracyTrendUseCase(repository)(USER)) { stats, _, _ -> stats }
                                val trends = combine(GetSessionsForUserUseCase(repository)(USER),
                                    GetAxisTrendsUseCase(repository)(USER)) { sessions, _ -> sessions.size }
                                combine(core, trends, GetJapaSummaryUseCase(JapaHistoryRepositoryImpl(db))(USER)) { stats, rows, japa ->
                                    assertEquals(size, stats.totalSessions)
                                    assertEquals(size, rows)
                                    // Every fifth row is incorrect; every seventh is PRACTICE.
                                    val expected = (0 until size).count { it % 5 != 0 && it % 7 != 0 }
                                    assertEquals(expected.toString(), japa.lifetime.toString())
                                }.first()
                            }
                        }
                        samples.put(JSONObject().put("wallMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                            .put("cpuMs", Process.getElapsedCpuTime() - cpu)
                            .put("selectQueries", queries.get() - beforeQueries)
                            .put("mappedSessionRows", (WorkProbe.snapshot().first["fixture.materializedRows"] ?: 0) - beforeRows))
                        assertEquals(0L, WorkProbe.snapshot().second["fixture.sessions"])
                        assertEquals(0L, WorkProbe.snapshot().second["japa.history"])
                        save(false)
                        status("$size sessions: sample ${it + 1}/$iterations complete")
                    }
                } finally { db.close(); context.deleteDatabase(name) }
            }
        } finally { WorkProbe.enabled = false }
        save(true)
    }

    private suspend fun seed(db: MantraSiddhiDatabase, size: Int) = db.withTransaction {
        val anchor = Instant.parse("2026-09-12T06:00:00Z").toEpochMilli()
        val words = listOf("om", "bhur", "bhuvah", "svah", "tat", "savitur", "varenyam", "bhargo",
            "devasya", "dhimahi", "dhiyo", "yo", "nah", "prachodayat")
        repeat(size) { i ->
            val correct = i % 5 != 0
            val mode = if (i % 7 == 0) "PRACTICE" else "NORMAL"
            val time = anchor - (i % 365) * 86_400_000L - (i / 365) * 60_000L
            val row = RecitationSessionEntity(userId = USER, mantraId = "gayatri", mode = mode,
                overallScore = if (correct) 0.95 else 0.62,
                wordScoresJson = words.joinToString(",") { "$it:${if (correct) "0.95" else "0.62"}" },
                transcription = words.joinToString(" "), isCorrect = correct, durationMs = 24_000,
                createdAt = time, stableId = "perf-session-$i")
            val id = db.recitationDao().insert(row)
            // Half the accepted normal sessions have ledger entries; half exercise legacy fallback.
            if (correct && mode == "NORMAL" && i % 2 == 0) {
                db.japaDao().putEntry(JapaEntryEntity("perf-entry-$i", USER, "gayatri",
                    Instant.ofEpochMilli(time).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
                        .format(DateTimeFormatter.BASIC_ISO_DATE), "Asia/Kolkata", null, 1, 1, time,
                    null, null, "AUDIO_ACCEPTED", sessionId = id))
            }
        }
    }

    private fun status(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nHistory baseline: $message\n")
        })
    }

    private companion object { const val USER = 900_001L }
}
