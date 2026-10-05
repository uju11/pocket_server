package com.remotemedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ui.theme.*

@Composable
fun CreditsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Developer Profile Card ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(TacticalOrange.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, TacticalOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🚀", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("PROJECT ARCHITECT & LEAD", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Cotton Candy", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Autonomous Systems & Embedded Android", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = StatusGreen)

            Spacer(modifier = Modifier.height(14.dp))

            // Profile Links Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GitHub Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { openUrl("https://github.com/uju11") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🐙", fontSize = 12.sp)
                        Text("GITHUB", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                }

                // LinkedIn Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { openUrl("https://www.linkedin.com/in/ujjwal-mv") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💼", fontSize = 12.sp)
                        Text("LINKEDIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                    }
                }
            }
        }

        // ── Buy Me A Coffee Support Card ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalYellow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .clickable { openUrl("https://buymeacoffee.com") }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("☕", fontSize = 24.sp)
                    Column {
                        Text("SUPPORT POCKETNODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Buy me a coffee to keep updates rolling", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .background(TacticalYellow, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("DONATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalBg)
                }
            }
        }

        // ── Tech Stack & Architecture Specs ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("// SYSTEM_SPECIFICATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(2.dp))

            SpecRow("CORE ENGINE", "Kotlin Coroutines + Flow")
            SpecRow("UI FRAMEWORK", "Jetpack Compose + Material3")
            SpecRow("MEDIA SERVER", "Jellyfin Embedded Transcoding Engine")
            SpecRow("BROADCASTER", "Cling UPnP / DLNA Broadcaster")
            SpecRow("TORRENT DAEMON", "Libtorrent4j + FrostWire Engine")
            SpecRow("WEB SERVICES", "Ktor Embedded Netty HTTP Server")
            SpecRow("TRANSCODING", "Android MediaCodec Hardware Acceleration")
            SpecRow("SANDBOX VAULT", "Scoped Storage + .nomedia Sentinel")
        }

        // ── Open Source & Licensing ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Column {
                Text("OPEN SOURCE ARCHITECTURE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "PocketNode is built entirely with open-source technologies. No telemetry, no cloud dependency, completely decentralized and autonomous.",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
    }
}
