package tk.glucodata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import tk.glucodata.ui.theme.displayLargeExpressive
import tk.glucodata.ui.theme.labelSmallPrim
import tk.glucodata.ui.theme.labelLargeExpressive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.theme.displayLargeExpressive
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.rotate as modifierRotate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

@Composable
fun DashboardCombinedHeader(

    currentGlucose: String,
    currentRate: Float,
    viewMode: Int,
    latestPoint: GlucosePoint?,
    sensorName: String,
    daysRemaining: String,
    sensorStatus: String,
    activeSensors: List<String> = emptyList(),
    sensorProgress: Float = 0f,
    sensorHoursRemaining: Long = 999L,
    currentDay: Int = 0,
    history: List<GlucosePoint> = emptyList(),
    calibratedValue: Float? = null,
    isMmol: Boolean,
    onHeroClick: () -> Unit = {}
) {
    // Determine Colors based on logic
    // Glucose: Error if low/high, else Primary Container (Tonal)
    val glucoseContainerColor = MaterialTheme.colorScheme.primaryContainer
    val glucoseContentColor = MaterialTheme.colorScheme.onPrimaryContainer

    // Sensor: Surface Variant (Tonal) vs Tertiary Container (Expiring)
    // FIX: Replaced fragile string matching (failed in Dutch, false positive in English for "1 days in use")
    // with robust progress-based logic. >90% progress (approx <1.4 days left) triggers Expiring state.
    val isExpiring = sensorProgress > 0.90f
    // FIX: User reported "Silly" contrast in Dark Mode with surfaceVariant (too bright/green).
    // Switched to primaryContainer to match the Glucose Card (Left) for a cohesive dark look.
    val sensorContainerColor = if (isExpiring) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) // Darker variant
    val sensorContentColor = if (isExpiring) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    // Advanced Trend
    val trendResult = remember(history, latestPoint) {
        if (history.isNotEmpty()) {
             // Map Kotlin UI points to Native Java points for shared TrendEngine
             val nativeList = history.map { tk.glucodata.GlucosePoint(it.timestamp, it.value, it.rawValue) }
             tk.glucodata.logic.TrendEngine.calculateTrend(nativeList, useRaw = (viewMode == 1 || viewMode == 3), isMmol = isMmol)
        } else if (latestPoint != null) {
            // Fallback
             val nativeList = listOf(tk.glucodata.GlucosePoint(latestPoint.timestamp, latestPoint.value, latestPoint.rawValue))
             tk.glucodata.logic.TrendEngine.calculateTrend(nativeList, useRaw = (viewMode == 1 || viewMode == 3), isMmol = isMmol)
        } else {
            tk.glucodata.logic.TrendEngine.TrendResult(tk.glucodata.logic.TrendEngine.TrendState.Unknown, 0f, 0f, 0f, 0f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min), // Equal height
        horizontalArrangement = Arrangement.spacedBy(4.dp) // 12dp Gap
    ) {
        // --- LEFT CARD: GLUCOSE (2/3) ---
        // HERO CARD: M3 Expressive Adaptive Layout

        // 1. Resolve Values using shared logic (with calibration if active)
        val dvs = remember(latestPoint, viewMode, currentGlucose, calibratedValue) {
            if (latestPoint != null) {
                getDisplayValues(latestPoint, viewMode, "", calibratedValue)
            } else {
                null
            }
        }

        val primaryText = dvs?.primaryStr ?: currentGlucose
        val secondaryText = dvs?.secondaryStr
        val tertiaryText = dvs?.tertiaryStr

        // 3. Adaptive Layout Params
        val startPadding = 28.dp

        Card(
            onClick = onHeroClick,
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = glucoseContainerColor,
                contentColor = glucoseContentColor
            ),
            shape = RoundedCornerShape(topStart = 48.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = startPadding, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Primary Value
                AnimatedContent(
                    targetState = primaryText,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeIn(tween(200)))
                            .togetherWith(slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 2 } + fadeOut(tween(100)))
                    },
                    label = "GlucoseHeroAnimation"
                ) { glucoseValue ->
                    Text(
                        text = glucoseValue,
                        style = MaterialTheme.typography.displayLargeExpressive,
                        color = glucoseContentColor
                    )
                }

                // Secondary Value (inline)
                if (secondaryText != null) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.displaySmall,
                        color = glucoseContentColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
                        color = glucoseContentColor.copy(alpha = 0.7f)
                    )
                }

                // Tertiary Value (smaller, appended)
                if (tertiaryText != null) {
                    Text(
                        text = " · $tertiaryText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glucoseContentColor.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Trend Icon
                tk.glucodata.ui.components.TrendIndicator(
                    trendResult = trendResult,
                    modifier = Modifier.size(40.dp),
                    color = glucoseContentColor
                )
            }
        }

        // --- RIGHT CARD: SENSOR (1/3) ---
        Card(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight(),
             colors = CardDefaults.cardColors(
                containerColor = sensorContainerColor,
                contentColor = sensorContentColor
            ),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 48.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Dynamic Fill Layer
                if (sensorProgress > 0f) {
                    val fillColor = when {
                        sensorProgress > 0.95f -> MaterialTheme.colorScheme.error
                        sensorProgress > 0.80f -> MaterialTheme.colorScheme.tertiary
                        else -> androidx.compose.ui.graphics.Color(0xFF66BB6A) // Muted Green (400) - "Make it Green"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(sensorProgress)
                            .background(fillColor.copy(alpha = 0.25f)) // Slightly increased opacity for visibility
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp), // Matched Vertical Padding (12dp)
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    // 0. Signal Quality Indicator (above sensor name)
                    if (trendResult.noiseLevel > 0f) {
                        SignalQualityIndicator(
                            noiseLevel = trendResult.noiseLevel,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    
                    // 1. Sensor Name (Top Label)
                    if (sensorName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sensorName,
                                style = MaterialTheme.typography.labelMedium, // M3 Standard
                                color = sensorContentColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false) // Allow shrinking for dots
                            )
                            
                            // Active Indicators (Dots) - Right of Name
                            // Hidden if only 1 active sensor
                            if (activeSensors.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                activeSensors.forEach { serial ->
                                     Box(
                                         modifier = Modifier
                                             .size(6.dp)
                                             .background(tk.glucodata.ui.viewmodel.SensorColors.getColor(serial), androidx.compose.foundation.shape.CircleShape)
                                     )
                                     Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }


                    // 2. Main Status / Days (Hero Value)
                    // Priority: Status if critical, else Days "1 / 14"
                    val mainText = if (sensorStatus.isNotEmpty() && sensorStatus != "Ready") sensorStatus 
                                   else {
                                       if (sensorHoursRemaining <= 24) "$sensorHoursRemaining" + "h"
                                       else daysRemaining
                                   }
                    if (mainText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             // Text First
                             Text(
                                text = mainText,
                                style = MaterialTheme.typography.labelLarge,
                                color = sensorContentColor,
                                maxLines = 1
                            )

                             // Icon: Always show for Main sensor, regardless of count
                             Spacer(modifier = Modifier.width(8.dp))
                             
                             if (sensorHoursRemaining <= 24) {
                                  // Urgent: Hourglass with Dynamic Speed
                                  val duration = (500 + (1500 * (sensorHoursRemaining / 24f))).toInt().coerceAtLeast(500)
                                  AnimatedHourglassIcon(
                                      color = sensorContentColor.copy(alpha = 0.8f),
                                      modifier = Modifier.size(14.dp),
                                      cycleDuration = duration
                                  )
                             } else {
                                  // Normal: Custom Calendar with Page Flip
                                  CustomCalendarIcon(
                                      color = sensorContentColor.copy(alpha = 0.6f),
                                      modifier = Modifier.size(14.dp),
                                      currentDay = currentDay
                                  )
                             }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomCalendarIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    currentDay: Int = 0
) {
    // 1. Subtle Idle "Breathing" Flip (0 -> -6 -> 0)
    val infiniteTransition = rememberInfiniteTransition(label = "CalendarFlip")
    val subtleFlip by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 8000 // Occasional flip
                0f at 0
                -8f at 200 using androidx.compose.animation.core.EaseOut // Flip up
                0f at 500 using androidx.compose.animation.core.EaseIn   // Settle
                0f at 8000
            }
        ),
        label = "CalendarPageFlip"
    )

    // 2. Prominent "Day Change" Flip (Triggered by currentDay)
    // 0 -> -25 -> 0 (Big Nod)
    val prominentFlip = remember { androidx.compose.animation.core.Animatable(0f) }
    
    androidx.compose.runtime.LaunchedEffect(currentDay) {
        if (currentDay > 0) { // Don't animate on initial 0
             prominentFlip.animateTo(
                 targetValue = -25f,
                 animationSpec = tween(300, easing = androidx.compose.animation.core.EaseOutBack)
             )
             prominentFlip.animateTo(
                 targetValue = 0f,
                 animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
             )
        }
    }

    val totalRotation = subtleFlip + prominentFlip.value

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Refine stroke: 1.5dp might be too thick for 13dp size. Let's try 1.25dp visual weight equivalence.
        // Actually, user said minimalist style. 1.2dp is fine.
        
        // 1. Body
        val cornerRadius = 2.dp.toPx()
        
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.25f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.75f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )
        
        // "Substance in the middle": Draw 2 small horizontal lines (rows)
        // Positioned in the center of the body area.
        val bodyTop = h * 0.25f
        val bodyHeight = h * 0.75f
        val row1Y = bodyTop + (bodyHeight * 0.4f)
        val row2Y = bodyTop + (bodyHeight * 0.7f)
        val margin = w * 0.25f
        val lineWidth = w * 0.5f // Centered line
        
        drawLine(
            color = color.copy(alpha = 0.5f), // Fainter lines
            start = androidx.compose.ui.geometry.Offset(margin, row1Y),
            end = androidx.compose.ui.geometry.Offset(margin + lineWidth, row1Y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
             color = color.copy(alpha = 0.5f),
             start = androidx.compose.ui.geometry.Offset(margin, row2Y),
             end = androidx.compose.ui.geometry.Offset(margin + lineWidth, row2Y),
             strokeWidth = 1.dp.toPx()
        )

        // 2. Header (Top Binding Bar) with "Page Flip" Rotation
        rotate(degrees = totalRotation, pivot = androidx.compose.ui.geometry.Offset(w / 2, 0f)) {
            // Filled Header
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.25f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            
            // Rings (Cutouts)
            val ringX1 = w * 0.25f
            val ringX2 = w * 0.75f
            val ringH = h * 0.3f
            
            drawLine(
                color = androidx.compose.ui.graphics.Color.Transparent,
                start = androidx.compose.ui.geometry.Offset(ringX1, 0f),
                end = androidx.compose.ui.geometry.Offset(ringX1, ringH),
                strokeWidth = 1.dp.toPx(),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear 
            )
             drawLine(
                color = androidx.compose.ui.graphics.Color.Transparent, 
                start = androidx.compose.ui.geometry.Offset(ringX2, 0f),
                end = androidx.compose.ui.geometry.Offset(ringX2, ringH),
                strokeWidth = 1.dp.toPx(),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )
        }
    }
}

@Composable
private fun AnimatedHourglassIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    cycleDuration: Int = 10000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HourglassFlip")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = cycleDuration
                0f at 0
                180f at (cycleDuration * 0.4f).toInt() using androidx.compose.animation.core.EaseInOut
                180f at cycleDuration
            }
        ),
        label = "HourglassRotation"
    )

    Icon(
        imageVector = Icons.Rounded.HourglassEmpty,
        contentDescription = null,
        tint = color,
        modifier = modifier.modifierRotate(rotation)
    )
}




@Composable
fun RecentReadingsCard(
    recentReadings: List<GlucosePoint>,
    unit: String,
    viewMode: Int,
    content: @Composable (Int, GlucosePoint) -> Unit // Rendering the row with Index
) {
    if (recentReadings.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            // User Request: "readings row card 2 bottom edges 4dp radius"
            // Top: 16dp (Standard), Bottom: 4dp (Continuity)
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            // User Request: "kill it" (shadows)
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                recentReadings.forEachIndexed { index, item ->
                    key(item) {
                        AnimatedVisibility(
                            visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn()
                        ) {
                            content(index, item)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipeable row using Material 3 SwipeToDismissBox
 * - Swipe LEFT to DELETE (red background, trash icon)
 * - Swipe RIGHT to DISABLE/ENABLE
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDeleteRow(
    onDelete: () -> Unit,
    onDisable: () -> Unit,
    isDisabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Key on isDisabled so state resets when toggled
    val dismissState = key(isDisabled) {
        androidx.compose.material3.rememberSwipeToDismissBoxState(
            positionalThreshold = { it * 0.25f }, // 25% threshold for easier swipe
            confirmValueChange = { dismissValue ->
                when (dismissValue) {
                    androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
                        true
                    }
                    androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> {
                        onDisable()
                        false
                    }
                    androidx.compose.material3.SwipeToDismissBoxValue.Settled -> false
                }
            }
        )
    }
    
    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> if (isDisabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val alignment = when (direction) {
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            val icon = when (direction) {
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> if (isDisabled) Icons.Filled.Check else Icons.Filled.Close
                else -> Icons.Filled.Delete
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        content = {
            Box(modifier = Modifier.clickable(onClick = onClick)) {
                content()
            }
        }
    )
}

/**
 * Calibrations Card - Displays calibration list on dashboard with enable toggle and Calibrate button.
 * Only shown when calibrations exist. M3 Compliant: tap-to-edit, swipe-to-delete with undo.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CalibrationsCard(
    viewMode: Int,
    isMmol: Boolean,
    onAddCalibration: () -> Unit,
    onEditCalibration: (tk.glucodata.data.calibration.CalibrationEntity) -> Unit,
    onViewHistory: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null
) {
    val isRawMode = viewMode == 1 || viewMode == 3
    
    // Collect calibrations and enable state
    val allCalibrations by tk.glucodata.data.calibration.CalibrationManager.getCalibrationsFlow()?.collectAsState(initial = tk.glucodata.data.calibration.CalibrationManager.getCachedCalibrations())
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(tk.glucodata.data.calibration.CalibrationManager.getCachedCalibrations()) }
    val calibrations = allCalibrations.filter { it.isRawMode == isRawMode }
    
    val isEnabledForRaw by tk.glucodata.data.calibration.CalibrationManager.isEnabledForRaw.collectAsState()
    val isEnabledForAuto by tk.glucodata.data.calibration.CalibrationManager.isEnabledForAuto.collectAsState()
    val isCalibrationEnabled = if (isRawMode) isEnabledForRaw else isEnabledForAuto
    
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dateFormatter = androidx.compose.runtime.remember { java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()) }
    val format = if (isMmol) "%.1f" else "%.0f"
    
    // Pending delete for undo
    var pendingDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<tk.glucodata.data.calibration.CalibrationEntity?>(null) }
    var showClearConfirmation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // Local disabled state (until DB field added) - tracks disabled calibration IDs
    // Track items being deleted for animation
    val animatingOutIds = remember { mutableStateListOf<Int>() }
    // Track items being restored for animation
    val animatingInIds = remember { mutableStateListOf<Int>() }
    
    // EMPTY STATE: No card shown at all - just the button floating (Full Width, Tonal)
    if (calibrations.isEmpty()) {
        androidx.compose.material3.FilledTonalButton(
            onClick = onAddCalibration,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
//            elevation = androidx.compose.material3.ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Calibrate",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }
    
    // POPULATED STATE: Card with button at top, switch, list
    // Sensor card styling: primaryContainer with transparency
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Dual-Action Header (Matching CalibrationListScreen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 // Left: Clear All (Visible only if items exist)
                 if (calibrations.isNotEmpty()) {
                     androidx.compose.material3.FilledTonalButton(
                        onClick = { showClearConfirmation = true },
//                        modifier = Modifier
//                            .weight(1f),
//                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 4.dp,
                            bottomEnd = 4.dp
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear")
                    }
                 } else {
//                     Spacer(modifier = Modifier.weight(8.dp))
                 }

                 // Right: Calibrate
                 androidx.compose.material3.Button(
                    onClick = onAddCalibration,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(
                        topStart = 4.dp, 
                        bottomStart = 4.dp,
                        topEnd = 12.dp, 
                        bottomEnd = 12.dp
                    ),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
//                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calibrate",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            // Compact Enable Switch Row - clickable entire row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        tk.glucodata.data.calibration.CalibrationManager.setEnabledForMode(isRawMode, !isCalibrationEnabled) 
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable Calibration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Adjust sensor readings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                androidx.compose.material3.Switch(
                    checked = isCalibrationEnabled,
                    onCheckedChange = { enabled ->
                        tk.glucodata.data.calibration.CalibrationManager.setEnabledForMode(isRawMode, enabled)
                    },
                    thumbContent = if (isCalibrationEnabled) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize),
                            )
                        }
                    } else null
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            // Calibration List (Gmail-style swipe-to-delete)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (isCalibrationEnabled) 1f else 0.38f }
                    .animateContentSize() // Animate height changes (e.g. deletion)
            ) {
                // Limit to 8 items for dashboard
                val visibleCalibrations = calibrations.take(8)
                
                visibleCalibrations.forEachIndexed { index: Int, cal: tk.glucodata.data.calibration.CalibrationEntity ->
                    key(cal.id) {
                        val visibleState = remember {
                            MutableTransitionState(!animatingInIds.contains(cal.id))
                        }
                        visibleState.targetState = cal.id !in animatingOutIds

                        AnimatedVisibility(
                            visibleState = visibleState,
                            exit = shrinkVertically() + fadeOut(),
                            enter = expandVertically() + fadeIn()
                        ) {
                            val sensorValue = if (isRawMode) cal.sensorValueRaw else cal.sensorValue
                        
                        Column {
                            // Track if this calibration is disabled (Persistent)
                            val isRowDisabled = !cal.isEnabled
                            
                            // Gmail-style swipeable row
                            SwipeableDeleteRow(
                                 onDelete = {
                                     // Trigger animation first
                                     animatingOutIds.add(cal.id)
                                     
                                     scope.launch {
                                         delay(300) // Wait for animation
                                         tk.glucodata.data.calibration.CalibrationManager.deleteCalibration(cal)
                                         animatingOutIds.removeAll { it == cal.id } // Cleanup
                                         
                                         // Show Undo Snackbar
                                         val result = snackbarHostState?.showSnackbar(
                                             message = "Calibration deleted",
                                             actionLabel = "Undo",
                                             duration = androidx.compose.material3.SnackbarDuration.Short
                                         )
                                         
                                         // Handle Undo
                                         if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                             animatingInIds.add(cal.id)
                                             tk.glucodata.data.calibration.CalibrationManager.restoreCalibration(cal)
                                         }
                                     }
                                 },
                                onDisable = {
                                    // Persistent Update: Toggle enabled state in DB
                                    scope.launch {
                                        tk.glucodata.data.calibration.CalibrationManager.updateCalibration(cal.copy(isEnabled = !cal.isEnabled))
                                    }
                                },
                                isDisabled = isRowDisabled,
                                onClick = { onEditCalibration(cal) }
                            ) {
                                // Row content with disabled visual state
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp) // Enforce M3 Touch Target
                                        .background(
                                            if (isRowDisabled) 
                                                MaterialTheme.colorScheme.surfaceVariant
                                            else 
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormatter.format(java.util.Date(cal.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isRowDisabled) 
                                            MaterialTheme.colorScheme.outlineVariant
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(java.util.Locale.getDefault(), format, sensorValue)} → ${String.format(java.util.Locale.getDefault(), format, cal.userValue)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isRowDisabled)
                                            MaterialTheme.colorScheme.outlineVariant
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            
                            if (index < visibleCalibrations.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
                }
                }

                // Footer: If more than 8 items, show "Previous calibrations" button
                if (calibrations.size > 8) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onViewHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Previous calibrations")
                    }
                }
            }
        }
    
    // Clear Options Bottom Sheet
    if (showClearConfirmation) {
        DashboardClearOptionsBottomSheet(
            onDismiss = { showClearConfirmation = false },
            onClearDisabled = {
                scope.launch {
                    val disabled = calibrations.filter { !it.isEnabled }
                    disabled.forEach { tk.glucodata.data.calibration.CalibrationManager.deleteCalibration(it) }
                    snackbarHostState?.showSnackbar(
                        message = "${disabled.size} disabled calibrations cleared",
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                }
                showClearConfirmation = false
            },
            onClearAll = {
                scope.launch {
                    val backup = calibrations
                    tk.glucodata.data.calibration.CalibrationManager.clearAll()
                    
                    val result = snackbarHostState?.showSnackbar(
                        message = "All calibrations cleared",
                        actionLabel = "Undo",
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                    
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        tk.glucodata.data.calibration.CalibrationManager.restoreAll(backup)
                    }
                }
                showClearConfirmation = false
            },
            disabledCount = calibrations.count { !it.isEnabled },
            totalCount = calibrations.size
        )
    }
}



/**
 * Animated Signal Quality Indicator using xDrip-style noise (Poly Fit Error Variance).
 * Shows actual noise value - calculated via 2nd-degree polynomial regression.
 */
@Composable
fun SignalQualityIndicator(
    noiseLevel: Float, // Error Variance from Poly Fit
    modifier: Modifier = Modifier
) {
    // Color thresholds (Standard xDrip 0-200+ scale)
    // <10: Clean (Green), 10-25: Light (Lt Green), 25-60: Medium (Amber), >60: Heavy (Red)
    val color = when {
        noiseLevel < 10f -> androidx.compose.ui.graphics.Color(0xB34CAF50)  // Green
        noiseLevel < 25f -> androidx.compose.ui.graphics.Color(0xB38BC34A)  // Light Green
        noiseLevel < 60f -> androidx.compose.ui.graphics.Color(0xB3FFC107)  // Amber
        else -> androidx.compose.ui.graphics.Color(0xB3F44336)               // Red
    }
    
    // Animation: Pulse for medium+, Shake for heavy
    val infiniteTransition = rememberInfiniteTransition(label = "signalPulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (noiseLevel >= 25f) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (noiseLevel >= 60f) 300 else 600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val shakeRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (noiseLevel >= 60f) 8f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeRotation"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Animated Icon
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Noise: $noiseLevel",
            tint = color,
            modifier = Modifier
                .size(14.dp)
                .scale(pulseScale)
                .modifierRotate(shakeRotation)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Raw noise value (xDrip-style, 1 decimal)
        Text(
            text = String.format(java.util.Locale.US, "%.1f", noiseLevel),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.9f)
        )
    }
}


/**
 * Bottom sheet for clear options in Dashboard
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DashboardClearOptionsBottomSheet(
    onDismiss: () -> Unit,
    onClearDisabled: () -> Unit,
    onClearAll: () -> Unit,
    disabledCount: Int,
    totalCount: Int
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp) // Extra padding for nav bar
                .padding(bottom = 24.dp)
        ) {
            Text("Clear Calibrations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Choose what to clear:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (disabledCount > 0) {
                androidx.compose.material3.Surface(onClick = onClearDisabled, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear disabled only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("$disabledCount disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            androidx.compose.material3.Surface(onClick = onClearAll, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        Text("$totalCount calibrations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}



