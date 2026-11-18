package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.League
import com.example.domain.model.MarketLeg
import com.example.domain.model.OddsFormat
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.EvBadgePill
import com.example.ui.components.RhoChip
import com.example.ui.components.TeamLogoBadge
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CorrEdgeUiState
import com.example.ui.viewmodel.EvSortMode

@Composable
fun EvScannerScreen(
    uiState: CorrEdgeUiState,
    onSelectLeague: (League?) -> Unit,
    onSelectSort: (EvSortMode) -> Unit,
    onToggleSandboxLeg: (MarketLeg) -> Unit,
    onClearSandbox: () -> Unit,
    onInspectSandbox: (CorrelatedParlay) -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current

    val displayedLegs = remember(
        uiState.allLegs,
        uiState.selectedScannerLeague,
        uiState.evSortMode,
        uiState.preferences.enabledLeagues
    ) {
        val base = uiState.allLegs.filter { leg ->
            leg.league in uiState.preferences.enabledLeagues &&
                (uiState.selectedScannerLeague == null || leg.league == uiState.selectedScannerLeague)
        }
        when (uiState.evSortMode) {
            EvSortMode.HIGHEST_EV -> base.sortedByDescending { it.evPercent }
            EvSortMode.HIGHEST_PROB -> base.sortedByDescending { it.fairTrueProb }
            EvSortMode.SOONEST -> base.sortedBy { it.matchupShort }
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
            // Header + Filters
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "+EV Market Feed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap + on 2–3 legs to test custom Gaussian correlation (ρ) in the Lab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // League filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedScannerLeague == null,
                        onClick = { onSelectLeague(null) },
                        label = { Text("All (${uiState.allLegs.size})") },
                        modifier = Modifier.testTag("scanner_filter_all")
                    )
                    League.entries.filter { it in uiState.preferences.enabledLeagues }.forEach { league ->
                        val count = uiState.allLegs.count { it.league == league }
                        FilterChip(
                            selected = uiState.selectedScannerLeague == league,
                            onClick = { onSelectLeague(league) },
                            label = { Text("${league.code} ($count)") },
                            modifier = Modifier.testTag("scanner_filter_${league.code}")
                        )
                    }
                }

                // Sort row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EvSortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.evSortMode == mode,
                            onClick = { onSelectSort(mode) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = quant.correlationContainer,
                                selectedLabelColor = quant.correlationCyan
                            ),
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("scanner_sort_${mode.name}")
                        )
                    }
                }
            }

            // Legs List
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (displayedLegs.isEmpty()) {
                    item(key = "empty_legs") {
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
                                Text(
                                    text = "No priced market legs in this league filter",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select 'All' or refresh from the Top 3 tab when games open for pricing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(items = displayedLegs, key = { it.id }) { leg ->
                        EvLegCard(
                            leg = leg,
                            oddsFormat = uiState.preferences.oddsFormat,
                            isInSandbox = leg.id in sandboxIds,
                            onToggleSandbox = { onToggleSandboxLeg(leg) }
                        )
                    }
                }

                item(key = "scanner_disclaimer") {
                    AnalyticsDisclaimerFooter()
                }
            }

            // Interactive Custom Correlation Sandbox Dock
            AnimatedVisibility(visible = uiState.sandboxLegs.isNotEmpty()) {
                Surface(
                    color = quant.elevatedSurface,
                    border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Science,
                                    contentDescription = "Correlation sandbox",
                                    tint = quant.correlationCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CORRELATION LAB (${uiState.sandboxLegs.size}/3 LEGS)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = quant.correlationCyan
                                )
                            }
                            IconButton(
                                onClick = onClearSandbox,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear sandbox",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = uiState.sandboxLegs.joinToString("  +  ") { it.selectionLabel },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val result = uiState.sandboxResult
                        if (result != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Corr Prob: ${OddsMath.formatPercent(result.correlatedProb)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = quant.positiveEv
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RhoChip(rho = result.avgRho)
                                        EvBadgePill(evPercent = result.correlatedEvPercent)
                                    }
                                }
                                Button(
                                    onClick = { onInspectSandbox(result) },
                                    colors = ButtonDefaults.buttonColors(containerColor = quant.correlationCyan),
                                    modifier = Modifier.testTag("inspect_sandbox_button")
                                ) {
                                    Text("Inspect Matrix")
                                }
                            }
                        } else {
                            Text(
                                text = "Select 1 more leg to compute joint probability & ρ covariance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvLegCard(
    leg: MarketLeg,
    oddsFormat: OddsFormat,
    isInSandbox: Boolean,
    onToggleSandbox: () -> Unit
) {
    val quant = LocalQuantColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (isInSandbox) 1.5.dp else 1.dp,
            color = if (isInSandbox) quant.correlationCyan else quant.cardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamLogoBadge(
                logoUrl = if (leg.sideTag == "AWAY") leg.awayLogoUrl else leg.homeLogoUrl,
                fallbackText = leg.league.code,
                size = 34.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = quant.elevatedSurface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = leg.league.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = leg.matchupShort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = leg.selectionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Fair ${OddsMath.formatPercent(leg.fairTrueProb)} vs Mkt ${OddsMath.formatPercent(leg.marketImpliedProb)} • ${leg.sharpBookLabel}",
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                EvBadgePill(evPercent = leg.evPercent)
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onToggleSandbox,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sandbox_toggle_${leg.id}")
            ) {
                Icon(
                    imageVector = if (isInSandbox) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (isInSandbox) "Remove from correlation lab" else "Add to correlation lab",
                    tint = if (isInSandbox) quant.correlationCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
