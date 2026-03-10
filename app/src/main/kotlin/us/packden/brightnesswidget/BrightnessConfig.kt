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
    const val ICON_SIZE_DP = 32       // brightness icon centered in the first segment

    /**
     * Gamma exponent for perceptual brightness mapping.
     *
     * The Android Quick Settings brightness slider uses a gamma curve
     * (AOSP BrightnessUtils.convertLinearToGamma / convertGammaToLinear)
     * so that evenly-spaced slider positions correspond to perceptually
     * even brightness changes. GAMMA ≈ 2.2 matches the AOSP curve.
     *
     * With this curve:
     *   - Widget steps map to evenly-spaced positions on the QS slider
     *   - Step 1 = slider at minimum, Step N = slider at 100%
     *   - More steps are allocated to the dim end (where the eye is
     *     more sensitive) and fewer to the bright end
     */
    const val GAMMA = 2.2f
}
