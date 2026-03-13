package tk.glucodata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import tk.glucodata.MainActivity
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.SuperGattCallback
import tk.glucodata.data.GlucoseRepository
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.util.GlucoseFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExpressiveAppWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val glucoseRepo = GlucoseRepository()
        
        // 1. Get History (Last 24h for chart)
        val history = withContext(Dispatchers.IO) {
            glucoseRepo.getRecentHistory(24 * 60 * 60 * 1000L)
        }
        
        // 2. Get Current Reading
        val lastGlucose = Natives.lastglucose()
        val webGlucose = SuperGattCallback.previousglucose
        
        val validGlucose = if (webGlucose != null && (System.currentTimeMillis() - webGlucose.time < 15 * 60 * 1000)) {
             GlucosePoint(webGlucose.value.toFloatOrNull() ?: 0f, "", webGlucose.time, 0f, webGlucose.rate)
        } else if (lastGlucose != null) {
             GlucosePoint(lastGlucose.value.toFloatOrNull() ?: 0f, "", lastGlucose.time * 1000L, 0f, lastGlucose.rate)
        } else {
             history.lastOrNull()
        }

        // 3. Calculate Delta (Change from previous reading)
        var delta = 0f
        if (validGlucose != null) {
            // Find a reading ~5-15 mins ago needed? Or just the previous one in history?
            // Usually simple delta is Current - Previous.
            // Let's find the last point in history that is BEFORE the current point.
            val previousPoint = history.filter { it.timestamp < validGlucose.timestamp }.maxByOrNull { it.timestamp }
            if (previousPoint != null) {
                delta = validGlucose.value - previousPoint.value
            }
        }
        // Round delta
        delta = (Math.round(delta * 10.0) / 10.0).toFloat()

        provideContent {
            GlanceTheme {
                WidgetContent(
                    validGlucose = validGlucose,
                    history = history,
                    delta = delta
                )
            }
        }
    }

    @Composable
    fun WidgetContent(
        validGlucose: GlucosePoint?,
        history: List<GlucosePoint>,
        delta: Float 
    ) {
        val context = LocalContext.current
        val size = LocalSize.current
        
        val isMmol = GlucoseFormatter.isMmolApp()
        
        val primaryStr = if (validGlucose != null) {
            GlucoseFormatter.format(validGlucose.value, isMmol)
        } else {
            "--"
        }
        
        val rawValue = validGlucose?.rawValue ?: 0f
        val showSecondary = validGlucose != null && rawValue > 0f && Math.abs(validGlucose.value - rawValue) > 0.1f
        val secondaryStr = if (showSecondary) {
            GlucoseFormatter.format(rawValue, isMmol)
        } else {
            ""
        }
        
        val rate = validGlucose?.rate ?: 0f
        
        val showChart = size.height >= 120.dp
        
        // --- SENSOR INFO LOGIC ---
        var activeSerial = "Unknown"
        var progressRatio = 0f
        var daysRemainingText = ""
        
        try {
            val sName = Natives.lastsensorname() ?: ""
            if (sName.isNotEmpty()) {
                activeSerial = sName
                val gatts = tk.glucodata.SensorBluetooth.mygatts() ?: emptyList()
                val gatt = gatts.find { it.SerialNumber == sName }
                
                if (gatt != null) {
                    if (gatt is tk.glucodata.drivers.aidex.AiDexDriver) {
                        val remainingHours = try { gatt.getSensorRemainingHours() } catch(_: Throwable) { -1 }
                        val ageHours = try { gatt.getSensorAgeHours() } catch(_: Throwable) { -1 }
                        if (remainingHours >= 0 && ageHours >= 0) {
                            val totalHours = remainingHours + ageHours
                            if (totalHours > 0) {
                                progressRatio = (ageHours.toFloat() / totalHours.toFloat()).coerceIn(0f, 1f)
                            }
                            // Assuming 14 days max for AiDex usually, or use totalHours / 24
                            val daysLeft = remainingHours / 24
                            val totalDays = totalHours / 24
                            daysRemainingText = "$daysLeft / $totalDays"
                        }
                    } else {
                        // Legacy/Sibionics
                        val expectedEndMs = Natives.getSensorEndTime(gatt.dataptr, false)
                        val startMs = Natives.getSensorStartmsec(gatt.dataptr)
                        if (startMs > 0 && expectedEndMs > startMs) {
                            val now = System.currentTimeMillis()
                            val totalMs = expectedEndMs - startMs
                            val elapsedMs = now - startMs
                            progressRatio = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                            
                            val remainingMs = (expectedEndMs - now).coerceAtLeast(0)
                            val daysLeft = remainingMs / (1000 * 60 * 60 * 24)
                            val totalDays = totalMs / (1000 * 60 * 60 * 24)
                            daysRemainingText = "$daysLeft / $totalDays"
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Native failed, fallback gently
        }
        // -----------------------
        
        val expressiveFont = FontFamily("sans-serif")

        // Single Container, vertical layout
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java)))
                .padding(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // TOP ROW: SPLIT PILLS
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(80.dp).padding(4.dp), // Height matches reference roughly
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- LEFT PILL: DATA ---
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp), 
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                         // Primary Value
                         Text(
                            text = primaryStr,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Normal, 
                                fontFamily = expressiveFont
                            ),
                            maxLines = 1
                        )
                        
                        // Separator + Secondary handled minimally to save space in pill
                        if (showSecondary) {
                            Text(
                                text = " / ",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = expressiveFont
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = secondaryStr, 
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 28.sp, 
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = expressiveFont
                                ),
                                maxLines = 1
                            )
                        }
                        
                        Spacer(GlanceModifier.defaultWeight()) // Push arrow right
                        
                        // Native engine arrow
                        val arrowColor = GlanceTheme.colors.onSurface.getColor(context)
                        val arrowBitmap = remember(rate, arrowColor) {
                             tk.glucodata.NotificationChartDrawer.drawArrow(
                                 context, 
                                 rate, 
                                 isMmol, 
                                 arrowColor.toArgb(),
                                 1.3f // Scaled for pill
                             )
                        }
                        if (arrowBitmap != null) {
                            Image(
                                provider = ImageProvider(arrowBitmap),
                                contentDescription = "Trend",
                                modifier = GlanceModifier.size(32.dp)
                            )
                        }
                    }
                }
                
                Spacer(GlanceModifier.width(4.dp)) // Gap between pills
                
                // --- RIGHT PILL: SENSOR INFO ---
                // We use a generated Bitmap as the background to show progress visually
                val bgColor = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb() // Empty track color
                val fgColor = GlanceTheme.colors.primary.getColor(context).toArgb()      // Filled track color
                
                // Estimate size since Glance doesn't let us measure easily: 120dp x 80dp
                val density = context.resources.displayMetrics.density
                val progressBg = remember(progressRatio, bgColor, fgColor) {
                     WidgetBitmapGenerators.generateSensorProgressBitmap(
                         widthPx = (120 * density).toInt(),
                         heightPx = (80 * density).toInt(),
                         progressRatio = progressRatio,
                         bgColor = bgColor,
                         fgColor = fgColor,
                         cornerRadiusPx = 24 * density
                     )
                }

                Box(
                    modifier = GlanceModifier
                        .width(120.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Background Image (Progress)
                    Image(
                        provider = ImageProvider(progressBg),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = androidx.glance.layout.ContentScale.FillBounds
                    )
                    
                    // 2. Text Content over it
                    Column(
                        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeSerial,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontFamily = expressiveFont,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = daysRemainingText,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 16.sp,
                                    fontFamily = expressiveFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            // Spacer(GlanceModifier.width(4.dp))
                            // Optional: Small icon for sensor/calendar
                        }
                    }
                }
            }
            
            // BOTTOM SECTION: CHART (When Expanded)
            if (showChart) {
                Box(
                     modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp),
                     contentAlignment = Alignment.Center
                ) {
                     val chartColor = GlanceTheme.colors.primary.getColor(context)
                     val chartBitmap = remember(history, chartColor) {
                        WidgetBitmapGenerators.generateChartBitmap(
                            context = context,
                            history = history,
                            widthDto = 300, 
                            heightDto = 100, 
                            color = chartColor.toArgb(),
                            isMmol = isMmol
                        )
                    }
                     Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = "Chart",
                        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                        contentScale = androidx.glance.layout.ContentScale.FillBounds
                    )
                }
            }
        }
    }
}

