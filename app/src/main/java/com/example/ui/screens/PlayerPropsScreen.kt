package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.GameEvent
import com.example.domain.model.League
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PreviousGameStatEntry
import com.example.domain.model.PropSide
import com.example.domain.model.PropSimScenario
import com.example.domain.model.ResearchCandidatePlayer
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.PlayerPropCard
import com.example.ui.components.ScheduleGameChipCard
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CorrEdgeUiState
import com.example.ui.viewmodel.PropDirectionFilter
import com.example.ui.viewmodel.PropsTabMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPropsScreen(
    uiState: CorrEdgeUiState,
    onSelectLeague: (League?) -> Unit,
    onSelectDirection: (PropDirectionFilter) -> Unit,
    onInspectGame: (GameEvent) -> Unit,
    onInspectProp: (PlayerPropPick) -> Unit,
    onToggleSaveProp: (PlayerPropPick) -> Unit,
    onTogglePropInSandbox: (PlayerPropPick) -> Unit,
    onSelectTabMode: (PropsTabMode) -> Unit,
    onOpenPlayerResearch: (PlayerPropPick) -> Unit,
    onSelectResearchLeague: (League?) -> Unit,
    onSelectResearchGame: (GameEvent) -> Unit,
    onSelectResearchCandidate: (ResearchCandidatePlayer) -> Unit,
    onSelectResearchPropStat: (String) -> Unit,
    onAdjustResearchLine: (Double) -> Unit,
    onSetResearchCustomLine: (Double) -> Unit,
    onSelectPreviousGameBar: (PreviousGameStatEntry) -> Unit,
    onSelectResearchSimScenario: (PropSimScenario) -> Unit,
    onRerunResearchSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val researchState = uiState.researchState
    val isResearchTab = researchState.tabMode == PropsTabMode.RESEARCH

    val leagueEvents = remember(
        uiState.allEvents,
        uiState.selectedPropsLeague,
        uiState.preferences.enabledLeagues
    ) {
        uiState.allEvents.filter { event ->
            event.league in uiState.preferences.enabledLeagues &&
                (uiState.selectedPropsLeague == null || event.league == uiState.selectedPropsLeague)
        }
    }

    val displayedProps = remember(
        uiState.allProps,
        uiState.selectedPropsLeague,
        uiState.propDirectionFilter,
        uiState.preferences.enabledLeagues
    ) {
        uiState.allProps.filter { prop ->
            val matchesLeague = prop.league in uiState.preferences.enabledLeagues &&
                (uiState.selectedPropsLeague == null || prop.league == uiState.selectedPropsLeague)
            val matchesDir = when (uiState.propDirectionFilter) {
                PropDirectionFilter.ALL -> true
                PropDirectionFilter.MORE -> prop.recommendedSide == PropSide.MORE
                PropDirectionFilter.LESS -> prop.recommendedSide == PropSide.LESS
            }
            matchesLeague && matchesDir
        }
    }

    val sandboxIds = remember(uiState.sandboxLegs) {
        uiState.sandboxLegs.map { it.id }.toSet()
    }

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
                    text = if (isResearchTab) "Player Research Lab" else "Player Props Board",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isResearchTab) {
                        "All upcoming games by sport, game clearance diagnostics, alt lines & 10K Monte Carlo simulator."
                    } else {
                        "Both MORE & LESS lines shown side-by-side with highlighted +EV recommendation."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                PrimaryTabRow(
                    selectedTabIndex = if (isResearchTab) 1 else 0,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isResearchTab) quant.correlationCyan else quant.positiveEv,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = !isResearchTab,
                        onClick = { onSelectTabMode(PropsTabMode.BOARD) },
                        text = { Text("Props Board (${displayedProps.size})") },
                        modifier = Modifier.testTag("props_mode_board")
                    )
                    Tab(
                        selected = isResearchTab,
                        onClick = { onSelectTabMode(PropsTabMode.RESEARCH) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.QueryStats,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Research (${researchState.upcomingEvents.size})")
                            }
                        },
                        modifier = Modifier.testTag("props_mode_research")
                    )
                }

                if (!isResearchTab) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // League filter row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedPropsLeague == null,
                            onClick = { onSelectLeague(null) },
                            label = { Text("All (${uiState.allProps.size})") },
                            modifier = Modifier.testTag("props_filter_all")
                        )
                        League.entries.filter { it in uiState.preferences.enabledLeagues }.forEach { league ->
                            val count = uiState.allProps.count { it.league == league }
                            FilterChip(
                                selected = uiState.selectedPropsLeague == league,
                                onClick = { onSelectLeague(league) },
                                label = { Text("${league.code} ($count)") },
                                modifier = Modifier.testTag("props_filter_${league.code}")
                            )
                        }
                    }

                    // Direction filter row (All / Take MORE / Take LESS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PropDirectionFilter.entries.forEach { dir ->
                            FilterChip(
                                selected = uiState.propDirectionFilter == dir,
                                onClick = { onSelectDirection(dir) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = quant.positiveEvContainer,
                                    selectedLabelColor = quant.positiveEv
                                ),
                                label = { Text(dir.label) },
                                modifier = Modifier.testTag("props_dir_${dir.name}")
                            )
                        }
                    }
                }
            }

            if (isResearchTab) {
                PlayerResearchContent(
                    researchState = researchState,
                    enabledLeagues = uiState.preferences.enabledLeagues,
                    oddsFormat = uiState.preferences.oddsFormat,
                    onSelectLeagueFilter = onSelectResearchLeague,
                    onSelectResearchGame = onSelectResearchGame,
                    onSelectCandidate = onSelectResearchCandidate,
                    onSelectPropStat = onSelectResearchPropStat,
                    onAdjustLine = onAdjustResearchLine,
                    onSetCustomLine = onSetResearchCustomLine,
                    onSelectPreviousGame = onSelectPreviousGameBar,
                    onSelectSimScenario = onSelectResearchSimScenario,
                    onRerunSimulation = onRerunResearchSimulation,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current League Schedule Strip at top for quick game inspection
                    if (leagueEvents.isNotEmpty()) {
                        item(key = "props_schedule_strip") {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${uiState.selectedPropsLeague?.code ?: "ALL"} SCHEDULE (${leagueEvents.size} GAMES)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = quant.correlationCyan
                                    )
                                    Text(
                                        text = "Tap game for props & lines",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = leagueEvents,
                                        key = { "prop_sched_${it.league.code}_${it.id}" }
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

                    if (uiState.feedStatus.isLoading && displayedProps.isEmpty()) {
                        item(key = "props_loading") {
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
                                    CircularProgressIndicator(color = quant.positiveEv)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Projecting Player Props & Athlete Portraits...",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    } else if (displayedProps.isEmpty()) {
                        item(key = "props_empty") {
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
                                        imageVector = Icons.Rounded.PersonSearch,
                                        contentDescription = null,
                                        tint = quant.correlationCyan,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No active player props in this filter",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Switch leagues above or tap Research to view full player rosters & graphs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(items = displayedProps, key = { it.id }) { prop ->
                            PlayerPropCard(
                                prop = prop,
                                oddsFormat = uiState.preferences.oddsFormat,
                                isInSandbox = prop.id in sandboxIds,
                                onToggleSave = { onToggleSaveProp(prop) },
                                onToggleSandbox = { onTogglePropInSandbox(prop) },
                                onClickPlayer = { onInspectProp(prop) },
                                onResearchPlayer = { onOpenPlayerResearch(prop) }
                            )
                        }
                    }

                    item(key = "props_disclaimer") {
                        AnalyticsDisclaimerFooter()
                    }
                }
            }
        }
    }
}
