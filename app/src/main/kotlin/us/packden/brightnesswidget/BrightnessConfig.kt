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
    const val SEGMENT_HEIGHT_DP = 48  // height of the bar in dp
    const val CORNER_RADIUS_DP = 8    // rounded corners on the outer bar
    const val DIVIDER_WIDTH_DP = 1    // hairline gap between segments
}
