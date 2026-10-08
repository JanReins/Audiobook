package com.janreins.audiobook.data

object CoverSources {
    private val priority = listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg", "folder.png")

    fun pickFolderCover(names: List<String>): String? = priority.firstNotNullOfOrNull { preferred ->
        names.firstOrNull { it.equals(preferred, ignoreCase = true) }
    }
}
