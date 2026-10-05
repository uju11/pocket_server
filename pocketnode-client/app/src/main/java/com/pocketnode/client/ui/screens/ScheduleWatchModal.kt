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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.ClientRepository
import com.pocketnode.client.ui.theme.*

@Composable
fun ScheduleWatchModal(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    var selectedDay by remember { mutableStateOf("TODAY") }
    var selectedTimeSlot by remember { mutableStateOf("08:30 PM") }
    var autoSendInvite by remember { mutableStateOf(true) }

    var walkieTalkieEnabled by remember { mutableStateOf(true) }
    var reactionsEnabled by remember { mutableStateOf(true) }
    var incognitoGuests by remember { mutableStateOf(false) }
    var preBuffer4k by remember { mutableStateOf(true) }

    var endpointMaya by remember { mutableStateOf(true) }
    var endpointKenji by remember { mutableStateOf(true) }
    var endpointSarah by remember { mutableStateOf(true) }
    var endpointGuest by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBg)
    ) {
        // Modal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalCanvas)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TacticalText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCHEDULE MODAL", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TacticalRedDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Action Bar (Cancel / Confirm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CANCEL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted,
                modifier = Modifier.clickable { onDismiss() }
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("POCKETNODE EVENT", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                Text("SCHEDULE GROUP WATCH", fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
            }
            Button(
                onClick = {
                    ClientRepository.scheduleWatchParty(
                        movie = "Interstellar (2014)",
                        timeSlot = selectedTimeSlot,
                        endpoints = listOfNotNull(
                            if (endpointMaya) "Maya" else null,
                            if (endpointKenji) "Kenji" else null,
                            if (endpointSarah) "Sarah" else null,
                            if (endpointGuest) "Guest Room" else null
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TacticalRedDark),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("CONFIRM", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }

        Divider(color = TacticalBorderMuted, thickness = 0.5.dp)

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── 01 // SELECTED CONTENT ────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("01 // SELECTED CONTENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text("CHANGE FILM ⇄", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalPanel)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(56.dp, 80.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E2838)),
                            contentAlignment = Alignment.Center
                        ) {
                            val poster = com.pocketnode.client.data.PosterResolver.getPoster("Interstellar")
                            if (poster.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(poster)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Interstellar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("🌌", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.background(TacticalRed, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("4K REMUX", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                                }
                                Box(modifier = Modifier.background(TacticalCanvas, RoundedCornerShape(2.dp)).border(0.5.dp, TacticalBorder, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("HEVC HDR10", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                }
                                Box(modifier = Modifier.background(TacticalCanvas, RoundedCornerShape(2.dp)).border(0.5.dp, TacticalBorder, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("58.4 GB", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Interstellar (2014)", fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                            Text("Dir. Christopher Nolan", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⏱ 2h 49m", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                Text("ATMOS 7.1.4", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161F2E), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFF2C3E5A), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Node-Vault-Alpha (NVMe Cache)", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                            Text("Bitrate ceiling: 92 Mbps direct-play", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        }
                        Box(modifier = Modifier.background(Color(0xFF1E2838), RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("READY", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                        }
                    }
                }
            }

            // ── 02 // SCHEDULE MATRIX ─────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("02 // SCHEDULE MATRIX", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text("TZ: EST (UTC-5)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date Pills
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val days = listOf(
                        Triple("TODAY", "01", "OCT"),
                        Triple("THU", "02", "OCT"),
                        Triple("FRI", "03", "OCT"),
                        Triple("SAT", "04", "OCT")
                    )
                    days.forEach { (label, day, month) ->
                        val isSelected = selectedDay == label
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) TacticalRedDark else TacticalCanvas)
                                .border(1.dp, if (isSelected) TacticalRed else TacticalBorder, RoundedCornerShape(4.dp))
                                .clickable { selectedDay = label }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (isSelected) Color.White else TacticalMuted)
                            Text(day, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color.White)
                            Text(month, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = if (isSelected) Color.White else TacticalMuted)
                        }
                    }
                    // Calendar Pick Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TacticalCanvas)
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TacticalMuted, modifier = Modifier.size(16.dp))
                            Text("PICK", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Screening Slot Time Picker
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalPanel)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SCREENING SLOT", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Box(modifier = Modifier.background(Color(0xFF1E2838), RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("PRIME EVENING", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF182230), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TacticalText)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selectedTimeSlot, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color.White)
                            Text("ESTIMATED END: 11:19 PM", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TacticalText)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Slot Pills
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("7:00 PM", "8:30 PM", "9:15 PM", "10:00 PM (Late)").forEach { slot ->
                            val isSel = selectedTimeSlot.startsWith(slot.take(4))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSel) TacticalRedDark else TacticalCanvas)
                                    .border(0.5.dp, if (isSel) TacticalRed else TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { selectedTimeSlot = slot.take(8) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(slot, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── 03 // CONNECTED ENDPOINTS ─────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("03 // CONNECTED ENDPOINTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text("3 SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalPanel)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EndpointRow(name = "Maya", detail = "Living Room OLED (AppleTV 4K)", isChecked = endpointMaya, onToggle = { endpointMaya = it })
                    EndpointRow(name = "Kenji", detail = "Studio Mac (Display XDR)", isChecked = endpointKenji, onToggle = { endpointKenji = it })
                    EndpointRow(name = "Sarah", detail = "Bedroom Shield (Nvidia Shield Pro)", isChecked = endpointSarah, onToggle = { endpointSarah = it })
                    EndpointRow(name = "Guest Room", detail = "Tablet (iPad Air 11\") • Idle", isChecked = endpointGuest, onToggle = { endpointGuest = it })

                    Spacer(modifier = Modifier.height(4.dp))

                    // Auto-Send Invite Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161F2E), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = TacticalRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Auto-Send Calendar Invite & Notification", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                                Text("Pushes ICS & Home Assistant notification", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            }
                        }
                        Switch(
                            checked = autoSendInvite,
                            onCheckedChange = { autoSendInvite = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = TacticalRed, checkedTrackColor = TacticalRedDim)
                        )
                    }
                }
            }

            // ── 04 // PARTY ENGINE FEATURES ───────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("04 // PARTY ENGINE FEATURES", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalPanel)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureToggle(
                        title = "WALKIE-TALKIE VOICE CHAT",
                        detail = "Low-latency Opus audio via local mesh relay",
                        checked = walkieTalkieEnabled,
                        onToggle = { walkieTalkieEnabled = it }
                    )
                    FeatureToggle(
                        title = "LIVE FLOATING REACTIONS",
                        detail = "Haptic real-time emoji telemetry on player screen",
                        checked = reactionsEnabled,
                        onToggle = { reactionsEnabled = it }
                    )
                    FeatureToggle(
                        title = "INCOGNITO MODE FOR GUESTS",
                        detail = "Exclude watch stats from public Trakt/Simkl profiles",
                        checked = incognitoGuests,
                        onToggle = { incognitoGuests = it }
                    )
                    FeatureToggle(
                        title = "PRE-BUFFER 4K STREAM",
                        detail = "Spin up decoders 15 min early to prevent packet lag",
                        checked = preBuffer4k,
                        onToggle = { preBuffer4k = it }
                    )
                }
            }

            // ── 05 // NODE QUEUE - EXISTING WATCHES ───────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("05 // NODE QUEUE • EXISTING WATCHES", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text("1 ACTIVE", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalPanel)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFF242A36), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text("🎬", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Past Lives", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                            Text("TOMORROW", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                        }
                        Text("09:00 PM • 1h 45m • 1080p SDR", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Text("• 2 RSVPs Confirmed", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalRed)
                    }
                }
            }
        }

        // ── BOTTOM BANNER & BROADCAST BUTTON ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1520))
                .border(width = 0.5.dp, color = TacticalBorder)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("⚛ POCKETNODE SYNC READY", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                Text("PEER-TO-PEER ENCRYPTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Interstellar will lock into 3 hardware endpoints on Today @ $selectedTimeSlot with zero transcoding overhead.",
                fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    ClientRepository.scheduleWatchParty("Interstellar (2014)", selectedTimeSlot, listOf("Maya", "Kenji", "Sarah"))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalRedDark),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "CONFIRM & BROADCAST SCHEDULE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun EndpointRow(
    name: String,
    detail: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(TacticalCanvas)
            .border(0.5.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF202838), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(2).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                Text(detail, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = TacticalRed, uncheckedColor = TacticalBorder)
        )
    }
}

@Composable
fun FeatureToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(TacticalCanvas)
            .border(0.5.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
            Text(detail, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = TacticalRed, checkedTrackColor = TacticalRedDim)
        )
    }
}
