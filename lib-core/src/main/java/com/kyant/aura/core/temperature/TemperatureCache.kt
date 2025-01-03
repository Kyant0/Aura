/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kyant.aura.core.temperature

import com.kyant.aura.core.hct.Hct
import com.kyant.aura.core.hct.HctSolver
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_11
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_12
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_13
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_21
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_22
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_23
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_31
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_32
import com.kyant.aura.core.utils.ColorUtils.SRGB_TO_XYZ_33
import com.kyant.aura.core.utils.ColorUtils.WHITE_POINT_D65_X
import com.kyant.aura.core.utils.ColorUtils.WHITE_POINT_D65_Y
import com.kyant.aura.core.utils.ColorUtils.WHITE_POINT_D65_Z
import com.kyant.aura.core.utils.ColorUtils.labF
import com.kyant.aura.core.utils.ColorUtils.linearized
import com.kyant.aura.core.utils.MathUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Design utilities using color temperature theory.
 *
 *
 * Analogous colors, complementary color, and cache to efficiently, lazily, generate data for
 * calculations when needed.
 */
class TemperatureCache
/**
 * Create a cache that allows calculation of ex. complementary and analogous colors.
 *
 * @param input Color to find complement/analogous colors of. Any colors will have the same tone,
 * and chroma as the input color, modulo any restrictions due to the other hues having lower
 * limits on chroma.
 */(private val input: Hct) {
    private val inputHue = input.hue.roundToInt()
    private val tempsByHue: DoubleArray
    private val coldestTemp: Double
    private val warmestTemp: Double
    private val tempRange: Double
    private val coldestHue: Int
    private val warmestHue: Int

    init {
        val tempsByHue = DoubleArray(361)
        val chroma = input.chroma
        val tone = input.tone
        var hue = 0
        var coldestTemp = Double.MAX_VALUE
        var coldestHue = inputHue
        var warmestTemp = Double.MIN_VALUE
        var warmestHue = inputHue
        while (hue <= 360) {
            val colorAtHue = HctSolver.solveToInt(hue.toDouble(), chroma, tone)
            val temp = rawTemperature(colorAtHue)
            tempsByHue[hue] = temp
            if (temp < coldestTemp) {
                coldestTemp = temp
                coldestHue = hue
            }
            if (temp > warmestTemp) {
                warmestTemp = temp
                warmestHue = hue
            }
            hue += 1
        }

        val inputTemp = rawTemperature(input.asArgb())
        tempsByHue[inputHue] = inputTemp
        if (inputTemp < coldestTemp) {
            coldestTemp = inputTemp
            coldestHue = inputHue
        }
        if (inputTemp > warmestTemp) {
            warmestTemp = inputTemp
            warmestHue = inputHue
        }
        this.tempsByHue = tempsByHue
        this.coldestTemp = coldestTemp
        this.warmestTemp = warmestTemp
        this.tempRange = warmestTemp - coldestTemp
        this.coldestHue = coldestHue
        this.warmestHue = warmestHue
    }

    /**
     * A color that complements the input color aesthetically.
     *
     *
     * In art, this is usually described as being across the color wheel. History of this shows
     * intent as a color that is just as cool-warm as the input color is warm-cool.
     */
    fun getComplement(): Hct {
        val startHueIsColdestToWarmest = isBetween(inputHue, coldestHue, warmestHue)
        val startHue = if (startHueIsColdestToWarmest) warmestHue else coldestHue
        val endHue = if (startHueIsColdestToWarmest) coldestHue else warmestHue
        var smallestError = 1000.0
        var answer = inputHue

        val complementRelativeTemp = 1.0 - getRelativeTemperature(inputHue)
        // Find the color in the other section, closest to the inverse percentile
        // of the input color. This is the complement.
        var hueAddend = 0
        while (hueAddend <= 360) {
            val hue = MathUtils.sanitizeDegreesInt(startHue + hueAddend)
            if (!isBetween(hue, startHue, endHue)) {
                hueAddend += 1
                continue
            }
            val relativeTemp = (tempsByHue[hue] - coldestTemp) / tempRange
            val error = abs(complementRelativeTemp - relativeTemp)
            if (error < smallestError) {
                smallestError = error
                answer = hue
            }
            hueAddend += 1
        }
        return input.copy(hue = answer.toDouble())
    }

    /**
     * 5 colors that pair well with the input color.
     *
     *
     * The colors are equidistant in temperature and adjacent in hue.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun getAnalogousColors(): List<Hct> = getAnalogousColors(5, 12)

    /**
     * A set of colors with differing hues, equidistant in temperature.
     *
     *
     * In art, this is usually described as a set of 5 colors on a color wheel divided into 12
     * sections. This method allows provision of either of those values.
     *
     *
     * Behavior is undefined when count or divisions is 0. When divisions < count, colors repeat.
     *
     * @param count     The number of colors to return, includes the input color.
     * @param divisions The number of divisions on the color wheel.
     */
    fun getAnalogousColors(count: Int, divisions: Int): List<Hct> {
        val hues = getAnalogousHues(count, divisions)
        return hues.map { input.copy(hue = it.toDouble()) }
    }

    fun getAnalogousColorAt(count: Int, divisions: Int, index: Int): Hct {
        val hues = getAnalogousHues(count, divisions)
        return input.copy(hue = hues[index].toDouble())
    }

    private fun getAnalogousHues(count: Int, divisions: Int): List<Int> {
        // The starting hue is the hue of the input color.
        val startHue = inputHue
        var lastTemp = getRelativeTemperature(startHue)

        val allColors: MutableList<Int> = ArrayList(divisions)
        allColors.add(startHue)

        var absoluteTotalTempDelta = 0.0
        for (i in 0..359) {
            val hue = MathUtils.sanitizeDegreesInt(startHue + i)
            val temp = getRelativeTemperature(hue)
            val tempDelta = abs(temp - lastTemp)
            lastTemp = temp
            absoluteTotalTempDelta += tempDelta
        }

        var hueAddend = 1
        val tempStep = absoluteTotalTempDelta / divisions.toDouble()
        var totalTempDelta = 0.0
        lastTemp = getRelativeTemperature(startHue)
        while (allColors.size < divisions) {
            val hue = MathUtils.sanitizeDegreesInt(startHue + hueAddend)
            val temp = getRelativeTemperature(hue)
            val tempDelta = abs(temp - lastTemp)
            totalTempDelta += tempDelta

            var desiredTotalTempDeltaForIndex = allColors.size * tempStep
            var indexSatisfied = totalTempDelta >= desiredTotalTempDeltaForIndex
            var indexAddend = 1
            // Keep adding this hue to the answers until its temperature is
            // insufficient. This ensures consistent behavior when there aren't
            // `divisions` discrete steps between 0 and 360 in hue with `tempStep`
            // delta in temperature between them.
            //
            // For example, white and black have no analogues: there are no other
            // colors at T100/T0. Therefore, they should just be added to the array
            // as answers.
            while (indexSatisfied && allColors.size < divisions) {
                allColors.add(hue)
                desiredTotalTempDeltaForIndex = (allColors.size + indexAddend) * tempStep
                indexSatisfied = totalTempDelta >= desiredTotalTempDeltaForIndex
                indexAddend++
            }
            lastTemp = temp
            hueAddend++

            if (hueAddend > 360) {
                while (allColors.size < divisions) {
                    allColors.add(hue)
                }
                break
            }
        }

        val answers: MutableList<Int> = ArrayList(count)
        answers.add(inputHue)

        val ccwCount = (count - 1) / 2
        for (i in 1..ccwCount) {
            var index = -i
            while (index < 0) {
                index = allColors.size + index
            }
            if (index >= allColors.size) {
                index = index % allColors.size
            }
            answers.add(0, allColors[index])
        }

        val cwCount = count - ccwCount - 1
        for (i in 1..cwCount) {
            var index = i
            if (index >= allColors.size) {
                index = index % allColors.size
            }
            answers.add(allColors[index])
        }

        return answers
    }

    /**
     * Temperature relative to all colors with the same chroma and tone.
     *
     * @param hue Hue to find the relative temperature of.
     * @return Value on a scale from 0 to 1.
     */
    fun getRelativeTemperature(hue: Int): Double {
        // Handle when there's no difference in temperature between warmest and
        // coldest: for example, at T100, only one color is available, white.
        if (tempRange == 0.0) {
            return 0.5
        }
        val differenceFromColdest = tempsByHue[hue] - coldestTemp
        return differenceFromColdest / tempRange
    }

    companion object {
        /**
         * Value representing cool-warm factor of a color. Values below 0 are considered cool, above,
         * warm.
         *
         *
         * Color science has researched emotion and harmony, which art uses to select colors. Warm-cool
         * is the foundation of analogous and complementary colors. See: - Li-Chen Ou's Chapter 19 in
         * Handbook of Color Psychology (2015). - Josef Albers' Interaction of Color chapters 19 and 21.
         *
         *
         * Implementation of Ou, Woodcock and Wright's algorithm, which uses Lab/LCH color space.
         * Return value has these properties:<br></br>
         * - Values below 0 are cool, above 0 are warm.<br></br>
         * - Lower bound: -9.66. Chroma is infinite. Assuming max of Lab chroma 130.<br></br>
         * - Upper bound: 8.61. Chroma is infinite. Assuming max of Lab chroma 130.
         */
        @JvmStatic
        fun rawTemperature(argb: Int): Double {
            // ============================================================================
            // Operations inlined from ColorUtils.labFromArgb to avoid repeated calculation
            // ============================================================================
            val linearR = linearized(argb shr 16 and 0xFF)
            val linearG = linearized(argb shr 8 and 0xFF)
            val linearB = linearized(argb and 0xFF)
            val x = SRGB_TO_XYZ_11 * linearR + SRGB_TO_XYZ_12 * linearG + SRGB_TO_XYZ_13 * linearB
            val y = SRGB_TO_XYZ_21 * linearR + SRGB_TO_XYZ_22 * linearG + SRGB_TO_XYZ_23 * linearB
            val z = SRGB_TO_XYZ_31 * linearR + SRGB_TO_XYZ_32 * linearG + SRGB_TO_XYZ_33 * linearB
            val xNormalized = x / WHITE_POINT_D65_X
            val yNormalized = y / WHITE_POINT_D65_Y
            val zNormalized = z / WHITE_POINT_D65_Z
            val fx = labF(xNormalized)
            val fy = labF(yNormalized)
            val fz = labF(zNormalized)
            val a = 500.0 * (fx - fy)
            val b = 200.0 * (fy - fz)
            // ============================================================================
            // Operations inlined from ColorUtils.labFromArgb to avoid repeated calculation
            // ============================================================================
            val hue = MathUtils.sanitizeDegreesDouble(Math.toDegrees(atan2(b, a)))
            val chroma = hypot(a, b)
            return -0.5 + 0.02 * chroma.pow(1.07) * cos(Math.toRadians(MathUtils.sanitizeDegreesDouble(hue - 50.0)))
        }

        /**
         * Determines if an angle is between two other angles, rotating clockwise.
         */
        @JvmStatic
        private fun isBetween(angle: Int, a: Int, b: Int): Boolean {
            return if (a < b) {
                a <= angle && angle <= b
            } else {
                a <= angle || angle <= b
            }
        }
    }
}
