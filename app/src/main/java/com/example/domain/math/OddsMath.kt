package com.example.domain.math

import com.example.domain.model.DevigMethod
import com.example.domain.model.OddsFormat
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object OddsMath {

    fun americanToDecimal(american: Int): Double {
        return when {
            american >= 100 -> 1.0 + (american / 100.0)
            american <= -100 -> 1.0 + (100.0 / abs(american).toDouble())
            else -> 1.91
        }
    }

    fun decimalToAmerican(decimal: Double): Int {
        val safe = decimal.coerceAtLeast(1.01)
        return if (safe >= 2.0) {
            ((safe - 1.0) * 100.0).roundToInt()
        } else {
            (-100.0 / (safe - 1.0)).roundToInt()
        }
    }

    fun americanToImpliedProb(american: Int): Double {
        val dec = americanToDecimal(american)
        return (1.0 / dec).coerceIn(0.01, 0.99)
    }

    fun probToAmerican(prob: Double): Int {
        val clamped = prob.coerceIn(0.015, 0.985)
        return decimalToAmerican(1.0 / clamped)
    }

    fun formatOdds(american: Int, format: OddsFormat): String {
        return when (format) {
            OddsFormat.AMERICAN -> if (american > 0) "+$american" else "$american"
            OddsFormat.DECIMAL -> String.format("%.2f", americanToDecimal(american))
        }
    }

    fun formatPercent(value: Double, includeSign: Boolean = false, decimals: Int = 1): String {
        val pct = value * 100.0
        val fmt = "%.${decimals}f%%"
        val formatted = String.format(fmt, pct)
        return if (includeSign && pct > 0) "+$formatted" else formatted
    }

    /**
     * Strips sportsbook vig from a 2-way market (e.g. Home/Away or Over/Under)
     * to recover fair implied probabilities summing to 1.0.
     */
    fun devigTwoWay(
        probA: Double,
        probB: Double,
        method: DevigMethod
    ): Pair<Double, Double> {
        val pA = probA.coerceIn(0.01, 0.99)
        val pB = probB.coerceIn(0.01, 0.99)
        val sum = pA + pB
        if (sum <= 1.0) {
            return (pA / sum) to (pB / sum)
        }
        return when (method) {
            DevigMethod.MULTIPLICATIVE -> {
                (pA / sum) to (pB / sum)
            }
            DevigMethod.POWER -> {
                // Solve pA^k + pB^k = 1 via binary search (handles favorite-longshot bias better than multiplicative)
                var low = 1.0
                var high = 3.0
                for (i in 0 until 24) {
                    val mid = (low + high) / 2.0
                    val total = pA.pow(mid) + pB.pow(mid)
                    if (total > 1.0) low = mid else high = mid
                }
                val k = (low + high) / 2.0
                val fairA = pA.pow(k)
                val fairB = pB.pow(k)
                val norm = fairA + fairB
                (fairA / norm) to (fairB / norm)
            }
        }
    }

    /**
     * Expected Value = (TrueProb * DecimalOdds) - 1
     */
    fun expectedValue(trueProb: Double, decimalOdds: Double): Double {
        return (trueProb.coerceIn(0.001, 0.999) * decimalOdds) - 1.0
    }

    /**
     * Quarter-Kelly stake percentage (capped at 5.0% bankroll risk for discipline)
     */
    fun quarterKellyPercent(trueProb: Double, decimalOdds: Double): Double {
        val b = (decimalOdds - 1.0).coerceAtLeast(0.01)
        val p = trueProb.coerceIn(0.01, 0.99)
        val q = 1.0 - p
        val fullKelly = ((b * p) - q) / b
        return (max(0.0, fullKelly) * 0.25 * 100.0).coerceAtMost(5.0)
    }

    /**
     * Standard Normal CDF Φ(x) via Abramowitz & Stegun 7.1.26
     */
    fun normalCdf(x: Double): Double {
        val z = x / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * abs(z))
        val poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
        val erf = 1.0 - poly * exp(-z * z)
        return if (z >= 0.0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
    }

    /**
     * Inverse Standard Normal CDF Φ^-1(p) via Peter Acklam's rational approximation
     */
    fun inverseNormalCdf(p: Double): Double {
        val prob = p.coerceIn(1e-6, 1.0 - 1e-6)
        val a = doubleArrayOf(
            -3.969683028665376e+01,
            2.209460984245205e+02,
            -2.759285104469687e+02,
            1.383577518672690e+02,
            -3.066479806614716e+01,
            2.506628277459239e+00
        )
        val b = doubleArrayOf(
            -5.447609879822406e+01,
            1.615858368580409e+02,
            -1.556989798598866e+02,
            6.680131188771972e+01,
            -1.328068155288572e+01
        )
        val c = doubleArrayOf(
            -7.784894002430293e-03,
            -3.223964580411365e-01,
            -2.400758277161838e+00,
            -2.549732539343734e+00,
            4.374664141464968e+00,
            2.938163982698783e+00
        )
        val d = doubleArrayOf(
            7.784695709041462e-03,
            3.224671290700398e-01,
            2.445134137142996e+00,
            3.754408661907416e+00
        )
        val pLow = 0.02425
        val pHigh = 1.0 - pLow
        return when {
            prob < pLow -> {
                val q = sqrt(-2.0 * ln(prob))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            prob <= pHigh -> {
                val q = prob - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
            }
            else -> {
                val q = sqrt(-2.0 * ln(1.0 - prob))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
        }
    }

    /**
     * Exact bivariate normal orthant / joint survival probability P(Z1 <= c1, Z2 <= c2)
     * using Gauss-Legendre / numerical integration on Drezner-Wesolowsky identity.
     */
    fun bivariateNormalJointProb(pA: Double, pB: Double, rho: Double): Double {
        val clippedRho = rho.coerceIn(-0.92, 0.92)
        if (abs(clippedRho) < 1e-4) return pA * pB

        val cA = inverseNormalCdf(pA)
        val cB = inverseNormalCdf(pB)
        // Numerical integration of φ(z) * Φ((cB - rho*z) / sqrt(1 - rho^2)) from -6 to cA
        val steps = 64
        val low = -5.5
        if (cA <= low) return 0.0001
        val h = (cA - low) / steps
        val denom = sqrt(1.0 - clippedRho * clippedRho)
        var sum = 0.0
        for (i in 0..steps) {
            val z = low + i * h
            val weight = when {
                i == 0 || i == steps -> 0.5
                else -> 1.0
            }
            val pdf = exp(-0.5 * z * z) / sqrt(2.0 * Math.PI)
            val cond = normalCdf((cB - clippedRho * z) / denom)
            sum += weight * pdf * cond
        }
        val joint = sum * h
        return joint.coerceIn(0.005, min(pA, pB))
    }
}
