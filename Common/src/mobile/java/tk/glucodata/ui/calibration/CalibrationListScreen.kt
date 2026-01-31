package tk.glucodata.ui.calibration

import androidx.compose.animation.AnimatedVisibility
import tk.glucodata.aidex.AiDexProbe
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.data.calibration.CalibrationEntity
import tk.glucodata.data.calibration.CalibrationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationListScreen(
    navController: NavController,
    isMmol: Boolean,
    viewMode: Int = 0, // Default to 0 (Auto)
    onAdd: () -> Unit,
    onEdit: (CalibrationEntity) -> Unit
) {
    val isRawMode = viewMode == 1 || viewMode == 3
    val modeTitle = if (isRawMode) "Raw" else "Auto"
    
    // Collect Data
    val allCalibrations by produceState(initialValue = emptyList<CalibrationEntity>()) {
        withContext(Dispatchers.IO) {
            CalibrationManager.getCalibrationsFlow()?.collect { list ->
                 value = list.sortedByDescending { c -> c.timestamp }
            }
        }
    }
    
    // Filter by mode
    val calibrations = allCalibrations.filter { it.isRawMode == isRawMode }

    // Toggle State
    val isEnabledForRaw by CalibrationManager.isEnabledForRaw.collectAsState()
    val isEnabledForAuto by CalibrationManager.isEnabledForAuto.collectAsState()
    val isCalibrationEnabled = if (isRawMode) isEnabledForRaw else isEnabledForAuto

    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    
    // Modal state for viewing/editing calibration
    var selectedCalibration by remember { mutableStateOf<CalibrationEntity?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    
    // Clear confirmation
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }

    // Exit selection mode when no items selected
    LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                // Selection Mode TopBar
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        // Select All
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == calibrations.size) {
                                emptySet()
                            } else {
                                calibrations.map { it.id }.toSet()
                            }
                        }) {
                            Text(if (selectedIds.size == calibrations.size) "Deselect all" else "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(
                            "Calibration ($modeTitle)",
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { tk.glucodata.aidex.AiDexProbe.getInstance().startProbe() }) {
                            Text("Probe")
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) 
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main List Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    end = 16.dp, 
                    top = 16.dp, 
                    bottom = 100.dp // Space for floating toolbar
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Enable Toggle (not shown in selection mode)
                if (!isSelectionMode) {
                    item {
                        EnableCalibrationCard(
                            isEnabled = isCalibrationEnabled,
                            onToggle = { enabled ->
                                CalibrationManager.setEnabledForMode(isRawMode, enabled)
                            }
                        )
                    }
                    
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // 2. Calibration Items with Swipe
                items(calibrations, key = { it.id }) { cal ->
                    SwipeableCalibrationRow(
                        modifier = Modifier.animateItem(),
                        cal = cal,
                        isMmol = isMmol,
                        isRawMode = isRawMode,
                        dateFormatter = dateFormatter,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedIds.contains(cal.id),
                        onTap = {
                            if (isSelectionMode) {
                                // Toggle selection
                                selectedIds = if (selectedIds.contains(cal.id)) {
                                    selectedIds - cal.id
                                } else {
                                    selectedIds + cal.id
                                }
                            } else {
                                // Open modal for viewing/editing
                                selectedCalibration = cal
                                showEditSheet = true
                            }
                        },
                        onLongPress = {
                            // Enter selection mode
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds = setOf(cal.id)
                            }
                        },
                        onDelete = { 
                            scope.launch {
                                val backup = cal
                                CalibrationManager.deleteCalibration(cal)
                                
                                val result = snackbarHostState.showSnackbar(
                                    message = "Calibration deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                
                                if (result == SnackbarResult.ActionPerformed) {
                                    CalibrationManager.addCalibration(
                                        timestamp = backup.timestamp,
                                        sensorValue = backup.sensorValue,
                                        sensorValueRaw = backup.sensorValueRaw,
                                        userValue = backup.userValue,
                                        sensorId = backup.sensorId,
                                        isRawMode = backup.isRawMode
                                    )
                                }
                            }
                        },
                        onToggleDisable = { 
                            scope.launch { 
                                CalibrationManager.updateCalibration(cal.copy(isEnabled = !cal.isEnabled))
                            }
                        }
                    )
                }
            }

            // 3. Floating Toolbar (Bottom)
            AnimatedVisibility(
                visible = !isSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                FloatingActionToolbar(
                    showClearAll = calibrations.isNotEmpty(),
                    onClearAll = { showClearConfirmation = true },
                    onAdd = onAdd
                )
            }
            
            // Selection Mode Toolbar
            AnimatedVisibility(
                visible = isSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                SelectionModeToolbar(
                    selectedCount = selectedIds.size,
                    onDelete = { showBulkDeleteConfirmation = true },
                    onDisable = {
                        scope.launch {
                            val toUpdate = calibrations.filter { selectedIds.contains(it.id) }
                            toUpdate.forEach { cal ->
                                CalibrationManager.updateCalibration(cal.copy(isEnabled = false))
                            }
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }
                    },
                    onEnable = {
                        scope.launch {
                            val toUpdate = calibrations.filter { selectedIds.contains(it.id) }
                            toUpdate.forEach { cal ->
                                CalibrationManager.updateCalibration(cal.copy(isEnabled = true))
                            }
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }
                    }
                )
            }
            
            // Bulk Delete Confirmation Dialog
            if (showBulkDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showBulkDeleteConfirmation = false },
                    title = { Text("Delete ${selectedIds.size} calibrations?") },
                    text = { Text("This action can be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBulkDeleteConfirmation = false
                                scope.launch {
                                    val toDelete = calibrations.filter { selectedIds.contains(it.id) }
                                    toDelete.forEach { CalibrationManager.deleteCalibration(it) }
                                    
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${toDelete.size} calibrations deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    
                                    if (result == SnackbarResult.ActionPerformed) {
                                        toDelete.forEach { cal ->
                                            CalibrationManager.addCalibration(
                                                timestamp = cal.timestamp,
                                                sensorValue = cal.sensorValue,
                                                sensorValueRaw = cal.sensorValueRaw,
                                                userValue = cal.userValue,
                                                sensorId = cal.sensorId,
                                                isRawMode = cal.isRawMode
                                            )
                                        }
                                    }
                                    
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                }
                            }
                        ) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBulkDeleteConfirmation = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
    
    // Clear Options Bottom Sheet
    if (showClearConfirmation) {
        ClearOptionsBottomSheet(
            onDismiss = { showClearConfirmation = false },
            onClearDisabled = {
                scope.launch {
                    val disabled = calibrations.filter { !it.isEnabled }
                    disabled.forEach { CalibrationManager.deleteCalibration(it) }
                    snackbarHostState.showSnackbar(
                        message = "${disabled.size} disabled calibrations cleared",
                        duration = SnackbarDuration.Short
                    )
                }
                showClearConfirmation = false
            },
            onClearAll = {
                scope.launch {
                    val backup = calibrations
                    CalibrationManager.clearAll()
                    
                    val result = snackbarHostState.showSnackbar(
                        message = "All calibrations cleared",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    
                    if (result == SnackbarResult.ActionPerformed) {
                        CalibrationManager.restoreAll(backup)
                    }
                }
                showClearConfirmation = false
            },
            disabledCount = calibrations.count { !it.isEnabled },
            totalCount = calibrations.size
        )
    }
    
    // Edit via CalibrationBottomSheet
    if (showEditSheet && selectedCalibration != null) {
        CalibrationBottomSheet(
            onDismiss = { 
                showEditSheet = false
                selectedCalibration = null
            },
            initialValueAuto = selectedCalibration!!.sensorValue,
            initialValueRaw = selectedCalibration!!.sensorValueRaw,
            initialTimestamp = selectedCalibration!!.timestamp,
            glucoseHistory = emptyList(), // Will use cached data
            isMmol = isMmol,
            viewMode = if (isRawMode) 1 else 0,
            onNavigateToHistory = { /* Already on history */ }
        )
    }
}


@Composable
private fun EnableCalibrationCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!isEnabled) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Calibration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Adjust sensor readings using these values",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Swipeable calibration row using Material 3 SwipeToDismissBox
 * - Swipe LEFT to DELETE (red background, trash icon)
 * - Swipe RIGHT to DISABLE/ENABLE (secondary/tertiary background)
 * - Tap to open modal
 * - Long press to enter selection mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCalibrationRow(
    modifier: Modifier = Modifier,
    cal: CalibrationEntity,
    isMmol: Boolean,
    isRawMode: Boolean,
    dateFormatter: SimpleDateFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleDisable: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // In selection mode, just show the card without swipe
    if (isSelectionMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() }
                    )
                }
        ) {
            CalibrationItemContent(
                cal = cal,
                isMmol = isMmol,
                isRawMode = isRawMode,
                dateFormatter = dateFormatter,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected
            )
        }
        return
    }
    
    // Key on isEnabled so state resets when toggled
    val dismissState = key(cal.isEnabled) {
        rememberSwipeToDismissBoxState(
            positionalThreshold = { it * 0.25f }, // 25% threshold for easier swipe
            confirmValueChange = { dismissValue ->
                when (dismissValue) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        // Swipe left = Delete
                        onDelete()
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        // Swipe right = Toggle disable
                        onToggleDisable()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )
    }
    
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.StartToEnd -> if (cal.isEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                SwipeToDismissBoxValue.StartToEnd -> if (cal.isEnabled) Icons.Filled.Close else Icons.Filled.Check
                else -> Icons.Filled.Delete
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        content = {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onLongPress = { onLongPress() }
                        )
                    }
            ) {
                CalibrationItemContent(
                    cal = cal,
                    isMmol = isMmol,
                    isRawMode = isRawMode,
                    dateFormatter = dateFormatter,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected
                )
            }
        }
    )
}

@Composable
private fun CalibrationItemContent(
    cal: CalibrationEntity,
    isMmol: Boolean,
    isRawMode: Boolean,
    dateFormatter: SimpleDateFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean
) {
    val sFmt = if (isMmol) "%.1f" else "%.0f"
    
    // Determine primary and secondary values based on mode
    val primaryValue = if (isRawMode) cal.sensorValueRaw else cal.sensorValue
    val secondaryValue = if (isRawMode) cal.sensorValue else cal.sensorValueRaw
    val secondaryLabel = if (isRawMode) "Auto" else "Raw"
    val hasSecondary = secondaryValue != 0f && secondaryValue != primaryValue
    
    // Disabled state: reduce opacity
    val cardAlpha = if (cal.isEnabled) 1f else 0.6f
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            !cal.isEnabled -> MaterialTheme.colorScheme.surfaceDim
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .animateContentSize() // Smooth content shift when checkbox appears
                .padding(16.dp)
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox with standard M3 scale animation
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Row {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            
            // Values & Info
            Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = cardAlpha }) {
                // Primary: Sensor → Calibrated
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.getDefault(), sFmt, primaryValue),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (cal.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.getDefault(), sFmt, cal.userValue),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (cal.isEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata: Date | Secondary value | Disabled badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormatter.format(Date(cal.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (hasSecondary) {
                        Text(
                            text = " · $secondaryLabel: ${String.format(Locale.getDefault(), sFmt, secondaryValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!cal.isEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Disabled", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingActionToolbar(
    showClearAll: Boolean,
    onClearAll: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showClearAll) {
            FilledTonalButton(
                onClick = onClearAll,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp, 
                    topEnd = 4.dp,
                    bottomEnd = 4.dp
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                bottomStart = 4.dp,
                topEnd = 28.dp,
                bottomEnd = 28.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Calibrate", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SelectionModeToolbar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Disable
            FilledTonalButton(
                onClick = onDisable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Disable", style = MaterialTheme.typography.labelLarge)
            }
            
            // Enable
            FilledTonalButton(
                onClick = onEnable,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enable", style = MaterialTheme.typography.labelLarge)
            }
            
            // Delete
            FilledTonalButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Bottom sheet for clear options following M3 guidelines
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearOptionsBottomSheet(
    onDismiss: () -> Unit,
    onClearDisabled: () -> Unit,
    onClearAll: () -> Unit,
    disabledCount: Int,
    totalCount: Int
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Clear Calibrations",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Choose what to clear:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Clear Disabled Only
            if (disabledCount > 0) {
                Surface(
                    onClick = onClearDisabled,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Clear disabled only",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$disabledCount disabled calibration${if (disabledCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Clear All
            Surface(
                onClick = onClearAll,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "$totalCount calibration${if (totalCount > 1) "s" else ""} will be removed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Cancel
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
