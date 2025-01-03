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
package com.kyant.aura.core.scheme

import com.kyant.aura.core.dislike.DislikeAnalyzer.fixIfDisliked
import com.kyant.aura.core.dynamiccolor.DynamicScheme
import com.kyant.aura.core.dynamiccolor.Variant
import com.kyant.aura.core.hct.Hct
import com.kyant.aura.core.palettes.TonalPalette
import com.kyant.aura.core.temperature.TemperatureCache
import kotlin.math.max

/**
 * A scheme that places the source color in Scheme.primaryContainer.
 *
 *
 * Primary Container is the source color, adjusted for color relativity. It maintains constant
 * appearance in light mode and dark mode. This adds ~5 tone in light mode, and subtracts ~5 tone in
 * dark mode.
 *
 *
 * Tertiary Container is an analogous color, specifically, the analog of a color wheel divided
 * into 6, and the precise analog is the one found by increasing hue. This is a scientifically
 * grounded equivalent to rotating hue clockwise by 60 degrees. It also maintains constant
 * appearance.
 */
class SchemeContent(sourceColorHct: Hct, isDark: Boolean, contrastLevel: Double) : DynamicScheme(
    sourceColorHct = sourceColorHct,
    variant = Variant.CONTENT,
    isDark = isDark,
    contrastLevel = contrastLevel,
    primaryPalette = TonalPalette.fromHueAndChroma(sourceColorHct.hue, sourceColorHct.chroma),
    secondaryPalette = TonalPalette.fromHueAndChroma(
        sourceColorHct.hue,
        max(sourceColorHct.chroma - 32.0, sourceColorHct.chroma * 0.5)
    ),
    tertiaryPalette = TonalPalette.fromHct(
        TemperatureCache(sourceColorHct)
            .getAnalogousColorAt(count = 3, divisions = 6, index = 2)
            .fixIfDisliked()
    ),
    neutralPalette = TonalPalette.fromHueAndChroma(sourceColorHct.hue, sourceColorHct.chroma / 8.0),
    neutralVariantPalette = TonalPalette.fromHueAndChroma(sourceColorHct.hue, (sourceColorHct.chroma / 8.0) + 4.0)
)
