package cz.pshornicek.vyveska.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.theme.AccentGold
import cz.pshornicek.vyveska.ui.theme.BorderColor

enum class FilterType(val label: String) {
    ALL("Vše"),
    ACTIVE("Vývěska"),
    ANNOUNCEMENT("Oznámení"),
    MEMORY("Vzpomínky"),
    ARCHIVE("Archiv")
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
    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Filtrování
    val filteredList = remember(obituaries, searchQuery, selectedFilter) {
        obituaries.filter { item ->
            val matchesFilter = when (selectedFilter) {
                FilterType.ALL -> true
                FilterType.ACTIVE -> item.isActive
                FilterType.ANNOUNCEMENT -> item.category == "Smuteční oznámení"
                FilterType.MEMORY -> item.category == "Vzpomínka"
                FilterType.ARCHIVE -> item.isArchived
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                (item.name.lowercase().contains(q)) ||
                (item.residence?.lowercase()?.contains(q) == true) ||
                (item.deathDate?.lowercase()?.contains(q) == true) ||
                (item.originalTitle?.lowercase()?.contains(q) == true)
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
                            Text("PS Horníček", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Hledat jméno, obec...") },
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

            // Filtrovací tlačítka
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

            // Obsah
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
                            ObituaryCard(obituary = item, onClick = { onSelectObituary(item) })
                        }
                    }
                }
            }
        }
    }

    // Dialog nastavení URL databáze
    if (showSettingsDialog) {
        var tempUrl by remember { mutableStateOf(currentDbUrl) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Zdroj databáze") },
            text = {
                Column {
                    Text(
                        "Zadejte URL adresu souboru database.json na GitHubu:",
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
fun ObituaryCard(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Náhled obrázku parte
            AsyncImage(
                model = obituary.imageUrl,
                contentDescription = obituary.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = obituary.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isMemory = obituary.category == "Vzpomínka"
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isMemory) Color(0xFF78350F) else Color(0xFF374151)
                    ) {
                        Text(
                            text = obituary.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMemory) Color(0xFFFDE68A) else Color(0xFFE5E7EB),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (obituary.isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF064E3B)
                        ) {
                            Text(
                                text = "Vývěska",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFA7F3D0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!obituary.residence.isNullOrBlank() || !obituary.deathDate.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(obituary.residence, obituary.deathDate).joinToString(" • "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
