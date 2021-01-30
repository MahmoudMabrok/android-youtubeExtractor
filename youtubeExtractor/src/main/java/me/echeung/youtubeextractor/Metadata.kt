package me.echeung.youtubeextractor

data class Metadata(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    /**
     * The video length in seconds.
     */
    val duration: Long,
    val viewCount: Long,
    val isLive: Boolean,
    val shortDescription: String
) {

    // 120 x 90
    val thumbUrl: String
        get() = "$IMAGE_BASE_URL$videoId/default.jpg"

    // 320 x 180
    val mqImageUrl: String
        get() = "$IMAGE_BASE_URL$videoId/mqdefault.jpg"

    // 480 x 360
    val hqImageUrl: String
        get() = "$IMAGE_BASE_URL$videoId/hqdefault.jpg"

    // 640 x 480
    val sdImageUrl: String
        get() = "$IMAGE_BASE_URL$videoId/sddefault.jpg"

    // Max Res
    val maxResImageUrl: String
        get() = "$IMAGE_BASE_URL$videoId/maxresdefault.jpg"

    companion object {
        private const val IMAGE_BASE_URL = "http://i.ytimg.com/vi/"
    }
}
