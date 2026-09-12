package com.mantrasiddhi.data.local

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mantrasiddhi.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Actual Room queries against isolated fixtures; never opens the signed-in application database. */
class VisibleDemandQueryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today get() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Test fun aggregateAndHistoryReadsStaySmallAtZeroThousandAndTenThousandRows() = runBlocking {
        for (size in listOf(0, 1000, 10000)) {
            val name = "visible-$size-${UUID.randomUUID()}"
            val sql = java.util.Collections.synchronizedList(mutableListOf<String>())
            val db = Room.databaseBuilder(context, MantraSiddhiDatabase::class.java, name)
                .addCallback(ChangedRecordJournal).setQueryCallback({ query, _ -> sql.add(query) }, java.util.concurrent.Executor { it.run() }).build()
            try {
                db.withTransaction {
                    db.userDao().insert(UserProfileEntity(7, "Fixture", "INDIVIDUAL", null, 0))
                    repeat(size) { i -> db.recitationDao().insert(RecitationSessionEntity(userId = 7, mantraId = "gayatri",
                        mode = "NORMAL", overallScore = .8, wordScoresJson = "word:0.8", transcription = "large-unused-field".repeat(20),
                        isCorrect = i % 2 == 0, durationMs = 100, createdAt = today - (i / 20) * 86400000L,
                        stableId = "fixture-$i")) }
                }
                sql.clear()
                val start = SystemClock.elapsedRealtimeNanos()
                val totals = db.recitationDao().observeTotals(7).first()
                val days = db.recitationDao().observePracticeDays(7).first()
                assertEquals(size, totals.total)
                assertEquals(size / 2, totals.correct)
                val overviewMs = (SystemClock.elapsedRealtimeNanos() - start) / 1000000.0
                assertTrue(sql.none { it.contains("SELECT * FROM recitation_sessions", true) })
                val japa = com.mantrasiddhi.domain.usecase.GetJapaSummaryUseCase(
                    com.mantrasiddhi.data.repository.JapaHistoryRepositoryImpl(db))(7).first()
                assertEquals((size / 2).toString(), japa.lifetime.toString())
                assertTrue(sql.none { it.contains("SELECT * FROM recitation_sessions", true) || it.contains("wordScoresJson", true) })
                val scores = db.recitationDao().observeScoreDays(7, today - 6 * 86400000L, today + 86400000L).first()
                assertTrue(scores.size <= 7)
                val first = db.recitationDao().observeHistoryPage(7, Long.MAX_VALUE, Long.MAX_VALUE, 31).first()
                assertEquals(minOf(size, 31), first.size)
                if (size > 30) {
                    val cursor = first[29]
                    val second = db.recitationDao().observeHistoryPage(7, cursor.createdAt, cursor.id, 31).first()
                    assertEquals(31, second.size)
                    assertTrue(first.take(30).map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty())
                    assertEquals(first[30], second[0])
                }
                assertTrue(db.recitationDao().observeHistoryPage(8, Long.MAX_VALUE, Long.MAX_VALUE, 31).first().isEmpty())
                assertTrue(sql.filter { it.contains("LIMIT", true) && it.contains("recitation_sessions") }.none {
                    it.contains("transcription", true) || it.contains("wordScoresJson", true)
                })
                android.util.Log.i("VisibleDemand", "size=$size overviewMs=$overviewMs days=${days.size} scoreDays=${scores.size} historyRows=${first.size}")
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun backupStatusCountsWithoutPayloadAndReviewReadsOneOwnedEntry() = runBlocking {
        val name = "visible-backup-${UUID.randomUUID()}"
        val db = MantraSiddhiDatabase.buildDatabase(context, name)
        try {
            db.userDao().insert(UserProfileEntity(7, "Fixture", "INDIVIDUAL", null, 0, "owner"))
            repeat(50) { i ->
                db.japaDao().putEntry(JapaEntryEntity("entry-$i", 7, "gayatri", "20260912", "UTC", "SAYAM", 10, 1, 100, null, null, "SANDHYA"))
                db.backupDao().putConflict(BackupConflictEntity("owner", "conflict-${i.toString().padStart(2, '0')}", "entry-$i", "{}", "{}"))
            }
            assertEquals(50, db.backupDao().observeConflictCount("owner").first())
            val review = db.backupDao().observeReview("owner", 7).first()
            assertEquals(1, review.size)
            assertEquals("conflict-00", review.single().id)
            assertEquals(10L, review.single().quantity)
            assertNull(db.backupDao().observeReview("owner", 8).first().single().quantity)
            assertTrue(db.backupDao().observeReview("other", 7).first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
