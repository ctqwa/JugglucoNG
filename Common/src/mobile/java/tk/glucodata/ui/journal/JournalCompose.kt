@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import tk.glucodata.Applic
import tk.glucodata.R
import tk.glucodata.data.journal.JournalActiveInsulinSummary
import tk.glucodata.data.journal.JournalChartMarker
import tk.glucodata.data.journal.JournalCurvePoint
import tk.glucodata.data.journal.JournalEntry
import tk.glucodata.data.journal.JournalEntryInput
import tk.glucodata.data.journal.JournalEntryType
import tk.glucodata.data.journal.JournalInsulinPreset
import tk.glucodata.data.journal.JournalIntensity
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.util.ConnectedButtonGroup
import tk.glucodata.ui.util.GlucoseFormatter

data class JournalEntryDraft(
    val entryId: Long? = null,
    val type: JournalEntryType,
    val timestamp: Long,
    val title: String = "",
    val amountText: String = "",
    val glucoseText: String = "",
    val durationText: String = "",
    val note: String = "",
    val intensity: JournalIntensity? = null,
    val insulinPresetId: Long? = null,
    val chartAnchorGlucoseMgDl: Float? = null,
    val pairWithDose: Boolean = false,
    val pairedAmountText: String = ""
)

data class JournalDoseProfile(
    val enabled: Boolean,
    val carbRatioGramsPerUnit: Float,
    val insulinSensitivityMgDlPerUnit: Float,
    val targetHighMgDl: Float
)

fun buildJournalChartMarkers(
    entries: List<JournalEntry>,
    presetsById: Map<Long, JournalInsulinPreset>,
    unit: String,
    history: List<GlucosePoint> = emptyList()
): List<JournalChartMarker> {
    val isMmol = GlucoseFormatter.isMmol(unit)
    fun nearestHistoryDisplayValue(timestamp: Long): Float? {
        if (history.isEmpty()) return null
        val nearest = history.minByOrNull { abs(it.timestamp - timestamp) } ?: return null
        val distance = abs(nearest.timestamp - timestamp)
        return nearest.value.takeIf { distance <= 30L * 60L * 1000L && it.isFinite() && it > 0.1f }
    }

    return entries.map { entry ->
        val preset = entry.insulinPresetId?.let(presetsById::get)
        val chartValue = when (entry.type) {
            JournalEntryType.INSULIN -> null
            JournalEntryType.FINGERSTICK -> entry.glucoseValueMgDl?.let {
                GlucoseFormatter.displayFromMgDl(it, isMmol)
            }
            else -> entry.glucoseValueMgDl?.let {
                GlucoseFormatter.displayFromMgDl(it, isMmol)
            } ?: nearestHistoryDisplayValue(entry.timestamp)
        }
        JournalChartMarker(
            entryId = entry.id,
            timestamp = entry.timestamp,
            type = entry.type,
            title = entry.title,
            accentColor = preset?.accentColor ?: journalTypeColor(entry.type).toArgb(),
            badgeText = journalMarkerBadge(entry.type),
            detailText = journalMarkerDetail(entry, preset, unit),
            amount = entry.amount,
            chartGlucoseValue = chartValue,
            curvePoints = if (entry.type == JournalEntryType.INSULIN && preset != null) {
                preset.curvePoints
            } else {
                emptyList()
            },
            activeStartMillis = if (entry.type == JournalEntryType.INSULIN && preset != null) {
                preset.activeStartAt(entry.timestamp)
            } else {
                null
            },
            activeEndMillis = if (entry.type == JournalEntryType.INSULIN && preset != null) {
                preset.activeEndAt(entry.timestamp)
            } else {
                null
            }
        )
    }
}

fun buildActiveInsulinSummary(
    entries: List<JournalEntry>,
    presetsById: Map<Long, JournalInsulinPreset>,
    atMillis: Long
): JournalActiveInsulinSummary? {
    val activeEntries = entries.mapNotNull { entry ->
        val preset = entry.insulinPresetId?.let(presetsById::get) ?: return@mapNotNull null
        if (entry.type != JournalEntryType.INSULIN) return@mapNotNull null
        if (!preset.countsTowardIob) return@mapNotNull null
        val amount = entry.amount ?: return@mapNotNull null
        val activity = preset.activityFractionAt(entry.timestamp, atMillis)
        if (activity <= 0.01f) return@mapNotNull null
        Triple(entry, preset, amount to activity)
    }
    if (activeEntries.isEmpty()) return null

    val totalUnits = activeEntries.sumOf { it.third.first.toDouble() }.toFloat()
    val weightedActivity = activeEntries.sumOf { (it.third.first * it.third.second).toDouble() }.toFloat()
    return JournalActiveInsulinSummary(
        activeEntryCount = activeEntries.size,
        totalUnits = totalUnits,
        weightedActivityPercent = ((weightedActivity / totalUnits) * 100f).roundToInt().coerceIn(0, 100),
        nextEndingAt = activeEntries.minOfOrNull { it.second.activeEndAt(it.first.timestamp) }
    )
}

@Composable
fun JournalQuickDock(
    unit: String,
    visibleEntries: List<JournalEntry>,
    insulinPresets: Map<Long, JournalInsulinPreset>,
    modifier: Modifier = Modifier,
    onTypeSelected: (JournalEntryType) -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
    onCalibrate: (() -> Unit)? = null
) {
    val primaryActions = remember(onCalibrate) {
        buildList {
            if (onCalibrate != null) add(JournalTrayAction.CALIBRATE)
            add(JournalTrayAction.INSULIN)
            add(JournalTrayAction.CARBS)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        JournalActionRow(
            actions = primaryActions,
            onAction = { action ->
                when (action) {
                    JournalTrayAction.CALIBRATE -> onCalibrate?.invoke()
                    JournalTrayAction.INSULIN -> onTypeSelected(JournalEntryType.INSULIN)
                    JournalTrayAction.CARBS -> onTypeSelected(JournalEntryType.CARBS)
                    else -> Unit
                }
            }
        )
        JournalActionRow(
            actions = listOf(
                JournalTrayAction.FINGERSTICK,
                JournalTrayAction.ACTIVITY,
                JournalTrayAction.NOTE
            ),
            onAction = { action ->
                when (action) {
                    JournalTrayAction.FINGERSTICK -> onTypeSelected(JournalEntryType.FINGERSTICK)
                    JournalTrayAction.ACTIVITY -> onTypeSelected(JournalEntryType.ACTIVITY)
                    JournalTrayAction.NOTE -> onTypeSelected(JournalEntryType.NOTE)
                    else -> Unit
                }
            }
        )

        if (visibleEntries.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                visibleEntries.forEach { entry ->
                    JournalEntryChip(
                        entry = entry,
                        unit = unit,
                        insulinPreset = entry.insulinPresetId?.let(insulinPresets::get),
                        onClick = { onEditEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun JournalEntrySheet(
    unit: String,
    selectedTimestamp: Long,
    suggestedGlucoseMgDl: Float? = null,
    suggestedAmountFraction: Float? = null,
    insulinPresets: List<JournalInsulinPreset>,
    doseProfile: JournalDoseProfile? = null,
    initialType: JournalEntryType,
    existingEntry: JournalEntry? = null,
    onDismiss: () -> Unit,
    onSave: (JournalEntryInput) -> Unit,
    onSaveEntries: ((List<JournalEntryInput>) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    sensorSerialProvider: () -> String?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val activeInsulinPresets = remember(insulinPresets) { insulinPresets.filter { !it.isArchived } }
    val presetsById = remember(insulinPresets) { insulinPresets.associateBy { it.id } }
    val initialDraft = remember(existingEntry?.id, initialType, selectedTimestamp, suggestedGlucoseMgDl, suggestedAmountFraction, unit, insulinPresets) {
        buildDraft(existingEntry, initialType, selectedTimestamp, unit, suggestedGlucoseMgDl, suggestedAmountFraction)
    }
    var draft by remember(existingEntry?.id, initialType, selectedTimestamp, suggestedGlucoseMgDl, suggestedAmountFraction, unit, insulinPresets) {
        mutableStateOf(initialDraft)
    }
    var showDatePicker by remember(existingEntry?.id, initialType, selectedTimestamp) { mutableStateOf(false) }
    var showTimePicker by remember(existingEntry?.id, initialType, selectedTimestamp) { mutableStateOf(false) }
    val saveInputs = draft.toInputs(unit, sensorSerialProvider(), presetsById)
    val canSave = saveInputs.isNotEmpty()
    val calculatorProfile = remember(doseProfile) {
        doseProfile?.takeIf {
            it.enabled &&
                it.carbRatioGramsPerUnit > 0f &&
                it.insulinSensitivityMgDlPerUnit > 0f
        }
    }
    LaunchedEffect(existingEntry?.id, draft.type, draft.pairWithDose, activeInsulinPresets) {
        if (
            existingEntry != null ||
            draft.insulinPresetId != null ||
            (draft.type != JournalEntryType.INSULIN && !(draft.type == JournalEntryType.CARBS && draft.pairWithDose))
        ) {
            return@LaunchedEffect
        }
        preferredInsulinPreset(activeInsulinPresets)?.let { preset ->
            draft = draft.copy(
                insulinPresetId = preset.id,
                title = preset.displayName
            )
        }
    }
    LaunchedEffect(
        draft.type,
        draft.amountText,
        draft.chartAnchorGlucoseMgDl,
        draft.pairWithDose,
        calculatorProfile
    ) {
        if (!draft.pairWithDose || calculatorProfile == null) return@LaunchedEffect
        val suggestedPair = when (draft.type) {
            JournalEntryType.CARBS -> calculateInsulinForCarbs(
                carbs = draft.amountText.parseFloatOrNull(),
                glucoseMgDl = draft.chartAnchorGlucoseMgDl,
                profile = calculatorProfile
            )?.totalInsulinUnits?.let(::formatInsulinDose)

            JournalEntryType.INSULIN -> calculateCoveredCarbsForInsulin(
                insulinUnits = draft.amountText.parseFloatOrNull(),
                glucoseMgDl = draft.chartAnchorGlucoseMgDl,
                profile = calculatorProfile
            )?.let(::formatCarbDose)

            else -> null
        }
        if (suggestedPair != null && suggestedPair != draft.pairedAmountText) {
            draft = draft.copy(pairedAmountText = suggestedPair)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                when (draft.type) {
                                    JournalEntryType.FINGERSTICK -> R.string.journal_type_bg_short
                                    else -> draft.type.labelRes()
                                }
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
//                        Text(
//                            text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
//                                .format(Date(draft.timestamp)),
//                            style = MaterialTheme.typography.bodyMedium,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
                    }
                    existingEntry?.id?.let { entryId ->
                        IconButton(onClick = { onDelete?.invoke(entryId) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item(key = "type_selector") {
                JournalTypeSelector(
                    selectedType = draft.type,
                    onTypeSelected = {
                        draft = draft.copy(type = it).normalizedForType(unit, suggestedGlucoseMgDl, suggestedAmountFraction)
                    }
                )
            }

            item(key = "date_time") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    JournalMetaCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Event,
                        title = stringResource(R.string.date),
                        value = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(draft.timestamp)),
                        onClick = { showDatePicker = true }
                    )
                    JournalMetaCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccessTime,
                        title = stringResource(R.string.time),
                        value = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(draft.timestamp)),
                        onClick = { showTimePicker = true }
                    )
                }
            }

            when (draft.type) {
                JournalEntryType.INSULIN -> {
                    item(key = "insulin_presets") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeInsulinPresets.forEach { preset ->
                                JournalPresetPill(
                                    preset = preset,
                                    selected = draft.insulinPresetId == preset.id,
                                    onClick = {
                                        draft = draft.copy(
                                            insulinPresetId = preset.id,
                                            title = preset.displayName
                                        )
                                    }
                                )
                            }
                        }
                    }
                    draft.insulinPresetId?.let { presetId ->
                        presetsById[presetId]?.let { preset ->
                            item(key = "insulin_window") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.journal_active_window,
                                            stringResource(R.string.minutes_short_format, preset.onsetMinutes),
                                            stringResource(R.string.minutes_short_format, preset.durationMinutes)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                    item(key = "insulin_amount") {
                        JournalStepperField(
                            value = draft.amountText,
                            onValueChange = { draft = draft.copy(amountText = it) },
                            onStep = { delta ->
                                draft = draft.copy(amountText = adjustDecimalDraft(draft.amountText, delta, step = 0.5f))
                            },
                            label = stringResource(R.string.journal_type_insulin),
                            suffix = "U"
                        )
                    }
                    if (existingEntry == null && calculatorProfile != null) {
                        item(key = "insulin_dose_assist") {
                            JournalDoseAssistCard(
                                draft = draft,
                                profile = calculatorProfile,
                                activeInsulinPresets = activeInsulinPresets,
                                unit = unit,
                                onDraftChange = { draft = it }
                            )
                        }
                    }
                }

                JournalEntryType.CARBS -> {
                    item(key = "carbs_amount") {
                        JournalStepperField(
                            value = draft.amountText,
                            onValueChange = { draft = draft.copy(amountText = it) },
                            onStep = { delta ->
                                draft = draft.copy(amountText = adjustDecimalDraft(draft.amountText, delta, step = 5f))
                            },
                            label = stringResource(R.string.carbo),
                            suffix = "g"
                        )
                    }
                    if (existingEntry == null && calculatorProfile != null) {
                        item(key = "carbs_dose_assist") {
                            JournalDoseAssistCard(
                                draft = draft,
                                profile = calculatorProfile,
                                activeInsulinPresets = activeInsulinPresets,
                                unit = unit,
                                onDraftChange = { draft = it }
                            )
                        }
                    }
                }

                JournalEntryType.FINGERSTICK -> {
                    item(key = "fingerstick_amount") {
                        JournalStepperField(
                            value = draft.glucoseText,
                            onValueChange = { draft = draft.copy(glucoseText = it) },
                            onStep = { delta ->
                                val step = if (GlucoseFormatter.isMmol(unit)) 0.1f else 5f
                                draft = draft.copy(glucoseText = adjustDecimalDraft(draft.glucoseText, delta, step))
                            },
                            label = stringResource(R.string.glucose_with_unit, unit),
                            suffix = null
                        )
                    }
                }

                JournalEntryType.ACTIVITY -> {
                    item(key = "activity_name") {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = { draft = draft.copy(title = it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.name).trimTrailingLabel()) },
                            singleLine = true
                        )
                    }
                    item(key = "activity_duration") {
                        JournalStepperField(
                            value = draft.durationText,
                            onValueChange = { draft = draft.copy(durationText = it.filter(Char::isDigit)) },
                            onStep = { delta ->
                                draft = draft.copy(durationText = adjustIntegerDraft(draft.durationText, delta * 5, minValue = 0))
                            },
                            label = stringResource(R.string.duration_label),
                            suffix = stringResource(R.string.minutes),
                            keyboardType = KeyboardType.Number
                        )
                    }
                    item(key = "activity_intensity") {
                        JournalIntensitySelector(
                            selectedIntensity = draft.intensity,
                            onIntensitySelected = { draft = draft.copy(intensity = it) }
                        )
                    }
                }

                JournalEntryType.NOTE -> {
                    item(key = "note_title") {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = { draft = draft.copy(title = it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.name).trimTrailingLabel()) },
                            singleLine = true
                        )
                    }
                }
            }

            item(key = "note_field") {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (draft.type == JournalEntryType.NOTE) 132.dp else 96.dp),
                    label = { Text(stringResource(R.string.journal_note_label)) },
                    singleLine = false,
                    maxLines = if (draft.type == JournalEntryType.NOTE) 5 else 3
                )
            }

            item(key = "save_button") {
                FilledTonalButton(
                    onClick = {
                        if (canSave) {
                            if (saveInputs.size > 1) {
                                onSaveEntries?.invoke(saveInputs) ?: saveInputs.forEach(onSave)
                            } else {
                                saveInputs.firstOrNull()?.let(onSave)
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = journalTypeIcon(draft.type),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.save))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = draft.timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        draft = draft.copy(timestamp = mergeJournalDate(draft.timestamp, selected))
                    }
                    showDatePicker = false
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = remember(draft.timestamp) {
            Calendar.getInstance().apply { timeInMillis = draft.timestamp }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(text = stringResource(R.string.time)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(
                        timestamp = mergeJournalTime(
                            draft.timestamp,
                            timePickerState.hour to timePickerState.minute
                        )
                    )
                    showTimePicker = false
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun buildDraft(
    existingEntry: JournalEntry?,
    initialType: JournalEntryType,
    selectedTimestamp: Long,
    unit: String,
    suggestedGlucoseMgDl: Float? = null,
    suggestedAmountFraction: Float? = null
): JournalEntryDraft {
    if (existingEntry == null) {
        return JournalEntryDraft(
            type = initialType,
            timestamp = selectedTimestamp,
            title = if (initialType == JournalEntryType.ACTIVITY) Applic.app.getString(R.string.journal_type_activity) else "",
            amountText = when (initialType) {
                JournalEntryType.INSULIN -> suggestedAmountFraction?.let(::suggestedInsulinAmountForFraction).orEmpty()
                JournalEntryType.CARBS -> suggestedAmountFraction?.let(::suggestedCarbAmountForFraction).orEmpty()
                else -> ""
            },
            glucoseText = if (initialType == JournalEntryType.FINGERSTICK) {
                suggestedGlucoseMgDl?.let { formatGlucoseForEditor(it, unit) }.orEmpty()
            } else {
                ""
            },
            chartAnchorGlucoseMgDl = suggestedGlucoseMgDl
        )
    }
    return JournalEntryDraft(
        entryId = existingEntry.id,
        type = existingEntry.type,
        timestamp = existingEntry.timestamp,
        title = existingEntry.title,
        amountText = existingEntry.amount?.let(::formatFloatForEditor).orEmpty(),
        glucoseText = existingEntry.glucoseValueMgDl?.let { formatGlucoseForEditor(it, unit) }.orEmpty(),
        durationText = existingEntry.durationMinutes?.toString().orEmpty(),
        note = existingEntry.note.orEmpty(),
        intensity = existingEntry.intensity,
        insulinPresetId = existingEntry.insulinPresetId,
        chartAnchorGlucoseMgDl = existingEntry.glucoseValueMgDl
    )
}

private fun preferredInsulinPreset(
    presets: List<JournalInsulinPreset>
): JournalInsulinPreset? {
    val sortedPresets = presets.sortedBy { it.sortOrder }
    return sortedPresets.firstOrNull { it.countsTowardIob } ?: sortedPresets.firstOrNull()
}

private fun JournalEntryDraft.normalizedForType(
    unit: String,
    suggestedGlucoseMgDl: Float? = null,
    suggestedAmountFraction: Float? = null
): JournalEntryDraft {
    return when (type) {
        JournalEntryType.INSULIN -> copy(
            amountText = amountText.ifBlank {
                suggestedAmountFraction?.let(::suggestedInsulinAmountForFraction).orEmpty()
            },
            glucoseText = "",
            durationText = "",
            intensity = null,
            pairWithDose = false,
            pairedAmountText = ""
        )

        JournalEntryType.CARBS -> copy(
            title = "",
            amountText = amountText.ifBlank {
                suggestedAmountFraction?.let(::suggestedCarbAmountForFraction).orEmpty()
            },
            glucoseText = "",
            durationText = "",
            intensity = null,
            insulinPresetId = null,
            pairWithDose = false,
            pairedAmountText = ""
        )

        JournalEntryType.FINGERSTICK -> copy(
            title = "",
            amountText = "",
            durationText = "",
            intensity = null,
            insulinPresetId = null,
            pairWithDose = false,
            pairedAmountText = "",
            glucoseText = glucoseText.ifBlank {
                suggestedGlucoseMgDl?.let { formatGlucoseForEditor(it, unit) }
                    ?: if (GlucoseFormatter.isMmol(unit)) "5.6" else "100"
            }
        )

        JournalEntryType.ACTIVITY -> copy(
            amountText = "",
            glucoseText = "",
            insulinPresetId = null,
            pairWithDose = false,
            pairedAmountText = ""
        )

        JournalEntryType.NOTE -> copy(
            amountText = "",
            glucoseText = "",
            durationText = "",
            intensity = null,
            insulinPresetId = null,
            pairWithDose = false,
            pairedAmountText = ""
        )
    }
}

private fun JournalEntryDraft.toInput(
    unit: String,
    sensorSerial: String?,
    presetsById: Map<Long, JournalInsulinPreset>
): JournalEntryInput? {
    val noteValue = note.trim().takeIf { it.isNotBlank() }
    return when (type) {
        JournalEntryType.INSULIN -> {
            val presetId = insulinPresetId ?: return null
            val preset = presetsById[presetId] ?: return null
            val amountValue = amountText.parseFloatOrNull() ?: return null
            JournalEntryInput(
                id = entryId,
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = type,
                title = preset.displayName,
                note = noteValue,
                amount = amountValue,
                glucoseValueMgDl = chartAnchorGlucoseMgDl,
                insulinPresetId = presetId
            )
        }

        JournalEntryType.CARBS -> {
            val grams = amountText.parseFloatOrNull() ?: return null
            JournalEntryInput(
                id = entryId,
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = type,
                title = Applic.app.getString(R.string.carbo),
                note = noteValue,
                amount = grams,
                glucoseValueMgDl = chartAnchorGlucoseMgDl
            )
        }

        JournalEntryType.FINGERSTICK -> {
            val glucoseMgDl = parseGlucoseToMgDl(glucoseText, unit) ?: return null
            JournalEntryInput(
                id = entryId,
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = type,
                title = Applic.app.getString(R.string.journal_type_fingerstick),
                note = noteValue,
                glucoseValueMgDl = glucoseMgDl
            )
        }

        JournalEntryType.ACTIVITY -> {
            val duration = durationText.parseIntOrNull() ?: return null
            val titleValue = title.trim().takeIf { it.isNotBlank() } ?: return null
            JournalEntryInput(
                id = entryId,
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = type,
                title = titleValue,
                note = noteValue,
                durationMinutes = duration,
                intensity = intensity,
                glucoseValueMgDl = chartAnchorGlucoseMgDl
            )
        }

        JournalEntryType.NOTE -> {
            val titleValue = title.trim().takeIf { it.isNotBlank() }
                ?: noteValue?.take(30)
                ?: return null
            JournalEntryInput(
                id = entryId,
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = type,
                title = titleValue,
                note = noteValue ?: titleValue,
                glucoseValueMgDl = chartAnchorGlucoseMgDl
            )
        }
    }
}

private fun JournalEntryDraft.toInputs(
    unit: String,
    sensorSerial: String?,
    presetsById: Map<Long, JournalInsulinPreset>
): List<JournalEntryInput> {
    val primary = toInput(unit, sensorSerial, presetsById) ?: return emptyList()
    if (!pairWithDose || entryId != null || type !in setOf(JournalEntryType.INSULIN, JournalEntryType.CARBS)) {
        return listOf(primary)
    }
    val paired = when (type) {
        JournalEntryType.CARBS -> {
            val presetId = insulinPresetId ?: return emptyList()
            val preset = presetsById[presetId] ?: return emptyList()
            val amountValue = pairedAmountText.parseFloatOrNull()?.takeIf { it > 0f } ?: return emptyList()
            JournalEntryInput(
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = JournalEntryType.INSULIN,
                title = preset.displayName,
                amount = amountValue,
                glucoseValueMgDl = chartAnchorGlucoseMgDl,
                insulinPresetId = presetId
            )
        }

        JournalEntryType.INSULIN -> {
            val grams = pairedAmountText.parseFloatOrNull()?.takeIf { it > 0f } ?: return emptyList()
            JournalEntryInput(
                timestamp = timestamp,
                sensorSerial = sensorSerial,
                type = JournalEntryType.CARBS,
                title = Applic.app.getString(R.string.carbo),
                amount = grams,
                glucoseValueMgDl = chartAnchorGlucoseMgDl
            )
        }

        else -> null
    } ?: return listOf(primary)
    return listOf(primary, paired)
}

@Composable
private fun JournalDoseAssistCard(
    draft: JournalEntryDraft,
    profile: JournalDoseProfile,
    activeInsulinPresets: List<JournalInsulinPreset>,
    unit: String,
    onDraftChange: (JournalEntryDraft) -> Unit
) {
    val insulinSuggestion = remember(draft.amountText, draft.chartAnchorGlucoseMgDl, profile) {
        calculateInsulinForCarbs(
            carbs = draft.amountText.parseFloatOrNull(),
            glucoseMgDl = draft.chartAnchorGlucoseMgDl,
            profile = profile
        )
    }
    val coveredCarbsSuggestion = remember(draft.amountText, draft.chartAnchorGlucoseMgDl, profile) {
        calculateCoveredCarbsForInsulin(
            insulinUnits = draft.amountText.parseFloatOrNull(),
            glucoseMgDl = draft.chartAnchorGlucoseMgDl,
            profile = profile
        )
    }
    val pairLabel = when (draft.type) {
        JournalEntryType.CARBS -> stringResource(R.string.journal_dose_pair_insulin)
        JournalEntryType.INSULIN -> stringResource(R.string.journal_dose_pair_carbs)
        else -> ""
    }
    val pairAmount = when (draft.type) {
        JournalEntryType.CARBS -> insulinSuggestion?.totalInsulinUnits?.let(::formatInsulinDose)
        JournalEntryType.INSULIN -> coveredCarbsSuggestion?.let(::formatCarbDose)
        else -> null
    }
    val pairEnabled = pairAmount != null
    val pairOnClick: () -> Unit = {
        val enabled = !draft.pairWithDose
        onDraftChange(
            draft.copy(
                pairWithDose = enabled,
                pairedAmountText = if (enabled) pairAmount.orEmpty() else "",
                insulinPresetId = when {
                    draft.type == JournalEntryType.CARBS && enabled -> draft.insulinPresetId
                        ?: preferredInsulinPreset(activeInsulinPresets)?.id
                    draft.type == JournalEntryType.CARBS -> null
                    else -> draft.insulinPresetId
                }
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.journal_dose_math_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.predictive_carb_ratio_value, profile.carbRatioGramsPerUnit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (draft.type) {
                JournalEntryType.CARBS -> {
                    val suggestion = insulinSuggestion
                    if (suggestion != null) {
                        Text(
                            text = stringResource(
                                R.string.journal_dose_suggested_insulin,
                                formatInsulinDose(suggestion.totalInsulinUnits)
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            JournalDoseMetric(
                                label = stringResource(
                                    R.string.journal_dose_food_component,
                                    "${formatInsulinComponent(suggestion.foodInsulinUnits)} U"
                                )
                            )
                            if (suggestion.correctionInsulinUnits > 0f) {
                                JournalDoseMetric(
                                    label = stringResource(
                                        R.string.journal_dose_correction_component,
                                        "${formatInsulinComponent(suggestion.correctionInsulinUnits)} U"
                                    )
                                )
                            }
                            draft.chartAnchorGlucoseMgDl?.let { glucose ->
                                JournalDoseMetric(label = formatGlucoseForEditor(glucose, unit))
                            }
                        }
                    }
                }

                JournalEntryType.INSULIN -> {
                    if (coveredCarbsSuggestion != null) {
                        Text(
                            text = stringResource(
                                R.string.journal_dose_covers_carbs,
                                formatCarbDose(coveredCarbsSuggestion)
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                else -> Unit
            }

            FilledTonalButton(
                onClick = pairOnClick,
                enabled = pairEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (draft.pairWithDose) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (draft.pairWithDose) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Icon(
                    imageVector = if (draft.type == JournalEntryType.CARBS) Icons.Default.Vaccines else Icons.Default.Restaurant,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = pairLabel)
            }

            if (draft.pairWithDose) {
                if (draft.type == JournalEntryType.CARBS) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeInsulinPresets.forEach { preset ->
                            JournalPresetPill(
                                preset = preset,
                                selected = draft.insulinPresetId == preset.id,
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            insulinPresetId = preset.id,
                                            title = if (draft.type == JournalEntryType.INSULIN) preset.displayName else draft.title
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                JournalStepperField(
                    value = draft.pairedAmountText,
                    onValueChange = { onDraftChange(draft.copy(pairedAmountText = it)) },
                    onStep = { delta ->
                        val step = if (draft.type == JournalEntryType.CARBS) 0.5f else 5f
                        onDraftChange(
                            draft.copy(
                                pairedAmountText = adjustDecimalDraft(draft.pairedAmountText, delta, step)
                            )
                        )
                    },
                    label = if (draft.type == JournalEntryType.CARBS) {
                        stringResource(R.string.journal_type_insulin)
                    } else {
                        stringResource(R.string.carbo)
                    },
                    suffix = if (draft.type == JournalEntryType.CARBS) "U" else "g"
                )
            }
        }
    }
}

@Composable
private fun JournalDoseMetric(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private data class JournalInsulinDoseSuggestion(
    val foodInsulinUnits: Float,
    val correctionInsulinUnits: Float,
    val totalInsulinUnits: Float
)

private fun calculateInsulinForCarbs(
    carbs: Float?,
    glucoseMgDl: Float?,
    profile: JournalDoseProfile
): JournalInsulinDoseSuggestion? {
    val carbValue = carbs?.takeIf { it > 0f } ?: return null
    val food = carbValue / profile.carbRatioGramsPerUnit
    val correction = glucoseMgDl
        ?.let { ((it - profile.targetHighMgDl) / profile.insulinSensitivityMgDlPerUnit).coerceAtLeast(0f) }
        ?: 0f
    return JournalInsulinDoseSuggestion(
        foodInsulinUnits = food,
        correctionInsulinUnits = correction,
        totalInsulinUnits = roundInsulinDose(food + correction)
    )
}

private fun calculateCoveredCarbsForInsulin(
    insulinUnits: Float?,
    glucoseMgDl: Float?,
    profile: JournalDoseProfile
): Float? {
    val insulinValue = insulinUnits?.takeIf { it > 0f } ?: return null
    val correction = glucoseMgDl
        ?.let { ((it - profile.targetHighMgDl) / profile.insulinSensitivityMgDlPerUnit).coerceAtLeast(0f) }
        ?: 0f
    return ((insulinValue - correction).coerceAtLeast(0f) * profile.carbRatioGramsPerUnit)
        .let { (it / 5f).roundToInt() * 5f }
        .takeIf { it > 0f }
}

private fun roundInsulinDose(value: Float): Float {
    return ((value / 0.5f).roundToInt() * 0.5f).coerceAtLeast(0.5f)
}

private fun formatInsulinDose(value: Float): String = formatFloatForEditor(roundInsulinDose(value))

private fun formatInsulinComponent(value: Float): String {
    return formatFloatForEditor(((value / 0.1f).roundToInt() * 0.1f).coerceAtLeast(0f))
}

private fun formatCarbDose(value: Float): String {
    return formatFloatForEditor(((value / 5f).roundToInt() * 5f).coerceAtLeast(5f))
}

private enum class JournalTrayAction {
    CALIBRATE,
    INSULIN,
    CARBS,
    FINGERSTICK,
    ACTIVITY,
    NOTE
}

@Composable
private fun JournalActionRow(
    actions: List<JournalTrayAction>,
    onAction: (JournalTrayAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { action ->
            FilledTonalButton(
                onClick = { onAction(action) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = when (action) {
                        JournalTrayAction.CALIBRATE -> Icons.Default.Bloodtype
                        JournalTrayAction.INSULIN -> Icons.Default.Vaccines
                        JournalTrayAction.CARBS -> Icons.Default.Restaurant
                        JournalTrayAction.FINGERSTICK -> Icons.Default.Bloodtype
                        JournalTrayAction.ACTIVITY -> Icons.Default.DirectionsRun
                        JournalTrayAction.NOTE -> Icons.AutoMirrored.Filled.Label
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (action) {
                        JournalTrayAction.CALIBRATE -> stringResource(R.string.calibrate_action)
                        JournalTrayAction.INSULIN -> stringResource(R.string.journal_type_insulin)
                        JournalTrayAction.CARBS -> stringResource(R.string.carbo)
                        JournalTrayAction.FINGERSTICK -> stringResource(R.string.journal_type_bg_short)
                        JournalTrayAction.ACTIVITY -> stringResource(R.string.journal_type_activity)
                        JournalTrayAction.NOTE -> stringResource(R.string.journal_type_note)
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun JournalTypeSelector(
    selectedType: JournalEntryType,
    onTypeSelected: (JournalEntryType) -> Unit
) {
    val selectedContentColor = MaterialTheme.colorScheme.onSurface
    val selectedContainerBase = MaterialTheme.colorScheme.surfaceContainerHigh
    ConnectedButtonGroup(
        options = JournalEntryType.entries,
        selectedOption = selectedType,
        onOptionSelected = onTypeSelected,
        label = { _ -> },
        icon = { journalTypeIcon(it) },
        iconOnly = true,
        modifier = Modifier.fillMaxWidth(),
        itemHeight = 48.dp,
        selectedContainerColorFor = { type -> journalTypeSelectedContainerColor(type, selectedContainerBase) },
        selectedContentColorFor = { selectedContentColor },
        iconTint = { type, _ -> journalTypeColor(type) },
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun JournalIntensitySelector(
    selectedIntensity: JournalIntensity?,
    onIntensitySelected: (JournalIntensity) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JournalIntensity.entries.forEach { intensity ->
            FilterChip(
                selected = selectedIntensity == intensity,
                onClick = { onIntensitySelected(intensity) },
                label = { Text(text = stringResource(intensity.labelRes())) }
            )
        }
    }
}

@Composable
private fun JournalPresetPill(
    preset: JournalInsulinPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            Color(preset.accentColor).copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(preset.accentColor), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun JournalInlineChip(
    entry: JournalEntry,
    unit: String,
    insulinPreset: JournalInsulinPreset?,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = insulinPreset?.let { Color(it.accentColor) } ?: journalTypeColor(entry.type)
    val inlineLabel = journalMarkerDetail(entry, insulinPreset, unit).ifBlank { entry.title }
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        shape = RoundedCornerShape(if (expanded) 16.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (expanded) 12.dp else 10.dp,
                vertical = if (expanded) 8.dp else 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = journalTypeIcon(entry.type),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (expanded) 16.dp else 14.dp)
            )
            Text(
                text = inlineLabel,
                style = if (expanded) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JournalEntryChip(
    entry: JournalEntry,
    unit: String,
    insulinPreset: JournalInsulinPreset?,
    onClick: () -> Unit
) {
    val tint = insulinPreset?.let { Color(it.accentColor) } ?: journalTypeColor(entry.type)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 156.dp, max = 220.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                color = tint.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = journalTypeIcon(entry.type),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = describeJournalEntry(entry, unit, insulinPreset),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.wrapContentWidth(Alignment.End)
            )
        }
    }
}

@Composable
private fun JournalMetaCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun JournalStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    label: String,
    suffix: String?,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onStep(-1) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                suffix = suffix?.let { { Text(it) } }
            )
            FilledTonalIconButton(
                onClick = { onStep(1) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
            }
        }
    }
}

private fun describeJournalEntry(
    entry: JournalEntry,
    unit: String,
    insulinPreset: JournalInsulinPreset?
): String {
    return when (entry.type) {
        JournalEntryType.INSULIN -> {
            val amount = entry.amount?.let(::formatFloatForEditor).orEmpty()
            val window = insulinPreset?.let {
                Applic.app.getString(
                    R.string.journal_active_window,
                    Applic.app.getString(R.string.minutes_short_format, it.onsetMinutes),
                    Applic.app.getString(R.string.minutes_short_format, it.durationMinutes)
                )
            }
            listOfNotNull("$amount U".takeIf { amount.isNotBlank() }, window, entry.note).joinToString(" · ")
        }

        JournalEntryType.CARBS -> {
            val amount = entry.amount?.let(::formatFloatForEditor).orEmpty()
            listOfNotNull("$amount g".takeIf { amount.isNotBlank() }, entry.note).joinToString(" · ")
        }

        JournalEntryType.FINGERSTICK -> {
            listOfNotNull(
                entry.glucoseValueMgDl?.let { formatGlucoseForEditor(it, unit) },
                entry.note
            ).joinToString(" · ")
        }

        JournalEntryType.ACTIVITY -> {
            listOfNotNull(
                entry.durationMinutes?.let { Applic.app.getString(R.string.minutes_short_format, it) },
                entry.intensity?.let { Applic.app.getString(it.labelRes()) },
                entry.note
            ).joinToString(" · ")
        }

        JournalEntryType.NOTE -> {
            entry.note ?: entry.title
        }
    }
}

private fun journalMarkerBadge(type: JournalEntryType): String {
    return when (type) {
        JournalEntryType.INSULIN -> "I"
        JournalEntryType.CARBS -> "C"
        JournalEntryType.FINGERSTICK -> "B"
        JournalEntryType.ACTIVITY -> "A"
        JournalEntryType.NOTE -> "N"
    }
}

private fun journalMarkerDetail(
    entry: JournalEntry,
    insulinPreset: JournalInsulinPreset?,
    unit: String
): String {
    return when (entry.type) {
        JournalEntryType.INSULIN -> {
            val amount = entry.amount?.let(::formatFloatForEditor).orEmpty()
            "$amount U".trim()
        }

        JournalEntryType.CARBS -> {
            val amount = entry.amount?.let(::formatFloatForEditor).orEmpty()
            "$amount g".trim()
        }

        JournalEntryType.FINGERSTICK -> {
            entry.glucoseValueMgDl?.let { formatGlucoseForEditor(it, unit) }.orEmpty()
        }

        JournalEntryType.ACTIVITY -> {
            entry.durationMinutes?.let { "${it}m" } ?: insulinPreset?.displayName.orEmpty()
        }

        JournalEntryType.NOTE -> {
            (entry.note ?: entry.title).take(10)
        }
    }
}

private fun formatGlucoseForEditor(glucoseMgDl: Float, unit: String): String {
    val value = if (GlucoseFormatter.isMmol(unit)) glucoseMgDl / 18.0182f else glucoseMgDl
    return if (GlucoseFormatter.isMmol(unit)) {
        DecimalFormat("0.#", DecimalFormatSymbols(Locale.getDefault())).format(value)
    } else {
        formatFloatForEditor(value)
    }
}

private fun parseGlucoseToMgDl(value: String, unit: String): Float? {
    val parsed = value.parseFloatOrNull() ?: return null
    return if (GlucoseFormatter.isMmol(unit)) parsed * 18.0182f else parsed
}

private fun formatFloatForEditor(value: Float): String {
    return if (abs(value - value.roundToInt()) < 0.001f) {
        value.roundToInt().toString()
    } else {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.getDefault())).format(value)
    }
}

private fun String.parseFloatOrNull(): Float? {
    return trim().replace(',', '.').toFloatOrNull()
}

private fun String.parseIntOrNull(): Int? {
    return trim().toIntOrNull()
}

private fun String.trimTrailingLabel(): String {
    return trim().trimEnd(':').trim()
}

private fun journalTypeIcon(type: JournalEntryType): ImageVector {
    return when (type) {
        JournalEntryType.INSULIN -> Icons.Default.Vaccines
        JournalEntryType.CARBS -> Icons.Default.Restaurant
        JournalEntryType.FINGERSTICK -> Icons.Default.Bloodtype
        JournalEntryType.ACTIVITY -> Icons.Default.DirectionsRun
        JournalEntryType.NOTE -> Icons.AutoMirrored.Filled.Label
    }
}

private fun JournalEntryType.labelRes(): Int {
    return when (this) {
        JournalEntryType.INSULIN -> R.string.journal_type_insulin
        JournalEntryType.CARBS -> R.string.carbo
        JournalEntryType.FINGERSTICK -> R.string.journal_type_fingerstick
        JournalEntryType.ACTIVITY -> R.string.journal_type_activity
        JournalEntryType.NOTE -> R.string.journal_type_note
    }
}

private fun JournalIntensity.labelRes(): Int {
    return when (this) {
        JournalIntensity.LIGHT -> R.string.journal_intensity_light
        JournalIntensity.MODERATE -> R.string.journal_intensity_moderate
        JournalIntensity.INTENSE -> R.string.journal_intensity_intense
    }
}

private fun adjustDecimalDraft(value: String, direction: Int, step: Float): String {
    val current = value.parseFloatOrNull() ?: 0f
    val next = (current + (direction * step)).coerceAtLeast(0f)
    return formatFloatForEditor(next)
}

private fun suggestedInsulinAmountForFraction(fraction: Float): String {
    val normalized = fraction.coerceIn(0f, 1f)
    val stepped = ((normalized * 20f) / 0.5f).roundToInt() * 0.5f
    return formatFloatForEditor(stepped.coerceAtLeast(0.5f))
}

private fun suggestedCarbAmountForFraction(fraction: Float): String {
    val normalized = fraction.coerceIn(0f, 1f)
    val stepped = ((normalized * 100f) / 5f).roundToInt() * 5f
    return formatFloatForEditor(stepped.coerceAtLeast(5f))
}

private fun adjustIntegerDraft(value: String, delta: Int, minValue: Int = 0): String {
    val current = value.parseIntOrNull() ?: 0
    return (current + delta).coerceAtLeast(minValue).toString()
}

private fun mergeJournalDate(currentTimestamp: Long, selectedDayTimestamp: Long): Long {
    val current = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
    val selected = Calendar.getInstance().apply { timeInMillis = selectedDayTimestamp }
    current.set(Calendar.YEAR, selected.get(Calendar.YEAR))
    current.set(Calendar.MONTH, selected.get(Calendar.MONTH))
    current.set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
    return current.timeInMillis
}

private fun mergeJournalTime(currentTimestamp: Long, selectedTime: Pair<Int, Int>): Long {
    return Calendar.getInstance().apply {
        timeInMillis = currentTimestamp
        set(Calendar.HOUR_OF_DAY, selectedTime.first)
        set(Calendar.MINUTE, selectedTime.second)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
