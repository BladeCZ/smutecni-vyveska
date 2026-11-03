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
import cz.pshornicek.vyveska.MainActivity
import cz.pshornicek.vyveska.model.Obituary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ObituaryRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "obituaries_cache.json")

    companion object {
        const val PREFS_NAME = "vyveska_prefs"
        const val KEY_DATABASE_URL = "database_url"
        const val KEY_KNOWN_IDS = "known_obituary_ids"
        const val DEFAULT_FALLBACK_URL = "https://bladecz.github.io/smutecni-vyveska/data/database.json"
        const val CHANNEL_ID = "smutecni_vyveska_notifications"
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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upozornění na nově přidaná parte na smuteční vývěsce"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun getDatabaseUrl(): String {
        return prefs.getString(KEY_DATABASE_URL, DEFAULT_FALLBACK_URL) ?: DEFAULT_FALLBACK_URL
    }

    fun setDatabaseUrl(url: String) {
        prefs.edit().putString(KEY_DATABASE_URL, url).apply()
    }

    suspend fun getObituaries(forceRefresh: Boolean = false): Result<List<Obituary>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cacheFile.exists()) {
            val cached = loadFromCache()
            if (cached != null && cached.isNotEmpty()) {
                return@withContext Result.success(cached)
            }
        }

        val urlString = getDatabaseUrl()
        try {
            val url = URL("$urlString?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmutecniVyveska-Android/1.1")
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<Obituary>>() {}.type
                val items: List<Obituary> = gson.fromJson(jsonString, type)

                checkAndNotifyNewItems(items)
                cacheFile.writeText(jsonString)
                Result.success(items)
            } else {
                val cached = loadFromCache()
                if (cached != null) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
                }
            }
        } catch (e: Exception) {
            val cached = loadFromCache()
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun checkAndNotifyNewItems(items: List<Obituary>) {
        val knownSet = prefs.getStringSet(KEY_KNOWN_IDS, null)
        val currentIds = items.map { it.id }.toSet()

        if (knownSet != null && knownSet.isNotEmpty()) {
            val newItems = items.filter { it.isActive && !knownSet.contains(it.id) }
            newItems.take(3).forEachIndexed { idx, item ->
                sendNotification(item, 1000 + idx)
            }
        }

        prefs.edit().putStringSet(KEY_KNOWN_IDS, currentIds).apply()
    }

    fun sendNotification(item: Obituary, notificationId: Int = 1001) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val townPart = if (!item.residence.isNullOrBlank()) "📍 ${item.residence} • " else ""
            val datePart = if (!item.deathDate.isNullOrBlank()) "Úmrtí: ${item.deathDate}" else ""
            val srcPart = if (!item.sourceName.isNullOrBlank()) " (${item.sourceName})" else ""

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("✝ ${item.getDisplayNameWithAge()}")
                .setContentText("$townPart$datePart$srcPart")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, notification)
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
