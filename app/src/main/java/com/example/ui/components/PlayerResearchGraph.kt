package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.GameEvent
import com.example.domain.model.InjurySeverity
import com.example.domain.model.PlayerInjuryInfo
import com.example.domain.model.PreviousGameAvailability
import com.example.domain.model.PreviousGameStatEntry
import com.example.ui.theme.LocalQuantColors
import java.util.Locale
import kotlin.math.max

@Composable
fun PropHistoryBarChartCard(
    statLabel: String,
    lineValue: Double,
    gameLogs: List<PreviousGameStatEntry>,
    selectedGameId: String?,
    onSelectGame: (PreviousGameStatEntry) -> Unit,
    onAdjustLine: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val points = remember(gameLogs, statLabel) {
        gameLogs.mapNotNull { entry ->
            val v = entry.propValues[statLabel] ?: return@mapNotNull null
            entry to v
        }
    }

    val overCount = points.count { it.second > lineValue }
    val totalCount = points.size
    val hitPct = if (totalCount > 0) (overCount * 100.0 / totalCount) else 0.0
    val l5Avg = points.takeLast(5).map { it.second }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val l10Avg = points.map { it.second }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val leanMore = hitPct >= 50.0 || l5Avg >= lineValue

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top KPI & Alt-Line Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${statLabel.uppercase(Locale.US)} • LAST $totalCount GAMES",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.correlationCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$overCount/$totalCount MORE (${String.format(Locale.US, "%.0f%%", hitPct)})",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (leanMore) quant.positiveEv else quant.negativeCoral
                        )
                        Surface(
                            color = if (leanMore) quant.positiveEvContainer else quant.negativeContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (leanMore) quant.positiveEv else quant.negativeCoral)
                        ) {
                            Text(
                                text = if (leanMore) "LEAN MORE" else "LEAN LESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (leanMore) quant.positiveEv else quant.negativeCoral,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "L5 Avg: ${String.format(Locale.US, "%.1f", l5Avg)} • L10 Avg: ${String.format(Locale.US, "%.1f", l10Avg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Interactive Alt-Line Stepper (-0.5 / +0.5)
                Surface(
                    color = quant.elevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, quant.cardBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjustLine(-0.5) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("research_line_minus")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease prop line",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatStatLine(lineValue),
                                style = MaterialTheme.typography.titleMedium,
                                color = quant.correlationCyan
                            )
                        }
                        IconButton(
                            onClick = { onAdjustLine(0.5) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("research_line_plus")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase prop line",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (points.isEmpty()) {
                Text(
                    text = "No logged values for $statLabel in recent games.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxVal = max(lineValue * 1.25, (points.maxOf { it.second } * 1.15).coerceAtLeast(1.0))
                val chartHeightDp = 165.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp)
                ) {
                    // Background dashed threshold line on Canvas
                    val lineColor = quant.correlationCyan
                    val gridColor = quant.cardBorder.copy(alpha = 0.5f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val chartTop = 20.dp.toPx()
                        val chartBottom = size.height - 6.dp.toPx()
                        val usableH = (chartBottom - chartTop).coerceAtLeast(10f)

                        // Subtle horizontal baseline
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, chartBottom),
                            end = Offset(size.width, chartBottom),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Dashed prop line threshold
                        val lineRatio = (lineValue / maxVal).toFloat().coerceIn(0f, 1f)
                        val thresholdY = chartBottom - (lineRatio * usableH)
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, thresholdY),
                            end = Offset(size.width, thresholdY),
                            strokeWidth = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }

                    // Interactive Bar Columns
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        points.forEach { (entry, value) ->
                            val isOver = value > lineValue
                            val isSelected = entry.eventId == selectedGameId
                            val barColor = if (isOver) quant.positiveEv else quant.negativeCoral
                            val heightFraction = (value / maxVal).toFloat().coerceIn(0.06f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .background(quant.correlationContainer.copy(alpha = 0.45f))
                                                .border(1.dp, quant.correlationCyan, RoundedCornerShape(6.dp))
                                        } else Modifier
                                    )
                                    .clickable { onSelectGame(entry) }
                                    .padding(top = 2.dp, bottom = 6.dp)
                                    .testTag("chart_bar_${entry.eventId}")
                            ) {
                                Text(
                                    text = formatStatLine(value),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOver) quant.positiveEv else quant.negativeCoral,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth(0.72f)
                                            .fillMaxHeight(heightFraction)
                                    ) {
                                        drawRoundRect(
                                            color = barColor,
                                            size = Size(size.width, size.height),
                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // X-axis Opponent & Date Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    points.forEach { (entry, _) ->
                        val isSelected = entry.eventId == selectedGameId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectGame(entry) }
                        ) {
                            Text(
                                text = entry.opponentAbbr,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) quant.correlationCyan else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = entry.dateDisplay,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap any game bar above to inspect full stats, why they cleared/missed, and Who Played / Didn't Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = quant.correlationCyan
                )
            }
        }
    }
}

@Composable
fun PreviousGameAvailabilityCard(
    availability: PreviousGameAvailability,
    researchedPlayerId: String,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    var selectedFilter by remember(availability.eventId) { mutableIntStateOf(0) } // 0 = Didn't Play, 1 = Played

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "PREVIOUS GAME LINEUP • ${availability.matchSummary.uppercase(Locale.US)}",
                style = MaterialTheme.typography.labelMedium,
                color = quant.correlationCyan
            )
            Text(
                text = "Teammate & opponent availability context for this game",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (availability.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = quant.correlationCyan
                    )
                    Text(
                        text = "Loading box score & DNP / Inactive list...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        leadingIcon = {
                            Icon(Icons.Rounded.PersonOff, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text("Didn't Play (${availability.didNotPlayPlayers.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = quant.negativeContainer,
                            selectedLabelColor = quant.negativeCoral
                        ),
                        modifier = Modifier.testTag("tab_did_not_play")
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        leadingIcon = {
                            Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text("Played (${availability.playedPlayers.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = quant.positiveEvContainer,
                            selectedLabelColor = quant.positiveEv
                        ),
                        modifier = Modifier.testTag("tab_who_played")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedFilter == 0) {
                    if (availability.didNotPlayPlayers.isEmpty()) {
                        Text(
                            text = "No DNP or inactive players flagged in this game's box score.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availability.didNotPlayPlayers.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(quant.elevatedSurface)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${p.playerName} (${p.teamAbbr} ${p.position})".trim(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = p.didNotPlayReason ?: "Inactive / Did Not Play",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = quant.negativeContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, quant.negativeCoral.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = p.minutesOrStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = quant.negativeCoral,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (availability.playedPlayers.isEmpty()) {
                        Text(
                            text = "Player box score stats unavailable for this matchup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availability.playedPlayers.take(20).forEach { p ->
                                val isTarget = p.playerId == researchedPlayerId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isTarget) quant.positiveEvContainer else quant.elevatedSurface
                                        )
                                        .then(
                                            if (isTarget) Modifier.border(1.dp, quant.positiveEv, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${p.playerName} (${p.teamAbbr} ${p.position})".trim(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isTarget) quant.positiveEv else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = p.statSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = p.minutesOrStatus,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isTarget) quant.positiveEv else quant.correlationCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchupInjuriesCard(
    event: GameEvent,
    injuries: List<PlayerInjuryInfo>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocalHospital,
                        contentDescription = null,
                        tint = quant.moderateAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MATCHUP INJURY REPORT • ${event.shortName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.moderateAmber
                    )
                }
                if (!isLoading) {
                    Text(
                        text = "${injuries.size} listed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = quant.moderateAmber
                    )
                    Text(
                        text = "Checking ESPN injury report for ${event.awayTeam.abbreviation} & ${event.homeTeam.abbreviation}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (injuries.isEmpty()) {
                Text(
                    text = "No players currently listed on the ESPN injury report for ${event.awayTeam.abbreviation} vs ${event.homeTeam.abbreviation}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    injuries.forEach { inj ->
                        val (bg, fg) = when (inj.severity) {
                            InjurySeverity.OUT, InjurySeverity.DOUBTFUL -> quant.negativeContainer to quant.negativeCoral
                            InjurySeverity.QUESTIONABLE, InjurySeverity.DAY_TO_DAY -> quant.moderateContainer to quant.moderateAmber
                            InjurySeverity.PROBABLE -> quant.positiveEvContainer to quant.positiveEv
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(quant.elevatedSurface)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerAvatar(
                                headshotUrl = inj.headshotUrl,
                                playerName = inj.playerName,
                                teamAbbr = inj.teamAbbr,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${inj.playerName} (${inj.teamAbbr} ${inj.position})".trim(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = inj.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = inj.status.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviousGameLogTableCard(
    gameLogs: List<PreviousGameStatEntry>,
    selectedGameId: String?,
    onSelectGame: (PreviousGameStatEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "GAME-BY-GAME MULTI-PROP STATS (NEWEST FIRST)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            gameLogs.reversed().forEachIndexed { idx, entry ->
                val isSelected = entry.eventId == selectedGameId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) quant.correlationContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .clickable { onSelectGame(entry) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.38f)) {
                        Text(
                            text = "${entry.dateDisplay} ${entry.opponentAbbr}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) quant.correlationCyan else MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.scoreResult.isNotBlank()) {
                            Text(
                                text = entry.scoreResult,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = entry.rawStatsSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.62f),
                        textAlign = TextAlign.End
                    )
                }
                if (idx < gameLogs.lastIndex) {
                    HorizontalDivider(
                        color = quant.cardBorder.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
