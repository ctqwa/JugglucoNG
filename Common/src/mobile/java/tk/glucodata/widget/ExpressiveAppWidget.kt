package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.DisplayDataState
import tk.glucodata.GlucosePoint
import tk.glucodata.MainActivity
import tk.glucodata.NotificationChartDrawer
import tk.glucodata.R
import tk.glucodata.WidgetDisplaySource
import tk.glucodata.ui.util.GlucoseFormatter

class ExpressiveAppWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val currentDisplay = withContext(Dispatchers.IO) {
            WidgetDisplaySource.resolveWidgetSnapshot()
        }
        val chartHistory = withContext(Dispatchers.IO) {
            WidgetDisplaySource.resolveChartHistory(currentDisplay, WidgetDisplaySource.CHART_WINDOW_MS)
        }
        val activeSensorSerial = currentDisplay?.sensorId ?: WidgetDisplaySource.resolveActiveSensorSerial()
        val resolvedDisplay = WidgetDisplaySource.resolveDisplaySnapshot(currentDisplay, chartHistory, activeSensorSerial)
        val viewMode = resolvedDisplay?.viewMode ?: WidgetDisplaySource.resolveViewMode(activeSensorSerial)
        val dataState = WidgetDisplaySource.resolveDataState(resolvedDisplay, chartHistory, activeSensorSerial)
        val hasCalibration = WidgetDisplaySource.hasCalibration(activeSensorSerial, viewMode)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    currentDisplay = resolvedDisplay,
                    history = chartHistory,
                    dataState = dataState,
                    activeSensorSerial = activeSensorSerial,
                    viewMode = viewMode,
                    hasCalibration = hasCalibration
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        currentDisplay: CurrentDisplaySource.Snapshot?,
        history: List<GlucosePoint>,
        dataState: DisplayDataState.Status,
        activeSensorSerial: String?,
        viewMode: Int,
        hasCalibration: Boolean
    ) {
        val context = LocalContext.current
        val size = LocalSize.current
        val prefs = remember(context) {
            context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        }

        val isMmol = GlucoseFormatter.isMmolApp()
        val fontSize = prefs.getFloat("notification_font_size", 1.0f)
        val fontWeight = prefs.getInt("notification_font_weight", 400)
        val useSystemFont = prefs.getInt("notification_font_family", 0) == 1
        val showArrow = prefs.getBoolean("notification_show_arrow", true)
        val arrowScale = prefs.getFloat("notification_arrow_size", 1.0f)

        val displayValue = currentDisplay?.primaryValue ?: 0f
        val glucoseColor = remember(displayValue, isMmol) {
            NotificationChartDrawer.getGlucoseColor(context, displayValue, isMmol)
        }
        val valueText = currentDisplay?.fullFormatted
        val rate = currentDisplay?.rate ?: Float.NaN

        val valueBitmap = remember(valueText, glucoseColor, fontSize, fontWeight, useSystemFont) {
            valueText?.let {
                NotificationChartDrawer.drawGlucoseText(
                    context,
                    it,
                    glucoseColor,
                    fontSize,
                    fontWeight,
                    useSystemFont
                )
            }
        }

        val arrowBitmap = remember(rate, showArrow, arrowScale, glucoseColor, isMmol) {
            if (showArrow && rate.isFinite()) {
                NotificationChartDrawer.drawArrow(context, rate, isMmol, glucoseColor, arrowScale)
            } else {
                null
            }
        }

        val showChart = history.size >= 2 && size.height >= 132.dp
        val density = context.resources.displayMetrics.density
        val chartBitmap = remember(history, showChart, size, isMmol, viewMode, hasCalibration, activeSensorSerial) {
            if (!showChart) {
                null
            } else {
                val widthPx = ((size.width.value - 24f).coerceAtLeast(96f) * density).roundToInt()
                val heightPx = ((size.height.value - 104f).coerceAtLeast(60f) * density).roundToInt()
                NotificationChartDrawer.drawChart(
                    context,
                    history,
                    widthPx,
                    heightPx,
                    isMmol,
                    viewMode,
                    true,
                    hasCalibration,
                    true,
                    activeSensorSerial,
                    WidgetDisplaySource.CHART_WINDOW_MS
                )
            }
        }

        val fallbackText = remember(dataState) {
            when {
                dataState.isStale -> context.getString(R.string.nonewvalue)
                dataState.isAwaitingData || !dataState.sensorPresent -> context.getString(R.string.novalue)
                else -> context.getString(R.string.novalue)
            }
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(6.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(24.dp)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (valueBitmap != null) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Image(
                            provider = ImageProvider(valueBitmap),
                            contentDescription = "Glucose"
                        )

                        Spacer(GlanceModifier.defaultWeight())

                        if (arrowBitmap != null) {
                            Image(
                                provider = ImageProvider(arrowBitmap),
                                contentDescription = "Trend"
                            )
                        }
                    }
                } else {
                    Text(
                        text = fallbackText,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontFamily = FontFamily("sans-serif")
                        ),
                        maxLines = 1
                    )
                }
            }

            if (chartBitmap != null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(top = 6.dp)
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = "Chart",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = androidx.glance.layout.ContentScale.FillBounds
                    )
                }
            }
        }
    }
}
