package com.mantrasiddhi.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mantrasiddhi.data.preferences.PracticeGuidePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PracticeGuidePreferencesTest {
    @Test fun shownOpenedAndAcknowledgedAreSeparateAndSurviveReopening() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PracticeGuidePreferences(context)
        val profile = System.nanoTime()
        store.markShown(profile, 1, openedByUser = false)
        val shown = store.observe(profile, 1).first()
        assertNotNull(shown.shownAt)
        assertNull(shown.openedAt)
        assertNull(shown.acknowledgedAt)
        store.markShown(profile, 1, openedByUser = true)
        assertNotNull(store.observe(profile, 1).first().openedAt)
        assertNull(store.observe(profile, 1).first().acknowledgedAt)
        store.acknowledge(profile, 1)
        val read = store.observe(profile, 1).first()
        assertNotNull(read.acknowledgedAt)
        assertEquals(read, PracticeGuidePreferences(context).observe(profile, 1).first())
        store.markShown(profile, 1, openedByUser = false)
        store.acknowledge(profile, 1)
        assertEquals(read, store.observe(profile, 1).first())
    }

    @Test fun anotherProfileAndNewGuidanceVersionNeedTheirOwnAcknowledgement() = runTest {
        val store = PracticeGuidePreferences(ApplicationProvider.getApplicationContext<Context>())
        val profile = System.nanoTime()
        store.markShown(profile, 1, openedByUser = true)
        store.acknowledge(profile, 1)
        assertNull(store.observe(profile + 1, 1).first().shownAt)
        assertNull(store.observe(profile + 1, 1).first().acknowledgedAt)
        val revised = store.observe(profile, 2).first()
        assertEquals(2, revised.guidanceVersion)
        assertNull(revised.shownAt)
        assertNull(revised.acknowledgedAt)
        assertNotNull(store.observe(profile, 1).first().acknowledgedAt)
    }
}
