package com.remotemedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun GuideScreen(
    onReplayOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf(
        "📺 JELLYFIN",
        "📱 VLC / DLNA",
        "✈️ TELEGRAM",
        "🌐 WEB BROWSER",
        "🔗 NETWORK & IP"
    )
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Section Selector Tabs ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEachIndexed { index, title ->
                val isSelected = selectedSection == index
                Box(
                    modifier = Modifier
                        .weight(1f)
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
                        .clickable { selectedSection = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) TacticalOrange else TacticalMuted,
                        maxLines = 1
                    )
                }
            }
        }

        // ── Section Guide Content ──────────────────────────────────────────────
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "guideContent"
        ) { section ->
            when (section) {
                0 -> JellyfinGuide(context)
                1 -> VlcGuide(context)
                2 -> TelegramGuide(context)
                3 -> WebGuide(context)
                4 -> NetworkGuide(context)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Re-run Onboarding walkthrough ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .clickable { onReplayOnboarding() }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🔄 REPLAY FIRST-TIME ONBOARDING TOUR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalOrange
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Review the 5-step system introduction and app guide",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
            }
        }
    }
}

@Composable
private fun JellyfinGuide(context: Context) {
    GuideContainer(
        title = "JELLYFIN HOME MEDIA SERVER",
        subtitle = "Full-featured media hub on standard port 8096"
    ) {
        GuideStep(
            number = "1",
            title = "Install Jellyfin on Your TV or Device",
            body = "Search for 'Jellyfin' in the Google Play Store on Android TV, Amazon Appstore on Fire TV, Apple TV App Store, or Roku Channel Store. It is 100% free."
        )
        GuideStep(
            number = "2",
            title = "Connect via Auto-Discovery",
            body = "Open the app. PocketNode broadcasts its location across your home Wi-Fi using UDP port 7359. Your PocketNode server will appear automatically!"
        )
        GuideStep(
            number = "3",
            title = "Manual Server Address",
            body = "If your TV does not auto-detect, choose 'Add Server' and enter http://<YOUR_DEVICE_IP>:8096 (e.g. 192.168.x.x:8096). Note: Smart TVs require numeric IP addresses instead of .local hostnames.",
            copyText = "http://pocketnode.local:8096",
            context = context
        )
        GuideStep(
            number = "4",
            title = "Sign In With Your Account",
            body = "Sign in using your Admin account created during setup, or use the default Guest account for household members."
        )
    }
}

@Composable
private fun VlcGuide(context: Context) {
    GuideContainer(
        title = "UNIVERSAL PLUG & PLAY (UPNP) / DLNA",
        subtitle = "Zero-login streaming on port 8090 • Detected in Windows, Smart TVs & VLC"
    ) {
        GuideStep(
            number = "1",
            title = "Windows PC / File Explorer",
            body = "Open 'Network' or 'This PC' in Windows File Explorer. PocketNode Media Server appears automatically under 'Media Devices'. Double-click to stream."
        )
        GuideStep(
            number = "2",
            title = "Samsung / LG / Sony Smart TVs",
            body = "On your TV remote, open 'Home Dashboard', 'Source', or 'Media Players'. 'PocketNode Media Server' appears automatically in connected devices."
        )
        GuideStep(
            number = "3",
            title = "VLC, Infuse & Kodi (iPhone, iPad, Android)",
            body = "In VLC, click 'Network' -> 'Universal Plug'n'Play'. In Infuse on Apple TV/iOS, add 'UPnP/DLNA'. All your movies and video thumbnails are ready to play without logging in."
        )
    }
}

@Composable
private fun TelegramGuide(context: Context) {
    GuideContainer(
        title = "TELEGRAM REMOTE DOWNLOADER BOT",
        subtitle = "Control downloads from anywhere outside your home"
    ) {
        GuideStep(
            number = "1",
            title = "Create a Telegram Bot",
            body = "Open Telegram, search for '@BotFather', and send /newbot. Follow the prompt to get your unique Bot Token."
        )
        GuideStep(
            number = "2",
            title = "Add Token to PocketNode Settings",
            body = "Open PocketNode Settings -> Telegram tab. Paste your Bot Token and switch Telegram Bot to Active."
        )
        GuideStep(
            number = "3",
            title = "Send Magnet Links or Movie Titles",
            body = "Send any magnet link directly to your bot in Telegram. PocketNode will immediately start downloading the file at home."
        )
    }
}

@Composable
private fun WebGuide(context: Context) {
    GuideContainer(
        title = "WEB BROWSER CONSOLE",
        subtitle = "Lightweight browser interface on port 8080"
    ) {
        GuideStep(
            number = "1",
            title = "Open Any Web Browser",
            body = "On any phone, laptop, or tablet connected to your home Wi-Fi, open Chrome, Safari, Firefox, or Edge."
        )
        GuideStep(
            number = "2",
            title = "Enter the Web Panel URL",
            body = "Type the following address into your browser's URL bar:",
            copyText = "http://pocketnode.local:8080",
            context = context
        )
        GuideStep(
            number = "3",
            title = "Browse and Stream Directly",
            body = "You can browse your media vault, stream MP4/MKV video directly in the browser player, or upload new files."
        )
    }
}

@Composable
private fun NetworkGuide(context: Context) {
    GuideContainer(
        title = "NETWORK & ADDRESS BINDING",
        subtitle = "Permanent access without changing Wi-Fi IPs"
    ) {
        GuideStep(
            number = "1",
            title = "Why Do Wi-Fi IP Addresses Change?",
            body = "Home routers use DHCP to assign IP addresses dynamically. When your phone reconnects, its IP might change from 192.168.1.4 to 192.168.1.15."
        )
        GuideStep(
            number = "2",
            title = "The Solution: Zero-Config mDNS",
            body = "PocketNode broadcasts 'pocketnode.local' on your home network. Use this address instead of IP addresses so your connection never breaks:",
            copyText = "http://pocketnode.local:8096",
            context = context
        )
        GuideStep(
            number = "3",
            title = "Static DHCP Reservation",
            body = "For 100% rock-solid performance, open your home Wi-Fi router settings and reserve a static IP for your PocketNode phone's MAC address."
        )
    }
}

@Composable
private fun GuideContainer(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TacticalBorder))
        content()
    }
}

@Composable
private fun GuideStep(
    number: String,
    title: String,
    body: String,
    copyText: String? = null,
    context: Context? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(TacticalOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(1.dp, TacticalOrange, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Spacer(modifier = Modifier.height(3.dp))
            Text(body, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, lineHeight = 14.sp)

            if (copyText != null && context != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .background(TacticalSurface, RoundedCornerShape(3.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable {
                            val clip = ClipData.newPlainText("PocketNode URL", copyText)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied: $copyText", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(copyText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                    Text("[ COPY ]", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                }
            }
        }
    }
}
