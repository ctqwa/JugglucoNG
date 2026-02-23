package tk.glucodata.ui.overlay

import android.view.MotionEvent
import android.content.Intent
import android.graphics.Typeface // Added for Google Sans
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import tk.glucodata.service.FloatingGlucoseService.CutoutData
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import tk.glucodata.data.settings.FloatingSettingsRepository
import tk.glucodata.ui.theme.MainFontFile
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.components.TrendIndicator
import tk.glucodata.logic.TrendEngine
import tk.glucodata.data.calibration.CalibrationManager
import tk.glucodata.ui.getDisplayValues
import tk.glucodata.Natives

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun FloatingGlucoseOverlay(
    repository: FloatingSettingsRepository,
    historyFlow: Flow<List<GlucosePoint>>,
    onUpdatePosition: (Int, Int) -> Unit,
    cutoutDataFlow: Flow<tk.glucodata.service.FloatingGlucoseService.CutoutData>,
    statusBarHeightFlow: Flow<androidx.compose.ui.unit.Dp>
) {
    val context = LocalContext.current

    // Settings State
    val isTransparent by repository.isTransparent.collectAsState(initial = false)
    val showSecondary by repository.showSecondary.collectAsState(initial = false)
    val fontSource by repository.fontSource.collectAsState(initial = "APP")
    val fontSize by repository.fontSize.collectAsState(initial = 16f)
    val fontWeightSetting by repository.fontWeight.collectAsState(initial = "REGULAR")
    val showArrow by repository.showArrow.collectAsState(initial = true)
    val cornerRadius by repository.cornerRadius.collectAsState(initial = 28f)
    val opacity by repository.backgroundOpacity.collectAsState(initial = 0.6f)
    val isDynamicIsland by repository.isDynamicIslandEnabled.collectAsState(initial = false)
    val verticalOffset by repository.islandVerticalOffset.collectAsState(initial = 0f)
    val manualGap by repository.islandGap.collectAsState(initial = 0f)

    // Metrics State (from Service WindowInsets)
    val cutoutData by cutoutDataFlow.collectAsState(initial = tk.glucodata.service.FloatingGlucoseService.CutoutData(0.dp, 0.dp))
    val cutoutWidth = cutoutData.width
    val cutoutBottom = cutoutData.bottom
    val statusBarHeight by statusBarHeightFlow.collectAsState(initial = 0.dp)

    // Data State: History List
    val history by historyFlow.collectAsState(initial = emptyList())
    
    // Derived Data
    val glucosePoint = remember(history) { history.lastOrNull() }
    
    // View Mode & Calibration
    val viewData = remember(glucosePoint) {
        val sName = Natives.lastsensorname()
        if (!sName.isNullOrEmpty()) {
            val ptr = Natives.getdataptr(sName)
            if (ptr != 0L) {
                Pair(Natives.getViewMode(ptr), Natives.getunit())
            } else Pair(0, Natives.getunit())
        } else Pair(0, Natives.getunit())
    }
    val viewMode = viewData.first
    val unitInt = viewData.second
    
    val trendResult = remember(history) {
        if (history.isNotEmpty()) {
            TrendEngine.calculateTrend(history, isMmol = (unitInt == 1))
        } else {
            TrendEngine.TrendResult(TrendEngine.TrendState.Unknown, 0f, 0f, 0f, 0f)
        }
    }
    
    // Gap Calculation
    // Use manual gap if > 0, otherwise detected width. Default 70dp if all else fails.
    val finalGap = if (manualGap > 0f) manualGap.dp else if (cutoutWidth > 0.dp) cutoutWidth else 70.dp 
    // Height: Ensure we cover at least the Cutout Bottom or Status Bar height, whichever is larger
    val detectedHeight = if (cutoutBottom > 0.dp) cutoutBottom else if (statusBarHeight > 0.dp) statusBarHeight else 0.dp
    val finalHeight = detectedHeight
    
    // Styles
    val finalBgColor = if (isTransparent) Color.Transparent else Color.Black.copy(alpha = opacity)
    val finalShape = RoundedCornerShape(cornerRadius.dp)
    val finalTextColor = Color.White
    
    // Drag Modifier
    val dragModifier = if (isDynamicIsland) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = { }) { change, dragAmount ->
            change.consume()
            onUpdatePosition(dragAmount.x.toInt(), dragAmount.y.toInt())
        }
    }

    // Font Logic
    val weightVal = when (fontWeightSetting) {
        "LIGHT" -> 300
        "MEDIUM" -> 500
        else -> 400
    }
    
    val fontFamily = if (fontSource == "APP") {
        try {
            FontFamily(
                Font(
                    MainFontFile,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weightVal))
                )
            )
        } catch (th: Throwable) {
            android.util.Log.w("FloatingGlucoseOverlay", "Variable font fallback activated", th)
            FontFamily(Font(MainFontFile))
        }
    } else {
        remember(weightVal) {
             val familyName = if (weightVal >= 500) "google-sans-medium" else "google-sans"
             try {
                 FontFamily(Typeface.create(familyName, Typeface.NORMAL))
             } catch (e: Exception) { FontFamily.SansSerif }
        }
    }
    val fontWeight = FontWeight(weightVal)

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    
    // ROOT LAYOUT CHANGE: Use Column just for Vertical Offset Spacer if Island
    // We don't use 'Surface' as root for Island anymore, because we want split layout.
    
    // ROOT LAYOUT: Column for vertical offset spacer
    if (isDynamicIsland) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .then(dragModifier)
                .clickable { launchIntent?.let { context.startActivity(it) } }
        ) {
             if (verticalOffset > 0f) {
                 Spacer(modifier = Modifier.height(verticalOffset.dp))
             }
             
             // ISLAND LAYOUT - Unified Pill, Asymmetrically Centered
             // We use custom layout to report a Symmetric Size (for Window alignment)
             // but draw the Background Pill tightly around asymmetric content.
             
             AsymmetricCenteringRow(
                 gap = finalGap,
                 verticalAlignment = Alignment.CenterVertically,
                 backgroundContent = {
                     Surface(
                         color = finalBgColor,
                         shape = finalShape,
                         modifier = Modifier.fillMaxSize()
                     ) {}
                 }
             ) {
                 // LEFT: Values
                 Box(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                     if (glucosePoint != null) {
                            val point = glucosePoint!!
                            val unit = if (unitInt == 1) "mmol/L" else "mg/dL"
                            val isRawModeForCal = viewMode == 1 || viewMode == 3
                            val hasCalibration = CalibrationManager.hasActiveCalibration(isRawModeForCal)
                            val calibratedValue = if (hasCalibration) {
                                 val baseValue = if (isRawModeForCal) point.rawValue else point.value
                                 if (baseValue.isFinite() && baseValue > 0.1f) {
                                     CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeForCal)
                                 } else {
                                     null
                                 }
                            } else null
                            val dvs = getDisplayValues(point, viewMode, unit, calibratedValue)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dvs.primaryStr, color = finalTextColor, fontSize = fontSize.sp, fontFamily = fontFamily, fontWeight = fontWeight, textAlign = TextAlign.End)
                                if (showSecondary && !dvs.secondaryStr.isNullOrEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(dvs.secondaryStr!!, color = finalTextColor.copy(alpha=0.7f), fontSize = (fontSize*0.7f).sp, fontFamily = fontFamily, fontWeight = fontWeight)
                                }
                            }
                     } else {
                         Text("---", color = finalTextColor, fontSize = fontSize.sp)
                     }
                 }
                
                // RIGHT: Arrow
                Box(modifier = Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                     if (showArrow && glucosePoint != null) {
                        TrendIndicator(
                            trendResult = trendResult,
                            modifier = Modifier.size((fontSize * 0.85f).dp),
                            color = finalTextColor
                        )
                    } else {
                         Spacer(Modifier.size(1.dp)) 
                    }
                }
             }
        }
    } else {
        // ORIGINAL FLOATING LAYOUT (Unified Pill)
        Surface(
            color = finalBgColor,
            shape = finalShape,
            modifier = Modifier
                .wrapContentSize()
                .then(dragModifier)
                .clickable { launchIntent?.let { context.startActivity(it) } }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 if (glucosePoint != null) {
                    val point = glucosePoint!!
                    val unit = if (unitInt == 1) "mmol/L" else "mg/dL"
                    val isRawModeForCal = viewMode == 1 || viewMode == 3
                    val hasCalibration = CalibrationManager.hasActiveCalibration(isRawModeForCal)
                    val calibratedValue = if (hasCalibration) {
                         val baseValue = if (isRawModeForCal) point.rawValue else point.value
                         if (baseValue.isFinite() && baseValue > 0.1f) {
                             CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeForCal)
                         } else {
                             null
                         }
                    } else null
                    val dvs = getDisplayValues(point, viewMode, unit, calibratedValue)

                    if (isTransparent) {
                        OutlinedText(dvs.primaryStr, fontSize, fontFamily, fontWeight, finalTextColor, Color.Black.copy(alpha=0.5f))
                    } else {
                        Text(dvs.primaryStr, color = finalTextColor, fontSize = fontSize.sp, fontFamily = fontFamily, fontWeight = fontWeight)
                    }
                    
                    val secondaryText = if (showSecondary) dvs.secondaryStr ?: "" else dvs.secondaryStr
                    if (!secondaryText.isNullOrEmpty()) {
                        if (isTransparent) {
                            OutlinedText(secondaryText, fontSize*0.7f, fontFamily, fontWeight, finalTextColor.copy(alpha=0.7f), Color.Black.copy(alpha=0.5f))
                        } else {
                            Text(secondaryText, color = finalTextColor.copy(alpha=0.7f), fontSize = (fontSize*0.7f).sp, fontFamily = fontFamily, fontWeight = fontWeight)
                        }
                    }

                    if (showArrow) {
                        TrendIndicator(trendResult, Modifier.size((fontSize * 0.85f).dp), finalTextColor)
                    }
                 } else {
                     Text("---", color = finalTextColor, fontSize = fontSize.sp, fontFamily = fontFamily, fontWeight = fontWeight)
                 }
            }
        }
    }
}

@Composable
fun OutlinedText(
    text: String, 
    fontSize: Float, 
    fontFamily: FontFamily, 
    fontWeight: FontWeight, 
    textColor: Color, 
    outlineColor: Color
) {
    Box {
        // Outline (Stroke)
        Text(
            text = text,
            color = outlineColor,
            fontSize = fontSize.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            style = TextStyle.Default.copy(
                drawStyle = Stroke(
                    miter = 10f,
                    width = 5f,
                    join = StrokeJoin.Round
                )
            )
        )
        // Fill (Main Text)
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.sp,
            fontFamily = fontFamily,
        )
    }
}



@Composable
fun AsymmetricCenteringRow(
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp,
    verticalAlignment: Alignment.Vertical,
    backgroundContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // Layout that takes:
    // 1. Background (as a composable)
    // 2. Left and Right content
    // Calculations:
    // - Measure L, R.
    // - SymmetricWidth = 2 * max(L, R) + Gap.
    // - ActualContentWidth = L + Gap + R.
    // - OffsetX = if (L < R) (SymmetricWidth - ActualContentWidth) / 2 else 0? 
    //   Wait. Layout places children.
    //   If L=50, R=100, Gap=10.
    //   Max=100. SymW = 210.
    //   Center of SymW = 105.
    //   Gap must start at Center - Gap/2 = 100.
    //   So Left must end at 100. Left Start = 100 - 50 = 50.
    //   Gap ends at 110. Right starts at 110. Right ends at 210.
    //   
    //   Offset Calculation:
    //   LeftX = max(L, R) - L.
    //   RightX = max(L, R) + Gap.
    //   BgX = LeftX. BgWidth = L + Gap + R.
    
    androidx.compose.ui.layout.Layout(
        contents = listOf(backgroundContent, content),
        modifier = modifier
    ) { (bgMeasurables, contentMeasurables), constraints ->
        
        // Measure Content first
        val contentPlaceables = contentMeasurables.take(2).map { it.measure(constraints.copy(minWidth = 0)) }
        val left = contentPlaceables.getOrNull(0)
        val right = contentPlaceables.getOrNull(1)
        
        val leftW = left?.width ?: 0
        val rightW = right?.width ?: 0
        val leftH = left?.height ?: 0
        val rightH = right?.height ?: 0
        
        val gapPx = gap.roundToPx()
        val maxSideWidth = maxOf(leftW, rightW)
        
        // Report Symmetric Size
        val totalWidth = maxSideWidth * 2 + gapPx
        val totalHeight = maxOf(leftH, rightH)
        
        // Measure Background to fit TIGHTLY around content (L + G + R)
        val bgWidth = leftW + gapPx + rightW
        val bgPlaceable = bgMeasurables.firstOrNull()?.measure(
            androidx.compose.ui.unit.Constraints.fixed(bgWidth, totalHeight)
        )
        
        fun getY(height: Int): Int {
             return when (verticalAlignment) {
                 Alignment.CenterVertically -> (totalHeight - height) / 2
                 Alignment.Bottom -> totalHeight - height
                 else -> 0
             }
        }
        
        layout(totalWidth, totalHeight) {
            val startX = maxSideWidth - leftW
            
            // Place Background
            bgPlaceable?.place(x = startX, y = 0)
            
            // Place Left
            left?.place(x = startX, y = getY(leftH))
            
            // Place Right
            right?.place(x = maxSideWidth + gapPx, y = getY(rightH))
        }
    }
}
