package me.echeung.youtubeextractor

data class Video(
    /**
     * Format data for the specific file.
     */
    val format: Format,
    /**
     * The url to download the file.
     */
    val url: String
)
