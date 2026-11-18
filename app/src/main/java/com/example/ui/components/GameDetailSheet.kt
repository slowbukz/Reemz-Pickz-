package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.GameEvent
import com.example.domain.model.MarketLeg
import com.example.domain.model.OddsFormat
import com.example.domain.model.PlayerPropPick
import com.example.ui.theme.LocalQuantColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailSheet(
    event: GameEvent,
    gameProps: List<PlayerPropPick>,
    gameLines: List<MarketLeg>,
    gameParlays: List<CorrelatedParlay>,
    sandboxLegIds: Set<String>,
    oddsFormat: OddsFormat,
    onDismiss: () -> Unit,
    onOpenParlay: (CorrelatedParlay) -> Unit,
    onToggleSaveParlay: (CorrelatedParlay) -> Unit,
    onToggleSaveProp: (PlayerPropPick) -> Unit,
    onToggleSandboxLeg: (MarketLeg) -> Unit,
    onTogglePropInSandbox: (PlayerPropPick) -> Unit,
    onInspectProp: ((PlayerPropPick) -> Unit)? = null,
    onResearchPlayer: ((PlayerPropPick) -> Unit)? = null,
    onResearchMatchup: ((GameEvent) -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(event.id) { mutableIntStateOf(0) }

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
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Game Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${event.league.displayName.uppercase()} • ${event.statusDetail}",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.correlationCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamLogoBadge(logoUrl = event.awayTeam.logoUrl, fallbackText = event.awayTeam.abbreviation, size = 28.dp)
                        Text(
                            text = "${event.awayTeam.abbreviation} @ ${event.homeTeam.abbreviation}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TeamLogoBadge(logoUrl = event.homeTeam.logoUrl, fallbackText = event.homeTeam.abbreviation, size = 28.dp)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_game_detail_sheet")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close game details")
                }
            }

            // ESPN Predictor Bar
            event.predictorHomeWinProb?.let { homeProb ->
                Spacer(modifier = Modifier.height(10.dp))
                val awayProb = 1.0 - homeProb
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${event.awayTeam.abbreviation} ${OddsMath.formatPercent(awayProb, decimals = 0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ESPN Predictor",
                        style = MaterialTheme.typography.labelSmall,
                        color = quant.correlationCyan
                    )
                    Text(
                        text = "${event.homeTeam.abbreviation} ${OddsMath.formatPercent(homeProb, decimals = 0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = quant.positiveEv
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(awayProb.toFloat().coerceIn(0.05f, 0.95f))
                            .height(6.dp)
                            .background(quant.correlationCyan)
                    )
                    Box(
                        modifier = Modifier
                            .weight(homeProb.toFloat().coerceIn(0.05f, 0.95f))
                            .height(6.dp)
                            .background(quant.positiveEv)
                    )
                }
            }

            if (onResearchMatchup != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onResearchMatchup(event) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = quant.correlationCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("game_detail_research_matchup")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueryStats,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Player Research, Prop Graphs & Injuries",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = quant.positiveEv,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Top Player Props (${gameProps.size})") },
                    modifier = Modifier.testTag("game_detail_tab_props")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Game Lines & SGP (${gameLines.size})") },
                    modifier = Modifier.testTag("game_detail_tab_lines")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                if (gameProps.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Player props for this matchup have not posted active roster projections yet. Check the Game Lines & SGP tab for spreads, totals, and correlated edges.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gameProps.forEach { prop ->
                            PlayerPropCard(
                                prop = prop,
                                oddsFormat = oddsFormat,
                                isInSandbox = prop.id in sandboxLegIds,
                                onToggleSave = { onToggleSaveProp(prop) },
                                onToggleSandbox = { onTogglePropInSandbox(prop) },
                                onClickPlayer = onInspectProp?.let { callback -> { callback(prop) } },
                                onResearchPlayer = onResearchPlayer?.let { callback -> { callback(prop) } }
                            )
                        }
                    }
                }
            } else {
                // Market Lines Snapshot
                val snap = event.marketSnapshot
                if (snap != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "MARKET LINES • ${snap.providerName.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.correlationCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val spreadText = snap.spreadLine?.let { sp ->
                                    val sign = if (sp > 0) "+" else ""
                                    "${event.homeTeam.abbreviation} $sign$sp"
                                } ?: "—"
                                GameLineMiniTile(
                                    label = "Spread",
                                    value = spreadText,
                                    modifier = Modifier.weight(1f)
                                )
                                GameLineMiniTile(
                                    label = "Total O/U",
                                    value = snap.totalLine?.toString() ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                val mlText = if (snap.awayMoneyline != null && snap.homeMoneyline != null) {
                                    "${OddsMath.formatOdds(snap.awayMoneyline, oddsFormat)} / ${OddsMath.formatOdds(snap.homeMoneyline, oddsFormat)}"
                                } else "—"
                                GameLineMiniTile(
                                    label = "Away / Home ML",
                                    value = mlText,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Priced +EV Market Legs for this game
                if (gameLines.isNotEmpty()) {
                    Text(
                        text = "PRICED GAME LINES (+EV VALUATION)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gameLines.forEach { leg ->
                            val inSandbox = leg.id in sandboxLegIds
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    width = if (inSandbox) 1.5.dp else 1.dp,
                                    color = if (inSandbox) quant.correlationCyan else quant.cardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = leg.selectionLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${leg.marketType.label} • Fair ${OddsMath.formatPercent(leg.fairTrueProb)} vs Mkt ${OddsMath.formatPercent(leg.marketImpliedProb)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        EvBadgePill(evPercent = leg.evPercent)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onToggleSandboxLeg(leg) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (inSandbox) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = "Add line to Correlation Lab",
                                            tint = if (inSandbox) quant.correlationCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Same-Game Correlated Parlays
                Text(
                    text = "TOP SAME-GAME CORRELATED PARLAYS",
                    style = MaterialTheme.typography.labelMedium,
                    color = quant.positiveEv
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (gameParlays.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Insufficient 2-way correlated lines posted for this game yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    gameParlays.forEach { parlay ->
                        CompactParlayCard(
                            parlay = parlay,
                            oddsFormat = oddsFormat,
                            onClick = { onOpenParlay(parlay) },
                            onToggleSave = { onToggleSaveParlay(parlay) },
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }

            AnalyticsDisclaimerFooter()
        }
    }
}

@Composable
private fun GameLineMiniTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Surface(
        color = quant.elevatedSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
