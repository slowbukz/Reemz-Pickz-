package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketType
import com.example.domain.model.OddsFormat
import com.example.ui.theme.LocalQuantColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParlayDetailSheet(
    parlay: CorrelatedParlay,
    oddsFormat: OddsFormat,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onClickPlayerLeg: ((MarketLeg) -> Unit)? = null
) {
    val quant = LocalQuantColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (parlay.rank > 0) "RANK #${parlay.rank} CORRELATED EDGE" else "CUSTOM CORRELATION LAB",
                        style = MaterialTheme.typography.labelMedium,
                        color = quant.positiveEv
                    )
                    Text(
                        text = "${parlay.legs.size}-Pick Multi-Sim Breakdown",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_detail_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close breakdown"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Metric Quant Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Correlated Prob",
                    value = OddsMath.formatPercent(parlay.correlatedProb),
                    valueColor = quant.positiveEv,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Correlated EV",
                    value = OddsMath.formatPercent(parlay.correlatedEvPercent, includeSign = true),
                    valueColor = if (parlay.correlatedEvPercent >= 0) quant.positiveEv else quant.negativeCoral,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Market / Fair Odds",
                    value = "${OddsMath.formatOdds(parlay.marketParlayAmericanOdds, oddsFormat)} / ${OddsMath.formatOdds(parlay.correlatedFairAmericanOdds, oddsFormat)}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "0.25x Kelly Unit",
                    value = "${String.format("%.2f", parlay.quarterKellyPercent)}%",
                    valueColor = quant.correlationCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProbabilityLiftBar(
                independentProb = parlay.independentProb,
                correlatedProb = parlay.correlatedProb
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                        Text(
                            text = "SHARP SIM SUITE (${parlay.simConsensusCount / 1000}K TRIALS)",
                            style = MaterialTheme.typography.labelMedium,
                            color = quant.correlationCyan
                        )
                        Text(
                            text = parlay.sharpSoftwareGrade,
                            style = MaterialTheme.typography.labelSmall,
                            color = quant.positiveEv
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "Gaussian (10k)",
                            value = OddsMath.formatPercent(parlay.gaussianCopulaProb),
                            valueColor = quant.positiveEv,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Student-t (10k)",
                            value = OddsMath.formatPercent(parlay.studentTCopulaProb),
                            valueColor = quant.correlationCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Pace Stress (5k)",
                            value = OddsMath.formatPercent(parlay.paceStressSimProb),
                            valueColor = quant.moderateAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "INDIVIDUAL LEG VALUATION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, quant.cardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    parlay.legs.forEachIndexed { idx, leg ->
                        val isPropLeg = leg.marketType == MarketType.PLAYER_PROP_MORE || leg.marketType == MarketType.PLAYER_PROP_LESS
                        ParlayLegRow(
                            leg = leg,
                            oddsFormat = oddsFormat,
                            onClick = if (isPropLeg && onClickPlayerLeg != null) {
                                { onClickPlayerLeg(leg) }
                            } else null
                        )
                        Text(
                            text = "Model Source: ${leg.sharpBookLabel} • Market Implied ${OddsMath.formatPercent(leg.marketImpliedProb)} vs Fair ${OddsMath.formatPercent(leg.fairTrueProb)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (idx < parlay.legs.lastIndex) {
                            HorizontalDivider(
                                color = quant.cardBorder,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "PAIRWISE COVARIANCE MATRIX (ρ)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            parlay.correlationLinks.forEach { link ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, quant.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${link.legALabel} ↔ ${link.legBLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RhoChip(rho = link.rho)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = link.thesis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggleSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (parlay.isSaved) quant.elevatedSurface else quant.positiveEv,
                    contentColor = if (parlay.isSaved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("detail_save_button")
            ) {
                Icon(
                    imageVector = if (parlay.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (parlay.isSaved) "Saved in Quant Watchlist" else "Save to Quant Watchlist",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            AnalyticsDisclaimerFooter()
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val quant = LocalQuantColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, quant.cardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor
        )
    }
}
