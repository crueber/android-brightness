package us.packden.brightnesswidget

/**
 * Central configuration for the brightness widget.
 *
 * Change BRIGHTNESS_STEPS to adjust granularity:
 *   10 = 10% increments (larger tap targets, coarser control)
 *   20 = 5% increments (smaller tap targets, finer control)
 */
object BrightnessConfig {
    const val BRIGHTNESS_STEPS = 10
    const val SEGMENT_GAP_DP = 2    // gap between segments in dp
    const val SEGMENT_HEIGHT_DP = 48 // height of the bar in dp
}
