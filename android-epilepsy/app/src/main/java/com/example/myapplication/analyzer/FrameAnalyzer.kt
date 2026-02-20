package com.example.myapplication.analyzer

import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Analyzes screen frames for rapid flashing by dividing the screen into regions.
 * Each region is tracked independently so small flashing GIFs in a browser grid
 * can still trigger the overlay even if the rest of the screen is static.
 */
class FrameAnalyzer(private val onFlashingDetected: () -> Unit) : ImageReader.OnImageAvailableListener {
    // Divide screen into a grid of regions for localized flash detection
    private val regionGridSize = 4 // 4x4 = 16 regions
    private val previousRegionLuminance = DoubleArray(regionGridSize * regionGridSize) { -1.0 }
    private val regionFlashCount = IntArray(regionGridSize * regionGridSize)
    private val regionLastFlashTime = LongArray(regionGridSize * regionGridSize)
    
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val LUM_THRESHOLD = 25.0    // Luminance change to count as a "flash"
        private const val FLASH_WINDOW_MS = 500L   // Time window for counting rapid flashes
        private const val REQUIRED_FLASHES = 3     // Number of rapid transitions to confirm flashing
        private const val SAMPLES_PER_REGION = 5   // Sample grid within each region
    }

    override fun onImageAvailable(reader: ImageReader?) {
        val image: Image? = try {
            reader?.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        image?.use {
            val planes = it.planes
            if (planes.isEmpty() || planes[0].buffer == null) return

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = it.width
            val height = it.height

            val regionWidth = width / regionGridSize
            val regionHeight = height / regionGridSize

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

                        if (sampleCount == 0) continue
                        val currentLum = totalLum / sampleCount

                        if (previousRegionLuminance[regionIndex] >= 0) {
                            val diff = Math.abs(currentLum - previousRegionLuminance[regionIndex])

                            if (diff > LUM_THRESHOLD) {
                                val now = System.currentTimeMillis()
                                if (now - regionLastFlashTime[regionIndex] < FLASH_WINDOW_MS) {
                                    regionFlashCount[regionIndex]++
                                    if (regionFlashCount[regionIndex] >= REQUIRED_FLASHES) {
                                        Log.d("FrameAnalyzer", "Region [$rx,$ry] flashing! diff=${"%.1f".format(diff)}")
                                        mainHandler.post { onFlashingDetected() }
                                        regionFlashCount[regionIndex] = 0
                                    }
                                } else {
                                    regionFlashCount[regionIndex] = 1
                                }
                                regionLastFlashTime[regionIndex] = now
                            }
                        }
                        previousRegionLuminance[regionIndex] = currentLum
                    }
                }
            } catch (e: Exception) {
                // buffer issue, skip frame
            }
        }
    }
}
