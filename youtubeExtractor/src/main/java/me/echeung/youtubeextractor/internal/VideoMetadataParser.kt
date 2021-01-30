package me.echeung.youtubeextractor.internal

import me.echeung.youtubeextractor.VideoMetadata

class VideoMetadataParser {
    fun parseVideoMetadata(videoId: String, videoInfo: String): VideoMetadata {
        var isLiveStream = false
        var title = ""
        var author = ""
        var channelId = ""
        var shortDescription = ""
        var viewCount: Long = 0
        var duration: Long = 0

        var mat = patTitle.matcher(videoInfo)
        if (mat.find()) {
            title = mat.group(1)
        }

        mat = patHlsvp.matcher(videoInfo)
        if (mat.find()) {
            isLiveStream = true
        }

        mat = patAuthor.matcher(videoInfo)
        if (mat.find()) {
            author = mat.group(1)
        }

        mat = patChannelId.matcher(videoInfo)
        if (mat.find()) {
            channelId = mat.group(1)
        }

        mat = patShortDescript.matcher(videoInfo)
        if (mat.find()) {
            shortDescription = mat.group(1)
        }

        mat = patLength.matcher(videoInfo)
        if (mat.find()) {
            duration = mat.group(1).toLong()
        }

        mat = patViewCount.matcher(videoInfo)
        if (mat.find()) {
            viewCount = mat.group(1).toLong()
        }

        return VideoMetadata(
            videoId,
            title,
            author,
            channelId,
            duration,
            viewCount,
            isLiveStream,
            shortDescription
        )
    }

    companion object {
        private val patTitle = "\"title\"\\s*:\\s*\"(.*?)\"".toPattern()
        private val patAuthor = "\"author\"\\s*:\\s*\"(.+?)\"".toPattern()
        private val patChannelId = "\"channelId\"\\s*:\\s*\"(.+?)\"".toPattern()
        private val patLength = "\"lengthSeconds\"\\s*:\\s*\"(\\d+?)\"".toPattern()
        private val patViewCount = "\"viewCount\"\\s*:\\s*\"(\\d+?)\"".toPattern()
        private val patShortDescript = "\"shortDescription\"\\s*:\\s*\"(.+?)\"".toPattern()
        private val patHlsvp = "hlsvp=(.+?)(&|\\z)".toPattern()
    }
}
