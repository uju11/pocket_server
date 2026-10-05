package com.remotemedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ui.theme.*

@Composable
fun WebTabScreen() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        PocketCard {
            SectionHeader(title = "WEB_PANEL", badge = "PLACEHOLDER", badgeColor = TacticalMuted)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌐", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("// WEB_INTERFACE", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Not yet implemented", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
                }
            }
        }
    }
}
