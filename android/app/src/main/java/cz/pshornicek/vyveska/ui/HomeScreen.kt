package cz.pshornicek.vyveska.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.theme.AccentGold

enum class FilterType(val label: String) {
    ACTIVE("Aktuální vývěska"),
    ARCHIVE("Archiv"),
    ALL("Vše")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    obituaries: List<Obituary>,
    isLoading: Boolean,
    errorMessage: String?,
    currentDbUrl: String,
    onRefresh: () -> Unit,
    onUpdateDbUrl: (String) -> Unit,
    onSelectObituary: (Obituary) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Výchozí filtr: POUZE AKTUÁLNÍ VÝVĚSKA
    var selectedFilter by remember { mutableStateOf(FilterType.ACTIVE) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val filteredList = remember(obituaries, searchQuery, selectedFilter) {
        obituaries.filter { item ->
            val matchesFilter = when (selectedFilter) {
                FilterType.ACTIVE -> item.isActive
                FilterType.ARCHIVE -> item.isArchived
                FilterType.ALL -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                (item.name.lowercase().contains(q)) ||
                (item.residence?.lowercase()?.contains(q) == true) ||
                (item.deathDate?.lowercase()?.contains(q) == true) ||
                (item.deathCause?.lowercase()?.contains(q) == true) ||
                (item.ceremony?.lowercase()?.contains(q) == true)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✝", color = AccentGold, fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text("Smuteční vývěska", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Moravičany • Litovel • Zábřeh (1 týden od úmrtí)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Obnovit")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Nastavení")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Vyhledávací pole
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Hledat jméno, město, věk...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Záložky: Aktuální vývěska vs Archiv
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterType.values()) { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGold,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Obsah - Seznam bez náhledů obrázků
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && obituaries.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentGold
                    )
                } else if (filteredList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (errorMessage != null) "Chyba při stahování dat" else "Žádné výsledky",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                            ) {
                                Text("Zkusit znovu")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            ObituaryTableCard(obituary = item, onClick = { onSelectObituary(item) })
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        var tempUrl by remember { mutableStateOf(currentDbUrl) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Zdroj databáze") },
            text = {
                Column {
                    Text(
                        "URL adresa souboru database.json na GitHubu:",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateDbUrl(tempUrl)
                        showSettingsDialog = false
                        onRefresh()
                    }
                ) {
                    Text("Uložit a načíst")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }
}

@Composable
fun ObituaryTableCard(
    obituary: Obituary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Hlavička: Jméno + Kategorie
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = obituary.getDisplayNameWithAge(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // MĚSTO / BYDLIŠTĚ PŘÍMO POD JMÉNEM
                    if (!obituary.residence.isNullOrBlank()) {
                        Text(
                            text = "📍 ${obituary.residence}" + (if (!obituary.sourceName.isNullOrBlank()) " • ${obituary.sourceName}" else ""),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else if (!obituary.sourceName.isNullOrBlank()) {
                        Text(
                            text = obituary.sourceName,
                            fontSize = 12.sp,
                            color = AccentGold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                val isMemory = obituary.category == "Vzpomínka"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isMemory) Color(0xFF78350F) else Color(0xFF334155)
                ) {
                    Text(
                        text = obituary.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMemory) Color(0xFFFEF08A) else Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mřížka údajů: Věk, Datum úmrtí, Důvod
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (!obituary.age.isNullOrBlank()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("VĚK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(obituary.age, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!obituary.deathDate.isNullOrBlank()) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text("DATUM ÚMRTÍ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(obituary.deathDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (!obituary.deathCause.isNullOrBlank()) {
                        Column {
                            Text("DŮVOD / OKOLNOST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(obituary.deathCause, fontSize = 13.sp)
                        }
                    }

                    if (!obituary.ceremony.isNullOrBlank()) {
                        Column {
                            Text("POSLEDNÍ ROZLOUČENÍ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(obituary.ceremony, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tlačítko: Detail (otevře obrázek parte)
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zobrazit obrázek parte", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
