package com.example.domain.math

import com.example.domain.model.GameEvent
import com.example.domain.model.InjurySeverity
import com.example.domain.model.PlayerInjuryInfo
import com.example.domain.model.PropSimScenario
import com.example.domain.model.PropSimulationResult
import com.example.domain.model.SimHistogramBucket
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

object PlayerPropSimEngine {

    /**
     * Runs a 10,000-trial Monte Carlo simulation for a specific player stat line & alt threshold,
     * blending recent recency-weighted performance, variance, upcoming Vegas game pace, and user scenario.
     */
    fun simulatePropOutcomes(
        statLabel: String,
        historicalValues: List<Double>,
        lineValue: Double,
        upcomingGame: GameEvent?,
        playerTeamAbbr: String,
        matchupInjuries: List<PlayerInjuryInfo>,
        scenario: PropSimScenario = PropSimScenario.BASELINE,
        runId: Int = 0
    ): PropSimulationResult {
        val cleanValues = historicalValues.filter { it >= 0.0 }
        val baseMean = if (cleanValues.isNotEmpty()) {
            // 65% recent L5 weight + 35% full window weight
            val fullAvg = cleanValues.average()
            val recentAvg = cleanValues.takeLast(min(5, cleanValues.size)).average()
            recentAvg * 0.65 + fullAvg * 0.35
        } else {
            lineValue + 0.5
        }

        val sampleStd = if (cleanValues.size >= 2) {
            val mean = cleanValues.average()
            val variance = cleanValues.sumOf { (it - mean) * (it - mean) } / (cleanValues.size - 1).toDouble()
            sqrt(variance).coerceAtLeast(max(0.65, baseMean * 0.16))
        } else {
            max(0.85, baseMean * 0.28)
        }

        // Upcoming game pace multiplier from total O/U vs league average
        val paceFactor = if (upcomingGame != null && upcomingGame.marketSnapshot?.totalLine != null) {
            val totalRatio = upcomingGame.marketSnapshot.totalLine / upcomingGame.league.avgGameTotal
            (1.0 + (totalRatio - 1.0) * 0.65).coerceIn(0.88, 1.15)
        } else 1.0

        // Teammate OUT injury boost (if Baseline is selected, reflect real injury boost mildly)
        val teammateOutCount = matchupInjuries.count {
            it.teamAbbr.equals(playerTeamAbbr, ignoreCase = true) &&
                (it.severity == InjurySeverity.OUT || it.severity == InjurySeverity.DOUBTFUL)
        }
        val injuryFactor = if (teammateOutCount > 0 && scenario == PropSimScenario.BASELINE) {
            (1.0 + min(teammateOutCount, 2) * 0.035)
        } else 1.0

        val totalMultiplier = (paceFactor * injuryFactor * scenario.multiplier).coerceIn(0.75, 1.32)
        val projMean = (baseMean * totalMultiplier).coerceAtLeast(0.1)
        val adjStd = (sampleStd * sqrt(totalMultiplier)).coerceAtLeast(0.55)

        val trials = 10_000
        val seed = (statLabel.hashCode().toLong() shl 16) xor
            (lineValue.toBits()) xor
            (scenario.ordinal.toLong() * 7919L) xor
            (runId.toLong() * 104729L)
        val rng = Random(seed)
        val draws = DoubleArray(trials)

        // Blend Student-t (df = 7) tail variance for realistic ceilings/floors
        var moreHits = 0
        val discreteIntegerStat = isDiscreteCountStat(statLabel)
        for (i in 0 until trials) {
            val u1 = rng.nextDouble().coerceAtLeast(1e-9)
            val u2 = rng.nextDouble()
            val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)

            // Occasional fatigue / foul / hot-hand variance shock
            val tailScale = if (rng.nextDouble() < 0.12) 1.28 else 0.96
            var simulatedVal = (projMean + z * adjStd * tailScale).coerceAtLeast(0.0)
            if (discreteIntegerStat) {
                simulatedVal = round(simulatedVal)
            }
            draws[i] = simulatedVal
            if (simulatedVal > lineValue) {
                moreHits++
            }
        }

        draws.sort()
        val simMean = draws.average()
        val simMedian = draws[trials / 2]
        val floorP10 = draws[(trials * 0.10).toInt().coerceIn(0, trials - 1)]
        val ceilingP90 = draws[(trials * 0.90).toInt().coerceIn(0, trials - 1)]

        val moreProb = (moreHits.toDouble() / trials.toDouble()).coerceIn(0.01, 0.99)
        val lessProb = (1.0 - moreProb).coerceIn(0.01, 0.99)

        val buckets = buildHistogramBuckets(draws, lineValue, discreteIntegerStat)

        return PropSimulationResult(
            statLabel = statLabel,
            lineValue = lineValue,
            scenario = scenario,
            trialCount = trials,
            simMean = simMean,
            simMedian = simMedian,
            floorP10 = floorP10,
            ceilingP90 = ceilingP90,
            moreProb = moreProb,
            lessProb = lessProb,
            fairMoreOdds = OddsMath.probToAmerican(moreProb),
            fairLessOdds = OddsMath.probToAmerican(lessProb),
            paceMultiplierApplied = totalMultiplier,
            histogram = buckets,
            runId = runId
        )
    }

    private fun buildHistogramBuckets(
        sortedDraws: DoubleArray,
        lineValue: Double,
        discrete: Boolean
    ): List<SimHistogramBucket> {
        if (sortedDraws.isEmpty()) return emptyList()
        val p02 = sortedDraws[(sortedDraws.size * 0.02).toInt()]
        val p98 = sortedDraws[(sortedDraws.size * 0.98).toInt()]
        val minBound = min(p02, lineValue * 0.6).coerceAtLeast(0.0)
        val maxBound = max(p98, lineValue * 1.4).coerceAtLeast(minBound + 2.0)

        val bucketCount = 9
        val step = ((maxBound - minBound) / bucketCount.toDouble()).coerceAtLeast(if (discrete) 1.0 else 0.5)
        val counts = IntArray(bucketCount)

        for (v in sortedDraws) {
            val idx = ((v - minBound) / step).toInt().coerceIn(0, bucketCount - 1)
            counts[idx]++
        }

        val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        return List(bucketCount) { i ->
            val low = minBound + i * step
            val high = low + step
            val mid = (low + high) / 2.0
            val label = if (step >= 4.0) {
                "${low.toInt()}"
            } else if (step >= 1.0) {
                "${round(low).toInt()}"
            } else {
                String.format(Locale.US, "%.1f", mid)
            }
            SimHistogramBucket(
                label = label,
                midpoint = mid,
                count = counts[i],
                fraction = counts[i].toDouble() / maxCount.toDouble(),
                isMoreSide = mid > lineValue
            )
        }
    }

    private fun isDiscreteCountStat(statLabel: String): Boolean {
        val lower = statLabel.lowercase(Locale.US)
        return "yard" !in lower && "yds" !in lower
    }

    /**
     * Generates smart alt line presets around the player's base line for fast inspection.
     */
    fun buildAltLinePresets(baseLine: Double, statLabel: String): List<Double> {
        val lower = statLabel.lowercase(Locale.US)
        val step = when {
            "yds" in lower || "yard" in lower -> 15.0
            baseLine >= 18.0 -> 2.0
            baseLine >= 8.0 -> 1.0
            else -> 0.5
        }
        return listOf(-2, -1, 0, 1, 2).map { mult ->
            val raw = (baseLine + mult * step).coerceAtLeast(0.5)
            // Keep clean .5 increments
            val rounded = round(raw * 2.0) / 2.0
            if (rounded % 1.0 == 0.0) (rounded - 0.5).coerceAtLeast(0.5) else rounded
        }.distinct().sorted()
    }

    fun stepIncrementForStat(baseLine: Double, statLabel: String): Double {
        val lower = statLabel.lowercase(Locale.US)
        return when {
            "yds" in lower || "yard" in lower -> 5.0
            else -> 0.5
        }
    }
}
