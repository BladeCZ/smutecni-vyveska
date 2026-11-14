package cz.pshornicek.vyveska.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.theme.AccentGold

private enum class EditableField(val label: String, val multiline: Boolean = false) {
    NAME("Jméno a příjmení"),
    GENDER("Pohlaví (M = Muž, F = Žena, MF = Manželé)"),
    CATEGORY("Kategorie (Smuteční oznámení / Vzpomínka)"),
    DEATH_DATE("Datum úmrtí (např. 12. 11. 2026)"),
    DEATH_CAUSE("Důvod / okolnost úmrtí", multiline = true),
    AGE("Věk (např. 78 let)"),
    RESIDENCE("Bydliště (město / obec)"),
    CEREMONY("Poslední rozloučení (kdy a kde)", multiline = true)
}

private val DETAIL_TOWN_COORDS: Map<String, Pair<Double, Double>> = mapOf(
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
    "Olomouc" to (49.5938 to 17.2509)
)

private fun parseCzechDateToIso(rawDate: String): Pair<String, String> {
    val cleaned = rawDate.trim()
    val match = Regex("(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})").find(cleaned)
    if (match != null) {
        val d = match.groupValues[1].toIntOrNull()
        val m = match.groupValues[2].toIntOrNull()
        val y = match.groupValues[3].toIntOrNull()
        if (d != null && m != null && y != null && d in 1..31 && m in 1..12) {
            val disp = String.format("%02d. %02d. %04d", d, m, y)
            val iso = String.format("%04d-%02d-%02d", y, m, d)
            return disp to iso
        }
    }
    return cleaned to ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    obituary: Obituary,
    onBack: () -> Unit,
    onUpdateObituary: (Obituary, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var isEditMode by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<EditableField?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }

    // Obsluha systémového tlačítka Zpět
    BackHandler {
        when {
            editingField != null -> editingField = null
            isEditMode -> isEditMode = false
            scale > 1.05f -> {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(obituary.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode) isEditMode = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isEditMode = !isEditMode
                        if (!isEditMode) saveFeedback = null
                    }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Ukončit úpravy" else "Upravit",
                            tint = if (isEditMode) AccentGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { shareObituary(context, obituary) }) {
                        Icon(Icons.Default.Share, contentDescription = "Sdílet")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Obrázek parte s možností zoomu a posunu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = obituary.getBestImageUrl(),
                    contentDescription = obituary.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }

            // Textové detaily parte (v režimu úprav lze klepnutím na jakýkoli údaj text opravit)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEditMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = AccentGold.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "✏️ Režim úprav: Klepněte na text níže, který chcete upravit. Změna se uloží do databáze.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentGold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!saveFeedback.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF065F46)
                        ) {
                            Text(
                                text = saveFeedback!!,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isEditMode) Modifier.clickable { editingField = EditableField.NAME } else Modifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = obituary.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isEditMode) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Upravit jméno",
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        SuggestionChip(
                            onClick = {
                                if (isEditMode) editingField = EditableField.CATEGORY
                            },
                            label = { Text(obituary.category) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = AccentGold
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    if (isEditMode) {
                        val genderLabel = when (obituary.gender) {
                            "F" -> "♀ Žena"
                            "M" -> "♂ Muž"
                            "MF" -> "♀♂ Manželé"
                            else -> "Neurčeno"
                        }
                        EditableDetailItem(
                            label = "Pohlaví",
                            value = genderLabel,
                            isEditMode = true,
                            onEditClick = { editingField = EditableField.GENDER }
                        )
                    }

                    if (!obituary.deathDate.isNullOrBlank() || isEditMode) {
                        EditableDetailItem(
                            label = "Datum úmrtí",
                            value = obituary.deathDate?.takeIf { it.isNotBlank() } ?: "Neuvedeno",
                            isEditMode = isEditMode,
                            isEmpty = obituary.deathDate.isNullOrBlank(),
                            onEditClick = { editingField = EditableField.DEATH_DATE }
                        )
                    }

                    if (!obituary.deathCause.isNullOrBlank() || isEditMode) {
                        EditableDetailItem(
                            label = "Důvod / okolnost úmrtí",
                            value = obituary.deathCause?.takeIf { it.isNotBlank() } ?: "Neuvedeno",
                            isEditMode = isEditMode,
                            isEmpty = obituary.deathCause.isNullOrBlank(),
                            onEditClick = { editingField = EditableField.DEATH_CAUSE }
                        )
                    }

                    if (!obituary.age.isNullOrBlank() || isEditMode) {
                        EditableDetailItem(
                            label = "Věk",
                            value = obituary.age?.takeIf { it.isNotBlank() } ?: "Neuvedeno",
                            isEditMode = isEditMode,
                            isEmpty = obituary.age.isNullOrBlank(),
                            onEditClick = { editingField = EditableField.AGE }
                        )
                    }

                    if (!obituary.residence.isNullOrBlank() || isEditMode) {
                        EditableDetailItem(
                            label = "Bydliště",
                            value = obituary.residence?.takeIf { it.isNotBlank() } ?: "Neuvedeno",
                            isEditMode = isEditMode,
                            isEmpty = obituary.residence.isNullOrBlank(),
                            onEditClick = { editingField = EditableField.RESIDENCE }
                        )
                    }

                    if (!obituary.ceremony.isNullOrBlank() || isEditMode) {
                        EditableDetailItem(
                            label = "Poslední rozloučení",
                            value = obituary.ceremony?.takeIf { it.isNotBlank() } ?: "Neuvedeno",
                            isEditMode = isEditMode,
                            isEmpty = obituary.ceremony.isNullOrBlank(),
                            onEditClick = { editingField = EditableField.CEREMONY }
                        )
                    }

                    EditableDetailItem(
                        label = "Stav",
                        value = if (obituary.isActive) "Aktuálně na smuteční vývěsce" else "V archivu parte",
                        isEditMode = false,
                        onEditClick = {}
                    )

                    Button(
                        onClick = { shareObituary(context, obituary) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sdílet parte")
                    }
                }
            }
        }
    }

    // Dialog pro úpravu zvoleného údaje parte
    editingField?.let { field ->
        val initialValue = when (field) {
            EditableField.NAME -> obituary.name
            EditableField.GENDER -> obituary.gender ?: ""
            EditableField.CATEGORY -> obituary.category
            EditableField.DEATH_DATE -> obituary.deathDate ?: ""
            EditableField.DEATH_CAUSE -> obituary.deathCause ?: ""
            EditableField.AGE -> obituary.age ?: ""
            EditableField.RESIDENCE -> obituary.residence ?: ""
            EditableField.CEREMONY -> obituary.ceremony ?: ""
        }
        var draft by remember(field) { mutableStateOf(initialValue) }

        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text("✏️ Upravit: ${field.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (field) {
                        EditableField.GENDER -> {
                            listOf("F" to "♀ Žena", "M" to "♂ Muž", "MF" to "♀♂ Manželé", "" to "Neurčeno").forEach { (code, lbl) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { draft = code }
                                ) {
                                    RadioButton(selected = draft == code, onClick = { draft = code })
                                    Text(lbl, fontSize = 14.sp)
                                }
                            }
                        }
                        EditableField.CATEGORY -> {
                            listOf("Smuteční oznámení", "Vzpomínka").forEach { cat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { draft = cat }
                                ) {
                                    RadioButton(selected = draft == cat, onClick = { draft = cat })
                                    Text(cat, fontSize = 14.sp)
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = !field.multiline,
                                minLines = if (field.multiline) 3 else 1,
                                maxLines = if (field.multiline) 5 else 1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    onClick = {
                        val cleanedDraft = draft.trim()
                        val updated = when (field) {
                            EditableField.NAME -> obituary.copy(
                                name = cleanedDraft.ifBlank { obituary.name },
                                manualEdit = true
                            )
                            EditableField.GENDER -> obituary.copy(
                                gender = cleanedDraft.ifBlank { null },
                                manualEdit = true
                            )
                            EditableField.CATEGORY -> obituary.copy(
                                category = cleanedDraft.ifBlank { "Smuteční oznámení" },
                                manualEdit = true
                            )
                            EditableField.DEATH_DATE -> {
                                val (disp, iso) = parseCzechDateToIso(cleanedDraft)
                                obituary.copy(
                                    deathDate = disp.ifBlank { null },
                                    deathDateIso = iso.ifBlank { obituary.deathDateIso },
                                    manualEdit = true
                                )
                            }
                            EditableField.DEATH_CAUSE -> obituary.copy(
                                deathCause = cleanedDraft.ifBlank { null },
                                manualEdit = true
                            )
                            EditableField.AGE -> {
                                val normalizedAge = if (cleanedDraft.isNotBlank() && cleanedDraft.all { it.isDigit() }) {
                                    "$cleanedDraft let"
                                } else {
                                    cleanedDraft.ifBlank { null }
                                }
                                obituary.copy(
                                    age = normalizedAge,
                                    manualEdit = true
                                )
                            }
                            EditableField.RESIDENCE -> {
                                val coords = DETAIL_TOWN_COORDS[cleanedDraft]
                                obituary.copy(
                                    residence = cleanedDraft.ifBlank { null },
                                    lat = coords?.first ?: obituary.lat,
                                    lon = coords?.second ?: obituary.lon,
                                    manualEdit = true
                                )
                            }
                            EditableField.CEREMONY -> obituary.copy(
                                ceremony = cleanedDraft.ifBlank { null },
                                manualEdit = true
                            )
                        }
                        editingField = null
                        isSaving = true
                        onUpdateObituary(updated) { syncedToCloud ->
                            isSaving = false
                            saveFeedback = if (syncedToCloud) {
                                "✓ Změna byla uložena do databáze."
                            } else {
                                "✓ Změna byla uložena v zařízení (synchronizace do databáze proběhne po připojení)."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text(if (isSaving) "Ukládám..." else "Uložit")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("Zrušit")
                }
            }
        )
    }
}

@Composable
fun EditableDetailItem(
    label: String,
    value: String,
    isEditMode: Boolean,
    isEmpty: Boolean = false,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEditMode) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onEditClick)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (isEditMode) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Upravit $label",
                    tint = AccentGold,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = value,
            fontSize = 15.sp,
            color = if (isEmpty) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
        )
    }
}

fun shareObituary(context: Context, obituary: Obituary) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Smuteční parte - ${obituary.name}")
        putExtra(
            Intent.EXTRA_TEXT,
            "Smuteční oznámení: ${obituary.name}\n${obituary.getBestImageUrl()}"
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Sdílet parte přes"))
}
