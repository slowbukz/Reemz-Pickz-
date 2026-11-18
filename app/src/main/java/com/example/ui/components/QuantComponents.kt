package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.SavedParlayDoc
import com.example.data.firebase.SavedPropDoc
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.EventState
import com.example.domain.model.GameEvent
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketType
import com.example.domain.model.OddsFormat
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PropSide
import com.example.ui.theme.LocalQuantColors
import java.util.Locale

@Composable
fun TeamLogoBadge(
    logoUrl: String?,
    fallbackText: String,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = "$fallbackText logo",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, quant.cardBorder, CircleShape)
                .padding(3.dp)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(quant.elevatedSurface)
                .border(1.dp, quant.cardBorder, CircleShape)
        ) {
            Text(
                text = fallbackText.take(3).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PlayerAvatar(
    headshotUrl: String?,
    playerName: String,
    teamAbbr: String,
    size: Dp = 50.dp,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    var imageFailed by remember(headshotUrl) { mutableStateOf(false) }
    val borderColor = if (highlighted) quant.positiveEv.copy(alpha = 0.7f) else quant.cardBorder
    val initials = remember(playerName) {
        playerName.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { teamAbbr.take(2).uppercase() }
    }

    if (!headshotUrl.isNullOrBlank() && !imageFailed) {
        AsyncImage(
            model = headshotUrl,
            contentDescription = "$playerName headshot",
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(quant.elevatedSurface)
                .border(1.5.dp, borderColor, CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(quant.elevatedSurface)
                .border(1.5.dp, borderColor, CircleShape)
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = quant.correlationCyan
            )
        }
    }
}

@Composable
fun EvBadgePill(
    evPercent: Double,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val (bg, fg) = when {
        evPercent >= 0.03 -> quant.positiveEvContainer to quant.positiveEv
        evPercent >= 0.0 -> quant.moderateContainer to quant.moderateAmber
        else -> quant.negativeContainer to quant.negativeCoral
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Text(
            text = "${OddsMath.formatPercent(evPercent, includeSign = true)} EV",
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RhoChip(
    rho: Double,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val color = when {
        rho >= 0.45 -> quant.positiveEv
        rho >= 0.20 -> quant.correlationCyan
        else -> quant.moderateAmber
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = "Correlation coefficient",
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "ρ = ${String.format(Locale.US, "%+.2f", rho)}",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
fun ProbabilityLiftBar(
    independentProb: Double,
    correlatedProb: Double,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val indepColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val liftColor = quant.positiveEv
    val trackColor = quant.elevatedSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Indep: ${OddsMath.formatPercent(independentProb)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val lift = correlatedProb - independentProb
            Text(
                text = "Corr Lift: ${OddsMath.formatPercent(lift, includeSign = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (lift >= 0) quant.positiveEv else quant.negativeCoral
            )
            Text(
                text = "True: ${OddsMath.formatPercent(correlatedProb)}",
                style = MaterialTheme.typography.labelSmall,
                color = quant.positiveEv,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = radius
            )
            val corrWidth = (correlatedProb.toFloat().coerceIn(0.02f, 1f)) * size.width
            val indepWidth = (independentProb.toFloat().coerceIn(0.01f, 1f)) * size.width

            drawRoundRect(
                color = liftColor,
                size = Size(corrWidth, size.height),
                cornerRadius = radius
            )
            drawRoundRect(
                color = indepColor,
                size = Size(indepWidth.coerceAtMost(corrWidth), size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun PlayerPropCard(
    prop: PlayerPropPick,
    oddsFormat: OddsFormat,
    isInSandbox: Boolean,
    onToggleSave: () -> Unit,
    onToggleSandbox: () -> Unit,
    modifier: Modifier = Modifier,
    onClickPlayer: (() -> Unit)? = null,
    onResearchPlayer: (() -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val isMoreRecommended = prop.recommendedSide == PropSide.MORE

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_prop_card_${prop.id}")
            .let { base -> if (onClickPlayer != null) base.clickable(onClick = onClickPlayer) else base }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Athlete Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerAvatar(
                    headshotUrl = prop.headshotUrl,
                    playerName = prop.playerName,
                    teamAbbr = prop.teamAbbr,
                    size = 52.dp,
                    highlighted = true
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = quant.elevatedSurface,
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = "${prop.league.code} • ${prop.teamAbbr} ${prop.position}".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = quant.correlationCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = prop.startTimeDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = prop.playerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = prop.matchupShort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleSandbox,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("prop_sandbox_${prop.id}")
                ) {
                    Icon(
                        imageVector = if (isInSandbox) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = if (isInSandbox) "Remove prop from Correlation Lab" else "Add recommended prop to Correlation Lab",
                        tint = if (isInSandbox) quant.correlationCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_prop_${prop.id}")
                ) {
                    Icon(
                        imageVector = if (prop.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (prop.isSaved) "Remove saved prop" else "Save prop",
                        tint = if (prop.isSaved) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Line + Model Projection Bar
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, quant.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = prop.statLabel.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Line ${formatStatLine(prop.lineValue)} ${prop.statLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "MODEL PROJECTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", prop.projectedValue)} ${prop.statLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = quant.positiveEv
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BOTH MORE and LESS options displayed side-by-side; Recommended pick highlighted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PropSideChoiceTile(
                    sideLabel = "MORE ${formatStatLine(prop.lineValue)}",
                    isRecommended = isMoreRecommended,
                    badgeText = "TAKE MORE",
                    probability = prop.moreProb,
                    americanOdds = prop.moreAmericanOdds,
                    evPercent = prop.moreEvPercent,
                    oddsFormat = oddsFormat,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prop_more_tile_${prop.id}")
                )
                PropSideChoiceTile(
                    sideLabel = "LESS ${formatStatLine(prop.lineValue)}",
                    isRecommended = !isMoreRecommended,
                    badgeText = "TAKE LESS",
                    probability = prop.lessProb,
                    americanOdds = prop.lessAmericanOdds,
                    evPercent = prop.lessEvPercent,
                    oddsFormat = oddsFormat,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prop_less_tile_${prop.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (onClickPlayer != null) "${prop.confidenceThesis} Tap card for full pick rationale." else prop.confidenceThesis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onResearchPlayer != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onResearchPlayer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = quant.correlationCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("research_prop_${prop.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueryStats,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Research Game Logs, Graphs & Injuries",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PropSideChoiceTile(
    sideLabel: String,
    isRecommended: Boolean,
    badgeText: String,
    probability: Double,
    americanOdds: Int,
    evPercent: Double,
    oddsFormat: OddsFormat,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val bgColor = if (isRecommended) quant.positiveEvContainer else quant.elevatedSurface.copy(alpha = 0.65f)
    val borderColor = if (isRecommended) quant.positiveEv else quant.cardBorder
    val borderWidth = if (isRecommended) 2.dp else 1.dp
    val titleColor = if (isRecommended) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sideLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecommended) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isRecommended) FontWeight.Bold else FontWeight.Medium
                )
                if (isRecommended) {
                    Surface(
                        color = quant.positiveEv,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = badgeText,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = OddsMath.formatPercent(probability),
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor
                    )
                    Text(
                        text = "Win Prob",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = OddsMath.formatOdds(americanOdds, oddsFormat),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${OddsMath.formatPercent(evPercent, includeSign = true)} EV",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (evPercent >= 0) quant.positiveEv else quant.negativeCoral
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleGameChipCard(
    event: GameEvent,
    oddsFormat: OddsFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val stateColor = when (event.state) {
        EventState.LIVE -> quant.positiveEv
        EventState.UPCOMING -> quant.correlationCyan
        EventState.FINAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (event.state == EventState.LIVE) quant.positiveEv.copy(alpha = 0.6f) else quant.cardBorder),
        modifier = modifier
            .width(215.dp)
            .testTag("schedule_chip_${event.id}")
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = quant.elevatedSurface,
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = event.league.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = quant.correlationCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = event.statusDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogoBadge(logoUrl = event.awayTeam.logoUrl, fallbackText = event.awayTeam.abbreviation, size = 22.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.awayTeam.abbreviation,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (event.state != EventState.UPCOMING && !event.awayTeam.score.isNullOrBlank()) {
                        event.awayTeam.score
                    } else {
                        event.marketSnapshot?.awayMoneyline?.let { OddsMath.formatOdds(it, oddsFormat) } ?: event.awayTeam.record
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogoBadge(logoUrl = event.homeTeam.logoUrl, fallbackText = event.homeTeam.abbreviation, size = 22.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.homeTeam.abbreviation,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (event.state != EventState.UPCOMING && !event.homeTeam.score.isNullOrBlank()) {
                        event.homeTeam.score
                    } else {
                        event.marketSnapshot?.homeMoneyline?.let { OddsMath.formatOdds(it, oddsFormat) } ?: event.homeTeam.record
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = quant.cardBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(6.dp))

            val snap = event.marketSnapshot
            val lineSummary = buildString {
                snap?.spreadLine?.let {
                    val sign = if (it > 0) "+" else ""
                    append("${event.homeTeam.abbreviation} $sign$it")
                }
                snap?.totalLine?.let {
                    if (isNotEmpty()) append(" • ")
                    append("O/U $it")
                }
                if (isEmpty()) append("Tap for Props & Lines")
            }
            Text(
                text = lineSummary,
                style = MaterialTheme.typography.labelSmall,
                color = quant.correlationCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SavedParlayDocCard(
    doc: SavedParlayDoc,
    oddsFormat: OddsFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (doc.sameGame) "SGP • ${doc.leagueCodes}" else doc.leagueCodes,
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    RhoChip(rho = doc.avgRho)
                    EvBadgePill(evPercent = doc.correlatedEvPercent)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove saved parlay",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (doc.legSummaries.isNotEmpty()) {
                        doc.legSummaries.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = OddsMath.formatPercent(doc.correlatedProb),
                        style = MaterialTheme.typography.titleLarge,
                        color = quant.positiveEv
                    )
                    Text(
                        text = OddsMath.formatOdds(doc.marketAmericanOdds.toInt(), oddsFormat),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SavedPropDocCard(
    doc: SavedPropDoc,
    oddsFormat: OddsFormat,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val isMore = doc.recommendedSide == "MORE"
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                headshotUrl = doc.headshotUrl,
                playerName = doc.playerName,
                teamAbbr = doc.teamAbbr,
                size = 44.dp,
                highlighted = true
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${doc.playerName} (${doc.teamAbbr} • ${doc.leagueCode})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${doc.matchup} • Line ${formatStatLine(doc.lineValue)} ${doc.statLabel} (Proj ${String.format(Locale.US, "%.1f", doc.projectedValue)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = quant.positiveEvContainer,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, quant.positiveEv)
                    ) {
                        Text(
                            text = if (isMore) "TAKE MORE ${formatStatLine(doc.lineValue)}" else "TAKE LESS ${formatStatLine(doc.lineValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.positiveEv,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${OddsMath.formatPercent(doc.winProb)} • ${OddsMath.formatOdds(if (isMore) doc.moreOdds.toInt() else doc.lessOdds.toInt(), oddsFormat)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove saved prop",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TopParlaySpotlightCard(
    parlay: CorrelatedParlay,
    oddsFormat: OddsFormat,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    onClickPlayerLeg: ((MarketLeg) -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val isRankOne = parlay.rank == 1
    val accentColor = when (parlay.rank) {
        1 -> quant.positiveEv
        2 -> quant.correlationCyan
        else -> quant.moderateAmber
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.85f),
            quant.cardBorder,
            accentColor.copy(alpha = 0.35f)
        )
    )

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, borderBrush),
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_parlay_card_${parlay.rank}")
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (isRankOne) Icons.Rounded.Bolt else Icons.Rounded.AutoGraph,
                                contentDescription = "Rank ${parlay.rank}",
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (parlay.rank) {
                                    1 -> "#1 TOP CORRELATED"
                                    2 -> "#2 HIGHEST PROBABILITY"
                                    else -> "#${parlay.rank} STRONG CORRELATION"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor
                            )
                        }
                    }

                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (parlay.isSameGame) "${parlay.legs.size}-PICK SGP • ${parlay.legs.first().league.code}"
                            else "${parlay.legs.size}-PICK • ${parlay.legs.map { it.league.code }.distinct().joinToString("/")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_parlay_btn_${parlay.rank}")
                ) {
                    Icon(
                        imageVector = if (parlay.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (parlay.isSaved) "Remove saved parlay" else "Save parlay",
                        tint = if (parlay.isSaved) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .border(1.dp, quant.cardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MULTI-SIM HIT%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = OddsMath.formatPercent(parlay.correlatedProb),
                        style = MaterialTheme.typography.displaySmall,
                        color = accentColor
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SHARP +EV",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    EvBadgePill(evPercent = parlay.correlatedEvPercent)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MARKET ODDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = OddsMath.formatOdds(parlay.marketParlayAmericanOdds, oddsFormat),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fair ${OddsMath.formatOdds(parlay.correlatedFairAmericanOdds, oddsFormat)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = quant.positiveEv
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Top Bettor Software 25k Multi-Simulation Consensus Strip
            Surface(
                color = quant.elevatedSurface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, quant.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${parlay.sharpSoftwareGrade} • ${parlay.simConsensusCount / 1000}K SIMS",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan
                        )
                        Text(
                            text = "Agreement ${OddsMath.formatPercent(parlay.simAgreementScore, decimals = 0)} • 0.25x Kelly ${String.format(Locale.US, "%.2f", parlay.quarterKellyPercent)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.positiveEv
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Gauss ${OddsMath.formatPercent(parlay.gaussianCopulaProb, decimals = 1)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Student-t ${OddsMath.formatPercent(parlay.studentTCopulaProb, decimals = 1)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pace Stress ${OddsMath.formatPercent(parlay.paceStressSimProb, decimals = 1)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            parlay.legs.forEachIndexed { index, leg ->
                val isPropLeg = leg.marketType == MarketType.PLAYER_PROP_MORE || leg.marketType == MarketType.PLAYER_PROP_LESS
                ParlayLegRow(
                    leg = leg,
                    oddsFormat = oddsFormat,
                    onClick = if (isPropLeg && onClickPlayerLeg != null) {
                        { onClickPlayerLeg(leg) }
                    } else null
                )
                if (index < parlay.legs.lastIndex) {
                    HorizontalDivider(
                        color = quant.cardBorder.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProbabilityLiftBar(
                independentProb = parlay.independentProb,
                correlatedProb = parlay.correlatedProb
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RhoChip(rho = parlay.avgRho)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = parlay.primaryThesis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ParlayLegRow(
    leg: MarketLeg,
    oddsFormat: OddsFormat,
    onClick: (() -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val isProp = leg.marketType == MarketType.PLAYER_PROP_MORE || leg.marketType == MarketType.PLAYER_PROP_LESS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) {
                    base
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("clickable_leg_${leg.id}")
                } else base
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isProp) {
            PlayerAvatar(
                headshotUrl = leg.homeLogoUrl,
                playerName = leg.selectionLabel,
                teamAbbr = leg.homeTeamName,
                size = 32.dp,
                highlighted = true
            )
        } else {
            TeamLogoBadge(
                logoUrl = if (leg.sideTag == "AWAY") leg.awayLogoUrl else leg.homeLogoUrl,
                fallbackText = leg.league.code,
                size = 30.dp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leg.selectionLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isProp && onClick != null) {
                    "${leg.matchupShort} • Tap player for why"
                } else {
                    "${leg.matchupShort} • ${leg.marketType.label} • ${leg.startTimeDisplay}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isProp && onClick != null) quant.correlationCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = OddsMath.formatOdds(leg.offeredAmericanOdds, oddsFormat),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${OddsMath.formatPercent(leg.fairTrueProb)} fair • ${OddsMath.formatPercent(leg.evPercent, includeSign = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (leg.evPercent >= 0) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompactParlayCard(
    parlay: CorrelatedParlay,
    oddsFormat: OddsFormat,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_parlay_card_${parlay.rank}")
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${parlay.rank}",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.correlationCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    RhoChip(rho = parlay.avgRho)
                    EvBadgePill(evPercent = parlay.correlatedEvPercent)
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (parlay.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (parlay.isSaved) "Remove saved parlay" else "Save parlay",
                        tint = if (parlay.isSaved) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    parlay.legs.forEach { leg ->
                        Text(
                            text = "• ${leg.selectionLabel} (${leg.matchupShort})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = OddsMath.formatPercent(parlay.correlatedProb),
                        style = MaterialTheme.typography.titleLarge,
                        color = quant.positiveEv
                    )
                    Text(
                        text = OddsMath.formatOdds(parlay.marketParlayAmericanOdds, oddsFormat),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsDisclaimerFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(id = R.string.analytics_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

fun formatStatLine(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}
