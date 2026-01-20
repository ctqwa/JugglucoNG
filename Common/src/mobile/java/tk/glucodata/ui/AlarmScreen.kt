package tk.glucodata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R

@Composable
fun AlarmScreen(
    glucoseBitmap: androidx.compose.ui.graphics.ImageBitmap,
    arrowBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    alarmType: String, // "LOW", "HIGH", "URGENT", etc.
    message: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    // Dark Mode Theme Colors
    val isLow = alarmType.contains("LOW", ignoreCase = true)
    val isHigh = alarmType.contains("HIGH", ignoreCase = true)

    // Background Gradient: Deep dark with subtle color hint
    val gradientColors = when {
        isLow -> listOf(Color(0xFF1A0000), Color(0xFF000000)) // Deep Red to Black
        isHigh -> listOf(Color(0xFF1A1200), Color(0xFF000000)) // Deep Orange to Black
        else -> listOf(Color(0xFF121212), Color(0xFF000000))
    }
    
    val accentColor = when {
        isLow -> Color(0xFFFF5252) // Red Accent
        isHigh -> Color(0xFFFFB74D) // Orange Accent
        else -> Color(0xFFE0E0E0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Content Container
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Fullscreen Alert Label (Dominant)
            // Using IBM Plex Sans Light (300)
            val ibmPlexSansFamily = androidx.compose.ui.text.font.FontFamily(
                androidx.compose.ui.text.font.Font(R.font.ibm_plex_sans_var, androidx.compose.ui.text.font.FontWeight.Light)
            )

            // Auto-sizing text or just very large text
            Text(
                text = message.uppercase(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 80.sp, // Massive
                    fontWeight = FontWeight.Light,
                    fontFamily = ibmPlexSansFamily,
                    letterSpacing = 12.sp, // Wide spacing for uppercase
                    lineHeight = 90.sp // Proper line spacing
                ),
                color = accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 2. Secondary Data (Glucose + Arrow) - Standard Size (Restored)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                 // Glucose Value (Restored to 140dp)
                 androidx.compose.foundation.Image(
                     bitmap = glucoseBitmap,
                     contentDescription = "Glucose Value",
                     modifier = Modifier.height(140.dp), 
                     contentScale = androidx.compose.ui.layout.ContentScale.Fit
                 )

                if (arrowBitmap != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.foundation.Image(
                        bitmap = arrowBitmap,
                        contentDescription = "Trend Arrow",
                        modifier = Modifier.height(80.dp), // Restored to 80dp
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
        }

        // Actions
        // Spacer(modifier = Modifier.weight(1f)) // Removed to let Box fill space
        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Snooze (Secondary)
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(end = 12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(32.dp) // Pill shape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_snooze),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Snooze",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Dismiss (Primary - Slide to dismiss in future, button for now)
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(start = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                 shape = RoundedCornerShape(32.dp)
            ) {
                 Icon(
                    painter = painterResource(id = R.drawable.ic_dismiss),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
