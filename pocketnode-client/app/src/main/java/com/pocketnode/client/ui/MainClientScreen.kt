package com.pocketnode.client.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.ClientRepository
import com.pocketnode.client.data.NavigationTab
import com.pocketnode.client.data.UserRole
import com.pocketnode.client.ui.components.ClientHeaderBar
import com.pocketnode.client.ui.components.IncognitoBanner
import com.pocketnode.client.ui.components.TacticalBottomNavigation
import com.pocketnode.client.ui.screens.*
import com.pocketnode.client.ui.theme.*

@Composable
fun MainClientScreen() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.WATCH) }
    val session by ClientRepository.session.collectAsState()

    // Modals & Player Overlay
    var showScheduleModal by remember { mutableStateOf(false) }
    var showStorageDiagnostics by remember { mutableStateOf(false) }
    var activePlayingTitle by remember { mutableStateOf<String?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String>("") }
    var activeFormatBadge by remember { mutableStateOf<String>("4K HEVC • REMUX") }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp) // space for the taller pill nav
        ) {
            // Header Bar
            ClientHeaderBar(
                session = session,
                onToggleIncognito = {
                    ClientRepository.toggleIncognito()
                    val stateNow = !session.isIncognito
                    showToast(if (stateNow) "Incognito Active: Telemetry scrubbed & invisible to LAN" else "Incognito Deactivated")
                },
                onOpenProfile = {
                    val nextRole = when (session.role) {
                        UserRole.ADMIN -> UserRole.GUEST
                        UserRole.GUEST -> UserRole.KIDS
                        UserRole.KIDS -> UserRole.ADMIN
                    }
                    ClientRepository.switchRole(nextRole)
                    showToast("Switched user profile to: ${nextRole.label}")
                }
            )

            // Incognito Banner
            IncognitoBanner(
                isIncognito = session.isIncognito,
                onDismiss = { ClientRepository.toggleIncognito() }
            )

            // Dynamic Tab Page
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    NavigationTab.WATCH -> {
                        WatchTabScreen(
                            onOpenScheduleModal = { showScheduleModal = true },
                            onPlayMovie = { title, streamUrl, formatBadge ->
                                activePlayingTitle = title
                                activeStreamUrl = streamUrl
                                activeFormatBadge = formatBadge
                            }
                        )
                    }

                    NavigationTab.LIBRARY -> {
                        LibraryTabScreen(
                            onPlayMovie = { title, streamUrl, formatBadge ->
                                activePlayingTitle = title
                                activeStreamUrl = streamUrl
                                activeFormatBadge = formatBadge
                            }
                        )
                    }

                    NavigationTab.TORRENTS -> {
                        TorrentStreamScreen(
                            onPlayMovie = { title, streamUrl, formatBadge ->
                                activePlayingTitle = title
                                activeStreamUrl = streamUrl
                                activeFormatBadge = formatBadge
                            },
                            onToast = { showToast(it) }
                        )
                    }


                    NavigationTab.SCANNER -> {
                        MediaScannerScreen()
                    }

                    NavigationTab.TRANSFERS -> {
                        TransfersTabScreen(
                            currentRole = session.role,
                            onRoleChange = { ClientRepository.switchRole(it) },
                            incognitoMode = session.isIncognito,
                            onToggleIncognito = { ClientRepository.toggleIncognito() },
                            onToast = { showToast(it) },
                            onPlayMovie = { title, streamUrl, formatBadge ->
                                activePlayingTitle = title
                                activeStreamUrl = streamUrl
                                activeFormatBadge = formatBadge
                            }
                        )
                    }

                    NavigationTab.SETTINGS -> {
                        SettingsTabScreen(
                            currentRole = session.role,
                            onRoleChange = { ClientRepository.switchRole(it) },
                            incognitoMode = session.isIncognito,
                            onToggleIncognito = { ClientRepository.toggleIncognito() },
                            onOpenStorageDiagnostics = {
                                showStorageDiagnostics = true
                            },
                            onToast = { showToast(it) }
                        )
                    }
                }
            }
        }

        // ── New Floating Pill Navigation Bar ──────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TacticalBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }

        // Overlay Modal 1: Schedule Watch Party Modal
        if (showScheduleModal) {
            ScheduleWatchModal(
                onDismiss = { showScheduleModal = false }
            )
        }

        // Overlay Modal 2: Storage & Memory Diagnostics Modal
        if (showStorageDiagnostics) {
            StorageDiagnosticsModal(
                onDismiss = { showStorageDiagnostics = false },
                onPurgeCompleted = { freedMsg ->
                    showToast(freedMsg)
                }
            )
        }

        // Fullscreen ExoPlayer Screen with Tactical HUD & Walkie-Talkie Intercom
        activePlayingTitle?.let { title ->
            PlayerScreen(
                title = title,
                streamUrl = activeStreamUrl,
                formatBadge = activeFormatBadge,
                initialPositionMs = ClientRepository.getSavedProgressMs(title, activeStreamUrl),
                onClose = {
                    activePlayingTitle = null
                    activeStreamUrl = ""
                },
                onToast = { showToast(it) }
            )
        }
    }
}
