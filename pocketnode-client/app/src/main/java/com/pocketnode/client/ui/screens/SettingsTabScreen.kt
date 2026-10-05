package com.pocketnode.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.UserRole
import com.pocketnode.client.ui.components.RoleBadge
import com.pocketnode.client.ui.components.TacticalHeader
import com.pocketnode.client.ui.theme.*

@Composable
fun SettingsTabScreen(
    currentRole: UserRole,
    onRoleChange: (UserRole) -> Unit,
    incognitoMode: Boolean,
    onToggleIncognito: () -> Unit,
    onOpenStorageDiagnostics: () -> Unit,
    onToast: (String) -> Unit
) {
    var selectedSubTab by remember { mutableStateOf("APP") } // APP, SERVER, PERMISSIONS
    var hardwareAccel by remember { mutableStateOf(true) }
    var preferAv1 by remember { mutableStateOf(true) }
    var directPlayDolby by remember { mutableStateOf(true) }
    var pushToTalkEnabled by remember { mutableStateOf(true) }
    var audioDuckingLevel by remember { mutableStateOf(25f) } // 25% movie audio when speaking
    var micSensitivity by remember { mutableStateOf(80f) }
    var liveEmojiReactions by remember { mutableStateOf(true) }
    var autoPruneCache by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
    ) {
        // Sticky Header
        TacticalHeader(
            currentRole = currentRole,
            incognitoMode = incognitoMode,
            onToggleIncognito = onToggleIncognito,
            onRoleClick = {
                val next = when (currentRole) {
                    UserRole.ADMIN -> UserRole.GUEST
                    UserRole.GUEST -> UserRole.KIDS
                    UserRole.KIDS -> UserRole.ADMIN
                }
                onRoleChange(next)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Screen Title & Sub-tabs (App, Server, Perms)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM SETTINGS",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NODE CONFIGURATION & ENDPOINT TUNING",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Role switcher pill
                RoleBadge(role = currentRole)
            }

            // Sub tabs switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("APP", "SERVER (ADMIN)", "PERMISSIONS").forEach { tab ->
                    val isSelected = selectedSubTab.startsWith(tab.take(3))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) TacticalAmber.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                if (tab.startsWith("SERVER") && currentRole != UserRole.ADMIN) {
                                    onToast("Admin privileges required for Server Configuration")
                                } else {
                                    selectedSubTab = tab
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) TacticalAmber else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Switch display based on tab
            when {
                selectedSubTab.startsWith("SERVER") && currentRole == UserRole.ADMIN -> {
                    ServerSettingsSection(onToast = onToast)
                }
                selectedSubTab.startsWith("PERM") -> {
                    PermissionsSettingsSection(currentRole = currentRole, onToast = onToast)
                }
                else -> {
                    // APP Settings (Matching Image 4)
                    AppSettingsSection(
                        hardwareAccel = hardwareAccel,
                        onToggleHardwareAccel = { hardwareAccel = !hardwareAccel },
                        preferAv1 = preferAv1,
                        onTogglePreferAv1 = { preferAv1 = !preferAv1 },
                        directPlayDolby = directPlayDolby,
                        onToggleDirectPlayDolby = { directPlayDolby = !directPlayDolby },
                        pushToTalkEnabled = pushToTalkEnabled,
                        onTogglePushToTalk = { pushToTalkEnabled = !pushToTalkEnabled },
                        audioDuckingLevel = audioDuckingLevel,
                        onAudioDuckingChange = { audioDuckingLevel = it },
                        micSensitivity = micSensitivity,
                        onMicSensitivityChange = { micSensitivity = it },
                        liveEmojiReactions = liveEmojiReactions,
                        onToggleLiveEmoji = { liveEmojiReactions = !liveEmojiReactions },
                        autoPruneCache = autoPruneCache,
                        onToggleAutoPrune = { autoPruneCache = !autoPruneCache },
                        onOpenStorageDiagnostics = onOpenStorageDiagnostics,
                        onToast = onToast
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun AppSettingsSection(
    hardwareAccel: Boolean,
    onToggleHardwareAccel: () -> Unit,
    preferAv1: Boolean,
    onTogglePreferAv1: () -> Unit,
    directPlayDolby: Boolean,
    onToggleDirectPlayDolby: () -> Unit,
    pushToTalkEnabled: Boolean,
    onTogglePushToTalk: () -> Unit,
    audioDuckingLevel: Float,
    onAudioDuckingChange: (Float) -> Unit,
    micSensitivity: Float,
    onMicSensitivityChange: (Float) -> Unit,
    liveEmojiReactions: Boolean,
    onToggleLiveEmoji: () -> Unit,
    autoPruneCache: Boolean,
    onToggleAutoPrune: () -> Unit,
    onOpenStorageDiagnostics: () -> Unit,
    onToast: (String) -> Unit
) {
    // Section 1: Playback Engine
    SettingsGroupHeader(title = "01 // PLAYBACK ENGINE & CODECS")
    TacticalSettingToggle(
        title = "Hardware Acceleration",
        subtitle = "MediaCodec pipeline with 10-bit HDR tone-mapping",
        checked = hardwareAccel,
        onCheckedChange = { onToggleHardwareAccel() }
    )
    TacticalSettingToggle(
        title = "Force Direct Play (Bypass Transcode)",
        subtitle = "Always stream raw container (MKV/MP4) without server re-encoding",
        checked = preferAv1,
        onCheckedChange = { onTogglePreferAv1() }
    )
    TacticalSettingToggle(
        title = "Dolby Atmos / DTS-HD Passthrough",
        subtitle = "Bitstream spatial audio directly to external soundbar or receiver",
        checked = directPlayDolby,
        onCheckedChange = { onToggleDirectPlayDolby() }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Section 2: Storage & Cache (Matching Image 4)
    SettingsGroupHeader(title = "02 // STORAGE, CACHE & MEMORY")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DEVICE CACHE UTILIZATION",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "5.2 GB used of 12.0 GB allocated limit",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onOpenStorageDiagnostics,
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DIAGNOSE & PURGE",
                        color = TacticalAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { 5.2f / 12.0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TacticalAmber,
                trackColor = ObsidianSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pre-buffer chunks: 4.1 GB", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Thumbnails & subs: 1.1 GB", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }

    TacticalSettingToggle(
        title = "Auto-Prune Cold Cache",
        subtitle = "Automatically evict finished streams older than 48 hours",
        checked = autoPruneCache,
        onCheckedChange = { onToggleAutoPrune() }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Section 3: Party Intercom & Audio (SyncPlay Walkie-Talkie)
    SettingsGroupHeader(title = "03 // PARTY INTERCOM & WALKIE-TALKIE")
    TacticalSettingToggle(
        title = "Walkie-Talkie Push-to-Talk (PTT)",
        subtitle = "Real-time Opus voice chat over mesh websocket during group watch",
        checked = pushToTalkEnabled,
        onCheckedChange = { onTogglePushToTalk() }
    )

    // Slider: Audio Ducking
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Film Audio Ducking During PTT",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${audioDuckingLevel.toInt()}% Vol",
                    color = TacticalCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Automatically drops movie volume when friends speak",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Slider(
                value = audioDuckingLevel,
                onValueChange = onAudioDuckingChange,
                valueRange = 0f..50f,
                colors = SliderDefaults.colors(
                    thumbColor = TacticalCyan,
                    activeTrackColor = TacticalCyan,
                    inactiveTrackColor = ObsidianSurface
                )
            )
        }
    }

    // Slider: Mic Sensitivity
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Microphone Noise Gate",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${micSensitivity.toInt()}%",
                    color = TacticalGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Suppresses living room echo and background noise",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Slider(
                value = micSensitivity,
                onValueChange = onMicSensitivityChange,
                valueRange = 20f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = TacticalGreen,
                    activeTrackColor = TacticalGreen,
                    inactiveTrackColor = ObsidianSurface
                )
            )
        }
    }

    TacticalSettingToggle(
        title = "Floating Flying Emoji Bar",
        subtitle = "Render live reactions during party play with physics float-up",
        checked = liveEmojiReactions,
        onCheckedChange = { onToggleLiveEmoji() }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Section 4: Audit & Diagnostics
    SettingsGroupHeader(title = "04 // AUDIT & PROTOCOL CONTROLS")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onToast("Local playback history cleared") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurface),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Text("CLEAR HISTORY", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Button(
            onClick = { onToast("Reset client certificates and reconnecting...") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalRed.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalRed.copy(alpha = 0.5f))
        ) {
            Text("RESET TOKEN", color = TacticalRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ServerSettingsSection(onToast: (String) -> Unit) {
    SettingsGroupHeader(title = "ADMIN CONTROL // POCKETNODE CORE DAEMON")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PRIMARY SERVER INSTANCE", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("PocketNode-HomeMaster", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("http://192.168.1.100:8080", color = TacticalCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("ONLINE", color = TacticalGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricPill(label = "ACTIVE SESSIONS", value = "3 Devices")
                MetricPill(label = "TX BANDWIDTH", value = "42.8 Mbps")
                MetricPill(label = "CPU LOAD", value = "14.2%")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    TacticalSettingToggle(
        title = "Advertise Server on mDNS / LAN",
        subtitle = "Zero-config auto discovery for phones, Shield TVs, and smart displays",
        checked = true,
        onCheckedChange = { onToast("Updated mDNS broadcast flag") }
    )

    TacticalSettingToggle(
        title = "Allow Remote Guest Ingestion",
        subtitle = "Allow friends to submit download requests to the transmission queue",
        checked = true,
        onCheckedChange = { onToast("Guest ingestion permission toggled") }
    )

    TacticalSettingToggle(
        title = "Hardware Transcoder nvenc/vaapi",
        subtitle = "Transcode on-the-fly when bandwidth drops below 10 Mbps",
        checked = false,
        onCheckedChange = { onToast("Transcoder toggled") }
    )
}

@Composable
private fun PermissionsSettingsSection(
    currentRole: UserRole,
    onToast: (String) -> Unit
) {
    SettingsGroupHeader(title = "ACCESS CONTROL MATRIX")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ROLE PERMISSION MAP",
                color = TacticalAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow("ADMIN (a)", "Full host control, download queue, server metrics, user management", true)
            PermissionRow("GUEST (g)", "Watch all media, initiate party, submit download requests, subtitles", true)
            PermissionRow("KIDS (k)", "Filtered animated/family media only, spectators disabled, downloads disabled", true)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsGroupHeader(title = "LOCAL DEVICE PERMISSIONS")
    TacticalSettingToggle(
        title = "Microphone Access (Voice Intercom)",
        subtitle = "Required for SyncPlay push-to-talk walkie-talkie",
        checked = true,
        onCheckedChange = { onToast("Microphone permissions granted") }
    )
    TacticalSettingToggle(
        title = "Nearby Wi-Fi Devices Permission",
        subtitle = "Required for zero-config mesh discovery & direct device push",
        checked = true,
        onCheckedChange = { onToast("Nearby devices permissions granted") }
    )
    TacticalSettingToggle(
        title = "External Storage / Save Offline",
        subtitle = "Allows direct encrypted caching to internal SD/UFS storage",
        checked = true,
        onCheckedChange = { onToast("Storage permissions granted") }
    )
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TacticalSettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBase,
                    checkedTrackColor = TacticalAmber,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianSurface
                )
            )
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PermissionRow(role: String, desc: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (enabled) TacticalGreen else TacticalRed,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(role, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(desc, color = TextSecondary, fontSize = 10.sp)
        }
    }
}
