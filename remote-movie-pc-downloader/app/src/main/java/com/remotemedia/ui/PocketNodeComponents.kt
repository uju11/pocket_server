package com.remotemedia.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import com.remotemedia.R
import com.remotemedia.ServerForegroundService
import com.remotemedia.ui.theme.*

// ─── Status Badge ─────────────────────────────────────────────────────────────

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ─── Top TTY Status Bar ──────────────────────────────────────────────────────

@Composable
fun PocketNodeStatusBar(
    liveUtcTime: String,
    batteryPercent: Int,
    isCharging: Boolean,
    serverUptime: String = "",
    isServerRunning: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalBg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: TTY + Clock
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TTY:01", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
            Text(" // ", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Text(liveUtcTime.ifEmpty { "00:00:00 UTC" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow)
        }
        // Right: Signal + Battery
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (isServerRunning) StatusGreen else TacticalMuted))
            Spacer(modifier = Modifier.width(4.dp))
            Text("-64dBm [5G] / ", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Text("${batteryPercent}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Text(" [${if (isCharging) "⚡" else "🔋"}]", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (isCharging) TacticalYellow else TacticalMuted)
        }
    }
}

// ─── Morphing Logo ↔ Hamburger / Back Button ────────────────────────────────

@Composable
fun MorphingLogoBackButton(
    isSubPage: Boolean = false,
    isDrawerOpen: Boolean = false,
    isServerRunning: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val redGrad = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C)),
            start = Offset(0f, 0f),
            end = Offset(512f, 512f)
        )
    }
    val yellowGrad = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFB800), Color(0xFFD97706)),
            start = Offset(0f, 0f),
            end = Offset(512f, 512f)
        )
    }

    Canvas(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        val s = size.width / 512f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            // 1. Squircle badge background
            drawRoundRect(
                brush = if (isServerRunning) redGrad else yellowGrad,
                topLeft = Offset(0f, 0f),
                size = Size(512f, 512f),
                cornerRadius = CornerRadius(108f, 108f)
            )

            // Inner subtle border highlight
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, 0f),
                size = Size(512f, 512f),
                cornerRadius = CornerRadius(108f, 108f),
                style = Stroke(width = 8f)
            )

            if (isSubPage) {
                // ── Crisp, unmistakable tactical Back Arrow [ ← ] ──
                val arrowPath = Path().apply {
                    // Shaft
                    moveTo(370f, 256f)
                    lineTo(155f, 256f)
                    // Upper wing
                    moveTo(265f, 155f)
                    lineTo(155f, 256f)
                    // Lower wing
                    lineTo(265f, 357f)
                }
                drawPath(
                    path = arrowPath,
                    color = Color.White,
                    style = Stroke(width = 46f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else if (isDrawerOpen) {
                // ── 3 Parallel Hamburger Bars ──
                val barW = 290f
                val barH = 34f
                val barRx = 17f
                val barX = 111f
                listOf(146f, 239f, 332f).forEach { barY ->
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(barX, barY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barRx, barRx)
                    )
                }
            } else {
                // ── Default PocketNode Server Emblem (3 Blades) ──
                // Top Blade
                drawRoundRect(
                    color = Color(0xFF2A354C),
                    topLeft = Offset(96f, 96f),
                    size = Size(320f, 72f),
                    cornerRadius = CornerRadius(36f, 36f)
                )
                drawRoundRect(
                    color = Color(0xFF3E4F6F),
                    topLeft = Offset(96f, 96f),
                    size = Size(320f, 72f),
                    cornerRadius = CornerRadius(36f, 36f),
                    style = Stroke(width = 4f)
                )
                drawCircle(color = Color(0xFF00F5D4), radius = 11f, center = Offset(146f, 132f))
                drawCircle(color = Color(0xFF38BDF8), radius = 7f, center = Offset(178f, 132f))
                drawLine(
                    color = Color(0xFF0E131D),
                    start = Offset(224f, 132f),
                    end = Offset(368f, 132f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                // Middle Glowing Blade
                drawRoundRect(
                    color = Color(0xFFFF7B00),
                    topLeft = Offset(64f, 208f),
                    size = Size(384f, 96f),
                    cornerRadius = CornerRadius(48f, 48f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(64f, 208f),
                    size = Size(384f, 96f),
                    cornerRadius = CornerRadius(48f, 48f),
                    style = Stroke(width = 5f)
                )
                val playPath = Path().apply {
                    moveTo(246f, 234f)
                    lineTo(282f, 256f)
                    lineTo(246f, 278f)
                    close()
                }
                drawPath(path = playPath, color = Color(0xFF0E131D))
                drawCircle(color = Color(0xFF0E131D), radius = 10f, center = Offset(128f, 256f))
                drawLine(
                    color = Color(0xFF0E131D),
                    start = Offset(336f, 256f),
                    end = Offset(384f, 256f),
                    strokeWidth = 12f,
                    cap = StrokeCap.Round
                )

                // Bottom Blade
                drawRoundRect(
                    color = Color(0xFF2A354C),
                    topLeft = Offset(96f, 344f),
                    size = Size(320f, 72f),
                    cornerRadius = CornerRadius(36f, 36f)
                )
                drawRoundRect(
                    color = Color(0xFF3E4F6F),
                    topLeft = Offset(96f, 344f),
                    size = Size(320f, 72f),
                    cornerRadius = CornerRadius(36f, 36f),
                    style = Stroke(width = 4f)
                )
                drawCircle(color = Color(0xFF00F5D4), radius = 11f, center = Offset(146f, 380f))
                drawCircle(color = Color(0xFF475569), radius = 7f, center = Offset(178f, 380f))
                drawLine(
                    color = Color(0xFF0E131D),
                    start = Offset(224f, 380f),
                    end = Offset(368f, 380f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF00F5D4),
                    start = Offset(224f, 380f),
                    end = Offset(280f, 380f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ─── App Header Bar ───────────────────────────────────────────────────────────

@Composable
fun PocketNodeHeader(
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    isSubPage: Boolean = false,
    onBack: () -> Unit = {},
    pageTitle: String = "SETTINGS",
    isDrawerOpen: Boolean = false,
    onToggleDrawer: () -> Unit = {}
) {
    val isServerRunning by ServerForegroundService.isRunning.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Boxy Brand Lockup (Logo ↔ Back + Title)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(TacticalSurface.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    if (isSubPage) onBack() else onToggleDrawer()
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            MorphingLogoBackButton(
                isSubPage = isSubPage,
                isDrawerOpen = isDrawerOpen,
                isServerRunning = isServerRunning,
                onClick = {
                    if (isSubPage) onBack() else onToggleDrawer()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedContent(
                targetState = isSubPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "headerTitle"
            ) { subPage ->
                if (subPage) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            "// $pageTitle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalText
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            "[ TAP TO RETURN ]",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalYellow
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "POCKETNODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalText
                            )
                            Box(
                                modifier = Modifier
                                    .background(StatusGreen.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                    .border(0.5.dp, StatusGreen, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "BETA",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = StatusGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            if (isDrawerOpen) "// MENU OPEN" else "// CORE_SYSTEM",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDrawerOpen) TacticalOrange else TacticalMuted
                        )
                    }
                }
            }
        }
        // Right buttons: visible only on main dashboard; in submodules (Settings, CLI, etc.),
        // CLI, CFG, and CLOSE are removed — only the morphing back button on the left is used.
        AnimatedVisibility(
            visible = !isSubPage,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .background(TacticalYellow, RoundedCornerShape(3.dp))
                        .clickable { onOpenTerminal() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(">_ CLI", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalBg)
                }
                Box(
                    modifier = Modifier
                        .background(TacticalRed, RoundedCornerShape(3.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙ CFG", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }
    }
}

// ─── Tab Bar ─────────────────────────────────────────────────────────────────

val POCKET_TABS = listOf("General", "Jellyfin", "Telegram", "Torrent", "Web")

@Composable
fun PocketNodeTabBar(selectedTab: Int, onTabSelected: (Int) -> Unit, activeIndicators: Map<Int, Boolean> = emptyMap()) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        POCKET_TABS.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val isActive = activeIndicators[index] ?: false
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) TacticalOrange else TacticalPanel,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) TacticalOrange else TacticalBorder,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isActive) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else StatusGreen))
                    }
                    Text(
                        text = title.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) Color.White else TacticalMuted
                    )
                }
            }
        }
    }
}

// ─── Section Header ──────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, badge: String = "", badgeColor: Color = StatusGreen) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "// $title",
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, badgeColor, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = badgeColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// ─── Path Display Box ────────────────────────────────────────────────────────

@Composable
fun PocketPathBox(path: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Text(path, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
    }
}

// ─── Action Button ───────────────────────────────────────────────────────────

@Composable
fun PocketActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgColor: Color = TacticalPanel,
    borderColor: Color = TacticalBorder,
    textColor: Color = TacticalText
) {
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = textColor)
    }
}

// ─── Pocket Card ─────────────────────────────────────────────────────────────

@Composable
fun PocketCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(14.dp),
        content = content
    )
}

// ─── Toggle Row ──────────────────────────────────────────────────────────────

@Composable
fun PocketToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = TacticalText)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
        }
        PocketSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PocketSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = StatusGreen,
            uncheckedThumbColor = TacticalMuted,
            uncheckedTrackColor = TacticalSurface,
            uncheckedBorderColor = TacticalBorder
        )
    )
}

// ─── Mono Input Field ────────────────────────────────────────────────────────

@Composable
fun PocketInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { if (label.isNotBlank()) Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TacticalText
        ),
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TacticalOrange,
            unfocusedBorderColor = TacticalBorder,
            focusedLabelColor = TacticalOrange,
            cursorColor = TacticalOrange,
            focusedContainerColor = TacticalSurface,
            unfocusedContainerColor = TacticalSurface
        ),
        shape = RoundedCornerShape(4.dp)
    )
}

// ─── PocketNode Shared Footer ────────────────────────────────────────────────

@Composable
fun PocketNodeFooter(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made by ", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text("Xmedia", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
            }
            Text("REV 2026.09.29 // BETA BUILD", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
        Box(
            modifier = Modifier
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .clickable {
                    android.widget.Toast.makeText(context, "Thank you for supporting PocketNode! ☕", android.widget.Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text("☕ Buy me a coffee", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
        }
    }
}
