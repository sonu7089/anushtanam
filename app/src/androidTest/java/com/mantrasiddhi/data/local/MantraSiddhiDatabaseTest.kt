/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mantrasiddhi.data.local.dao.RecitationDao
import com.mantrasiddhi.data.local.dao.TargetDao
import com.mantrasiddhi.data.local.dao.UserDao
import com.mantrasiddhi.data.local.entity.RecitationSessionEntity
import com.mantrasiddhi.data.local.entity.TargetEntity
import com.mantrasiddhi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MantraSiddhiDatabaseTest {

    private lateinit var db: MantraSiddhiDatabase
    private lateinit var userDao: UserDao
    private lateinit var targetDao: TargetDao
    private lateinit var recitationDao: RecitationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MantraSiddhiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        targetDao = db.targetDao()
        recitationDao = db.recitationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun should_insertAndGetUserProfile_when_validEntity() = runTest {
        val user = UserProfileEntity(id = 1, name = "Arjun", profileType = "INDIVIDUAL", parentId = null, avatarIndex = 0)
        userDao.insert(user)
        val loaded = userDao.getById(1)
        assertNotNull(loaded)
        assertEquals("Arjun", loaded?.name)
    }

    @Test
    fun should_insertAndGetActiveTarget_when_validEntity() = runTest {
        val target = TargetEntity(
            id = 1,
            userId = 2,
            setByUserId = 2,
            targetRecitationCount = 108,
            currentRecitationCount = 0,
            moneyAmount = 0.0,
            isCompleted = false
        )
        targetDao.insert(target)
        val loaded = targetDao.getActiveForUser(2)
        assertNotNull(loaded)
        assertEquals(108, loaded?.targetRecitationCount)
    }

    @Test
    fun should_insertAndGetRecitationSession_when_validEntity() = runTest {
        val session = RecitationSessionEntity(
            id = 1,
            userId = 2,
            mode = "NORMAL",
            overallScore = 0.95,
            wordScoresJson = "om:0.95",
            transcription = "om",
            isCorrect = true,
            durationMs = 3000,
            createdAt = System.currentTimeMillis()
        )
        recitationDao.insert(session)
        val sessions = recitationDao.getSessionsForUser(2).first()
        assertEquals(1, sessions.size)
        assertEquals("om", sessions[0].transcription)
    }

    @Test
    fun should_updateTargetCurrentCount_when_recitationsDone() = runTest {
        val target = TargetEntity(
            id = 1,
            userId = 2,
            setByUserId = 2,
            targetRecitationCount = 10,
            currentRecitationCount = 0,
            moneyAmount = 0.0,
            isCompleted = false
        )
        targetDao.insert(target)
        val updated = target.copy(currentRecitationCount = 5)
        targetDao.update(updated)
        val loaded = targetDao.getActiveForUser(2)
        assertEquals(5, loaded?.currentRecitationCount)
    }

    @Test
    fun should_returnChildrenOfParent_when_childrenExist() = runTest {
        val parent = UserProfileEntity(id = 10, name = "Meera", profileType = "PARENT", parentId = null, avatarIndex = 1)
        val child = UserProfileEntity(id = 11, name = "Aarav", profileType = "CHILD", parentId = 10, avatarIndex = 2)
        userDao.insert(parent)
        userDao.insert(child)
        val children = userDao.getChildrenOf(10).first()
        assertEquals(1, children.size)
        assertEquals("Aarav", children[0].name)
    }
}
