package cz.pshornicek.vyveska.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cz.pshornicek.vyveska.BuildConfig
import cz.pshornicek.vyveska.MainActivity
import cz.pshornicek.vyveska.model.Obituary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class ObituaryRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "obituaries_cache.json")

    companion object {
        const val PREFS_NAME = "vyveska_prefs"
        const val KEY_KNOWN_IDS = "known_obituary_ids"
        const val KEY_READ_IDS = "read_obituary_ids"
        const val KEY_LOCAL_OVERRIDES = "local_obituary_overrides"
        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_SOURCES = "notify_sources"
        const val KEY_NOTIFY_MEMORIES = "notify_include_memories"
        const val DEFAULT_FALLBACK_URL = "https://bladecz.github.io/smutecni-vyveska/data/database.json"
        const val CHANNEL_ID = "smutecni_vyveska_notifications_high"

        val ALL_SOURCES = listOf(
            "Horníček Moravičany",
            "Hrandop Litovel",
            "Drozd Zábřeh"
        )

        fun getOverridesUrl(): String {
            val cloud = BuildConfig.CLOUDINARY_CLOUD_NAME.ifBlank { "pf9pdwlp" }
            return "https://res.cloudinary.com/$cloud/raw/upload/smutecni-vyveska/overrides.json"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nová smuteční oznámení",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automatická upozornění na nově přidaná parte na smuteční vývěsce"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun getDatabaseUrl(): String = DEFAULT_FALLBACK_URL

    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFY_ENABLED, true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()
    }

    fun getNotificationSources(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFY_SOURCES, ALL_SOURCES.toSet())?.toSet() ?: ALL_SOURCES.toSet()
    }

    fun setNotificationSources(sources: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFY_SOURCES, HashSet(sources)).apply()
    }

    fun getNotifyIncludeMemories(): Boolean {
        return prefs.getBoolean(KEY_NOTIFY_MEMORIES, false)
    }

    fun setNotifyIncludeMemories(include: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_MEMORIES, include).apply()
    }

    /**
     * Vrátí množinu ID parte, která již uživatel viděl (po otevření aplikace déle než 10 vteřin).
     * Při úplně prvním spuštění s touto funkcí označí jako přečtená všechna archivní parte
     * a starší aktivní parte, aby indikátor "NOVÉ" svítil jen u nejnovějších (max. 3) aktuálních parte.
     */
    fun getReadObituaryIds(currentItems: List<Obituary>): Set<String> {
        if (!prefs.contains(KEY_READ_IDS)) {
            val activeIds = currentItems
                .filter { it.isActive && it.category != "Vzpomínka" }
                .take(3)
                .map { it.id }
                .toSet()
            val fallbackActiveIds = if (activeIds.isEmpty()) {
                currentItems.filter { it.isActive }.take(3).map { it.id }.toSet()
            } else activeIds
            val initialRead = currentItems
                .map { it.id }
                .filter { !fallbackActiveIds.contains(it) }
                .toSet()
            prefs.edit().putStringSet(KEY_READ_IDS, HashSet(initialRead)).apply()
            return initialRead
        }
        return prefs.getStringSet(KEY_READ_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun markObituariesAsRead(ids: Set<String>) {
        if (ids.isEmpty()) return
        val existing = prefs.getStringSet(KEY_READ_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.addAll(ids)
        prefs.edit().putStringSet(KEY_READ_IDS, HashSet(existing)).apply()
    }

    private fun normalizeAndSortItems(rawItems: List<Obituary>): List<Obituary> {
        val refDate = LocalDate.now()

        return rawItems.map { item ->
            val active = item.isActiveWithinWeek(refDate)
            item.copy(
                isActive = active,
                isArchived = !active,
                sourceName = if (item.sourceName.isNullOrBlank() || item.sourceName == "PS Horníček") "Horníček Moravičany" else item.sourceName
            )
        }.sortedWith(
            compareByDescending<Obituary> {
                it.deathDateIso?.takeIf { s -> s.isNotBlank() } ?: it.firstSeen?.take(10) ?: "0000-00-00"
            }.thenBy { it.name }
        )
    }

    private fun loadLocalOverridesJson(): JSONObject {
        val raw = prefs.getString(KEY_LOCAL_OVERRIDES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveLocalOverridesJson(obj: JSONObject) {
        prefs.edit().putString(KEY_LOCAL_OVERRIDES, obj.toString()).apply()
    }

    private fun fetchRemoteOverridesJson(): JSONObject {
        return try {
            val url = URL("${getOverridesUrl()}?t=${System.currentTimeMillis()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmutecniVyveska-Android/2.3")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else {
                JSONObject()
            }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun mergeOverridesIntoItems(items: List<Obituary>, remoteOverrides: JSONObject, localOverrides: JSONObject): List<Obituary> {
        return items.map { item ->
            val remotePatch = remoteOverrides.optJSONObject(item.id)
            val localPatch = localOverrides.optJSONObject(item.id)
            val patch = when {
                remotePatch != null && localPatch != null -> {
                    val merged = JSONObject(remotePatch.toString())
                    localPatch.keys().forEach { k -> merged.put(k, localPatch.get(k)) }
                    merged
                }
                remotePatch != null -> remotePatch
                localPatch != null -> localPatch
                else -> null
            }
            if (patch != null) applyPatchToObituary(item, patch) else item
        }
    }

    private fun applyPatchToObituary(item: Obituary, patch: JSONObject): Obituary {
        val patchedName = if (patch.has("name")) patch.optString("name", item.name) else item.name
        val patchedGender = if (patch.has("gender")) patch.optString("gender").ifBlank { null } else item.gender
        val patchedCategory = if (patch.has("category")) patch.optString("category", item.category) else item.category
        val patchedAge = if (patch.has("age")) patch.optString("age").ifBlank { null } else item.age
        val patchedDeathDate = if (patch.has("death_date")) patch.optString("death_date").ifBlank { null } else item.deathDate
        val patchedDeathDateIso = if (patch.has("death_date_iso")) patch.optString("death_date_iso").ifBlank { null } else item.deathDateIso
        val patchedDeathCause = if (patch.has("death_cause")) patch.optString("death_cause").ifBlank { null } else item.deathCause
        val patchedResidence = if (patch.has("residence")) patch.optString("residence").ifBlank { null } else item.residence
        val patchedCeremony = if (patch.has("ceremony")) patch.optString("ceremony").ifBlank { null } else item.ceremony
        val patchedLat = if (patch.has("lat") && !patch.isNull("lat")) patch.optDouble("lat") else item.lat
        val patchedLon = if (patch.has("lon") && !patch.isNull("lon")) patch.optDouble("lon") else item.lon

        return item.copy(
            name = patchedName.ifBlank { item.name },
            gender = patchedGender,
            category = patchedCategory.ifBlank { item.category },
            age = patchedAge,
            deathDate = patchedDeathDate,
            deathDateIso = patchedDeathDateIso,
            deathCause = patchedDeathCause,
            residence = patchedResidence,
            lat = patchedLat,
            lon = patchedLon,
            ceremony = patchedCeremony,
            manualEdit = true
        )
    }

    private fun buildPatchJson(item: Obituary): JSONObject {
        return JSONObject().apply {
            put("name", item.name)
            put("gender", item.gender ?: "")
            put("category", item.category)
            put("age", item.age ?: "")
            put("death_date", item.deathDate ?: "")
            put("death_date_iso", item.deathDateIso ?: "")
            put("death_cause", item.deathCause ?: "")
            put("residence", item.residence ?: "")
            if (item.lat != null && item.lon != null) {
                put("lat", item.lat)
                put("lon", item.lon)
            }
            put("ceremony", item.ceremony ?: "")
            put("manual_edit", true)
            put("updated_at", Instant.now().toString())
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun uploadOverridesToCloudinary(overridesJson: JSONObject): Boolean {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.ifBlank { "pf9pdwlp" }
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return false
        }

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val folder = "smutecni-vyveska"
        val publicId = "overrides.json"
        val paramsToSign = "folder=$folder&invalidate=true&overwrite=true&public_id=$publicId&timestamp=$timestamp$apiSecret"
        val signature = sha1Hex(paramsToSign)

        val boundary = "----VyveskaBoundary${System.currentTimeMillis()}"
        val uploadUrl = "https://api.cloudinary.com/v1_1/$cloudName/raw/upload"
        val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val fields = listOf(
            "api_key" to apiKey,
            "timestamp" to timestamp,
            "public_id" to publicId,
            "folder" to folder,
            "overwrite" to "true",
            "invalidate" to "true",
            "signature" to signature
        )

        DataOutputStream(conn.outputStream).use { out ->
            for ((k, v) in fields) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
                out.write(v.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"overrides.json\"\r\n")
            out.writeBytes("Content-Type: application/json\r\n\r\n")
            out.write(overridesJson.toString(2).toByteArray(Charsets.UTF_8))
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
        }

        return conn.responseCode in 200..299
    }

    /**
     * Uloží upravené údaje parte lokálně i do sdílené databáze na Cloudu (overrides.json),
     * odkud změnu okamžitě načítá web i aplikace a při příštím běhu ji uloží i scraper do GitHub databáze.
     */
    suspend fun saveObituaryOverride(updated: Obituary): Result<Boolean> = withContext(Dispatchers.IO) {
        val patch = buildPatchJson(updated)

        // 1. Lokální uložení do SharedPreferences + aktualizace lokální cache
        val localOverrides = loadLocalOverridesJson()
        localOverrides.put(updated.id, patch)
        saveLocalOverridesJson(localOverrides)

        try {
            val cached = loadFromCache()
            if (cached != null) {
                val updatedCache = cached.map { if (it.id == updated.id) updated else it }
                cacheFile.writeText(gson.toJson(updatedCache))
            }
        } catch (_: Exception) {
        }

        // 2. Synchronizace s cloudovou databází overrides.json na Cloudinary
        return@withContext try {
            val remoteOverrides = fetchRemoteOverridesJson()
            localOverrides.keys().forEach { idKey ->
                remoteOverrides.put(idKey, localOverrides.getJSONObject(idKey))
            }
            remoteOverrides.put(updated.id, patch)
            val uploaded = uploadOverridesToCloudinary(remoteOverrides)
            Result.success(uploaded)
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    suspend fun getObituaries(forceRefresh: Boolean = false): Result<List<Obituary>> = withContext(Dispatchers.IO) {
        val localOverrides = loadLocalOverridesJson()

        if (!forceRefresh && cacheFile.exists()) {
            val cached = loadFromCache()
            if (cached != null && cached.isNotEmpty()) {
                val merged = mergeOverridesIntoItems(cached, JSONObject(), localOverrides)
                return@withContext Result.success(normalizeAndSortItems(merged))
            }
        }

        val urlString = getDatabaseUrl()
        try {
            val url = URL("$urlString?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmutecniVyveska-Android/2.3")
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<Obituary>>() {}.type
                val rawItems: List<Obituary> = gson.fromJson(jsonString, type)
                val remoteOverrides = fetchRemoteOverridesJson()
                val merged = mergeOverridesIntoItems(rawItems, remoteOverrides, localOverrides)
                val items = normalizeAndSortItems(merged)

                checkAndNotifyNewItems(items)
                cacheFile.writeText(jsonString)
                Result.success(items)
            } else {
                val cached = loadFromCache()
                if (cached != null) {
                    val merged = mergeOverridesIntoItems(cached, JSONObject(), localOverrides)
                    Result.success(normalizeAndSortItems(merged))
                } else {
                    Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
                }
            }
        } catch (e: Exception) {
            val cached = loadFromCache()
            if (cached != null) {
                val merged = mergeOverridesIntoItems(cached, JSONObject(), localOverrides)
                Result.success(normalizeAndSortItems(merged))
            } else {
                Result.failure(e)
            }
        }
    }

    private fun checkAndNotifyNewItems(items: List<Obituary>) {
        val knownSet = prefs.getStringSet(KEY_KNOWN_IDS, null)
        val currentIds = items.map { it.id }.toSet()

        if (knownSet != null && knownSet.isNotEmpty() && isNotificationsEnabled()) {
            val allowedSources = getNotificationSources()
            val includeMemories = getNotifyIncludeMemories()

            val newMatchingItems = items.filter { item ->
                item.isActive &&
                !knownSet.contains(item.id) &&
                allowedSources.contains(item.getEffectiveSource()) &&
                (includeMemories || item.category != "Vzpomínka")
            }

            newMatchingItems.take(5).forEachIndexed { idx, item ->
                sendNotification(item, 1000 + (item.id.hashCode() and 0x7FFFFFFF) % 8000 + idx)
            }
        }

        prefs.edit().putStringSet(KEY_KNOWN_IDS, HashSet(currentIds)).apply()
    }

    fun sendNotification(item: Obituary, notificationId: Int = 1001) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val townPart = if (!item.residence.isNullOrBlank()) "📍 ${item.residence} • " else ""
            val datePart = if (!item.deathDate.isNullOrBlank()) "Úmrtí: ${item.deathDate}" else ""
            val srcPart = " (${item.getEffectiveSource()})"
            val prefix = if (item.category == "Vzpomínka") "🕯️ Vzpomínka: " else "✝ Nové parte: "

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$prefix${item.getDisplayNameWithAge()}")
                .setContentText("$townPart$datePart$srcPart")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    fun sendAppUpdateNotification(versionInfo: AppVersionInfo) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_update_dialog", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                9500,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("⬆️ Nová verze aplikace (${versionInfo.remoteVersionName})")
                .setContentText("Klepněte pro stažení a instalaci aktualizace z GitHubu.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(9500, notification)
        } catch (_: Exception) {
        }
    }

    private fun loadFromCache(): List<Obituary>? {
        return try {
            if (cacheFile.exists()) {
                val jsonString = cacheFile.readText()
                val type = object : TypeToken<List<Obituary>>() {}.type
                gson.fromJson(jsonString, type)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
