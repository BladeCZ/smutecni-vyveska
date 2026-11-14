package cz.pshornicek.vyveska

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import cz.pshornicek.vyveska.data.AppUpdateManager
import cz.pshornicek.vyveska.data.AppVersionInfo
import cz.pshornicek.vyveska.data.ObituaryRepository
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.DetailScreen
import cz.pshornicek.vyveska.ui.HomeScreen
import cz.pshornicek.vyveska.ui.theme.SmutecniVyveskaTheme
import cz.pshornicek.vyveska.work.ObituarySyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
                var unreadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var unreadInitialized by remember { mutableStateOf(false) }
                var markedReadThisSession by remember { mutableStateOf(false) }

                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var selectedObituary by remember { mutableStateOf<Obituary?>(null) }

                var notifyEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
                var notifySources by remember { mutableStateOf(repository.getNotificationSources()) }
                var notifyIncludeMemories by remember { mutableStateOf(repository.getNotifyIncludeMemories()) }
                var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimizationIgnored()) }

                var versionInfo by remember { mutableStateOf<AppVersionInfo?>(null) }
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateCheckError by remember { mutableStateOf<String?>(null) }
                var showUpdateDialog by remember { mutableStateOf(openUpdateFromNotification) }

                fun loadData(forceRefresh: Boolean = false) {
                    isLoading = true
                    errorMessage = null
                    lifecycleScope.launch {
                        val result = repository.getObituaries(forceRefresh)
                        result.onSuccess { list ->
                            obituaries = list
                            if (!unreadInitialized && list.isNotEmpty()) {
                                val readIds = repository.getReadObituaryIds(list)
                                unreadIds = list.filter { it.isActive && !readIds.contains(it.id) }.map { it.id }.toSet()
                                unreadInitialized = true
                            }
                            isLoading = false
                        }.onFailure {
                            errorMessage = it.localizedMessage ?: "Nepodařilo se stáhnout parte"
                            isLoading = false
                        }
                    }
                }

                fun checkAppUpdate(autoShowDialogIfAvailable: Boolean = false) {
                    isCheckingUpdate = true
                    updateCheckError = null
                    lifecycleScope.launch {
                        val res = AppUpdateManager.checkForUpdate(this@MainActivity)
                        isCheckingUpdate = false
                        res.onSuccess { info ->
                            versionInfo = info
                            if (autoShowDialogIfAvailable && info.isUpdateAvailable) {
                                showUpdateDialog = true
                            }
                        }.onFailure { err ->
                            updateCheckError = err.localizedMessage ?: "Chyba sítě"
                        }
                    }
                }

                // 10vteřinový časovač v aktivním popředí:
                // Pokud uživatel zavře aplikaci dříve než za 10s (omyl), indikátory NOVÉ zůstanou.
                // Pokud zůstane déle než 10s, označí se parte jako přečtená a při dalším otevření se indikátory skryjí.
                val lifecycleOwner = this@MainActivity
                DisposableEffect(lifecycleOwner, obituaries.size, markedReadThisSession) {
                    var timerJob: Job? = null
                    fun startReadTimerIfNeeded() {
                        if (markedReadThisSession || obituaries.isEmpty()) return
                        timerJob?.cancel()
                        timerJob = lifecycleScope.launch {
                            delay(10_000L)
                            repository.markObituariesAsRead(obituaries.map { it.id }.toSet())
                            markedReadThisSession = true
                        }
                    }

                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                isBatteryOptIgnored = checkBatteryOptimizationIgnored()
                                startReadTimerIfNeeded()
                            }
                            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                                timerJob?.cancel()
                                timerJob = null
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        startReadTimerIfNeeded()
                    }
                    onDispose {
                        timerJob?.cancel()
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    loadData(forceRefresh = true)
                    checkAppUpdate(autoShowDialogIfAvailable = true)
                }

                if (selectedObituary != null) {
                    DetailScreen(
                        obituary = selectedObituary!!,
                        onBack = { selectedObituary = null },
                        onUpdateObituary = { updated, onResult ->
                            obituaries = obituaries.map { if (it.id == updated.id) updated else it }
                            selectedObituary = updated
                            lifecycleScope.launch {
                                val res = repository.saveObituaryOverride(updated)
                                onResult(res.getOrDefault(false))
                            }
                        }
                    )
                } else {
                    HomeScreen(
                        obituaries = obituaries,
                        unreadIds = unreadIds,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        notifyEnabled = notifyEnabled,
                        notifySources = notifySources,
                        notifyIncludeMemories = notifyIncludeMemories,
                        isBatteryOptimizationIgnored = isBatteryOptIgnored,
                        versionInfo = versionInfo,
                        isCheckingUpdate = isCheckingUpdate,
                        updateCheckError = updateCheckError,
                        showUpdateDialog = showUpdateDialog,
                        onDismissUpdateDialog = { showUpdateDialog = false },
                        onOpenUpdateDialog = { showUpdateDialog = true },
                        onCheckForUpdate = { checkAppUpdate(autoShowDialogIfAvailable = false) },
                        onRequestIgnoreBatteryOptimization = { requestBatteryOptimizationExemption() },
                        onRefresh = { loadData(forceRefresh = true) },
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

    private fun checkBatteryOptimizationIgnored(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(packageName) == true
        } catch (_: Exception) {
            true
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
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
