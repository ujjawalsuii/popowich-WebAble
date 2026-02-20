package com.example.myapplication.analyzer

import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Flash detection based on the W3C WCAG 2.1 photosensitive seizure standard:
 * Content is dangerous if it flashes MORE THAN 3 TIMES PER SECOND.
 * 
 * A "flash" = one complete bright→dark→bright cycle (2 direction reversals).
 * We count complete cycles over a 1-second sliding window per region.
 * 
 * This approach is immune to scrolling/swiping because:
 * - Scrolling rarely creates 3+ complete oscillation CYCLES per second in one region
 * - Real strobing easily hits 5-15+ cycles per second
 * - The luminance threshold (50) filters out subtle contrast from scrolling
 */
class FrameAnalyzer(private val onFlashingDetected: () -> Unit) : ImageReader.OnImageAvailableListener {
    private val regionGridSize = 8  // 8x8 = 64 regions for detecting small/PiP videos
    private val totalRegions = regionGridSize * regionGridSize
    private val previousRegionLuminance = DoubleArray(totalRegions) { -1.0 }
    
    // Direction: +1 = brighter, -1 = darker
    private val regionLastDirection = IntArray(totalRegions)
    
    // Store timestamps of each direction reversal per region (sliding window)
    private val regionReversalTimestamps = Array(totalRegions) { mutableListOf<Long>() }

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        // Luminance change must be at least this much to count
        // Normal scrolling = 10-30, real flashing = 60-200+
        private const val LUM_THRESHOLD = 50.0
        
        // WCAG: dangerous = 3+ flashes per second
        // 1 flash = 2 reversals, so 3 flashes = 6 reversals in 1 second
        private const val REVERSALS_PER_SECOND = 6
        private const val WINDOW_MS = 1000L
        
        private const val SAMPLES_PER_REGION = 5
    }

    override fun onImageAvailable(reader: ImageReader?) {
        val image: Image? = try {
            reader?.acquireLatestImage()
        } catch (e: Exception) { null }

        image?.use {
            val planes = it.planes
            if (planes.isEmpty() || planes[0].buffer == null) return

            val now = System.currentTimeMillis()
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = it.width
            val height = it.height
            val regionWidth = width / regionGridSize
            val regionHeight = height / regionGridSize

            val currentLuminances = DoubleArray(totalRegions)

            try {
                for (ry in 0 until regionGridSize) {
                    for (rx in 0 until regionGridSize) {
                        val regionIndex = ry * regionGridSize + rx
                        val startX = rx * regionWidth
                        val startY = ry * regionHeight
                        var totalLum = 0.0
                        var sampleCount = 0

                        for (sy in 0 until SAMPLES_PER_REGION) {
                            for (sx in 0 until SAMPLES_PER_REGION) {
                                val x = startX + (regionWidth / SAMPLES_PER_REGION) * sx
                                val y = startY + (regionHeight / SAMPLES_PER_REGION) * sy
                                val pixelOffset = y * rowStride + x * pixelStride
                                if (pixelOffset + 2 < buffer.capacity()) {
                                    buffer.position(pixelOffset)
                                    val r = buffer.get().toInt() and 0xFF
                                    val g = buffer.get().toInt() and 0xFF
                                    val b = buffer.get().toInt() and 0xFF
                                    totalLum += 0.299 * r + 0.587 * g + 0.114 * b
                                    sampleCount++
                                }
                            }
                        }
                        currentLuminances[regionIndex] = if (sampleCount > 0) totalLum / sampleCount else 0.0
                    }
                }
            } catch (e: Exception) { return }

            // Check each region for oscillation
            for (regionIndex in 0 until totalRegions) {
                if (previousRegionLuminance[regionIndex] < 0) {
                    previousRegionLuminance[regionIndex] = currentLuminances[regionIndex]
                    continue
                }

                val delta = currentLuminances[regionIndex] - previousRegionLuminance[regionIndex]
                val absDelta = Math.abs(delta)

                if (absDelta > LUM_THRESHOLD) {
                    val direction = if (delta > 0) 1 else -1
                    val prevDirection = regionLastDirection[regionIndex]

                    // Did the direction reverse? That's half a flash cycle
                    if (prevDirection != 0 && direction != prevDirection) {
                        // Record this reversal timestamp
                        regionReversalTimestamps[regionIndex].add(now)
                        
                        // Clean out old timestamps outside our 1-second window
                        regionReversalTimestamps[regionIndex].removeAll { it < now - WINDOW_MS }
                        
                        val reversalsInWindow = regionReversalTimestamps[regionIndex].size
                        
                        if (reversalsInWindow >= REVERSALS_PER_SECOND) {
                            val rx = regionIndex % regionGridSize
                            val ry = regionIndex / regionGridSize
                            Log.d("FrameAnalyzer", "WCAG violation [$rx,$ry]: $reversalsInWindow reversals/sec")
                            mainHandler.post { onFlashingDetected() }
                            // Clear to avoid rapid re-triggers
                            regionReversalTimestamps[regionIndex].clear()
                        }
                    }

                    regionLastDirection[regionIndex] = direction
                }

                previousRegionLuminance[regionIndex] = currentLuminances[regionIndex]
            }
        }
    }
}
