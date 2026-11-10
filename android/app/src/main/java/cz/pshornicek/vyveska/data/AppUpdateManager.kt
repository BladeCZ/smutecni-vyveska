package cz.pshornicek.vyveska.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class AppVersionInfo(
    val localVersionCode: Long,
    val localVersionName: String,
    val remoteVersionCode: Long,
    val remoteVersionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

enum class InstallLaunchStatus {
    LAUNCHED_INSTALLER,
    NEEDS_PERMISSION,
    ERROR
}

object AppUpdateManager {

    const val VERSION_JSON_URL = "https://github.com/BladeCZ/smutecni-vyveska/releases/download/parte-images/version.json"
    const val GITHUB_RELEASE_API_URL = "https://api.github.com/repos/BladeCZ/smutecni-vyveska/releases/tags/parte-images"
    const val DEFAULT_APK_URL = "https://github.com/BladeCZ/smutecni-vyveska/releases/download/parte-images/SmutecniVyveska.apk"

    private const val PREFS_NAME = "vyveska_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_code"

    fun getLocalPackageInfo(context: Context): PackageInfo {
        return context.packageManager.getPackageInfo(context.packageName, 0)
    }

    fun getLocalVersionCode(context: Context): Long {
        val pInfo = getLocalPackageInfo(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    }

    fun getLocalVersionName(context: Context): String {
        return getLocalPackageInfo(context).versionName ?: "2.0.0"
    }

    fun getDownloadedApkFile(context: Context): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "SmutecniVyveska.apk")
    }

    private fun openConnectionWithRedirects(urlStr: String, acceptHeader: String = "*/*"): HttpURLConnection {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmutecniVyveska-Android-Updater/2.2")
                setRequestProperty("Accept", acceptHeader)
            }
            val code = conn.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) break
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
            } else {
                return conn
            }
        }
        throw Exception("Příliš mnoho přesměrování při stahování z GitHubu")
    }

    suspend fun checkForUpdate(context: Context): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        val localCode = getLocalVersionCode(context)
        val localName = getLocalVersionName(context)
        val localUpdateTimeMs = getLocalPackageInfo(context).lastUpdateTime

        try {
            // 1. Nejprve zkusíme stáhnout version.json z GitHub Release
            try {
                val vConn = openConnectionWithRedirects(
                    "$VERSION_JSON_URL?t=${System.currentTimeMillis()}",
                    "application/json"
                )
                if (vConn.responseCode == 200) {
                    val body = vConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val remoteCode = json.optLong("versionCode", 0L)
                    val remoteName = json.optString("versionName", "")
                    val apkUrl = json.optString("apkUrl", DEFAULT_APK_URL).ifBlank { DEFAULT_APK_URL }
                    val apkSize = json.optLong("apkSize", 0L)
                    val updatedAt = json.optString("updatedAt", "")
                    val notes = json.optString("releaseNotes", "Nová verze aplikace Smuteční vývěska z GitHubu.")

                    val isNewer = (remoteCode > localCode) ||
                        (remoteName.isNotBlank() && remoteName != localName && remoteCode >= localCode)

                    return@withContext Result.success(
                        AppVersionInfo(
                            localVersionCode = localCode,
                            localVersionName = localName,
                            remoteVersionCode = remoteCode,
                            remoteVersionName = remoteName.ifBlank { "v$remoteCode" },
                            apkUrl = apkUrl,
                            apkSizeBytes = apkSize,
                            publishedAt = updatedAt,
                            releaseNotes = notes,
                            isUpdateAvailable = isNewer
                        )
                    )
                }
            } catch (_: Exception) {
                // Pokračujeme na záložní GitHub API
            }

            // 2. Záložní kontrola přes GitHub Releases API
            val apiConn = openConnectionWithRedirects(GITHUB_RELEASE_API_URL, "application/vnd.github+json")
            if (apiConn.responseCode == 200) {
                val body = apiConn.inputStream.bufferedReader().use { it.readText() }
                val releaseJson = JSONObject(body)
                val assets = releaseJson.optJSONArray("assets")

                var apkUrl = DEFAULT_APK_URL
                var apkSize = 0L
                var updatedAt = ""
                var remoteCodeFromVersionJson = 0L
                var remoteNameFromVersionJson = ""

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name == "SmutecniVyveska.apk") {
                            apkUrl = asset.optString("browser_download_url", DEFAULT_APK_URL)
                            apkSize = asset.optLong("size", 0L)
                            updatedAt = asset.optString("updated_at", "")
                        }
                    }
                }

                val remoteEpochMs = try {
                    if (updatedAt.isNotBlank()) Instant.parse(updatedAt).toEpochMilli() else 0L
                } catch (_: Exception) {
                    0L
                }

                // Pokud v release ještě není version.json, porovnáme čas sestavení APK s časem instalace aplikace (+15 min tolerance)
                val isNewerByTimestamp = remoteEpochMs > (localUpdateTimeMs + 15 * 60 * 1000L)
                val displayRemoteName = when {
                    remoteNameFromVersionJson.isNotBlank() -> remoteNameFromVersionJson
                    updatedAt.length >= 10 -> "GitHub (${updatedAt.take(10)})"
                    else -> "Nejnovější sestavení"
                }

                Result.success(
                    AppVersionInfo(
                        localVersionCode = localCode,
                        localVersionName = localName,
                        remoteVersionCode = remoteCodeFromVersionJson,
                        remoteVersionName = displayRemoteName,
                        apkUrl = apkUrl,
                        apkSizeBytes = apkSize,
                        publishedAt = updatedAt,
                        releaseNotes = "Nejnovější sestavení APK z GitHub Releases.",
                        isUpdateAvailable = isNewerByTimestamp
                    )
                )
            } else {
                Result.failure(Exception("GitHub API vrátilo HTTP ${apiConn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String = DEFAULT_APK_URL,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = getDownloadedApkFile(context)
            val tempFile = File(targetFile.parentFile, "SmutecniVyveska.apk.part")
            if (tempFile.exists()) tempFile.delete()

            val conn = openConnectionWithRedirects("$apkUrl?t=${System.currentTimeMillis()}")
            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("Chyba stahování APK: HTTP ${conn.responseCode}"))
            }

            val totalBytes = conn.contentLengthLong
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var lastReportedPercent = -1
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(downloadedBytes, totalBytes, percent)
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            onProgress(downloadedBytes, totalBytes, 100)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installDownloadedApk(context: Context, apkFile: File = getDownloadedApkFile(context)): InstallLaunchStatus {
        if (!apkFile.exists()) return InstallLaunchStatus.ERROR

        if (!canRequestPackageInstalls(context)) {
            openUnknownSourcesSettings(context)
            return InstallLaunchStatus.NEEDS_PERMISSION
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            InstallLaunchStatus.LAUNCHED_INSTALLER
        } catch (e: Exception) {
            InstallLaunchStatus.ERROR
        }
    }

    fun shouldNotifyBackgroundUpdate(context: Context, remoteVersionCode: Long): Boolean {
        if (remoteVersionCode <= 0L) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getLong(KEY_LAST_NOTIFIED_VERSION, 0L)
        return remoteVersionCode > lastNotified
    }

    fun markBackgroundUpdateNotified(context: Context, remoteVersionCode: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_NOTIFIED_VERSION, remoteVersionCode).apply()
    }
}
