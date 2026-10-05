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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.DevicePolicy
import com.remotemedia.core.FilePermissionRule
import com.remotemedia.core.PermissionsManager
import com.remotemedia.core.StorageManager
import com.remotemedia.ui.theme.*
import java.io.File

@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    var selectedTopTab by remember { mutableIntStateOf(0) } // 0: Files, 1: Devices
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
    ) {
        // ── Top Mode Switcher (Files vs Devices) ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeTabButton(
                title = "📁 FILE PERMISSIONS",
                selected = selectedTopTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedTopTab = 0 }
            )
            ModeTabButton(
                title = "📱 DEVICE POLICIES",
                selected = selectedTopTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedTopTab = 1 }
            )
        }

        // ── Tab Content ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTopTab) {
                0 -> FilePermissionsTab()
                1 -> DevicePoliciesTab()
            }
        }
    }
}

// ─── TAB 1: FILE PERMISSIONS ─────────────────────────────────────────────────

@Composable
private fun FilePermissionsTab() {
    var selectedCategory by remember { mutableStateOf("Movies") }
    val categories = StorageManager.CURATED_CATEGORIES
    val fileRules by PermissionsManager.fileRules.collectAsState()
    val scrollState = rememberScrollState()

    val files = remember(selectedCategory, fileRules) {
        StorageManager.getCategoryFiles(selectedCategory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = cat.equals(selectedCategory, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) TacticalOrange.copy(alpha = 0.2f) else TacticalSurface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) TacticalOrange else TacticalBorder,
                            RoundedCornerShape(4.dp)
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) TacticalOrange else TacticalMuted
                    )
                }
            }
        }

        // Category Info Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// VAULT_CATEGORY: ${selectedCategory.uppercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText
                )
                Text(
                    "${files.size} FILES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalOrange
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    StorageManager.getCategoryDir(selectedCategory).absolutePath,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    ".NOMEDIA ACTIVE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = StatusGreen
                )
            }
        }

        // File List Header
        Text(
            "// CURATED_FILES & ACCESS_RULES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted
        )

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "NO FILES IN $selectedCategory",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Downloaded movies, music, or host shared files will appear here.\nAdmin has full control over Guest and Kids visibility.",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.forEach { file ->
                    val rule = PermissionsManager.getRuleForFile(file, selectedCategory)
                    FilePermissionCard(file = file, rule = rule)
                }
            }
        }
    }
}

@Composable
private fun FilePermissionCard(
    file: File,
    rule: FilePermissionRule
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    getFileIcon(file.name),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        file.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        formatFileSize(file.length()),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        if (rule.uploadedByRole == "ADMIN") TacticalOrange.copy(alpha = 0.15f) else ElectricTeal.copy(alpha = 0.15f),
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        0.5.dp,
                        if (rule.uploadedByRole == "ADMIN") TacticalOrange else ElectricTeal,
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "${rule.uploadedByRole} UPLOAD",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (rule.uploadedByRole == "ADMIN") TacticalOrange else ElectricTeal
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(TacticalBorder.copy(alpha = 0.5f)))
        Spacer(modifier = Modifier.height(8.dp))

        // Role Permission Toggles Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Admin: Always full
            RolePermissionPill(
                label = "ADMIN",
                stateText = "FULL ACCESS",
                isActive = true,
                activeColor = StatusGreen,
                isToggleable = false,
                onClick = {},
                modifier = Modifier.weight(1f)
            )

            // Guest Toggle
            RolePermissionPill(
                label = "GUEST",
                stateText = if (rule.visibleToGuest) "VISIBLE" else "HIDDEN",
                isActive = rule.visibleToGuest,
                activeColor = TacticalYellow,
                isToggleable = true,
                onClick = { PermissionsManager.toggleGuestAccess(file.absolutePath) },
                modifier = Modifier.weight(1f)
            )

            // Kids Toggle
            RolePermissionPill(
                label = "KIDS",
                stateText = if (rule.visibleToKids) "VISIBLE" else "HIDDEN",
                isActive = rule.visibleToKids,
                activeColor = ElectricTeal,
                isToggleable = true,
                onClick = { PermissionsManager.toggleKidsAccess(file.absolutePath) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RolePermissionPill(
    label: String,
    stateText: String,
    isActive: Boolean,
    activeColor: Color,
    isToggleable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.12f) else TacticalSurface,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.6f) else TacticalBorder,
                RoundedCornerShape(4.dp)
            )
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = isToggleable) { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                stateText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isActive) activeColor else TacticalMuted
            )
        }
    }
}

// ─── TAB 2: DEVICE POLICIES ──────────────────────────────────────────────────

@Composable
private fun DevicePoliciesTab() {
    val devicePolicies by PermissionsManager.devicePolicies.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// DETECTED_DEVICES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
                Text(
                    "${devicePolicies.size} CONNECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = StatusGreen
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Control per-device role assignments, Direct Play bitstream streaming, and category access.",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted,
                lineHeight = 14.sp
            )
        }

        // Device Cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            devicePolicies.values.forEach { policy ->
                DevicePolicyCard(policy = policy)
            }
        }
    }
}

@Composable
private fun DevicePolicyCard(policy: DevicePolicy) {
    val isBlocked = policy.isBlocked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isBlocked) TacticalRed.copy(alpha = 0.08f) else TacticalPanel,
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isBlocked) TacticalRed.copy(alpha = 0.5f) else TacticalBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Device Title & Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        getDeviceIcon(policy.clientType),
                        fontSize = 14.sp
                    )
                    Text(
                        policy.deviceName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isBlocked) TacticalRed else TacticalText
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${policy.ipAddress} • ${policy.clientType}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (isBlocked) TacticalRed else StatusGreen.copy(alpha = 0.15f),
                        RoundedCornerShape(3.dp)
                    )
                    .border(1.dp, if (isBlocked) TacticalRed else StatusGreen, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isBlocked) "BLOCKED" else "ONLINE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isBlocked) TacticalBg else StatusGreen
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(TacticalBorder.copy(alpha = 0.5f)))

        // Assigned Role Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ASSIGNED ROLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ADMIN", "GUEST", "KIDS").forEach { role ->
                    val isSelected = policy.assignedRole.equals(role, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) TacticalOrange else TacticalSurface,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                0.5.dp,
                                if (isSelected) TacticalOrange else TacticalBorder,
                                RoundedCornerShape(3.dp)
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { PermissionsManager.setDeviceRole(policy.deviceId, role) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            role,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) TacticalBg else TacticalMuted
                        )
                    }
                }
            }
        }

        // Direct Play Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(4.dp))
                .border(
                    1.dp,
                    if (policy.forceDirectPlay) StatusGreen.copy(alpha = 0.5f) else TacticalBorder,
                    RoundedCornerShape(4.dp)
                )
                .clickable { PermissionsManager.toggleDeviceDirectPlay(policy.deviceId) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚡", fontSize = 11.sp)
                    Text("FORCE DIRECT PLAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
                Text("Raw bitstream passthrough (saves phone battery & CPU)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
            Text(
                if (policy.forceDirectPlay) "[ ENABLED ]" else "[ AUTO ]",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (policy.forceDirectPlay) StatusGreen else TacticalMuted
            )
        }

        // Allowed Category Whitelist Chips
        Column {
            Text("ALLOWED CATEGORIES ON THIS DEVICE", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StorageManager.CURATED_CATEGORIES.forEach { cat ->
                    val isAllowed = policy.allowedCategories.contains(cat)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isAllowed) TacticalYellow.copy(alpha = 0.15f) else TacticalSurface,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                0.5.dp,
                                if (isAllowed) TacticalYellow else TacticalBorder,
                                RoundedCornerShape(3.dp)
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { PermissionsManager.toggleCategoryForDevice(policy.deviceId, cat) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            cat,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isAllowed) TacticalYellow else TacticalMuted
                        )
                    }
                }
            }
        }

        // Block / Revoke Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isBlocked) StatusGreen.copy(alpha = 0.12f) else TacticalRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .border(1.dp, if (isBlocked) StatusGreen else TacticalRed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .clickable { PermissionsManager.toggleDeviceBlock(policy.deviceId) }
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isBlocked) "UNBLOCK DEVICE" else "REVOKE & BLOCK ACCESS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isBlocked) StatusGreen else TacticalRed
            )
        }
    }
}

// ─── REUSABLE HELPERS ────────────────────────────────────────────────────────

@Composable
private fun ModeTabButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) TacticalOrange else TacticalSurface,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (selected) TacticalOrange else TacticalBorder,
                RoundedCornerShape(4.dp)
            )
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (selected) TacticalBg else TacticalText
        )
    }
}

private fun getFileIcon(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "avi", "mov", "webm" -> "🎬"
        "mp3", "flac", "m4a", "wav", "aac" -> "🎵"
        "jpg", "jpeg", "png", "webp", "gif" -> "📷"
        "pdf", "epub", "txt", "doc"         -> "📄"
        else                                -> "📦"
    }
}

private fun getDeviceIcon(clientType: String): String {
    return when (clientType.uppercase()) {
        "JELLYFIN" -> "📺"
        "DLNA"     -> "📱"
        "WEB"      -> "🌐"
        else       -> "💻"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    val g = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, g.toDouble()), u[g])
}
