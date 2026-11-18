package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.firebase.SavedParlayDoc
import com.example.data.firebase.SavedPropDoc
import com.example.domain.math.OddsMath
import com.example.domain.model.DevigMethod
import com.example.domain.model.League
import com.example.domain.model.OddsFormat
import com.example.domain.model.ParlaySizePreference
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.SavedParlayDocCard
import com.example.ui.components.SavedPropDocCard
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CloudWatchlistState
import com.example.ui.viewmodel.CorrEdgeUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantLabScreen(
    uiState: CorrEdgeUiState,
    userDisplayName: String,
    userEmail: String,
    onSignOut: () -> Unit,
    onToggleLeaguePref: (League) -> Unit,
    onMinProbChange: (Double) -> Unit,
    onMinRhoChange: (Double) -> Unit,
    onParlaySizeChange: (ParlaySizePreference) -> Unit,
    onDevigMethodChange: (DevigMethod) -> Unit,
    onOddsFormatChange: (OddsFormat) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onOpenSavedParlayDoc: (SavedParlayDoc) -> Unit,
    onDeleteSavedParlayDoc: (String) -> Unit,
    onDeleteSavedPropDoc: (String) -> Unit,
    onOpenSavedPropDoc: (SavedPropDoc) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val prefs = uiState.preferences

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "lab_header") {
                Column {
                    Text(
                        text = "Quant Lab & Cloud Watchlist",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Cloud-synced parlays & props, Google account, and devigging controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Google Account & Firebase Sync Card
            item(key = "account_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, quant.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(quant.positiveEvContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = "Cloud sync active",
                                    tint = quant.positiveEv,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = userDisplayName.ifBlank { "Reemz Quant Member" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userEmail.ifBlank { "Google Authenticated • Firestore Synced" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }

            // Saved Parlays Watchlist Section
            item(key = "watchlist_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdded,
                        contentDescription = null,
                        tint = quant.positiveEv,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SAVED CORRELATED PARLAYS (${uiState.savedParlayDocs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.positiveEv
                    )
                }
            }

            when (val wState = uiState.watchlistState) {
                is CloudWatchlistState.Loading -> {
                    item(key = "watchlist_loading") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = quant.positiveEv
                            )
                            Text(
                                text = "Syncing cloud watchlist...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is CloudWatchlistState.Error -> {
                    item(key = "watchlist_error") {
                        Surface(
                            color = quant.negativeContainer,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, quant.negativeCoral.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = wState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = quant.negativeCoral,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is CloudWatchlistState.Ready -> {
                    if (wState.parlays.isEmpty()) {
                        item(key = "empty_parlay_watchlist") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, quant.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Bookmark any correlated parlay from Parlays or Leagues to sync it to your cloud watchlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(items = wState.parlays, key = { "saved_parlay_${it.id}" }) { doc ->
                            SavedParlayDocCard(
                                doc = doc,
                                oddsFormat = prefs.oddsFormat,
                                onClick = { onOpenSavedParlayDoc(doc) },
                                onDelete = { onDeleteSavedParlayDoc(doc.id) }
                            )
                        }
                    }

                    // Saved Player Props Watchlist Section
                    item(key = "saved_props_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonSearch,
                                contentDescription = null,
                                tint = quant.correlationCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SAVED PLAYER PROPS (${wState.props.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.correlationCyan
                            )
                        }
                    }

                    if (wState.props.isEmpty()) {
                        item(key = "empty_props_watchlist") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, quant.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Bookmark any More/Less player prop from the Props tab or a game sheet to track it here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(items = wState.props, key = { "saved_prop_${it.id}" }) { propDoc ->
                            SavedPropDocCard(
                                doc = propDoc,
                                oddsFormat = prefs.oddsFormat,
                                onDelete = { onDeleteSavedPropDoc(propDoc.id) },
                                onClick = { onOpenSavedPropDoc(propDoc) }
                            )
                        }
                    }
                }
            }

            // Engine Parameters Card
            item(key = "engine_params") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, quant.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = quant.correlationCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CORRELATION & EV ENGINE PARAMETERS",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.correlationCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Minimum Hit Probability", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = OddsMath.formatPercent(prefs.minHitProbability, decimals = 0),
                                style = MaterialTheme.typography.labelLarge,
                                color = quant.positiveEv
                            )
                        }
                        Slider(
                            value = prefs.minHitProbability.toFloat(),
                            onValueChange = { onMinProbChange(it.toDouble()) },
                            valueRange = 0.20f..0.70f,
                            colors = SliderDefaults.colors(
                                thumbColor = quant.positiveEv,
                                activeTrackColor = quant.positiveEv
                            ),
                            modifier = Modifier.testTag("slider_min_prob")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Minimum Correlation (ρ)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format(Locale.US, "%+.2f", prefs.minCorrelationRho),
                                style = MaterialTheme.typography.labelLarge,
                                color = quant.correlationCyan
                            )
                        }
                        Slider(
                            value = prefs.minCorrelationRho.toFloat(),
                            onValueChange = { onMinRhoChange(it.toDouble()) },
                            valueRange = 0.08f..0.55f,
                            colors = SliderDefaults.colors(
                                thumbColor = quant.correlationCyan,
                                activeTrackColor = quant.correlationCyan
                            ),
                            modifier = Modifier.testTag("slider_min_rho")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Parlay Leg Structure", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ParlaySizePreference.entries.forEach { sizePref ->
                                FilterChip(
                                    selected = prefs.parlaySizePreference == sizePref,
                                    onClick = { onParlaySizeChange(sizePref) },
                                    label = { Text(sizePref.label) },
                                    modifier = Modifier.testTag("pref_size_${sizePref.name}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Active Sports Feeds", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            League.entries.forEach { league ->
                                FilterChip(
                                    selected = league in prefs.enabledLeagues,
                                    onClick = { onToggleLeaguePref(league) },
                                    label = { Text(league.code) },
                                    modifier = Modifier.testTag("pref_league_${league.code}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Devigging Method", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DevigMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = prefs.devigMethod == method,
                                    onClick = { onDevigMethodChange(method) },
                                    label = { Text(method.label) },
                                    modifier = Modifier.testTag("pref_devig_${method.name}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Odds Display Format", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OddsFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = prefs.oddsFormat == format,
                                    onClick = { onOddsFormatChange(format) },
                                    label = { Text(format.label) },
                                    modifier = Modifier.testTag("pref_odds_${format.name}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Obsidian Terminal Dark Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "High-contrast dark analytics palette",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.darkTheme,
                                onCheckedChange = onDarkThemeChange,
                                modifier = Modifier.testTag("switch_dark_theme")
                            )
                        }
                    }
                }
            }

            // Data Sources & API Telemetry Card
            item(key = "feed_diagnostics") {
                val status = uiState.feedStatus
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, quant.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "LIVE DATA PIPELINE STATUS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = quant.positiveEv,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESPN Scoreboard, Rosters & Predictor: Active (${status.espnEventsCount} events • ${uiState.allProps.size} props)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (status.oddsApiActive) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (status.oddsApiActive) quant.positiveEv else quant.correlationCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (status.oddsApiActive) {
                                        "The Odds API Multi-Book Devigging: Active (${status.oddsApiEventsCount} matched events)"
                                    } else {
                                        "The Odds API Multi-Book Feed: Optional Upgrade"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!status.oddsApiActive) {
                                    Text(
                                        text = "Add THE_ODDS_API_KEY in the AI Studio Secrets panel to layer multi-sportsbook Pinnacle/DraftKings/FanDuel devigging on top of ESPN live feeds.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "lab_disclaimer") {
                AnalyticsDisclaimerFooter()
            }
        }
    }
}
