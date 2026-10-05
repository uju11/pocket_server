package com.remotemedia.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.StorageManager
import com.remotemedia.services.JellyfinManager
import com.remotemedia.ui.theme.AccentGreen
import com.remotemedia.ui.theme.AccentRed
import com.remotemedia.ui.theme.BorderGrey
import com.remotemedia.ui.theme.LightBackground
import com.remotemedia.ui.theme.PrimaryBlue
import com.remotemedia.ui.theme.SurfaceLightGrey
import com.remotemedia.ui.theme.SurfaceWhite
import com.remotemedia.ui.theme.TextDark
import com.remotemedia.ui.theme.TextMuted

@Composable
fun JellyfinSettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var users by remember { mutableStateOf(JellyfinManager.getUsers()) }
    var libraries by remember { mutableStateOf(JellyfinManager.getLibraries()) }

    var newUserName by remember { mutableStateOf("") }
    var newUserIsAdmin by remember { mutableStateOf(true) }

    var newLibName by remember { mutableStateOf("") }
    var newLibType by remember { mutableStateOf("movies") }
    var newLibFolder by remember { mutableStateOf(StorageManager.getMoviesDir().absolutePath) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
            }
            Text(
                text = "🍿 Jellyfin Server Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Library Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Library", tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Media Library Collections",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Libraries List
                libraries.forEach { lib ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(SurfaceLightGrey, shape = RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "📁 ${lib.name} (${lib.collectionType})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = lib.folderPath, fontSize = 11.sp, color = TextMuted)
                        }
                        IconButton(
                            onClick = {
                                JellyfinManager.removeLibrary(context, lib.id)
                                libraries = JellyfinManager.getLibraries()
                                Toast.makeText(context, "Library removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Add New Library Collection:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newLibName,
                    onValueChange = { newLibName = it },
                    label = { Text("Library Name (e.g., Movies, Shows)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = TextMuted)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newLibFolder,
                    onValueChange = { newLibFolder = it },
                    label = { Text("Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = TextMuted)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (newLibName.isBlank()) {
                            Toast.makeText(context, "Please enter a library name", Toast.LENGTH_SHORT).show()
                        } else {
                            JellyfinManager.addLibrary(context, newLibName.trim(), newLibType, newLibFolder.trim())
                            libraries = JellyfinManager.getLibraries()
                            newLibName = ""
                            Toast.makeText(context, "New library added!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Media Library", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "Users", tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "User Account Management",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Users List
                users.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(SurfaceLightGrey, shape = RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "👤 ${user.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = if (user.isAdmin) "Administrator" else "Standard User", fontSize = 11.sp, color = if (user.isAdmin) PrimaryBlue else TextMuted)
                        }

                        if (users.size > 1) {
                            IconButton(
                                onClick = {
                                    JellyfinManager.removeUser(context, user.id)
                                    users = JellyfinManager.getUsers()
                                    Toast.makeText(context, "User removed", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete User", tint = AccentRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Create New Jellyfin User:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = TextMuted)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Grant Administrator Access", fontSize = 12.sp, color = TextDark)
                    Switch(checked = newUserIsAdmin, onCheckedChange = { newUserIsAdmin = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (newUserName.isBlank()) {
                            Toast.makeText(context, "Please enter a username", Toast.LENGTH_SHORT).show()
                        } else {
                            JellyfinManager.addUser(context, newUserName.trim(), newUserIsAdmin)
                            users = JellyfinManager.getUsers()
                            newUserName = ""
                            Toast.makeText(context, "User created successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
