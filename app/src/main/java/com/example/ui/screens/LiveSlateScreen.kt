package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.model.EventState
import com.example.domain.model.GameEvent
import com.example.domain.model.League
import com.example.domain.model.OddsFormat
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.NbaNewsSectionContent
import com.example.ui.components.ScheduleGameChipCard
import com.example.ui.components.TeamLogoBadge
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CorrEdgeUiState
import com.example.ui.viewmodel.LiveSlateSubTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSlateScreen(
    uiState: CorrEdgeUiState,
    onSelectLeague: (League?) -> Unit,
    onInspectGame: (GameEvent) -> Unit,
    onSelectSubTab: (LiveSlateSubTab) -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onRefreshScoresNow: () -> Unit,
    onRefreshNbaIntel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val isNbaNewsTab = uiState.liveSlateSubTab == LiveSlateSubTab.NBA_NEWS
    val liveGamesCount = remember(uiState.allEvents) {
        uiState.allEvents.count { it.state == EventState.LIVE }
    }

    val displayedEvents = remember(
        uiState.allEvents,
        uiState.selectedSlateLeague,
        uiState.preferences.enabledLeagues
    ) {
        uiState.allEvents.filter { event ->
            event.league in uiState.preferences.enabledLeagues &&
                (uiState.selectedSlateLeague == null || event.league == uiState.selectedSlateLeague)
        }
    }

    val leagueTitle = uiState.selectedSlateLeague?.displayName ?: "All Enabled Leagues"

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = if (isNbaNewsTab) "NBA News & Weekly Intel" else "$leagueTitle Live Scores",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isNbaNewsTab) {
                        "Breaking NBA headlines, who will & won't play this week, B2B spots & pick edges."
                    } else {
                        "Auto-refreshing every 12s for live scores, clocks, spreads & player props."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                PrimaryTabRow(
                    selectedTabIndex = if (isNbaNewsTab) 1 else 0,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isNbaNewsTab) quant.correlationCyan else quant.positiveEv,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = !isNbaNewsTab,
                        onClick = { onSelectSubTab(LiveSlateSubTab.SCORES) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsScore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (liveGamesCount > 0) "Live Scores ($liveGamesCount LIVE)" else "Live Scores"
                                )
                            }
                        },
                        modifier = Modifier.testTag("slate_tab_scores")
                    )
                    Tab(
                        selected = isNbaNewsTab,
                        onClick = { onSelectSubTab(LiveSlateSubTab.NBA_NEWS) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Newspaper,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("NBA News & Week")
                            }
                        },
                        modifier = Modifier.testTag("slate_tab_nba_news")
                    )
                }

                if (!isNbaNewsTab) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto-Live Sync Control Strip
                    Surface(
                        color = if (uiState.autoRefreshEnabled) quant.positiveEvContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (uiState.autoRefreshEnabled) quant.positiveEv.copy(alpha = 0.45f) else quant.cardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val timeText = uiState.feedStatus.lastUpdatedMillis?.let {
                                    SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(it))
                                } ?: "Syncing..."
                                Text(
                                    text = if (uiState.autoRefreshEnabled) {
                                        "AUTO-LIVE 12s • $liveGamesCount Live • Updated $timeText"
                                    } else {
                                        "AUTO-LIVE PAUSED • Last updated $timeText"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.autoRefreshEnabled) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onToggleAutoRefresh,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_auto_refresh_btn")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.autoRefreshEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (uiState.autoRefreshEnabled) "Pause auto score sync" else "Resume auto score sync",
                                        tint = if (uiState.autoRefreshEnabled) quant.positiveEv else quant.correlationCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onRefreshScoresNow,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("manual_score_sync_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh live scores now",
                                        tint = quant.correlationCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        League.entries.filter { it in uiState.preferences.enabledLeagues }.forEach { league ->
                            val count = uiState.allEvents.count { it.league == league }
                            FilterChip(
                                selected = uiState.selectedSlateLeague == league,
                                onClick = { onSelectLeague(league) },
                                label = { Text("${league.code} ($count)") },
                                modifier = Modifier.testTag("slate_filter_${league.code}")
                            )
                        }
                        FilterChip(
                            selected = uiState.selectedSlateLeague == null,
                            onClick = { onSelectLeague(null) },
                            label = { Text("All (${uiState.allEvents.size})") },
                            modifier = Modifier.testTag("slate_filter_all")
                        )
                    }
                }
            }

            if (isNbaNewsTab) {
                NbaNewsSectionContent(
                    nbaIntel = uiState.nbaIntel,
                    onRefreshIntel = onRefreshNbaIntel,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Current League Schedule Strip pinned at the TOP of the league feed
                if (displayedEvents.isNotEmpty()) {
                    item(key = "league_top_schedule_strip") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, quant.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarMonth,
                                            contentDescription = null,
                                            tint = quant.positiveEv,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "CURRENT ${uiState.selectedSlateLeague?.code ?: "MULTI-LEAGUE"} SCHEDULE (${displayedEvents.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = quant.positiveEv
                                        )
                                    }
                                    Text(
                                        text = "Tap game ›",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = quant.correlationCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = displayedEvents,
                                        key = { "sched_top_${it.league.code}_${it.id}" }
                                    ) { event ->
                                        ScheduleGameChipCard(
                                            event = event,
                                            oddsFormat = uiState.preferences.oddsFormat,
                                            onClick = { onInspectGame(event) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayedEvents.isEmpty()) {
                    item(key = "empty_slate") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, quant.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsScore,
                                    contentDescription = null,
                                    tint = quant.correlationCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No scheduled games found in $leagueTitle right now",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select another league tab above to view its live schedule, props, and game lines.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(items = displayedEvents, key = { "${it.league.code}_${it.id}" }) { event ->
                        val propsCount = uiState.allProps.count { it.eventId == event.id }
                        GameSlateCard(
                            event = event,
                            propsCount = propsCount,
                            oddsFormat = uiState.preferences.oddsFormat,
                            onInspectGame = { onInspectGame(event) }
                        )
                    }
                }

                    item(key = "slate_disclaimer") {
                        AnalyticsDisclaimerFooter()
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSlateCard(
    event: GameEvent,
    propsCount: Int,
    oddsFormat: OddsFormat,
    onInspectGame: () -> Unit
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
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_slate_card_${event.id}")
            .clickable(onClick = onInspectGame)
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
                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = event.league.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = event.statusDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor
                    )
                }

                Text(
                    text = event.dataSourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TeamRow(
                logoUrl = event.awayTeam.logoUrl,
                name = event.awayTeam.name,
                abbr = event.awayTeam.abbreviation,
                record = event.awayTeam.record,
                score = event.awayTeam.score,
                showScore = event.state != EventState.UPCOMING
            )
            Spacer(modifier = Modifier.height(8.dp))
            TeamRow(
                logoUrl = event.homeTeam.logoUrl,
                name = event.homeTeam.name,
                abbr = event.homeTeam.abbreviation,
                record = event.homeTeam.record,
                score = event.homeTeam.score,
                showScore = event.state != EventState.UPCOMING
            )

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

            val snap = event.marketSnapshot
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    snap?.spreadLine?.let { sp ->
                        val spStr = if (sp > 0) "+$sp" else "$sp"
                        LinePill("${event.homeTeam.abbreviation} $spStr")
                    }
                    snap?.totalLine?.let { tot ->
                        LinePill("O/U $tot")
                    }
                    snap?.homeMoneyline?.let { ml ->
                        LinePill("ML ${OddsMath.formatOdds(ml, oddsFormat)}")
                    }
                    if (snap == null && propsCount > 0) {
                        LinePill("$propsCount Player Props")
                    }
                }

                OutlinedButton(
                    onClick = onInspectGame,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("inspect_game_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoGraph,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (propsCount > 0) "Props ($propsCount) & Lines" else "Props & Lines",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamRow(
    logoUrl: String?,
    name: String,
    abbr: String,
    record: String,
    score: String?,
    showScore: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogoBadge(logoUrl = logoUrl, fallbackText = abbr, size = 28.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (record.isNotBlank()) {
            Text(
                text = record,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (showScore && !score.isNullOrBlank()) {
            Text(
                text = score,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LinePill(text: String) {
    val quant = LocalQuantColors.current
    Surface(
        color = quant.elevatedSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, quant.cardBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}
