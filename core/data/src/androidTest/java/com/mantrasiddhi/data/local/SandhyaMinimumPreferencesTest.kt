package com.mantrasiddhi.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.model.SandhyaMinimums
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SandhyaMinimumPreferencesTest {
    @Test fun minimumsPersistForTheirProfileAndLeaveReminderSettingsAlone() = runTest {
        val preferences = SandhyaPreferences(ApplicationProvider.getApplicationContext<Context>())
        val userId = System.currentTimeMillis()
        val before = preferences.current()
        assertEquals(SandhyaMinimums(), preferences.minimums(userId).first())
        preferences.setMinimums(userId, SandhyaMinimums(216, 54, 600))
        assertEquals(SandhyaMinimums(216, 54, 600), preferences.minimums(userId).first())
        assertEquals(SandhyaMinimums(), preferences.minimums(userId + 1).first())
        assertEquals(before, preferences.current())
        preferences.setMinimums(userId, SandhyaMinimums(0, 1, Long.MAX_VALUE))
        assertEquals(SandhyaMinimums(0, 1, Long.MAX_VALUE), preferences.minimums(userId).first())
    }
}
