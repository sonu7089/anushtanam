package com.mantrasiddhi.data.messages

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.gms.tasks.Task
import com.mantrasiddhi.domain.model.TeachingMessage
import com.mantrasiddhi.domain.model.TeachingDeliveryPolicy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.messageAwait(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}

data class TeachingPage(val messages: List<TeachingMessage>, val offline: Boolean,
                        val cursor: DocumentSnapshot?, val hasMore: Boolean)
data class TeachingDetail(val message: TeachingMessage?, val offline: Boolean)

@Singleton
class TeachingRepository @Inject constructor(private val firestore: FirebaseFirestore,
    private val refreshClock: TeachingRefreshClock) {
    private val collection get() = firestore.collection("publishedContent")
    private val feedMutex = Mutex()
    private var firstPage: TeachingPage? = null
    private val _feedReadyAt = MutableStateFlow(0L)
    val feedReadyAt = _feedReadyAt.asStateFlow()
    fun refreshDelayMillis() = (_feedReadyAt.value - refreshClock.now()).coerceAtLeast(0)

    suspend fun page(after: DocumentSnapshot? = null): TeachingPage {
        if (after != null) return fetchPage(after)
        return feedMutex.withLock {
            val saved = firstPage
            if (saved != null && refreshDelayMillis() > 0) return@withLock saved.copy(
                messages = saved.messages.filter { it.isVisible(System.currentTimeMillis()) })
            _feedReadyAt.value = refreshClock.now() + 10_000L
            firstPage = saved ?: TeachingPage(emptyList(), true, null, false)
            // Failed attempts share the cooldown too. The singleton retains it across page navigation.
            try {
                fetchPage(null).also { firstPage = it }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                (saved?.copy(offline = true) ?: TeachingPage(emptyList(), true, null, false)).also { firstPage = it }
            }
        }
    }

    private suspend fun fetchPage(after: DocumentSnapshot?): TeachingPage {
        var query = collection.orderBy("pinned", Query.Direction.DESCENDING)
            .orderBy("publishedAt", Query.Direction.DESCENDING).limit(30)
        if (after != null) query = query.startAfter(after)
        val server = runCatching { withTimeout(8_000) { query.get(Source.SERVER).messageAwait() } }
        currentCoroutineContext().ensureActive()
        val snapshot = server.getOrNull() ?: query.get(Source.CACHE).messageAwait()
        return TeachingPage(snapshot.documents.mapNotNull(::decode).sortedByDescending { it.pinned },
            server.isFailure, snapshot.documents.lastOrNull(), snapshot.size() == 30)
    }

    suspend fun detail(id: String): TeachingDetail {
        if (!TeachingDeliveryPolicy.validId(id)) return TeachingDetail(null, false)
        val document = collection.document(id)
        val server = runCatching { withTimeout(8_000) { document.get(Source.SERVER).messageAwait() } }
        currentCoroutineContext().ensureActive()
        val snapshot = server.getOrNull() ?: runCatching { document.get(Source.CACHE).messageAwait() }.getOrNull()
        return TeachingDetail(snapshot?.let(::decode), server.isFailure)
    }

    private fun decode(doc: DocumentSnapshot): TeachingMessage? = runCatching {
        if (!doc.exists() || doc.getLong("schemaVersion") != 1L ||
            doc.getString("locale") != "en" || doc.getBoolean("published") != true ||
            doc.getString("type") !in setOf("teaching", "message", "announcement")) return null
        val title = doc.getString("title")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null
        val body = doc.getString("body")?.takeIf { it.isNotBlank() && it.length <= 20_000 } ?: return null
        TeachingMessage(doc.id, doc.getLong("revision") ?: return null, title,
            doc.getString("summary").orEmpty().take(280), body,
            doc.getString("attribution").orEmpty().take(160), doc.getLong("publishedAt") ?: return null,
            doc.getLong("expiresAt") ?: return null, doc.getString("art") ?: "river",
            doc.getBoolean("pinned") == true).takeIf { it.isVisible(System.currentTimeMillis()) }
    }.getOrNull()
}

class TeachingRefreshClock @Inject constructor() {
    fun now(): Long = android.os.SystemClock.elapsedRealtime()
}
