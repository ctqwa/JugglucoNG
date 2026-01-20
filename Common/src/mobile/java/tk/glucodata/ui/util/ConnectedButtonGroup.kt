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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
                 Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp), // Reduce padding effectively to fit text
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon logic: Respect caller provided icon.
                    // If caller wants an icon for selected state, they return it.
                    val customIcon = icon?.invoke(option)
                    if (customIcon != null) {
                         Icon(
                            imageVector = customIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // Explicit Typography container to ensure consistency
                    androidx.compose.material3.ProvideTextStyle(
                         value = MaterialTheme.typography.labelLarge
                    ) {
                        label(option)
                    }
                }
            }
        }
    }
}
