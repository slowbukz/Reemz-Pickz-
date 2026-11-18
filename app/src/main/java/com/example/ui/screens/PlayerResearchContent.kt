package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.PlayerPropSimEngine
import com.example.domain.model.GameEvent
import com.example.domain.model.League
import com.example.domain.model.OddsFormat
import com.example.domain.model.PreviousGameStatEntry
import com.example.domain.model.PropSide
import com.example.domain.model.PropSimScenario
import com.example.domain.model.ResearchCandidatePlayer
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.MatchupInjuriesCard
import com.example.ui.components.PlayerAvatar
import com.example.ui.components.PlayerPropSimulatorCard
import com.example.ui.components.PreviousGameAvailabilityCard
import com.example.ui.components.PreviousGameLogTableCard
import com.example.ui.components.PropHistoryBarChartCard
import com.example.ui.components.SelectedGameResearchDetailCard
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.formatStatLine
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.PlayerResearchUiState
import kotlin.math.abs

@Composable
fun PlayerResearchContent(
    researchState: PlayerResearchUiState,
    enabledLeagues: Set<League>,
    oddsFormat: OddsFormat,
    onSelectLeagueFilter: (League?) -> Unit,
    onSelectResearchGame: (GameEvent) -> Unit,
    onSelectCandidate: (ResearchCandidatePlayer) -> Unit,
    onSelectPropStat: (String) -> Unit,
    onAdjustLine: (Double) -> Unit,
    onSetCustomLine: (Double) -> Unit,
    onSelectPreviousGame: (PreviousGameStatEntry) -> Unit,
    onSelectSimScenario: (PropSimScenario) -> Unit,
    onRerunSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current

    val filteredUpcomingGames = remember(researchState.upcomingEvents, researchState.leagueFilter) {
        researchState.upcomingEvents.filter {
            researchState.leagueFilter == null || it.league == researchState.leagueFilter
        }
    }

    val gamesBySport = remember(filteredUpcomingGames) {
        League.entries.mapNotNull { league ->
            val sportGames = filteredUpcomingGames.filter { it.league == league }
            if (sportGames.isEmpty()) null else league to sportGames
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. All Upcoming Games Selector Separated by Sport
        item(key = "research_upcoming_header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = quant.positiveEv,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ALL UPCOMING GAMES (${filteredUpcomingGames.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.positiveEv
                        )
                    }
                    Text(
                        text = "Separated by sport",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = researchState.leagueFilter == null,
                        onClick = { onSelectLeagueFilter(null) },
                        label = { Text("All Sports (${researchState.upcomingEvents.size})") },
                        modifier = Modifier.testTag("research_league_all")
                    )
                    League.entries.filter { it in enabledLeagues }.forEach { league ->
                        val count = researchState.upcomingEvents.count { it.league == league }
                        if (count > 0) {
                            FilterChip(
                                selected = researchState.leagueFilter == league,
                                onClick = { onSelectLeagueFilter(league) },
                                label = { Text("${league.code} ($count)") },
                                modifier = Modifier.testTag("research_league_${league.code}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (gamesBySport.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No upcoming games found for this sport filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gamesBySport.forEach { (league, sportGames) ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = quant.elevatedSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, quant.cardBorder)
                                    ) {
                                        Text(
                                            text = "${league.displayName.uppercase()} • ${sportGames.size} ${if (sportGames.size == 1) "GAME" else "GAMES"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = quant.correlationCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Tap matchup to research",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(
                                        items = sportGames,
                                        key = { "res_game_${it.league.code}_${it.id}" }
                                    ) { event ->
                                        val isSelected = researchState.selectedGame?.id == event.id
                                        ResearchUpcomingGameChip(
                                            event = event,
                                            isSelected = isSelected,
                                            onClick = { onSelectResearchGame(event) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val selectedGame = researchState.selectedGame
        if (selectedGame != null) {
            // 2. Player Selector Roster for the Selected Game
            item(key = "research_players_strip") {
                Column {
                    Text(
                        text = "PLAYERS IN ${selectedGame.shortName} (${researchState.candidates.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.correlationCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (researchState.candidatesLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = quant.correlationCyan
                            )
                            Text(
                                text = "Loading rosters & player props...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (researchState.candidates.isEmpty()) {
                        Text(
                            text = "No player roster data posted for this matchup yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(
                                items = researchState.candidates,
                                key = { "cand_${it.eventId}_${it.playerId}" }
                            ) { candidate ->
                                val isSelected = researchState.activeProfile?.playerId == candidate.playerId
                                ResearchCandidateChip(
                                    candidate = candidate,
                                    isSelected = isSelected,
                                    onClick = { onSelectCandidate(candidate) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Upcoming Matchup Injury Report
            item(key = "research_matchup_injuries") {
                MatchupInjuriesCard(
                    event = selectedGame,
                    injuries = researchState.matchupInjuries,
                    isLoading = researchState.injuriesLoading
                )
            }
        }

        // 4. Active Player Research Profile (All Props Graph + Who Played / Didn't Play)
        val profile = researchState.activeProfile
        if (profile != null) {
            item(key = "research_player_hero") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, quant.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(
                                headshotUrl = profile.headshotUrl,
                                playerName = profile.playerName,
                                teamAbbr = profile.teamAbbr,
                                size = 56.dp,
                                highlighted = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${profile.league.code} • ${profile.teamAbbr} ${profile.position}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = quant.correlationCyan
                                )
                                Text(
                                    text = profile.playerName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Upcoming: ${profile.matchupShort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val allStatLabels = remember(profile.propLinesByLabel, profile.gameLogs) {
                            (profile.propLinesByLabel.keys + profile.gameLogs.flatMap { it.propValues.keys }).distinct()
                        }
                        val heroActiveStat = researchState.selectedPropLabel ?: allStatLabels.firstOrNull()
                        val heroBaseLine = heroActiveStat?.let { profile.propLinesByLabel[it] } ?: researchState.customLineValue
                        val heroActiveLine = researchState.customLineValue ?: heroBaseLine

                        if (allStatLabels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SELECT PROP CATEGORY TO GRAPH & SIMULATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allStatLabels.forEach { statLabel ->
                                    val selected = heroActiveStat == statLabel
                                    val lineVal = profile.propLinesByLabel[statLabel]
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectPropStat(statLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = quant.positiveEvContainer,
                                            selectedLabelColor = quant.positiveEv
                                        ),
                                        label = {
                                            val suffix = lineVal?.let { " (${formatStatLine(it)})" }.orEmpty()
                                            Text("$statLabel$suffix")
                                        },
                                        modifier = Modifier.testTag("research_stat_chip_$statLabel")
                                    )
                                }
                            }
                        }

                        if (heroActiveStat != null && heroBaseLine != null && heroActiveLine != null) {
                            val altPresets = remember(heroBaseLine, heroActiveStat) {
                                PlayerPropSimEngine.buildAltLinePresets(heroBaseLine, heroActiveStat)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "QUICK ALT LINES ($heroActiveStat)",
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
                                altPresets.forEach { alt ->
                                    val isSel = abs(alt - heroActiveLine) < 0.01
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { onSetCustomLine(alt) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = quant.correlationContainer,
                                            selectedLabelColor = quant.correlationCyan
                                        ),
                                        label = { Text("Alt ${formatStatLine(alt)}") },
                                        modifier = Modifier.testTag("hero_alt_line_${formatStatLine(alt)}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (profile.isLoading) {
                item(key = "research_profile_loading") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
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
                                text = "Fetching ${profile.playerName}'s ESPN Gamelog & Prop Splits...",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            } else if (profile.gameLogs.isEmpty()) {
                item(key = "research_profile_empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueryStats,
                                contentDescription = null,
                                tint = quant.correlationCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = profile.errorMessage ?: "No recent season games available for this player yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val activeStat = researchState.selectedPropLabel
                    ?: profile.propLinesByLabel.keys.firstOrNull()
                    ?: profile.gameLogs.firstNotNullOfOrNull { it.propValues.keys.firstOrNull() }
                val activeLine = researchState.customLineValue
                    ?: activeStat?.let { profile.propLinesByLabel[it] }
                    ?: 0.5
                val selectedEntry = profile.gameLogs.firstOrNull {
                    it.eventId == researchState.selectedPreviousGameId
                } ?: profile.gameLogs.lastOrNull()

                if (activeStat != null) {
                    item(key = "research_bar_chart") {
                        PropHistoryBarChartCard(
                            statLabel = activeStat,
                            lineValue = activeLine,
                            gameLogs = profile.gameLogs,
                            selectedGameId = selectedEntry?.eventId,
                            onSelectGame = onSelectPreviousGame,
                            onAdjustLine = onAdjustLine
                        )
                    }
                }

                // Selected Game Stats, Alt-Line Check & Why They Cleared / Didn't Clear
                if (selectedEntry != null && activeStat != null) {
                    item(key = "research_selected_game_detail") {
                        SelectedGameResearchDetailCard(
                            profile = profile,
                            selectedGame = selectedEntry,
                            activeStat = activeStat,
                            activeLine = activeLine,
                            availability = researchState.previousGameAvailability,
                            onSelectPropStat = onSelectPropStat,
                            onSelectAltLine = onSetCustomLine,
                            onAdjustLine = onAdjustLine
                        )
                    }
                }

                // 10K Monte Carlo Outcome Simulator for Selected Prop & Alt Line
                if (activeStat != null) {
                    item(key = "research_prop_simulator") {
                        PlayerPropSimulatorCard(
                            profile = profile,
                            activeStat = activeStat,
                            activeLine = activeLine,
                            selectedScenario = researchState.simScenario,
                            simResult = researchState.propSimulation,
                            oddsFormat = oddsFormat,
                            onSelectScenario = onSelectSimScenario,
                            onSelectAltLine = onSetCustomLine,
                            onAdjustLine = onAdjustLine,
                            onRunSimulation = onRerunSimulation
                        )
                    }
                }

                // Selected Previous Game Who Played / Who Didn't Play
                researchState.previousGameAvailability?.let { availability ->
                    item(key = "research_prev_game_availability") {
                        PreviousGameAvailabilityCard(
                            availability = availability,
                            researchedPlayerId = profile.playerId
                        )
                    }
                }

                // Multi-Prop Game-by-Game Log Table
                item(key = "research_gamelog_table") {
                    PreviousGameLogTableCard(
                        gameLogs = profile.gameLogs,
                        selectedGameId = selectedEntry?.eventId,
                        onSelectGame = onSelectPreviousGame
                    )
                }
            }
        }

        item(key = "research_disclaimer") {
            AnalyticsDisclaimerFooter()
        }
    }
}

@Composable
private fun ResearchUpcomingGameChip(
    event: GameEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val quant = LocalQuantColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) quant.positiveEvContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) quant.positiveEv else quant.cardBorder
        ),
        modifier = Modifier
            .width(184.dp)
            .testTag("research_game_${event.id}")
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.league.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = quant.correlationCyan
                )
                Text(
                    text = event.statusDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TeamLogoBadge(logoUrl = event.awayTeam.logoUrl, fallbackText = event.awayTeam.abbreviation, size = 20.dp)
                Text(
                    text = "${event.awayTeam.abbreviation} @ ${event.homeTeam.abbreviation}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                TeamLogoBadge(logoUrl = event.homeTeam.logoUrl, fallbackText = event.homeTeam.abbreviation, size = 20.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            val totalStr = event.marketSnapshot?.totalLine?.let { "O/U $it" } ?: "Tap to research"
            Text(
                text = totalStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResearchCandidateChip(
    candidate: ResearchCandidatePlayer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val quant = LocalQuantColors.current
    Surface(
        color = if (isSelected) quant.correlationContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) quant.correlationCyan else quant.cardBorder
        ),
        modifier = Modifier
            .width(168.dp)
            .testTag("research_candidate_${candidate.playerId}")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                headshotUrl = candidate.headshotUrl,
                playerName = candidate.playerName,
                teamAbbr = candidate.teamAbbr,
                size = 38.dp,
                highlighted = isSelected
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.playerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val propTag = candidate.primaryProp?.let {
                    val side = if (it.recommendedSide == PropSide.MORE) "MORE" else "LESS"
                    "$side ${formatStatLine(it.lineValue)} ${it.statLabel}"
                } ?: "${candidate.teamAbbr} ${candidate.position}".trim()
                Text(
                    text = propTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (candidate.primaryProp != null) quant.positiveEv else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
