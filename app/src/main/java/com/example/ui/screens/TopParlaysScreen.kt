package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.EventState
import com.example.domain.model.MarketLeg
import com.example.domain.model.NbaAvailabilityBucket
import com.example.ui.components.AnalyticsDisclaimerFooter
import com.example.ui.components.CompactParlayCard
import com.example.ui.components.TopParlaySpotlightCard
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CorrEdgeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TopParlaysScreen(
    uiState: CorrEdgeUiState,
    onRefresh: () -> Unit,
    onOpenParlay: (CorrelatedParlay) -> Unit,
    onToggleSave: (CorrelatedParlay) -> Unit,
    onOpenQuantLab: () -> Unit,
    onClickPlayerLeg: (MarketLeg) -> Unit = {},
    onOpenNbaNews: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val status = uiState.feedStatus
    val liveCount = uiState.allEvents.count { it.state == EventState.LIVE }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Banner Header
            item(key = "hero_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_quant_hero_bg),
                        contentDescription = "Quantitative probability matrix background",
                        contentScale = ContentScale.Crop,
                        alpha = 0.48f,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xBB0B0F14),
                                        Color(0xEE0B0F14)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = quant.positiveEvContainer,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, quant.positiveEv.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (status.isLoading) quant.moderateAmber else quant.positiveEv)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (status.isLoading) "SCANNING LIVE MARKETS..."
                                        else if (status.oddsApiActive) "LIVE: MULTI-BOOK + ESPN"
                                        else "LIVE: ESPN MARKET ENGINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = quant.positiveEv
                                    )
                                }
                            }

                            IconButton(
                                onClick = onRefresh,
                                enabled = !status.isLoading,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_market_button")
                            ) {
                                if (status.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = quant.positiveEv
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh live market data",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_reemz_logo),
                                contentDescription = "Reemz Pickz logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "REEMZ PICKZ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = quant.correlationCyan
                                )
                                Text(
                                    text = "Top 3 Parlays • 3 Picks",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Top 3-pick parlays recommended for ${uiState.researchState.upcomingEvents.size} upcoming games across 25,000+ simulations (Gaussian Copula, Student-t Tail Copula & Pace Stress Sim) + Sharp +EV (${uiState.allLegs.size} legs).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF18212C).copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, quant.cardBorder)
                            ) {
                                Text(
                                    text = "Min Prob ${OddsMath.formatPercent(uiState.preferences.minHitProbability, decimals = 0)} • Min ρ ${String.format("%+.2f", uiState.preferences.minCorrelationRho)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = quant.correlationCyan,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                            status.lastUpdatedMillis?.let { ts ->
                                val timeStr = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(ts))
                                val liveTag = if (uiState.autoRefreshEnabled) {
                                    if (liveCount > 0) "Auto 12s ($liveCount LIVE) • $timeStr"
                                    else "Auto-Live 12s • $timeStr"
                                } else {
                                    "Synced $timeStr"
                                }
                                Text(
                                    text = liveTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = quant.positiveEv,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // NBA News & Weekly Availability Quick-Access Card
            item(key = "nba_news_quick_card") {
                val outCount = uiState.nbaIntel.weeklyPlayerStatuses.count {
                    it.bucket == NbaAvailabilityBucket.WONT_PLAY
                }
                val b2bCount = uiState.nbaIntel.teamWeekSchedules.count { it.hasBackToBack }
                val topHeadline = uiState.nbaIntel.articles.firstOrNull()?.headline
                    ?: "View NBA injuries, who will/won't play this week & breaking league news"

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable(onClick = onOpenNbaNews)
                        .testTag("home_nba_news_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = null,
                            tint = quant.correlationCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NBA NEWS & WEEKLY OUTLOOK • $outCount OUT • $b2bCount B2B",
                                style = MaterialTheme.typography.labelSmall,
                                color = quant.correlationCyan
                            )
                            Text(
                                text = topHeadline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open NBA News and Weekly Availability",
                            tint = quant.correlationCyan
                        )
                    }
                }
            }

            item(key = "loading_bar") {
                AnimatedVisibility(
                    visible = status.isLoading && uiState.topThreeParlays.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        color = quant.positiveEv,
                        trackColor = quant.elevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            if (status.errorMessage != null && uiState.topThreeParlays.isEmpty()) {
                item(key = "error_banner") {
                    Surface(
                        color = quant.negativeContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.negativeCoral.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Market Feed Notice",
                                style = MaterialTheme.typography.titleMedium,
                                color = quant.negativeCoral
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = status.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("retry_scan_button")
                            ) {
                                Text("Retry Live Scan")
                            }
                        }
                    }
                }
            }

            if (status.isLoading && uiState.topThreeParlays.isEmpty()) {
                item(key = "initial_loading") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator(color = quant.positiveEv)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Collecting Live Sports & Odds Feeds",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fetching NBA, NFL, NHL, MLB, EPL, NCAAB & NCAAF lines and solving Cholesky correlation matrices...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (uiState.topThreeParlays.isEmpty() && !status.isLoading) {
                item(key = "empty_state") {
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                                contentDescription = null,
                                tint = quant.correlationCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Parlays Meet Current Quant Filters",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Lower your minimum hit probability or correlation threshold in Quant Lab, or refresh active sports feeds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onOpenQuantLab,
                                    modifier = Modifier.testTag("open_filters_button")
                                ) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adjust Filters")
                                }
                                Button(onClick = onRefresh) {
                                    Text("Rescan")
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "top_3_section_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOP 3 RECOMMENDED PARLAYS (3-PICK)",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.positiveEv
                        )
                        Text(
                            text = "Tap any player for pick why",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan
                        )
                    }
                }

                // Top #1, #2, and #3 Spotlight Cards
                items(
                    items = uiState.topThreeParlays,
                    key = { "top_${it.id}" }
                ) { parlay ->
                    TopParlaySpotlightCard(
                        parlay = parlay,
                        oddsFormat = uiState.preferences.oddsFormat,
                        onClick = { onOpenParlay(parlay) },
                        onToggleSave = { onToggleSave(parlay) },
                        onClickPlayerLeg = onClickPlayerLeg
                    )
                }

                if (uiState.runnerUpParlays.isNotEmpty()) {
                    item(key = "runner_up_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MORE 3-PICK UPCOMING PARLAYS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.runnerUpParlays.size} ranked",
                                style = MaterialTheme.typography.labelSmall,
                                color = quant.correlationCyan
                            )
                        }
                    }

                    items(
                        items = uiState.runnerUpParlays,
                        key = { "sub_${it.id}" }
                    ) { parlay ->
                        CompactParlayCard(
                            parlay = parlay,
                            oddsFormat = uiState.preferences.oddsFormat,
                            onClick = { onOpenParlay(parlay) },
                            onToggleSave = { onToggleSave(parlay) }
                        )
                    }
                }
            }

            item(key = "disclaimer_footer") {
                AnalyticsDisclaimerFooter()
            }
        }
    }
}
