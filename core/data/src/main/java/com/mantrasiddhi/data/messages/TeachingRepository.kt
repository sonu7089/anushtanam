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
import kotlinx.coroutines.flow.flowOn
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
                        val cursor: DocumentSnapshot?, val hasMore: Boolean, val refreshing: Boolean = false)
data class TeachingDetail(val message: TeachingMessage?, val offline: Boolean, val refreshing: Boolean = false)

@Singleton
class TeachingRepository @Inject constructor(private val firestore: FirebaseFirestore,
    private val refreshClock: TeachingRefreshClock,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO) {
    companion object { const val HOME_REFLECTION_ID = "home-reflection" }

    private val reflectionMutex = Mutex()
    private var reflectionCacheLoaded = false
    private var reflectionReadyAt = 0L
    private val _reflection = MutableStateFlow<TeachingMessage?>(null)
    val reflection = _reflection.asStateFlow()

    /** A fixed document keeps the mentor's note independent of feed ordering and pagination. */
    suspend fun refreshReflection() = reflectionMutex.withLock {
        if (refreshClock.now() < reflectionReadyAt) return@withLock
        reflectionReadyAt = refreshClock.now() + 10_000L
        val document = collection.document(HOME_REFLECTION_ID)
        if (!reflectionCacheLoaded) {
            val cached = runCatching { withTimeout(1_000) { document.get(Source.CACHE).messageAwait() } }
            currentCoroutineContext().ensureActive()
            _reflection.value = cached.getOrNull()?.let(::decode)
            reflectionCacheLoaded = true
        }
        val server = runCatching { withTimeout(8_000) { document.get(Source.SERVER).messageAwait() } }
        currentCoroutineContext().ensureActive()
        // A successful missing/invalid document clears stale content; an outage retains valid cache.
        _reflection.value = if (server.isSuccess) server.getOrNull()?.let(::decode)
            else _reflection.value?.takeIf { it.isVisible(System.currentTimeMillis()) }
    }

    private val collection get() = firestore.collection("publishedContent")
    private val feedMutex = Mutex()
    private var firstPage: TeachingPage? = null
    private val _feedReadyAt = MutableStateFlow(0L)
    val feedReadyAt = _feedReadyAt.asStateFlow()
    fun refreshDelayMillis() = (_feedReadyAt.value - refreshClock.now()).coerceAtLeast(0)

    /** Cache emissions precede server work; successful refreshes replace removed/expired content. */
    fun observePage(): kotlinx.coroutines.flow.Flow<TeachingPage> = kotlinx.coroutines.flow.flow {
        val cached = firstPage ?: runCatching {
            withTimeout(1_000) { pageQuery(null).get(Source.CACHE).messageAwait() }.let { snapshot ->
                TeachingPage(snapshot.documents.mapNotNull(::decode), true, snapshot.documents.lastOrNull(), snapshot.size() == 30)
            }
        }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (cached != null) emit(cached.copy(messages = cached.messages.filter { it.isVisible(System.currentTimeMillis()) }, refreshing = true))
        // Retain the disk page if this refresh is cancelled after the cache emission.
        feedMutex.withLock { if (firstPage == null && cached != null) firstPage = cached }
        val refreshed = page()
        emit(if (refreshed.offline && refreshed.messages.isEmpty() && cached != null)
            cached.copy(messages = cached.messages.filter { it.isVisible(System.currentTimeMillis()) }, offline = true)
            else refreshed)
    }.flowOn(ioDispatcher)

    fun observeDetail(id: String): kotlinx.coroutines.flow.Flow<TeachingDetail> = kotlinx.coroutines.flow.flow {
        if (!TeachingDeliveryPolicy.validId(id)) { emit(TeachingDetail(null, false)); return@flow }
        val cached = runCatching { withTimeout(1_000) { collection.document(id).get(Source.CACHE).messageAwait() } }.getOrNull()
        currentCoroutineContext().ensureActive()
        cached?.let(::decode)?.let { emit(TeachingDetail(it, true, refreshing = true)) }
        emit(detail(id))
    }.flowOn(ioDispatcher)

    private fun pageQuery(after: DocumentSnapshot?): Query {
        val query = collection.orderBy("pinned", Query.Direction.DESCENDING)
            .orderBy("publishedAt", Query.Direction.DESCENDING).limit(30)
        return if (after == null) query else query.startAfter(after)
    }

    suspend fun page(after: DocumentSnapshot? = null): TeachingPage = kotlinx.coroutines.withContext(ioDispatcher) {
        if (after != null) return@withContext fetchPage(after)
        feedMutex.withLock {
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
        val query = pageQuery(after)
        val server = runCatching { withTimeout(8_000) { query.get(Source.SERVER).messageAwait() } }
        currentCoroutineContext().ensureActive()
        val snapshot = server.getOrNull() ?: query.get(Source.CACHE).messageAwait()
        // Filtering invalid/expired documents preserves the query's pinned/publication order.
        return TeachingPage(snapshot.documents.mapNotNull(::decode),
            server.isFailure, snapshot.documents.lastOrNull(), snapshot.size() == 30)
    }

    suspend fun detail(id: String): TeachingDetail = kotlinx.coroutines.withContext(ioDispatcher) {
        if (!TeachingDeliveryPolicy.validId(id)) return@withContext TeachingDetail(null, false)
        val document = collection.document(id)
        val server = runCatching { withTimeout(8_000) { document.get(Source.SERVER).messageAwait() } }
        currentCoroutineContext().ensureActive()
        val snapshot = server.getOrNull() ?: runCatching { document.get(Source.CACHE).messageAwait() }.getOrNull()
        TeachingDetail(snapshot?.let(::decode), server.isFailure)
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
