package com.mantrasiddhi.presentation.screens.purashcharana.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.LotusArtwork

/** Photographic decoration. Actual counts and progress remain in the adjacent UI. */
@Composable
fun PurashcharanaMala(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.purashcharana_mala), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // The empty center of the photographed loop sits above its hanging tassel.
        LotusArtwork(LotusArt.EMBLEM, Modifier.fillMaxSize(0.68f).offset(y = -maxHeight * 0.12f))
    }
}
