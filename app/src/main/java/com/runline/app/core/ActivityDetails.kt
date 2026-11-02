package com.runline.app.core

/** Editable annotations are separate from original GPS samples and recording checkpoints. */
data class ActivityDetails(
    val title: String = "",
    val description: String = "",
    val effort: Int? = null,
    val activityType: ActivityType? = null,
    val routine: RoutineType = RoutineType.NONE,
    val photoIds: List<String> = emptyList(),
) {
    fun validated(): ActivityDetails {
        require(title.length <= MAX_TITLE && description.length <= MAX_DESCRIPTION)
        require(effort == null || effort in 1..10)
        require(photoIds.size <= MAX_PHOTOS && photoIds.distinct().size == photoIds.size)
        require(photoIds.all { it.matches(PHOTO_ID) })
        require(title.none { it == '\n' || it == '\r' || it.code == 0 })
        require(description.none { it.code == 0 })
        return copy(title = title.trim(), description = description.trim())
    }
    companion object {
        const val MAX_TITLE = 120
        const val MAX_DESCRIPTION = 4000
        const val MAX_PHOTOS = 6
        val PHOTO_ID = Regex("[a-f0-9-]{36}\\.jpg")
    }
}

enum class RoutineType(val storageValue: String) {
    NONE("none"), EASY("easy"), LONG("long"), TEMPO("tempo"), INTERVALS("intervals"),
    RECOVERY("recovery"), RACE("race"), OTHER("other");
    companion object {
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}
