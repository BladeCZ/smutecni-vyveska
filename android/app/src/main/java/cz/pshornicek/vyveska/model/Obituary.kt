package cz.pshornicek.vyveska.model

import com.google.gson.annotations.SerializedName

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
    val ceremony: String? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("first_seen") val firstSeen: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
) {
    fun getBestImageUrl(): String {
        return if (!releaseImageUrl.isNullOrBlank()) releaseImageUrl else imageUrl
    }

    fun getBestFaceUrl(): String? {
        return if (!releaseFaceUrl.isNullOrBlank()) releaseFaceUrl else null
    }

    fun getDisplayNameWithAge(): String {
        val sym = when (gender) {
            "F" -> "♀ "
            "M" -> "♂ "
            "MF" -> "♀♂ "
            else -> ""
        }
        val agePart = if (!age.isNullOrBlank()) " ($age)" else ""
        return "$sym$name$agePart"
    }
}
