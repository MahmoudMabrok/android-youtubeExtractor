package at.huber.youtubeExtractor

data class YtFile(
    /**
     * Format data for the specific file.
     */
    val format: Format,
    /**
     * The url to download the file.
     */
    val url: String
)
