package com.mantrasiddhi.presentation.screens.messages

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.mantrasiddhi.domain.model.TeachingMessage
import com.mantrasiddhi.presentation.components.*
import com.mantrasiddhi.resources.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MessagesHomeEntry(onClick: () -> Unit) {
    LotusCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        val surface = MaterialTheme.colorScheme.surfaceContainerLow
        Box(Modifier.fillMaxWidth().height(124.dp)) {
            LotusArtwork(LotusArt.MESSAGES, Modifier.matchParentSize(),
                usage = com.mantrasiddhi.presentation.components.ArtUsage.Banner)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color.Transparent, 0.65f to Color.Transparent, 1f to surface)))
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp, top = 8.dp)) {
            Text(stringResource(R.string.messages_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.messages_home_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.messages_open), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun MessagesScreen(onBack: () -> Unit, onOpen: (String) -> Unit,
                   contentId: String? = null, viewModel: MessagesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner, contentId) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh(contentId)
            try {
                now = System.currentTimeMillis()
                while (true) { delay(30_000); now = System.currentTimeMillis() }
            } finally { viewModel.stop() }
        }
    }
    MessagesContent(state.copy(items = state.items.filter { it.isVisible(now) },
        detail = state.detail?.takeIf { it.id == contentId && it.isVisible(now) }), contentId != null, onBack, onOpen,
        { viewModel.refresh(contentId) }, { viewModel.refresh(more = true) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesContent(state: MessagesState, isDetail: Boolean, onBack: () -> Unit,
    onOpen: (String) -> Unit, onRefresh: () -> Unit, onMore: () -> Unit) {
    Scaffold(topBar = {
        LotusTopAppBar(title = { Text(stringResource(R.string.messages_page_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.messages_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!isDetail) item {
                PageIntro(stringResource(R.string.messages_title), stringResource(R.string.messages_intro), LotusArt.MESSAGES,
                    titleAction = {
                        val label = stringResource(if (state.loading || state.refreshing) R.string.messages_refreshing else R.string.messages_refresh)
                        OutlinedIconButton(onClick = onRefresh, enabled = !state.loading && !state.refreshing && !state.appending && state.refreshReady,
                            modifier = Modifier.offset(y = 8.dp).size(48.dp).semantics { contentDescription = label }) {
                            if (state.loading || state.refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Icon(painterResource(R.drawable.ic_sync), null, Modifier.size(24.dp))
                        }
                    })
            }
            if ((state.loading || state.refreshing) && isDetail) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.refreshFailed) item {
                TextButton(onClick = onRefresh, enabled = state.refreshReady && !state.refreshing) { Text(stringResource(R.string.section_retry)) }
            }
            if (state.offline && (state.items.isNotEmpty() || state.detail != null)) item {
                Text(stringResource(R.string.messages_offline), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isDetail && state.detail != null) {
                val message = state.detail
                item {
                    AmbientArtwork(messageArt(message.art), Modifier.fillMaxWidth().height(180.dp))
                    Text(message.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    MessageAttribution(message)
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    SelectionContainer { Text(message.body, style = MaterialTheme.typography.bodyLarge) }
                }
            } else if (isDetail && !state.loading) item {
                PageIntro(stringResource(if (state.offline) R.string.messages_offline_title else R.string.messages_unavailable_title),
                    stringResource(if (state.offline) R.string.messages_offline_body else R.string.messages_unavailable_body), LotusArt.JASMINE)
            } else if (!isDetail) {
                if (state.items.isEmpty() && !state.loading) item {
                    LotusCard {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.messages_empty_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(if (state.offline) R.string.messages_offline_empty else R.string.messages_empty_body),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(stringResource(R.string.messages_fallback), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                items(state.items, key = { it.id }) { message ->
                    MessageListCard(message, onClick = { onOpen(message.id) })
                }
                if (state.appending) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.appendFailed) item {
                    TextButton(onClick = onMore, enabled = !state.appending) { Text(stringResource(R.string.section_retry)) }
                }
                if (state.hasMore) item {
                    OutlinedButton(onClick = onMore, enabled = !state.loading && !state.refreshing && !state.appending, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.messages_more))
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageListCard(message: TeachingMessage, onClick: () -> Unit) {
    LotusCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val showPicture = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f
            Column {
                if (message.pinned) {
                    Text(stringResource(R.string.messages_featured), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
                            Instant.ofEpochMilli(message.publishedAt).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(message.title, style = MaterialTheme.typography.headlineSmall)
                    }
                    if (showPicture) LotusArtwork(messageArt(message.art),
                        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                        usage = com.mantrasiddhi.presentation.components.ArtUsage.Thumbnail)
                }
                Spacer(Modifier.height(12.dp))
                Text(message.summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                FlowRow(Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message.attribution.ifBlank { stringResource(R.string.messages_from) },
                        modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.messages_read), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAttribution(message: TeachingMessage) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message.attribution.ifBlank { stringResource(R.string.messages_from) },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
            Instant.ofEpochMilli(message.publishedAt).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun messageArt(art: String) = when (art) {
    "lotus" -> LotusArt.LOTUS
    "jasmine" -> LotusArt.JASMINE
    "diya" -> LotusArt.DIYA
    else -> LotusArt.RIVER
}
