package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.math.PlayerPropSimEngine
import com.example.domain.model.OddsFormat
import com.example.domain.model.PlayerResearchProfile
import com.example.domain.model.PreviousGameAvailability
import com.example.domain.model.PreviousGameStatEntry
import com.example.domain.model.PropSimScenario
import com.example.domain.model.PropSimulationResult
import com.example.ui.theme.LocalQuantColors
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedGameResearchDetailCard(
    profile: PlayerResearchProfile,
    selectedGame: PreviousGameStatEntry,
    activeStat: String,
    activeLine: Double,
    availability: PreviousGameAvailability?,
    onSelectPropStat: (String) -> Unit,
    onSelectAltLine: (Double) -> Unit,
    onAdjustLine: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val actualValue = selectedGame.propValues[activeStat]
    val cleared = actualValue != null && actualValue > activeLine
    val margin = actualValue?.minus(activeLine)
    val baseLine = profile.propLinesByLabel[activeStat] ?: activeLine
    val altLinePresets = remember(baseLine, activeStat) {
        PlayerPropSimEngine.buildAltLinePresets(baseLine, activeStat)
    }
    val stepInc = remember(baseLine, activeStat) {
        PlayerPropSimEngine.stepIncrementForStat(baseLine, activeStat)
    }

    val avgMinutes = remember(profile.gameLogs) {
        profile.gameLogs.mapNotNull { it.minutesValue }.takeIf { it.isNotEmpty() }?.average()
    }
    val avgStat = remember(profile.gameLogs, activeStat) {
        profile.gameLogs.mapNotNull { it.propValues[activeStat] }.takeIf { it.isNotEmpty() }?.average()
    }

    val bannerBg = when {
        actualValue == null -> quant.elevatedSurface
        cleared -> quant.positiveEvContainer
        else -> quant.negativeContainer
    }
    val bannerAccent = when {
        actualValue == null -> quant.correlationCyan
        cleared -> quant.positiveEv
        else -> quant.negativeCoral
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val reasons = remember(
        profile.teamAbbr,
        selectedGame,
        activeStat,
        activeLine,
        availability,
        avgMinutes,
        avgStat,
        quant
    ) {
        buildSelectedGameClearanceReasons(
            playerName = profile.playerName,
            playerTeamAbbr = profile.teamAbbr,
            entry = selectedGame,
            statLabel = activeStat,
            lineValue = activeLine,
            avgMinutes = avgMinutes,
            avgStat = avgStat,
            availability = availability,
            positiveColor = quant.positiveEv,
            negativeColor = quant.negativeCoral,
            cyanColor = quant.correlationCyan,
            amberColor = quant.moderateAmber,
            neutralColor = onSurfaceVariant
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.5.dp, bannerAccent.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("selected_game_detail_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Game Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED GAME BREAKDOWN • ${selectedGame.dateDisplay} ${selectedGame.opponentAbbr}",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.correlationCyan
                    )
                    if (selectedGame.scoreResult.isNotBlank() || selectedGame.minutesDisplay.isNotBlank()) {
                        val metaStr = buildString {
                            if (selectedGame.scoreResult.isNotBlank()) append(selectedGame.scoreResult)
                            if (selectedGame.minutesDisplay.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("${selectedGame.minutesDisplay} MIN")
                            }
                        }
                        Text(
                            text = metaStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (actualValue != null) {
                    Surface(
                        color = bannerBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, bannerAccent)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (cleared) Icons.Rounded.CheckCircle else Icons.Rounded.HighlightOff,
                                contentDescription = null,
                                tint = bannerAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (cleared) "CLEARED" else "MISSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = bannerAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Output vs Active Line Hero
            Surface(
                color = bannerBg.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, bannerAccent.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeStat.uppercase(Locale.US)} VS ALT LINE ${formatStatLine(activeLine)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (actualValue != null) {
                                val deltaText = String.format(Locale.US, "%+.1f", margin ?: 0.0)
                                "${formatStatLine(actualValue)} $activeStat ($deltaText)"
                            } else {
                                "No $activeStat recorded"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = bannerAccent
                        )
                    }

                    // Inline Alt-Line fine stepper
                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, quant.cardBorder)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onAdjustLine(-stepInc) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_detail_line_minus")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Lower alt line",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = formatStatLine(activeLine),
                                style = MaterialTheme.typography.labelLarge,
                                color = quant.correlationCyan
                            )
                            IconButton(
                                onClick = { onAdjustLine(stepInc) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_detail_line_plus")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Raise alt line",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switch Prop Category & Quick Alt Lines for Selected Game
            if (selectedGame.propValues.size > 1) {
                Text(
                    text = "CHECK PROP IN THIS GAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedGame.propValues.forEach { (statKey, statVal) ->
                        val refLine = profile.propLinesByLabel[statKey] ?: 0.5
                        val hit = statVal > refLine
                        val isSelected = statKey == activeStat
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPropStat(statKey) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (hit) quant.positiveEvContainer else quant.negativeContainer,
                                selectedLabelColor = if (hit) quant.positiveEv else quant.negativeCoral
                            ),
                            label = {
                                Text("$statKey: ${formatStatLine(statVal)}")
                            },
                            modifier = Modifier.testTag("selected_game_prop_$statKey")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "TEST ALT LINES FOR ${activeStat.uppercase(Locale.US)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                altLinePresets.forEach { preset ->
                    val isSelected = abs(preset - activeLine) < 0.01
                    val wouldClear = actualValue != null && actualValue > preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectAltLine(preset) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = quant.correlationContainer,
                            selectedLabelColor = quant.correlationCyan
                        ),
                        label = {
                            val tag = if (actualValue != null) {
                                if (wouldClear) " ✓" else " ✕"
                            } else ""
                            Text("${formatStatLine(preset)}$tag")
                        },
                        modifier = Modifier.testTag("alt_line_chip_${formatStatLine(preset)}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Why they cleared or missed
            Text(
                text = if (cleared) "WHY THEY CLEARED ${formatStatLine(activeLine)} $activeStat"
                else "WHY THEY MISSED ${formatStatLine(activeLine)} $activeStat",
                style = MaterialTheme.typography.labelMedium,
                color = bannerAccent
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(1.dp, quant.cardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reasons.forEachIndexed { idx, reason ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = reason.icon,
                            contentDescription = null,
                            tint = reason.color,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reason.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = reason.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (idx < reasons.lastIndex) {
                        HorizontalDivider(color = quant.cardBorder.copy(alpha = 0.45f))
                    }
                }
            }

            // Full Box Score Stats recorded for that game
            if (selectedGame.fullStats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FULL PLAYER BOXSCORE IN THIS GAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedGame.fullStats.forEach { (statName, statValue) ->
                        Surface(
                            color = quant.elevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, quant.cardBorder)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = statName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = statValue,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
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
fun PlayerPropSimulatorCard(
    profile: PlayerResearchProfile,
    activeStat: String,
    activeLine: Double,
    selectedScenario: PropSimScenario,
    simResult: PropSimulationResult?,
    oddsFormat: OddsFormat,
    onSelectScenario: (PropSimScenario) -> Unit,
    onSelectAltLine: (Double) -> Unit,
    onAdjustLine: (Double) -> Unit,
    onRunSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val baseLine = profile.propLinesByLabel[activeStat] ?: activeLine
    val altLines = remember(baseLine, activeStat) {
        PlayerPropSimEngine.buildAltLinePresets(baseLine, activeStat)
    }
    val stepInc = remember(baseLine, activeStat) {
        PlayerPropSimEngine.stepIncrementForStat(baseLine, activeStat)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_prop_simulator_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoGraph,
                        contentDescription = null,
                        tint = quant.correlationCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "MONTE CARLO OUTCOME SIMULATOR (10K)",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.correlationCyan
                        )
                        Text(
                            text = "${profile.playerName} • $activeStat @ ${formatStatLine(activeLine)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onRunSimulation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = quant.correlationCyan,
                        contentColor = Color(0xFF061018)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("run_prop_simulation_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simulate", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scenario Selector
            Text(
                text = "GAME SCRIPT SCENARIO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PropSimScenario.entries.forEach { scenario ->
                    FilterChip(
                        selected = selectedScenario == scenario,
                        onClick = { onSelectScenario(scenario) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = quant.positiveEvContainer,
                            selectedLabelColor = quant.positiveEv
                        ),
                        label = { Text(scenario.label) },
                        modifier = Modifier.testTag("sim_scenario_${scenario.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Alt Line Switcher + Fine Stepper inside Simulator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    altLines.forEach { alt ->
                        val isSel = abs(alt - activeLine) < 0.01
                        FilterChip(
                            selected = isSel,
                            onClick = { onSelectAltLine(alt) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = quant.correlationContainer,
                                selectedLabelColor = quant.correlationCyan
                            ),
                            label = { Text("Alt ${formatStatLine(alt)}") },
                            modifier = Modifier.testTag("sim_alt_${formatStatLine(alt)}")
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustLine(-stepInc) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease simulated line", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onAdjustLine(stepInc) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase simulated line", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (simResult != null) {
                Spacer(modifier = Modifier.height(10.dp))

                // MORE vs LESS Simulated Probabilities & Fair Odds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val favorsMore = simResult.moreProb >= 0.50
                    SimMetricTile(
                        label = "SIM MORE ${formatStatLine(simResult.lineValue)}",
                        value = OddsMath.formatPercent(simResult.moreProb),
                        subValue = "Fair ${OddsMath.formatOdds(simResult.fairMoreOdds, oddsFormat)}",
                        accent = if (favorsMore) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant,
                        highlighted = favorsMore,
                        modifier = Modifier.weight(1f)
                    )
                    SimMetricTile(
                        label = "SIM LESS ${formatStatLine(simResult.lineValue)}",
                        value = OddsMath.formatPercent(simResult.lessProb),
                        subValue = "Fair ${OddsMath.formatOdds(simResult.fairLessOdds, oddsFormat)}",
                        accent = if (!favorsMore) quant.positiveEv else quant.negativeCoral,
                        highlighted = !favorsMore,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Floor / Mean / Median / Ceiling Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimMiniStatBox(
                        label = "P10 Floor",
                        value = String.format(Locale.US, "%.1f", simResult.floorP10),
                        modifier = Modifier.weight(1f)
                    )
                    SimMiniStatBox(
                        label = "Sim Mean",
                        value = String.format(Locale.US, "%.1f", simResult.simMean),
                        valueColor = quant.correlationCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SimMiniStatBox(
                        label = "Median",
                        value = String.format(Locale.US, "%.1f", simResult.simMedian),
                        modifier = Modifier.weight(1f)
                    )
                    SimMiniStatBox(
                        label = "P90 Ceiling",
                        value = String.format(Locale.US, "%.1f", simResult.ceilingP90),
                        valueColor = quant.positiveEv,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulated Outcome Distribution Histogram
                Text(
                    text = "SIMULATED DISTRIBUTION (${selectedScenario.subtitle.uppercase(Locale.US)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .border(1.dp, quant.cardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    simResult.histogram.forEach { bucket ->
                        val barColor = if (bucket.isMoreSide) quant.positiveEv else quant.negativeCoral
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .fillMaxHeight(bucket.fraction.toFloat().coerceIn(0.06f, 1f))
                                ) {
                                    drawRoundRect(
                                        color = barColor,
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = bucket.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bucket.isMoreSide) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class GameClearanceReason(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val color: Color
)

private fun buildSelectedGameClearanceReasons(
    playerName: String,
    playerTeamAbbr: String,
    entry: PreviousGameStatEntry,
    statLabel: String,
    lineValue: Double,
    avgMinutes: Double?,
    avgStat: Double?,
    availability: PreviousGameAvailability?,
    positiveColor: Color,
    negativeColor: Color,
    cyanColor: Color,
    amberColor: Color,
    neutralColor: Color
): List<GameClearanceReason> {
    val reasons = mutableListOf<GameClearanceReason>()
    val actual = entry.propValues[statLabel]
    val cleared = actual != null && actual > lineValue

    // 1. Outcome vs Line + Seasonal Baseline
    if (actual != null) {
        val diff = actual - lineValue
        val avgNote = avgStat?.let {
            " (vs ${String.format(Locale.US, "%.1f", it)} recent avg)"
        }.orEmpty()
        reasons.add(
            GameClearanceReason(
                icon = if (cleared) Icons.Rounded.CheckCircle else Icons.Rounded.HighlightOff,
                title = if (cleared) "Cleared Line by ${String.format(Locale.US, "%+.1f", diff)}"
                else "Fell Short by ${String.format(Locale.US, "%.1f", abs(diff))}",
                detail = "$playerName finished with ${formatStatLine(actual)} $statLabel against the ${formatStatLine(lineValue)} threshold$avgNote.",
                color = if (cleared) positiveColor else negativeColor
            )
        )
    }

    // 2. Minutes / Playing Time & Blowout / Clutch Game Score Margin
    val scoreMargin = parseGameScoreMargin(entry.scoreResult)
    val minVal = entry.minutesValue
    if (minVal != null && avgMinutes != null && avgMinutes > 0.5) {
        val minDiff = minVal - avgMinutes
        val minTitle = when {
            minDiff >= 3.0 -> "Heavy Run (${entry.minutesDisplay} MIN, +${String.format(Locale.US, "%.1f", minDiff)} vs avg)"
            minDiff <= -4.0 -> "Reduced Minutes (${entry.minutesDisplay} MIN, ${String.format(Locale.US, "%.1f", minDiff)} vs avg)"
            else -> "Standard Rotation (${entry.minutesDisplay} MIN)"
        }
        val marginNote = when {
            scoreMargin != null && scoreMargin >= 18 -> " Large ${scoreMargin}-point margin impacted late starter run."
            scoreMargin != null && scoreMargin <= 5 -> " Competitive ${scoreMargin}-point finish kept primary starters in for closing possessions."
            else -> ""
        }
        reasons.add(
            GameClearanceReason(
                icon = Icons.Rounded.Schedule,
                title = minTitle,
                detail = if (minDiff <= -4.0 && !cleared) {
                    "Playing ${entry.minutesDisplay} mins (below the ${String.format(Locale.US, "%.1f", avgMinutes)} min avg) directly capped opportunity.$marginNote"
                } else if (minDiff >= 3.0 && cleared) {
                    "Extended court time (${entry.minutesDisplay} mins vs ${String.format(Locale.US, "%.1f", avgMinutes)} avg) created extra counting-stat volume.$marginNote"
                } else {
                    "Logged ${entry.minutesDisplay} minutes compared to a ${String.format(Locale.US, "%.1f", avgMinutes)} min season pace.$marginNote"
                },
                color = if (minDiff <= -4.0) negativeColor else cyanColor
            )
        )
    } else if (entry.scoreResult.isNotBlank()) {
        val marginText = if (scoreMargin != null && scoreMargin >= 15) {
            "Wide ${scoreMargin}-pt score gap (${entry.scoreResult}) shifted late-game usage."
        } else {
            "Final game flow (${entry.scoreResult}) shaped possession tempo and matchup script."
        }
        reasons.add(
            GameClearanceReason(
                icon = Icons.Rounded.Schedule,
                title = "Game Script (${entry.scoreResult})",
                detail = marginText,
                color = cyanColor
            )
        )
    }

    // 3. Shooting / Conversion Efficiency & Turnovers
    val fgRatio = parseMadeAttemptRatio(entry.fullStats["FG"])
    val tpRatio = parseMadeAttemptRatio(entry.fullStats["3PT"])
    val effSummary = entry.efficiencySummary
    if (effSummary.isNotBlank()) {
        val fgPct = fgRatio?.let { (m, a) -> if (a > 0) (m * 100.0 / a) else null }
        val effTitle = when {
            fgPct != null && fgPct >= 52.0 -> "Hot Efficiency (${String.format(Locale.US, "%.0f%%", fgPct)} FG)"
            fgPct != null && fgPct <= 36.0 -> "Cold Shooting (${String.format(Locale.US, "%.0f%%", fgPct)} FG)"
            else -> "Boxscore Efficiency Splits"
        }
        val extraNote = when {
            fgPct != null && fgPct >= 52.0 && cleared -> " High shot-making conversion propelled the stat ceiling."
            fgPct != null && fgPct <= 36.0 && !cleared -> " Sub-36% shooting accuracy held back total output despite attempts."
            tpRatio != null && tpRatio.second >= 6 -> " Attempted ${tpRatio.second} threes (${tpRatio.first}-${tpRatio.second})."
            else -> ""
        }
        reasons.add(
            GameClearanceReason(
                icon = Icons.Rounded.Bolt,
                title = effTitle,
                detail = "$effSummary.$extraNote",
                color = when {
                    fgPct != null && fgPct >= 52.0 -> positiveColor
                    fgPct != null && fgPct <= 36.0 -> negativeColor
                    else -> amberColor
                }
            )
        )
    }

    // 4. Lineup & Teammate DNP Context from PreviousGameAvailability
    if (availability != null && !availability.isLoading) {
        val sameTeamDnp = availability.didNotPlayPlayers.filter {
            it.teamAbbr.equals(playerTeamAbbr, ignoreCase = true)
        }
        val oppDnp = availability.didNotPlayPlayers.filter {
            !it.teamAbbr.equals(playerTeamAbbr, ignoreCase = true)
        }
        if (sameTeamDnp.isNotEmpty() || oppDnp.isNotEmpty()) {
            val detailText = buildString {
                if (sameTeamDnp.isNotEmpty()) {
                    val names = sameTeamDnp.take(3).joinToString(", ") { it.playerName }
                    append("Teammates out ($names) opened extra team usage/touches for $playerName. ")
                }
                if (oppDnp.isNotEmpty()) {
                    val names = oppDnp.take(3).joinToString(", ") { it.playerName }
                    append("Opponent was missing $names.")
                }
            }.trim()
            reasons.add(
                GameClearanceReason(
                    icon = Icons.Rounded.Groups,
                    title = if (sameTeamDnp.isNotEmpty()) "Lineup Boost (${sameTeamDnp.size} Teammate DNP)" else "Opponent Missing Rotation Players",
                    detail = detailText,
                    color = if (sameTeamDnp.isNotEmpty()) positiveColor else cyanColor
                )
            )
        } else {
            reasons.add(
                GameClearanceReason(
                    icon = Icons.Rounded.Groups,
                    title = "Full Rotation Available",
                    detail = "No key DNP absences flagged in this game's boxscore — usage came under normal team rotation.",
                    color = neutralColor
                )
            )
        }
    }

    return reasons
}

private fun parseGameScoreMargin(scoreResult: String): Int? {
    val regex = Regex("(\\d+)\\s*-\\s*(\\d+)")
    val match = regex.find(scoreResult) ?: return null
    val a = match.groupValues[1].toIntOrNull() ?: return null
    val b = match.groupValues[2].toIntOrNull() ?: return null
    return abs(a - b)
}

private fun parseMadeAttemptRatio(raw: String?): Pair<Int, Int>? {
    if (raw.isNullOrBlank() || "-" !in raw) return null
    val parts = raw.split("-")
    val made = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val att = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    return made to att
}

@Composable
private fun SimMetricTile(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Surface(
        color = if (highlighted) quant.positiveEvContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) accent else quant.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SimMiniStatBox(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, quant.cardBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
