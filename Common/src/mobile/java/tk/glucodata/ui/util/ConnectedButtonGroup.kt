package tk.glucodata.ui.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A custom implementation of a Connected Button Group, compliant with Material 3 Expressive.
 * Replaces the deprecated SegmentedButton.
 * 
 * Specs:
 * - Shared outer container with border.
 * - Items connected (no spacing).
 * - Vertical dividers between items (hidden when adjacent to selection).
 * - Selected item filled.
 */
@Composable
fun <T> ConnectedButtonGroup(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.extraLarge,
    containerColorSelected: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColorSelected: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColorUnselected: Color = Color.Transparent,
    contentColorUnselected: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = shape,
        color = containerColorUnselected,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption
                
                // Item Container
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = if (isSelected) containerColorSelected else Color.Transparent,
                    contentColor = if (isSelected) contentColorSelected else contentColorUnselected,
                    onClick = { onOptionSelected(option) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        label(option)
                    }
                }

                // Divider (Placed between items)
                if (index < options.lastIndex) {
                    val nextSelected = options[index + 1] == selectedOption
                    // Hide divider if current or next is selected (Standard M3 Segmented look)
                    // The filled selection state provides enough contrast.
                    val isDividerVisible = !isSelected && !nextSelected
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(if (isDividerVisible) borderColor else Color.Transparent)
                    )
                }
            }
        }
    }
}
