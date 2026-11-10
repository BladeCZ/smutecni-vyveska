package cz.pshornicek.vyveska

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cz.pshornicek.vyveska.data.AppUpdateManager
import cz.pshornicek.vyveska.data.AppVersionInfo
import cz.pshornicek.vyveska.data.ObituaryRepository
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.DetailScreen
import cz.pshornicek.vyveska.ui.HomeScreen
import cz.pshornicek.vyveska.ui.theme.SmutecniVyveskaTheme
import cz.pshornicek.vyveska.work.ObituarySyncWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ObituaryRepository

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = ObituaryRepository(this)
        askNotificationPermissionIfNeeded()
        ObituarySyncWorker.schedulePeriodicSync(this)

        val openUpdateFromNotification = intent?.getBooleanExtra("open_update_dialog", false) == true

        setContent {
            SmutecniVyveskaTheme {
                var obituaries by remember { mutableStateOf<List<Obituary>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var selectedObituary by remember { mutableStateOf<Obituary?>(null) }
                var currentDbUrl by remember { mutableStateOf(repository.getDatabaseUrl()) }

                var notifyEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
                var notifySources by remember { mutableStateOf(repository.getNotificationSources()) }
                var notifyIncludeMemories by remember { mutableStateOf(repository.getNotifyIncludeMemories()) }

                var versionInfo by remember { mutableStateOf<AppVersionInfo?>(null) }
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateStatusMessage by remember { mutableStateOf<String?>(null) }
                var showUpdateDialog by remember { mutableStateOf(openUpdateFromNotification) }

                fun loadData(forceRefresh: Boolean = false) {
                    isLoading = true
                    errorMessage = null
                    lifecycleScope.launch {
                        val result = repository.getObituaries(forceRefresh)
                        result.onSuccess {
                            obituaries = it
                            isLoading = false
                        }.onFailure {
                            errorMessage = it.localizedMessage ?: "Nepodařilo se stáhnout parte"
                            isLoading = false
                        }
                    }
                }

                fun checkAppUpdate(manualTrigger: Boolean = false) {
                    isCheckingUpdate = true
                    if (manualTrigger) {
                        updateStatusMessage = "Kontroluji aktualizace na GitHubu..."
                    }
                    lifecycleScope.launch {
                        val res = AppUpdateManager.checkForUpdate(this@MainActivity)
                        isCheckingUpdate = false
                        res.onSuccess { info ->
                            versionInfo = info
                            if (info.isUpdateAvailable) {
                                updateStatusMessage = "Je dostupná nová verze ${info.remoteVersionName}!"
                                showUpdateDialog = true
                            } else if (manualTrigger) {
                                updateStatusMessage = "Máte nejnovější verzi aplikace (${info.localVersionName})."
                            }
                        }.onFailure { err ->
                            if (manualTrigger) {
                                updateStatusMessage = "Nepodařilo se ověřit aktualizaci: ${err.localizedMessage ?: "chyba sítě"}"
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    loadData(forceRefresh = true)
                    checkAppUpdate(manualTrigger = false)
                }

                if (selectedObituary != null) {
                    DetailScreen(
                        obituary = selectedObituary!!,
                        onBack = { selectedObituary = null }
                    )
                } else {
                    HomeScreen(
                        obituaries = obituaries,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        currentDbUrl = currentDbUrl,
                        notifyEnabled = notifyEnabled,
                        notifySources = notifySources,
                        notifyIncludeMemories = notifyIncludeMemories,
                        versionInfo = versionInfo,
                        isCheckingUpdate = isCheckingUpdate,
                        updateStatusMessage = updateStatusMessage,
                        showUpdateDialog = showUpdateDialog,
                        onDismissUpdateDialog = { showUpdateDialog = false },
                        onOpenUpdateDialog = { showUpdateDialog = true },
                        onCheckForUpdate = { checkAppUpdate(manualTrigger = true) },
                        onRefresh = { loadData(forceRefresh = true) },
                        onUpdateDbUrl = { newUrl ->
                            repository.setDatabaseUrl(newUrl)
                            currentDbUrl = newUrl
                            loadData(forceRefresh = true)
                        },
                        onSaveNotifySettings = { enabled, sources, includeMemories ->
                            repository.setNotificationsEnabled(enabled)
                            repository.setNotificationSources(sources)
                            repository.setNotifyIncludeMemories(includeMemories)
                            notifyEnabled = enabled
                            notifySources = sources
                            notifyIncludeMemories = includeMemories
                            ObituarySyncWorker.schedulePeriodicSync(this@MainActivity, replaceExisting = true)
                            if (enabled) {
                                askNotificationPermissionIfNeeded()
                            }
                        },
                        onTestNotification = {
                            askNotificationPermissionIfNeeded()
                            val sample = obituaries.firstOrNull { it.isActive } ?: obituaries.firstOrNull()
                            if (sample != null) {
                                repository.sendNotification(sample, 9999)
                            }
                        },
                        onSelectObituary = { item ->
                            selectedObituary = item
                        }
                    )
                }
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
