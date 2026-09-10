package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.theme.KromiumColors

@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KromiumColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(480.dp)
                .border(1.dp, KromiumColors.Error.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = KromiumColors.Surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Initialization Error",
                    tint = KromiumColors.Error,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Initialization Error",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = KromiumColors.TextPrimary
                )

                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = KromiumColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Cyan)
                ) {
                    Text("Retry Initialization", color = KromiumColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
