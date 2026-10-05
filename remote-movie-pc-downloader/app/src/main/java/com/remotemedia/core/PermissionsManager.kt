package com.remotemedia.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class FilePermissionRule(
    val filePath: String,
    val fileName: String,
    val category: String,
    val visibleToGuest: Boolean = false,
    val visibleToKids: Boolean = false,
    val uploadedByRole: String = "ADMIN", // "ADMIN", "GUEST"
    val timestamp: Long = System.currentTimeMillis()
)

data class DevicePolicy(
    val deviceId: String,
    val deviceName: String,
    val clientType: String, // "JELLYFIN", "DLNA", "WEB", "CLIENT_APP"
    val ipAddress: String,
    val assignedRole: String = "GUEST", // "ADMIN", "GUEST", "KIDS"
    val allowedCategories: List<String> = StorageManager.CURATED_CATEGORIES,
    val forceDirectPlay: Boolean = false,
    val isBlocked: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

object PermissionsManager {

    private val gson = Gson()
    private var context: Context? = null

    // In-memory state flows for UI reactivity
    private val _fileRules = MutableStateFlow<Map<String, FilePermissionRule>>(emptyMap())
    val fileRules: StateFlow<Map<String, FilePermissionRule>> = _fileRules.asStateFlow()

    private val _devicePolicies = MutableStateFlow<Map<String, DevicePolicy>>(emptyMap())
    val devicePolicies: StateFlow<Map<String, DevicePolicy>> = _devicePolicies.asStateFlow()

    fun init(appContext: Context) {
        context = appContext
        loadRules()
        loadDevices()
    }

    // ─── FILE PERMISSION CONTROLS ─────────────────────────────────────────────

    private fun loadRules() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("pocketnode_permissions", Context.MODE_PRIVATE)
        val json = prefs.getString("file_rules_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<Map<String, FilePermissionRule>>() {}.type
                val map: Map<String, FilePermissionRule> = gson.fromJson(json, type)
                _fileRules.value = map
            } catch (e: Exception) {
                Logger.e(LogTag.SYSTEM, "Failed to load file permissions: ${e.message}")
            }
        }
    }

    private fun persistRules() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("pocketnode_permissions", Context.MODE_PRIVATE)
        val json = gson.toJson(_fileRules.value)
        prefs.edit().putString("file_rules_json", json).apply()
    }

    fun getRuleForFile(file: File, category: String, uploadedByRole: String = "ADMIN"): FilePermissionRule {
        val existing = _fileRules.value[file.absolutePath]
        if (existing != null) return existing

        // Default rules:
        // Admin upload -> Admin only (false, false)
        // Guest upload -> Visible to all guests & admin (true, false)
        val defaultGuest = uploadedByRole.equals("GUEST", ignoreCase = true)
        val newRule = FilePermissionRule(
            filePath = file.absolutePath,
            fileName = file.name,
            category = category,
            visibleToGuest = defaultGuest,
            visibleToKids = false,
            uploadedByRole = uploadedByRole
        )
        setFileRule(newRule)
        return newRule
    }

    fun setFileRule(rule: FilePermissionRule) {
        val updated = _fileRules.value.toMutableMap()
        updated[rule.filePath] = rule
        _fileRules.value = updated
        persistRules()
    }

    fun toggleGuestAccess(filePath: String) {
        val current = _fileRules.value[filePath] ?: return
        val updated = current.copy(visibleToGuest = !current.visibleToGuest)
        setFileRule(updated)
    }

    fun toggleKidsAccess(filePath: String) {
        val current = _fileRules.value[filePath] ?: return
        val updated = current.copy(visibleToKids = !current.visibleToKids)
        setFileRule(updated)
    }

    fun canUserAccessFile(filePath: String, userRole: String): Boolean {
        if (userRole.equals("ADMIN", ignoreCase = true)) return true
        val rule = _fileRules.value[filePath] ?: return false
        return if (userRole.equals("KIDS", ignoreCase = true)) {
            rule.visibleToKids
        } else {
            // GUEST role
            rule.visibleToGuest
        }
    }

    // ─── DEVICE POLICY CONTROLS ───────────────────────────────────────────────

    private fun loadDevices() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("pocketnode_permissions", Context.MODE_PRIVATE)
        val json = prefs.getString("device_policies_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<Map<String, DevicePolicy>>() {}.type
                val map: Map<String, DevicePolicy> = gson.fromJson(json, type)
                _devicePolicies.value = map
            } catch (e: Exception) {
                Logger.e(LogTag.SYSTEM, "Failed to load device policies: ${e.message}")
            }
        } else {
            // Seed sample detected devices for immediate demonstration & test
            seedSampleDevices()
        }
    }

    private fun persistDevices() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("pocketnode_permissions", Context.MODE_PRIVATE)
        val json = gson.toJson(_devicePolicies.value)
        prefs.edit().putString("device_policies_json", json).apply()
    }

    private fun seedSampleDevices() {
        val samples = mapOf(
            "dev_living_room_tv" to DevicePolicy(
                deviceId = "dev_living_room_tv",
                deviceName = "Living Room Sony 4K TV",
                clientType = "JELLYFIN",
                ipAddress = "192.168.1.105",
                assignedRole = "GUEST",
                allowedCategories = listOf("Movies", "Videos"),
                forceDirectPlay = true,
                isBlocked = false
            ),
            "dev_kids_ipad" to DevicePolicy(
                deviceId = "dev_kids_ipad",
                deviceName = "Kids iPad Pro",
                clientType = "DLNA",
                ipAddress = "192.168.1.112",
                assignedRole = "KIDS",
                allowedCategories = listOf("Movies", "Music"),
                forceDirectPlay = false,
                isBlocked = false
            ),
            "dev_admin_pc" to DevicePolicy(
                deviceId = "dev_admin_pc",
                deviceName = "Main Desktop PC",
                clientType = "WEB",
                ipAddress = "192.168.1.50",
                assignedRole = "ADMIN",
                allowedCategories = StorageManager.CURATED_CATEGORIES,
                forceDirectPlay = true,
                isBlocked = false
            )
        )
        _devicePolicies.value = samples
        persistDevices()
    }

    fun registerOrUpdateDevice(
        deviceId: String,
        deviceName: String,
        clientType: String,
        ipAddress: String
    ): DevicePolicy {
        val existing = _devicePolicies.value[deviceId]
        val updated = if (existing != null) {
            existing.copy(
                deviceName = if (deviceName.isNotBlank()) deviceName else existing.deviceName,
                ipAddress = ipAddress,
                lastSeen = System.currentTimeMillis()
            )
        } else {
            DevicePolicy(
                deviceId = deviceId,
                deviceName = deviceName.ifEmpty { "Network Client ($ipAddress)" },
                clientType = clientType,
                ipAddress = ipAddress,
                assignedRole = "GUEST",
                allowedCategories = StorageManager.CURATED_CATEGORIES,
                forceDirectPlay = false,
                isBlocked = false
            )
        }
        val map = _devicePolicies.value.toMutableMap()
        map[deviceId] = updated
        _devicePolicies.value = map
        persistDevices()
        return updated
    }

    fun setDeviceRole(deviceId: String, newRole: String) {
        val existing = _devicePolicies.value[deviceId] ?: return
        val updated = existing.copy(assignedRole = newRole)
        val map = _devicePolicies.value.toMutableMap()
        map[deviceId] = updated
        _devicePolicies.value = map
        persistDevices()
    }

    fun toggleDeviceDirectPlay(deviceId: String) {
        val existing = _devicePolicies.value[deviceId] ?: return
        val updated = existing.copy(forceDirectPlay = !existing.forceDirectPlay)
        val map = _devicePolicies.value.toMutableMap()
        map[deviceId] = updated
        _devicePolicies.value = map
        persistDevices()
    }

    fun toggleDeviceBlock(deviceId: String) {
        val existing = _devicePolicies.value[deviceId] ?: return
        val updated = existing.copy(isBlocked = !existing.isBlocked)
        val map = _devicePolicies.value.toMutableMap()
        map[deviceId] = updated
        _devicePolicies.value = map
        persistDevices()
    }

    fun toggleCategoryForDevice(deviceId: String, category: String) {
        val existing = _devicePolicies.value[deviceId] ?: return
        val list = existing.allowedCategories.toMutableList()
        if (list.contains(category)) {
            list.remove(category)
        } else {
            list.add(category)
        }
        val updated = existing.copy(allowedCategories = list)
        val map = _devicePolicies.value.toMutableMap()
        map[deviceId] = updated
        _devicePolicies.value = map
        persistDevices()
    }

    fun isDeviceBlocked(deviceId: String): Boolean {
        return _devicePolicies.value[deviceId]?.isBlocked == true
    }

    fun getDevicePolicy(deviceId: String): DevicePolicy? {
        return _devicePolicies.value[deviceId]
    }
}
