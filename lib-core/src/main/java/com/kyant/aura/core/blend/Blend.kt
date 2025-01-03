/*
 * Copyright 2021 Google LLC
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
// This file is automatically generated. Do not modify it.
package com.kyant.aura.core.blend

import com.kyant.aura.core.hct.Cam16
import com.kyant.aura.core.hct.Cam16Ucs
import com.kyant.aura.core.hct.Hct
import com.kyant.aura.core.hct.HctSolver
import com.kyant.aura.core.utils.ColorUtils
import com.kyant.aura.core.utils.MathUtils
import kotlin.math.min

/**
 * Functions for blending in HCT and CAM16.
 */
object Blend {
    /**
     * Blend the design color's HCT hue towards the key color's HCT hue, in a way that leaves the
     * original color recognizable and recognizably shifted towards the key color.
     *
     * @param designColor ARGB representation of an arbitrary color.
     * @param sourceColor ARGB representation of the main theme color.
     * @return The design color with a hue shifted towards the system's color, a slightly
     * warmer/cooler variant of the design color's hue.
     */
    fun harmonize(designColor: Int, sourceColor: Int): Int {
        val fromHct = Hct(designColor)
        val toHue = Hct.hueOf(sourceColor)
        val differenceDegrees = MathUtils.differenceDegrees(fromHct.hue, toHue)
        val rotationDegrees = min(differenceDegrees * 0.5, 15.0)
        val outputHue =
            MathUtils.sanitizeDegreesDouble(
                fromHct.hue + rotationDegrees * MathUtils.rotationDirection(fromHct.hue, toHue)
            )
        return HctSolver.solveToInt(outputHue, fromHct.chroma, fromHct.tone)
    }

    /**
     * Blends hue from one color into another. The chroma and tone of the original color are
     * maintained.
     *
     * @param from   ARGB representation of color
     * @param to     ARGB representation of color
     * @param amount how much blending to perform; 0.0 >= and <= 1.0
     * @return from, with a hue blended towards to. Chroma and tone are constant.
     */
    fun hctHue(from: Int, to: Int, amount: Double): Int {
        val fromCam = Cam16.fromInt(from)
        val fromCamUcs = Cam16Ucs.fromCam16(fromCam)
        val toCamUcs = Cam16Ucs.fromInt(to)
        val fromJ = fromCamUcs.jstar
        val fromA = fromCamUcs.astar
        val fromB = fromCamUcs.bstar
        val toJ = toCamUcs.jstar
        val toA = toCamUcs.astar
        val toB = toCamUcs.bstar
        val jstar = fromJ + (toJ - fromJ) * amount
        val astar = fromA + (toA - fromA) * amount
        val bstar = fromB + (toB - fromB) * amount
        val ucs = Cam16Ucs.toInt(jstar, astar, bstar)

        val ucsCam = Cam16.fromInt(ucs)
        val blended = HctSolver.solveToInt(ucsCam.hue, fromCam.chroma, ColorUtils.lstarFromArgb(from))
        return blended
    }
}
