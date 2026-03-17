package tk.glucodata.ui.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A Connected Button Group with M3 Expressive shape morphing.
 * 
 * - Items behave as a group but are visually distinct (spaced by 2dp).
 * - Shapes animate based on selection state and position:
 *   - Selected: Fully rounded (Pill).
 *   - Unselected (Start): Rounded Start, Squared End.
 *   - Unselected (Middle): Squared both sides.
 *   - Unselected (End): Squared Start, Rounded End.
 * 
 * @param itemHeight Default 48.dp
 * @param spacing Default 2.dp
 */
@Composable
fun <T> ConnectedButtonGroup(
    options: List<T>,
    selectedOption: T? = null,
    selectedOptions: List<T> = emptyList(),
    onOptionSelected: (T) -> Unit,
    label: @Composable (T) -> Unit,
    labelText: ((T) -> String)? = null,
    icon: (@Composable (T) -> ImageVector?)? = null,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    itemHeight: Dp = 40.dp,
    spacing: Dp = 2.dp,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, // Slightly darker than surface for contrast
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .selectableGroup()
            .height(itemHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = if (multiSelect) selectedOptions.contains(option) else option == selectedOption
            
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) selectedContainerColor else unselectedContainerColor,
                label = "containerColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
                label = "contentColor"
            )
            
            // Shape Logic
            // Full radius (50%) for rounded sides, meaningful for 48dp height -> 24dp
            // "Square" side isn't sharp 0dp in M3 usually, often has a tiny radius (e.g. 4dp) or 0.
            // Let's use 0% for square inner connections to look "Connected" but separated by space.
            val fullRadiusPercent = 50
            val smallRadiusPercent = 16 // Slight rounding for "squared" edges looks more refined, or 0 for strict. Let's go with 10% for a "tile" look or 0 for "brick". User said "squared off". Let's stick to a very small percent or 0.
            // User photo suggests quite square inner edges. Let's use 4% for "Small" and 50% for "Full".
            
            // Start Corners
            val targetTopStart = if (isSelected || index == 0) fullRadiusPercent else smallRadiusPercent
            val targetBottomStart = if (isSelected || index == 0) fullRadiusPercent else smallRadiusPercent
            
            // End Corners
            val targetTopEnd = if (isSelected || index == options.lastIndex) fullRadiusPercent else smallRadiusPercent
            val targetBottomEnd = if (isSelected || index == options.lastIndex) fullRadiusPercent else smallRadiusPercent

            val topStart by animateIntAsState(targetTopStart, label = "topStart")
            val bottomStart by animateIntAsState(targetBottomStart, label = "bottomStart")
            val topEnd by animateIntAsState(targetTopEnd, label = "topEnd")
            val bottomEnd by animateIntAsState(targetBottomEnd, label = "bottomEnd")

            Surface(
                onClick = { onOptionSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .height(itemHeight), // Fill container height explicitly
                shape = RoundedCornerShape(
                    topStartPercent = topStart,
                    topEndPercent = topEnd,
                    bottomEndPercent = bottomEnd,
                    bottomStartPercent = bottomStart
                ),
                color = containerColor,
                contentColor = contentColor,
                border = null 
            ) {
                var availableWidthPx by remember(option) { mutableIntStateOf(0) }
                val customIcon = icon?.invoke(option)
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val availableWidth = with(density) { availableWidthPx.toDp() }
                val labelTextStyle: TextStyle = if (labelText != null && availableWidthPx > 0) {
                    val horizontalPadding = 4.dp
                    val iconAllowance = if (customIcon != null) 26.dp else 0.dp
                    val textWidthPx = with(density) { (availableWidth - horizontalPadding - iconAllowance).coerceAtLeast(24.dp).toPx() }
                    listOf(
                        MaterialTheme.typography.labelLarge,
                        MaterialTheme.typography.labelMedium,
                        MaterialTheme.typography.labelSmall
                    ).firstOrNull { style ->
                        options.maxOfOrNull { item ->
                            textMeasurer.measure(
                                text = AnnotatedString(labelText(item)),
                                style = style,
                                maxLines = 1
                            ).size.width
                        }?.let { it <= textWidthPx } == true
                    } ?: MaterialTheme.typography.labelSmall
                } else {
                    when {
                        availableWidthPx == 0 -> MaterialTheme.typography.labelLarge
                        availableWidth >= 92.dp -> MaterialTheme.typography.labelLarge
                        availableWidth >= 72.dp -> MaterialTheme.typography.labelMedium
                        else -> MaterialTheme.typography.labelSmall
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .onSizeChanged { availableWidthPx = it.width }
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (customIcon != null) {
                        Icon(
                            imageVector = customIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(if (availableWidth >= 72.dp) 8.dp else 4.dp))
                    }

                    if (labelText != null) {
                        Text(
                            text = labelText(option),
                            style = labelTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        androidx.compose.material3.ProvideTextStyle(value = labelTextStyle) {
                            label(option)
                        }
                    }
                }
            }
        }
    }
}
