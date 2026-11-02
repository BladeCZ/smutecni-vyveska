package cz.pshornicek.vyveska.model

import com.google.gson.annotations.SerializedName

data class Obituary(
    val id: String,
    val name: String,
    val category: String,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("is_archived") val isArchived: Boolean = false,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("local_image_path") val localImagePath: String? = null,
    val age: String? = null,
    @SerializedName("death_date") val deathDate: String? = null,
    val residence: String? = null,
    val ceremony: String? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("first_seen") val firstSeen: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
)
