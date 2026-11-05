package cz.pshornicek.vyveska.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
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
import cz.pshornicek.vyveska.data.ObituaryRepository
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.theme.AccentGold
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

enum class MainTab(val label: String) {
    ACTIVE("📋 Aktuální"),
    ARCHIVE("🗄️ Archiv"),
    STATS("📊 Statistika")
}

private val TOWN_COORDS_MAP: Map<String, Pair<Double, Double>> = mapOf(
    "Loštice" to (49.7458 to 16.9278),
    "Mohelnice" to (49.7769 to 16.9194),
    "Moravičany" to (49.7569 to 16.9636),
    "Pavlov" to (49.7408 to 16.8803),
    "Úsov" to (49.7986 to 17.0108),
    "Dubicko" to (49.8267 to 16.9647),
    "Bohuslavice" to (49.8208 to 16.9400),
    "Lukavice" to (49.8236 to 16.9158),
    "Třeština" to (49.7967 to 16.9639),
    "Krchleby" to (49.8117 to 16.8322),
    "Podolí" to (49.7664 to 16.8844),
    "Pískov" to (49.8058 to 17.0794),
    "Veleboř" to (49.8250 to 17.0406),
    "Rohle" to (49.8639 to 17.0233),
    "Studená Loučka" to (49.7611 to 16.8269),
    "Mírov" to (49.7928 to 16.8508),
    "Palonín" to (49.7367 to 16.9739),
    "Zvole" to (49.8414 to 16.9169),
    "Stavenice" to (49.7775 to 16.9817),
    "Červená Lhota" to (49.7136 to 17.0156),
    "Bílá Lhota" to (49.7103 to 16.9761),
    "Slavoňov" to (49.8017 to 16.9061),
    "Řimice" to (49.7186 to 17.0144),
    "Klopina" to (49.8225 to 17.0308),
    "Police" to (49.8147 to 17.0014),
    "Medlov" to (49.7844 to 17.0644),
    "Mladeč" to (49.7061 to 17.0169),
    "Zábřeh" to (49.8825 to 16.8722),
    "Litovel" to (49.7011 to 17.0761),
    "Uničov" to (49.7708 to 17.1214),
    "Šumperk" to (49.9653 to 16.9706),
    "Olomouc" to (49.5938 to 17.2509),
    "Náklo" to (49.6542 to 17.1292),
    "Chromeč" to (49.9469 to 16.8719),
    "Rájec" to (49.8453 to 16.8856),
    "Postřelmov" to (49.9078 to 16.9061),
    "Bludov" to (49.9428 to 16.9297),
    "Ruda nad Moravou" to (49.9839 to 16.8764),
    "Sudkov" to (49.9114 to 16.9425),
    "Lesnice" to (49.8736 to 16.9453),
    "Leština" to (49.8742 to 16.9233),
    "Brníčko" to (49.8950 to 16.9736),
    "Kolšov" to (49.9039 to 16.9250),
    "Nemile" to (49.8808 to 16.8358),
    "Červenka" to (49.7219 to 17.0811),
    "Senice na Hané" to (49.6228 to 17.0878),
    "Cholina" to (49.6611 to 17.0542),
    "Bílsko" to (49.6472 to 17.0369),
    "Haňovice" to (49.6797 to 17.0439),
    "Pňovice" to (49.7192 to 17.1636),
    "Střeň" to (49.6964 to 17.1528),
    "Příkazy" to (49.6361 to 17.1450),
    "Horka nad Moravou" to (49.6400 to 17.2094),
    "Štěpánov" to (49.6850 to 17.2208),
    "Bouzov" to (49.7042 to 16.8894),
    "Hvozd" to (49.6317 to 16.9178),
    "Luká" to (49.6478 to 16.9467)
)

private fun normStr(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return n.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun getItemCoords(item: Obituary): Pair<Double, Double>? {
    if (item.lat != null && item.lon != null) return item.lat to item.lon
    if (!item.residence.isNullOrBlank()) {
        TOWN_COORDS_MAP[item.residence]?.let { return it }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    obituaries: List<Obituary>,
    isLoading: Boolean,
    errorMessage: String?,
    currentDbUrl: String,
    notifyEnabled: Boolean,
    notifySources: Set<String>,
    notifyIncludeMemories: Boolean,
    onRefresh: () -> Unit,
    onUpdateDbUrl: (String) -> Unit,
    onSaveNotifySettings: (Boolean, Set<String>, Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onSelectObituary: (Obituary) -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.ACTIVE) }
    var filterExpanded by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var filterGender by remember { mutableStateOf("all") } // "all", "F", "M"
    var filterTown by remember { mutableStateOf("") }
    var filterRadiusKm by remember { mutableIntStateOf(20) }
    var filterSource by remember { mutableStateOf("all") }
    var hideMemories by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    val allKnownTowns = remember(obituaries) {
        val s = TOWN_COORDS_MAP.keys.toMutableSet()
        obituaries.forEach { it.residence?.takeIf { r -> r.isNotBlank() }?.let { r -> s.add(r) } }
        s.sorted()
    }

    val centerCoords = remember(filterTown, obituaries) {
        val q = normStr(filterTown)
        if (q.isEmpty()) null
        else {
            val matchedTown = allKnownTowns.firstOrNull { normStr(it) == q }
                ?: allKnownTowns.firstOrNull { normStr(it).startsWith(q) }
            if (matchedTown != null && TOWN_COORDS_MAP.containsKey(matchedTown)) {
                TOWN_COORDS_MAP[matchedTown]
            } else {
                val fromItem = obituaries.firstOrNull { normStr(it.residence) == q && it.lat != null && it.lon != null }
                if (fromItem?.lat != null && fromItem.lon != null) fromItem.lat to fromItem.lon else null
            }
        }
    }

    val filteredAllPeriods = remember(obituaries, searchQuery, filterGender, filterTown, filterRadiusKm, filterSource, hideMemories, centerCoords) {
        obituaries.filter { item ->
            if (hideMemories && item.category == "Vzpomínka") return@filter false
            if (filterGender != "all") {
                val g = item.gender ?: ""
                if (g != filterGender && g != "MF") return@filter false
            }
            if (filterSource != "all") {
                if (item.getEffectiveSource() != filterSource) return@filter false
            }
            if (filterTown.isNotBlank()) {
                if (filterRadiusKm == 0 || centerCoords == null) {
                    if (!normStr(item.residence).contains(normStr(filterTown))) return@filter false
                } else {
                    val ic = getItemCoords(item) ?: return@filter false
                    val dist = haversineKm(centerCoords.first, centerCoords.second, ic.first, ic.second)
                    if (dist > filterRadiusKm) return@filter false
                }
            }
            if (searchQuery.isNotBlank()) {
                val q = normStr(searchQuery)
                val match = normStr(item.name).contains(q) ||
                        normStr(item.residence).contains(q) ||
                        normStr(item.age).contains(q) ||
                        normStr(item.deathDate).contains(q) ||
                        normStr(item.deathCause).contains(q) ||
                        normStr(item.ceremony).contains(q)
                if (!match) return@filter false
            }
            true
        }
    }

    val activeList = remember(filteredAllPeriods) { filteredAllPeriods.filter { it.isActive } }
    val archiveList = remember(filteredAllPeriods) { filteredAllPeriods.filter { it.isArchived } }
    val displayedList = if (selectedTab == MainTab.ACTIVE) activeList else archiveList

    val anyFilterActive = searchQuery.isNotBlank() || filterGender != "all" || filterTown.isNotBlank() || filterSource != "all" || hideMemories

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✝", color = AccentGold, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text("Smuteční vývěska", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "Aktuální po dobu 1 týdne od úmrtí",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Nastavení upozornění",
                            tint = if (notifyEnabled) AccentGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
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
            // 1. Záložky: Aktuální | Statistika | Archiv
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val countSuffix = when (tab) {
                        MainTab.ACTIVE -> " (${activeList.size})"
                        MainTab.STATS -> ""
                        MainTab.ARCHIVE -> " (${archiveList.size})"
                    }
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = "${tab.label}$countSuffix",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGold,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // 2. Sbalitelná lišta filtru (nezabírá místo na displeji telefonu)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clickable { filterExpanded = !filterExpanded },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (anyFilterActive) "🔍 Vyhledávání a filtry (Aktivní)" else "🔍 Vyhledávání a filtry",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (anyFilterActive) AccentGold else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (filterExpanded) "▲ Skrýt" else "▼ Rozbalit",
                        fontSize = 12.sp,
                        color = AccentGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (filterExpanded) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Hledat jméno, příjmení, datum...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Filtr Pohlaví
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pohlaví:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            listOf("all" to "Vše", "F" to "♀ Ženy", "M" to "♂ Muži").forEach { (valKey, lbl) ->
                                FilterChip(
                                    selected = filterGender == valKey,
                                    onClick = { filterGender = valKey },
                                    label = { Text(lbl, fontSize = 12.sp) }
                                )
                            }
                        }

                        // Filtr Město (psací pole + našeptávač) + Okolí v km
                        OutlinedTextField(
                            value = filterTown,
                            onValueChange = { filterTown = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Město / obec (např. Loštice)", fontSize = 12.sp) },
                            trailingIcon = {
                                if (filterTown.isNotEmpty()) {
                                    IconButton(onClick = { filterTown = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Vymazat město")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (filterTown.isNotBlank()) {
                            val matchingTowns = allKnownTowns.filter {
                                normStr(it).contains(normStr(filterTown)) && normStr(it) != normStr(filterTown)
                            }.take(6)
                            if (matchingTowns.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(matchingTowns) { townSuggestion ->
                                        SuggestionChip(
                                            onClick = { filterTown = townSuggestion },
                                            label = { Text("📍 $townSuggestion", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Okolí:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(0, 5, 10, 20, 30, 50)) { km ->
                                        FilterChip(
                                            selected = filterRadiusKm == km,
                                            onClick = { filterRadiusKm = km },
                                            label = { Text(if (km == 0) "Pouze obec" else "+$km km", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Filtr Zdroj parte
                        Text("Zdroj parte:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val sources = listOf("all" to "Všechny zdroje") + ObituaryRepository.ALL_SOURCES.map { it to it }
                            items(sources) { (srcKey, srcLabel) ->
                                FilterChip(
                                    selected = filterSource == srcKey,
                                    onClick = { filterSource = srcKey },
                                    label = { Text(srcLabel, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Skrýt vzpomínky + Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { hideMemories = !hideMemories }
                            ) {
                                Checkbox(checked = hideMemories, onCheckedChange = { hideMemories = it })
                                Text("Skrýt vzpomínky", fontSize = 13.sp)
                            }
                            if (anyFilterActive) {
                                TextButton(onClick = {
                                    searchQuery = ""
                                    filterGender = "all"
                                    filterTown = ""
                                    filterRadiusKm = 20
                                    filterSource = "all"
                                    hideMemories = false
                                }) {
                                    Text("✕ Zrušit filtry", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Obsah podle zvolené záložky
            if (selectedTab == MainTab.STATS) {
                StatsScreen(obituaries = filteredAllPeriods)
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading && obituaries.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = AccentGold
                        )
                    } else if (displayedList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (errorMessage != null) "Chyba při stahování dat" else "Žádné záznamy",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
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
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedList, key = { it.id }) { item ->
                                val distKm = if (filterTown.isNotBlank() && centerCoords != null) {
                                    getItemCoords(item)?.let { ic ->
                                        haversineKm(centerCoords.first, centerCoords.second, ic.first, ic.second).roundToInt()
                                    }
                                } else null

                                ObituaryTableCard(
                                    obituary = item,
                                    distanceKm = distKm,
                                    onClick = { onSelectObituary(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Nastavení (Notifikace: výběr pohřební služby, smuteční oznámení vs. vzpomínky + URL)
    if (showSettingsDialog) {
        var tempNotifyEnabled by remember { mutableStateOf(notifyEnabled) }
        var tempSources by remember { mutableStateOf(notifySources) }
        var tempIncludeMemories by remember { mutableStateOf(notifyIncludeMemories) }
        var tempUrl by remember { mutableStateOf(currentDbUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("🔔 Nastavení a upozornění", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempNotifyEnabled = !tempNotifyEnabled }
                    ) {
                        Checkbox(checked = tempNotifyEnabled, onCheckedChange = { tempNotifyEnabled = it })
                        Column {
                            Text("Zapnout upozornění na nová parte", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Upozorní vás při objevení nového parte", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text("Pohřební služby pro notifikace:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AccentGold)
                    ObituaryRepository.ALL_SOURCES.forEach { srcName ->
                        val checked = tempSources.contains(srcName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSources = if (checked) tempSources - srcName else tempSources + srcName
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    tempSources = if (isChecked) tempSources + srcName else tempSources - srcName
                                }
                            )
                            Text(srcName, fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text("Typ oznámení pro notifikace:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AccentGold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempIncludeMemories = false }
                    ) {
                        RadioButton(selected = !tempIncludeMemories, onClick = { tempIncludeMemories = false })
                        Text("Pouze smuteční oznámení (bez vzpomínek)", fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempIncludeMemories = true }
                    ) {
                        RadioButton(selected = tempIncludeMemories, onClick = { tempIncludeMemories = true })
                        Text("Smuteční oznámení i výroční vzpomínky", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onTestNotification,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔 Vyzkoušet testovací upozornění", fontSize = 12.sp)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    Text("Zdroj databáze (URL):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveNotifySettings(tempNotifyEnabled, tempSources, tempIncludeMemories)
                        if (tempUrl != currentDbUrl) {
                            onUpdateDbUrl(tempUrl)
                        }
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("Uložit")
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
    distanceKm: Int? = null,
    onClick: () -> Unit
) {
    val faceUrl = obituary.getBestFaceUrl()
    val genderColor = when (obituary.gender) {
        "F" -> Color(0xFFF472B6)
        "M" -> Color(0xFF60A5FA)
        else -> MaterialTheme.colorScheme.onSurface
    }

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
            // Hlavička: Fotka obličeje (pokud je na parte) + Jméno (věk) + Město + Zdroj
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (faceUrl != null) {
                    AsyncImage(
                        model = faceUrl,
                        contentDescription = "Portrét ${obituary.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1219))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val sym = obituary.getGenderSymbol()
                        if (sym.isNotEmpty()) {
                            Text(
                                text = "$sym ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = genderColor
                            )
                        }
                        Text(
                            text = obituary.name + (if (!obituary.age.isNullOrBlank()) " (${obituary.age})" else ""),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!obituary.residence.isNullOrBlank()) {
                            val distStr = if (distanceKm != null) " ($distanceKm km)" else ""
                            Text(
                                text = "📍 ${obituary.residence}$distStr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentGold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = obituary.getEffectiveSource(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Údaje pod sebou: Datum úmrtí, Důvod / okolnost, Poslední rozloučení
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!obituary.deathDate.isNullOrBlank()) {
                        Column {
                            Text("DATUM ÚMRTÍ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(obituary.deathDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("KDY A KDE MÁ POHŘEB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(obituary.ceremony, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("📄 Detail (Zobrazit parte)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
