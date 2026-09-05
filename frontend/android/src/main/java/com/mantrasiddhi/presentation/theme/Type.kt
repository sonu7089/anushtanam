package com.mantrasiddhi.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.R

// Bundled fonts work offline; Android's fallback handles Devanagari glyphs.
@OptIn(ExperimentalTextApi::class)
private fun bundledFamily(resource: Int) = FontFamily(
    *listOf(400, 500, 600, 700).map { weight ->
        Font(resource, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }.toTypedArray()
)
val EditorialFont = bundledFamily(R.font.newsreader)
val ReadingFont = bundledFamily(R.font.dm_sans)
private fun editorial(size: Int, line: Int) = TextStyle(
    fontFamily = EditorialFont, fontWeight = FontWeight.Normal,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.3).sp
)
private fun reading(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = ReadingFont, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = 0.sp
)
val Typography = Typography(
    displayLarge = editorial(56, 64), displayMedium = editorial(44, 52),
    displaySmall = editorial(36, 44), headlineLarge = editorial(32, 40),
    headlineMedium = editorial(28, 36), headlineSmall = editorial(24, 32),
    titleLarge = editorial(26, 34), titleMedium = reading(16, 24, FontWeight.Medium),
    titleSmall = reading(14, 20, FontWeight.Medium), bodyLarge = reading(16, 25),
    bodyMedium = reading(14, 22), bodySmall = reading(12, 18),
    labelLarge = reading(14, 20, FontWeight.SemiBold),
    labelMedium = reading(12, 16, FontWeight.Medium), labelSmall = reading(11, 16, FontWeight.Medium)
)
