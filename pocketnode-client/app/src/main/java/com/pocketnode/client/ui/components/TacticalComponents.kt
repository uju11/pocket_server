package com.pocketnode.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.ClientSession
import com.pocketnode.client.ui.theme.*

@Composable
fun ClientHeaderBar(
    session: ClientSession,
    onToggleIncognito: () -> Unit,
    onOpenProfile: () -> Unit,
    onSearchClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JellyfinBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Jellyfin Play Logo + 10GBE Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Glowing Play Logo Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF26143D))
                    .border(1.dp, Color(0xFF5E2B8F), CircleShape)
                    .clickable { onToggleIncognito() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Jellyfin Home",
                    tint = NeonPurpleLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 10GBE Connection Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF131627))
                    .border(1.dp, Color(0xFF222744), RoundedCornerShape(20.dp))
                    .clickable { onToggleIncognito() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (session.isConnected) NeonCyan else Color.Gray, CircleShape)
                )
                Text(
                    text = "10GBE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyanMuted,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Right: Search Icon + Purple User Avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onSearchClick() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Profile Avatar with Purple Fill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B2CF5))
                    .border(1.5.dp, Color(0xFFC04CFD), CircleShape)
                    .clickable { onOpenProfile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Profile (${session.role.label})",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun IncognitoBanner(
    isIncognito: Boolean,
    onDismiss: () -> Unit
) {
    if (!isIncognito) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF191129))
            .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(NeonPurple, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "ZERO-TRACE INCOGNITO ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Scrubbed from household activity logs & hubs.",
                        fontSize = 10.sp,
                        color = TacticalMuted
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2E194D))
                    .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "SHIELD ON",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurpleLight
                )
            }
        }
    }
}

@Composable
fun TacticalBottomNav(
    currentTab: String,
    onSelectTab: (String) -> Unit
) {
    val items = listOf(
        NavigationItem("WATCH",     "Home",      Icons.Outlined.Home,          Icons.Filled.Home),
        NavigationItem("LIBRARY",   "Library",   Icons.Outlined.VideoLibrary,  Icons.Filled.VideoLibrary),
        NavigationItem("TORRENTS",  "Torrents",  Icons.Outlined.CloudDownload, Icons.Filled.CloudDownload),
        NavigationItem("TELEGRAM",  "Telegram",  Icons.Outlined.Send,          Icons.Filled.Send),
        NavigationItem("SCANNER",   "Scanner",   Icons.Outlined.ManageSearch,  Icons.Filled.ManageSearch),
        NavigationItem("TRANSFERS", "Transfers", Icons.Outlined.Download,      Icons.Filled.Download),
        NavigationItem("SETTINGS",  "Settings",  Icons.Outlined.Settings,      Icons.Filled.Settings)
    )

    // Outer scrim / frosted panel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xF0090A14))
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Pill container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0D0E1C))
                .border(1.dp, Color(0xFF1C2040), RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentTab == item.id
                val accentColor = when (item.id) {
                    "TORRENTS" -> AmberOrange
                    "TELEGRAM" -> NeonCyan
                    "SCANNER"  -> StatusGreen
                    else       -> NeonPurpleLight
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) accentColor.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelectTab(item.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) accentColor else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val id: String,
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun RoleBadge(
    role: com.pocketnode.client.data.UserRole,
    modifier: Modifier = Modifier
) {
    val bg = when (role) {
        com.pocketnode.client.data.UserRole.ADMIN -> TacticalRed.copy(alpha = 0.2f)
        com.pocketnode.client.data.UserRole.GUEST -> ElectricTeal.copy(alpha = 0.2f)
        com.pocketnode.client.data.UserRole.KIDS -> GoldYellow.copy(alpha = 0.2f)
    }
    val fg = when (role) {
        com.pocketnode.client.data.UserRole.ADMIN -> TacticalRed
        com.pocketnode.client.data.UserRole.GUEST -> ElectricTeal
        com.pocketnode.client.data.UserRole.KIDS -> GoldYellow
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = role.label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TacticalHeader(
    currentRole: com.pocketnode.client.data.UserRole,
    incognitoMode: Boolean,
    onToggleIncognito: () -> Unit,
    onRoleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App & Connection Info
        Column {
            Text(
                "POCKETNODE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TacticalTextWhite,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(TacticalRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    "CONNECTED TO HOME",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TacticalMuted
                )
            }
        }

        // Action controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Incognito Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (incognitoMode) TacticalRedDim else TacticalCanvas)
                    .border(
                        1.dp,
                        if (incognitoMode) TacticalRed else TacticalBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onToggleIncognito() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (incognitoMode) Icons.Default.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = if (incognitoMode) TacticalRed else TacticalMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (incognitoMode) "INCOGNITO: ON" else "INCOGNITO: OFF",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (incognitoMode) TacticalRed else TacticalText
                )
            }

            // Role Badge / Avatar button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRoleClick() }
            ) {
                RoleBadge(role = currentRole)
            }
        }
    }
}

@Composable
fun TacticalBottomNavigation(
    currentTab: com.pocketnode.client.data.NavigationTab,
    onTabSelected: (com.pocketnode.client.data.NavigationTab) -> Unit
) {
    TacticalBottomNav(
        currentTab = currentTab.name,
        onSelectTab = { tabName ->
            val tab = com.pocketnode.client.data.NavigationTab.valueOf(tabName)
            onTabSelected(tab)
        }
    )
}
