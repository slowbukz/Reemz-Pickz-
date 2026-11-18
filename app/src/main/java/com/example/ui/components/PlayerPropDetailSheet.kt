package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.model.GameEvent
import com.example.domain.model.InjurySeverity
import com.example.domain.model.OddsFormat
import com.example.domain.model.PlayerInjuryInfo
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PlayerResearchProfile
import com.example.domain.model.PropSide
import com.example.ui.theme.LocalQuantColors
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPropDetailSheet(
    prop: PlayerPropPick,
    event: GameEvent?,
    researchProfile: PlayerResearchProfile?,
    matchupInjuries: List<PlayerInjuryInfo>,
    isLoadingContext: Boolean,
    isInSandbox: Boolean,
    oddsFormat: OddsFormat,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleSandbox: () -> Unit,
    onOpenFullResearch: () -> Unit
) {
    val quant = LocalQuantColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isMore = prop.recommendedSide == PropSide.MORE
    val recProb = if (isMore) prop.moreProb else prop.lessProb
    val recOdds = if (isMore) prop.moreAmericanOdds else prop.lessAmericanOdds
    val recEv = if (isMore) prop.moreEvPercent else prop.lessEvPercent
    val recLabel = if (isMore) "TAKE MORE ${formatStatLine(prop.lineValue)} ${prop.statLabel}" else "TAKE LESS ${formatStatLine(prop.lineValue)} ${prop.statLabel}"
    val projDelta = prop.projectedValue - prop.lineValue
    val projDeltaPct = if (prop.lineValue > 0.0) (projDelta / prop.lineValue) * 100.0 else 0.0

    val recentValues = remember(researchProfile, prop.statLabel) {
        researchProfile?.gameLogs.orEmpty().mapNotNull { entry ->
            entry.propValues[prop.statLabel]?.let { value -> entry to value }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("player_prop_detail_sheet")
        ) {
            // Athlete Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerAvatar(
                    headshotUrl = prop.headshotUrl,
                    playerName = prop.playerName,
                    teamAbbr = prop.teamAbbr,
                    size = 60.dp,
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
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${prop.league.code} • ${prop.teamAbbr} ${prop.position}".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = quant.correlationCyan,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = event?.name ?: prop.matchupShort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("prop_detail_save_icon")
                ) {
                    Icon(
                        imageVector = if (prop.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (prop.isSaved) "Remove saved prop" else "Save prop",
                        tint = if (prop.isSaved) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_prop_detail_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player prop breakdown"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recommended Pick Banner
            Surface(
                color = quant.positiveEvContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, quant.positiveEv),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isMore) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                                contentDescription = null,
                                tint = quant.positiveEv,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "RECOMMENDED PICK",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.positiveEv
                            )
                        }
                        EvBadgePill(evPercent = recEv)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = recLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PropDetailStatBox(
                            label = "Market Line",
                            value = formatStatLine(prop.lineValue),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        PropDetailStatBox(
                            label = "Model Proj",
                            value = String.format(Locale.US, "%.1f (%+.1f)", prop.projectedValue, projDelta),
                            valueColor = quant.positiveEv,
                            modifier = Modifier.weight(1.25f)
                        )
                        PropDetailStatBox(
                            label = "Win Prob",
                            value = OddsMath.formatPercent(recProb),
                            valueColor = quant.correlationCyan,
                            modifier = Modifier.weight(1f)
                        )
                        PropDetailStatBox(
                            label = "Odds",
                            value = OddsMath.formatOdds(recOdds, oddsFormat),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.95f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Side-by-side MORE vs LESS comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SideComparisonCard(
                    title = "MORE ${formatStatLine(prop.lineValue)}",
                    winProb = prop.moreProb,
                    odds = prop.moreAmericanOdds,
                    ev = prop.moreEvPercent,
                    oddsFormat = oddsFormat,
                    highlighted = isMore,
                    modifier = Modifier.weight(1f)
                )
                SideComparisonCard(
                    title = "LESS ${formatStatLine(prop.lineValue)}",
                    winProb = prop.lessProb,
                    odds = prop.lessAmericanOdds,
                    ev = prop.lessEvPercent,
                    oddsFormat = oddsFormat,
                    highlighted = !isMore,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Why This Is a Good Pick For This Game
            Text(
                text = "WHY THIS IS A GOOD PICK FOR THIS GAME",
                style = MaterialTheme.typography.labelMedium,
                color = quant.correlationCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            val gameReasons = remember(prop, event, matchupInjuries, quant) {
                buildGameSpecificPickReasons(
                    prop = prop,
                    event = event,
                    injuries = matchupInjuries,
                    projDelta = projDelta,
                    projDeltaPct = projDeltaPct,
                    positiveColor = quant.positiveEv,
                    cyanColor = quant.correlationCyan,
                    amberColor = quant.moderateAmber,
                    neutralColor = onSurfaceVariantColor
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, quant.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    gameReasons.forEachIndexed { idx, reason ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = reason.icon,
                                contentDescription = null,
                                tint = reason.accentColor,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reason.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = reason.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (idx < gameReasons.lastIndex) {
                            HorizontalDivider(
                                color = quant.cardBorder.copy(alpha = 0.65f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Form & Hit Rate Snapshot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT GAME FORM VS LINE (${formatStatLine(prop.lineValue)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoadingContext) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = quant.correlationCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, quant.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isLoadingContext && recentValues.isEmpty()) {
                        Text(
                            text = "Loading recent game logs, L5/L10 averages & injury status...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (recentValues.isEmpty()) {
                        Text(
                            text = "No recent ${prop.statLabel.lowercase(Locale.US)} logs returned yet from ESPN. Open Full Research Graphs below to inspect all categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val last10 = recentValues.takeLast(10)
                        val last5 = recentValues.takeLast(5)
                        val avg5 = last5.map { it.second }.average()
                        val avg10 = last10.map { it.second }.average()
                        val hits = last10.count { (_, valNum) ->
                            if (isMore) valNum > prop.lineValue else valNum < prop.lineValue
                        }
                        val hitPct = hits.toDouble() / last10.size.toDouble()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PropDetailStatBox(
                                label = "L5 Avg",
                                value = String.format(Locale.US, "%.1f", avg5),
                                valueColor = if ((isMore && avg5 > prop.lineValue) || (!isMore && avg5 < prop.lineValue)) quant.positiveEv else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            PropDetailStatBox(
                                label = "L10 Avg",
                                value = String.format(Locale.US, "%.1f", avg10),
                                valueColor = if ((isMore && avg10 > prop.lineValue) || (!isMore && avg10 < prop.lineValue)) quant.positiveEv else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            PropDetailStatBox(
                                label = "Hit Rate (L${last10.size})",
                                value = "$hits/${last10.size} (${(hitPct * 100).toInt()}%)",
                                valueColor = if (hitPct >= 0.6) quant.positiveEv else quant.correlationCyan,
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            last10.reversed().forEach { (entry, valNum) ->
                                val cleared = if (isMore) valNum > prop.lineValue else valNum < prop.lineValue
                                val chipBg = if (cleared) quant.positiveEvContainer else quant.negativeContainer
                                val chipBorder = if (cleared) quant.positiveEv.copy(alpha = 0.6f) else quant.negativeCoral.copy(alpha = 0.45f)
                                val chipText = if (cleared) quant.positiveEv else quant.negativeCoral

                                Surface(
                                    color = chipBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, chipBorder)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${entry.homeAway} ${entry.opponentAbbr}".trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatStatLine(valNum),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = chipText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleSandbox,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, if (isInSandbox) quant.correlationCyan else quant.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isInSandbox) quant.correlationCyan else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("prop_detail_sandbox_btn")
                ) {
                    Icon(
                        imageVector = if (isInSandbox) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInSandbox) "In 3-Pick Lab" else "Add to Lab",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onToggleSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (prop.isSaved) quant.elevatedSurface else quant.positiveEv,
                        contentColor = if (prop.isSaved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("prop_detail_save_btn")
                ) {
                    Icon(
                        imageVector = if (prop.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (prop.isSaved) "Saved Prop" else "Save Prop",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenFullResearch,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = quant.correlationCyan),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("prop_detail_open_research_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Full Game Logs, Graphs & Injury Matrix",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            AnalyticsDisclaimerFooter()
        }
    }
}

private data class PickRationaleItem(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val accentColor: Color
)

private fun buildGameSpecificPickReasons(
    prop: PlayerPropPick,
    event: GameEvent?,
    injuries: List<PlayerInjuryInfo>,
    projDelta: Double,
    projDeltaPct: Double,
    positiveColor: Color,
    cyanColor: Color,
    amberColor: Color,
    neutralColor: Color
): List<PickRationaleItem> {
    val isMore = prop.recommendedSide == PropSide.MORE
    val list = mutableListOf<PickRationaleItem>()

    // 1. Quantitative Projection Edge
    val dirWord = if (isMore) "above" else "below"
    val recEv = if (isMore) prop.moreEvPercent else prop.lessEvPercent
    val recProb = if (isMore) prop.moreProb else prop.lessProb
    list.add(
        PickRationaleItem(
            icon = Icons.Rounded.AutoGraph,
            title = "Model Projection Edge (${String.format(Locale.US, "%+.1f", projDelta)} ${prop.statLabel})",
            detail = "Our statistical distribution projects ${prop.playerName} at ${String.format(Locale.US, "%.1f", prop.projectedValue)} ${prop.statLabel} — ${String.format(Locale.US, "%.1f%%", abs(projDeltaPct))} $dirWord the ${formatStatLine(prop.lineValue)} market line, generating a ${OddsMath.formatPercent(recProb)} win probability and ${OddsMath.formatPercent(recEv, includeSign = true)} sharp +EV.",
            accentColor = positiveColor
        )
    )

    // 2. Game Pace & Total Environment
    val total = prop.expectedTotal ?: event?.marketSnapshot?.totalLine ?: prop.league.avgGameTotal
    val leagueAvgTotal = prop.league.avgGameTotal
    val totalDiffPct = ((total - leagueAvgTotal) / leagueAvgTotal) * 100.0
    val paceHeadline = when {
        totalDiffPct >= 2.5 -> "High-Pace Game Total (O/U ${formatStatLine(total)})"
        totalDiffPct <= -2.5 -> "Controlled-Tempo Total (O/U ${formatStatLine(total)})"
        else -> "Neutral Game Total (O/U ${formatStatLine(total)})"
    }
    val paceDetail = when {
        totalDiffPct >= 2.5 && isMore ->
            "This matchup carries a ${formatStatLine(total)} total (${String.format(Locale.US, "%+.1f%%", totalDiffPct)} above the ${prop.league.code} average of ${formatStatLine(leagueAvgTotal)}), creating extra possessions and elevated offensive volume that directly support MORE ${prop.statLabel}."
        totalDiffPct <= -2.5 && !isMore ->
            "With the market total set at ${formatStatLine(total)} (${String.format(Locale.US, "%.1f%%", totalDiffPct)} below the ${prop.league.code} baseline), slower pace and half-court/defensive compression favor LESS ${prop.statLabel}."
        else ->
            "Game total of ${formatStatLine(total)} sits near the ${prop.league.code} baseline (${formatStatLine(leagueAvgTotal)}), keeping scoring opportunities stable with ${prop.playerName}'s projected share driving the edge."
    }
    list.add(
        PickRationaleItem(
            icon = Icons.Rounded.Speed,
            title = paceHeadline,
            detail = paceDetail,
            accentColor = cyanColor
        )
    )

    // 3. Spread & Game Script
    val homeMargin = prop.expectedHomeMargin ?: event?.marketSnapshot?.spreadLine?.let { -it } ?: 0.0
    val teamMargin = if (prop.sideTag == "HOME") homeMargin else -homeMargin
    val absMargin = abs(teamMargin)
    val scriptTitle = when {
        absMargin <= 4.5 -> "Competitive Spread Script (${String.format(Locale.US, "%+.1f", teamMargin)} Margin)"
        prop.isTeamFavorite -> "${prop.teamAbbr} Favored Script (${String.format(Locale.US, "%+.1f", teamMargin)})"
        else -> "${prop.teamAbbr} Underdog Script (${String.format(Locale.US, "%+.1f", teamMargin)})"
    }
    val scriptDetail = when {
        absMargin <= 4.5 && isMore ->
            "With a tight ${String.format(Locale.US, "%.1f", absMargin)}-point projected margin, ${prop.teamAbbr} starters project for full late-game leverage minutes without blowout bench risk."
        prop.isTeamFavorite && isMore ->
            "${prop.teamAbbr} projects ahead by ~${String.format(Locale.US, "%.1f", absMargin)}, aligning ${prop.playerName}'s offensive production with team win/cover flow (positive team-prop correlation)."
        !prop.isTeamFavorite && isMore ->
            "As a ${String.format(Locale.US, "%.1f", absMargin)}-point underdog, ${prop.teamAbbr} faces a trailing/pass-heavy catch-up script that concentrates usage on primary producers like ${prop.playerName}."
        else ->
            "Projected ${String.format(Locale.US, "%+.1f", teamMargin)} game script reduces high-variance upside, supporting the LESS ${formatStatLine(prop.lineValue)} distribution tail."
    }
    list.add(
        PickRationaleItem(
            icon = Icons.Rounded.Bolt,
            title = scriptTitle,
            detail = scriptDetail,
            accentColor = amberColor
        )
    )

    // 4. Matchup Injuries & Usage Context
    val sameTeamOut = injuries.filter {
        it.teamAbbr.equals(prop.teamAbbr, ignoreCase = true) &&
            (it.severity == InjurySeverity.OUT || it.severity == InjurySeverity.DOUBTFUL)
    }
    val oppOut = injuries.filter {
        !it.teamAbbr.equals(prop.teamAbbr, ignoreCase = true) &&
            (it.severity == InjurySeverity.OUT || it.severity == InjurySeverity.DOUBTFUL)
    }
    val injuryDetail = when {
        sameTeamOut.isNotEmpty() || oppOut.isNotEmpty() -> buildString {
            if (sameTeamOut.isNotEmpty()) {
                val names = sameTeamOut.take(3).joinToString(", ") { "${it.playerName} (${it.status})" }
                append("${prop.teamAbbr} absences ($names) open extra usage/minutes share. ")
            }
            if (oppOut.isNotEmpty()) {
                val names = oppOut.take(3).joinToString(", ") { "${it.playerName} (${it.status})" }
                append("Opponent also lists $names out, impacting defensive matchup depth.")
            }
        }.trim()
        else -> "No major OUT/DOUBTFUL rotation disruptions reported on the latest ESPN injury wire for ${prop.matchupShort}, stabilizing ${prop.playerName}'s role."
    }
    list.add(
        PickRationaleItem(
            icon = Icons.Rounded.MedicalServices,
            title = if (sameTeamOut.isNotEmpty() || oppOut.isNotEmpty()) "Matchup Injury & Usage Impact" else "Clean Rotation & Injury Wire",
            detail = injuryDetail,
            accentColor = if (sameTeamOut.isNotEmpty()) positiveColor else neutralColor
        )
    )

    return list
}

@Composable
private fun PropDetailStatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, quant.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SideComparisonCard(
    title: String,
    winProb: Double,
    odds: Int,
    ev: Double,
    oddsFormat: OddsFormat,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Surface(
        color = if (highlighted) quant.positiveEvContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) quant.positiveEv else quant.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                color = if (highlighted) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = OddsMath.formatPercent(winProb),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${OddsMath.formatOdds(odds, oddsFormat)} (${OddsMath.formatPercent(ev, includeSign = true)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ev >= 0) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
