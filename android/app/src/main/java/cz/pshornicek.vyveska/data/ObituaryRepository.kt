package cz.pshornicek.vyveska.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cz.pshornicek.vyveska.model.Obituary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ObituaryRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "obituaries_cache.json")

    // Výchozí URL na hostovanou databázi (např. GitHub Pages nebo raw GitHub)
    // Uživatel si může v aplikaci nastavit libovolnou URL svého repozitáře
    companion object {
        const val PREFS_NAME = "vyveska_prefs"
        const val KEY_DATABASE_URL = "database_url"
        // Zástupná URL, uživatel zadá své GitHub uživatelské jméno / repozitář
        const val DEFAULT_FALLBACK_URL = "https://raw.githubusercontent.com/username/smutecni-vyveska/main/data/database.json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDatabaseUrl(): String {
        return prefs.getString(KEY_DATABASE_URL, DEFAULT_FALLBACK_URL) ?: DEFAULT_FALLBACK_URL
    }

    fun setDatabaseUrl(url: String) {
        prefs.edit().putString(KEY_DATABASE_URL, url).apply()
    }

    suspend fun getObituaries(forceRefresh: Boolean = false): Result<List<Obituary>> = withContext(Dispatchers.IO) {
        // Pokud nemáme vynucený refresh a existuje lokální cache, zkusíme nejprve načíst z cache
        if (!forceRefresh && cacheFile.exists()) {
            val cached = loadFromCache()
            if (cached != null && cached.isNotEmpty()) {
                return@withContext Result.success(cached)
            }
        }

        // Pokus o stažení z internetu
        val urlString = getDatabaseUrl()
        try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmutecniVyveska-Android/1.0")
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<Obituary>>() {}.type
                val items: List<Obituary> = gson.fromJson(jsonString, type)

                // Uložit do lokální cache pro offline použití
                cacheFile.writeText(jsonString)
                Result.success(items)
            } else {
                // Fallback na lokální cache při chybě sítě
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
