package com.mantrasiddhi.data.messages

import com.mantrasiddhi.domain.model.NotificationNudge
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Test

class NotificationOnlyDeliveryTest {
    private val notifications = mockk<TeachingNotifications>(relaxed = true)
    private val repository = mockk<TeachingRepository>()
    private val note = NotificationNudge("festival", "A festival note", "Tap to take a look.", System.currentTimeMillis() + 60000)

    private fun queue(payload: Map<String, String> = note.toPayload()) {
        every { notifications.practicing } returns false
        every { notifications.isEnabledNow() } returns true
        every { notifications.pending() } returns mapOf(note.campaignId to JSONObject(payload).toString())
    }

    @Test fun `standalone delivery uses queued text without reading any message`() = runTest {
        queue()
        deliverPendingTeachings(notifications, repository)
        verify { notifications.displayNudge(note) }
        coVerify(exactly = 0) { repository.detail(any()) }
    }

    @Test fun `active japa keeps the standalone notification waiting`() = runTest {
        queue()
        every { notifications.practicing } returns true
        deliverPendingTeachings(notifications, repository)
        verify(exactly = 0) { notifications.displayNudge(any()) }
        verify(exactly = 0) { notifications.discard(any()) }
        coVerify(exactly = 0) { repository.detail(any()) }
    }

    @Test fun `opt-out and expired or unsupported destinations are discarded`() = runTest {
        queue(note.toPayload() + ("destination" to "offers"))
        deliverPendingTeachings(notifications, repository)
        queue(note.toPayload() + ("expiresAt" to "1"))
        deliverPendingTeachings(notifications, repository)
        queue()
        every { notifications.isEnabledNow() } returns false
        deliverPendingTeachings(notifications, repository)
        verify(exactly = 3) { notifications.discard(note.campaignId) }
        verify(exactly = 0) { notifications.displayNudge(any()) }
        coVerify(exactly = 0) { repository.detail(any()) }
    }
}
