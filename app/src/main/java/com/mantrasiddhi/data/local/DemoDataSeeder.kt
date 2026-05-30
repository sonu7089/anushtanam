/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local

import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.SavingsDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.SavingsEntity
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to seed rich demo data representing user stories.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val database: MantraSiddhiDatabase,
    private val userDao: UserDao,
    private val targetDao: TargetDao,
    private val recitationDao: RecitationDao,
    private val savingsDao: SavingsDao
) {
    suspend fun seed() {
        database.clearAllTables()

        // 1. Seed User Profiles
        userDao.insertAll(
            listOf(
                UserProfileEntity(id = 1, name = "Arjun", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 0),
                UserProfileEntity(id = 2, name = "Meera", profileType = "PARENT", parentId = null, avatarIndex = 1),
                UserProfileEntity(id = 3, name = "Aarav", profileType = "CHILD", parentId = 2, avatarIndex = 2),
                UserProfileEntity(id = 4, name = "Priya", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 3)
            )
        )

        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        // 2. Seed Arjun: Consistent Practitioner, 85% avg accuracy, 45/108 target
        targetDao.insert(
            TargetEntity(id = 1, userId = 1, setByUserId = 1, targetRecitationCount = 108, currentRecitationCount = 108, moneyAmount = 10.0, isCompleted = true)
        )
        targetDao.insert(
            TargetEntity(id = 2, userId = 1, setByUserId = 1, targetRecitationCount = 108, currentRecitationCount = 45, moneyAmount = 10.0, isCompleted = false)
        )
        savingsDao.insert(
            SavingsEntity(userId = 1, totalSaved = 10.0, totalLocked = 4.17, totalUnlocked = 10.0)
        )
        // Seed 15 sessions for Arjun over last 7 days (mostly correct, avg 85%)
        val arjunSessions = listOf(
            // Today (Day 6)
            generateSession(1, "NORMAL", 0.88, now - 2 * 60 * 60 * 1000L, 8500),
            generateSession(1, "NORMAL", 0.91, now - 6 * 60 * 60 * 1000L, 8100),
            generateSession(1, "PRACTICE", 0.78, now - 8 * 60 * 60 * 1000L, 10200),
            // Yesterday (Day 5)
            generateSession(1, "NORMAL", 0.86, now - dayMs - 1 * 60 * 60 * 1000L, 9000),
            generateSession(1, "NORMAL", 0.89, now - dayMs - 5 * 60 * 60 * 1000L, 8800),
            // Day 4
            generateSession(1, "NORMAL", 0.87, now - 2 * dayMs - 2 * 60 * 60 * 1000L, 8900),
            generateSession(1, "NORMAL", 0.85, now - 2 * dayMs - 4 * 60 * 60 * 1000L, 9100),
            generateSession(1, "PRACTICE", 0.82, now - 2 * dayMs - 7 * 60 * 60 * 1000L, 9500),
            // Day 3
            generateSession(1, "NORMAL", 0.89, now - 3 * dayMs - 3 * 60 * 60 * 1000L, 8700),
            // Day 2
            generateSession(1, "NORMAL", 0.85, now - 4 * dayMs - 2 * 60 * 60 * 1000L, 9300),
            generateSession(1, "NORMAL", 0.88, now - 4 * dayMs - 6 * 60 * 60 * 1000L, 8900),
            // Day 1
            generateSession(1, "PRACTICE", 0.74, now - 5 * dayMs - 4 * 60 * 60 * 1000L, 11000),
            generateSession(1, "NORMAL", 0.86, now - 5 * dayMs - 8 * 60 * 60 * 1000L, 9200),
            // Day 0
            generateSession(1, "NORMAL", 0.85, now - 6 * dayMs - 3 * 60 * 60 * 1000L, 9400),
            generateSession(1, "NORMAL", 0.87, now - 6 * dayMs - 9 * 60 * 60 * 1000L, 9000)
        )
        arjunSessions.forEach { recitationDao.insert(it) }

        // 3. Seed Meera: Occasional practitioner
        savingsDao.insert(
            SavingsEntity(userId = 2, totalSaved = 0.0, totalLocked = 0.0, totalUnlocked = 0.0)
        )
        val meeraSessions = listOf(
            generateSession(2, "NORMAL", 0.83, now - dayMs, 9800),
            generateSession(2, "NORMAL", 0.87, now - 2 * dayMs, 9100),
            generateSession(2, "NORMAL", 0.79, now - 4 * dayMs, 10500),
            generateSession(2, "PRACTICE", 0.81, now - 5 * dayMs, 10100),
            generateSession(2, "NORMAL", 0.86, now - 6 * dayMs, 9400)
        )
        meeraSessions.forEach { recitationDao.insert(it) }

        // 4. Seed Aarav: Improving Beginner, 65% avg accuracy, 12/108 target
        targetDao.insert(
            TargetEntity(id = 3, userId = 3, setByUserId = 2, targetRecitationCount = 108, currentRecitationCount = 12, moneyAmount = 5.0, isCompleted = false)
        )
        savingsDao.insert(
            SavingsEntity(userId = 3, totalSaved = 0.0, totalLocked = 0.56, totalUnlocked = 0.0)
        )
        // Seed 12 sessions for Aarav (more incorrect, avg 65%)
        val aaravSessions = listOf(
            generateSession(3, "NORMAL", 0.62, now - 1 * 60 * 60 * 1000L, 12000),
            generateSession(3, "NORMAL", 0.68, now - 4 * 60 * 60 * 1000L, 11500),
            generateSession(3, "PRACTICE", 0.58, now - dayMs - 2 * 60 * 60 * 1000L, 13000),
            generateSession(3, "NORMAL", 0.86, now - dayMs - 5 * 60 * 60 * 1000L, 9900), // Correct! (1)
            generateSession(3, "NORMAL", 0.64, now - 2 * dayMs - 1 * 60 * 60 * 1000L, 12500),
            generateSession(3, "NORMAL", 0.85, now - 2 * dayMs - 6 * 60 * 60 * 1000L, 10100), // Correct! (2)
            generateSession(3, "PRACTICE", 0.71, now - 3 * dayMs - 3 * 60 * 60 * 1000L, 11000),
            generateSession(3, "NORMAL", 0.88, now - 4 * dayMs - 4 * 60 * 60 * 1000L, 9700), // Correct! (3)
            generateSession(3, "NORMAL", 0.61, now - 4 * dayMs - 8 * 60 * 60 * 1000L, 13200),
            generateSession(3, "PRACTICE", 0.55, now - 5 * dayMs - 2 * 60 * 60 * 1000L, 14000),
            generateSession(3, "NORMAL", 0.87, now - 5 * dayMs - 6 * 60 * 60 * 1000L, 10000), // Correct! (4)
            generateSession(3, "NORMAL", 0.63, now - 6 * dayMs - 3 * 60 * 60 * 1000L, 12800)
        )
        // Since active targets count is 12, we insert some historical correct recitations as well to make it 12
        repeat(8) { idx ->
            recitationDao.insert(
                generateSession(3, "NORMAL", 0.87, now - (7 + idx) * dayMs, 10000)
            )
        }
        aaravSessions.forEach { recitationDao.insert(it) }

        // 5. Seed Priya: Advanced, high accuracy, multiple completed targets
        targetDao.insert(
            TargetEntity(id = 4, userId = 4, setByUserId = 4, targetRecitationCount = 108, currentRecitationCount = 108, moneyAmount = 15.0, isCompleted = true)
        )
        targetDao.insert(
            TargetEntity(id = 5, userId = 4, setByUserId = 4, targetRecitationCount = 108, currentRecitationCount = 108, moneyAmount = 15.0, isCompleted = true)
        )
        targetDao.insert(
            TargetEntity(id = 6, userId = 4, setByUserId = 4, targetRecitationCount = 108, currentRecitationCount = 80, moneyAmount = 15.0, isCompleted = false)
        )
        savingsDao.insert(
            SavingsEntity(userId = 4, totalSaved = 30.0, totalLocked = 11.11, totalUnlocked = 30.0)
        )
        // Seed 20 sessions for Priya (high scores, avg 92%)
        val priyaSessions = mutableListOf<RecitationSessionEntity>()
        for (i in 0 until 20) {
            val score = 0.88 + (Math.random() * 0.10)
            val time = now - (i % 7) * dayMs - (i * 45 * 60 * 1000L)
            priyaSessions.add(
                generateSession(4, if (i % 5 == 0) "PRACTICE" else "NORMAL", score, time, 7500 + (Math.random() * 1000).toLong())
            )
        }
        priyaSessions.forEach { recitationDao.insert(it) }
    }

    private fun generateSession(
        userId: Long,
        mode: String,
        overallScore: Double,
        createdAt: Long,
        durationMs: Long
    ): RecitationSessionEntity {
        val isCorrect = overallScore >= 0.85
        val wordScoresStr = GayatriMantra.transliterations.joinToString(",") { word ->
            val score = (overallScore + (Math.random() * 0.1 - 0.05)).coerceIn(0.4, 1.0)
            String.format(Locale.US, "%s:%.2f", word, score)
        }
        // Spread the three axes around the overall score so the per-axis progress
        // trends (Phase 4.4) are distinct: completeness tends highest, pronunciation
        // lags slightly behind correctness.
        val completeness = (overallScore + 0.05 + (Math.random() * 0.04 - 0.02)).coerceIn(0.4, 1.0)
        val correctness = (overallScore + (Math.random() * 0.04 - 0.02)).coerceIn(0.4, 1.0)
        val pronunciation = (overallScore - 0.05 + (Math.random() * 0.04 - 0.02)).coerceIn(0.4, 1.0)
        return RecitationSessionEntity(
            userId = userId,
            mode = mode,
            overallScore = overallScore,
            wordScoresJson = wordScoresStr,
            transcription = "om bhur bhuvah svah tatsavitur varenyam bhargo devasya dhimahi dhiyo yo nah prachodayat",
            isCorrect = isCorrect,
            durationMs = durationMs,
            createdAt = createdAt,
            correctness = correctness,
            completeness = completeness,
            pronunciation = pronunciation
        )
    }
}
