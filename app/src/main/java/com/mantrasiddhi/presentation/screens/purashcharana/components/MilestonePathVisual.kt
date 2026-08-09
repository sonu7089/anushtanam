/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.purashcharana.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.PurashcharanaMilestone
import com.mantrasiddhi.presentation.screens.purashcharana.formatIndianCount
import com.mantrasiddhi.presentation.screens.purashcharana.milestoneGlossRes
import com.mantrasiddhi.presentation.screens.purashcharana.milestoneTitleRes

/**
 * The Purashcharana ladder drawn as a path climbing down the screen, one node per stage.
 *
 * This is the centrepiece of the screen, and it is a *path* rather than a bar chart on
 * purpose: 24,00,000 japa is too large a number to feel, but "three stages behind me, this
 * one next" is something a sadhak can hold. Stages already crossed are filled solid and
 * ticked, the next one is ringed and named, the rest are quiet outlines — so the eye lands
 * on the one stage that is actually in front of the reciter.
 *
 * The ladder is data, not decoration: it comes straight from
 * [com.mantrasiddhi.domain.model.PurashcharanaPath], so a shorter mantra would draw its own
 * (shorter) ladder without a line changing here.
 *
 * @param milestones the ladder, ascending; typically ten stages for the Gayatri.
 * @param reachedCount how many leading stages have been crossed.
 */
@Composable
fun MilestonePathVisual(
    milestones: List<PurashcharanaMilestone>,
    reachedCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        milestones.forEachIndexed { index, milestone ->
            MilestoneRow(
                milestone = milestone,
                isReached = index < reachedCount,
                isNext = index == reachedCount,
                isFirst = index == 0,
                isLast = index == milestones.lastIndex,
                railAboveTravelled = index < reachedCount,
                railBelowTravelled = index + 1 < reachedCount
            )
        }
    }
}

@Composable
private fun MilestoneRow(
    milestone: PurashcharanaMilestone,
    isReached: Boolean,
    isNext: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    railAboveTravelled: Boolean,
    railBelowTravelled: Boolean
) {
    val travelledColor = MaterialTheme.colorScheme.primary
    val aheadColor = MaterialTheme.colorScheme.outlineVariant
    val onNodeColor = MaterialTheme.colorScheme.onPrimary

    // IntrinsicSize.Min lets the rail Canvas stretch to whatever height the text column
    // needs, so a long gloss never breaks the line of the path.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centreX = size.width / 2f
                val centreY = size.height / 2f
                val nodeRadius = 9.dp.toPx()
                val railWidth = 2.dp.toPx()

                if (!isFirst) {
                    drawLine(
                        color = if (railAboveTravelled) travelledColor else aheadColor,
                        start = Offset(centreX, 0f),
                        end = Offset(centreX, centreY - nodeRadius),
                        strokeWidth = railWidth,
                        cap = StrokeCap.Round
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = if (railBelowTravelled) travelledColor else aheadColor,
                        start = Offset(centreX, centreY + nodeRadius),
                        end = Offset(centreX, size.height),
                        strokeWidth = railWidth,
                        cap = StrokeCap.Round
                    )
                }

                val centre = Offset(centreX, centreY)
                when {
                    isReached -> {
                        drawCircle(color = travelledColor, radius = nodeRadius, center = centre)
                        drawMilestoneTick(
                            centre = centre,
                            nodeRadius = nodeRadius,
                            color = onNodeColor,
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    isNext -> {
                        // Ringed and hollow: the stage being walked towards, not yet earned.
                        drawCircle(
                            color = travelledColor,
                            radius = nodeRadius,
                            center = centre,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = travelledColor,
                            radius = nodeRadius * 0.34f,
                            center = centre
                        )
                    }
                    else -> {
                        drawCircle(
                            color = aheadColor,
                            radius = nodeRadius * 0.72f,
                            center = centre,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = milestoneTitleRes(milestone.stage)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isReached || isNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatIndianCount(milestone.japaCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(id = milestoneGlossRes(milestone.stage)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isReached || isNext) {
                Spacer(modifier = Modifier.height(6.dp))
                MilestoneBadge(
                    text = stringResource(
                        id = if (isReached) {
                            R.string.purashcharana_milestone_reached
                        } else {
                            R.string.purashcharana_milestone_next
                        }
                    ),
                    containerColor = if (isReached) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isReached) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}

@Composable
private fun MilestoneBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

/**
 * The tick inside a crossed node. Drawn rather than iconified because the core icon set is
 * the only one available here and a 9dp `Icons.Default.Check` inside a Canvas node would
 * mean an extra composable per row for two strokes.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMilestoneTick(
    centre: Offset,
    nodeRadius: Float,
    color: Color,
    strokeWidth: Float
) {
    val left = Offset(centre.x - nodeRadius * 0.42f, centre.y + nodeRadius * 0.02f)
    val bottom = Offset(centre.x - nodeRadius * 0.10f, centre.y + nodeRadius * 0.36f)
    val right = Offset(centre.x + nodeRadius * 0.46f, centre.y - nodeRadius * 0.36f)
    drawLine(color, left, bottom, strokeWidth, StrokeCap.Round)
    drawLine(color, bottom, right, strokeWidth, StrokeCap.Round)
}
