@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.util.ConnectedButtonGroup
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

fun formatStatsDateRange(range: StatsDateRange?): String? {
    if (range == null) return null
    val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
    return "${formatter.format(Date(range.startMillis))} - ${formatter.format(Date(range.endMillis))}"
}

fun clampStatsDateRangeToAvailable(range: StatsDateRange?, availableRange: StatsDateRange?): StatsDateRange? {
    if (range == null) return availableRange
    if (availableRange == null) return range
    val startMillis = maxOf(range.startMillis, availableRange.startMillis)
    val endMillis = minOf(range.endMillis, availableRange.endMillis)
    return if (endMillis >= startMillis) {
        StatsDateRange(startMillis = startMillis, endMillis = endMillis)
    } else {
        availableRange
    }
}

fun toPickerUtcDateMillis(timestampMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return localDate
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

fun pickerUtcDateMillisToLocalStart(utcDateMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(utcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return localDate
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

fun pickerUtcDateMillisToLocalEnd(utcDateMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(utcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return localDate
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli() - 1L
}

private fun formatPickerHeadline(
    startUtcMillis: Long?,
    endUtcMillis: Long?
): String? {
    if (startUtcMillis == null && endUtcMillis == null) return null
    val monthDayFormatter = SimpleDateFormat("d MMM", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val monthDayYearFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    return when {
        startUtcMillis != null && endUtcMillis != null &&
            yearFormatter.format(Date(startUtcMillis)) == yearFormatter.format(Date(endUtcMillis)) ->
            "${monthDayFormatter.format(Date(startUtcMillis))} - ${monthDayYearFormatter.format(Date(endUtcMillis))}"
        startUtcMillis != null && endUtcMillis != null ->
            "${monthDayYearFormatter.format(Date(startUtcMillis))} - ${monthDayYearFormatter.format(Date(endUtcMillis))}"
        startUtcMillis != null -> monthDayYearFormatter.format(Date(startUtcMillis))
        else -> monthDayYearFormatter.format(Date(endUtcMillis!!))
    }
}

@Composable
fun StatsDateRangePickerHeadline(
    state: DateRangePickerState,
    modifier: Modifier = Modifier
) {
    val headline = remember(state.selectedStartDateMillis, state.selectedEndDateMillis) {
        formatPickerHeadline(state.selectedStartDateMillis, state.selectedEndDateMillis)
    }
    if (!headline.isNullOrBlank()) {
        Text(
            text = headline,
            modifier = modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatsRangeSelectorControl(
    selectedRange: StatsTimeRange?,
    activeRange: StatsDateRange?,
    isLoading: Boolean,
    hasData: Boolean,
    readingCount: Int,
    onRangeSelected: (StatsTimeRange) -> Unit,
    onCustomRangeClick: () -> Unit,
    modifier: Modifier = Modifier,
    countLabelResId: Int = R.string.points
) {
    val view = LocalView.current
    var lastRangeHapticAt by remember { mutableLongStateOf(0L) }
    val subtitle = when {
        isLoading -> stringResource(R.string.loading_data)
        activeRange != null -> formatStatsDateRange(activeRange)
        else -> stringResource(R.string.statistics_subtitle)
    }
    val ranges = StatsTimeRange.entries.toList()

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasData && readingCount > 0) {
                Text(
                    text = "$readingCount ${stringResource(countLabelResId)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Surface(
                modifier = Modifier.heightIn(min = 36.dp),
                onClick = onCustomRangeClick,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = if (selectedRange == null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (selectedRange == null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier
                        .animateContentSize()
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    AnimatedContent(
                        targetState = subtitle ?: stringResource(R.string.statistics_subtitle),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "statsRangeSubtitle"
                    ) { targetSubtitle ->
                        Text(
                            text = targetSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val context = LocalContext.current
        ConnectedButtonGroup(
            options = ranges,
            selectedOption = selectedRange,
            onOptionSelected = { range ->
                if (range != selectedRange) {
                    val now = System.currentTimeMillis()
                    if (now - lastRangeHapticAt >= 90L) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        lastRangeHapticAt = now
                    }
                    onRangeSelected(range)
                }
            },
            labelText = { option -> context.getString(option.labelResId) },
            label = { option ->
                Text(
                    text = stringResource(option.labelResId),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            itemHeight = 40.dp,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
