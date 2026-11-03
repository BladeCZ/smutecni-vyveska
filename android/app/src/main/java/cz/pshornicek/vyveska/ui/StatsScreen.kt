package cz.pshornicek.vyveska.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pshornicek.vyveska.model.Obituary
import cz.pshornicek.vyveska.ui.theme.AccentGold
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val PinkFemale = Color(0xFFF472B6)
private val BlueMale = Color(0xFF60A5FA)
private val GoldLine = Color(0xFFFBBF24)

private fun normalizeCz(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return n.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
}

private fun classifyCause(item: Obituary): String {
    val c = normalizeCz((item.deathCause ?: "") + " " + (item.rawText ?: ""))
    return when {
        c.contains("tragick") -> "Tragicky"
        c.contains("kratk") && c.contains("nemoc") -> "Po krátké nemoci"
        (c.contains("dlouh") || c.contains("delsi") || c.contains("tezke")) && c.contains("nemoc") -> "Po dlouhé nemoci"
        c.contains("nahle") || c.contains("necekane") || c.contains("neocekavane") -> "Náhle a neočekávaně"
        c.contains("tise") || c.contains("spanek") || c.contains("spanku") -> "Tiše / ve spánku"
        else -> "Neuvedeno"
    }
}

private data class WeekBucket(
    val key: String,
    val label: String,
    var fCount: Int = 0,
    var mCount: Int = 0,
    val fAges: MutableList<Int> = mutableListOf(),
    val mAges: MutableList<Int> = mutableListOf(),
    val allAges: MutableList<Int> = mutableListOf()
)

private data class TownStat(
    val town: String,
    var total: Int = 0,
    var fCount: Int = 0,
    var mCount: Int = 0,
    val allAges: MutableList<Int> = mutableListOf()
)

@Composable
fun StatsScreen(obituaries: List<Obituary>) {
    val items = remember(obituaries) {
        obituaries.filter { it.category != "Vzpomínka" }
    }

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Žádná data pro výpočet statistiky.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    val allAges = mutableListOf<Int>()
    val fAges = mutableListOf<Int>()
    val mAges = mutableListOf<Int>()
    var fCount = 0
    var mCount = 0
    var youngest: Pair<Obituary, Int>? = null
    var oldest: Pair<Obituary, Int>? = null

    items.forEach { it ->
        when (it.gender) {
            "F" -> fCount++
            "M" -> mCount++
            "MF" -> { fCount++; mCount++ }
        }
        val age = it.getAgeNumber()
        if (age != null) {
            allAges.add(age)
            if (it.gender == "F") fAges.add(age)
            if (it.gender == "M") mAges.add(age)
            if (youngest == null || age < youngest!!.second) youngest = it to age
            if (oldest == null || age > oldest!!.second) oldest = it to age
        }
    }

    val avgAll = if (allAges.isNotEmpty()) allAges.average() else null
    val avgF = if (fAges.isNotEmpty()) fAges.average() else null
    val avgM = if (mAges.isNotEmpty()) mAges.average() else null
    val sortedAges = allAges.sorted()
    val medianAll = if (sortedAges.isNotEmpty()) {
        val mid = sortedAges.size / 2
        if (sortedAges.size % 2 == 1) sortedAges[mid].toDouble()
        else (sortedAges[mid - 1] + sortedAges[mid]) / 2.0
    } else null

    // Seskupení po týdnech v čase
    val datedItems = items.filter { !it.deathDateIso.isNullOrBlank() && it.deathDateIso.length >= 10 }
        .sortedBy { it.deathDateIso }

    val weekMap = linkedMapOf<String, WeekBucket>()
    val dowCountsF = IntArray(7)
    val dowCountsM = IntArray(7)

    datedItems.forEach { it ->
        try {
            val d = LocalDate.parse(it.deathDateIso!!.take(10))
            val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            val key = monday.toString()
            val label = "${monday.dayOfMonth}.${monday.monthValue}.–${sunday.dayOfMonth}.${sunday.monthValue}."
            val b = weekMap.getOrPut(key) { WeekBucket(key, label) }
            if (it.gender == "F") b.fCount++ else b.mCount++
            val age = it.getAgeNumber()
            if (age != null) {
                b.allAges.add(age)
                if (it.gender == "F") b.fAges.add(age) else b.mAges.add(age)
            }
            val dowIdx = d.dayOfWeek.value - 1
            if (it.gender == "F") dowCountsF[dowIdx]++ else dowCountsM[dowIdx]++
        } catch (_: Exception) {
        }
    }
    val weeks = weekMap.values.toList()

    // Věkové dekády
    val ageLabels = listOf("do 49", "50–59", "60–69", "70–79", "80–89", "90+ let")
    val fBuckets = IntArray(6)
    val mBuckets = IntArray(6)
    items.forEach { it ->
        val age = it.getAgeNumber() ?: return@forEach
        val idx = when {
            age < 50 -> 0
            age < 60 -> 1
            age < 70 -> 2
            age < 80 -> 3
            age < 90 -> 4
            else -> 5
        }
        if (it.gender == "F") fBuckets[idx]++ else mBuckets[idx]++
    }

    // Města a obce
    val townMap = mutableMapOf<String, TownStat>()
    items.forEach { it ->
        val t = it.residence?.takeIf { r -> r.isNotBlank() } ?: return@forEach
        val st = townMap.getOrPut(t) { TownStat(t) }
        st.total++
        if (it.gender == "F") st.fCount++ else st.mCount++
        it.getAgeNumber()?.let { a -> st.allAges.add(a) }
    }
    val townStats = townMap.values.sortedByDescending { it.total }

    // Okolnosti úmrtí
    val causeMap = mutableMapOf<String, Pair<Int, MutableList<Int>>>()
    items.forEach { it ->
        val c = classifyCause(it)
        val cur = causeMap[c] ?: (0 to mutableListOf())
        val ages = cur.second
        it.getAgeNumber()?.let { a -> ages.add(a) }
        causeMap[c] = (cur.first + 1) to ages
    }
    val causeList = causeMap.entries.sortedByDescending { it.value.first }

    val totalGender = maxOf(1, fCount + mCount)
    val fPct = ((fCount * 100.0) / totalGender).roundToInt()
    val mPct = 100 - fPct

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. KPI Přehledové karty
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📊 Souhrnná statistika úmrtí", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AccentGold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PRŮMĚRNÝ VĚK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = if (avgAll != null) String.format(Locale.US, "%.1f let", avgAll) else "–",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (medianAll != null) {
                                Text("Medián: ${medianAll.roundToInt()} let", fontSize = 12.sp, color = AccentGold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ŽENY vs. MUŽI", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("♀ $fCount ($fPct %) : ♂ $mCount ($mPct %)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "♀ ${if (avgF != null) String.format(Locale.US, "%.1f", avgF) else "–"} let • ♂ ${if (avgM != null) String.format(Locale.US, "%.1f", avgM) else "–"} let",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                    if (avgF != null && avgM != null) {
                        val diff = avgF - avgM
                        Text(
                            text = "Ženy se v regionu dožívají v průměru o ${String.format(Locale.US, "%.1f", abs(diff))} let ${if (diff >= 0) "více" else "méně"}.",
                            fontSize = 12.sp,
                            color = PinkFemale
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(
                        text = "Nejstarší: ${oldest?.first?.name ?: "–"} (${oldest?.second ?: "–"} let, ${oldest?.first?.residence ?: "–"})\n" +
                               "Nejmladší: ${youngest?.first?.name ?: "–"} (${youngest?.second ?: "–"} let, ${youngest?.first?.residence ?: "–"})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 2. GRAF V ČASE: Počet úmrtí po týdnech (♀ Ženy vs. ♂ Muži)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📈 Vývoj počtu úmrtí v čase (po týdnech)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Skládaný graf: ♀ Ženy (růžová) + ♂ Muži (modrá)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    if (weeks.isNotEmpty()) {
                        val maxTotal = maxOf(1, weeks.maxOf { it.fCount + it.mCount })
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            val barSpace = size.width / weeks.size
                            val barWidth = (barSpace * 0.62f).coerceAtMost(48f)
                            weeks.forEachIndexed { idx, w ->
                                val x = idx * barSpace + (barSpace - barWidth) / 2f
                                val mH = (w.mCount.toFloat() / maxTotal) * (size.height - 20f)
                                val fH = (w.fCount.toFloat() / maxTotal) * (size.height - 20f)
                                val mTop = size.height - mH
                                val fTop = mTop - fH
                                drawRect(color = BlueMale, topLeft = Offset(x, mTop), size = Size(barWidth, mH))
                                drawRect(color = PinkFemale, topLeft = Offset(x, fTop), size = Size(barWidth, fH))
                            }
                        }
                        weeks.forEach { w ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(w.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                Text(
                                    "Celkem ${w.fCount + w.mCount} (♀ ${w.fCount} / ♂ ${w.mCount})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. GRAF V ČASE: Vývoj průměrného věku dožití v čase
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⏳ Vývoj průměrného věku úmrtí v čase", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Průměrný věk v jednotlivých týdnech (♀ Ženy vs. ♂ Muži vs. Celkem)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    if (weeks.size >= 2) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            val minAge = 50f
                            val maxAge = 98f
                            val stepX = size.width / (weeks.size - 1).coerceAtLeast(1)

                            fun drawAgeLine(series: List<Double?>, color: Color) {
                                val path = Path()
                                var started = false
                                series.forEachIndexed { idx, v ->
                                    if (v != null) {
                                        val x = idx * stepX
                                        val ratio = ((v.toFloat() - minAge) / (maxAge - minAge)).coerceIn(0f, 1f)
                                        val y = size.height - ratio * (size.height - 16f) - 8f
                                        if (!started) {
                                            path.moveTo(x, y)
                                            started = true
                                        } else {
                                            path.lineTo(x, y)
                                        }
                                        drawCircle(color = color, radius = 6f, center = Offset(x, y))
                                    }
                                }
                                if (started) {
                                    drawPath(path = path, color = color, style = Stroke(width = 4f))
                                }
                            }

                            drawAgeLine(weeks.map { if (it.allAges.isNotEmpty()) it.allAges.average() else null }, GoldLine)
                            drawAgeLine(weeks.map { if (it.fAges.isNotEmpty()) it.fAges.average() else null }, PinkFemale)
                            drawAgeLine(weeks.map { if (it.mAges.isNotEmpty()) it.mAges.average() else null }, BlueMale)
                        }
                    }
                    weeks.forEach { w ->
                        val aAll = if (w.allAges.isNotEmpty()) String.format(Locale.US, "%.1f let", w.allAges.average()) else "–"
                        val aF = if (w.fAges.isNotEmpty()) String.format(Locale.US, "%.1f", w.fAges.average()) else "–"
                        val aM = if (w.mAges.isNotEmpty()) String.format(Locale.US, "%.1f", w.mAges.average()) else "–"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(w.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            Text("Průměr: $aAll (♀ $aF / ♂ $aM)", fontSize = 11.sp, color = AccentGold)
                        }
                    }
                }
            }
        }

        // 4. Pohlaví vs. Věk úmrtí (Věkové skupiny)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("♀♂ Pohlaví vs. Věk úmrtí (Věkové skupiny)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val maxBucket = maxOf(1, (0..5).maxOf { fBuckets[it] + mBuckets[it] })
                    ageLabels.forEachIndexed { idx, label ->
                        val f = fBuckets[idx]
                        val m = mBuckets[idx]
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("♀ $f • ♂ $m (celkem ${f + m})", fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val fW = (f.toFloat() / maxBucket).coerceIn(0f, 1f)
                                val mW = (m.toFloat() / maxBucket).coerceIn(0f, 1f)
                                if (fW > 0f) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fW).background(PinkFemale))
                                }
                                if (mW > 0f) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(mW).background(BlueMale))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Statistika podle měst a obcí (Počet + průměrný věk v obci)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📍 Města a obce (Počet úmrtí a průměrný věk)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val maxTown = maxOf(1, townStats.firstOrNull()?.total ?: 1)
                    townStats.take(15).forEach { t ->
                        val avgTownAge = if (t.allAges.isNotEmpty()) String.format(Locale.US, "%.1f let", t.allAges.average()) else "–"
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("📍 ${t.town}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${t.total}× (♀${t.fCount}/♂${t.mCount}) • prům. $avgTownAge", fontSize = 12.sp, color = AccentGold)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((t.total.toFloat() / maxTown).coerceIn(0.04f, 1f))
                                        .background(AccentGold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Okolnosti úmrtí + Dny v týdnu
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💔 Okolnosti úmrtí a průměrný věk", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    causeList.forEach { (cause, pair) ->
                        val cnt = pair.first
                        val avgC = if (pair.second.isNotEmpty()) String.format(Locale.US, "%.1f let", pair.second.average()) else "–"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cause, fontSize = 13.sp)
                            Text("$cnt× (prům. věk $avgC)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentGold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text("📅 Úmrtí podle dne v týdnu", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val dayNames = listOf("Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle")
                    dayNames.forEachIndexed { idx, dName ->
                        val f = dowCountsF[idx]
                        val m = dowCountsM[idx]
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dName, fontSize = 12.sp)
                            Text("Celkem ${f + m}× (♀ $f / ♂ $m)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
