package tk.glucodata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryBrowseRange(val days: Int, val labelRes: Int) {
    DAY_1(1, R.string.range_1d),
    DAY_7(7, R.string.range_7d),
    DAY_14(14, R.string.range_14d),
    DAY_30(30, R.string.range_30d),
    DAY_90(90, R.string.range_90d)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBrowseScreen(
    glucoseHistory: List<GlucosePoint>,
    unit: String,
    viewMode: Int,
    targetLow: Float,
    targetHigh: Float,
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity>,
    onBack: () -> Unit,
    onPointClick: ((GlucosePoint) -> Unit)? = null
) {
    var selectedHistoryRange by rememberSaveable { mutableStateOf(HistoryBrowseRange.DAY_30) }
    var selectedChartRange by rememberSaveable { mutableStateOf(TimeRange.H24) }

    val now = System.currentTimeMillis()
    val cutoff = remember(selectedHistoryRange, now) {
        now - selectedHistoryRange.days * 24L * 60L * 60L * 1000L
    }
    val filteredHistory = remember(glucoseHistory, cutoff) {
        val source = glucoseHistory.filter { it.timestamp >= cutoff }
        source.sortedByDescending { it.timestamp }
    }
    val chartHistory = remember(filteredHistory) { filteredHistory.asReversed() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.historyname)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.no_data_available), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryBrowseRange.entries.forEach { range ->
                        AssistChip(
                            onClick = { selectedHistoryRange = range },
                            label = { Text(stringResource(range.labelRes)) },
                            leadingIcon = if (selectedHistoryRange == range) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Circle,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    DashboardChartSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        glucoseHistory = chartHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        viewMode = viewMode,
                        calibrations = calibrations,
                        onTimeRangeSelected = { selectedChartRange = it },
                        selectedTimeRange = selectedChartRange,
                        isExpanded = false,
                        expandedProgress = 0f,
                        onToggleExpanded = null,
                        onPointClick = onPointClick,
                        onCalibrationClick = null
                    )
                }
            }

            item {
                Text(
                    text = "${filteredHistory.size} ${stringResource(R.string.readings)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            itemsIndexed(filteredHistory, key = { _, item -> item.timestamp }) { index, item ->
                HistoryReadingRow(
                    point = item,
                    unit = unit,
                    isFirst = index == 0,
                    isLast = index == filteredHistory.lastIndex,
                    onClick = {
                        if (onPointClick != null) {
                            onPointClick(item)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryReadingRow(
    point: GlucosePoint,
    unit: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val dateLabel = remember(point.timestamp) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(point.timestamp))
    }
    val valueLabel = remember(point.value, unit) {
        if (tk.glucodata.ui.util.GlucoseFormatter.isMmol(unit)) {
            String.format(Locale.getDefault(), "%.1f", point.value)
        } else {
            point.value.toInt().toString()
        }
    }

    val shape = when {
        isFirst && isLast -> RoundedCornerShape(16.dp)
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        isLast -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(6.dp)
    }

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            )
        }
    }
}
