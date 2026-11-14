package cz.pshornicek.vyveska.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Obituary(
    val id: String,
    val name: String,
    val gender: String? = null,
    val category: String,
    @SerializedName("source_name") val sourceName: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("is_archived") val isArchived: Boolean = false,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("local_image_path") val localImagePath: String? = null,
    @SerializedName("release_image_url") val releaseImageUrl: String? = null,
    @SerializedName("face_image_path") val faceImagePath: String? = null,
    @SerializedName("release_face_url") val releaseFaceUrl: String? = null,
    val age: String? = null,
    @SerializedName("death_date") val deathDate: String? = null,
    @SerializedName("death_date_iso") val deathDateIso: String? = null,
    @SerializedName("death_cause") val deathCause: String? = null,
    val residence: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val ceremony: String? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("first_seen") val firstSeen: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("manual_edit") val manualEdit: Boolean? = null
) {
    fun getBestImageUrl(): String {
        if (!releaseImageUrl.isNullOrBlank()) return releaseImageUrl
        if (!localImagePath.isNullOrBlank()) {
            return "https://bladecz.github.io/smutecni-vyveska/${localImagePath.removePrefix("/")}"
        }
        return imageUrl
    }

    fun getBestFaceUrl(): String? {
        if (!releaseFaceUrl.isNullOrBlank()) return releaseFaceUrl
        if (!faceImagePath.isNullOrBlank()) {
            return "https://bladecz.github.io/smutecni-vyveska/${faceImagePath.removePrefix("/")}"
        }
        return null
    }

    fun getEffectiveSource(): String {
        return if (!sourceName.isNullOrBlank()) sourceName else "Horníček Moravičany"
    }

    fun getGenderSymbol(): String {
        return when (gender) {
            "F" -> "♀"
            "M" -> "♂"
            "MF" -> "♀♂"
            else -> ""
        }
    }

    fun getDisplayNameWithAge(): String {
        val sym = getGenderSymbol()
        val prefix = if (sym.isNotEmpty()) "$sym " else ""
        val agePart = if (!age.isNullOrBlank()) " ($age)" else ""
        return "$prefix$name$agePart"
    }

    fun getAgeNumber(): Int? {
        if (age.isNullOrBlank()) return null
        val m = Regex("(\\d{1,3})").find(age) ?: return null
        val v = m.groupValues[1].toIntOrNull() ?: return null
        return if (v in 0..115) v else null
    }

    fun isActiveWithinWeek(referenceDate: LocalDate): Boolean {
        val iso = when {
            !deathDateIso.isNullOrBlank() && deathDateIso.length >= 10 -> deathDateIso.take(10)
            !firstSeen.isNullOrBlank() && firstSeen.length >= 10 -> firstSeen.take(10)
            else -> null
        }
        if (iso != null) {
            try {
                val d = LocalDate.parse(iso)
                val diff = ChronoUnit.DAYS.between(d, referenceDate)
                return diff <= 7
            } catch (_: Exception) {
            }
        }
        return isActive
    }
}
