package com.mantrasiddhi.presentation.screens.family

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mantrasiddhi.resources.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun familyQrMatrix(payload: String) = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512,
    mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ChildFamilyQr(state: FamilyUiState, onShow: () -> Unit) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val invite = state.qrInvite
    var now by remember(invite?.token) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(invite) {
        while (invite != null && !invite.used && now < invite.expiresAtMillis) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val active = invite != null && !invite.used && now < invite.expiresAtMillis
    val bitmap by produceState<Bitmap?>(null, invite?.token, active) {
        value = if (active) withContext(Dispatchers.Default) {
            val matrix = familyQrMatrix(invite!!.payload)
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        } else null
    }
    LaunchedEffect(bitmap) { if (bitmap != null) bringIntoView.bringIntoView() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_qr_body), style = MaterialTheme.typography.bodyMedium)
        bitmap?.let {
            Image(it.asImageBitmap(), stringResource(R.string.family_qr_description),
                Modifier.sizeIn(maxWidth = 260.dp, maxHeight = 260.dp).fillMaxWidth().aspectRatio(1f).background(Color.White))
        }
        if (invite != null) {
            Text(stringResource(when {
                invite.used -> R.string.family_qr_used
                !active -> R.string.family_qr_expired
                else -> R.string.family_qr_lifetime
            }), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onShow, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (invite == null) R.string.family_show_qr else R.string.family_new_qr))
        }
    }
}

@Composable
internal fun FamilyLinkFeedback(state: FamilyUiState) {
    if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
    state.errorRes?.let {
        Text(stringResource(it), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
}
