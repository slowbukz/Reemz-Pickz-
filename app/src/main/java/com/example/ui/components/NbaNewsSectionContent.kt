package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.InjurySeverity
import com.example.domain.model.NbaAvailabilityBucket
import com.example.domain.model.NbaIntelBundle
import com.example.domain.model.NbaNewsArticle
import com.example.domain.model.NbaNewsCategory
import com.example.domain.model.NbaTeamStandingRow
import com.example.domain.model.NbaTeamWeekSchedule
import com.example.domain.model.NbaTransactionItem
import com.example.domain.model.NbaWeeklyPlayerStatus
import com.example.ui.theme.LocalQuantColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NbaNewsSectionContent(
    nbaIntel: NbaIntelBundle,
    onRefreshIntel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    val uriHandler = LocalUriHandler.current

    var selectedBucket by remember { mutableStateOf(NbaAvailabilityBucket.WONT_PLAY) }
    var selectedNewsCategory by remember { mutableStateOf(NbaNewsCategory.ALL) }
    var showTransactionsInstead by remember { mutableStateOf(false) }
    var selectedConference by remember { mutableStateOf("East") }
    var showAllAvailability by remember { mutableStateOf(false) }

    val wontPlayList = remember(nbaIntel.weeklyPlayerStatuses) {
        nbaIntel.weeklyPlayerStatuses.filter { it.bucket == NbaAvailabilityBucket.WONT_PLAY }
    }
    val questionableList = remember(nbaIntel.weeklyPlayerStatuses) {
        nbaIntel.weeklyPlayerStatuses.filter { it.bucket == NbaAvailabilityBucket.QUESTIONABLE }
    }
    val playingList = remember(nbaIntel.weeklyPlayerStatuses) {
        nbaIntel.weeklyPlayerStatuses.filter { it.bucket == NbaAvailabilityBucket.PLAYING_THIS_WEEK }
    }

    val activeAvailabilityList = when (selectedBucket) {
        NbaAvailabilityBucket.WONT_PLAY -> wontPlayList
        NbaAvailabilityBucket.QUESTIONABLE -> questionableList
        NbaAvailabilityBucket.PLAYING_THIS_WEEK -> playingList
    }
    val visibleAvailability = if (showAllAvailability) activeAvailabilityList else activeAvailabilityList.take(12)

    val filteredArticles = remember(nbaIntel.articles, selectedNewsCategory) {
        if (selectedNewsCategory == NbaNewsCategory.ALL) {
            nbaIntel.articles
        } else {
            nbaIntel.articles.filter { it.category == selectedNewsCategory }
        }
    }

    val conferenceStandings = remember(nbaIntel.standings, selectedConference) {
        nbaIntel.standings.filter {
            it.conference.contains(selectedConference, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Banner: Week Outlook Summary + Manual Refresh
        item(key = "nba_intel_header") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, quant.correlationCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NBA WEEKLY INTEL • ${nbaIntel.weekRangeDisplay.uppercase(Locale.US)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.correlationCyan
                            )
                            Text(
                                text = "Major league news, who's in/out this week & B2B pick edges",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            nbaIntel.lastUpdatedMillis?.let { ts ->
                                val timeStr = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(ts))
                                Text(
                                    text = "Live ESPN NBA Sync • Updated $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = quant.positiveEv
                                )
                            }
                        }
                        IconButton(
                            onClick = onRefreshIntel,
                            enabled = !nbaIntel.isLoading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_nba_intel_button")
                        ) {
                            if (nbaIntel.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = quant.correlationCyan
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh NBA News and Weekly Availability",
                                    tint = quant.correlationCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntelStatPill(
                            label = "Won't Play",
                            value = "${wontPlayList.size}",
                            accent = quant.negativeCoral,
                            container = quant.negativeContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IntelStatPill(
                            label = "GTD / Ques",
                            value = "${questionableList.size}",
                            accent = quant.moderateAmber,
                            container = quant.moderateContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IntelStatPill(
                            label = "B2B Alerts",
                            value = "${nbaIntel.teamWeekSchedules.count { it.hasBackToBack }}",
                            accent = quant.correlationCyan,
                            container = quant.correlationContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (nbaIntel.isLoading && nbaIntel.articles.isEmpty() && nbaIntel.weeklyPlayerStatuses.isEmpty()) {
            item(key = "nba_intel_loading") {
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
                        CircularProgressIndicator(color = quant.correlationCyan)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pulling Live NBA News, Weekly Injuries & Schedule Edges...",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        nbaIntel.errorMessage?.let { err ->
            item(key = "nba_intel_error") {
                Surface(
                    color = quant.negativeContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, quant.negativeCoral.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = quant.negativeCoral,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 2. Who Will & Won't Play This Week
        item(key = "nba_weekly_availability") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, quant.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nba_weekly_availability_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocalHospital,
                            contentDescription = null,
                            tint = quant.negativeCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WHO WILL & WON'T PLAY THIS WEEK",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Official ESPN NBA availability synced with this week's upcoming schedule & beneficiary targets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val buckets = listOf(
                            Triple(NbaAvailabilityBucket.WONT_PLAY, "Won't Play (${wontPlayList.size})", quant.negativeCoral),
                            Triple(NbaAvailabilityBucket.QUESTIONABLE, "GTD / Ques (${questionableList.size})", quant.moderateAmber),
                            Triple(NbaAvailabilityBucket.PLAYING_THIS_WEEK, "Playing This Week (${playingList.size})", quant.positiveEv)
                        )
                        buckets.forEach { (bucket, label, accent) ->
                            val isSel = selectedBucket == bucket
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedBucket = bucket
                                    showAllAvailability = false
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                ),
                                label = { Text(label) },
                                modifier = Modifier.testTag("nba_avail_tab_${bucket.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (visibleAvailability.isEmpty()) {
                        Text(
                            text = "No players currently listed under ${selectedBucket.label}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleAvailability.forEach { player ->
                                NbaWeeklyPlayerStatusRow(player = player)
                            }
                        }

                        if (activeAvailabilityList.size > 12) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = quant.elevatedSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, quant.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAllAvailability = !showAllAvailability }
                                    .testTag("nba_toggle_all_availability")
                            ) {
                                Text(
                                    text = if (showAllAvailability) {
                                        "Show top 12 players"
                                    } else {
                                        "Show all ${activeAvailabilityList.size} players in ${selectedBucket.label}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = quant.correlationCyan,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Weekly NBA Schedule Workload, Back-to-Backs & High-Total Edges
        if (nbaIntel.teamWeekSchedules.isNotEmpty()) {
            item(key = "nba_schedule_edges") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = quant.moderateAmber,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "THIS WEEK'S NBA B2B & SCHEDULE EDGES",
                                style = MaterialTheme.typography.labelMedium,
                                color = quant.moderateAmber
                            )
                        }
                        Text(
                            text = "${nbaIntel.teamWeekSchedules.size} teams active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(
                            items = nbaIntel.teamWeekSchedules,
                            key = { "nba_wk_${it.teamAbbr}" }
                        ) { sched ->
                            NbaTeamScheduleEdgeCard(schedule = sched)
                        }
                    }
                }
            }
        }

        // 4. Major NBA News & Pick-Impact Intelligence + Roster Transactions
        item(key = "nba_news_section_header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = null,
                            tint = quant.positiveEv,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MAJOR NBA NEWS & PICK EDGES",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.positiveEv
                        )
                    }
                    Text(
                        text = if (showTransactionsInstead) "${nbaIntel.transactions.size} moves" else "${filteredArticles.size} stories",
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
                    NbaNewsCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = !showTransactionsInstead && selectedNewsCategory == cat,
                            onClick = {
                                showTransactionsInstead = false
                                selectedNewsCategory = cat
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = quant.positiveEvContainer,
                                selectedLabelColor = quant.positiveEv
                            ),
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("nba_news_cat_${cat.name}")
                        )
                    }
                    FilterChip(
                        selected = showTransactionsInstead,
                        onClick = { showTransactionsInstead = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = quant.correlationContainer,
                            selectedLabelColor = quant.correlationCyan
                        ),
                        label = { Text("Transactions (${nbaIntel.transactions.size})") },
                        modifier = Modifier.testTag("nba_news_transactions_tab")
                    )
                }
            }
        }

        if (showTransactionsInstead) {
            if (nbaIntel.transactions.isEmpty()) {
                item(key = "nba_tx_empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No recent NBA roster transactions returned right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = nbaIntel.transactions, key = { it.id }) { tx ->
                    NbaTransactionCard(tx = tx)
                }
            }
        } else {
            if (filteredArticles.isEmpty()) {
                item(key = "nba_news_empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, quant.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No NBA articles matched this filter yet. Try 'All Intel' or tap refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = filteredArticles, key = { "nba_art_${it.id}" }) { article ->
                    NbaNewsArticleCard(
                        article = article,
                        onOpenUrl = { url ->
                            runCatching { uriHandler.openUri(url) }
                        }
                    )
                }
            }
        }

        // 5. Live NBA Standings, Hot Streaks & Differential (For Spread / ML Picks)
        if (nbaIntel.standings.isNotEmpty()) {
            item(key = "nba_standings_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, quant.cardBorder),
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
                                    imageVector = Icons.Rounded.AutoGraph,
                                    contentDescription = null,
                                    tint = quant.correlationCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NBA STANDINGS, STREAKS & POINT DIFF",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = quant.correlationCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("East", "West").forEach { conf ->
                                FilterChip(
                                    selected = selectedConference == conf,
                                    onClick = { selectedConference = conf },
                                    label = { Text("$conf Conference") },
                                    modifier = Modifier.testTag("nba_standings_$conf")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TEAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.4f))
                            Text("W-L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.6f))
                            Text("STRK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.55f))
                            Text("L10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.55f))
                            Text("DIFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.55f))
                        }
                        HorizontalDivider(color = quant.cardBorder)
                        conferenceStandings.forEach { row ->
                            NbaStandingLine(row = row)
                        }
                    }
                }
            }
        }

        item(key = "nba_intel_disclaimer") {
            AnalyticsDisclaimerFooter()
        }
    }
}

@Composable
private fun IntelStatPill(
    label: String,
    value: String,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = container.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NbaWeeklyPlayerStatusRow(player: NbaWeeklyPlayerStatus) {
    val quant = LocalQuantColors.current
    val (bg, fg, icon) = when (player.bucket) {
        NbaAvailabilityBucket.WONT_PLAY -> Triple(quant.negativeContainer, quant.negativeCoral, Icons.Rounded.PersonOff)
        NbaAvailabilityBucket.QUESTIONABLE -> Triple(quant.moderateContainer, quant.moderateAmber, Icons.Rounded.Schedule)
        NbaAvailabilityBucket.PLAYING_THIS_WEEK -> Triple(quant.positiveEvContainer, quant.positiveEv, Icons.Rounded.CheckCircle)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(quant.elevatedSurface)
            .border(1.dp, quant.cardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                headshotUrl = player.headshotUrl,
                playerName = player.playerName,
                teamAbbr = player.teamAbbr,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${player.playerName} (${player.teamAbbr} ${player.position})".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (player.gamesThisWeek > 0) {
                        "${player.gamesThisWeek} gms this week • Next: ${player.nextMatchupDisplay}"
                    } else {
                        player.nextMatchupDisplay
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = quant.correlationCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = bg,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, fg.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = player.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = player.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (player.isBackToBackTeam || player.beneficiaryTeammates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (player.isBackToBackTeam) {
                    Surface(
                        color = quant.moderateContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${player.teamAbbr} B2B WEEK",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.moderateAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                player.beneficiaryTeammates.forEach { teammate ->
                    Surface(
                        color = quant.positiveEvContainer,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, quant.positiveEv.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Usage Boost: $teammate",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.positiveEv,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NbaTeamScheduleEdgeCard(schedule: NbaTeamWeekSchedule) {
    val quant = LocalQuantColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = 1.dp,
            color = if (schedule.hasBackToBack) quant.moderateAmber.copy(alpha = 0.7f) else quant.cardBorder
        ),
        modifier = Modifier.width(196.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogoBadge(logoUrl = schedule.logoUrl, fallbackText = schedule.teamAbbr, size = 22.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = schedule.teamAbbr,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (schedule.hasBackToBack) {
                    Surface(
                        color = quant.moderateContainer,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = "B2B SPOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.moderateAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (schedule.gamesThisWeek >= 4) {
                    Surface(
                        color = quant.correlationContainer,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = "4-GM WK",
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.correlationCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${schedule.gamesThisWeek} games this week",
                style = MaterialTheme.typography.labelMedium,
                color = quant.positiveEv
            )
            Text(
                text = "Next: ${schedule.nextOpponentDisplay}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            schedule.highestTotalLine?.let { tot ->
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Peak Vegas O/U: ${String.format(Locale.US, "%.1f", tot)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = quant.correlationCyan
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NbaNewsArticleCard(
    article: NbaNewsArticle,
    onOpenUrl: (String) -> Unit
) {
    val quant = LocalQuantColors.current
    val (catBg, catFg) = when (article.category) {
        NbaNewsCategory.INJURY_UPDATE -> quant.negativeContainer to quant.negativeCoral
        NbaNewsCategory.LINEUP_ROTATION -> quant.moderateContainer to quant.moderateAmber
        NbaNewsCategory.PICK_EDGE -> quant.positiveEvContainer to quant.positiveEv
        else -> quant.correlationContainer to quant.correlationCyan
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, quant.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!article.articleUrl.isNullOrBlank()) {
                    Modifier.clickable { onOpenUrl(article.articleUrl) }
                } else Modifier
            )
            .testTag("nba_article_${article.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = catBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, catFg.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = article.category.label.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        color = catFg,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = article.publishedDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!article.articleUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open full NBA story",
                            tint = quant.correlationCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.headline,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(146.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = article.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (article.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pick Impact Callout
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(quant.elevatedSurface)
                    .border(1.dp, catFg.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(9.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = catFg,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = article.pickImpactNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (article.teamTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    article.teamTags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = quant.correlationCyan,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NbaTransactionCard(tx: NbaTransactionItem) {
    val quant = LocalQuantColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
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
                Surface(
                    color = quant.correlationContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${tx.teamAbbr} • ${tx.impactTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = quant.correlationCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = tx.dateDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tx.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun NbaStandingLine(row: NbaTeamStandingRow) {
    val quant = LocalQuantColors.current
    val isHotStreak = row.streakDisplay.startsWith("W", ignoreCase = true)
    val diffPositive = row.pointDiffDisplay.startsWith("+") ||
        (row.pointDiffDisplay.toDoubleOrNull()?.let { it > 0.0 } == true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1.4f)
        ) {
            Text(
                text = "${row.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp)
            )
            TeamLogoBadge(logoUrl = row.logoUrl, fallbackText = row.teamAbbr, size = 20.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = row.teamAbbr,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "${row.wins}-${row.losses}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
        Text(
            text = row.streakDisplay,
            style = MaterialTheme.typography.labelMedium,
            color = if (isHotStreak) quant.positiveEv else quant.negativeCoral,
            modifier = Modifier.weight(0.55f)
        )
        Text(
            text = row.lastTenDisplay,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.55f)
        )
        Text(
            text = row.pointDiffDisplay,
            style = MaterialTheme.typography.labelMedium,
            color = if (diffPositive) quant.positiveEv else quant.negativeCoral,
            modifier = Modifier.weight(0.55f)
        )
    }
}
