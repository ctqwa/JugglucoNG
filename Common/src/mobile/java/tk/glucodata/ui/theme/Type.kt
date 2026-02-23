@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package tk.glucodata.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontVariation
import tk.glucodata.R

// ============================================================================
//  COMPREHENSIVE FONT CONFIGURATION (PER-STYLE CONTROL)
//  Font: IBM Plex Sans Variable (Supports Cyrillic)
// ============================================================================

val MainFontFile = R.font.ibm_plex_sans_var

// --- HELPER VALUES ---
// WIDTHS (wdth):
// - 100f (Standard): Normal width.
// - 90f (Semi-Condensed): Sleek look.
// - 85f (Condensed): Minimum width for IBM Plex Sans. Best for Data.
const val WidthStandard = 100f
const val WidthSemiCondensed = 90f
const val WidthCondensed = 85f

// OPTICAL SIZE (opsz):
// IBM Plex Sans Variable does NOT support Optical Size.
// This axis has been removed.

// ============================================================================
//  GUIDELINES & EXAMPLES
// ============================================================================
/*
 *  CONFIGURATION: IBM PLEX SANS
 *  - Supports Cyrillic characters!
 *  - Width range: 85 (Narrow) to 100 (Wide).
 *  - Weight range: 100 (Thin) to 700 (Bold).
 *
 *  STYLE: SLEEK & EFFICIENT
 *  - Width: 90f for general text.
 *  - Weight: 400 (Regular) for most elements.
 */

// ----------------------------------------------------------------------------
//  1. DISPLAY STYLES (Huge numbers, Hero screens)
// ----------------------------------------------------------------------------

// Display Large (Main Glucose Value)
const val DisplayLarge_Weight = 500
const val DisplayLarge_Width  = WidthCondensed
const val DisplayLarge_Space  = -0.55

// Display Medium
const val DisplayMedium_Weight = 400
const val DisplayMedium_Width  = WidthStandard
const val DisplayMedium_Space  = 0.0

// Display Small
const val DisplaySmall_Weight = 400
const val DisplaySmall_Width  = WidthStandard
const val DisplaySmall_Space  = 0.0

// ----------------------------------------------------------------------------
//  2. HEADLINE STYLES (Section headers, Top App Bars)
// ----------------------------------------------------------------------------

// Headline Large
const val HeadlineLarge_Weight = 500
const val HeadlineLarge_Width  = WidthStandard
const val HeadlineLarge_Space  = 0.0

// Headline Medium
const val HeadlineMedium_Weight = 400
const val HeadlineMedium_Width  = WidthStandard
const val HeadlineMedium_Space  = 0.0

// Headline Small
const val HeadlineSmall_Weight = 500
const val HeadlineSmall_Width  = WidthStandard
const val HeadlineSmall_Space  = 0.0

// ----------------------------------------------------------------------------
//  3. TITLE STYLES (Card titles, Dialog titles, Lists)
// ----------------------------------------------------------------------------

// Title Large (Page Headers)
const val TitleLarge_Weight = 500
const val TitleLarge_Width  = WidthCondensed
const val TitleLarge_Space  = 0.25

// Title Medium (List Items, Settings)
const val TitleMedium_Weight = 400 // Regular (Sleek List Look)
const val TitleMedium_Width  = WidthStandard
const val TitleMedium_Space  = 0.15

// Title Small
const val TitleSmall_Weight = 500 // Medium (Distinction)
const val TitleSmall_Width  = WidthStandard
const val TitleSmall_Space  = 0.1

// ----------------------------------------------------------------------------
//  4. BODY STYLES (Paragraphs, Long text)
// ----------------------------------------------------------------------------

// Body Large
const val BodyLarge_Weight = 400
const val BodyLarge_Width  = WidthStandard
const val BodyLarge_Space  = 0.5

// Body Medium (Standard Settings Description)
const val BodyMedium_Weight = 400 // Regular (Restored from 300 to match user needs)
const val BodyMedium_Width  = WidthStandard
const val BodyMedium_Space  = 0.25

// Body Small
const val BodySmall_Weight = 400
const val BodySmall_Width  = WidthStandard
const val BodySmall_Space  = 0.4

// ----------------------------------------------------------------------------
//  5. LABEL STYLES (Buttons, Tags, Captions)
// ----------------------------------------------------------------------------

// Label Large (Buttons)
const val LabelLarge_Weight = 500
const val LabelLarge_Width  = WidthStandard
const val LabelLarge_Space  = 0.1

// Label Medium
const val LabelMedium_Weight = 500
const val LabelMedium_Width  = WidthStandard
const val LabelMedium_Space  = 0.5

// Label Small (Captions)
const val LabelSmall_Weight = 500
const val LabelSmall_Width  = WidthStandard
const val LabelSmall_Space  = 0.5


// ============================================================================
//  INTERNAL FACTORY
// ============================================================================

/**
 * Creates a distinct FontFamily for a specific style configuration.
 * Note: 'opsz' removed as IBM Plex Sans relies on standard scaling.
 */
private fun ibmPlexSans(weight: Int, width: Float): FontFamily {
    return try {
        FontFamily(
            Font(
                MainFontFile,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width)
                )
            )
        )
    } catch (th: Throwable) {
        android.util.Log.w("AppTypography", "Variable font fallback activated", th)
        FontFamily(Font(MainFontFile))
    }
}

// ============================================================================
//  TYPOGRAPHY DEFINITION
// ============================================================================

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ibmPlexSans(DisplayLarge_Weight, DisplayLarge_Width),
        fontWeight = FontWeight(DisplayLarge_Weight),
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = DisplayLarge_Space.sp
    ),
    displayMedium = TextStyle(
        fontFamily = ibmPlexSans(DisplayMedium_Weight, DisplayMedium_Width),
        fontWeight = FontWeight(DisplayMedium_Weight),
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = DisplayMedium_Space.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ibmPlexSans(DisplaySmall_Weight, DisplaySmall_Width),
        fontWeight = FontWeight(DisplaySmall_Weight),
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = DisplaySmall_Space.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ibmPlexSans(HeadlineLarge_Weight, HeadlineLarge_Width),
        fontWeight = FontWeight(HeadlineLarge_Weight),
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = HeadlineLarge_Space.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ibmPlexSans(HeadlineMedium_Weight, HeadlineMedium_Width),
        fontWeight = FontWeight(HeadlineMedium_Weight),
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = HeadlineMedium_Space.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ibmPlexSans(HeadlineSmall_Weight, HeadlineSmall_Width),
        fontWeight = FontWeight(HeadlineSmall_Weight),
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = HeadlineSmall_Space.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ibmPlexSans(TitleLarge_Weight, TitleLarge_Width),
        fontWeight = FontWeight(TitleLarge_Weight),
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = TitleLarge_Space.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ibmPlexSans(TitleMedium_Weight, TitleMedium_Width),
        fontWeight = FontWeight(TitleMedium_Weight),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = TitleMedium_Space.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ibmPlexSans(TitleSmall_Weight, TitleSmall_Width),
        fontWeight = FontWeight(TitleSmall_Weight),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = TitleSmall_Space.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ibmPlexSans(BodyLarge_Weight, BodyLarge_Width),
        fontWeight = FontWeight(BodyLarge_Weight),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = BodyLarge_Space.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ibmPlexSans(BodyMedium_Weight, BodyMedium_Width),
        fontWeight = FontWeight(BodyMedium_Weight),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = BodyMedium_Space.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ibmPlexSans(BodySmall_Weight, BodySmall_Width),
        fontWeight = FontWeight(BodySmall_Weight),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = BodySmall_Space.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ibmPlexSans(LabelLarge_Weight, LabelLarge_Width),
        fontWeight = FontWeight(LabelLarge_Weight),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = LabelLarge_Space.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ibmPlexSans(LabelMedium_Weight, LabelMedium_Width),
        fontWeight = FontWeight(LabelMedium_Weight),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = LabelMedium_Space.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ibmPlexSans(LabelSmall_Weight, LabelSmall_Width),
        fontWeight = FontWeight(LabelSmall_Weight),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = LabelSmall_Space.sp
    )
)

// 4. Emphasized Styles (Extensions for Expressive Look)

// Glucose Value: Uses the Display Large config but allows local overrides if needed
val Typography.displayLargeExpressive: TextStyle
    get() = displayLarge.copy(
        letterSpacing = (-0.5).sp
    )

// Status Indicators
val Typography.labelSmallPrim: TextStyle
    get() = labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )

// Settings Labels
val Typography.labelLargeExpressive: TextStyle
    get() = labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
