package com.wifibodydetector

import kotlin.math.sqrt
import kotlin.math.abs

class AnomalyDetector(
    private var thresholdSigma: Double = 1.5,
    windowSize: Int = 30
) {
    private val rssiHistory = mutableListOf<Double>()
    private val diffHistory = mutableListOf<Double>()
    private val maxWindowSize = windowSize

    data class DetectionResult(
        val isAnomaly: Boolean,
        val anomalyScore: Double,
        val currentRSSI: Double,
        val meanRSSI: Double,
        val stdDevRSSI: Double,
        val meanDiff: Double,
        val stdDevDiff: Double
    )

    fun addReading(rssi: Double): DetectionResult {
        rssiHistory.add(rssi)
        if (rssiHistory.size > maxWindowSize) {
            rssiHistory.removeAt(0)
        }

        if (rssiHistory.size >= 2) {
            val diff = abs(rssiHistory[rssiHistory.size - 1] - rssiHistory[rssiHistory.size - 2])
            diffHistory.add(diff)
            if (diffHistory.size > maxWindowSize) {
                diffHistory.removeAt(0)
            }
        }

        return computeResult()
    }

    fun setThreshold(sigma: Double) {
        thresholdSigma = sigma
    }

    private fun computeResult(): DetectionResult {
        if (rssiHistory.size < 5) {
            return DetectionResult(false, 0.0, currentRSSI(), 0.0, 0.0, 0.0, 0.0)
        }

        val mean = rssiHistory.average()
        val stdDev = standardDeviation(rssiHistory, mean)
        val current = rssiHistory.last()

        val meanDiff = if (diffHistory.isNotEmpty()) diffHistory.average() else 0.0
        val stdDevDiff = if (diffHistory.size > 1) standardDeviation(diffHistory, meanDiff) else 0.0

        val zScore = if (stdDev > 0) abs(current - mean) / stdDev else 0.0
        val diffZScore = if (stdDevDiff > 0) {
            val latestDiff = if (diffHistory.isNotEmpty()) diffHistory.last() else 0.0
            abs(latestDiff - meanDiff) / stdDevDiff
        } else 0.0

        val combinedScore = (zScore + diffZScore) / 2.0
        val isAnomaly = combinedScore > thresholdSigma && rssiHistory.size >= 10

        return DetectionResult(
            isAnomaly = isAnomaly,
            anomalyScore = combinedScore,
            currentRSSI = current,
            meanRSSI = mean,
            stdDevRSSI = stdDev,
            meanDiff = meanDiff,
            stdDevDiff = stdDevDiff
        )
    }

    private fun currentRSSI(): Double = if (rssiHistory.isNotEmpty()) rssiHistory.last() else 0.0

    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    fun reset() {
        rssiHistory.clear()
        diffHistory.clear()
    }

    fun getHistory(): List<Double> = rssiHistory.toList()
}
