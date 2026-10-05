package com.remotemedia.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.StorageManager
import com.remotemedia.services.JellyfinManager
import com.remotemedia.ui.theme.StatusGreen
import com.remotemedia.ui.theme.TacticalBg
import com.remotemedia.ui.theme.TacticalBorder
import com.remotemedia.ui.theme.TacticalCanvas
import com.remotemedia.ui.theme.TacticalMuted
import com.remotemedia.ui.theme.TacticalOrange
import com.remotemedia.ui.theme.TacticalSurface
import com.remotemedia.ui.theme.TacticalText
import com.remotemedia.ui.theme.TacticalYellow

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 5

    // Admin account creation state
    var adminName by remember { mutableStateOf("Admin") }
    var adminPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Top Header & Progress Dots ─────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("POCKETNODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                Box(
                    modifier = Modifier
                        .background(StatusGreen.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                        .border(0.5.dp, StatusGreen, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("SETUP", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Step Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (step in 1..totalSteps) {
                    val isActive = step == currentStep
                    val isPast = step < currentStep
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isActive -> TacticalOrange
                                    isPast   -> StatusGreen
                                    else     -> TacticalBorder
                                }
                            )
                    )
                }
            }
        }

        // ── Slide Content Carousel ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingSlide"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (step) {
                        1 -> SlideWelcome()
                        2 -> SlideStreaming()
                        3 -> SlideDownloads()
                        4 -> SlideAppSections()
                        5 -> SlideAdminAccountSetup(
                            adminName = adminName,
                            onAdminNameChange = { adminName = it },
                            adminPassword = adminPassword,
                            onAdminPasswordChange = { adminPassword = it },
                            showPassword = showPassword,
                            onToggleShowPassword = { showPassword = !showPassword }
                        )
                    }
                }
            }
        }

        // ── Navigation Buttons ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 1) {
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { currentStep-- }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "← BACK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalText
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(if (currentStep > 1) 1.3f else 2f)
                    .background(
                        if (currentStep == totalSteps) StatusGreen else TacticalOrange,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (currentStep == totalSteps) StatusGreen else TacticalOrange,
                        RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            // Finalize Admin & Default Accounts
                            JellyfinManager.setupDefaultUsers(
                                context = context,
                                adminName = adminName.trim(),
                                adminHasPassword = adminPassword.isNotBlank()
                            )
                            prefs.edit()
                                .putBoolean("is_first_launch_completed", true)
                                .putString("admin_password_hash", adminPassword.trim())
                                .apply()

                            onFinishOnboarding()
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (currentStep == totalSteps) "🚀  COMPLETE SETUP & LAUNCH" else "CONTINUE  →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalBg
                )
            }
        }
    }
}

// ─── SLIDE 1: WELCOME & THE SYSTEM STORY ───────────────────────────────────────

@Composable
private fun SlideWelcome() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(TacticalOrange.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, TacticalOrange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("⚡", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your 24/7 Home Streaming Server",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PocketNode turns this Android device into your personal home media hub. Stream your movies, music, and shows to any TV or device in your house without subscription fees or power-hungry PCs.",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        OnboardingCard(
            icon = "🔌",
            title = "Ultra-Low Power & Silent",
            description = "Uses 95% less energy than leaving a desktop PC on. Completely silent and runs cool."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "🔋",
            title = "Always Running & Reliable",
            description = "Runs 24/7 in the background. Even during power cuts, your phone's battery keeps your server alive."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "🛡️",
            title = "100% Free & Private",
            description = "All media stays on your local home Wi-Fi. No monthly fees, no cloud accounts, no tracking."
        )
    }
}

// ─── SLIDE 2: HOW STREAMING WORKS (VLC & JELLYFIN) ─────────────────────────────

@Composable
private fun SlideStreaming() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(StatusGreen.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, StatusGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("📺", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Stream on Any TV, PC & Phone",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your media appears automatically on every device connected to your home Wi-Fi:",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        OnboardingCard(
            icon = "🎬",
            title = "Jellyfin on Smart TVs",
            description = "Install the official Jellyfin app on Android TV, Fire TV, Apple TV, or Roku. PocketNode is discovered automatically on your Wi-Fi!"
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "📱",
            title = "VLC Media Player (Zero-Login)",
            description = "Open VLC on your TV, PC, Mac, or tablet. Click 'Local Network' to browse and stream your movies instantly with zero passwords."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "🌐",
            title = "Web Browser Panel (:8080)",
            description = "Open pocketnode.local:8080 in Chrome, Safari, or Edge to watch videos or upload files right inside your browser."
        )
    }
}

// ─── SLIDE 3: BACKGROUND DOWNLOADS & AUTOMATION ───────────────────────────────

@Composable
private fun SlideDownloads() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(TacticalYellow.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, TacticalYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("📥", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Automated Background Downloads",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Download movies and media without lifting a finger or keeping a computer running:",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        OnboardingCard(
            icon = "✈️",
            title = "Telegram Remote Controller",
            description = "Send a movie title or magnet link from Telegram while outside—PocketNode downloads it at home automatically."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "⚡",
            title = "Built-in Torrent Engine",
            description = "High-speed torrent engine that downloads in the background even when your phone's screen is turned off."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "🔒",
            title = "Private Vault (Hidden from Gallery)",
            description = "All downloads stay strictly inside PocketNode's private vault and will NEVER clutter your phone's photo gallery or camera roll."
        )
    }
}

// ─── SLIDE 4: DASHBOARD & SECTIONS TOUR ───────────────────────────────────────

@Composable
private fun SlideAppSections() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(TacticalOrange.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, TacticalOrange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🧭", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Dashboard at a Glance",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Here is what each section on your home dashboard means:",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        OnboardingCard(
            icon = "🔴",
            title = "Master Switch",
            description = "One tap on the master switch starts or stops all home streaming and downloading services together."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "📊",
            title = "System Vitals",
            description = "Simple gauges monitor storage space, RAM, and temperature to keep your phone running cool and safe."
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingCard(
            icon = "📖",
            title = "Field Guides in Menu",
            description = "Need help connecting your TV, VLC, or Telegram? Step-by-step field manuals are always waiting for you in the side menu!"
        )
    }
}

// ─── SLIDE 5: ADMIN & FAMILY ACCOUNT SETUP ─────────────────────────────────────

@Composable
private fun SlideAdminAccountSetup(
    adminName: String,
    onAdminNameChange: (String) -> Unit,
    adminPassword: String,
    onAdminPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(StatusGreen.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, StatusGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("👑", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create Your Admin Account",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Admin account has full access to the server, downloads, and content settings.",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Admin Username Input
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("ADMIN USERNAME", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = adminName,
                    onValueChange = onAdminNameChange,
                    textStyle = TextStyle(
                        color = TacticalText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Admin Password Input
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ADMIN PASSWORD (OPTIONAL)", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text(
                    if (showPassword) "HIDE" else "SHOW",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalYellow,
                    modifier = Modifier.clickable { onToggleShowPassword() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = adminPassword,
                    onValueChange = onAdminPasswordChange,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(
                        color = TacticalText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (adminPassword.isEmpty()) {
                            Text("Leave blank for password-free local access", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Automatic Family Profiles Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("👥", fontSize = 12.sp)
                    Text("AUTOMATIC FAMILY PROFILES INITIALIZED", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "PocketNode automatically creates 'Guest' and 'Kids' default accounts:\n" +
                    "• Guest: Can stream approved files and download. Cannot delete.\n" +
                    "• Kids: View-only mode for family-safe approved media.",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ─── REUSABLE ONBOARDING CARD ──────────────────────────────────────────────────

@Composable
private fun OnboardingCard(
    icon: String,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(icon, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    description,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
