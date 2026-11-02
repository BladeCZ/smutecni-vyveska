package cz.pshornicek.vyveska

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import cz.pshornicek.vyveska.data.ObituaryRepository
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.DetailScreen
import cz.pshornicek.vyveska.ui.HomeScreen
import cz.pshornicek.vyveska.ui.theme.SmutecniVyveskaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ObituaryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = ObituaryRepository(this)

        setContent {
            SmutecniVyveskaTheme {
                var obituaries by remember { mutableStateOf<List<Obituary>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var selectedObituary by remember { mutableStateOf<Obituary?>(null) }
                var currentDbUrl by remember { mutableStateOf(repository.getDatabaseUrl()) }

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

                LaunchedEffect(Unit) {
                    loadData(forceRefresh = false)
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
                        onRefresh = { loadData(forceRefresh = true) },
                        onUpdateDbUrl = { newUrl ->
                            repository.setDatabaseUrl(newUrl)
                            currentDbUrl = newUrl
                        },
                        onSelectObituary = { item ->
                            selectedObituary = item
                        }
                    )
                }
            }
        }
    }
}
